package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * 2D overlay ESP. World points are projected to the screen with plain math,
 * so no world-render hooks are needed. Works in first person.
 */
public final class Esp {
    private static MinecraftClient mc;
    private static boolean wasOn;
    private static long animStart;
    private static double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, focal;
    private static int sw, sh;

    static void setup(float delta) {
        sw = mc.getWindow().getScaledWidth();
        sh = mc.getWindow().getScaledHeight();
        Vec3d eye = mc.player.getCameraPosVec(delta);
        ex = eye.x; ey = eye.y; ez = eye.z;
        double yaw = Math.toRadians(mc.player.getYaw(delta));
        double pit = Math.toRadians(mc.player.getPitch(delta));
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pit), cp = Math.cos(pit);
        fx = -sy * cp; fy = -sp; fz = cy * cp;
        rx = -cy; rz = -sy;
        ux = -sy * sp; uy = cp; uz = cy * sp;
        double fov = Math.toRadians(mc.options.getFov().getValue());
        focal = (sh / 2.0) / Math.tan(fov / 2.0);
    }

    /** Returns {x, y} on screen or null when behind the camera. */
    static double[] project(double x, double y, double z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double d = dx * fx + dy * fy + dz * fz;
        if (d < 0.1) return null;
        double sx = dx * rx + dz * rz;
        double sY = dx * ux + dy * uy + dz * uz;
        return new double[]{sw / 2.0 + sx / d * focal, sh / 2.0 - sY / d * focal};
    }

    public static void render(DrawContext c, float delta) {
        mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        // animations restart from zero every time the module is switched on
        if (Modules.TARGET_ESP.on && !wasOn) animStart = now;
        wasOn = Modules.TARGET_ESP.on;

        if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
        if (!Modules.TARGET_ESP.on && !Modules.TARGET_BOX.on && !Modules.GLASS_ESP.on) return;
        Entity t = Features.espTarget(mc);
        if (t == null) return;

        setup(delta);
        Vec3d p = t.getLerpedPos(delta);
        double w = t.getWidth(), h = t.getHeight();
        boolean looked = mc.targetedEntity == t;
        float fade = looked ? 1f : (float) Math.max(0, Math.min(1, (3000 - (now - Features.targetTime)) / 600.0));
        int a1 = Theme.accent(), a2 = Theme.accent2();

        if (Modules.TARGET_ESP.on) {
            long anim = (long) ((now - animStart) * Modules.TARGET_ESP.f("Speed"));
            switch (Modules.TARGET_ESP.idx("Mode")) {
                case 1 -> katanas(c, p, w, h, now - animStart, fade, a1, a2);
                case 2 -> scan(c, p, w, h, now - animStart, fade, a1, a2);
                default -> ring(c, p, w, h, anim, fade, a1, a2);
            }
        }
        if (Modules.GLASS_ESP.on) glass(c, p, w, h, fade);
        if (Modules.TARGET_BOX.on) box(c, t, p, w, h, fade, a1, a2);
    }

    // ---------- helpers ----------
    static void line(DrawContext c, double x1, double y1, double x2, double y2, int th, int col) {
        int n = (int) Math.min(700, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (n <= 0) { c.fill((int) x1, (int) y1, (int) x1 + th, (int) y1 + th, col); return; }
        for (int i = 0; i <= n; i++) {
            int px = (int) (x1 + (x2 - x1) * i / n), py = (int) (y1 + (y2 - y1) * i / n);
            c.fill(px, py, px + th, py + th, col);
        }
    }

    private static void line3(DrawContext c, double ax, double ay, double az, double bx, double by, double bz, int th, int col) {
        double[] a = project(ax, ay, az), b = project(bx, by, bz);
        if (a == null || b == null) return;
        line(c, a[0], a[1], b[0], b[1], th, col);
    }

    /** Wireframe box of 12 edges. */
    static void wireBox(DrawContext c, double cx, double y0, double cz, double w, double h, int th, int col) {
        double hw = w / 2, y1 = y0 + h;
        for (int i = 0; i < 4; i++) {
            double ax = cx + ((i == 0 || i == 3) ? -hw : hw), az = cz + ((i < 2) ? -hw : hw);
            int j = (i + 1) % 4;
            double bx = cx + ((j == 0 || j == 3) ? -hw : hw), bz = cz + ((j < 2) ? -hw : hw);
            line3(c, ax, y0, az, bx, y0, bz, th, col);
            line3(c, ax, y1, az, bx, y1, bz, th, col);
            line3(c, ax, y0, az, ax, y1, az, th, col);
        }
    }

    /** Screen rectangle {x1,y1,x2,y2} around an entity box, or null when behind the camera. */
    private static int[] screenBox(Vec3d p, double w, double h) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9, hw = w / 2;
        for (int i = 0; i < 8; i++) {
            double[] s = project(p.x + ((i & 1) == 0 ? -hw : hw), p.y + ((i & 2) == 0 ? 0 : h), p.z + ((i & 4) == 0 ? -hw : hw));
            if (s == null) return null;
            minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
        }
        return new int[]{(int) minX, (int) minY, (int) maxX, (int) maxY};
    }

    // ---------- Katanas ----------
    private static void katanas(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double radius = (w * 0.85 + 0.25) * Modules.TARGET_ESP.f("Radius");
        double spd = Modules.TARGET_ESP.f("Katana speed");
        int n = 3;
        for (int k = 0; k < n; k++) {
            double ang = t * spd / 600.0 + k * Math.PI * 2 / n;
            double cx = p.x + Math.cos(ang) * radius, cz = p.z + Math.sin(ang) * radius;
            double cy = p.y + h * (0.55 + 0.18 * Math.sin(t * spd / 900.0 + k * 2));
            // blade points up and leans along the orbit tangent
            double dx = -Math.sin(ang) * 0.35, dz = Math.cos(ang) * 0.35, dy = 0.75;
            int col = Theme.mix(a1, a2, k / (float) n);
            for (int g = 1; g <= 3; g++) {   // short motion trail
                double ga = ang - g * 0.18 * Math.signum(spd);
                double gx = p.x + Math.cos(ga) * radius, gz = p.z + Math.sin(ga) * radius;
                line3(c, gx, cy, gz, gx - Math.sin(ga) * 0.35, cy + 0.75, gz + Math.cos(ga) * 0.35, 1,
                    Theme.alpha(col, fade * 0.22f / g));
            }
            line3(c, cx, cy, cz, cx + dx, cy + dy, cz + dz, 2, Theme.alpha(col, fade));
            line3(c, cx, cy, cz, cx + dx * 0.9, cy + dy * 0.9, cz + dz * 0.9, 1, Theme.alpha(0xFFFFFFFF, fade * 0.9f));
            line3(c, cx, cy, cz, cx - dx * 0.35, cy - 0.28, cz - dz * 0.35, 2, Theme.alpha(0xFF3A3A44, fade));
        }
        // two crossing slashes right after a hit
        long since = System.currentTimeMillis() - Features.hitTime;
        if (since < 380) {
            float a = 1f - since / 380f, grow = Math.min(1f, since / 110f);
            double my = p.y + h * 0.5, sz = (h * 0.6) * grow;
            double[][] dirs = {{0.8, 1.0, 0.3}, {-0.8, 1.0, -0.3}};
            for (double[] d : dirs) {
                line3(c, p.x - d[0] * sz, my - d[1] * sz, p.z - d[2] * sz, p.x + d[0] * sz, my + d[1] * sz, p.z + d[2] * sz, 2,
                    Theme.alpha(0xFFFFFFFF, a * fade));
                line3(c, p.x - d[0] * sz, my - d[1] * sz - 0.03, p.z - d[2] * sz, p.x + d[0] * sz, my + d[1] * sz - 0.03, p.z + d[2] * sz, 1,
                    Theme.alpha(a1, a * fade * 0.8f));
            }
        }
    }

    // ---------- Scan ----------
    private static void scan(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double spd = Modules.TARGET_ESP.f("Scan speed");
        int trail = (int) Modules.TARGET_ESP.f("Scan trail");
        int waves = (int) Modules.TARGET_ESP.f("Scan waves");
        double bw = w + 0.1;
        if (Modules.TARGET_ESP.b("Scan outline")) wireBox(c, p.x, p.y, p.z, bw, h, 1, Theme.alpha(a1, fade * 0.45f));
        for (int wv = 0; wv < waves; wv++) {
            for (int k = 0; k < trail; k++) {
                double ph = ((t * spd / 1600.0) + wv / (double) waves - k * 0.012) % 2.0;
                if (ph < 0) ph += 2.0;
                double yy = p.y + h * (ph < 1.0 ? ph : 2.0 - ph);
                float la = (1f - k / (float) trail) * fade;
                int col = Theme.mix(a1, a2, wv / (float) Math.max(1, waves));
                double hw = bw / 2;
                int th = k == 0 ? 2 : 1;
                int cc = k == 0 ? Theme.alpha(0xFFFFFFFF, la) : Theme.alpha(col, la * 0.7f);
                line3(c, p.x - hw, yy, p.z - hw, p.x + hw, yy, p.z - hw, th, cc);
                line3(c, p.x + hw, yy, p.z - hw, p.x + hw, yy, p.z + hw, th, cc);
                line3(c, p.x + hw, yy, p.z + hw, p.x - hw, yy, p.z + hw, th, cc);
                line3(c, p.x - hw, yy, p.z + hw, p.x - hw, yy, p.z - hw, th, cc);
            }
        }
    }

    // ---------- Glass ----------
    private static void glass(DrawContext c, Vec3d p, double w, double h, float fade) {
        int[] b = screenBox(p, w, h);
        if (b == null) return;
        int x1 = b[0] - 2, y1 = b[1] - 2, x2 = b[2] + 2, y2 = b[3] + 2;
        if (x2 - x1 < 2 || y2 - y1 < 2 || x2 - x1 > 2000 || y2 - y1 > 2000) return;
        int tint = 0xFF000000 | java.awt.Color.HSBtoRGB(Modules.GLASS_ESP.f("Tint hue"), 0.55f, 1f);
        float str = Modules.GLASS_ESP.f("Tint strength");
        float hit = 0f;
        if (Modules.GLASS_ESP.b("Hit reaction")) {
            long since = System.currentTimeMillis() - Features.hitTime;
            if (since < 350) hit = 1f - since / 350f;
        }
        int th = (int) Modules.GLASS_ESP.f("Glass thickness");
        int hgt = y2 - y1;
        for (int y = y1; y < y2; y++) {   // vertical gradient fill, brighter at the top like a glass pane
            float k = (y - y1) / (float) hgt;
            float a = (str * (1.15f - 0.6f * k) + hit * 0.35f) * fade;
            c.fill(x1, y, x2, y + 1, Theme.alpha(Theme.mix(tint, 0xFFFFFFFF, hit * 0.6f), a));
        }
        int edge = Theme.alpha(Theme.mix(tint, 0xFFFFFFFF, 0.55f + hit * 0.4f), (0.75f + hit * 0.25f) * fade);
        c.fill(x1, y1, x2, y1 + th, edge); c.fill(x1, y2 - th, x2, y2, edge);
        c.fill(x1, y1, x1 + th, y2, edge); c.fill(x2 - th, y1, x2, y2, edge);
        // diagonal highlight streaks
        int sw = x2 - x1;
        line(c, x1 + sw * 0.15, y2 - 3, x1 + sw * 0.55, y1 + 3, 1, Theme.alpha(0xFFFFFFFF, 0.22f * fade));
        line(c, x1 + sw * 0.30, y2 - 3, x1 + sw * 0.70, y1 + 3, 1, Theme.alpha(0xFFFFFFFF, 0.12f * fade));
    }

    private static void ring(DrawContext c, Vec3d p, double w, double h, long now, float fade, int a1, int a2) {
        double radius = (w * 0.85 + 0.15) * Modules.TARGET_ESP.f("Radius");
        int layers = (int) Modules.TARGET_ESP.f("Trail");
        int points = 40;
        for (int layer = 0; layer < layers; layer++) {
            double tt = (now - layer * 70L) % 1800L / 1800.0;
            double y = p.y + h * (0.5 + 0.5 * Math.sin(tt * Math.PI * 2));
            float la = (1f - layer / (float) layers) * fade;
            for (int i = 0; i < points; i++) {
                double ang = i * Math.PI * 2 / points + now / 900.0;
                double[] s = project(p.x + Math.cos(ang) * radius, y, p.z + Math.sin(ang) * radius);
                if (s == null) continue;
                int col = Theme.mix(a1, a2, i / (float) points);
                int size = layer == 0 ? 2 : 1;
                int sx = (int) s[0], sy = (int) s[1];
                c.fill(sx, sy, sx + size, sy + size, Theme.alpha(col, la * 0.95f));
            }
        }
        // bright orbiting dots
        if (Modules.TARGET_ESP.b("Orbit dots")) for (int k = 0; k < 3; k++) {
            double ang = now / 500.0 + k * Math.PI * 2 / 3;
            double y = p.y + h * (0.5 + 0.5 * Math.sin(now / 1800.0 * Math.PI * 2));
            double[] s = project(p.x + Math.cos(ang) * radius, y, p.z + Math.sin(ang) * radius);
            if (s == null) continue;
            int sx = (int) s[0], sy = (int) s[1];
            c.fill(sx - 1, sy - 1, sx + 2, sy + 2, Theme.alpha(0xFFFFFFFF, fade));
        }
    }

    private static void box(DrawContext c, Entity t, Vec3d p, double w, double h, float fade, int a1, int a2) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        double hw = w / 2;
        for (int i = 0; i < 8; i++) {
            double[] s = project(p.x + ((i & 1) == 0 ? -hw : hw), p.y + ((i & 2) == 0 ? 0 : h), p.z + ((i & 4) == 0 ? -hw : hw));
            if (s == null) return;
            minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
        }
        int x1 = (int) minX - 2, y1 = (int) minY - 2, x2 = (int) maxX + 2, y2 = (int) maxY + 2;
        int len = Math.max(3, Math.min((int) Modules.TARGET_BOX.f("Corner size"), (x2 - x1) / 2));
        int ca = Theme.alpha(a1, fade), cb = Theme.alpha(a2, fade);
        // corner brackets
        c.fill(x1, y1, x1 + len, y1 + 1, ca); c.fill(x1, y1, x1 + 1, y1 + len, ca);
        c.fill(x2 - len, y1, x2, y1 + 1, cb); c.fill(x2 - 1, y1, x2, y1 + len, cb);
        c.fill(x1, y2 - 1, x1 + len, y2, ca); c.fill(x1, y2 - len, x1 + 1, y2, ca);
        c.fill(x2 - len, y2 - 1, x2, y2, cb); c.fill(x2 - 1, y2 - len, x2, y2, cb);
        // health bar on the left
        if (Modules.TARGET_BOX.b("Health bar") && t instanceof LivingEntity le) {
            float k = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
            int bh = y2 - y1;
            c.fill(x1 - 5, y1, x1 - 3, y2, Theme.alpha(0xFF26262D, fade));
            int fh = (int) (bh * k);
            for (int i = 0; i < fh; i++) {
                c.fill(x1 - 5, y2 - i - 1, x1 - 3, y2 - i, Theme.alpha(Theme.mix(a1, a2, i / (float) Math.max(1, bh)), fade));
            }
        }
    }

    private Esp() {}
}

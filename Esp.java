package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 2D overlay ESP. World points are projected to the screen with plain math, so no
 * world-render hooks are needed. Works in first person.
 *
 * Performance notes: projection does not allocate, every line is a single rotated
 * rectangle (not one fill per pixel) and all randomness is a stateless hash.
 */
public final class Esp {
    private static MinecraftClient mc;
    private static boolean wasOn;
    private static long animStart;
    private static double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, focal;
    private static int sw, sh;
    /** Result of the last successful proj(): screen x, y and depth. */
    static double ox, oy, od;
    private static final float[] QUAL = {0.5f, 0.75f, 1f};
    private static float q = 0.75f;
    private static final double TAU = Math.PI * 2;

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

    /** Projects a world point into ox/oy/od. Returns false when it is behind the camera. */
    static boolean proj(double x, double y, double z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double d = dx * fx + dy * fy + dz * fz;
        if (d < 0.1) return false;
        double sx = dx * rx + dz * rz;
        double sY = dx * ux + dy * uy + dz * uz;
        ox = sw / 2.0 + sx / d * focal;
        oy = sh / 2.0 - sY / d * focal;
        od = d;
        return true;
    }

    /** Returns {x, y} on screen or null when behind the camera. */
    static double[] project(double x, double y, double z) {
        return proj(x, y, z) ? new double[]{ox, oy} : null;
    }

    /** Stateless random number in [0, 1). */
    static double hash(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * (1.0 / (1L << 53));
    }

    private static float vis;
    private static Entity visFor;

    /** True when at least one of a few points on the target can be seen from the camera (no block in between). */
    private static boolean canSee(Entity t, Vec3d p, double h) {
        Vec3d eye = mc.player.getCameraPosVec(1f);
        double[] ys = {0.12, 0.5, 0.88};
        for (double k : ys) {
            Vec3d pt = new Vec3d(p.x, p.y + h * k, p.z);
            var res = mc.world.raycast(new RaycastContext(eye, pt, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
            if (res.getType() == HitResult.Type.MISS) return true;
        }
        return false;
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
        if (fade <= 0.01f) return;
        // hide everything while the target is behind a wall; fade in/out instead of popping
        boolean seen = canSee(t, p, h);
        if (visFor != t) { visFor = t; vis = seen ? 1f : 0f; }
        vis += ((seen ? 1f : 0f) - vis) * 0.25f;
        if (vis <= 0.02f) return;
        fade *= vis;
        int a1 = Theme.accent(), a2 = Theme.accent2();

        if (Modules.TARGET_ESP.on) {
            q = QUAL[Modules.TARGET_ESP.idx("Quality")];
            long el = now - animStart;
            int mode = Modules.TARGET_ESP.idx("Mode");
            switch (mode) {
                case 1 -> katanas(c, p, w, h, el, fade, a1, a2);
                case 2 -> scan(c, p, w, h, el, fade, a1, a2);
                case 3 -> lightning(c, p, w, h, el, fade, a1, false);
                case 4 -> lightning(c, p, w, h, el, fade, a1, true);
                case 5 -> helix(c, p, w, h, el, fade, a1, a2);
                case 6 -> shockwave(c, p, w, el, fade, a1, a2);
                case 7 -> halo(c, p, w, h, el, fade, a1, a2);
                case 8 -> orbs(c, p, w, h, el, fade, a1, a2);
                default -> ring(c, p, w, h, el, fade, a1, a2);
            }
            if (Modules.TARGET_ESP.b("Hit lightning")) hitStrike(c, p, h, fade, a1);
        }
        if (Modules.GLASS_ESP.on) glass(c, p, w, h, fade);
        if (Modules.TARGET_BOX.on) box(c, t, p, w, h, fade, a1, a2);
    }

    // ---------- drawing primitives ----------
    /** A line is ONE rotated rectangle, whatever its length. */
    static void line(DrawContext c, double x1, double y1, double x2, double y2, int th, int col) {
        if ((col >>> 24) == 0) return;
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len > 4000) return;
        if (len < 1) { c.fill((int) x1, (int) y1, (int) x1 + th, (int) y1 + th, col); return; }
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate((float) x1, (float) y1);
        m.rotate((float) Math.atan2(dy, dx));
        int top = -(th / 2);
        c.fill(0, top, (int) Math.ceil(len), top + th, col);
        m.popMatrix();
    }

    private static void line3(DrawContext c, double ax, double ay, double az, double bx, double by, double bz, int th, int col) {
        if (!proj(ax, ay, az)) return;
        double sx = ox, sy = oy;
        if (!proj(bx, by, bz)) return;
        line(c, sx, sy, ox, oy, th, col);
    }

    private static void dot(DrawContext c, double x, double y, int size, int col) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        c.fill(ix, iy, ix + size, iy + size, col);
    }

    /** Soft glowing point: a faint halo, a coloured body and a white core. */
    private static void glowDot(DrawContext c, double x, double y, int col, float a) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        c.fill(ix - 2, iy - 2, ix + 3, iy + 3, Theme.alpha(col, a * 0.16f));
        c.fill(ix - 1, iy - 1, ix + 2, iy + 2, Theme.alpha(col, a * 0.5f));
        c.fill(ix, iy, ix + 1, iy + 1, Theme.alpha(0xFFFFFFFF, a));
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

    // ---------- Lightning ----------
    /** Jagged bolt from the start point to the target, with a soft glow and short forks. */
    private static void bolt(DrawContext c, double sx, double sy, double sz, double tx, double ty, double tz,
                             long seed, float a, int col, int segs, boolean fork) {
        double ppx = 0, ppy = 0;
        boolean has = false;
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs;
            double wob = (i == 0 || i == segs) ? 0 : 0.42 * (1.0 - t * 0.5);
            double x = sx + (tx - sx) * t + (hash(seed * 31 + i * 3L) - 0.5) * 2 * wob;
            double y = sy + (ty - sy) * t;
            double z = sz + (tz - sz) * t + (hash(seed * 31 + i * 3L + 1) - 0.5) * 2 * wob;
            if (!proj(x, y, z)) { has = false; continue; }
            double cx = ox, cy = oy;
            if (has) {
                line(c, ppx, ppy, cx, cy, 3, Theme.alpha(col, a * 0.16f));
                line(c, ppx, ppy, cx, cy, 2, Theme.alpha(col, a * 0.4f));
                line(c, ppx, ppy, cx, cy, 1, Theme.alpha(0xFFFFFFFF, a));
            }
            ppx = cx; ppy = cy; has = true;
            if (fork && i > 1 && i < segs - 1 && hash(seed * 17 + i) < 0.3) {
                double bx = x + (hash(seed * 13 + i) - 0.5) * 1.4;
                double by = y - 0.9 - hash(seed * 19 + i) * 0.6;
                double bz = z + (hash(seed * 23 + i) - 0.5) * 1.4;
                if (proj((x + bx) / 2 + (hash(seed * 29 + i) - 0.5) * 0.3, (y + by) / 2, (z + bz) / 2)) {
                    double mx = ox, my = oy;
                    if (proj(bx, by, bz)) {
                        line(c, cx, cy, mx, my, 1, Theme.alpha(col, a * 0.6f));
                        line(c, mx, my, ox, oy, 1, Theme.alpha(col, a * 0.45f));
                    }
                }
            }
        }
    }

    /** Flash and sparks where a bolt lands. */
    private static void impact(DrawContext c, double x, double y, double z, long age, long seed, float a, int col) {
        if (!proj(x, y, z)) return;
        double cx = ox, cy = oy;
        float k = Math.min(1f, age / 120f);
        glowDot(c, cx, cy, col, a);
        int n = (int) (3 + 3 * q);
        for (int i = 0; i < n; i++) {
            double ang = hash(seed + 50 + i) * TAU;
            double len = (3 + hash(seed + 60 + i) * 8) * k;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            line(c, cx + ca * len * 0.3, cy + sa * len * 0.3, cx + ca * len, cy + sa * len, 1, Theme.alpha(0xFFFFFFFF, a * 0.8f));
        }
    }

    private static void lightning(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, boolean storm) {
        Module m = Modules.TARGET_ESP;
        int n = (int) m.f("Bolts");
        long period = (long) (1000 / m.f("Bolt rate"));
        double topY = p.y + h + (storm ? 2.3 : 3.6);
        int col = Theme.mix(a1, 0xFFFFFFFF, 0.5f);
        if (storm) storm(c, p, h, t, fade);

        for (int k = 0; k < n; k++) {
            long tt = t + k * period / n + k * 37L;
            long slot = tt / period, age = tt % period;
            if (age > 320) continue;
            float life = 1f - age / 320f;
            float flick = ((age / 40) % 2 == 0) ? 1f : 0.6f;
            long seed = slot * 7919L + k * 104729L;
            double ang = hash(seed) * TAU, dist = 0.15 + hash(seed + 1) * (w * 0.5 + 0.45);
            double tx = p.x + Math.cos(ang) * dist, tz = p.z + Math.sin(ang) * dist;
            double ty = p.y + (hash(seed + 2) < 0.5 ? 0 : h * 0.5 * hash(seed + 3));
            double sx = tx + (hash(seed + 4) - 0.5) * 1.6, sz = tz + (hash(seed + 5) - 0.5) * 1.6;
            float a = fade * life * flick;
            bolt(c, sx, topY, sz, tx, ty, tz, seed, a, col, (int) (5 + 3 * q), q > 0.6f);
            impact(c, tx, ty, tz, age, seed, a, a1);
        }

        // short electric arcs crawling around the body, re-rolled every 70 ms
        long slice = t / 70;
        int arcs = q < 0.6f ? 1 : 2;
        for (int k = 0; k < arcs; k++) {
            long seed = slice * 131 + k * 17L;
            double a0 = hash(seed) * TAU, y0 = p.y + h * (0.15 + 0.7 * hash(seed + 1)), rad = w * 0.6 + 0.12;
            double ppx = 0, ppy = 0;
            boolean has = false;
            for (int i = 0; i < 5; i++) {
                double ang = a0 + i * 0.34, rr = rad + (hash(seed + 10 + i) - 0.5) * 0.22;
                double yy = y0 + (hash(seed + 20 + i) - 0.5) * 0.32;
                if (!proj(p.x + Math.cos(ang) * rr, yy, p.z + Math.sin(ang) * rr)) { has = false; continue; }
                if (has) {
                    line(c, ppx, ppy, ox, oy, 2, Theme.alpha(a1, fade * 0.25f));
                    line(c, ppx, ppy, ox, oy, 1, Theme.alpha(0xFFFFFFFF, fade * 0.85f));
                }
                ppx = ox; ppy = oy; has = true;
            }
        }
    }

    /** Dark cloud above the target with falling rain. */
    private static void storm(DrawContext c, Vec3d p, double h, long t, float fade) {
        double y = p.y + h + 2.2;
        int blobs = (int) (12 * q) + 4;
        for (int i = 0; i < blobs; i++) {
            double ang = hash(i * 13L) * TAU + t / 9000.0 * (0.6 + hash(i * 7L));
            double r = hash(i * 29L) * 1.1;
            double by = y + (hash(i * 5L) - 0.5) * 0.25;
            if (!proj(p.x + Math.cos(ang) * r, by, p.z + Math.sin(ang) * r)) continue;
            int s = (int) Math.max(4, focal * 0.55 / od);
            int bx = (int) ox, byy = (int) oy;
            c.fill(bx - s, byy - s / 2, bx + s, byy + s / 2, Theme.alpha(0xFF3A4254, fade * 0.16f));
            c.fill(bx - s * 2 / 3, byy - s / 3, bx + s * 2 / 3, byy + s / 3, Theme.alpha(0xFF5A6478, fade * 0.12f));
        }
        int drops = (int) (22 * q);
        for (int i = 0; i < drops; i++) {
            double ang = hash(i * 3L + 900) * TAU, r = hash(i * 3L + 901) * 1.5;
            double dx = p.x + Math.cos(ang) * r, dz = p.z + Math.sin(ang) * r;
            double ph = (t * 0.0011 * (0.8 + hash(i * 3L + 902) * 0.5) + hash(i * 3L + 903)) % 1.0;
            double yy = y - ph * (y - p.y);
            if (!proj(dx, yy, dz)) continue;
            double ax = ox, ay = oy;
            if (proj(dx, yy + 0.45, dz)) line(c, ax, ay, ox, oy, 1, Theme.alpha(0xFFBFD8FF, fade * 0.5f));
        }
    }

    /** Bolt that strikes your target right after you hit it (works with every mode). */
    private static void hitStrike(DrawContext c, Vec3d p, double h, float fade, int a1) {
        long since = System.currentTimeMillis() - Features.hitTime;
        if (since > 420) return;
        float life = 1f - since / 420f;
        float flick = ((since / 40) % 2 == 0) ? 1f : 0.6f;
        long seed = Features.hitTime;
        float a = fade * life * flick;
        bolt(c, p.x + (hash(seed) - 0.5) * 0.8, p.y + h + 3.5, p.z + (hash(seed + 1) - 0.5) * 0.8,
            p.x, p.y + h * 0.5, p.z, seed, a, Theme.mix(a1, 0xFFFFFFFF, 0.6f), 7, true);
        impact(c, p.x, p.y + h * 0.5, p.z, since, seed, a, a1);
    }

    // ---------- Ring ----------
    private static void ring(DrawContext c, Vec3d p, double w, double h, long el, float fade, int a1, int a2) {
        double speed = Modules.TARGET_ESP.f("Speed");
        long now = (long) (el * speed);
        double radius = (w * 0.85 + 0.15) * Modules.TARGET_ESP.f("Radius");
        int layers = Math.max(1, (int) (Modules.TARGET_ESP.f("Trail") * (0.5 + 0.5 * q)));
        int points = (int) (40 * q);
        for (int layer = 0; layer < layers; layer++) {
            double tt = (now - layer * 70L) % 1800L / 1800.0;
            double y = p.y + h * (0.5 + 0.5 * Math.sin(tt * TAU));
            float la = (1f - layer / (float) layers) * fade;
            int size = layer == 0 ? 2 : 1;
            for (int i = 0; i < points; i++) {
                double ang = i * TAU / points + now / 900.0;
                if (!proj(p.x + Math.cos(ang) * radius, y, p.z + Math.sin(ang) * radius)) continue;
                dot(c, ox, oy, size, Theme.alpha(Theme.mix(a1, a2, i / (float) points), la * 0.95f));
            }
        }
        if (Modules.TARGET_ESP.b("Orbit dots")) for (int k = 0; k < 3; k++) {
            double ang = now / 500.0 + k * TAU / 3;
            double y = p.y + h * (0.5 + 0.5 * Math.sin(now / 1800.0 * TAU));
            if (!proj(p.x + Math.cos(ang) * radius, y, p.z + Math.sin(ang) * radius)) continue;
            glowDot(c, ox, oy, a1, fade);
        }
    }

    // ---------- Katanas ----------
    private static void katanas(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double radius = (w * 0.85 + 0.25) * Modules.TARGET_ESP.f("Radius");
        double spd = Modules.TARGET_ESP.f("Katana speed");
        int n = 3;
        int trail = q < 0.6f ? 1 : 3;
        for (int k = 0; k < n; k++) {
            double ang = t * spd / 600.0 + k * TAU / n;
            double cx = p.x + Math.cos(ang) * radius, cz = p.z + Math.sin(ang) * radius;
            double cy = p.y + h * (0.55 + 0.18 * Math.sin(t * spd / 900.0 + k * 2));
            double dx = -Math.sin(ang) * 0.35, dz = Math.cos(ang) * 0.35, dy = 0.75;
            int col = Theme.mix(a1, a2, k / (float) n);
            for (int g = 1; g <= trail; g++) {
                double ga = ang - g * 0.18 * Math.signum(spd);
                double gx = p.x + Math.cos(ga) * radius, gz = p.z + Math.sin(ga) * radius;
                line3(c, gx, cy, gz, gx - Math.sin(ga) * 0.35, cy + 0.75, gz + Math.cos(ga) * 0.35, 1, Theme.alpha(col, fade * 0.22f / g));
            }
            line3(c, cx, cy, cz, cx + dx, cy + dy, cz + dz, 2, Theme.alpha(col, fade));
            line3(c, cx, cy, cz, cx + dx * 0.9, cy + dy * 0.9, cz + dz * 0.9, 1, Theme.alpha(0xFFFFFFFF, fade * 0.9f));
            line3(c, cx, cy, cz, cx - dx * 0.35, cy - 0.28, cz - dz * 0.35, 2, Theme.alpha(0xFF3A3A44, fade));
        }
        long since = System.currentTimeMillis() - Features.hitTime;
        if (since < 380) {
            float a = 1f - since / 380f, grow = Math.min(1f, since / 110f);
            double my = p.y + h * 0.5, sz = (h * 0.6) * grow;
            for (int s = 0; s < 2; s++) {
                double dx = s == 0 ? 0.8 : -0.8, dy = 1.0, dz = s == 0 ? 0.3 : -0.3;
                line3(c, p.x - dx * sz, my - dy * sz, p.z - dz * sz, p.x + dx * sz, my + dy * sz, p.z + dz * sz, 2, Theme.alpha(0xFFFFFFFF, a * fade));
                line3(c, p.x - dx * sz, my - dy * sz - 0.03, p.z - dz * sz, p.x + dx * sz, my + dy * sz - 0.03, p.z + dz * sz, 1, Theme.alpha(a1, a * fade * 0.8f));
            }
        }
    }

    // ---------- Scan ----------
    private static void scan(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double spd = Modules.TARGET_ESP.f("Scan speed");
        int trail = (int) Modules.TARGET_ESP.f("Scan trail");
        int waves = (int) Modules.TARGET_ESP.f("Scan waves");
        int step = q < 0.6f ? 2 : 1;
        double bw = w + 0.1, hw = bw / 2;
        if (Modules.TARGET_ESP.b("Scan outline")) wireBox(c, p.x, p.y, p.z, bw, h, 1, Theme.alpha(a1, fade * 0.45f));
        for (int wv = 0; wv < waves; wv++) {
            for (int k = 0; k < trail; k += step) {
                double ph = ((t * spd / 1600.0) + wv / (double) waves - k * 0.012) % 2.0;
                if (ph < 0) ph += 2.0;
                double yy = p.y + h * (ph < 1.0 ? ph : 2.0 - ph);
                float la = (1f - k / (float) trail) * fade;
                int col = Theme.mix(a1, a2, wv / (float) Math.max(1, waves));
                int th = k == 0 ? 2 : 1;
                int cc = k == 0 ? Theme.alpha(0xFFFFFFFF, la) : Theme.alpha(col, la * 0.7f);
                line3(c, p.x - hw, yy, p.z - hw, p.x + hw, yy, p.z - hw, th, cc);
                line3(c, p.x + hw, yy, p.z - hw, p.x + hw, yy, p.z + hw, th, cc);
                line3(c, p.x + hw, yy, p.z + hw, p.x - hw, yy, p.z + hw, th, cc);
                line3(c, p.x - hw, yy, p.z + hw, p.x - hw, yy, p.z - hw, th, cc);
            }
        }
    }

    // ---------- Helix ----------
    private static void helix(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double radius = (w * 0.7 + 0.2) * Modules.TARGET_ESP.f("Radius");
        double sp = Modules.TARGET_ESP.f("Speed");
        int pts = (int) (22 * q);
        double spin = t * sp / 650.0;
        for (int strand = 0; strand < 2; strand++) {
            int col = strand == 0 ? a1 : a2;
            for (int i = 0; i <= pts; i++) {
                double u = i / (double) pts;
                double ang = u * Math.PI * 3 + spin + strand * Math.PI;
                if (!proj(p.x + Math.cos(ang) * radius, p.y + h * u, p.z + Math.sin(ang) * radius)) continue;
                float la = fade * (0.35f + 0.65f * (float) u);
                dot(c, ox, oy, i % 3 == 0 ? 2 : 1, Theme.alpha(col, la));
            }
            // bright head climbing the strand
            double u = (t * sp / 2200.0 + strand * 0.5) % 1.0, ang = u * Math.PI * 3 + spin + strand * Math.PI;
            if (proj(p.x + Math.cos(ang) * radius, p.y + h * u, p.z + Math.sin(ang) * radius)) glowDot(c, ox, oy, col, fade);
        }
        // rungs between the strands
        int rungs = (int) (5 * q) + 1;
        for (int i = 0; i < rungs; i++) {
            double u = (i + 0.5) / rungs, ang = u * Math.PI * 3 + spin;
            line3(c, p.x + Math.cos(ang) * radius, p.y + h * u, p.z + Math.sin(ang) * radius,
                p.x - Math.cos(ang) * radius, p.y + h * u, p.z - Math.sin(ang) * radius, 1, Theme.alpha(0xFFFFFFFF, fade * 0.18f));
        }
    }

    // ---------- Shockwave ----------
    private static void shockwave(DrawContext c, Vec3d p, double w, long t, float fade, int a1, int a2) {
        double sp = Modules.TARGET_ESP.f("Speed"), base = Modules.TARGET_ESP.f("Radius");
        int pts = (int) (30 * q) + 8;
        for (int k = 0; k < 3; k++) {
            double ph = (t * sp / 1800.0 + k / 3.0) % 1.0;
            double r = (w * 0.4 + 0.1) + ph * (0.6 + base * 1.2);
            float a = (float) Math.pow(1.0 - ph, 1.5) * fade;
            if (a < 0.02f) continue;
            int size = ph < 0.25 ? 2 : 1;
            for (int i = 0; i < pts; i++) {
                double ang = i * TAU / pts;
                if (!proj(p.x + Math.cos(ang) * r, p.y + 0.05, p.z + Math.sin(ang) * r)) continue;
                dot(c, ox, oy, size, Theme.alpha(Theme.mix(a1, a2, i / (float) pts), a));
            }
        }
        if (proj(p.x, p.y + 0.05, p.z)) glowDot(c, ox, oy, a1, fade * 0.7f);
    }

    // ---------- Halo ----------
    private static void halo(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double y = p.y + h + 0.3 + 0.05 * Math.sin(t / 400.0);
        double r = Math.max(0.22, w * 0.42) * Modules.TARGET_ESP.f("Radius");
        double sp = Modules.TARGET_ESP.f("Speed");
        int pts = (int) (28 * q) + 6;
        double head = t * sp / 700.0;
        for (int i = 0; i < pts; i++) {
            double ang = i * TAU / pts;
            double d = ((head - ang) % TAU + TAU) % TAU / TAU;
            float k = (float) Math.max(0, 1 - d * 1.4);
            if (!proj(p.x + Math.cos(ang) * r, y, p.z + Math.sin(ang) * r)) continue;
            dot(c, ox, oy, k > 0.6f ? 2 : 1, Theme.alpha(Theme.mix(a1, 0xFFFFFFFF, k * 0.8f), fade * (0.4f + 0.6f * k)));
        }
        int sparks = q < 0.6f ? 2 : 4;
        for (int i = 0; i < sparks; i++) {
            double ph = (t / 1500.0 + i / (double) sparks) % 1.0;
            double ang = hash(i * 11L) * TAU;
            if (!proj(p.x + Math.cos(ang) * r * 0.8, y + ph * 0.5, p.z + Math.sin(ang) * r * 0.8)) continue;
            int ix = (int) ox, iy = (int) oy;
            int sc = Theme.alpha(0xFFFFFFFF, fade * (1f - (float) ph));
            c.fill(ix - 1, iy, ix + 2, iy + 1, sc);
            c.fill(ix, iy - 1, ix + 1, iy + 2, sc);
        }
    }

    // ---------- Orbs ----------
    private static void orbs(DrawContext c, Vec3d p, double w, double h, long t, float fade, int a1, int a2) {
        double r = (w * 0.9 + 0.3) * Modules.TARGET_ESP.f("Radius");
        double sp = Modules.TARGET_ESP.f("Speed");
        int tail = (int) (7 * q) + 2;
        double cy = p.y + h * 0.5;
        for (int k = 0; k < 3; k++) {
            double tilt = k * Math.PI / 3;
            double st = Math.sin(tilt), ct = Math.cos(tilt);
            int col = Theme.mix(a1, a2, k / 2f);
            double a0 = t * sp / 700.0 + k * TAU / 3;
            for (int j = tail; j >= 1; j--) {
                double a = a0 - j * 0.12;
                if (!proj(p.x + Math.cos(a) * r, cy + Math.sin(a) * st * r * 0.8, p.z + Math.sin(a) * ct * r)) continue;
                dot(c, ox, oy, 1, Theme.alpha(col, fade * (1f - j / (float) (tail + 1)) * 0.7f));
            }
            if (proj(p.x + Math.cos(a0) * r, cy + Math.sin(a0) * st * r * 0.8, p.z + Math.sin(a0) * ct * r)) glowDot(c, ox, oy, col, fade);
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
        int bands = 8;   // vertical gradient in a few bands, brighter at the top like a glass pane
        for (int i = 0; i < bands; i++) {
            int ya = y1 + hgt * i / bands, yb = y1 + hgt * (i + 1) / bands;
            if (yb <= ya) continue;
            float k = (i + 0.5f) / bands;
            float a = (str * (1.15f - 0.6f * k) + hit * 0.35f) * fade;
            c.fill(x1, ya, x2, yb, Theme.alpha(Theme.mix(tint, 0xFFFFFFFF, hit * 0.6f), a));
        }
        int edge = Theme.alpha(Theme.mix(tint, 0xFFFFFFFF, 0.55f + hit * 0.4f), (0.75f + hit * 0.25f) * fade);
        c.fill(x1, y1, x2, y1 + th, edge); c.fill(x1, y2 - th, x2, y2, edge);
        c.fill(x1, y1, x1 + th, y2, edge); c.fill(x2 - th, y1, x2, y2, edge);
        int sw2 = x2 - x1;
        line(c, x1 + sw2 * 0.15, y2 - 3, x1 + sw2 * 0.55, y1 + 3, 1, Theme.alpha(0xFFFFFFFF, 0.22f * fade));
        line(c, x1 + sw2 * 0.30, y2 - 3, x1 + sw2 * 0.70, y1 + 3, 1, Theme.alpha(0xFFFFFFFF, 0.12f * fade));
    }

    /** Screen rectangle {x1,y1,x2,y2} around an entity box, or null when behind the camera. */
    private static int[] screenBox(Vec3d p, double w, double h) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9, hw = w / 2;
        for (int i = 0; i < 8; i++) {
            if (!proj(p.x + ((i & 1) == 0 ? -hw : hw), p.y + ((i & 2) == 0 ? 0 : h), p.z + ((i & 4) == 0 ? -hw : hw))) return null;
            minX = Math.min(minX, ox); maxX = Math.max(maxX, ox);
            minY = Math.min(minY, oy); maxY = Math.max(maxY, oy);
        }
        return new int[]{(int) minX, (int) minY, (int) maxX, (int) maxY};
    }

    // ---------- Box ----------
    private static void box(DrawContext c, Entity t, Vec3d p, double w, double h, float fade, int a1, int a2) {
        int[] b = screenBox(p, w, h);
        if (b == null) return;
        int x1 = b[0] - 2, y1 = b[1] - 2, x2 = b[2] + 2, y2 = b[3] + 2;
        int len = Math.max(3, Math.min((int) Modules.TARGET_BOX.f("Corner size"), (x2 - x1) / 2));
        int ca = Theme.alpha(a1, fade), cb = Theme.alpha(a2, fade);
        c.fill(x1, y1, x1 + len, y1 + 1, ca); c.fill(x1, y1, x1 + 1, y1 + len, ca);
        c.fill(x2 - len, y1, x2, y1 + 1, cb); c.fill(x2 - 1, y1, x2, y1 + len, cb);
        c.fill(x1, y2 - 1, x1 + len, y2, ca); c.fill(x1, y2 - len, x1 + 1, y2, ca);
        c.fill(x2 - len, y2 - 1, x2, y2, cb); c.fill(x2 - 1, y2 - len, x2, y2, cb);
        if (Modules.TARGET_BOX.b("Health bar") && t instanceof LivingEntity le) {
            float k = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
            int bh = y2 - y1;
            c.fill(x1 - 5, y1, x1 - 3, y2, Theme.alpha(0xFF26262D, fade));
            int fh = (int) (bh * k), bands = 6;
            for (int i = 0; i < bands; i++) {
                int ya = fh * i / bands, yb = fh * (i + 1) / bands;
                if (yb <= ya) continue;
                c.fill(x1 - 5, y2 - yb, x1 - 3, y2 - ya, Theme.alpha(Theme.mix(a1, a2, (i + 0.5f) / bands * fh / Math.max(1, bh)), fade));
            }
        }
    }

    private Esp() {}
}

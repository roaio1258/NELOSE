package nelose;

import net.minecraft.client.gui.DrawContext;

/** Animated Nelose logo: comet ring, pulsing glow, orbiting dots and an "N". */
public final class Avatar {
    private static void disc(DrawContext c, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, col);
        }
    }

    /** Draws the logo chosen in Settings, with the blizzard above it. */
    public static void draw(DrawContext c, int cx, int cy, int R, float fade) {
        draw(c, cx, cy, R, fade, Modules.LOGO.idx("Model"), Modules.LOGO.b("Blizzard"));
    }

    public static void draw(DrawContext c, int cx, int cy, int R, float fade, int model, boolean storm) {
        double t = Modules.LOGO.b("Animated") ? (System.currentTimeMillis() % 100000L) / 1000.0 : 1.0;
        if (model == 0) ring(c, cx, cy, R, fade, t); else art(c, cx, cy, R, fade, t, model);
        if (storm) blizzard(c, cx, cy, R, fade, t);
    }

    // ---------- textured logos (Haunter / cat) ----------
    private static final int[] SIZES = {18, 26, 38, 62, 96};
    private static final java.util.Map<String, net.minecraft.util.Identifier> TEX = new java.util.HashMap<>();

    private static net.minecraft.util.Identifier tex(String name, int size) {
        return TEX.computeIfAbsent(name + size,
            k -> net.minecraft.util.Identifier.of("nelose", "textures/logo/" + name + "_" + size + ".png"));
    }

    private static void art(DrawContext c, int cx, int cy, int R, float fade, double t, int model) {
        int a1 = Theme.accent();
        int want = 2 * R + 2, size = SIZES[0];
        for (int s : SIZES) if (Math.abs(s - want) < Math.abs(size - want)) size = s;   // 1:1 texture, no resampling blur
        double pulse = 0.5 + 0.5 * Math.sin(t * 2.2);
        int bob = (int) Math.round(Math.sin(t * 2.0) * (R >= 10 ? 2 : 1));

        if (Modules.LOGO.b("Winter glow")) {
            for (int i = 5; i >= 1; i--) {
                disc(c, cx, cy + bob, R + i * 2, Theme.alpha(a1, fade * (0.014f + 0.016f * (float) pulse) * (6 - i) / 5f));
            }
        }
        if (fade > 0.3f) {
            c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex(model == 1 ? "haunter" : "cat", size),
                cx - size / 2, cy - size / 2 + bob, 0f, 0f, size, size, size, size);
        }
        // twinkling frost sparkles
        for (int i = 0; i < 3; i++) {
            double ph = (t * 0.9 + i * 0.37) % 1.0;
            float k = (float) Math.sin(ph * Math.PI);
            int px = cx + (int) Math.round(Math.cos(i * 2.4 + t * 0.3) * R * 0.9), py = cy + bob + (int) Math.round(Math.sin(i * 2.4 + t * 0.3) * R * 0.8);
            int fl = Theme.alpha(0xFFFFFFFF, fade * k);
            c.fill(px - 1, py, px + 2, py + 1, fl);
            c.fill(px, py - 1, px + 1, py + 2, fl);
        }
    }

    /** Wind-driven snow that falls onto the top of the logo. */
    private static void blizzard(DrawContext c, int cx, int cy, int R, float fade, double t) {
        int n = R >= 10 ? 30 : 10;
        int span = (int) (R * 3.2), left = cx - span / 2;
        int top = cy - R - (R >= 10 ? (int) (R * 1.3) : 8), bottom = cy - (int) (R * 0.15);
        double wind = 0.7 + 0.5 * Math.sin(t * 0.5);
        for (int i = 0; i < n; i++) {
            double depth = 0.4 + ((i * 37) % 10) / 10.0 * 0.6;
            double p = ((i * 0.6180339) + t * 0.45 * depth) % 1.0;
            double x = left + ((((i * 7919L) % span) + p * R * 2.4 * wind * depth) % span + span) % span;
            double y = top + p * (bottom - top);
            float a = fade * (float) (Math.sin(p * Math.PI) * depth);
            int s = depth > 0.8 ? 2 : 1;
            if (depth > 0.62 && depth <= 0.8) {   // gust streak
                c.fill((int) x - 2, (int) y, (int) x + 1, (int) y + 1, Theme.alpha(0xFFFFFFFF, a * 0.5f));
            }
            c.fill((int) x, (int) y, (int) x + s, (int) y + s, Theme.alpha(0xFFFFFFFF, a));
        }
    }

    private static void ring(DrawContext c, int cx, int cy, int R, float fade, double t) {
        int a1 = Theme.accent(), a2 = Theme.accent2();
        int th = R >= 10 ? 2 : 1;

        // pulsing glow
        double pulse = 0.5 + 0.5 * Math.sin(t * 2.2);
        for (int i = 6; i >= 1; i--) {
            disc(c, cx, cy, R + i + 1, Theme.alpha(a1, fade * (0.018f + 0.02f * (float) pulse) * (7 - i) / 6f));
        }

        // body
        disc(c, cx, cy, R, Theme.alpha(Theme.BORDER, fade));
        disc(c, cx, cy, R - 1, Theme.alpha(0xFF08080B, fade));

        // base ring + comet tail
        double head = (t * 150) % 360;
        for (int deg = 0; deg < 360; deg += 2) {
            double rad = Math.toRadians(deg);
            int px = cx + (int) Math.round(Math.cos(rad) * (R - 1));
            int py = cy + (int) Math.round(Math.sin(rad) * (R - 1));
            double d = ((head - deg) % 360 + 360) % 360 / 360.0;       // 0 at head
            float k = (float) Math.max(0, 1 - d * 1.6);
            int col = Theme.mix(a1, a2, (float) (0.5 + 0.5 * Math.sin(rad * 2 + t)));
            c.fill(px, py, px + th, py + th, Theme.alpha(Theme.mix(0xFF2A2A31, col, k), fade));
        }

        // winter: a cap of snow lying on top of the ring
        int cap = R >= 10 ? 3 : 2;
        for (int deg = 212; deg <= 328; deg++) {
            double rad = Math.toRadians(deg), d = Math.abs(deg - 270) / 58.0;
            int thick = d < 0.35 ? cap : (d < 0.75 ? Math.max(1, cap - 1) : 1);
            int sx = cx + (int) Math.round(Math.cos(rad) * R), sy = cy + (int) Math.round(Math.sin(rad) * R) - 1;
            for (int k = 0; k < thick; k++) c.fill(sx, sy + k, sx + 1, sy + k + 1, Theme.alpha(0xFFF4FAFF, fade * (k == 0 ? 1f : 0.85f)));
        }

        // orbiting dots
        if (R >= 10) {
            for (int i = 0; i < 3; i++) {
                double ang = t * 1.6 + i * Math.PI * 2 / 3;
                double rr = R * 0.62;
                int px = cx + (int) Math.round(Math.cos(ang) * rr);
                int py = cy + (int) Math.round(Math.sin(ang * 1.0) * rr);
                for (int tr = 4; tr >= 0; tr--) {
                    double a = ang - tr * 0.13;
                    int tx = cx + (int) Math.round(Math.cos(a) * rr);
                    int ty = cy + (int) Math.round(Math.sin(a) * rr);
                    c.fill(tx, ty, tx + 1, ty + 1, Theme.alpha(a2, fade * (1 - tr / 5f) * 0.7f));
                }
                int fl = Theme.alpha(0xFFFFFFFF, fade);   // tiny snowflake
                c.fill(px - 1, py, px + 2, py + 1, fl);
                c.fill(px, py - 1, px + 1, py + 2, fl);
            }
        } else if (R >= 6) {
            // small version (watermark): one white dot orbiting the N
            double ang = t * 2.0;
            int px = cx + (int) Math.round(Math.cos(ang) * (R - 3)), py = cy + (int) Math.round(Math.sin(ang) * (R - 3));
            c.fill(px, py, px + 1, py + 1, Theme.alpha(0xFFFFFFFF, fade));
        }

        // "N" - same letter as the icon, scaled to the size of the logo
        if (R >= 6) {
            int sw = R >= 10 ? 2 : 1;
            int s = Math.max(3, (int) Math.round(R * (R >= 10 ? 0.34 : 0.42)));
            int w = Math.max(2, (int) Math.round(s * 0.8));
            int g = Theme.mix(a1, a2, (float) (0.5 + 0.5 * Math.sin(t * 1.5)));
            c.fill(cx - w, cy - s, cx - w + sw, cy + s + 1, Theme.alpha(g, fade));
            c.fill(cx + w - sw + 1, cy - s, cx + w + 1, cy + s + 1, Theme.alpha(g, fade));
            for (int yy = 0; yy <= 2 * s; yy++) {
                int xx = cx - w + (int) Math.round(yy * (2.0 * w - sw + 1) / (2 * s));
                c.fill(xx, cy - s + yy, xx + sw, cy - s + yy + 1, Theme.alpha(g, fade));
            }
        } else {
            disc(c, cx, cy, 2, Theme.alpha(Theme.mix(a1, a2, (float) pulse), fade));
        }
    }
}

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
            int gl = R >= 10 ? 5 : 3, gs = R >= 10 ? 2 : 3;
            for (int i = gl; i >= 1; i--) {
                disc(c, cx, cy + bob, R + i * gs, Theme.alpha(a1, fade * (0.014f + 0.016f * (float) pulse) * (gl + 1 - i) / (float) gl));
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

    private static void ring(DrawContext c, int cx, int cy, int R0, float fade, double t) {
        int a1 = Season.accent(), a2 = Season.accent2();
        boolean big = R0 >= 10;
        int th = big ? 2 : 1;

        // pop when the theme changes: the logo swells, a shockwave and sparks fly out
        double since = (System.currentTimeMillis() - Season.changedAt) / 1000.0;
        int R = R0;
        if (big && since < 0.55) {
            double k = since / 0.55;
            R = R0 + (int) Math.round(R0 * 0.22 * Math.sin(k * Math.PI) * (1 - k * 0.4));
        }
        if (big && since < 0.9) {
            float k = (float) (since / 0.9), a = fade * (1 - k) * (1 - k);
            int rr = R0 + (int) (k * R0 * 2.4);
            for (int deg = 0; deg < 360; deg += 2) {
                double rad = Math.toRadians(deg);
                c.fill(cx + (int) Math.round(Math.cos(rad) * rr), cy + (int) Math.round(Math.sin(rad) * rr),
                    cx + (int) Math.round(Math.cos(rad) * rr) + 2, cy + (int) Math.round(Math.sin(rad) * rr) + 2,
                    Theme.alpha(Theme.mix(a1, a2, deg / 360f), a));
            }
            for (int i = 0; i < 14; i++) {
                double ang = i * Math.PI * 2 / 14 + 0.3, d = R0 + k * R0 * 2.8;
                int sx = cx + (int) Math.round(Math.cos(ang) * d), sy = cy + (int) Math.round(Math.sin(ang) * d);
                c.fill(sx - 1, sy, sx + 2, sy + 1, Theme.alpha(0xFFFFFFFF, a));
                c.fill(sx, sy - 1, sx + 1, sy + 2, Theme.alpha(0xFFFFFFFF, a));
            }
        }

        // pulsing glow
        double pulse = 0.5 + 0.5 * Math.sin(t * 2.2);
        int gl = big ? 6 : 3, gs = big ? 1 : 2;
        for (int i = gl; i >= 1; i--) {
            disc(c, cx, cy, R + i * gs + 1, Theme.alpha(a1, fade * (0.018f + 0.02f * (float) pulse) * (gl + 1 - i) / (float) gl));
        }

        // summer: rotating sun rays behind the ring
        if (big && Season.current == Season.SUMMER) {
            for (int k = 0; k < 16; k++) {
                double ang = t * 0.35 + k * Math.PI / 8, len = R * (k % 2 == 0 ? 0.42 : 0.24);
                for (double d = R + 3; d < R + 3 + len; d += 1.5) {
                    int px = cx + (int) Math.round(Math.cos(ang) * d), py = cy + (int) Math.round(Math.sin(ang) * d);
                    c.fill(px, py, px + th, py + th, Theme.alpha(Theme.mix(a1, a2, k / 16f), fade * (1f - (float) ((d - R - 3) / len) * 0.6f)));
                }
            }
        }

        // tornado: gusts and leaves passing behind the ring
        if (big && Season.current == Season.TORNADO) {
            gust(c, cx, cy, R, fade, t, 0);
            gust(c, cx, cy, R, fade, t, 2);
            leaves(c, cx, cy, R, fade, t, false);
        }

        // body
        disc(c, cx, cy, R, Theme.alpha(Theme.BORDER, fade));
        disc(c, cx, cy, R - 1, Theme.alpha(Season.inner(), fade));

        // base ring + comet tail
        double head = (t * 150) % 360;
        for (int deg = 0; deg < 360; deg += (big ? 3 : 6)) {
            double rad = Math.toRadians(deg);
            int px = cx + (int) Math.round(Math.cos(rad) * (R - 1));
            int py = cy + (int) Math.round(Math.sin(rad) * (R - 1));
            double d = ((head - deg) % 360 + 360) % 360 / 360.0;       // 0 at head
            float k = (float) Math.max(0, 1 - d * 1.6);
            int col = Theme.mix(a1, a2, (float) (0.5 + 0.5 * Math.sin(rad * 2 + t)));
            c.fill(px, py, px + th, py + th, Theme.alpha(Theme.mix(0xFF2A2A31, col, k), fade));
        }

        // winter: a cap of snow lying on top of the ring
        if (Season.winter()) {
            int cap = big ? 3 : 2;
            for (int deg = 212; deg <= 328; deg += (big ? 2 : 4)) {
                double rad = Math.toRadians(deg), d = Math.abs(deg - 270) / 58.0;
                int thick = d < 0.35 ? cap : (d < 0.75 ? Math.max(1, cap - 1) : 1);
                int sx = cx + (int) Math.round(Math.cos(rad) * R), sy = cy + (int) Math.round(Math.sin(rad) * R) - 1;
                for (int k = 0; k < thick; k++) c.fill(sx, sy + k, sx + 1, sy + k + 1, Theme.alpha(0xFFF4FAFF, fade * (k == 0 ? 1f : 0.85f)));
            }
        }

        // orbiting dots
        if (big) {
            for (int i = 0; i < 3; i++) {
                double ang = t * 1.6 + i * Math.PI * 2 / 3;
                double rr = R * 0.62;
                int px = cx + (int) Math.round(Math.cos(ang) * rr);
                int py = cy + (int) Math.round(Math.sin(ang) * rr);
                for (int tr = 4; tr >= 0; tr--) {
                    double a = ang - tr * 0.13;
                    int tx = cx + (int) Math.round(Math.cos(a) * rr);
                    int ty = cy + (int) Math.round(Math.sin(a) * rr);
                    c.fill(tx, ty, tx + 1, ty + 1, Theme.alpha(a2, fade * (1 - tr / 5f) * 0.7f));
                }
                mote(c, px, py, fade);
            }
        } else if (R >= 6) {
            // small version (watermark): one white dot orbiting the N
            double ang = t * 2.0;
            int px = cx + (int) Math.round(Math.cos(ang) * (R - 3)), py = cy + (int) Math.round(Math.sin(ang) * (R - 3));
            c.fill(px, py, px + 1, py + 1, Theme.alpha(0xFFFFFFFF, fade));
        }

        // "N" - same letter as the icon, scaled to the size of the logo
        if (R >= 6) {
            int sw = big ? 2 : 1;
            int s = Math.max(3, (int) Math.round(R * (big ? 0.34 : 0.42)));
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

        // tornado: gusts and leaves sweeping in front of the ring
        if (big && Season.current == Season.TORNADO) {
            gust(c, cx, cy, R, fade, t, 1);
            gust(c, cx, cy, R, fade, t, 3);
            leaves(c, cx, cy, R, fade, t, true);
        }

        // halloween: a witch hat on the ring and two bats circling it
        if (big && Season.current == Season.HALLOWEEN) {
            witchHat(c, cx, cy, R, fade, t);
            for (int i = 0; i < 2; i++) {
                double ang = t * 1.1 + i * Math.PI;
                int bx = cx + (int) Math.round(Math.cos(ang) * (R + 9)), by = cy + (int) Math.round(Math.sin(ang) * (R * 0.5 + 4)) - 4;
                int fl = (int) Math.round(Math.sin(t * 11 + i * 2) * 2), col = Theme.alpha(0xFF050208, fade);
                c.fill(bx - 1, by, bx + 2, by + 2, col);
                c.fill(bx - 1, by - 1, bx, by, col);
                c.fill(bx + 1, by - 1, bx + 2, by, col);
                c.fill(bx - 6, by + fl, bx - 1, by + fl + 1, col);
                c.fill(bx + 2, by + fl, bx + 7, by + fl + 1, col);
                c.fill(bx - 4, by, bx - 1, by + 1, col);
                c.fill(bx + 2, by, bx + 5, by + 1, col);
            }
        }
    }

    /** A streak of wind sweeping across the logo: a tapering wavy tail that ends in a small curl. */
    private static void gust(DrawContext c, int cx, int cy, int R, float fade, double t, int i) {
        double u = (t * (0.5 + 0.1 * i) + i * 0.27) % 1.0;
        double span = (R + 30) * 2, y0 = cy + (i - 1.5) * R * 0.5;
        int col = i % 2 == 0 ? 0xFFE3EDF5 : 0xFF9DB3C4;
        double hx = cx - (R + 30) + u * span, hy = y0 + Math.sin(u * Math.PI * 2 + i * 1.7 + t * 0.8) * R * 0.28;
        for (int n = 0; n < 28; n++) {
            double uu = u - n * 0.016;
            if (uu < 0) break;
            double x = cx - (R + 30) + uu * span, y = y0 + Math.sin(uu * Math.PI * 2 + i * 1.7 + t * 0.8) * R * 0.28;
            float a = fade * (1f - n / 28f) * (float) Math.sin(uu * Math.PI) * 0.9f;
            int th = n < 10 ? 2 : 1;
            c.fill((int) x, (int) y, (int) x + th + 1, (int) y + th, Theme.alpha(col, a));
        }
        float ca = fade * (float) Math.sin(u * Math.PI) * 0.9f;
        for (int q = 0; q < 8; q++) {
            double ang = q * 0.85 + t * 7, rr = 0.5 + q * 0.38;
            int px = (int) Math.round(hx + Math.cos(ang) * rr), py = (int) Math.round(hy + Math.sin(ang) * rr);
            c.fill(px, py, px + 1, py + 1, Theme.alpha(col, ca * (1f - q / 10f)));
        }
    }

    /** Leaves carried around the logo by the wind; `front` picks the half passing in front of it. */
    private static void leaves(DrawContext c, int cx, int cy, int R, float fade, double t, boolean front) {
        for (int i = 0; i < 6; i++) {
            double ang = t * 2.4 + i * 1.047;
            if ((Math.sin(ang) > 0) != front) continue;
            double rr = R * (0.95 + 0.2 * Math.sin(t * 1.3 + i));
            int x = cx + (int) Math.round(Math.cos(ang) * rr * 1.2), y = cy + (int) Math.round(Math.sin(ang) * rr * 0.45 + (i - 2.5) * R * 0.2);
            int col = i % 2 == 0 ? 0xFF9DB04A : 0xFFC9D26A;
            c.fill(x, y, x + 3, y + 1, Theme.alpha(col, fade * 0.9f));
            c.fill(x + 1, y - 1, x + 2, y, Theme.alpha(col, fade * 0.9f));
            c.fill(x - 3, y, x - 1, y + 1, Theme.alpha(col, fade * 0.35f));
        }
    }

    /** A tiny ornament that orbits the logo: snowflake in winter, sparkle otherwise. */
    private static void mote(DrawContext c, int px, int py, float fade) {
        int fl = Theme.alpha(0xFFFFFFFF, fade);
        c.fill(px - 1, py, px + 2, py + 1, fl);
        c.fill(px, py - 1, px + 1, py + 2, fl);
        if (Season.winter()) {
            int d = Theme.alpha(0xFFBFE6FF, fade * 0.8f);
            c.fill(px - 2, py - 2, px - 1, py - 1, d);
            c.fill(px + 2, py - 2, px + 3, py - 1, d);
            c.fill(px - 2, py + 2, px - 1, py + 3, d);
            c.fill(px + 2, py + 2, px + 3, py + 3, d);
        } else {
            c.fill(px - 2, py, px - 1, py + 1, Theme.alpha(Season.accent(), fade * 0.8f));
            c.fill(px + 2, py, px + 3, py + 1, Theme.alpha(Season.accent(), fade * 0.8f));
        }
    }

    private static void witchHat(DrawContext c, int cx, int cy, int R, float fade, double t) {
        int top = cy - R + 1, hh = (int) (R * 1.05), bw = (int) (R * 0.95);
        double sway = Math.sin(t * 1.6) * 1.5;
        int dark = Theme.alpha(0xFF190D26, fade), band = Theme.alpha(0xFFB45CFF, fade), gold = Theme.alpha(0xFFFFC83D, fade);
        for (int row = 0; row < hh; row++) {
            double k = row / (double) hh;
            int half = (int) Math.round(1 + k * R * 0.55);
            int off = (int) Math.round(sway * (1 - k) + (1 - k) * (1 - k) * R * 0.35);
            int y = top - hh + row + 2;
            boolean isBand = row >= hh - 6 && row <= hh - 4;
            c.fill(cx - half + off, y, cx + half + 1 + off, y + 1, isBand ? band : dark);
            if (isBand && row == hh - 5) c.fill(cx + off - 1, y, cx + off + 2, y + 1, gold);
        }
        c.fill(cx - bw, top + 1, cx + bw + 1, top + 3, dark);
        c.fill(cx - bw + 1, top, cx + bw, top + 1, dark);
        c.fill(cx - bw + 1, top + 3, cx + bw, top + 4, dark);
        c.fill(cx - bw + 2, top, cx + bw - 1, top + 1, Theme.alpha(0xFFB45CFF, fade * 0.45f));
    }
}

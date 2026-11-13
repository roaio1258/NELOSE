package nelose;

import net.minecraft.client.gui.DrawContext;

/** Seasonal theme of the main menu: Halloween, Summer or Winter. Saved in the config. */
public final class Season {
    public static final String[] NAMES = {"Halloween", "Summer", "Winter", "Tornado"};
    public static final String[] DESC = {"Moonlit pumpkins", "Sunny green hills", "Aurora and snowfall", "Storm and lightning"};
    public static final int HALLOWEEN = 0, SUMMER = 1, WINTER = 2, TORNADO = 3;
    public static int current = WINTER;
    public static long changedAt = 0;   // when the theme was last switched, drives the logo animation

    private static final int[] SKY_TOP = {0xFF14082A, 0xFF2E8FE0, 0xFF040816, 0xFF0F1618};
    private static final int[] SKY_BOT = {0xFF6A2418, 0xFF9ADCF7, 0xFF1F3C63, 0xFF4C5E57};
    private static final int[][] ACC = {{0xFFFF8A1F, 0xFFB45CFF}, {0xFFFFC83D, 0xFF34D399}, {0xFFBFE6FF, 0xFF5BB8F5}, {0xFFFFE066, 0xFF7FB2E5}};

    private static final int[] HORIZON = {0xFFFF6A1F, 0xFFFFF1B8, 0xFF8EC4FF, 0xFFB5C46A};
    private static final float[] HORIZON_A = {0.30f, 0.45f, 0.34f, 0.30f};

    /** Color of the small dot in the theme selector. */
    public static int dot(int i) { return ACC[i][0]; }

    /** Fill color inside the logo ring. */
    public static int inner() { return current == HALLOWEEN ? 0xFF120820 : current == SUMMER ? 0xFF0B1A2A : current == TORNADO ? 0xFF0C1214 : 0xFF08080B; }

    public static boolean winter() { return current == WINTER; }

    /** Accent colors of the menu. Winter keeps following the color chosen in settings. */
    public static int accent()  { return current == WINTER ? Theme.accent()  : ACC[current][0]; }
    public static int accent2() { return current == WINTER ? Theme.accent2() : ACC[current][1]; }

    /** Color of the small version texts at the bottom. */
    public static int footer() { return current == WINTER ? 0xFFEAF2FF : 0xFFE6E6EC; }

    /** Filled circle. Big circles are drawn in 2-3 px bands, which is much cheaper and looks the same at this scale. */
    static void disc(DrawContext c, int cx, int cy, int r, int col) {
        if (((col >>> 24) & 0xFF) == 0) return;
        int st = r >= 26 ? 3 : r >= 10 ? 2 : 1;
        for (int dy = -r; dy <= r; dy += st) {
            int m = Math.min(r, dy + st / 2);
            int half = (int) (Math.sqrt(Math.max(0, r * r - m * m)) + 0.5);
            c.fill(cx - half, cy + dy, cx + half + 1, cy + Math.min(r + 1, dy + st), col);
        }
    }

    private static int screenW = 10000;   // set by sky(), used to skip off-screen clouds

    private static double time() { return System.currentTimeMillis() / 1000.0; }

    // ---------------------------------------------------------------- lightning / funnel helpers

    private static double rnd(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 32;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }

    private static long cycle() { return (long) (time() / 3.7); }

    /** 0..1 brightness of the current lightning strike (double flicker, then fade). */
    public static float flash() {
        double t = time() % 3.7;
        if (t < 0.07) return 1f;
        if (t < 0.13) return 0.15f;
        if (t < 0.20) return 0.9f;
        if (t < 0.55) return (float) (0.7 * (1 - (t - 0.20) / 0.35));
        return 0f;
    }

    /** One round puff of cloud: dark underside, body, and a lit top-left rim (lightning makes it brighter). */
    private static void puff(DrawContext c, int cx, int cy, int r, int base, float f, float fl) {
        r = Math.max(2, r);
        int rim = Theme.mix(Theme.mix(base, 0xFF7C8A8F, 0.50f), 0xFFDCE8FF, fl * 0.65f);
        disc(c, cx, cy, r, Theme.alpha(Theme.mix(base, 0xFF000000, 0.35f), f));
        disc(c, cx - r / 6, cy - r / 5, Math.max(1, (int) (r * 0.82)), Theme.alpha(Theme.mix(base, 0xFFDCE8FF, fl * 0.35f), f));
        disc(c, cx - r / 3, cy - r / 2, Math.max(1, (int) (r * 0.5)), Theme.alpha(rim, f * 0.75f));
        if (r >= 8) disc(c, cx - r / 2, cy - (int) (r * 0.62), Math.max(1, r / 4), Theme.alpha(Theme.mix(rim, 0xFFFFFFFF, 0.3f), f * 0.5f));
    }

    /** A cumulus built from overlapping puffs, so it has real volume. */
    private static void cloud(DrawContext c, int x, int y, int n, int rmin, int rmax, int base, float f, float fl) {
        if (x > screenW + rmax || x + (int) (n * (rmin + rmax) * 0.7) + rmax < 0) return;   // off screen
        for (int p = 0; p < n; p++) {
            int px = x + (int) (p * (rmin + rmax) * 0.7);
            int py = y + (int) Math.round(Math.sin(p * 1.9 + x * 0.05) * rmin * 0.6);
            int r = rmin + (int) ((rmax - rmin) * (0.5 + 0.5 * Math.sin(p * 2.3 + 1)));
            puff(c, px, py, r, base, f, fl);
        }
    }

    private static int funnelTop(int h) { return (int) (h * 0.26); }
    private static int funnelHalf(double k) { return 2 + (int) (26 * Math.pow(1 - k, 1.6)); }
    private static int funnelX(int w, double k, double t) { return (int) (w * 0.80) + (int) Math.round(Math.sin(k * 4 - t * 1.1) * 10 * (1 - k)); }

    private static void aurora(DrawContext c, int w, int h, float f, double t) {
        int[][] cols = {{0xFF4DFFB0, 0xFF3AA0FF}, {0xFFA78BFA, 0xFF60A5FA}, {0xFF34D399, 0xFF22D3EE}};
        for (int r = 0; r < 3; r++) {
            double base = h * (0.15 + 0.08 * r);
            for (int x = 0; x < w; x += 6) {
                int y = (int) (base + Math.sin(x * 0.011 + t * 0.35 + r * 2.1) * h * 0.05 + Math.sin(x * 0.029 + t * 0.6 + r) * h * 0.015);
                float sh = (float) (0.5 + 0.5 * Math.sin(x * 0.04 + t * 1.3 + r * 3));
                float ray = (float) (0.65 + 0.35 * Math.sin(x * 0.31 + t * 2 + r));   // vertical rays inside the curtain
                int len = 22 + (int) (14 * sh);
                float a = 0.20f * sh * ray * f * (r == 2 ? 0.6f : 1f);
                c.fillGradient(x, y, x + 6, y + len, Theme.alpha(cols[r][0], a), Theme.alpha(cols[r][1], 0f));
            }
        }
    }

    private static void winterSnow(DrawContext c, int w, int h, int count, float a) {
        if (count <= 0) return;
        count = Math.min(count, 90);
        double t = time();
        int ww = Math.max(1, w);
        for (int i = 0; i < count; i++) {
            double depth = 0.35 + (i * 31 % 10) / 10.0 * 0.65;
            double x = ((i * 7919L) % ww) + Math.sin(t * 0.7 + i) * 14 * depth + t * 5 * depth;   // gentle wind to the right
            x = ((x % ww) + ww) % ww;
            double y = (((i * 104729L) % Math.max(1, h)) + t * 22 * depth) % h;
            float pa = a * (float) depth * (float) Math.min(1.0, y / (h * 0.16));   // appear out of the glowing sky
            pa *= (float) Math.min(1.0, (h - y) / (h * 0.10));                       // melt away near the ground
            pa *= (float) (0.8 + 0.2 * Math.sin(t * 3 + i));                          // sparkle
            int xi = (int) x, yi = (int) y;
            if (depth > 0.9 && i % 3 == 0) {
                int fl = Theme.alpha(0xFFFFFFFF, pa), d = Theme.alpha(0xFFDCEBFF, pa * 0.6f);   // crystal
                c.fill(xi - 3, yi - 3, xi + 4, yi + 4, Theme.alpha(0xFFBFE6FF, pa * 0.10f));
                c.fill(xi - 2, yi, xi + 3, yi + 1, fl);
                c.fill(xi, yi - 2, xi + 1, yi + 3, fl);
                c.fill(xi - 1, yi - 1, xi, yi, d); c.fill(xi + 1, yi - 1, xi + 2, yi, d);
                c.fill(xi - 1, yi + 1, xi, yi + 2, d); c.fill(xi + 1, yi + 1, xi + 2, yi + 2, d);
            } else if (depth > 0.9) {
                c.fill(xi - 2, yi - 2, xi + 3, yi + 3, Theme.alpha(0xFFBFE6FF, pa * 0.12f));
                c.fill(xi - 1, yi, xi + 2, yi + 1, Theme.alpha(0xFFFFFFFF, pa));
                c.fill(xi, yi - 1, xi + 1, yi + 2, Theme.alpha(0xFFFFFFFF, pa));
            } else {
                int s = depth > 0.75 ? 2 : 1;
                if (s == 2) c.fill(xi - 1, yi - 1, xi + 3, yi + 3, Theme.alpha(0xFFBFE6FF, pa * 0.14f));
                c.fill(xi, yi, xi + s, yi + s, Theme.alpha(0xFFFFFFFF, pa));
            }
        }
    }

    /** Jagged snowy mountain range: dark body with a bright snow cap on the high points. */
    private static void mountain(DrawContext c, int w, int base, double amp, double freq, double phase, int body, float f) {
        for (int x = 0; x < w; x += 4) {
            double ridge = Math.abs(Math.sin(x * freq + phase)) * 0.62 + Math.abs(Math.sin(x * freq * 2.7 + phase * 1.3)) * 0.38;
            int hy = (int) (base - amp * (0.25 + 0.75 * ridge));
            c.fill(x, hy, x + 4, base + 2, Theme.alpha(body, f));
            if (ridge > 0.55) {
                int cap = 1 + (int) ((ridge - 0.55) * amp * 0.9);
                c.fill(x, hy, x + 4, hy + cap, Theme.alpha(0xFFE3F0FF, f * 0.85f));
                c.fill(x, hy + cap, x + 4, hy + cap + 1, Theme.alpha(0xFFAFCDEE, f * 0.45f));
            }
        }
    }

    private static void cabin(DrawContext c, int x, int base, float f, double t) {
        for (int i = 0; i < 5; i++) {       // chimney smoke
            double k = (t * 0.22 + i / 5.0) % 1.0;
            int sx = x + 20 + (int) Math.round(Math.sin(k * 5 + i) * 3 + k * 9), sy = base - 29 - (int) (k * 28);
            disc(c, sx, sy, 2 + (int) (k * 3), Theme.alpha(0xFFB8C8DC, f * 0.22f * (1f - (float) k)));
        }
        int dark = Theme.alpha(0xFF0D0F16, f);
        c.fill(x + 18, base - 28, x + 22, base - 17, dark);
        c.fill(x, base - 13, x + 26, base + 1, Theme.alpha(0xFF1A1310, f));
        for (int r = 0; r < 10; r++) {      // roof with snow along both slopes
            c.fill(x - 3 + r, base - 23 + r, x + 29 - r, base - 22 + r, dark);
            c.fill(x - 3 + r, base - 24 + r, x - 3 + r + 4, base - 23 + r, Theme.alpha(0xFFE3F0FF, f * 0.9f));
            c.fill(x + 29 - r - 4, base - 24 + r, x + 29 - r, base - 23 + r, Theme.alpha(0xFFE3F0FF, f * 0.9f));
        }
        float flick = (float) (0.85 + 0.15 * Math.sin(t * 5));
        disc(c, x + 8, base - 7, 8, Theme.alpha(0xFFFFB84D, f * 0.10f * flick));     // warm light spilling from the window
        c.fill(x + 5, base - 10, x + 11, base - 4, Theme.alpha(0xFFFFC96B, f * flick));
        c.fill(x + 7, base - 10, x + 8, base - 4, Theme.alpha(0xFF6B4A1E, f));
        c.fill(x + 5, base - 7, x + 11, base - 6, Theme.alpha(0xFF6B4A1E, f));
        c.fill(x + 16, base - 9, x + 21, base + 1, Theme.alpha(0xFF2A1C12, f));
    }

    private static void tornadoParticles(DrawContext c, int w, int h, int count, float a) {
        if (count <= 0) return;
        double t = time();
        int y0 = funnelTop(h), n = h - h / 7 - y0;
        for (int i = 0; i < count; i++) {     // rain
            double depth = 0.4 + (i * 31 % 10) / 10.0 * 0.6;
            int x = (int) ((i * 7919L) % Math.max(1, w)), y = (int) ((((i * 104729L) % Math.max(1, h)) + t * 240 * depth) % h);
            int col = Theme.alpha(0xFFB8D0E8, a * (float) depth * 0.45f);
            c.fill(x, y, x + 1, y + 3, col);
            c.fill(x - 1, y + 3, x, y + 5, col);
        }
        for (int i = 0; i < count / 2 + 4; i++) {   // debris whirling around the funnel
            double dep = 0.4 + (i * 17 % 10) / 10.0 * 0.6;
            double k = 1 - ((i * 0.6180339 + t * 0.10 * dep) % 1.0);
            double ang = t * (3 + dep * 3) + i * 1.7;
            double rad = funnelHalf(k) * (1.0 + 0.5 * dep) + 3;
            int x = funnelX(w, k, t) + (int) Math.round(Math.cos(ang) * rad);
            int y = y0 + (int) (k * n) + (int) Math.round(Math.sin(ang) * 3);
            float da = a * (Math.sin(ang) < 0 ? 0.5f : 1f);
            int col = i % 3 == 0 ? 0xFF8A7B55 : 0xFF3A3F36;
            c.fill(x, y, x + 2, y + 2, Theme.alpha(col, da));
        }
    }

    // ---------------------------------------------------------------- sky

    public static void sky(DrawContext c, int w, int h) {
        screenW = w;
        c.fillGradient(0, 0, w, h, SKY_TOP[current], SKY_BOT[current]);
        // glow along the horizon
        int top = h - h / 7, span = Math.max(20, h / 4);
        c.fillGradient(0, top - span, w, top, Theme.alpha(HORIZON[current], 0f), Theme.alpha(HORIZON[current], HORIZON_A[current]));
    }

    /** Sky colours, used by the loading screen. */
    public static int skyTop() { return SKY_TOP[current]; }
    public static int skyBot() { return SKY_BOT[current]; }

    // ---------------------------------------------------------------- silhouettes

    private static void hill(DrawContext c, int w, int base, double amp, double freq, double phase, int col, float f) {
        for (int x = 0; x < w; x += 4) {
            int hy = (int) (base - amp * 0.5 - amp * 0.5 * Math.sin(x * freq + phase) - amp * 0.25 * Math.sin(x * freq * 2.3 + phase * 1.7));
            c.fill(x, hy, x + 4, base + 2, Theme.alpha(col, f));
        }
    }

    private static void pine(DrawContext c, int cx, int base, int ph, int col, float f) {
        int tierH = Math.max(3, ph / 3);
        for (int row = 0; row < ph; row++) {
            int tier = row / tierH, local = row % tierH;
            int half = (int) (2 + tier * 2.2 + local * 0.55 * (1 + tier * 0.3));
            int y = base - ph + row;
            c.fill(cx - half, y, cx + half + 1, y + 1, Theme.alpha(col, f));
            if (local < 2) c.fill(cx - half + 1, y, cx + half, y + 1, Theme.alpha(0xFFE3F0FF, f * 0.55f));          // snow on top of every tier
            else if (local == tierH - 1) {                                                                          // frosty tips
                c.fill(cx - half, y, cx - half + 2, y + 1, Theme.alpha(0xFFE3F0FF, f * 0.40f));
                c.fill(cx + half - 1, y, cx + half + 1, y + 1, Theme.alpha(0xFFE3F0FF, f * 0.40f));
            }
        }
        c.fill(cx - 1, base, cx + 2, base + 3, Theme.alpha(col, f));
    }

    private static void deadTree(DrawContext c, int cx, int base, int th, int col, float f) {
        int k = Theme.alpha(col, f);
        c.fill(cx - 1, base - th, cx + 2, base + 2, k);
        for (int b = 0; b < 4; b++) {
            int dir = b % 2 == 0 ? -1 : 1, by = base - th + 4 + b * th / 5, len = 7 + (3 - b) * 3;
            for (int j = 0; j < len; j++) c.fill(cx + dir * j, by - j / 2, cx + dir * j + 1, by - j / 2 + 2, k);
        }
    }

    private static void roundTree(DrawContext c, int cx, int base, int r, int col, float f) {
        c.fill(cx - 1, base - r - 2, cx + 1, base + 2, Theme.alpha(0xFF5A3A1E, f));
        disc(c, cx, base - r - 3, r, Theme.alpha(col, f));
    }

    /** Hills and trees standing behind the ground line. */
    public static void skyline(DrawContext c, int w, int h, float f) {
        int top = h - h / 7;
        if (current == WINTER) {
            mountain(c, w, top, 70, 0.008, 0.6, 0xFF1D3459, f * 0.9f);        // far peaks
            mountain(c, w, top, 46, 0.013, 2.3, 0xFF132444, f);               // near peaks
            hill(c, w, top, 30, 0.012, 0.0, 0xFF0C1830, f);
            for (int i = 0; i < 5; i++) pine(c, (int) (w * (0.30 + i * 0.10)) + (i * 13) % 9, top - 3, 12 + (i * 7) % 6, 0xFF0A1528, f * 0.85f);   // small trees in the distance
            hill(c, w, top, 18, 0.020, 2.0, 0xFF081224, f);
            if (w >= 300) cabin(c, (int) (w * 0.24), top - 2, f, time());
            float[] pos = {0.04f, 0.11f, 0.18f, 0.82f, 0.89f, 0.96f};
            int[] ph = {40, 30, 24, 28, 42, 32};
            for (int i = 0; i < pos.length; i++) pine(c, (int) (w * pos[i]), top - 2, ph[i], 0xFF060E1B, f);
        } else if (current == HALLOWEEN) {
            hill(c, w, top, 30, 0.014, 0.5, 0xFF1A0C22, f);
            hill(c, w, top, 18, 0.022, 2.5, 0xFF0E0612, f);
            deadTree(c, (int) (w * 0.06), top - 2, 46, 0xFF08040C, f);
            deadTree(c, (int) (w * 0.94), top - 2, 38, 0xFF08040C, f);
        } else if (current == TORNADO) {
            double t = time();
            hill(c, w, top, 22, 0.012, 0.8, 0xFF1B2420, f);
            int y0 = funnelTop(h), n = top - y0;
            for (int r = 0; r < n; r += 2) {       // the funnel, shaded like a cylinder with a turbulent edge
                double k = r / (double) n;
                int half = funnelHalf(k) + (int) Math.round(Math.sin(r * 0.35 - t * 4) * 1.5 * (1 - k));
                int cx = funnelX(w, k, t), y = y0 + r, x0 = cx - half, fw = half * 2 + 1;
                float wave = (float) (0.5 + 0.5 * Math.sin(r * 0.55 - t * 9));
                for (int b = 0; b < 4; b++) {
                    float shade = Math.max(0f, Math.min(1f, 0.62f - 0.2f * b + 0.22f * wave));
                    c.fill(x0 + fw * b / 4, y, x0 + fw * (b + 1) / 4, y + 2, Theme.alpha(Theme.mix(0xFF161C20, 0xFF727F85, shade), f * 0.96f));
                }
                c.fill(x0 - 1, y, x0 + 1, y + 2, Theme.alpha(0xFF0B0F11, f * 0.7f));
                c.fill(x0 + fw - 1, y, x0 + fw + 1, y + 2, Theme.alpha(0xFF0B0F11, f * 0.7f));
            }
            for (int i = 0; i < 7; i++) {       // dust churned up at the base
                double ang = t * 2.2 + i * 0.9;
                int dx = funnelX(w, 1.0, t) + (int) Math.round(Math.cos(ang) * (8 + i * 3)), dy = top - 3 + (int) Math.round(Math.sin(ang) * 2);
                puff(c, dx, dy, 6 + i % 3 * 2, 0xFF5B5E4D, f * 0.75f, 0f);
            }
            float fl = flash();
            int fxTop = funnelX(w, 0, t);       // wall cloud on top of the funnel, with hanging bumps
            for (int i = 0; i < 8; i++) puff(c, fxTop - 56 + i * 16, y0 - 8 + (int) Math.round(Math.sin(i * 1.7) * 3), 15 + (i * 5) % 7, 0xFF141A1D, f, fl);
            for (int i = 0; i < 5; i++) puff(c, fxTop - 40 + i * 20, y0 + 8 + (i % 2) * 3, 5, 0xFF1A2124, f * 0.9f, fl);
            hill(c, w, top, 14, 0.022, 2.5, 0xFF10160F, f);
            int bx = (int) (w * 0.12);     // lonely barn
            c.fill(bx, top - 12, bx + 18, top + 1, Theme.alpha(0xFF0B0F0C, f));
            for (int r = 0; r < 8; r++) c.fill(bx - 2 + r, top - 20 + r, bx + 20 - r, top - 19 + r, Theme.alpha(0xFF0B0F0C, f));
        } else {
            hill(c, w, top, 30, 0.010, 0.0, 0xFF6DBF8B, f);
            hill(c, w, top, 18, 0.018, 1.5, 0xFF4FAE62, f);
            float[] pos = {0.07f, 0.15f, 0.84f, 0.93f};
            int[] r = {7, 5, 6, 8};
            for (int i = 0; i < pos.length; i++) roundTree(c, (int) (w * pos[i]), top - 2, r[i], 0xFF2F8F4E, f);
        }
    }

    /** Soft dark edges so the menu in the middle stands out. */
    public static void vignette(DrawContext c, int w, int h, float f) {
        int n = 26, e = 0x00000000, d = Theme.alpha(0xFF000000, 0.28f * f);
        c.fillGradient(0, 0, w, n, d, e);
        c.fillGradient(0, h - n, w, h, e, d);
        for (int i = 0; i < n; i += 2) {      // left / right edges in 2 px bands
            int col = Theme.alpha(0xFF000000, 0.28f * (1f - i / (float) n) * (1f - i / (float) n) * f);
            c.fill(i, n, i + 2, h - n, col);
            c.fill(w - i - 2, n, w - i, h - n, col);
        }
        if (current == WINTER) {      // cold blue frost creeping in from the edges
            int fr = Theme.alpha(0xFF9CC8FF, 0.07f * f);
            c.fillGradient(0, 0, w, 18, fr, Theme.alpha(0xFF9CC8FF, 0f));
            c.fillGradient(0, 0, 18, h, fr, Theme.alpha(0xFF9CC8FF, 0f));
        }
        if (current == TORNADO) {
            float fl = flash();
            if (fl > 0.01f) c.fill(0, 0, w, h, Theme.alpha(0xFFDCE8FF, 0.20f * fl * f));
        }
    }

    /** Things in the sky: moon and stars, sun and clouds. Winter has nothing extra. */
    public static void backdrop(DrawContext c, int w, int h, float f) {
        double t = time();
        if (current == HALLOWEEN) {
            for (int i = 0; i < 40; i++) {
                int x = (int) ((i * 7919L) % Math.max(1, w));
                int y = (int) ((i * 104729L) % Math.max(1, (int) (h * 0.55)));
                float tw = (float) (0.35 + 0.65 * Math.abs(Math.sin(t * 1.2 + i)));
                c.fill(x, y, x + 1, y + 1, Theme.alpha(0xFFFFE9C4, f * tw * 0.8f));
            }
            int mx = (int) (w * 0.82), my = (int) (h * 0.2), r = Math.max(14, Math.min(30, h / 12));
            for (int i = 4; i >= 1; i--) disc(c, mx, my, r + i * 10, Theme.alpha(0xFFFF9A2E, f * 0.03f));
            disc(c, mx, my, r, Theme.alpha(0xFFFFE2A8, f));
            disc(c, mx - r / 3, my - r / 4, Math.max(2, r / 5), Theme.alpha(0xFFE9C27F, f));
            disc(c, mx + r / 3, my + r / 3, Math.max(2, r / 4), Theme.alpha(0xFFE9C27F, f));
        } else if (current == WINTER) {
            for (int i = 0; i < 70; i++) {
                int x = (int) ((i * 7919L) % Math.max(1, w)), y = (int) ((i * 104729L) % Math.max(1, (int) (h * 0.5)));
                float tw = (float) (0.3 + 0.7 * Math.abs(Math.sin(t * 1.1 + i)));
                c.fill(x, y, x + 1, y + 1, Theme.alpha(0xFFDCEBFF, f * tw * 0.75f));
                if (i % 9 == 0) {      // a few brighter stars with a cross
                    c.fill(x - 1, y, x + 2, y + 1, Theme.alpha(0xFFFFFFFF, f * tw * 0.35f));
                    c.fill(x, y - 1, x + 1, y + 2, Theme.alpha(0xFFFFFFFF, f * tw * 0.35f));
                }
            }
            int mx = (int) (w * 0.80), my = (int) (h * 0.17), mr = Math.max(7, Math.min(16, h / 16));
            for (int i = 3; i >= 1; i--) disc(c, mx, my, mr + i * 8, Theme.alpha(0xFFBFE6FF, f * 0.04f));
            disc(c, mx, my, mr, Theme.alpha(0xFFEAF4FF, f));
            disc(c, mx - mr / 3, my - mr / 4, Math.max(1, mr / 5), Theme.alpha(0xFFC4D8F0, f));
            disc(c, mx + mr / 3, my + mr / 3, Math.max(1, mr / 4), Theme.alpha(0xFFC4D8F0, f));
            aurora(c, w, h, f, t);
            long sn = (long) (t / 7.0);      // a shooting star every few seconds
            double sp = (t % 7.0) / 0.9;
            if (sp < 1.0) {
                double sx0 = w * (0.10 + 0.55 * rnd(sn)), sy0 = h * (0.04 + 0.12 * rnd(sn + 1));
                float fade = (float) Math.sin(sp * Math.PI);
                for (int k = 0; k < 16; k++) {
                    int px = (int) (sx0 + sp * 90 - k * 2.4), py = (int) (sy0 + sp * 36 - k * 0.95);
                    c.fill(px, py, px + 1, py + 1, Theme.alpha(0xFFFFFFFF, f * fade * (1f - k / 16f)));
                }
            }
            c.fillGradient(0, 0, w, (int) (h * 0.22), Theme.alpha(0xFFBFE6FF, 0.10f * f), Theme.alpha(0xFFBFE6FF, 0f));   // glowing sky the snow falls out of
            for (int i = 0; i < 5; i++) {       // thin drifting cloud streaks
                int bw = 100 + (i * 41) % 70, span = w + bw, x = (int) ((t * (1.5 + i * 0.4) + i * span / 5.0) % span) - bw;
                int y = (int) (h * (0.26 + 0.045 * i));
                Theme.round(c, x, y, bw, 5, 2, Theme.alpha(0xFF9DBCE6, 0.06f * f));
                Theme.round(c, x + bw / 5, y + 3, bw * 3 / 5, 3, 1, Theme.alpha(0xFFB8D6F6, 0.05f * f));
            }
        } else if (current == TORNADO) {
            float fl = flash();
            int sh = (int) (h * 0.45);
            c.fillGradient(0, 0, w, sh, Theme.alpha(0xFF070B0D, 0.9f * f), Theme.alpha(0xFF070B0D, 0f));
            for (int i = 0; i < 4; i++) {       // far, pale clouds near the horizon
                int span = w + 160, x = (int) ((t * 2 + i * span / 4.0) % span) - 80, y = (int) (h * 0.30) + (i % 3) * 8;
                cloud(c, x, y, 4, 5, 9, 0xFF39443F, f * 0.9f, fl);
            }
            if (fl > 0.05f) {       // lightning bolt, drawn behind the near clouds so it comes out of them
                long cy = cycle();
                int x = (int) (w * (0.08 + 0.55 * rnd(cy))), y = 0, top = h - h / 7;
                while (y < top - 4) {
                    int seg = 6 + (int) (rnd(cy * 31 + y) * 8), dx = (int) Math.signum(rnd(cy * 17 + y * 3) - 0.5);
                    for (int j = 0; j < seg && y < top; j++, y++) {
                        if (j % 2 == 0) x += dx;
                        c.fill(x - 2, y, x + 4, y + 1, Theme.alpha(0xFFBFD8FF, f * fl * 0.18f));
                        c.fill(x, y, x + 2, y + 1, Theme.alpha(0xFFFFFFFF, f * fl));
                    }
                }
            }
            for (int i = 0; i < 4; i++) {       // mid layer
                int span = w + 300, x = (int) ((t * (3 + i % 3) + i * span / 4.0) % span) - 150, y = (int) (h * 0.12) + (i % 3) * (int) (h * 0.05);
                cloud(c, x, y, 6, 9, 14, 0xFF1F282B, f, fl);
            }
            for (int i = 0; i < 3; i++) {       // heavy near layer at the top
                int span = w + 360, x = (int) ((t * 4 + i * span / 3.0) % span) - 180, y = (int) (h * 0.02) + (i % 2) * 8;
                cloud(c, x, y, 5, 16, 22, 0xFF11171A, f, fl);
            }
        } else if (current == SUMMER) {
            int sx = (int) (w * 0.16), sy = (int) (h * 0.2), r = Math.max(12, Math.min(26, h / 14));
            for (int i = 4; i >= 1; i--) disc(c, sx, sy, r + i * 17, Theme.alpha(0xFFFFF3B0, f * 0.08f));
            for (int k = 0; k < 12; k++) {
                double ang = t * 0.25 + k * Math.PI / 6;
                for (int d = r + 5; d <= r + 14; d += 2) {
                    int px = sx + (int) Math.round(Math.cos(ang) * d), py = sy + (int) Math.round(Math.sin(ang) * d);
                    c.fill(px, py, px + 2, py + 2, Theme.alpha(0xFFFFF6C8, f * 0.8f));
                }
            }
            disc(c, sx, sy, r, Theme.alpha(0xFFFFF6C8, f));
            for (int i = 0; i < 5; i++) {
                double span = w + 220;
                int x = (int) ((t * (6 + i * 2) + i * span / 5) % span) - 110;
                int y = (int) (h * 0.1) + (i % 3) * 28 + i * 6;
                int cw = 70 + (i * 17) % 40;
                int col = Theme.alpha(0xFFFFFFFF, f * 0.85f);
                Theme.round(c, x, y + 8, cw, 14, 7, col);
                Theme.round(c, x + 12, y, cw / 2, 16, 8, col);
                Theme.round(c, x + cw / 2 - 4, y + 3, cw / 3, 14, 7, col);
            }
        }
    }

    // ---------------------------------------------------------------- ground

    public static void ground(DrawContext c, int w, int h, float f) {
        double t = time();
        int gh = h / 7, top = h - gh;
        if (current == WINTER) {
            c.fillGradient(0, top, w, top + gh, 0xFFB5CFEA, 0xFF3E5F8A);
            for (int x = 0; x < w; x += 4) {      // soft drifts along the horizon
                int d = 2 + (int) Math.round(Math.sin(x * 0.03) * 2 + Math.sin(x * 0.09 + 1) * 1.2);
                c.fill(x, top - d, x + 4, top, Theme.alpha(0xFFC9DDF3, f));
                c.fill(x, top - d, x + 4, top - d + 1, Theme.alpha(0xFFFFFFFF, 0.6f * f));
            }
            for (int i = 0; i < 26; i++) {        // ice glints
                int gx = (int) ((i * 7919L) % Math.max(1, w)), gy = top + 3 + (int) ((i * 104729L) % Math.max(1, gh - 4));
                float k = (float) Math.max(0, Math.sin(t * 1.6 + i * 2.3));
                c.fill(gx - 1, gy, gx + 2, gy + 1, Theme.alpha(0xFFFFFFFF, 0.55f * k * k * f));
                c.fill(gx, gy - 1, gx + 1, gy + 2, Theme.alpha(0xFFFFFFFF, 0.55f * k * k * f));
            }
        } else if (current == HALLOWEEN) {
            c.fillGradient(0, top, w, top + gh, 0xFF1C0F1C, 0xFF07030A);
            c.fill(0, top, w, top + 1, Theme.alpha(0xFFFF8A1F, 0.35f));
            float[] pos = {0.10f, 0.27f, 0.74f, 0.91f};
            int[] size = {13, 9, 11, 16};
            for (int i = 0; i < pos.length; i++) pumpkin(c, (int) (w * pos[i]), top + size[i] / 3, size[i], t, i, f);
        } else if (current == TORNADO) {
            c.fillGradient(0, top, w, top + gh, 0xFF222C18, 0xFF0A0F08);
            c.fill(0, top, w, top + 1, Theme.alpha(0xFFB5C46A, 0.25f));
            for (int x = 0; x < w; x += 4) {      // wheat bent by the wind
                int hh = 4 + (x * 7) % 5, lean = (int) Math.round(Math.sin(t * 3 + x * 0.2) * 2 + 3);
                c.fill(x, top - hh, x + 1, top, 0xFF3A4A22);
                c.fill(x + lean, top - hh - 1, x + lean + 1, top - hh + 1, 0xFF8A7B3A);
            }
        } else {
            c.fillGradient(0, top, w, top + gh, 0xFF4CAF50, 0xFF2E7D32);
            c.fill(0, top, w, top + 2, 0xFF7ED957);
            for (int x = 0; x < w; x += 5) {
                int hh = 3 + (x * 13) % 5;
                int sway = (int) Math.round(Math.sin(t * 1.5 + x * 0.1));
                c.fill(x + sway, top - hh, x + sway + 1, top, 0xFF3E9E45);
            }
        }
    }

    private static void pumpkin(DrawContext c, int cx, int cy, int r, double t, int i, float f) {
        int ry = Math.max(3, (int) (r * 0.8));
        float flick = (float) (0.75 + 0.25 * Math.sin(t * 6 + i * 2));
        disc(c, cx, cy, r + 5, Theme.alpha(0xFFFF8A1F, f * 0.05f * flick));
        for (int dy = -ry; dy <= ry; dy++) {
            double k = dy / (double) ry;
            int half = (int) Math.round(r * Math.sqrt(Math.max(0, 1 - k * k)));
            int col = Theme.alpha(Theme.mix(0xFFFF9A2E, 0xFFC24E0E, (dy + ry) / (2f * ry)), f);
            c.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, col);
            if (half > r / 3 + 1) {
                int rib = Theme.alpha(0xFFA8400A, f * 0.7f);
                c.fill(cx - r / 3, cy + dy, cx - r / 3 + 1, cy + dy + 1, rib);
                c.fill(cx + r / 3, cy + dy, cx + r / 3 + 1, cy + dy + 1, rib);
            }
        }
        c.fill(cx - 1, cy - ry - 3, cx + 2, cy - ry + 1, Theme.alpha(0xFF3F5A1E, f));
        int eye = Theme.alpha(0xFFFFE066, f * flick);
        int ex = Math.max(1, r / 2), ey = cy - r / 4;
        for (int k = 0; k < 3; k++) {
            c.fill(cx - ex - k, ey + k, cx - ex + k + 1, ey + k + 1, eye);
            c.fill(cx + ex - k, ey + k, cx + ex + k + 1, ey + k + 1, eye);
        }
        int my = cy + r / 4;
        c.fill(cx - ex, my, cx + ex + 1, my + 2, eye);
        for (int tx = 1; tx < 2 * ex; tx += 3) c.fill(cx - ex + tx, my, cx - ex + tx + 1, my + 1, Theme.alpha(0xFFC24E0E, f));
    }

    // ---------------------------------------------------------------- particles

    /** Falling snow, rising embers with bats, or drifting fireflies. */
    public static void particles(DrawContext c, int w, int h, int count, float a) {
        double t = time();
        if (current == WINTER) { winterSnow(c, w, h, count, a); return; }
        if (current == TORNADO) { tornadoParticles(c, w, h, count, a); return; }
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            double depth = 0.35 + (i * 31 % 10) / 10.0 * 0.65;
            double tw = 0.5 + 0.5 * Math.sin(t * 3 + i);
            if (current == HALLOWEEN) {
                double x = ((i * 7919L) % Math.max(1, w)) + Math.sin(t * 0.8 + i) * 10 * depth;
                double y = h - (((i * 104729L) % Math.max(1, h)) + t * 20 * depth) % h;
                int s = depth > 0.75 ? 2 : 1;
                int col = i % 3 == 0 ? 0xFFFFD27A : 0xFFFF8A1F;
                float pa = a * (float) (depth * (0.4 + 0.6 * tw));
                if (s == 2) c.fill((int) x - 1, (int) y - 1, (int) x + 3, (int) y + 3, Theme.alpha(col, pa * 0.2f));
                c.fill((int) x, (int) y, (int) x + s, (int) y + s, Theme.alpha(col, pa));
            } else {
                double x = ((i * 7919L) % Math.max(1, w)) + Math.sin(t * 0.5 + i) * 20;
                double y = ((i * 104729L) % Math.max(1, h)) + Math.cos(t * 0.4 + i * 1.3) * 14;
                float pa = a * (float) (0.25 + 0.75 * tw);
                c.fill((int) x - 1, (int) y - 1, (int) x + 3, (int) y + 3, Theme.alpha(0xFFFFF7B0, pa * 0.18f));
                c.fill((int) x, (int) y, (int) x + 2, (int) y + 2, Theme.alpha(0xFFFFF7B0, pa));
            }
        }
        if (current == HALLOWEEN) bats(c, w, h, a, t);
    }

    private static void bats(DrawContext c, int w, int h, float f, double t) {
        for (int i = 0; i < 6; i++) {
            double span = w + 60;
            int bx = (int) ((t * (14 + (i * 5) % 9) + i * span / 6) % span) - 30;
            int by = (int) (h * 0.12 + (i % 3) * h * 0.07 + Math.sin(t * 1.3 + i) * 8);
            int fl = (int) Math.round(Math.sin(t * 10 + i * 1.7) * 2);
            int col = Theme.alpha(0xFF050208, f);
            c.fill(bx - 1, by, bx + 2, by + 2, col);
            c.fill(bx - 1, by - 1, bx, by, col);
            c.fill(bx + 1, by - 1, bx + 2, by, col);
            c.fill(bx - 3, by, bx - 1, by + 1, col);
            c.fill(bx + 2, by, bx + 4, by + 1, col);
            c.fill(bx - 7, by + fl, bx - 3, by + fl + 1, col);
            c.fill(bx + 4, by + fl, bx + 8, by + fl + 1, col);
        }
    }

    /** Live miniature of a theme, used by the theme picker. Draws the real scene scaled down and clipped to the box. */
    public static void preview(DrawContext c, int x, int y, int w, int h, int theme, float f) {
        final int VW = 320, VH = 180;
        float s = Math.max(w / (float) VW, h / (float) VH);
        int old = current;
        current = theme;
        try {
            c.enableScissor(x, y, x + w, y + h);
            c.getMatrices().pushMatrix();
            c.getMatrices().translate((float) x, (float) y);
            c.getMatrices().scale(s, s);
            sky(c, VW, VH);
            backdrop(c, VW, VH, f);
            skyline(c, VW, VH, f);
            ground(c, VW, VH, f);
            particles(c, VW, VH, 12, 0.9f * f);
            c.getMatrices().popMatrix();
            c.disableScissor();
        } finally {
            current = old;
        }
    }

    private Season() {}
}

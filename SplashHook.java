package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashOverlay;

/**
 * Nelose loading screen. No mixin needed: the vanilla Mojang splash is wrapped once at startup.
 * The vanilla overlay keeps running underneath (it finishes the resource reload and opens the menu),
 * we only paint our own picture over it.
 */
public final class SplashHook {
    private static boolean wrapped;

    /** Called at startup and every tick until the splash has been wrapped. */
    public static void tryWrap(MinecraftClient mc) {
        if (wrapped || mc == null) return;
        Overlay o = mc.getOverlay();
        if (o instanceof SplashOverlay so) {
            wrapped = true;
            mc.setOverlay(new Wrapper(so));
        } else if (o == null && mc.currentScreen != null) {
            wrapped = true;    // too late, the game is already loaded
        }
    }

    private static final class Wrapper extends Overlay {
        private final SplashOverlay inner;
        private final long start = System.currentTimeMillis();
        private long doneAt;

        Wrapper(SplashOverlay inner) { this.inner = inner; }

        @Override
        public void render(DrawContext c, int mx, int my, float delta) {
            try { inner.render(c, mx, my, delta); } catch (RuntimeException e) { throw e; }
            MinecraftClient mc = MinecraftClient.getInstance();
            long now = System.currentTimeMillis();
            if (doneAt == 0 && mc.currentScreen != null) doneAt = now;   // reload finished, the menu is open
            float a = 1f;
            if (doneAt != 0) a = 1f - Math.max(0f, Math.min(1f, (now - doneAt) / 1000f - 1f));   // same timing as vanilla
            if (a <= 0.01f) return;
            paint(c, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), a, (now - start) / 1000.0);
        }
    }

    // 5x7 pixel letters
    private static final String[][] GLYPH = {
        {"10001", "11001", "10101", "10101", "10011", "10001", "10001"},   // N
        {"11111", "10000", "10000", "11110", "10000", "10000", "11111"},   // E
        {"10000", "10000", "10000", "10000", "10000", "10000", "11111"},   // L
        {"01110", "10001", "10001", "10001", "10001", "10001", "01110"},   // O
        {"01111", "10000", "10000", "01110", "00001", "00001", "11110"},   // S
        {"11111", "10000", "10000", "11110", "10000", "10000", "11111"}    // E
    };

    /** Only plain fills here: fonts and textures are not loaded yet while the game starts. */
    static void paint(DrawContext c, int w, int h, float a, double t) {
        int a1 = Season.accent(), a2 = Season.accent2();
        c.fillGradient(0, 0, w, h, Theme.alpha(Season.skyTop(), a), Theme.alpha(Season.skyBot(), a));
        int cx = w / 2, cy = h / 2 - 22;
        for (int i = 4; i >= 1; i--) Season.disc(c, cx, cy, 30 + i * 10, Theme.alpha(a1, a * 0.035f));
        Avatar.draw(c, cx, cy, 30, a, 0, false);

        int px = Math.max(2, Math.min(4, h / 120)), lw = 6 * px, total = 6 * lw - px;   // letters
        int x0 = cx - total / 2, y0 = cy + 46;
        for (int i = 0; i < 6; i++) {
            float k = i / 5f;
            float sh = (float) Math.max(0, 1 - Math.abs(k - ((t % 2.4) / 2.4 * 1.6 - 0.3)) * 3.5);
            int col = Theme.mix(Theme.mix(0xFFEDEDF2, Theme.mix(a1, a2, k), 0.45f), 0xFFFFFFFF, sh * 0.9f);
            for (int r = 0; r < 7; r++)
                for (int q = 0; q < 5; q++)
                    if (GLYPH[i][r].charAt(q) == '1')
                        c.fill(x0 + i * lw + q * px, y0 + r * px, x0 + i * lw + (q + 1) * px, y0 + (r + 1) * px, Theme.alpha(col, a));
        }

        int bw = 160, bh = 3, bx = cx - bw / 2, by = y0 + 7 * px + 14;     // moving loading bar
        c.fill(bx, by, bx + bw, by + bh, Theme.alpha(0xFF26262D, a));
        double p = (t * 0.55) % 1.0;
        int seg = 56, sx = bx + (int) ((bw + seg) * p) - seg;
        int l = Math.max(bx, sx), r2 = Math.min(bx + bw, sx + seg);
        if (r2 > l) Theme.gradBar(c, l, by, r2 - l, bh, a1, a2, seg, a);
    }

    private SplashHook() {}
}

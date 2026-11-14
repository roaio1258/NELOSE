package nelose;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

/**
 * Game start animation: a glowing "N" fades in over a starry sky, then turns into "NELOSE",
 * and the screen fades into the main menu. Any key or click skips it. Shown once per launch.
 */
public class IntroScreen extends Screen {
    private static final String WORD = "NELOSE VISUAL";
    private static final int WORD_GLYPH_W = 400;           // design width used for scaling

    // 5x7 pixel letters, drawn with plain fills (no textures, so nothing can come up as a missing-texture checkerboard)
    private static final java.util.Map<Character, String[]> GLYPH = new java.util.HashMap<>();
    static {
        GLYPH.put('N', new String[]{"10001", "11001", "10101", "10101", "10011", "10001", "10001"});
        GLYPH.put('E', new String[]{"11111", "10000", "10000", "11110", "10000", "10000", "11111"});
        GLYPH.put('L', new String[]{"10000", "10000", "10000", "10000", "10000", "10000", "11111"});
        GLYPH.put('O', new String[]{"01110", "10001", "10001", "10001", "10001", "10001", "01110"});
        GLYPH.put('S', new String[]{"01111", "10000", "10000", "01110", "00001", "00001", "11110"});
        GLYPH.put('V', new String[]{"10001", "10001", "10001", "10001", "10001", "01010", "00100"});
        GLYPH.put('I', new String[]{"11111", "00100", "00100", "00100", "00100", "00100", "11111"});
        GLYPH.put('U', new String[]{"10001", "10001", "10001", "10001", "10001", "10001", "01110"});
        GLYPH.put('A', new String[]{"01110", "10001", "10001", "11111", "10001", "10001", "10001"});
    }

    // timeline, seconds
    private static final double T_SKY = 0.6, T_N_IN = 0.5, T_N_OUT = 2.2, T_WORD = 2.7, T_WORD_DONE = 3.7, T_FADE = 5.2, T_END = 5.8;

    public static boolean shown;
    private long start;
    private boolean leaving;

    public IntroScreen() { super(Text.literal("Nelose")); }

    /** True when the intro should run now (once per launch, when enabled). */
    public static boolean wanted() { return !shown && Modules.BLUR.b("Intro"); }

    @Override protected void init() { if (start == 0) start = System.currentTimeMillis(); }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(DrawContext c, int mx, int my, float d) {}
    @Override public boolean keyPressed(KeyInput input) { finish(); return true; }
    @Override public boolean mouseClicked(Click click, boolean doubled) { finish(); return true; }

    private void finish() {
        if (leaving) return;
        leaving = true;
        shown = true;
        if (client == null) return;
        if (Modules.BLUR.b("Title screen")) client.setScreen(new NeloseTitleScreen());
        else client.setScreen(new TitleScreen());
    }

    private static float ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return (float) (x * x * (3 - 2 * x));
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        if (t >= T_END) { finish(); return; }
        int a1 = Season.accent(), a2 = Season.accent2();

        float sky = ease(t / T_SKY);
        float out = 1f - ease((t - T_FADE) / (T_END - T_FADE));          // final fade to black
        c.fill(0, 0, width, height, 0xFF000000);
        Season.sky(c, width, height);
        Season.backdrop(c, width, height, sky);
        stars(c, t, sky);
        Season.particles(c, width, height, 50, 0.7f * sky);
        Season.vignette(c, width, height, sky);
        // everything above is revealed from black
        if (sky < 1f) c.fill(0, 0, width, height, Theme.alpha(0xFF000000, 1f - sky));

        int cx = width / 2, cy = height / 2 - 8;
        float s = Math.min(height / 300f, width * 0.5f / WORD_GLYPH_W);

        float nIn = ease((t - T_N_IN) / 0.9);
        float nOut = ease((t - T_N_OUT) / 0.5);
        float nA = nIn * (1f - nOut);
        if (nA > 0.01f) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * 2.6));
            float k = (0.72f + 0.28f * nIn) * (1f - 0.22f * nOut) * (1f + 0.02f * pulse);
            halo(c, cx, cy, (int) (46 * s * k), a1, nA, pulse);
            int npx = Math.max(4, (int) (s * k * 9f));                     // size of one big "N" pixel
            drawText(c, "N", cx - (5 * npx) / 2, cy - (7 * npx) / 2, npx, nA, 99f, a1, a2, t);
            for (int i = 0; i < 12; i++) {                                  // sparks orbiting the N
                double an = t * 1.3 + i * Math.PI * 2 / 12;
                int ox = cx + (int) (Math.cos(an) * npx * 5.2), oy = cy + (int) (Math.sin(an) * npx * 5.2);
                int sz = i % 3 == 0 ? 2 : 1;
                c.fill(ox, oy, ox + sz, oy + sz, Theme.alpha(Theme.mix(a1, a2, i / 11f), nA * 0.85f));
            }
        }

        float wp = ease((t - T_WORD) / (T_WORD_DONE - T_WORD));
        if (wp > 0.001f) {
            float breathe = (float) (0.5 + 0.5 * Math.sin(t * 2.0));
            halo(c, cx, cy, (int) (66 * s), a2, 0.55f * wp, breathe);
            int wpx = Math.max(2, Math.min((int) (WORD_GLYPH_W * s * 1.5f / 75f), (int) (width * 0.9f / 75f)));   // 75 columns in "NELOSE VISUAL"
            int totalW = 75 * wpx;
            float reveal = 80f * wp;                        // columns shown so far, left -> right
            drawText(c, WORD, cx - totalW / 2, cy - (7 * wpx) / 2, wpx, Math.min(1f, wp * 1.6f), reveal, a1, a2, t);
        }

        // thin accent line under the logo, grows with the word
        if (wp > 0.01f) {
            int lw = (int) (width * 0.9f * Math.min(1f, WORD_GLYPH_W * s * 1.5f / Math.max(1f, width * 0.9f)) * wp), ly = cy + (int) (7 * Math.max(2, Math.min((int) (WORD_GLYPH_W * s * 1.5f / 75f), (int) (width * 0.9f / 75f))) / 2 + 12);
            Theme.gradBar(c, cx - lw / 2, ly, lw, 1, a1, a2, Math.max(1, lw), 0.7f * wp);
        }
        if (t > 0.9) {
            String hint = "Press any key to skip";
            c.drawCenteredTextWithShadow(textRenderer, hint, cx, height - 14, Theme.alpha(Theme.TEXT_DIM, 0.45f * Math.min(1f, (float) (t - 0.9)) * out));
        }
        if (out < 1f) c.fill(0, 0, width, height, Theme.alpha(0xFF000000, 1f - out));
    }

    /** Draws pixel text; only the first {@code revealCols} columns are visible, so the text appears left to right. */
    private void drawText(DrawContext c, String text, int x0, int y0, int px, float alpha, float revealCols, int a1, int a2, double t) {
        int col = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            String[] g = GLYPH.get(text.charAt(i));
            if (g == null) { col += 3; continue; }                         // space
            float k = n <= 1 ? 0.5f : i / (float) (n - 1);
            for (int r = 0; r < 7; r++) {
                float rowK = r / 6f;
                int base = Theme.mix(Theme.mix(0xFFEAF2FF, a1, 0.35f + 0.35f * rowK), a2, 0.25f * k);
                float sh = (float) Math.max(0, 1 - Math.abs(k - ((t % 2.4) / 2.4 * 1.6 - 0.3)) * 3.5);
                int colr = Theme.mix(base, 0xFFFFFFFF, sh * 0.6f);
                for (int q = 0; q < 5; q++) {
                    if (g[r].charAt(q) != '1') continue;
                    float ca = Math.min(1f, (revealCols - (col + q)) / 5f);   // soft left-to-right edge
                    if (ca <= 0f) continue;
                    float al = alpha * ca;
                    int x = x0 + (col + q) * px, y = y0 + r * px;
                    c.fill(x - 1, y - 1, x + px + 1, y + px + 1, Theme.alpha(a1, al * 0.22f));   // soft glow
                    c.fill(x, y, x + px, y + px, Theme.alpha(colr, al));
                }
            }
            col += 6;
        }
    }

    /** Soft coloured glow behind the logo, made of a few translucent discs. */
    private void halo(DrawContext c, int cx, int cy, int r, int col, float a, float pulse) {
        for (int i = 5; i >= 1; i--)
            Season.disc(c, cx, cy, r + i * 9, Theme.alpha(col, a * (0.012f + 0.012f * pulse) * (6 - i)));
    }

    private void stars(DrawContext c, double t, float f) {
        for (int i = 0; i < 110; i++) {
            int x = (int) ((i * 7919L + 13) % Math.max(1, width));
            int y = (int) ((i * 104729L + 7) % Math.max(1, (int) (height * 0.78)));
            float tw = (float) (0.3 + 0.7 * Math.abs(Math.sin(t * (0.7 + (i % 5) * 0.25) + i)));
            int col = Theme.alpha(i % 7 == 0 ? Season.accent() : 0xFFDDE8FF, f * tw * (i % 3 == 0 ? 0.9f : 0.5f));
            c.fill(x, y, x + 1, y + 1, col);
            if (i % 11 == 0) { c.fill(x - 1, y, x + 2, y + 1, Theme.alpha(col, 0.35f)); c.fill(x, y - 1, x + 1, y + 2, Theme.alpha(col, 0.35f)); }
        }
    }
}

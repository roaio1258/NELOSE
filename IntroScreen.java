package nelose;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Game start animation: a glowing "N" fades in over a starry sky, then turns into "NELOSE",
 * and the screen fades into the main menu. Any key or click skips it. Shown once per launch.
 */
public class IntroScreen extends Screen {
    private static final Identifier N_TEX = Identifier.of("nelose", "textures/intro/n.png");
    private static final Identifier WORD_TEX = Identifier.of("nelose", "textures/intro/nelose.png");
    private static final int NW = 192, NH = 192, WW = 768, WH = 192;
    private static final int WORD_GLYPH_W = 400;           // visible width of the word inside its texture

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
            draw(c, N_TEX, NW, NH, NW, cx, cy, s * k, nA);
        }

        float wp = ease((t - T_WORD) / (T_WORD_DONE - T_WORD));
        if (wp > 0.001f) {
            float breathe = (float) (0.5 + 0.5 * Math.sin(t * 2.0));
            halo(c, cx, cy, (int) (66 * s), a2, 0.55f * wp, breathe);
            int vis = (int) (WW * (0.5 - WORD_GLYPH_W / 2.0 / WW + wp * WORD_GLYPH_W / (double) WW) + 0.5);   // reveal left -> right
            int from = (WW - WORD_GLYPH_W) / 2 - 12;
            int w = Math.max(1, Math.min(WW, vis - from + 12));
            drawPart(c, WORD_TEX, WW, WH, from, w, cx, cy, s, Math.min(1f, wp * 1.6f));
        }

        // thin accent line under the logo, grows with the word
        if (wp > 0.01f) {
            int lw = (int) (WORD_GLYPH_W * s * 0.9f * wp), ly = cy + (int) (46 * s);
            Theme.gradBar(c, cx - lw / 2, ly, lw, 1, a1, a2, Math.max(1, lw), 0.7f * wp);
        }
        if (t > 0.9) {
            String hint = "Press any key to skip";
            c.drawCenteredTextWithShadow(textRenderer, hint, cx, height - 14, Theme.alpha(Theme.TEXT_DIM, 0.45f * Math.min(1f, (float) (t - 0.9)) * out));
        }
        if (out < 1f) c.fill(0, 0, width, height, Theme.alpha(0xFF000000, 1f - out));
    }

    private void draw(DrawContext c, Identifier id, int tw, int th, int w, int cx, int cy, float scale, float alpha) {
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(cx, cy);
        c.getMatrices().scale(scale, scale);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, -tw / 2, -th / 2, 0f, 0f, tw, th, tw, th, Theme.alpha(0xFFFFFFFF, alpha));
        c.getMatrices().popMatrix();
    }

    /** Draws only columns [from, from + w) of the texture, so the word appears from left to right. */
    private void drawPart(DrawContext c, Identifier id, int tw, int th, int from, int w, int cx, int cy, float scale, float alpha) {
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(cx, cy);
        c.getMatrices().scale(scale, scale);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, -tw / 2 + from, -th / 2, (float) from, 0f, w, th, tw, th, Theme.alpha(0xFFFFFFFF, alpha));
        c.getMatrices().popMatrix();
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

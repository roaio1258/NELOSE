package nelose;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cinematic intro shown once per launch, before the main menu.
 * Timeline: a big N fades in at the centre and glides into its slot while the rest of
 * "NELOSEVISUAL" rises letter by letter. The full word holds with a colour shimmer, a soft halo
 * and an expanding gradient line with a subtitle, then everything sinks down and fades into the menu.
 * Any key or click skips straight to the exit animation.
 */
public class NeloseIntroScreen extends Screen {
    private static final String WORD = "NELOSEVISUAL";
    private static final String SUB = "V I S U A L   C L I E N T";

    // timeline in seconds
    private static final float N_IN      = 0.00f;   // big N fades in
    private static final float N_IN_LEN  = 0.85f;
    private static final float MOVE      = 0.95f;   // N glides to its slot
    private static final float MOVE_LEN  = 0.85f;
    private static final float LETTERS   = 1.15f;   // the other letters appear
    private static final float LET_STEP  = 0.07f;
    private static final float LET_LEN   = 0.45f;
    private static final float DONE_AT   = LETTERS + (WORD.length() - 1) * LET_STEP + LET_LEN;
    private static final float EXIT      = 3.70f;   // everything drifts down and fades
    private static final float EXIT_LEN  = 1.00f;
    private static final float END       = EXIT + EXIT_LEN + 0.05f;

    private static boolean played;

    private final long opened = System.currentTimeMillis();
    private long skipAt = -1;   // when the user skipped, remap time so the exit stays smooth

    public static boolean shouldPlay() { return !played; }

    public NeloseIntroScreen() { super(Text.literal("NeloseVisual")); played = true; }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(DrawContext c, int mx, int my, float d) {}

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float easeOutCubic(float t) { t = clamp01(t); float u = 1f - t; return 1f - u * u * u; }
    private static float easeInCubic(float t) { t = clamp01(t); return t * t * t; }
    private static float easeInOut(float t) { t = clamp01(t); return t * t * (3f - 2f * t); }
    /** Ease out with a small overshoot, for the big N landing. */
    private static float easeOutBack(float t) {
        t = clamp01(t);
        float s = 1.35f, u = t - 1f;
        return 1f + u * u * ((s + 1f) * u + s);
    }

    /** Three-stop colour across the word: accent, accent2, and a warm pink tail. */
    private static int ramp(int a1, int a2, float k) {
        int pink = 0xFFFF7AD9;
        return k < 0.6f ? Theme.mix(a1, a2, k / 0.6f) : Theme.mix(a2, pink, (k - 0.6f) / 0.4f);
    }

    private float time() {
        float t = (System.currentTimeMillis() - opened) / 1000f;
        if (skipAt >= 0) {
            float ts = (skipAt - opened) / 1000f;
            t = Math.min(ts, EXIT) + (t - ts);   // jump the timeline to the exit phase
        }
        return t;
    }

    private void skip() { if (skipAt < 0 && time() < EXIT) skipAt = System.currentTimeMillis(); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        float t = time();
        if (t >= END) { client.setScreen(new NeloseTitleScreen()); return; }

        int a1 = Season.accent(), a2 = Season.accent2();
        float exit = clamp01((t - EXIT) / EXIT_LEN);
        float fade = 1f - easeInCubic(exit);                         // global alpha
        float drop = easeInCubic(exit) * (height * 0.45f + 40f);     // everything sinks down

        // background: deep gradient tinted by the theme, soft aura that breathes, drifting particles
        int bgTop = Theme.mix(0xFF06060A, a1, 0.07f);
        int bgBot = Theme.mix(0xFF0A0A14, a2, 0.10f);
        c.fillGradient(0, 0, width, height, Theme.alpha(bgTop, fade), Theme.alpha(bgBot, fade));
        int cx = width / 2, cy = height / 2;
        float breathe = 0.6f + 0.4f * (float) Math.sin(t * 1.4);
        for (int i = 8; i >= 1; i--) {
            int r = 22 + i * 18;
            c.fill(cx - r * 2, cy - r, cx + r * 2, cy + r, Theme.alpha(Theme.mix(a1, a2, i / 8f), 0.018f * breathe * fade));
        }
        Season.particles(c, width, height, 80, 0.85f * fade);

        // logo layout
        int ww = wordWidth();
        float s = Math.min(3.2f, Math.max(1.5f, (width - 60) / (float) ww));   // word scale
        float x0 = cx - ww * s / 2f;
        float baseY = cy - textRenderer.fontHeight * s / 2f - 6f + drop;

        float nIn   = easeOutBack((t - N_IN) / N_IN_LEN);            // big N entrance
        float nMove = easeInOut((t - MOVE) / MOVE_LEN);              // N travels to its slot
        float shimmer = ((t % 2.6f) / 2.6f) * 1.9f - 0.45f;          // highlight sweep
        float waveFrom = DONE_AT;

        int pen = 0;
        for (int i = 0; i < WORD.length(); i++) {
            String ch = WORD.substring(i, i + 1);
            int chw = textRenderer.getWidth(ch) + 1;
            float k = i / (float) (WORD.length() - 1);

            float la;    // letter alpha
            float lx, ly, ls;
            if (i == 0) {
                // the N: born big in the centre, then glides into the word
                float bigS = s * 2.6f;
                float slotX = x0 + pen * s;
                la = clamp01((t - N_IN) / N_IN_LEN);
                ls = bigS + (s - bigS) * nMove;
                if (t < MOVE) ls = bigS * (1f + (1f - nIn) * 0.6f);   // settle while alone
                float bigX = cx - (chw - 1) * ls / 2f;
                float bigY = cy - textRenderer.fontHeight * ls / 2f + drop;
                lx = bigX + (slotX - bigX) * nMove;
                ly = bigY + (baseY - bigY) * nMove;
            } else {
                float st = LETTERS + (i - 1) * LET_STEP;
                float p = easeOutCubic((t - st) / LET_LEN);
                la = clamp01((t - st) / LET_LEN);
                ls = s;
                lx = x0 + pen * s;
                ly = baseY + (1f - p) * 18f;   // rises into place
            }

            // gentle wave once the word is complete
            if (t > waveFrom && t < EXIT) ly += (float) Math.sin(t * 2.2 + i * 0.55) * 1.5f;

            float sh = Math.max(0f, 1f - Math.abs(k - shimmer) * 3.2f);
            int base = Theme.mix(ramp(a1, a2, k), 0xFFFFFFFF, sh * 0.85f);
            float alpha = la * fade;
            if (alpha > 0.02f) {
                // halo: the same glyph drawn four times in a soft tint behind the main one
                int halo = Theme.alpha(ramp(a1, a2, k), 0.35f * alpha);
                int[][] offs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                for (int[] o : offs) {
                    c.getMatrices().pushMatrix();
                    c.getMatrices().translate(lx + o[0] * ls * 1.5f, ly + o[1] * ls * 1.5f);
                    c.getMatrices().scale(ls, ls);
                    c.drawText(textRenderer, ch, 0, 0, halo, false);
                    c.getMatrices().popMatrix();
                }
                // main glyph with a dark drop shadow
                c.getMatrices().pushMatrix();
                c.getMatrices().translate(lx, ly);
                c.getMatrices().scale(ls, ls);
                c.drawText(textRenderer, ch, 1, 1, Theme.alpha(0xFF000000, 0.55f * alpha), false);
                c.drawText(textRenderer, ch, 0, 0, Theme.alpha(base, alpha), false);
                c.getMatrices().popMatrix();
            }
            pen += chw;
        }

        // gradient line and subtitle once the word is assembled
        float ua = easeOutCubic((t - DONE_AT) / 0.6f) * fade;
        if (ua > 0.02f) {
            int lw = (int) (ww * s * ua);
            int uy = (int) (baseY + textRenderer.fontHeight * s + 10f);
            // soft glow under the line
            Theme.gradBar(c, cx - lw / 2 - 2, uy - 2, lw + 4, 6, a1, a2, Math.max(1, lw), ua * 0.18f);
            Theme.gradBar(c, cx - lw / 2, uy, lw, 2, a1, a2, Math.max(1, lw), ua);
            int subCol = Theme.mix(a2, 0xFFFFFFFF, 0.35f);
            c.drawText(textRenderer, SUB, cx - textRenderer.getWidth(SUB) / 2, uy + 9, Theme.alpha(subCol, ua * 0.95f), false);
        }

        // skip hint
        if (t > 1.2f && t < EXIT) {
            float ha = clamp01((t - 1.2f) / 0.6f) * (0.35f + 0.25f * (float) Math.sin(t * 2.5)) * fade;
            String hint = "Press any key to skip";
            c.drawText(textRenderer, hint, cx - textRenderer.getWidth(hint) / 2, height - 18, Theme.alpha(Theme.TEXT_DIM, ha), false);
        }

        Season.vignette(c, width, height, fade);
    }

    private int wordWidth() {
        int w = -1;
        for (int i = 0; i < WORD.length(); i++) w += textRenderer.getWidth(WORD.substring(i, i + 1)) + 1;
        return w;
    }

    @Override
    public boolean keyPressed(KeyInput input) { skip(); return true; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) { skip(); return true; }
}

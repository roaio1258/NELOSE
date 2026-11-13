package nelose;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

/** Main menu in the Nelose style. The theme cards at the top show a live preview of each theme and switch it on click. */
public class NeloseTitleScreen extends Screen {
    private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Options", "Nelose settings", "Quit"};
    private static final int BW = 170, BH = 24, GAP = 6;
    private static final int CW = 88, CG = 8, CY = 10;          // theme cards row at the top
    private static final int PW = CW - 8, PH = PW * 9 / 16;       // live preview inside a card (16:9)
    private static final int CH = PH + 22;                        // preview + name line
    private final float[] hover = new float[LABELS.length];
    private final float[] themeHover = new float[Season.NAMES.length];
    private long opened;

    public NeloseTitleScreen() { super(Text.literal("Nelose")); }

    @Override
    protected void init() { opened = System.currentTimeMillis(); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {}

    private int bx() { return (width - BW) / 2; }
    private int by(int i) { return height / 2 - 6 + i * (BH + GAP); }
    private int themesX0() { int n = Season.NAMES.length; return (width - (n * CW + (n - 1) * CG)) / 2; }

    private void select(int i) {
        i = (i + Season.NAMES.length) % Season.NAMES.length;
        if (Season.current != i) { Season.current = i; Season.changedAt = System.currentTimeMillis(); Config.save(); }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        float f = Math.min(1f, (System.currentTimeMillis() - opened) / 600f);
        f = 1f - (1f - f) * (1f - f);
        int a1 = Season.accent(), a2 = Season.accent2();

        Season.sky(c, width, height);
        Season.backdrop(c, width, height, f);
        int gy = height / 2 - 62;
        for (int i = 40; i >= 4; i -= 4) {
            c.fill(width / 2 - i * 5, gy - i * 2, width / 2 + i * 5, gy + i * 2, Theme.alpha(a1, 0.024f * f));
        }
        Season.skyline(c, width, height, f);
        Season.ground(c, width, height, f);
        Season.particles(c, width, height, (int) Modules.BLUR.f("Title snow"), 0.9f * f);
        Season.vignette(c, width, height, f);
        float sw = Math.min(1f, (System.currentTimeMillis() - Season.changedAt) / 350f);   // fade in after a theme change
        if (sw < 1f) c.fill(0, 0, width, height, Theme.alpha(0xFF000000, 0.55f * (1f - sw) * (1f - sw)));

        // logo + name
        int ly = height / 2 - 62 + (int) ((1f - f) * 14);
        Avatar.draw(c, width / 2, ly, 30, f, Modules.LOGO.idx("Model"), Season.winter() && Modules.LOGO.b("Blizzard"));
        String name = "NELOSE";
        int tw = -1;
        for (int i = 0; i < name.length(); i++) tw += textRenderer.getWidth(name.substring(i, i + 1)) + 1;
        int nw = tw * 2;
        float sweep = (System.currentTimeMillis() % 2600L) / 2600f * 1.6f - 0.3f;   // highlight running through the letters
        int penX = 0;
        for (int i = 0; i < name.length(); i++) {
            String ch = name.substring(i, i + 1);
            float k = i / (float) (name.length() - 1);
            float sh = Math.max(0f, 1f - Math.abs(k - sweep) * 3.5f);
            int col = Theme.mix(Theme.mix(0xFFEDEDF2, Theme.mix(a1, a2, k), 0.45f), 0xFFFFFFFF, sh * 0.9f);
            c.getMatrices().pushMatrix();
            c.getMatrices().translate(width / 2f - nw / 2f + penX * 2f, ly + 38);
            c.getMatrices().scale(2f, 2f);
            c.drawTextWithShadow(textRenderer, ch, 0, 0, Theme.alpha(col, f));
            c.getMatrices().popMatrix();
            penX += textRenderer.getWidth(ch) + 1;
        }
        for (int i = 0; i < nw; i++) c.fill(width / 2 - nw / 2 + i, ly + 58, width / 2 - nw / 2 + i + 1, ly + 59, Theme.alpha(Theme.mix(a1, a2, i / (float) nw), f));

        // buttons
        for (int i = 0; i < LABELS.length; i++) {
            float h = hover[i];
            int ex = (int) (h * 5), w = BW + ex * 2;
            int x = bx() - ex, y = by(i) + (int) ((1f - f) * 10 * (i + 1));
            boolean hov = mx >= bx() && mx <= bx() + BW && my >= y && my <= y + BH;
            hover[i] += ((hov ? 1f : 0f) - hover[i]) * 0.25f;
            if (h > 0.05f) Theme.glow(c, x, y, w, BH, 5, Theme.alpha(a1, h), 4);
            Theme.round(c, x, y, w, BH, 5, Theme.alpha(Theme.mix(Theme.BORDER, a1, h), f));
            Theme.roundGrad(c, x + 1, y + 1, w - 2, BH - 2, 4,
                Theme.mix(0xFF22222A, 0xFF2C2C36, h), Theme.mix(0xFF141418, 0xFF1C1C24, h), f);
            c.fill(x + 5, y + 1, x + w - 5, y + 2, Theme.alpha(0xFFFFFFFF, f * (0.05f + 0.07f * h)));   // top highlight
            if (h > 0.05f) {   // accent bar on the left grows on hover
                int bh = (int) ((BH - 8) * h);
                for (int k = 0; k < bh; k++) c.fill(x + 4, y + (BH - bh) / 2 + k, x + 6, y + (BH - bh) / 2 + k + 1,
                    Theme.alpha(Theme.mix(a1, a2, k / (float) Math.max(1, bh)), f * h));
            }
            c.drawCenteredTextWithShadow(textRenderer, LABELS[i], width / 2, y + (BH - 8) / 2,
                Theme.alpha(Theme.mix(0xFFB4B4C0, 0xFFFFFFFF, h), f));
            if (h > 0.05f) c.drawTextWithShadow(textRenderer, ">", x + w - 14, y + (BH - 8) / 2, Theme.alpha(a1, f * h));
        }

        renderThemes(c, mx, my, f);

        c.drawText(textRenderer, "Minecraft 1.21.11", 6, height - 12, Theme.alpha(Season.footer(), f), false);
        String r = "Nelose v1.5";
        c.drawText(textRenderer, r, width - 6 - textRenderer.getWidth(r), height - 12, Theme.alpha(Season.footer(), f), false);
    }

    /** Row of theme cards at the top: live preview, name, and a highlighted border on the active theme. */
    private void renderThemes(DrawContext c, int mx, int my, float f) {
        int x0 = themesX0();
        for (int i = 0; i < Season.NAMES.length; i++) {
            int x = x0 + i * (CW + CG), y = CY + (int) ((1f - f) * -10 * (i + 1));
            boolean hov = mx >= x && mx <= x + CW && my >= CY && my <= CY + CH;
            themeHover[i] += ((hov ? 1f : 0f) - themeHover[i]) * 0.25f;
            float h = themeHover[i];
            boolean sel = i == Season.current;
            int dot = Season.dot(i);

            if (sel) Theme.glow(c, x, y, CW, CH, 5, Theme.alpha(dot, f), 4);
            else if (h > 0.05f) Theme.glow(c, x, y, CW, CH, 5, Theme.alpha(dot, h * f * 0.7f), 3);
            Theme.round(c, x, y, CW, CH, 5, Theme.alpha(sel ? dot : Theme.mix(Theme.BORDER, dot, h * 0.8f), f));
            Theme.round(c, x + 1, y + 1, CW - 2, CH - 2, 4, Theme.alpha(Theme.mix(0xFF14141A, 0xFF1C1C24, h), f));

            Season.preview(c, x + 4, y + 4, PW, PH, i, f);
            if (!sel) c.fill(x + 4, y + 4, x + 4 + PW, y + 4 + PH, Theme.alpha(0xFF000000, 0.30f * (1f - h) * f));

            String label = Season.NAMES[i];
            int ny = y + 4 + PH + 5;
            Theme.round(c, x + 6, ny + 1, 5, 5, 2, Theme.alpha(dot, f));
            c.drawTextWithShadow(textRenderer, label, x + 14, ny, Theme.alpha(sel ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, 0.6f + 0.4f * h), f));
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_LEFT) { select(Season.current - 1); return true; }
        if (k == GLFW.GLFW_KEY_RIGHT) { select(Season.current + 1); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
        int x0 = themesX0();
        for (int i = 0; i < Season.NAMES.length; i++) {
            int x = x0 + i * (CW + CG);
            if (mx >= x && mx <= x + CW && my >= CY && my <= CY + CH) { select(i); return true; }
        }
        for (int i = 0; i < LABELS.length; i++) {
            if (mx < bx() || mx > bx() + BW || my < by(i) || my > by(i) + BH) continue;
            switch (i) {
                case 0 -> client.setScreen(new SelectWorldScreen(this));
                case 1 -> client.setScreen(new MultiplayerScreen(this));
                case 2 -> client.setScreen(new OptionsScreen(this, client.options));
                case 3 -> client.setScreen(new NeloseScreen());
                default -> client.scheduleStop();
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }
}

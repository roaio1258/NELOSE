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

/** Main menu in the Nelose style. The theme button at the top opens a gallery with live previews of every theme. */
public class NeloseTitleScreen extends Screen {
    private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Options", "Nelose settings", "Quit"};
    private static final int BW = 170, BH = 24, GAP = 6;
    private static final int TBW = 124, TBH = 18, TBY = 8;     // "Theme" button at the top
    private static final int GAPC = 8;                          // gap between cards in the picker
    private final float[] hover = new float[LABELS.length];
    private final float[] card = new float[Season.NAMES.length];
    private float tbHover, menuAnim, doneHover, closeHover;
    private boolean menuOpen;
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

    // ---- theme picker layout
    private int cols() { return width >= 4 * 104 + 3 * GAPC + 44 ? 4 : 2; }
    private int cw() { int n = cols(); return Math.min(132, (width - 44 - (n - 1) * GAPC) / n); }
    private int pvw() { return cw() - 6; }
    private int pvh() { return pvw() * 9 / 16; }
    private int chh() { return pvh() + 32; }
    private int rows() { return (Season.NAMES.length + cols() - 1) / cols(); }
    private int mw() { return cols() * cw() + (cols() - 1) * GAPC + 24; }
    private int mh() { return 36 + rows() * chh() + (rows() - 1) * GAPC + 34; }
    private int mx0() { return (width - mw()) / 2; }
    private int my0() { return Math.max(4, (height - mh()) / 2); }
    private int cardX(int i) { return mx0() + 12 + (i % cols()) * (cw() + GAPC); }
    private int cardY(int i) { return my0() + 36 + (i / cols()) * (chh() + GAPC); }

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
        for (int i = 40; i >= 1; i--) {
            c.fill(width / 2 - i * 5, gy - i * 2, width / 2 + i * 5, gy + i * 2, Theme.alpha(a1, 0.006f * f));
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
            boolean hov = !menuOpen && mx >= bx() && mx <= bx() + BW && my >= y && my <= y + BH;
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
        // theme button
        int tbx = (width - TBW) / 2;
        boolean tbh = !menuOpen && mx >= tbx && mx <= tbx + TBW && my >= TBY && my <= TBY + TBH;
        tbHover += ((tbh || menuOpen ? 1f : 0f) - tbHover) * 0.25f;
        int curDot = Season.dot(Season.current);
        if (tbHover > 0.05f) Theme.glow(c, tbx, TBY, TBW, TBH, 5, Theme.alpha(curDot, tbHover * 0.8f), 3);
        Theme.round(c, tbx, TBY, TBW, TBH, 5, Theme.alpha(Theme.mix(Theme.BORDER, curDot, 0.35f + 0.65f * tbHover), f));
        Theme.roundGrad(c, tbx + 1, TBY + 1, TBW - 2, TBH - 2, 4,
            Theme.mix(0xFF1B1B22, curDot, 0.10f + 0.10f * tbHover), Theme.mix(0xFF101015, curDot, 0.04f + 0.04f * tbHover), f);
        Theme.round(c, tbx + 8, TBY + (TBH - 6) / 2, 6, 6, 3, Theme.alpha(curDot, f));
        c.drawTextWithShadow(textRenderer, "Theme: " + Season.NAMES[Season.current], tbx + 20, TBY + (TBH - 8) / 2, Theme.alpha(Theme.TEXT, f));
        c.drawTextWithShadow(textRenderer, menuOpen ? "^" : "v", tbx + TBW - 13, TBY + (TBH - 8) / 2, Theme.alpha(Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, tbHover), f));
        c.drawText(textRenderer, "Minecraft 1.21.11", 6, height - 12, Theme.alpha(Season.footer(), f), false);
        String r = "Nelose v1.5";
        c.drawText(textRenderer, r, width - 6 - textRenderer.getWidth(r), height - 12, Theme.alpha(Season.footer(), f), false);

        menuAnim += ((menuOpen ? 1f : 0f) - menuAnim) * 0.25f;
        if (!menuOpen && menuAnim < 0.02f) menuAnim = 0f;
        if (menuAnim > 0.01f) renderPicker(c, mx, my, menuAnim);
    }

    private void renderPicker(DrawContext c, int mx, int my, float a) {
        int a1 = Season.accent(), a2 = Season.accent2();
        c.fill(0, 0, width, height, Theme.alpha(0xFF000000, 0.62f * a));
        int x0 = mx0(), y0 = my0() + (int) ((1f - a) * 16), pw = mw(), ph = mh();
        Theme.glow(c, x0, y0, pw, ph, 8, Theme.alpha(a1, a), 6);
        Theme.card(c, x0, y0, pw, ph, 8, Theme.alpha(Theme.PANEL, a), Theme.alpha(Theme.BORDER, a));

        // header
        c.drawTextWithShadow(textRenderer, "Choose theme", x0 + 12, y0 + 11, Theme.alpha(Theme.TEXT, a));
        String cur = Season.NAMES[Season.current];
        c.drawText(textRenderer, cur, x0 + pw - 36 - textRenderer.getWidth(cur), y0 + 11, Theme.alpha(Season.dot(Season.current), a), false);
        Theme.gradBar(c, x0 + 12, y0 + 26, pw - 24, 1, a1, a2, pw - 24, a);
        int cx = x0 + pw - 24, cy = y0 + 7;
        boolean ch = mx >= cx && mx <= cx + 16 && my >= cy && my <= cy + 14;
        closeHover += ((ch ? 1f : 0f) - closeHover) * 0.25f;
        Theme.round(c, cx, cy, 16, 14, 4, Theme.alpha(Theme.mix(0xFF1B1B22, 0xFF3A2024, closeHover), a));
        c.drawCenteredTextWithShadow(textRenderer, "x", cx + 8, cy + 3, Theme.alpha(Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, closeHover), a));

        // cards
        int cw = cw(), pvw = pvw(), pvh = pvh(), chh = chh();
        for (int i = 0; i < Season.NAMES.length; i++) {
            int x = cardX(i), y = cardY(i) + (int) ((1f - a) * 8 * (i + 1));
            boolean hov = mx >= x && mx <= x + cw && my >= y && my <= y + chh;
            boolean sel = i == Season.current;
            card[i] += ((hov ? 1f : 0f) - card[i]) * 0.25f;
            int dot = Season.dot(i);
            if (sel) Theme.glow(c, x, y, cw, chh, 5, Theme.alpha(dot, a), 4);
            else if (card[i] > 0.05f) Theme.glow(c, x, y, cw, chh, 5, Theme.alpha(dot, card[i] * a * 0.7f), 3);
            Theme.round(c, x, y, cw, chh, 5, Theme.alpha(sel ? dot : Theme.mix(Theme.BORDER, dot, card[i] * 0.8f), a));
            Theme.round(c, x + 1, y + 1, cw - 2, chh - 2, 4, Theme.alpha(Theme.mix(0xFF14141A, 0xFF1C1C24, card[i]), a));

            int px = x + 3, py = y + 3;
            Season.preview(c, px, py, pvw, pvh, i, 1f);                                    // live miniature
            if (!sel) c.fill(px, py, px + pvw, py + pvh, Theme.alpha(0xFF000000, 0.30f * (1f - card[i])));
            if (a < 1f) c.fill(px, py, px + pvw, py + pvh, Theme.alpha(0xFF14141A, 1f - a));
            if (sel) {      // "ACTIVE" badge
                int bw = textRenderer.getWidth("ACTIVE") + 10;
                Theme.round(c, px + pvw - bw - 4, py + 4, bw, 12, 4, Theme.alpha(dot, a));
                Theme.round(c, px + pvw - bw - 3, py + 5, bw - 2, 10, 3, Theme.alpha(0xFF0B0B10, a));
                c.drawText(textRenderer, "ACTIVE", px + pvw - bw + 2, py + 7, Theme.alpha(dot, a), false);
            }
            int ny = py + pvh + 6;
            Theme.round(c, x + 8, ny + 1, 5, 5, 2, Theme.alpha(dot, a));
            c.drawTextWithShadow(textRenderer, Season.NAMES[i], x + 17, ny, Theme.alpha(sel ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, 0.6f + 0.4f * card[i]), a));
            if (textRenderer.getWidth(Season.DESC[i]) <= cw - 16)
                c.drawText(textRenderer, Season.DESC[i], x + 8, ny + 11, Theme.alpha(Theme.TEXT_DIM, a), false);
        }

        // footer
        int fy = y0 + ph - 24, dw = 52, dx = x0 + pw - 12 - dw;
        boolean dh = mx >= dx && mx <= dx + dw && my >= fy && my <= fy + 14;
        doneHover += ((dh ? 1f : 0f) - doneHover) * 0.25f;
        Theme.round(c, dx, fy, dw, 14, 4, Theme.alpha(Theme.mix(Theme.BORDER, a1, 0.4f + 0.6f * doneHover), a));
        Theme.roundGrad(c, dx + 1, fy + 1, dw - 2, 12, 3, Theme.mix(0xFF22222A, a1, 0.15f * doneHover + 0.05f), Theme.mix(0xFF141418, a1, 0.05f * doneHover), a);
        c.drawCenteredTextWithShadow(textRenderer, "Done", dx + dw / 2, fy + 3, Theme.alpha(Theme.mix(0xFFB4B4C0, 0xFFFFFFFF, doneHover), a));
        String hint = "Click to apply  -  arrows switch  -  Esc closes";
        if (textRenderer.getWidth(hint) < dx - x0 - 24) c.drawText(textRenderer, hint, x0 + 12, fy + 3, Theme.alpha(Theme.TEXT_DIM, a), false);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (menuOpen) {
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { menuOpen = false; return true; }
            if (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_UP) { select(Season.current - 1); return true; }
            if (k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_DOWN) { select(Season.current + 1); return true; }
            return true;
        }
        if (k == GLFW.GLFW_KEY_T) { menuOpen = true; return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
        if (menuOpen) {
            int x0 = mx0(), y0 = my0(), pw = mw(), ph = mh();
            if (mx >= x0 + pw - 24 && mx <= x0 + pw - 8 && my >= y0 + 7 && my <= y0 + 21) { menuOpen = false; return true; }
            int fy = y0 + ph - 24, dw = 52, dx = x0 + pw - 12 - dw;
            if (mx >= dx && mx <= dx + dw && my >= fy && my <= fy + 14) { menuOpen = false; return true; }
            for (int i = 0; i < Season.NAMES.length; i++) {
                if (mx >= cardX(i) && mx <= cardX(i) + cw() && my >= cardY(i) && my <= cardY(i) + chh()) { select(i); return true; }
            }
            if (mx < x0 || mx > x0 + pw || my < y0 || my > y0 + ph) menuOpen = false;   // click outside closes the picker
            return true;
        }
        int tbx = (width - TBW) / 2;
        if (mx >= tbx && mx <= tbx + TBW && my >= TBY && my <= TBY + TBH) { menuOpen = true; return true; }
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

package nelose;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

/** Main menu in the Nelose style: dark, snowy, with the winter logo. */
public class NeloseTitleScreen extends Screen {
    private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Options", "Nelose settings", "Quit"};
    private static final int BW = 170, BH = 24, GAP = 6;
    private final float[] hover = new float[LABELS.length];
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

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        float f = Math.min(1f, (System.currentTimeMillis() - opened) / 600f);
        f = 1f - (1f - f) * (1f - f);
        int a1 = Theme.accent(), a2 = Theme.accent2();

        // night sky gradient with a soft glow behind the logo
        for (int y = 0; y < height; y++) {
            float k = y / (float) height;
            c.fill(0, y, width, y + 1, Theme.mix(0xFF070A14, 0xFF111A2E, k));
        }
        int gy = height / 2 - 62;
        for (int i = 40; i >= 1; i--) {
            c.fill(width / 2 - i * 5, gy - i * 2, width / 2 + i * 5, gy + i * 2, Theme.alpha(a1, 0.006f * f));
        }
        // snowy ground line
        int gh = height / 7;
        for (int y = 0; y < gh; y++) c.fill(0, height - gh + y, width, height - gh + y + 1, Theme.alpha(0xFFE8F2FF, 0.05f + 0.10f * y / gh));
        c.fill(0, height - gh, width, height - gh + 1, Theme.alpha(0xFFFFFFFF, 0.25f));

        if (Modules.BLUR.f("Title snow") > 0) Snow.draw(c, width, height, (int) Modules.BLUR.f("Title snow"), 0.9f * f);

        // logo + name
        int ly = height / 2 - 62 + (int) ((1f - f) * 14);
        Avatar.draw(c, width / 2, ly, 30, f);
        int nw = textRenderer.getWidth("NELOSE") * 2;
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(width / 2f - nw / 2f, ly + 38);
        c.getMatrices().scale(2f, 2f);
        c.drawTextWithShadow(textRenderer, "NELOSE", 0, 0, Theme.alpha(Theme.TEXT, f));
        c.getMatrices().popMatrix();
        for (int i = 0; i < nw; i++) c.fill(width / 2 - nw / 2 + i, ly + 58, width / 2 - nw / 2 + i + 1, ly + 59, Theme.alpha(Theme.mix(a1, a2, i / (float) nw), f));

        // buttons
        for (int i = 0; i < LABELS.length; i++) {
            int x = bx(), y = by(i) + (int) ((1f - f) * 10 * (i + 1));
            boolean hov = mx >= x && mx <= x + BW && my >= y && my <= y + BH;
            hover[i] += ((hov ? 1f : 0f) - hover[i]) * 0.25f;
            if (hover[i] > 0.05f) Theme.glow(c, x, y, BW, BH, 5, Theme.alpha(a1, hover[i]), 3);
            Theme.card(c, x, y, BW, BH, 5, Theme.alpha(Theme.mix(Theme.CARD, Theme.CARD_HOV, hover[i]), f),
                Theme.alpha(Theme.mix(Theme.BORDER, a1, hover[i]), f));
            c.drawCenteredTextWithShadow(textRenderer, LABELS[i], width / 2, y + (BH - 8) / 2,
                Theme.alpha(Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, 0.4f + 0.6f * hover[i]), f));
        }
        c.drawText(textRenderer, "Minecraft 1.21.11", 6, height - 12, Theme.alpha(Theme.TEXT_DIM, f), false);
        String r = "Nelose v1.4";
        c.drawText(textRenderer, r, width - 6 - textRenderer.getWidth(r), height - 12, Theme.alpha(Theme.TEXT_DIM, f), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
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

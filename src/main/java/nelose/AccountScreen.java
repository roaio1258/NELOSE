package nelose;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Small window to type a nickname and switch to it. */
public class AccountScreen extends Screen {
    private static final int W = 280, H = 236, ROWH = 20;
    private TextFieldWidget field;
    private String msg = "";
    private boolean msgOk;
    private long opened;

    public AccountScreen() { super(Text.literal("Account")); }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private int applyY() { return py() + 98; }
    private int rowY(int i) { return py() + 146 + i * ROWH; }

    @Override
    protected void init() {
        opened = System.currentTimeMillis();
        field = new TextFieldWidget(textRenderer, px() + 16, py() + 72, W - 32, 20, Text.literal("Nickname"));
        field.setMaxLength(16);
        addDrawableChild(field);
        setInitialFocus(field);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {}

    @Override
    public void close() { client.setScreen(new NeloseScreen()); }

    private void apply(String name) {
        name = name.trim();
        if (!Accounts.valid(name)) { msg = "3-16 symbols: letters, digits, _"; msgOk = false; return; }
        if (Accounts.switchTo(client, name)) {
            Accounts.remember(name);
            msg = "Now playing as " + name;
            msgOk = true;
        } else { msg = "Could not switch the account"; msgOk = false; }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        float f = Math.min(1f, (System.currentTimeMillis() - opened) / 200f);
        int a1 = Theme.accent();
        int x = px(), y = py();
        c.fill(0, 0, width, height, Theme.alpha(0xFF000000, 0.55f * f));
        Theme.glow(c, x, y, W, H, 8, Theme.alpha(a1, f), 4);
        Theme.card(c, x, y, W, H, 8, Theme.alpha(Theme.PANEL, f), Theme.alpha(Theme.BORDER, f));

        c.drawTextWithShadow(textRenderer, "Account", x + 16, y + 14, Theme.alpha(Theme.TEXT, f));
        c.drawText(textRenderer, "Current: " + Accounts.current(client), x + 16, y + 30, Theme.alpha(Theme.TEXT_DIM, f), false);
        if (!msg.isEmpty()) c.drawText(textRenderer, msg, x + 16, y + 44, Theme.alpha(msgOk ? 0xFF6EE7A0 : 0xFFFF7A7A, f), false);
        c.drawText(textRenderer, "New nickname", x + 16, y + 60, Theme.alpha(Theme.TEXT_DIM, f), false);

        // apply button
        boolean hovA = mx >= x + 16 && mx <= x + W - 16 && my >= applyY() && my <= applyY() + 22;
        Theme.card(c, x + 16, applyY(), W - 32, 22, 5, Theme.alpha(hovA ? Theme.CARD_HOV : Theme.CARD, f), Theme.alpha(hovA ? a1 : Theme.BORDER, f));
        c.drawCenteredTextWithShadow(textRenderer, "Switch account", x + W / 2, applyY() + 7, Theme.alpha(hovA ? 0xFFFFFFFF : Theme.TEXT, f));

        // saved nicknames
        c.drawText(textRenderer, "Saved (click to use)", x + 16, y + 130, Theme.alpha(Theme.TEXT_DIM, f), false);
        for (int i = 0; i < Accounts.saved.size(); i++) {
            int ry = rowY(i);
            boolean hov = mx >= x + 16 && mx <= x + W - 16 && my >= ry && my <= ry + ROWH - 2;
            boolean cur = Accounts.saved.get(i).equals(Accounts.current(client));
            Theme.card(c, x + 16, ry, W - 32, ROWH - 2, 4, Theme.alpha(hov ? Theme.CARD_HOV : Theme.CARD, f), Theme.alpha(cur ? a1 : Theme.BORDER, f));
            c.drawTextWithShadow(textRenderer, Accounts.saved.get(i), x + 24, ry + 5, Theme.alpha(Theme.TEXT, f));
            c.drawText(textRenderer, "x", x + W - 28, ry + 5, Theme.alpha(0xFFFF7A7A, f), false);
        }
        c.drawText(textRenderer, "Works on servers with offline mode. Esc - back", x + 16, y + H - 12, Theme.alpha(Theme.TEXT_DIM, f * 0.7f), false);

        super.render(c, mx, my, delta);
        if (field.getText().isEmpty() && !field.isFocused())
            c.drawText(textRenderer, "type a nickname", field.getX() + 4, field.getY() + 6, Theme.alpha(Theme.TEXT_DIM, f), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            int x = px();
            if (mx >= x + 16 && mx <= x + W - 16 && my >= applyY() && my <= applyY() + 22) { apply(field.getText()); return true; }
            for (int i = 0; i < Accounts.saved.size(); i++) {
                int ry = rowY(i);
                if (mx >= x + 16 && mx <= x + W - 16 && my >= ry && my <= ry + ROWH - 2) {
                    String n = Accounts.saved.get(i);
                    if (mx >= x + W - 36) { Accounts.forget(n); msg = ""; }
                    else apply(n);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { apply(field.getText()); return true; }
        return super.keyPressed(input);
    }
}

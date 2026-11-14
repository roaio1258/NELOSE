package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Lets you move HUD widgets whenever the mouse is free in chat. Polls the mouse itself,
 * so it needs no screen hooks: open chat, drag a widget, double click to reset it.
 */
public final class HudDrag {
    private static boolean down;
    private static HudLayout.Item drag, lastItem;
    private static double gx, gy;
    private static long lastClick;

    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof ChatScreen)) {
            if (drag != null) { drag = null; Config.save(); }
            down = false;
            return;
        }
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        long win = mc.getWindow().getHandle();
        double[] cx = new double[1], cy = new double[1];
        int[] ww = new int[1], wh = new int[1];
        GLFW.glfwGetCursorPos(win, cx, cy);
        GLFW.glfwGetWindowSize(win, ww, wh);
        double mx = cx[0] * sw / Math.max(1, ww[0]);
        double my = cy[0] * sh / Math.max(1, wh[0]);
        boolean pressed = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        HudLayout.Item hov = drag != null ? drag : HudLayout.at(mx, my);
        if (pressed && !down) {                                   // button went down
            if (hov != null) {
                long now = System.currentTimeMillis();
                if (hov == lastItem && now - lastClick < 350) {   // double click: back to default place
                    hov.dx = 0; hov.dy = 0;
                    Config.save();
                    lastItem = null;
                } else {
                    drag = hov; gx = mx - hov.left(); gy = my - hov.top();
                    lastItem = hov; lastClick = now;
                }
            }
        } else if (pressed && drag != null) {
            HudLayout.drag(drag, mx, my, gx, gy, sw, sh);
        } else if (!pressed && drag != null) {
            drag = null;
            Config.save();
        }
        down = pressed;

        int a1 = Theme.accent();
        for (HudLayout.Item it : HudLayout.ITEMS.values()) {
            if (!it.visible()) continue;
            boolean on = it == hov;
            float al = it == drag ? 0.95f : on ? 0.7f : 0.28f;
            int x0 = Math.round(it.left()) - 2, y0 = Math.round(it.top()) - 2, x1 = x0 + it.w + 4, y1 = y0 + it.h + 4;
            int col = Theme.alpha(a1, al);
            c.fill(x0, y0, x1, y0 + 1, col); c.fill(x0, y1 - 1, x1, y1, col);
            c.fill(x0, y0, x0 + 1, y1, col); c.fill(x1 - 1, y0, x1, y1, col);
            if (on) c.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, Theme.alpha(a1, it == drag ? 0.14f : 0.07f));
        }
        String hint = "Drag HUD widgets to move them  -  double click to reset";
        c.drawCenteredTextWithShadow(mc.textRenderer, hint, sw / 2, 6, Theme.alpha(0xFFFFFFFF, 0.55f));
    }

    private HudDrag() {}
}

package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Popular HUD extras: keybind list, inventory view, module toggle notifications, session timer. */
public final class Extras {
    public static final long START = System.currentTimeMillis();

    // ---------- helpers ----------
    public static String keyName(int k) {
        switch (k) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LALT";
            case GLFW.GLFW_KEY_INSERT: return "INS";
            case GLFW.GLFW_KEY_DELETE: return "DEL";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "CAPS";
            case GLFW.GLFW_KEY_PAGE_UP: return "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PGDN";
            default: break;
        }
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F12) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(k, 0);
        return n == null ? "K" + k : n.toUpperCase(Locale.ROOT);
    }

    public static String session() {
        long s = (System.currentTimeMillis() - START) / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static boolean preview(MinecraftClient mc) {
        return mc.currentScreen instanceof NeloseScreen || mc.currentScreen instanceof ChatScreen;
    }

    // ---------- keybinds ----------
    public static void keybinds(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1) {
        Module M = Modules.KEYBINDS;
        List<Module> list = new ArrayList<>();
        for (Module m : Modules.ALL) if (m.key >= 0 && (!M.b("Only enabled") || m.on)) list.add(m);
        boolean fake = false;
        if (list.isEmpty()) {
            if (!preview(mc)) return;
            fake = true;
        }
        int rows = fake ? 3 : list.size();
        String[] nm = new String[rows], kn = new String[rows];
        boolean[] on = new boolean[rows];
        int nw = 0, kw = 0;
        for (int i = 0; i < rows; i++) {
            nm[i] = fake ? new String[]{"Zoom", "Fullbright", "Auto Clicker"}[i] : list.get(i).name;
            kn[i] = fake ? new String[]{"C", "V", "R"}[i] : keyName(list.get(i).key);
            on[i] = fake ? i == 0 : list.get(i).on;
            nw = Math.max(nw, tr.getWidth(nm[i]));
            kw = Math.max(kw, tr.getWidth(kn[i]) + 8);
        }
        int pad = 7, w = Math.max(96, pad * 2 + nw + 12 + kw), h = 16 + rows * 12 + 5;
        int sw = mc.getWindow().getScaledWidth();
        int x = sw - w - 6, y = 90;
        HudLayout.begin(c, "keybinds", x, y, w, h);
        Theme.card(c, x, y, w, h, 6, Theme.alpha(0xFF0B0B0E, 0.92f), Theme.mix(Theme.BORDER, a1, 0.25f));
        c.drawTextWithShadow(tr, "Keybinds", x + pad, y + 4, a1);
        c.fill(x + pad, y + 14, x + w - pad, y + 15, Theme.alpha(0xFFFFFFFF, 0.08f));
        for (int i = 0; i < rows; i++) {
            int ry = y + 18 + i * 12;
            c.drawText(tr, nm[i], x + pad, ry, on[i] ? Theme.TEXT : Theme.TEXT_DIM, false);
            int cw = tr.getWidth(kn[i]) + 8, cx = x + w - pad - cw;
            Theme.round(c, cx, ry - 2, cw, 11, 3, on[i] ? Theme.alpha(a1, 0.35f) : 0x30FFFFFF);
            c.drawText(tr, kn[i], cx + 4, ry, on[i] ? 0xFFFFFFFF : Theme.TEXT_DIM, false);
        }
        HudLayout.end(c);
    }

    // ---------- inventory ----------
    public static void inventory(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1) {
        Module M = Modules.INVENTORY_HUD;
        int w = 9 * 18 + 10, h = 3 * 18 + 10 + (M.b("Title") ? 12 : 0);
        int x = 8, y = mc.getWindow().getScaledHeight() - 30 - h - 6;
        HudLayout.begin(c, "inventory", x, y, w, h);
        Theme.card(c, x, y, w, h, 6, Theme.alpha(0xFF0B0B0E, M.f("Opacity")), Theme.mix(Theme.BORDER, a1, 0.2f));
        int oy = y + 5 + (M.b("Title") ? 12 : 0);
        if (M.b("Title")) c.drawTextWithShadow(tr, "Inventory", x + 7, y + 4, a1);
        var inv = mc.player.getInventory();
        for (int i = 0; i < 27; i++) {
            int sx = x + 5 + (i % 9) * 18, sy = oy + (i / 9) * 18;
            c.fill(sx, sy, sx + 17, sy + 17, Theme.alpha(0xFFFFFFFF, 0.05f));
            ItemStack st = inv.getStack(9 + i);
            if (st.isEmpty()) continue;
            c.drawItem(st, sx + 1, sy + 1);
            c.drawStackOverlay(tr, st, sx + 1, sy + 1);
        }
        HudLayout.end(c);
    }

    // ---------- module toggle notifications ----------
    private static final Map<Module, Boolean> PREV = new HashMap<>();
    private static final List<Object[]> TOASTS = new ArrayList<>();   // {text, on, start}

    public static void notifications(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1) {
        long now = System.currentTimeMillis();
        for (Module m : Modules.ALL) {
            Boolean p = PREV.put(m, m.on);
            if (p != null && p != m.on && m != Modules.NOTIFY) {
                TOASTS.add(new Object[]{(m.on ? "Enabled " : "Disabled ") + m.name, m.on, now});
                if (TOASTS.size() > 5) TOASTS.remove(0);
            }
        }
        if (!Modules.NOTIFY.on || mc.currentScreen != null) return;
        long dur = (long) (Modules.NOTIFY.f("Duration") * 1000);
        TOASTS.removeIf(t -> now - (long) t[2] > dur);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int n = TOASTS.size();
        for (int i = 0; i < n; i++) {
            Object[] t = TOASTS.get(n - 1 - i);
            long age = now - (long) t[2];
            float in = Math.min(1f, age / 180f), out = Math.min(1f, (dur - age) / 250f);
            float a = Math.max(0f, Math.min(in, out));
            String s = (String) t[0];
            boolean on = (boolean) t[1];
            int w = tr.getWidth(s) + 26, h = 18;
            int x = sw / 2 - w / 2, y = sh - 70 - i * 22 + (int) ((1f - in) * 6);
            Theme.card(c, x, y, w, h, 6, Theme.alpha(0xFF0B0B0E, 0.92f * a), Theme.alpha(Theme.mix(Theme.BORDER, on ? 0xFF6EE7A0 : 0xFFFB7185, 0.4f), a));
            Theme.round(c, x + 7, y + 6, 6, 6, 3, Theme.alpha(on ? 0xFF6EE7A0 : 0xFFFB7185, a));
            c.drawTextWithShadow(tr, s, x + 19, y + 5, Theme.alpha(Theme.TEXT, a));
        }
    }

    private Extras() {}
}

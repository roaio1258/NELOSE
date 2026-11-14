package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The "Метки" tab: saved places on the left, a create form and Autometki on the right. */
public final class MarksTab {
    private static final int GDIM = 0xFFC9D0DC;
    private static final int RP_W = 162, ROW_H = 24;

    private static int section;                 // 0 personal, 1 events
    private static int focus;                   // 0 none, 1 text, 2 X, 3 Y, 4 Z
    private static final String[] F = {"", "", "", ""};
    private static int colorIdx;
    private static int scrollRows;
    private static long badUntil;

    public static boolean typing() { return focus != 0; }

    // ---------- layout ----------
    private static int lw(int cw) { return cw - RP_W - 8; }

    private static List<Marks.Mark> shown() {
        List<Marks.Mark> r = new ArrayList<>();
        for (Marks.Mark m : Marks.LIST) if (m.auto == (section == 1)) r.add(m);
        return r;
    }

    // ---------- render ----------
    public static void render(DrawContext c, TextRenderer tr, int cx, int top, int cw, int h, int mx, int my, float f, int a1) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int lw = lw(cw), rx = cx + lw + 8;

        // left panel
        panel(c, cx, top, lw, h, f);
        int sw2 = (lw - 16) / 2;
        seg(c, tr, cx + 8, top + 8, sw2, "Личные", section == 0, mx, my, f, a1);
        seg(c, tr, cx + 8 + sw2, top + 8, sw2, "События", section == 1, mx, my, f, a1);
        List<Marks.Mark> list = shown();
        c.drawTextWithShadow(tr, "Сохранённые места", cx + 10, top + 34, Theme.alpha(Theme.TEXT, f));
        String cnt = String.valueOf(list.size());
        c.drawTextWithShadow(tr, cnt, cx + lw - 10 - tr.getWidth(cnt), top + 34, Theme.alpha(Theme.TEXT, f));
        int rowsTop = top + 48, vis = Math.max(1, (h - 48 - 20) / ROW_H);
        scrollRows = Math.max(0, Math.min(scrollRows, Math.max(0, list.size() - vis)));
        if (list.isEmpty()) {
            int pcx = cx + lw / 2, pcy = top + h / 2 - 12;
            pin(c, pcx, pcy, Theme.alpha(GDIM, f));
            String t1 = section == 0 ? "Пока нет меток" : "Пока нет событий";
            String t2 = section == 0 ? "Добавьте первую метку справа" : "Метка появится после смерти";
            c.drawCenteredTextWithShadow(tr, Text.literal(t1).formatted(Formatting.BOLD), pcx, pcy + 16, Theme.alpha(Theme.TEXT, f));
            c.drawCenteredTextWithShadow(tr, t2, pcx, pcy + 28, Theme.alpha(GDIM, f * 0.8f));
        }
        String d = Marks.dim(mc);
        for (int i = 0; i < vis && scrollRows + i < list.size(); i++) {
            Marks.Mark m = list.get(scrollRows + i);
            int ry = rowsTop + i * ROW_H, rw = lw - 16, rxx = cx + 8;
            boolean hov = mx >= rxx && mx <= rxx + rw && my >= ry && my <= ry + 22;
            Theme.round(c, rxx, ry, rw, 22, 5, Theme.alpha(0xFFFFFFFF, f * (hov ? 0.12f : 0.06f)));
            Theme.round(c, rxx + 6, ry + 8, 6, 6, 3, Theme.alpha(m.color, f));
            c.drawTextWithShadow(tr, tr.trimToWidth(m.name, rw - 44), rxx + 17, ry + 3, Theme.alpha(Theme.TEXT, f));
            String co = m.x + " " + m.y + " " + m.z;
            if (mc.player != null && m.dim.equals(d))
                co += "  " + String.format(Locale.ROOT, "%.0fm", Math.sqrt(mc.player.squaredDistanceTo(m.x + 0.5, m.y + 0.5, m.z + 0.5)));
            else if (!m.dim.equals(d)) co += "  другой мир";
            c.drawText(tr, tr.trimToWidth(co, rw - 44), rxx + 17, ry + 12, Theme.alpha(GDIM, f * 0.75f), false);
            int bx = rxx + rw - 16;
            boolean xh = mx >= bx && mx <= bx + 12 && my >= ry + 5 && my <= ry + 17;
            Theme.round(c, bx, ry + 5, 12, 12, 4, Theme.alpha(xh ? 0xFFE5334B : 0xFFFFFFFF, f * (xh ? 0.35f : 0.08f)));
            c.drawText(tr, "x", bx + 4, ry + 7, Theme.alpha(xh ? 0xFFFFFFFF : GDIM, f), false);
        }
        c.drawText(tr, "Видны только вам", cx + 10, top + h - 13, Theme.alpha(GDIM, f * 0.6f), false);

        // create form
        int rh = 164;
        panel(c, rx, top, RP_W, rh, f);
        c.drawTextWithShadow(tr, Text.literal("Создать метку").formatted(Formatting.BOLD), rx + 9, top + 8, Theme.alpha(Theme.TEXT, f));
        c.drawText(tr, "+", rx + RP_W - 14, top + 8, Theme.alpha(Theme.TEXT, f), false);
        c.drawText(tr, "Текст метки", rx + 9, top + 23, Theme.alpha(GDIM, f * 0.85f), false);
        field(c, tr, rx + 8, top + 34, RP_W - 16 - 26, 18, F[0], "", focus == 1, a1, f);
        int col = Marks.PALETTE[colorIdx];
        Theme.round(c, rx + RP_W - 8 - 22, top + 34, 22, 18, 5, Theme.alpha(col, f));
        String[] lab = {"X", "Y", "Z"};
        int fw = (RP_W - 16 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int fx = rx + 8 + i * (fw + 4);
            c.drawText(tr, lab[i], fx + 2, top + 58, Theme.alpha(GDIM, f * 0.85f), false);
            field(c, tr, fx, top + 68, fw, 16, F[i + 1], "", focus == i + 2, a1, f);
        }
        button(c, tr, rx + 8, top + 90, RP_W - 16, 18, "Мои координаты", false, mx, my, f, a1);
        button(c, tr, rx + 8, top + 112, RP_W - 16, 18, "Метка под прицелом", false, mx, my, f, a1);
        boolean bad = System.currentTimeMillis() < badUntil;
        button(c, tr, rx + 8, top + 138, RP_W - 16, 20, bad ? "Введите X Y Z" : "Поставить метку", true, mx, my, f, a1);

        // autometki
        int ay = top + rh + 8;
        panel(c, rx, ay, RP_W, 52, f);
        c.drawTextWithShadow(tr, Text.literal("Автометки").formatted(Formatting.BOLD), rx + 9, ay + 8, Theme.alpha(Theme.TEXT, f));
        int sx = rx + RP_W - 26 - 9, sy = ay + 7;
        float k = Marks.autoDeath ? 1f : 0f;
        Theme.round(c, sx, sy, 26, 12, 6, Theme.alpha(Marks.autoDeath ? a1 : 0xFF7A8396, f * (Marks.autoDeath ? 1f : 0.55f)));
        Theme.round(c, sx + 2 + (int) (14 * k), sy + 2, 8, 8, 4, Theme.alpha(0xFFFFFFFF, f));
        c.drawText(tr, "Точка смерти", rx + 9, ay + 24, Theme.alpha(GDIM, f * 0.9f), false);
        c.drawText(tr, "Только в текущем мире", rx + 9, ay + 36, Theme.alpha(GDIM, f * 0.65f), false);
    }

    private static void panel(DrawContext c, int x, int y, int w, int h, float f) {
        Theme.round(c, x, y, w, h, 8, Theme.alpha(0xFF14141C, f * 0.7f));
        NeloseScreen.outline(c, x, y, w, h, 8, Theme.alpha(0xFFFFFFFF, f * 0.14f));
    }

    private static void seg(DrawContext c, TextRenderer tr, int x, int y, int w, String t, boolean sel, int mx, int my, float f, int a1) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + 18;
        if (sel) Theme.round(c, x, y, w, 18, 6, Theme.alpha(0xFFFFFFFF, f * 0.22f));
        else if (hov) Theme.round(c, x, y, w, 18, 6, Theme.alpha(0xFFFFFFFF, f * 0.08f));
        c.drawCenteredTextWithShadow(tr, Text.literal(t).formatted(Formatting.BOLD), x + w / 2, y + 5, Theme.alpha(sel ? Theme.TEXT : GDIM, f));
    }

    private static void field(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String txt, String hint, boolean foc, int a1, float f) {
        Theme.round(c, x, y, w, h, 5, Theme.alpha(0xFFFFFFFF, f * 0.09f));
        if (foc) NeloseScreen.outline(c, x, y, w, h, 5, Theme.alpha(a1, f * 0.9f));
        boolean caret = foc && (System.currentTimeMillis() / 500) % 2 == 0;
        String s = tr.trimToWidth(txt, w - 10) + (caret ? "_" : "");
        c.drawText(tr, s, x + 5, y + (h - 8) / 2, Theme.alpha(Theme.TEXT, f), false);
    }

    private static void button(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String t, boolean primary, int mx, int my, float f, int a1) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        boolean bad = primary && System.currentTimeMillis() < badUntil;
        if (primary) Theme.round(c, x, y, w, h, 6, Theme.alpha(bad ? 0xFFE5334B : 0xFFF2F2F6, f * (hov ? 1f : 0.92f)));
        else Theme.round(c, x, y, w, h, 6, Theme.alpha(0xFFFFFFFF, f * (hov ? 0.2f : 0.13f)));
        int tc = primary ? (bad ? 0xFFFFFFFF : 0xFF141418) : Theme.TEXT;
        c.drawText(tr, t, x + 8, y + (h - 8) / 2, Theme.alpha(tc, f), false);
    }

    private static void pin(DrawContext c, int cx, int cy, int col) {
        Theme.round(c, cx - 7, cy - 9, 14, 14, 7, col);
        Theme.round(c, cx - 2, cy - 4, 4, 4, 2, 0xFF14141C);
        c.fill(cx - 3, cy + 4, cx + 3, cy + 6, col);
        c.fill(cx - 2, cy + 6, cx + 2, cy + 8, col);
        c.fill(cx - 1, cy + 8, cx + 1, cy + 10, col);
    }

    // ---------- input ----------
    public static boolean click(int cx, int top, int cw, int h, int mx, int my, int btn) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (btn != 0) return false;
        int lw = lw(cw), rx = cx + lw + 8;
        focus = 0;

        int sw2 = (lw - 16) / 2;
        if (my >= top + 8 && my <= top + 26) {
            if (mx >= cx + 8 && mx < cx + 8 + sw2) { section = 0; scrollRows = 0; return true; }
            if (mx >= cx + 8 + sw2 && mx <= cx + 8 + 2 * sw2) { section = 1; scrollRows = 0; return true; }
        }
        // delete buttons
        List<Marks.Mark> list = shown();
        int rowsTop = top + 48, vis = Math.max(1, (h - 48 - 20) / ROW_H);
        for (int i = 0; i < vis && scrollRows + i < list.size(); i++) {
            int ry = rowsTop + i * ROW_H, bx = cx + 8 + (lw - 16) - 16;
            if (mx >= bx && mx <= bx + 12 && my >= ry + 5 && my <= ry + 17) {
                Marks.LIST.remove(list.get(scrollRows + i));
                Marks.save();
                return true;
            }
        }
        // create form
        if (mx >= rx + 8 && mx <= rx + RP_W - 8 - 26 && my >= top + 34 && my <= top + 52) { focus = 1; return true; }
        if (mx >= rx + RP_W - 8 - 22 && mx <= rx + RP_W - 8 && my >= top + 34 && my <= top + 52) {
            colorIdx = (colorIdx + 1) % Marks.PALETTE.length;
            return true;
        }
        int fw = (RP_W - 16 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int fx = rx + 8 + i * (fw + 4);
            if (mx >= fx && mx <= fx + fw && my >= top + 68 && my <= top + 84) { focus = i + 2; return true; }
        }
        if (inside(mx, my, rx + 8, top + 90, RP_W - 16, 18) && mc.player != null) {
            F[1] = String.valueOf(mc.player.getBlockX());
            F[2] = String.valueOf(mc.player.getBlockY());
            F[3] = String.valueOf(mc.player.getBlockZ());
            return true;
        }
        if (inside(mx, my, rx + 8, top + 112, RP_W - 16, 18) && mc.player != null) {
            HitResult hr = mc.player.raycast(128.0, 0f, false);
            if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
                F[1] = String.valueOf(bh.getBlockPos().getX());
                F[2] = String.valueOf(bh.getBlockPos().getY());
                F[3] = String.valueOf(bh.getBlockPos().getZ());
            } else badUntil = System.currentTimeMillis() + 1200;
            return true;
        }
        if (inside(mx, my, rx + 8, top + 138, RP_W - 16, 20)) {
            try {
                int x = Integer.parseInt(F[1].trim()), y = Integer.parseInt(F[2].trim()), z = Integer.parseInt(F[3].trim());
                String name = F[0].trim().isEmpty() ? "Метка " + (shownCount(false) + 1) : F[0].trim();
                Marks.add(name, x, y, z, Marks.PALETTE[colorIdx], Marks.dim(mc), false);
                F[0] = F[1] = F[2] = F[3] = "";
                section = 0;
            } catch (NumberFormatException e) {
                badUntil = System.currentTimeMillis() + 1200;
            }
            return true;
        }
        int ay = top + 164 + 8;
        if (inside(mx, my, rx, ay, RP_W, 52)) {
            Marks.autoDeath = !Marks.autoDeath;
            Marks.save();
            return true;
        }
        return false;
    }

    private static int shownCount(boolean auto) {
        int n = 0;
        for (Marks.Mark m : Marks.LIST) if (m.auto == auto) n++;
        return n;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static boolean scroll(double v) {
        scrollRows -= (int) Math.signum(v);
        return true;
    }

    /** Typing into the focused field. Returns true when the key was used. */
    public static boolean key(int key) {
        if (focus == 0) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { focus = 0; return true; }
        if (key == GLFW.GLFW_KEY_TAB) { focus = focus % 4 + 1; return true; }
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) return true;
        int i = focus - 1;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!F[i].isEmpty()) F[i] = F[i].substring(0, F[i].length() - 1);
            return true;
        }
        long win = MinecraftClient.getInstance().getWindow().getHandle();
        boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        int max = i == 0 ? 18 : 9;
        if (F[i].length() >= max) return true;
        char ch = 0;
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) ch = (char) ('0' + (key - GLFW.GLFW_KEY_0));
        else if (key == GLFW.GLFW_KEY_MINUS) ch = shift && i == 0 ? '_' : '-';
        else if (i == 0 && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            ch = (char) ((shift ? 'A' : 'a') + (key - GLFW.GLFW_KEY_A));
        } else if (i == 0 && key == GLFW.GLFW_KEY_SPACE) ch = ' ';
        if (ch != 0) F[i] += ch;
        return true;
    }

    private MarksTab() {}
}

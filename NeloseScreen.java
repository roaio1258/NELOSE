package nelose;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NeloseScreen extends Screen {
    private static final int W = 500, H = 292, SIDE_FULL = 112, SIDE_MIN = 40, ROW = 28, STEP = 32;
    private static boolean collapsed, searching;
    private static String query = "";
    private float sideW = SIDE_FULL;
    private int side() { return Math.round(sideW); }
    private void resetScroll() { scroll = scrollTarget = 0; }
    private static final int CH = 40, CSTEP = 48, GAPG = 8;     // module cards: height, row step, gap
    private static final int GDIM = 0xFFC9D0DC, GDARK = 0xFF141A28;   // glass theme: dim text, dark base

    // ---------- frosted glass look ----------
    /** 1px outline of a rounded rectangle. */
    private static void outline(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.min(r, Math.min(w, h) / 2);
        c.fill(x + r, y, x + w - r, y + 1, col);
        c.fill(x + r, y + h - 1, x + w - r, y + h, col);
        c.fill(x, y + r, x + 1, y + h - r, col);
        c.fill(x + w - 1, y + r, x + w, y + h - r, col);
        int prev = r;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            int to = Math.max(inset + 1, prev);
            c.fill(x + inset, y + i, x + to, y + i + 1, col);
            c.fill(x + w - to, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - i - 1, x + to, y + h - i, col);
            c.fill(x + w - to, y + h - i - 1, x + w - inset, y + h - i, col);
            prev = inset;
        }
    }

    /** White-to-blue sheen over a rounded rectangle, drawn in 8 px bands (a per-row gradient was too many quads and made opening laggy). */
    private static void sheen(DrawContext c, int x, int y, int w, int h, int r, float a) {
        r = Math.min(r, Math.min(w, h) / 2);
        int row = 0;
        while (row < h) {
            boolean corner = row < r || row >= h - r;
            int hh = corner ? 1 : Math.max(1, Math.min(8, (h - r) - row));
            int inset = 0;
            if (row < r) { double dy = r - row - 0.5; inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy))); }
            else if (row >= h - r) { double dy = row - (h - r) + 0.5; inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy))); }
            float k = row / (float) Math.max(1, h - 1);
            c.fill(x + inset, y + row, x + w - inset, y + row + hh, Theme.alpha(Theme.mix(0xFFFFFFFF, 0xFFC8D4EA, k), a));
            row += hh;
        }
    }

    /** Fine frosted-glass grain over the window. Drawn with plain fills (no texture), so it can never show as a pink/black checkerboard. */
    private static void frost(DrawContext c, int x, int y, int w, int h) {
        int n = Math.min(900, Math.max(0, w * h / 140));
        for (int i = 0; i < n; i++) {
            long hsh = (i * 2654435761L + 0x9E3779B9L) & 0xFFFFFFFFL;
            int px = x + (int) (hsh % Math.max(1, w));
            int py = y + (int) (((hsh >>> 8) * 40503L + i * 7919L) % Math.max(1, h));
            boolean light = (hsh & 1) == 0;
            c.fill(px, py, px + 1, py + 1, light ? 0x14FFFFFF : 0x0C000000);
        }
    }

    /** Frosted glass window: soft shadow, dark base for contrast, white sheen, light edge. */
    private static void glass(DrawContext c, int x, int y, int w, int h, int r, float f, boolean strong) {
        Theme.round(c, x + 1, y + 3, w, h, r, Theme.alpha(0xFF000000, 0.18f * f));
        Theme.round(c, x, y, w, h, r, Theme.alpha(GDARK, (strong ? 0.62f : 0.40f) * f));
        sheen(c, x, y, w, h, r, (strong ? 0.12f : 0.14f) * f);
        outline(c, x, y, w, h, r, Theme.alpha(0xFFFFFFFF, 0.34f * f));
        c.fill(x + r, y + 1, x + w - r, y + 2, Theme.alpha(0xFFFFFFFF, 0.14f * f));
    }

    /** Glass card; `on` tints it with the accent colour. */
    private static void glassCard(DrawContext c, int x, int y, int w, int h, int r, float hover, float on, int a1, float f) {
        int base = 0xFF090B13;
        int fill = Theme.mix(base, Theme.mix(base, a1, 0.38f), on);
        Theme.round(c, x, y, w, h, r, Theme.alpha(fill, f * (0.52f + 0.08f * hover + 0.14f * on)));
        outline(c, x, y, w, h, r, Theme.alpha(Theme.mix(0xFFFFFFFF, a1, on * 0.8f), f * (0.11f + 0.10f * hover + 0.32f * on)));
    }

    private final Map<String, List<String>> WRAP = new HashMap<>();

    private List<String> wrap(String s, int w, int maxLines) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String word : s.split(" ")) {
            if (cur.isEmpty()) { cur = word; continue; }
            String t = cur + " " + word;
            if (textRenderer.getWidth(t) <= w) cur = t;
            else { out.add(cur); cur = word; if (out.size() == maxLines) return out; }
        }
        if (!cur.isEmpty() && out.size() < maxLines) out.add(cur);
        return out;
    }

    private static int gridW(int w) { return (w - GAPG) / 2; }
    private int gridX(int i, int gw) { return cx() + (i % 2) * (gw + GAPG); }
    private int gridY(int i) { return top() + (i / 2) * CSTEP - (int) scroll + slide(); }
    private static final int PW = 158, PHEAD = 24, PPAD = 8;
    private static Category tab = Category.VISUALS;
    private static final Map<Module, float[]> SAVED = new HashMap<>();
    /** Offset of the main window from the screen centre (saved in the config). */
    public static float winDX, winDY;
    private boolean dragWin;
    private double winGX, winGY;
    private HudLayout.Item dragHud;
    private double hudGX, hudGY;

    /** A floating, draggable settings window. */
    private static final class Panel {
        final Module m;
        float x, y, tx, ty, open, lift;
        float[] hx = new float[3], hy = new float[3];
        boolean closing, dragging, free;
        double grabX, grabY;
        Panel(Module m) { this.m = m; }
    }

    private long opened;
    private int x, y;
    private float scroll, scrollTarget, tabPos, tabFade = 1f;
    private final List<Panel> panels = new ArrayList<>();
    private Panel dragPanel, sliderPanel;
    private Setting activeSlider;
    private Module listening;   // module waiting for a key press

    public NeloseScreen() { super(Text.literal("Nelose")); }

    @Override
    protected void init() {
        opened = System.currentTimeMillis();
        genShards();
        tabPos = tab.ordinal();
    }

    @Override
    public boolean shouldPause() { return false; }

    private int animMode() { return Modules.BLUR.idx("Opening animation"); }

    /** 0..1 progress of the opening animation. */
    private float prog() {
        float dur = animMode() == 0 ? 220f : (animMode() == 1 ? 750f : 650f);
        return Math.min(1f, (System.currentTimeMillis() - opened) / dur);
    }

    private long closeAt;   // 0 = open, otherwise the moment the closing animation started

    private float closeProg() {
        return closeAt == 0 ? 0f : Math.min(1f, (System.currentTimeMillis() - closeAt) / 200f);
    }

    private float fade() {
        float t = prog();
        float o = 1f - (1f - t) * (1f - t);
        float q = closeProg();
        return o * (1f - ease(q));
    }

    /** Starts the fade-out; the screen really closes when it has finished. */
    @Override
    public void close() {
        if (closeAt != 0) return;
        closeAt = System.currentTimeMillis();
        for (Panel p : panels) p.closing = true;
        listening = null; activeSlider = null; dragPanel = null;
    }

    // ---------- shard opening animations ----------
    private static final int TX = 10, TY = 7;
    private final float[] shDelay = new float[TX * TY], shDx = new float[TX * TY], shDy = new float[TX * TY];

    private void genShards() {
        java.util.Random r = new java.util.Random();
        boolean glass = animMode() == 2;
        for (int i = 0; i < TX * TY; i++) {
            int gx = i % TX, gy = i / TX;
            float nx = (gx + 0.5f) / TX - 0.5f, ny = (gy + 0.5f) / TY - 0.5f;
            float dist = (float) Math.hypot(nx, ny);
            if (glass) {   // radial: shards fly in from the outside, centre lands first
                shDelay[i] = dist * 0.55f;
                shDx[i] = nx * 520f; shDy[i] = ny * 520f;
            } else {       // random order and direction
                shDelay[i] = r.nextFloat() * 0.5f;
                double a = r.nextDouble() * Math.PI * 2;
                shDx[i] = (float) Math.cos(a) * (150 + r.nextFloat() * 350);
                shDy[i] = (float) Math.sin(a) * (150 + r.nextFloat() * 350);
            }
        }
    }

    private void renderShards(DrawContext c, float p, int a1) {
        int tw = W / TX, th = H / TY;
        for (int i = 0; i < TX * TY; i++) {
            float q = Math.max(0f, Math.min(1f, (p - shDelay[i]) / (1f - shDelay[i] * 0.9f)));
            if (q >= 1f) continue;
            float e = ease(q);
            int tx = x + (i % TX) * tw + Math.round(shDx[i] * (1f - e));
            int ty = y + (i / TX) * th + Math.round(shDy[i] * (1f - e));
            int col = Theme.mix(0xFFDDEBFF, a1, 0.35f);
            float a = (0.18f + 0.5f * (1f - e)) * (q < 0.05f ? q / 0.05f : 1f);
            c.fill(tx, ty, tx + tw, ty + th, Theme.alpha(col, a));
            int edge = Theme.alpha(0xFFFFFFFF, a * 1.4f);
            c.fill(tx, ty, tx + tw, ty + 1, edge);
            c.fill(tx, ty, tx + 1, ty + th, edge);
            // a diagonal crack across each shard
            for (int k = 0; k < Math.min(tw, th); k++) c.fill(tx + k, ty + th - k - 1, tx + k + 1, ty + th - k, Theme.alpha(0xFFFFFFFF, a * 0.5f));
        }
    }

    private void renderCinematic(DrawContext c, float p, int a1) {
        float e = ease(p);
        int bar = (int) ((1f - e) * height * 0.5f);
        if (bar > 0) {
            c.fill(0, 0, width, bar, 0xFF000000);
            c.fill(0, height - bar, width, height, 0xFF000000);
            int line = Theme.alpha(a1, 1f - e);
            c.fill(0, bar, width, bar + 1, line);
            c.fill(0, height - bar - 1, width, height - bar, line);
        }
    }

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1f - (1f - t) * (1f - t) * (1f - t); }

    // ---------- layout helpers ----------
    private int cx() { return x + side() + 14; }
    private int cw() { return W - side() - 28; }
    private int top() { return y + 50; }
    private int bottom() { return y + H - 12; }
    private int slide() { return (int) ((1f - tabFade) * 10); }
    private int rowY(int i) { return top() + i * STEP - (int) scroll + slide(); }

    private boolean isGrid() { return !query.isEmpty() || (tab != Category.THEME && tab != Category.SETTINGS); }

    /** Modules of the open tab, or every module that matches the search text. */
    private List<Module> curMods() {
        if (query.isEmpty()) return Modules.of(tab);
        String q = query.toLowerCase(java.util.Locale.ROOT).trim();
        List<Module> r = new ArrayList<>();
        for (Module m : Modules.ALL) {
            if (m.cat == Category.THEME || m.cat == Category.SETTINGS) continue;
            if (m.name.toLowerCase(java.util.Locale.ROOT).contains(q) || m.desc.toLowerCase(java.util.Locale.ROOT).contains(q)) r.add(m);
        }
        return r;
    }

    private int maxScroll() {
        if (!isGrid()) return 0;
        return Math.max(0, ((curMods().size() + 1) / 2) * CSTEP - (bottom() - top()));
    }

    private int btnY() { return y + H - 30; }

    /** Bottom sidebar buttons: 0 search, 1 theme, 2 collapse. Only the collapse button stays when the bar is narrow. */
    private int[] btnRect(int i) {
        if (sideW >= 100f) return new int[]{x + 8 + i * 34, 28};
        return i == 2 ? new int[]{x + (side() - 24) / 2, 24} : null;
    }

    /** Sidebar tab position: two groups (Modules, Client), animated between neighbours. */
    private int slotI(int i) {
        int g = Category.THEME.ordinal();
        return i < g ? y + 86 + i * 24 : y + 196 + (i - g) * 24;
    }

    private int slotY(float pos) {
        int last = Category.values().length - 1;
        int lo = Math.max(0, Math.min(last, (int) Math.floor(pos))), hi = Math.min(last, lo + 1);
        float fr = Math.max(0f, Math.min(1f, pos - lo));
        return Math.round(slotI(lo) + (slotI(hi) - slotI(lo)) * fr);
    }

    private int tabY(Category cat) { return slotY(cat.ordinal()); }

    private static int rowH(Setting s) { return (s.isToggle || s.isMode) ? 20 : 28; }

    private static int panelH(Panel p) {
        int h = PHEAD + PPAD + PPAD + 40;   // +40: bind rows
        for (Setting s : p.m.settings) if (p.m.visible(s)) h += rowH(s);
        return h;
    }

    // ---------- panels ----------
    private Panel panelOf(Module m) {
        for (Panel p : panels) if (p.m == m) return p;
        return null;
    }

    private void togglePanel(Module m, int rowYPos) {
        Panel ex = panelOf(m);
        if (ex != null) {
            ex.closing = !ex.closing;
            if (!ex.closing) { panels.remove(ex); panels.add(ex); }
            return;
        }
        for (Panel q : panels) q.closing = true;   // one menu at a time
        Panel p = new Panel(m);
        int ph = panelH(p);
        boolean right = x + W + 8 + PW <= width - 4;
        p.tx = right ? x + W + 8 : x - PW - 8;
        p.ty = Math.max(4, Math.min(height - ph - 4, y));
        // start tucked behind the GUI edge, then slide out
        boolean fromRight = p.tx > x + W / 2f;
        p.x = fromRight ? x + W - 70 : x + 70 - PW;
        p.y = p.ty;
        for (int i = 0; i < 3; i++) { p.hx[i] = p.x; p.hy[i] = p.y; }
        panels.add(p);
    }

    private void renderPanel(DrawContext c, Panel p, int mx, int my, int a1, int a2) {
        p.open += ((p.closing ? 0f : 1f) - p.open) * 0.2f;
        float pa = ease(p.open);
        float sp = p.dragging ? 0.6f : 0.28f;
        p.x += (p.tx - p.x) * sp;
        p.y += (p.ty - p.y) * sp;
        p.lift += ((p.dragging ? 1f : 0f) - p.lift) * 0.25f;
        p.hx[2] = p.hx[1]; p.hy[2] = p.hy[1];
        p.hx[1] = p.hx[0]; p.hy[1] = p.hy[0];
        p.hx[0] = p.x;     p.hy[0] = p.y;

        int px = Math.round(p.x), py = Math.round(p.y), ph = panelH(p);

        // motion ghosts while moving
        float moved = Math.min(1f, (Math.abs(p.tx - p.x) + Math.abs(p.ty - p.y)) / 30f);
        for (int g = 2; g >= 1; g--) {
            float ga = 0.10f * moved * (3 - g) / 2f * pa;
            if (ga > 0.005f) Theme.round(c, Math.round(p.hx[g]), Math.round(p.hy[g]), PW, ph, 7, Theme.alpha(a1, ga));
        }

        if (p.lift > 0.02f) Theme.round(c, px + 3, py + 6, PW, ph, 7, Theme.alpha(0xFF000000, 0.40f * p.lift * pa));
        glass(c, px, py, PW, ph, 8, pa, true);
        if (p.lift > 0.02f) outline(c, px, py, PW, ph, 8, Theme.alpha(a1, pa * p.lift));

        // header: breadcrumb on the left, count or search box on the right
        int cx = cx(), cw = cw();
        List<Module> mods = curMods();
        boolean grid = isGrid();
        float hf = f * (0.4f + 0.6f * tabFade);
        String group = !query.isEmpty() ? "Search" : (tab.ordinal() < Category.THEME.ordinal() ? "Modules" : "Client");
        String crumb = group + " / ";
        String curName = query.isEmpty() ? tab.title : "\"" + query + "\"";
        c.drawTextWithShadow(textRenderer, crumb, cx, y + 14, Theme.alpha(GDIM, hf));
        c.drawTextWithShadow(textRenderer, Text.literal(curName).formatted(Formatting.BOLD), cx + textRenderer.getWidth(crumb), y + 14, Theme.alpha(Theme.TEXT, hf));
        String subTxt = query.isEmpty() ? tab.sub : mods.size() + (mods.size() == 1 ? " module found" : " modules found");
        c.drawText(textRenderer, subTxt, cx, y + 27, Theme.alpha(GDIM, hf * 0.8f), false);
        if (searching) {
            int bw = 126, bx2 = cx + cw - bw, by = y + 9;
            glassCard(c, bx2, by, bw, 20, 6, 0f, 1f, a1, f);
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            String shown = query.isEmpty() ? "Type to search..." : textRenderer.trimToWidth(query, bw - 24) + (caret ? "_" : "");
            c.drawText(textRenderer, shown, bx2 + 8, by + 6, Theme.alpha(query.isEmpty() ? GDIM : Theme.TEXT, f), false);
        } else if (grid) {
            long en = mods.stream().filter(m -> m.on).count();
            String cnt = en + "/" + mods.size();
            int tw = textRenderer.getWidth(cnt) + 14;
            Theme.round(c, cx + cw - tw, y + 12, tw, 16, 8, Theme.alpha(a1, hf * 0.18f));
            outline(c, cx + cw - tw, y + 12, tw, 16, 8, Theme.alpha(a1, hf * 0.5f));
            c.drawText(textRenderer, cnt, cx + cw - tw + 7, y + 16, Theme.alpha(Theme.TEXT, hf), false);
        }

        float rf = f * tabFade;
        int top = top(), bottom = bottom();
        c.enableScissor(cx - 2, top - 1, cx + cw + 2, bottom);
        if (!grid && tab == Category.THEME) {
            renderTheme(c, mx, my, rf, a1, a2);
        } else if (!grid && tab == Category.SETTINGS) {
            renderSettings(c, mx, my, rf, a1);
        } else {
            if (mods.isEmpty()) {
                String none = "No modules found";
                c.drawCenteredTextWithShadow(textRenderer, none, cx + cw / 2, top + 30, Theme.alpha(GDIM, rf));
            }
            int gw = gridW(cw - (maxScroll() > 0 ? 8 : 0));
            for (int i = 0; i < mods.size(); i++) {
                int ry = gridY(i);
                if (ry + CH < top || ry > bottom) continue;
                card(c, mods.get(i), gridX(i, gw), ry, gw, mx, my, rf, a1);
            }
        }
        c.disableScissor();

        if (maxScroll() > 0) {
            int trackH = bottom - top;
            int barH = Math.max(18, trackH * trackH / (trackH + maxScroll()));
            int barY = top + (int) ((trackH - barH) * (scroll / maxScroll()));
            Theme.round(c, cx + cw - 3, barY, 3, barH, 1, Theme.alpha(Theme.mix(0xFF3A3A44, a1, 0.35f), f));
        }

        // opening animation overlays
        float op = prog();
        if (op < 1f) {
            if (animMode() == 1) renderCinematic(c, op, a1);
            else if (animMode() >= 2) renderShards(c, op, a1);
        }

    }

    private boolean onGear(Module m, int rx, int rw, double mx) {
        return !m.settings.isEmpty() && mx >= rx + rw - 9 - 26 - 22 && mx <= rx + rw - 9 - 26 - 3;
    }

    /** Module card in the two-column grid: name and toggle on top, wrapped description below. */
    private void card(DrawContext c, Module m, int rx, int ry, int rw, int mx, int my, float f, int a1) {
        boolean hov = mx >= rx && mx <= rx + rw && my >= ry && my <= ry + CH && my >= top() && my <= bottom();
        m.anim += ((m.on ? 1f : 0f) - m.anim) * 0.22f;
        glassCard(c, rx, ry, rw, CH, 5, hov ? 1f : 0f, m.anim, a1, f);
        Text title = Text.literal(m.name).formatted(Formatting.BOLD);
        c.drawTextWithShadow(textRenderer, title, rx + 9, ry + 6, Theme.alpha(Theme.TEXT, f));
        if (m.key >= 0) {
            String kn = "[" + Binds.keyName(m.key) + "]";
            c.drawText(textRenderer, kn, rx + 14 + textRenderer.getWidth(title), ry + 6, Theme.alpha(a1, f), false);
        }
        List<String> lines = WRAP.computeIfAbsent(m.name + "|" + rw, k -> wrap(m.desc, rw - 18, 2));
        for (int k = 0; k < lines.size(); k++)
            c.drawText(textRenderer, lines.get(k), rx + 9, ry + 18 + k * 10, Theme.alpha(GDIM, f * 0.85f), false);
        int sw = 26, sh = 12, sx = rx + rw - sw - 9, sy = ry + 6;
        Theme.round(c, sx, sy, sw, sh, 6, Theme.alpha(Theme.mix(0xFF7A8396, a1, m.anim), f * (0.55f + 0.45f * m.anim)));
        Theme.round(c, sx + 2 + (int) ((sw - sh) * m.anim), sy + 2, sh - 4, sh - 4, 4, Theme.alpha(0xFFFFFFFF, f));
        Panel p = panelOf(m);
        boolean gh = onGear(m, rx, rw, mx) && my >= ry && my <= ry + CH;
        boolean opened = p != null && !p.closing;
        int gc = Theme.alpha(opened ? a1 : (gh ? 0xFFFFFFFF : GDIM), f);
        if (!m.settings.isEmpty()) chevron(c, sx - 18, ry + 5, opened, gh, gc, a1, f);
    }

    /** Small rounded button with an arrow: opens the settings menu of a module. */
    private void chevron(DrawContext c, int gx, int gy, boolean opened, boolean hov, int col, int a1, float f) {
        Theme.round(c, gx, gy, 14, 14, 4, Theme.alpha(opened ? a1 : 0xFFFFFFFF, f * (opened ? 0.25f : (hov ? 0.16f : 0.07f))));
        String ch = opened ? "v" : ">";
        c.drawText(textRenderer, ch, gx + 7 - textRenderer.getWidth(ch) / 2, gy + 3, col, false);
    }

    private void row(DrawContext c, Module m, int rx, int ry, int rw, int mx, int my, float f, int a1) {
        boolean hov = mx >= rx && mx <= rx + rw && my >= ry && my <= ry + ROW && my >= top() && my <= bottom();
        m.anim += ((m.on ? 1f : 0f) - m.anim) * 0.22f;
        glassCard(c, rx, ry, rw, ROW, 6, hov ? 1f : 0f, m.anim, a1, f);
        if (m.anim > 0.05f) Theme.round(c, rx + 1, ry + 7, 2, ROW - 14, 1, Theme.alpha(a1, f * m.anim));   // enabled marker
        c.drawTextWithShadow(textRenderer, m.name, rx + 9, ry + 5, Theme.alpha(Theme.TEXT, f));
        c.drawText(textRenderer, m.desc, rx + 9, ry + 16, Theme.alpha(GDIM, f), false);
        int sw = 26, sh = 12, sx = rx + rw - sw - 9, sy = ry + (ROW - sh) / 2;
        Theme.round(c, sx, sy, sw, sh, 6, Theme.alpha(Theme.mix(0xFF7A8396, a1, m.anim), f * (0.55f + 0.45f * m.anim)));
        Theme.round(c, sx + 2 + (int) ((sw - sh) * m.anim), sy + 2, sh - 4, sh - 4, 4, Theme.alpha(0xFFFFFFFF, f));

        if (m.key >= 0) {
            String kn = "[" + Binds.keyName(m.key) + "]";
            c.drawText(textRenderer, kn, rx + 14 + textRenderer.getWidth(m.name), ry + 5, Theme.alpha(a1, f), false);
        }
        {
            Panel p = panelOf(m);
            boolean gh = onGear(m, rx, rw, mx) && my >= ry && my <= ry + ROW;
            boolean opened = p != null && !p.closing;
            int gc = Theme.alpha(opened ? a1 : (gh ? 0xFFFFFFFF : GDIM), f);
            if (!m.settings.isEmpty()) chevron(c, sx - 18, ry + ROW / 2 - 7, opened, gh, gc, a1, f);
        }
    }

    // ---------- theme tab ----------
    private static final int SW = 30, SG = 8;

    private void renderTheme(DrawContext c, int mx, int my, float f, int a1, int a2) {
        int cx = cx(), cw = cw(), top = top() + slide();
        c.drawText(textRenderer, "Accent color", cx, top + 1, Theme.alpha(GDIM, f), false);
        int cols = 3, gw = (cw - SG * (cols - 1)) / cols;
        for (int i = 0; i < Theme.PRESET_NAMES.length; i++) {
            int sx = cx + (i % cols) * (gw + SG), sy = top + 14 + (i / cols) * (SW + SG);
            boolean sel = i == Theme.preset;
            boolean hov = mx >= sx && mx <= sx + gw && my >= sy && my <= sy + SW;
            glassCard(c, sx, sy, gw, SW, 6, hov ? 1f : 0f, sel ? 1f : 0f, Theme.grad(i, 0), f);
            int bx = sx + 8, bw = gw - 16;
            for (int k = 0; k < bw; k++) {
                c.fill(bx + k, sy + 5, bx + k + 1, sy + 13,
                    Theme.alpha(Theme.mix(Theme.grad(i, 0), Theme.grad(i, 1), k / (float) bw), f));
            }
            c.drawTextWithShadow(textRenderer, Theme.PRESET_NAMES[i], bx, sy + 17, Theme.alpha(sel ? Theme.TEXT : GDIM, f));
        }
        row(c, Modules.BLUR, cx, top + 14 + 3 * (SW + SG) + 6, cw, mx, my, f, a1);
    }

    // ---------- settings tab ----------
    private static final String[] MODEL_NAMES = {"N ring", "Haunter", "Cat"};
    private static final int MH = 62;

    private int modelRowY() { return top() + slide() + 14 + MH + 10; }
    private int accountY() { return modelRowY() + ROW + 24; }

    private void renderSettings(DrawContext c, int mx, int my, float f, int a1) {
        int cx = cx(), cw = cw(), top = top() + slide();
        c.drawText(textRenderer, "Logo model", cx, top + 1, Theme.alpha(GDIM, f), false);
        int n = MODEL_NAMES.length, gw = (cw - SG * (n - 1)) / n, cur = Modules.LOGO.idx("Model");
        for (int i = 0; i < n; i++) {
            int sx = cx + i * (gw + SG), sy = top + 14;
            boolean sel = i == cur, hov = mx >= sx && mx <= sx + gw && my >= sy && my <= sy + MH;
            glassCard(c, sx, sy, gw, MH, 6, hov ? 1f : 0f, sel ? 1f : 0f, a1, f);
            Avatar.draw(c, sx + gw / 2, sy + 26, 12, f, i, false);
            int nw = textRenderer.getWidth(MODEL_NAMES[i]);
            c.drawTextWithShadow(textRenderer, MODEL_NAMES[i], sx + (gw - nw) / 2, sy + MH - 14, Theme.alpha(sel ? Theme.TEXT : GDIM, f));
        }
        row(c, Modules.LOGO, cx, modelRowY(), cw, mx, my, f, a1);
        c.drawText(textRenderer, "Gear: animation, blizzard, glow", cx, modelRowY() + ROW + 6, Theme.alpha(GDIM, f * 0.8f), false);
        int ay = accountY();
        boolean ah = mx >= cx && mx <= cx + cw && my >= ay && my <= ay + ROW;
        glassCard(c, cx, ay, cw, ROW, 6, ah ? 1f : 0f, 0f, a1, f);
        c.drawTextWithShadow(textRenderer, "Account: " + Accounts.current(client), cx + 9, ay + 10, Theme.alpha(Theme.TEXT, f));
        c.drawText(textRenderer, ">", cx + cw - 14, ay + 10, Theme.alpha(a1, f), false);
    }

    // ---------- input ----------
    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            int k = input.key();
            if (k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE) listening.key = -1;
            else if (k != GLFW.GLFW_KEY_ESCAPE) listening.key = k;
            listening = null;
            return true;
        }
        int key = input.key();
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        if (searching) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { searching = false; query = ""; resetScroll(); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { searching = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); resetScroll(); }
                return true;
            }
            if (query.length() < 20) {
                if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) { query += (char) ('a' + (key - GLFW.GLFW_KEY_A)); resetScroll(); }
                else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) { query += (char) ('0' + (key - GLFW.GLFW_KEY_0)); resetScroll(); }
                else if (key == GLFW.GLFW_KEY_SPACE && !query.isEmpty()) query += ' ';
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !query.isEmpty()) { query = ""; resetScroll(); return true; }
        if (key == GLFW.GLFW_KEY_SLASH) { searching = true; return true; }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollTarget -= (float) v * 22f;
        return true;
    }

    private void applySlider(double mx) {
        if (activeSlider == null || sliderPanel == null) return;
        int tx = Math.round(sliderPanel.x) + PPAD + 1, tw = PW - PPAD * 2 - 2;
        activeSlider.setFromFraction((mx - tx) / tw);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();

        // panels first, topmost wins
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.closing || !inPanel(p, mx, my)) continue;
            panels.remove(i); panels.add(p);
            if (btn != 0) return true;
            if (my <= p.y + PHEAD) {
                int xx = Math.round(p.x) + PW - 16;
                if (mx >= xx - 3 && mx <= xx + 9) { p.closing = true; return true; }
                dragPanel = p; p.dragging = true; p.free = true;
                p.grabX = mx - p.x; p.grabY = my - p.y;
                return true;
            }
            int ry = Math.round(p.y) + PHEAD + PPAD;
            for (Setting s : p.m.settings) {
                if (!p.m.visible(s)) continue;
                int rh = rowH(s);
                if (my >= ry - 2 && my < ry + rh) {
                    if (s.isToggle) s.on = !s.on;
                    else if (s.isMode) s.cycle(mx > p.x + PW / 2.0 ? 1 : -1);
                    else { activeSlider = s; sliderPanel = p; applySlider(mx); }
                    return true;
                }
                ry += rh;
            }
            if (my >= ry - 2 && my < ry + 20) { listening = listening == p.m ? null : p.m; return true; }
            if (my >= ry + 20 && my < ry + 40) { p.m.bindMode ^= 1; return true; }
            return true;
        }

        // drag the main window by its header / logo area
        boolean inWin = mx >= x && mx <= x + W && my >= y && my <= y + H;
        if (btn == 0 && inWin && (my <= y + 40 || (mx <= x + side() && my <= y + 78))) {
            dragWin = true; winGX = mx - x; winGY = my - y;
            return true;
        }
        // drag HUD widgets that are outside the window
        if (btn == 0 && !inWin) {
            HudLayout.Item it = HudLayout.at(mx, my);
            if (it != null) {
                if (doubled) { it.dx = 0; it.dy = 0; return true; }
                dragHud = it; hudGX = mx - it.left(); hudGY = my - it.top();
                return true;
            }
        }

        // sidebar buttons: search, theme, collapse
        if (btn == 0) {
            for (int i = 0; i < 3; i++) {
                int[] r = btnRect(i);
                if (r != null && mx >= r[0] && mx <= r[0] + r[1] && my >= btnY() && my <= btnY() + 20) {
                    if (i == 0) { searching = !searching; if (!searching) query = ""; resetScroll(); }
                    else if (i == 1) { tab = Category.THEME; query = ""; searching = false; resetScroll(); tabFade = 0f; }
                    else collapsed = !collapsed;
                    return true;
                }
            }
        }

        // sidebar tabs
        if (btn == 0) {
            for (Category cat : Category.values()) {
                int ty = tabY(cat);
                if (mx >= x + 8 && mx <= x + side() - 8 && my >= ty && my <= ty + 22) {
                    if (cat != tab || !query.isEmpty() || searching) { tab = cat; query = ""; searching = false; resetScroll(); tabFade = 0f; }
                    return true;
                }
            }
        }

        // module rows
        int cx = cx(), cw = cw();
        if (!isGrid() && tab == Category.SETTINGS) {
            int top = top() + slide();
            if (btn == 0) {
                int n = MODEL_NAMES.length, gw = (cw - SG * (n - 1)) / n;
                for (int i = 0; i < n; i++) {
                    int sx = cx + i * (gw + SG), sy = top + 14;
                    if (mx >= sx && mx <= sx + gw && my >= sy && my <= sy + MH) { Modules.LOGO.setIdx("Model", i); return true; }
                }
            }
            int ay = accountY();
            if (btn == 0 && mx >= cx && mx <= cx + cw && my >= ay && my <= ay + ROW) { client.setScreen(new AccountScreen()); return true; }
            int ry = modelRowY();
            if (mx >= cx && mx <= cx + cw && my >= ry && my <= ry + ROW) {
                if (btn == 1 || (btn == 0 && onGear(Modules.LOGO, cx, cw, mx))) togglePanel(Modules.LOGO, ry);
                else if (btn == 0) Modules.LOGO.on = !Modules.LOGO.on;
                return true;
            }
        } else if (!isGrid() && tab == Category.THEME) {
            int top = top() + slide();
            if (btn == 0) {
                int cols = 3, gw = (cw - SG * (cols - 1)) / cols;
                for (int i = 0; i < Theme.PRESET_NAMES.length; i++) {
                    int sx = cx + (i % cols) * (gw + SG), sy = top + 14 + (i / cols) * (SW + SG);
                    if (mx >= sx && mx <= sx + gw && my >= sy && my <= sy + SW) { Theme.preset = i; return true; }
                }
            }
            int ry = top + 14 + 3 * (SW + SG) + 6;
            if (mx >= cx && mx <= cx + cw && my >= ry && my <= ry + ROW) {
                if (btn == 1 || (btn == 0 && onGear(Modules.BLUR, cx, cw, mx))) togglePanel(Modules.BLUR, ry);
                else if (btn == 0) Modules.BLUR.on = !Modules.BLUR.on;
                return true;
            }
        } else if (my >= top() && my <= bottom()) {
            List<Module> mods = curMods();
            int rw = cw - (maxScroll() > 0 ? 8 : 0);
            int gw = gridW(rw);
            for (int i = 0; i < mods.size(); i++) {
                int ry = gridY(i), rx = gridX(i, gw);
                if (mx >= rx && mx <= rx + gw && my >= ry && my <= ry + CH) {
                    Module m = mods.get(i);
                    if (btn == 1 || (btn == 0 && onGear(m, rx, gw, mx))) togglePanel(m, ry);
                    else if (btn == 0) m.on = !m.on;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mx = click.x(), my = click.y();
        if (dragWin) {
            winDX = (float) (mx - winGX - (width - W) / 2.0);
            winDY = (float) (my - winGY - (height - H) / 2.0);
            return true;
        }
        if (dragHud != null) {
            HudLayout.drag(dragHud, mx, my, hudGX, hudGY, width, height);
            return true;
        }
        if (dragPanel != null) {
            Panel p = dragPanel;
            p.tx = (float) Math.max(0, Math.min(width - PW, mx - p.grabX));
            p.ty = (float) Math.max(0, Math.min(height - panelH(p), my - p.grabY));
            return true;
        }
        if (activeSlider != null) { applySlider(mx); return true; }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragWin = false; dragHud = null;
        if (dragPanel != null) {
            dragPanel.dragging = false;
            SAVED.put(dragPanel.m, new float[]{dragPanel.tx, dragPanel.ty});
            dragPanel = null;
        }
        activeSlider = null; sliderPanel = null;
        return super.mouseReleased(click);
    }
}

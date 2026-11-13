package nelose;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NeloseScreen extends Screen {
    private static final int W = 500, H = 292, SIDE = 112, ROW = 28, STEP = 32;
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

    /** Frosted glass window: soft shadow, dark base for contrast, white sheen, light edge. */
    private static void glass(DrawContext c, int x, int y, int w, int h, int r, float f, boolean strong) {
        Theme.round(c, x + 1, y + 3, w, h, r, Theme.alpha(0xFF000000, 0.18f * f));
        Theme.round(c, x, y, w, h, r, Theme.alpha(GDARK, (strong ? 0.62f : 0.40f) * f));
        Theme.roundGrad(c, x, y, w, h, r, 0xFFFFFFFF, 0xFFC8D4EA, (strong ? 0.12f : 0.14f) * f);
        outline(c, x, y, w, h, r, Theme.alpha(0xFFFFFFFF, 0.34f * f));
        c.fill(x + r, y + 1, x + w - r, y + 2, Theme.alpha(0xFFFFFFFF, 0.14f * f));
    }

    /** Glass card; `on` tints it with the accent colour. */
    private static void glassCard(DrawContext c, int x, int y, int w, int h, int r, float hover, float on, int a1, float f) {
        int tint = Theme.mix(0xFFFFFFFF, a1, 0.5f);
        Theme.round(c, x, y, w, h, r, Theme.alpha(Theme.mix(0xFFFFFFFF, tint, on), f * (0.07f + 0.07f * hover + 0.06f * on)));
        outline(c, x, y, w, h, r, Theme.alpha(Theme.mix(0xFFFFFFFF, a1, on * 0.7f), f * (0.20f + 0.12f * hover + 0.25f * on)));
    }

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
    private int cx() { return x + SIDE + 14; }
    private int cw() { return W - SIDE - 28; }
    private int top() { return y + 50; }
    private int bottom() { return y + H - 12; }
    private int slide() { return (int) ((1f - tabFade) * 10); }
    private int rowY(int i) { return top() + i * STEP - (int) scroll + slide(); }

    private int maxScroll() {
        if (tab == Category.THEME || tab == Category.SETTINGS) return 0;
        return Math.max(0, ((Modules.of(tab).size() + 1) / 2) * CSTEP - (bottom() - top()));
    }

    /** Sidebar tab position: two groups (Modules, Client), animated between neighbours. */
    private int slotI(int i) {
        int g = Category.THEME.ordinal();
        return i < g ? y + 94 + i * 24 : y + 208 + (i - g) * 24;
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

        // header
        boolean hovHead = mx >= px && mx <= px + PW && my >= py && my <= py + PHEAD;
        Theme.round(c, px + 1, py + 1, PW - 2, PHEAD - 2, 6, Theme.alpha(0xFFFFFFFF, pa * (hovHead || p.dragging ? 0.14f : 0.08f)));
        c.drawTextWithShadow(textRenderer, p.m.name, px + 9, py + 8, Theme.alpha(Theme.TEXT, pa));
        // grip dots
        for (int i = 0; i < 3; i++) c.fill(px + PW - 36 + i * 4, py + 10, px + PW - 34 + i * 4, py + 12, Theme.alpha(GDIM, pa));
        // close "x"
        int xx = px + PW - 16, xy = py + 8;
        boolean hovX = mx >= xx - 3 && mx <= xx + 9 && my >= xy - 3 && my <= xy + 9;
        int xc = Theme.alpha(hovX ? 0xFFFFFFFF : GDIM, pa);
        for (int i = 0; i < 7; i++) { c.fill(xx + i, xy + i, xx + i + 1, xy + i + 1, xc); c.fill(xx + 6 - i, xy + i, xx + 7 - i, xy + i + 1, xc); }
        Theme.gradBar(c, px + 8, py + PHEAD - 1, PW - 16, 1, a1, a2, PW - 16, pa * 0.9f);

        // settings
        int ry = py + PHEAD + PPAD;
        for (Setting s : p.m.settings) {
            if (!p.m.visible(s)) continue;
            if (s.isToggle) {
                s.anim += ((s.on ? 1f : 0f) - s.anim) * 0.25f;
                c.drawTextWithShadow(textRenderer, s.name, px + PPAD + 1, ry + 6, Theme.alpha(Theme.TEXT, pa));
                int sx = px + PW - PPAD - 24, sy = ry + 5;
                Theme.round(c, sx, sy, 24, 11, 5, Theme.alpha(Theme.mix(0xFF7A8396, a1, s.anim), pa * (0.55f + 0.45f * s.anim)));
                Theme.round(c, sx + 2 + (int) (13 * s.anim), sy + 2, 7, 7, 3, Theme.alpha(0xFFFFFFFF, pa));
            } else if (s.isMode) {
                c.drawTextWithShadow(textRenderer, s.name, px + PPAD + 1, ry + 6, Theme.alpha(Theme.TEXT, pa));
                String v = "< " + s.display() + " >";
                c.drawText(textRenderer, v, px + PW - PPAD - textRenderer.getWidth(v), ry + 6, Theme.alpha(a1, pa), false);
            } else {
                c.drawTextWithShadow(textRenderer, s.name, px + PPAD + 1, ry + 2, Theme.alpha(Theme.TEXT, pa));
                String v = s.display();
                c.drawText(textRenderer, v, px + PW - PPAD - textRenderer.getWidth(v), ry + 2, Theme.alpha(a1, pa), false);
                int tx = px + PPAD + 1, tw = PW - PPAD * 2 - 2, ty = ry + 16;
                boolean act = activeSlider == s;
                Theme.round(c, tx, ty, tw, 4, 2, Theme.alpha(0x55FFFFFF, pa));
                int fw = Math.max(3, (int) (tw * s.fraction()));
                Theme.gradBar(c, tx, ty + 1, fw, 2, a1, a2, tw, pa);
                int kr = act ? 5 : 4;
                Theme.round(c, tx + fw - kr, ty + 2 - kr, kr * 2, kr * 2, kr, Theme.alpha(0xFFFFFFFF, pa));
            }
            ry += rowH(s);
        }
        // bind rows
        String bv = listening == p.m ? "press a key..." : "[" + Binds.keyName(p.m.key) + "]";
        c.drawTextWithShadow(textRenderer, "Bind", px + PPAD + 1, ry + 6, Theme.alpha(Theme.TEXT, pa));
        c.drawText(textRenderer, bv, px + PW - PPAD - textRenderer.getWidth(bv), ry + 6, Theme.alpha(listening == p.m ? 0xFFFFFFFF : a1, pa), false);
        String bm = "< " + (p.m.bindMode == 0 ? "Toggle" : "Hold") + " >";
        c.drawTextWithShadow(textRenderer, "Bind mode", px + PPAD + 1, ry + 26, Theme.alpha(Theme.TEXT, pa));
        c.drawText(textRenderer, bm, px + PW - PPAD - textRenderer.getWidth(bm), ry + 26, Theme.alpha(a1, pa), false);
    }

    private boolean inPanel(Panel p, double mx, double my) {
        return mx >= p.x && mx <= p.x + PW && my >= p.y && my <= p.y + panelH(p);
    }

    // ---------- render ----------
    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {
        if (Modules.BLUR.on) c.fill(0, 0, width, height, Theme.alpha(0xFF000000, Modules.BLUR.f("Dim") * fade()));
        if (Modules.BLUR.b("GUI snow")) Snow.draw(c, width, height, 70, 0.8f * fade());
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c, mx, my, delta);
        float f = fade();
        winDX = Math.max(-(width - W) / 2f, Math.min((width - W) / 2f, winDX));
        winDY = Math.max(-(height - H) / 2f, Math.min((height - H) / 2f, winDY));
        x = (width - W) / 2 + Math.round(winDX);
        y = (height - H) / 2 + Math.round(winDY) + (int) ((1f - f) * (animMode() == 1 ? 30 : 12));
        int a1 = Theme.accent(), a2 = Theme.accent2();

        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * 0.25f;
        tabPos += (tab.ordinal() - tabPos) * 0.25f;
        tabFade += (1f - tabFade) * 0.2f;

        if (closeAt != 0 && closeProg() >= 1f) { super.close(); return; }

        // settings menus are docked to the side of the GUI and slide out from behind it
        for (Panel p : panels) {
            if (!p.free) {
                boolean right = x + W + 8 + PW <= width - 4;
                p.tx = right ? x + W + 8 : x - PW - 8;
                p.ty = Math.max(4, Math.min(height - panelH(p) - 4, y));
            }
            renderPanel(c, p, mx, my, a1, a2);
        }
        panels.removeIf(p -> p.closing && p.open < 0.02f);

        // outlines of the movable HUD widgets
        HudLayout.Item hovItem = dragHud != null ? dragHud : HudLayout.at(mx, my);
        for (HudLayout.Item it : HudLayout.ITEMS.values()) {
            if (!it.visible()) continue;
            boolean hv = it == hovItem;
            int ox = Math.round(it.left()), oy = Math.round(it.top());
            int oc = Theme.alpha(hv ? a1 : 0xFFFFFFFF, f * (hv ? 0.9f : 0.28f));
            c.fill(ox - 2, oy - 2, ox + it.w + 2, oy - 1, oc);
            c.fill(ox - 2, oy + it.h + 1, ox + it.w + 2, oy + it.h + 2, oc);
            c.fill(ox - 2, oy - 2, ox - 1, oy + it.h + 2, oc);
            c.fill(ox + it.w + 1, oy - 2, ox + it.w + 2, oy + it.h + 2, oc);
        }
        String tip = "Drag the window and HUD widgets to move them · double click a widget to reset";
        c.drawCenteredTextWithShadow(textRenderer, tip, width / 2, height - 12, Theme.alpha(GDIM, f * 0.8f));

        glass(c, x, y, W, H, 10, f, false);
        Theme.gradBar(c, x + 14, y + 2, W - 28, 1, a1, a2, W - 28, f * 0.8f);              // accent line on top
        Theme.gradBar(c, x + 24, y + H - 2, W - 48, 1, a2, a1, W - 48, f * 0.3f);       // and a faint one below
        c.fill(x + SIDE + 8, y + 40, x + W - 10, y + 41, Theme.alpha(0xFFFFFFFF, f * 0.05f));   // header divider

        // sidebar
        c.fill(x + SIDE, y + 12, x + SIDE + 1, y + H - 12, Theme.alpha(0xFFFFFFFF, f * 0.12f));
        Avatar.draw(c, x + SIDE / 2, y + 34, 18, f);
        int nw = textRenderer.getWidth("Nelose");
        c.drawTextWithShadow(textRenderer, "Nelose", x + (SIDE - nw) / 2, y + 60, Theme.alpha(Theme.TEXT, f));
        Theme.gradBar(c, x + (SIDE - nw) / 2, y + 71, nw, 1, a1, a2, nw, f);

        // tabs on the side with a sliding highlight
        int hy = slotY(tabPos);
        Theme.round(c, x + 8, hy, SIDE - 16, 22, 6, Theme.alpha(0xFFFFFFFF, f * 0.14f));
        outline(c, x + 8, hy, SIDE - 16, 22, 6, Theme.alpha(0xFFFFFFFF, f * 0.22f));
        Theme.round(c, x + 8, hy + 6, 2, 10, 1, Theme.alpha(a1, f));
        c.drawText(textRenderer, "Modules", x + 12, y + 82, Theme.alpha(GDIM, f * 0.65f), false);
        c.drawText(textRenderer, "Client", x + 12, y + 196, Theme.alpha(GDIM, f * 0.65f), false);
        for (Category cat : Category.values()) {
            int ty = tabY(cat);
            boolean sel = cat == tab;
            boolean hov = mx >= x + 8 && mx <= x + SIDE - 8 && my >= ty && my <= ty + 22;
            if (hov && !sel) Theme.round(c, x + 8, ty, SIDE - 16, 22, 6, Theme.alpha(0xFFFFFFFF, f * 0.07f));
            c.drawTextWithShadow(textRenderer, cat.title, x + 20 + (sel ? 2 : 0), ty + 7,
                Theme.alpha(sel ? Theme.TEXT : (hov ? 0xFFC8C8D2 : GDIM), f));
        }
        c.drawTextWithShadow(textRenderer, "v1.5 · 1.21.11", x + 14, y + H - 18, Theme.alpha(GDIM, f));

        // header
        int cx = cx(), cw = cw();
        List<Module> mods = Modules.of(tab);
        long en = mods.stream().filter(m -> m.on).count();
        String cnt = tab == Category.SETTINGS ? "" : en + "/" + mods.size();
        float hf = f * (0.4f + 0.6f * tabFade);
        c.drawTextWithShadow(textRenderer, tab.title, cx, y + 14, Theme.alpha(Theme.TEXT, hf));
        c.drawTextWithShadow(textRenderer, cnt, cx + cw - textRenderer.getWidth(cnt), y + 14, Theme.alpha(a1, hf));
        c.drawTextWithShadow(textRenderer, tab.sub, cx, y + 27, Theme.alpha(GDIM, hf));
        String hint = "Right click for settings";
        c.drawText(textRenderer, hint, cx + cw - textRenderer.getWidth(hint), y + 27, Theme.alpha(GDIM, hf * 0.7f), false);

        float rf = f * tabFade;
        int top = top(), bottom = bottom();
        c.enableScissor(cx - 2, top - 1, cx + cw + 2, bottom);
        if (tab == Category.THEME) {
            renderTheme(c, mx, my, rf, a1, a2);
        } else if (tab == Category.SETTINGS) {
            renderSettings(c, mx, my, rf, a1);
        } else {
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
        return mx >= rx + rw - 9 - 26 - 22 && mx <= rx + rw - 9 - 26 - 3;
    }

    /** Module card in the two-column grid: name and toggle on top, wrapped description below. */
    private void card(DrawContext c, Module m, int rx, int ry, int rw, int mx, int my, float f, int a1) {
        boolean hov = mx >= rx && mx <= rx + rw && my >= ry && my <= ry + CH && my >= top() && my <= bottom();
        m.anim += ((m.on ? 1f : 0f) - m.anim) * 0.22f;
        glassCard(c, rx, ry, rw, CH, 7, hov ? 1f : 0f, m.anim, a1, f);
        c.drawTextWithShadow(textRenderer, m.name, rx + 9, ry + 6, Theme.alpha(Theme.TEXT, f));
        if (m.key >= 0) {
            String kn = "[" + Binds.keyName(m.key) + "]";
            c.drawText(textRenderer, kn, rx + 14 + textRenderer.getWidth(m.name), ry + 6, Theme.alpha(a1, f), false);
        }
        List<String> lines = wrap(m.desc, rw - 18, 2);
        for (int k = 0; k < lines.size(); k++)
            c.drawText(textRenderer, lines.get(k), rx + 9, ry + 18 + k * 10, Theme.alpha(GDIM, f * 0.85f), false);
        int sw = 26, sh = 12, sx = rx + rw - sw - 9, sy = ry + 6;
        Theme.round(c, sx, sy, sw, sh, 6, Theme.alpha(Theme.mix(0xFF7A8396, a1, m.anim), f * (0.55f + 0.45f * m.anim)));
        Theme.round(c, sx + 2 + (int) ((sw - sh) * m.anim), sy + 2, sh - 4, sh - 4, 4, Theme.alpha(0xFFFFFFFF, f));
        Panel p = panelOf(m);
        boolean gh = onGear(m, rx, rw, mx) && my >= ry && my <= ry + CH;
        boolean opened = p != null && !p.closing;
        int gc = Theme.alpha(opened ? a1 : (gh ? 0xFFFFFFFF : GDIM), f);
        int gx = sx - 14, gy = ry + 6;
        for (int i = 0; i < 3; i++) c.fill(gx, gy + i * 4, gx + 3, gy + i * 4 + 2, gc);
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
            int gx = sx - 14, gy = ry + ROW / 2 - 5;
            for (int i = 0; i < 3; i++) c.fill(gx, gy + i * 4, gx + 3, gy + i * 4 + 2, gc);
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
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
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
        if (btn == 0 && inWin && (my <= y + 40 || (mx <= x + SIDE && my <= y + 78))) {
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

        // sidebar tabs
        if (btn == 0) {
            for (Category cat : Category.values()) {
                int ty = tabY(cat);
                if (mx >= x + 8 && mx <= x + SIDE - 8 && my >= ty && my <= ty + 22) {
                    if (cat != tab) { tab = cat; scroll = scrollTarget = 0; tabFade = 0f; }
                    return true;
                }
            }
        }

        // module rows
        int cx = cx(), cw = cw();
        if (tab == Category.SETTINGS) {
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
        } else if (tab == Category.THEME) {
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
            List<Module> mods = Modules.of(tab);
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

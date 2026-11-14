package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The top watermark. Built from small segments (client name, player, server, fps, ping, clock)
 * that can be drawn as separate pills, one joined bar, or plain text. Everything is configurable
 * in the Watermark settings; the text of the name segment is changed with  .wm your text  in chat.
 */
public final class Watermark {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm"), FMT_S = DateTimeFormatter.ofPattern("HH:mm:ss"),
        FMT12 = DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH), FMT12_S = DateTimeFormatter.ofPattern("hh:mm:ss a", java.util.Locale.ENGLISH);
    private static final int GOOD = 0xFF6EE7A0, OK = 0xFFFBBF24, BAD = 0xFFFB7185;

    // segments, in drawing order
    private static final int NAME = 0, USER = 1, SERVER = 2, FPS = 3, PING = 4, TIME = 5, N = 6;
    private static final int ICON = 10, IGAP = 4, H = 22;

    private static final boolean[] ACTIVE = new boolean[N];
    private static final String[] VAL = new String[N], UNIT = new String[N];
    private static final int[] VCOL = new int[N];

    private static final int GRAPH_W = 24, GRAPH_H = 8;
    private static final int[] HIST = new int[GRAPH_W];
    private static long lastSample;
    private static int pingMs, lastPreset = -2;

    /** Text of the name segment (change with .wm text). */
    public static String text = Nelose.NAME;

    /** Bottom edge of the watermark block, used by the info panel. */
    public static int bottom = 8;

    // ---------- chat command ----------
    /** Handles  .wm text  /  .wm reset. Returns true when the chat line was a command (do not send it). */
    public static boolean command(String raw) {
        if (!raw.equals(".wm") && !raw.startsWith(".wm ")) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        String arg = raw.substring(3).trim();
        if (arg.isEmpty()) {
            say(mc, "[Nelose] Текст ватермарка: " + text + "   (изменить: .wm текст, сбросить: .wm reset)");
        } else if (arg.equalsIgnoreCase("reset")) {
            text = Nelose.NAME;
            Config.save();
            say(mc, "[Nelose] Текст ватермарка сброшен: " + text);
        } else {
            text = arg.length() > 24 ? arg.substring(0, 24) : arg;
            Config.save();
            say(mc, "[Nelose] Текст ватермарка сохранён: " + text);
        }
        return true;
    }

    private static void say(MinecraftClient mc, String s) {
        mc.inGameHud.getChatHud().addMessage(Text.literal(s));
    }

    // ---------- rendering ----------
    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        bottom = 8;
        if (!Modules.WATERMARK.on || mc.options.hudHidden || mc.player == null) return;
        Module M = Modules.WATERMARK;
        TextRenderer tr = mc.textRenderer;

        int pre = M.idx("Preset");
        if (lastPreset == -2) lastPreset = pre;
        else if (pre != lastPreset) { lastPreset = pre; applyPreset(M, pre); }

        long t0 = System.currentTimeMillis();
        if (t0 - lastSample >= 250) {
            lastSample = t0;
            System.arraycopy(HIST, 1, HIST, 0, GRAPH_W - 1);
            HIST[GRAPH_W - 1] = mc.getCurrentFps();
        }

        boolean icons = M.b("Icons"), anim = M.b("Animated");
        int style = M.idx("Style");
        float scale = M.f("Scale");

        // ----- collect the data of every segment
        ACTIVE[NAME] = M.b("Client name") && !text.isEmpty();
        VAL[NAME] = text; UNIT[NAME] = ""; VCOL[NAME] = Theme.TEXT;

        ACTIVE[USER] = M.b("Username");
        VAL[USER] = mc.player.getName().getString(); UNIT[USER] = ""; VCOL[USER] = Theme.TEXT;

        ACTIVE[SERVER] = M.b("Server");
        VAL[SERVER] = tr.trimToWidth(serverName(mc), 130); UNIT[SERVER] = ""; VCOL[SERVER] = Theme.TEXT;

        ACTIVE[FPS] = Modules.FPS.on;
        int fps = mc.getCurrentFps();
        VAL[FPS] = String.valueOf(fps); UNIT[FPS] = "fps"; VCOL[FPS] = fps >= 60 ? GOOD : (fps >= 30 ? OK : BAD);

        ACTIVE[PING] = Modules.PING.on;
        PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int ms = e == null ? 0 : e.getLatency();
        pingMs = ms;
        VAL[PING] = String.valueOf(ms); UNIT[PING] = "ms"; VCOL[PING] = ms < 60 ? GOOD : (ms < 150 ? OK : BAD);

        ACTIVE[TIME] = Modules.TIME.on;
        VAL[TIME] = LocalTime.now().format(Modules.TIME.b("12 hour")
            ? (Modules.TIME.b("Seconds") ? FMT12_S : FMT12) : (Modules.TIME.b("Seconds") ? FMT_S : FMT)); UNIT[TIME] = ""; VCOL[TIME] = Theme.TEXT;

        // ----- colors
        int a1, a2;
        if (M.idx("Color") == 1) {
            float hue = M.f("Hue");
            a1 = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.5f, 1f) & 0xFFFFFF);
            a2 = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue + 0.08f, 0.7f, 0.95f) & 0xFFFFFF);
        } else {
            a1 = Theme.accent(); a2 = Theme.accent2();
        }

        // ----- layout
        int pad = style == 2 ? 2 : 8, gap = (int) M.f("Spacing");
        int[] idx = new int[N], ws = new int[N];
        int n = 0, sum = 0;
        for (int i = 0; i < N; i++) {
            if (!ACTIVE[i]) continue;
            idx[n] = i; ws[n] = segW(tr, i, icons); sum += ws[n]; n++;
        }
        if (n == 0) return;
        int total;
        if (style == 0) total = sum + n * pad * 2 + gap * (n - 1);
        else if (style == 1) total = sum + pad * 2 + 11 * (n - 1);
        else total = sum + pad * 2 + 12 * (n - 1);

        int sw = mc.getWindow().getScaledWidth();
        int bw = Math.round(total * scale), bh = Math.round(H * scale);
        int align = M.idx("Align");
        int x = align == 0 ? 8 : (align == 1 ? (sw - bw) / 2 : sw - 8 - bw), y = 8;

        long now = System.currentTimeMillis();
        float pulse = anim ? (float) (0.5 + 0.5 * Math.sin(now / 900.0)) : 0.5f;
        int r = (int) M.f("Rounding");

        HudLayout.begin(ctx, "watermark", x, y, bw, bh);
        var mat = ctx.getMatrices();
        mat.pushMatrix();
        mat.translate((float) x, (float) y);
        mat.scale(scale, scale);

        if (style == 0) {
            int cx = 0;
            for (int k = 0; k < n; k++) {
                int pw = ws[k] + pad * 2;
                card(ctx, M, cx, 0, pw, r, a1, a2, pulse, now, anim);
                seg(ctx, tr, idx[k], cx + pad, 0, a1, a2, icons, anim, now, pulse);
                cx += pw + gap;
            }
        } else {
            if (style == 1) card(ctx, M, 0, 0, total, r, a1, a2, pulse, now, anim);
            int cx = pad;
            for (int k = 0; k < n; k++) {
                seg(ctx, tr, idx[k], cx, 0, a1, a2, icons, anim, now, pulse);
                cx += ws[k];
                if (k < n - 1) {
                    if (style == 1) ctx.fill(cx + 5, 6, cx + 6, H - 6, Theme.alpha(0xFFFFFFFF, 0.10f));
                    else ctx.drawTextWithShadow(tr, "|", cx + 4, (H - 8) / 2, Theme.alpha(a1, 0.6f));
                    cx += style == 1 ? 11 : 12;
                }
            }
        }
        mat.popMatrix();
        HudLayout.end(ctx);
        bottom = y + bh;
    }

    private static String serverName(MinecraftClient mc) {
        ServerInfo si = mc.getCurrentServerEntry();
        if (si == null) return mc.isInSingleplayer() ? "Singleplayer" : "Offline";
        String a = si.address == null ? "" : si.address;
        if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
        return a.isEmpty() ? "Server" : a;
    }

    private static int segW(TextRenderer tr, int i, boolean icons) {
        if (i == NAME) return (Modules.LOGO.on ? 22 : 0) + tr.getWidth(VAL[NAME]);
        return (icons ? ICON + IGAP : 0) + tr.getWidth(VAL[i]) + (UNIT[i].isEmpty() ? 0 : 3 + tr.getWidth(UNIT[i]))
            + (i == FPS && Modules.WATERMARK.b("FPS graph") ? 6 + GRAPH_W : 0);
    }

    // ---------- pieces ----------
    /** Background of a pill or of the whole bar: glow, gradient body, thin border, accent line. */
    private static void card(DrawContext ctx, Module M, int x, int y, int w, int r, int a1, int a2, float pulse, long now, boolean anim) {
        Theme.roundGrad(ctx, x, y, w, H, r, 0xFF17171D, 0xFF0A0A0E, M.f("Opacity"));
        if (M.b("Border")) outline(ctx, x, y, w, H, r, Theme.alpha(Theme.mix(Theme.BORDER, a1, 0.30f + 0.25f * pulse), Math.min(1f, M.f("Opacity") + 0.1f)));
        if (M.b("Border") && w - 2 * r > 6) Theme.gradBar(ctx, x + r, y, w - 2 * r, 1, a1, a2, w, 0.75f);
        if (w - 2 * r > 6) ctx.fill(x + r, y + 1, x + w - r, y + 2, Theme.alpha(0xFFFFFFFF, 0.07f));
        if (M.b("Accent line") && w > 24) {
            int lx = x + 9, lw = w - 18, ly = y + H - 3;
            Theme.gradBar(ctx, lx, ly, lw, 1, a2, a1, lw, 0.22f);
            if (anim) {
                int seg = Math.min(34, lw), sx = lx + (int) ((lw + seg) * ((now % 2600L) / 2600f)) - seg;
                int l = Math.max(lx, sx), rr = Math.min(lx + lw, sx + seg);
                if (rr > l) Theme.gradBar(ctx, l, ly, rr - l, 1, a1, 0xFFFFFFFF, seg, 0.85f);
            }
        }
    }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** 1px rounded outline (does not paint the inside, so translucent bodies stay clean). */
    private static void outline(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.min(r, Math.min(w, h) / 2);
        int prev = inset(r, 0);
        c.fill(x + prev, y, x + w - prev, y + 1, col);
        c.fill(x + prev, y + h - 1, x + w - prev, y + h, col);
        for (int i = 1; i < r; i++) {
            int in = inset(r, i), e = Math.max(in + 1, prev);
            c.fill(x + in, y + i, x + e, y + i + 1, col);
            c.fill(x + w - e, y + i, x + w - in, y + i + 1, col);
            c.fill(x + in, y + h - 1 - i, x + e, y + h - i, col);
            c.fill(x + w - e, y + h - 1 - i, x + w - in, y + h - i, col);
            prev = in;
        }
        c.fill(x, y + r, x + 1, y + h - r, col);
        c.fill(x + w - 1, y + r, x + w, y + h - r, col);
    }

    private static void seg(DrawContext ctx, TextRenderer tr, int i, int x, int y, int a1, int a2, boolean icons, boolean anim, long now, float pulse) {
        int cy = y + H / 2, ty = y + (H - 8) / 2;
        if (i == NAME) {
            int tx = x;
            if (Modules.LOGO.on) {
                boolean wg = Modules.LOGO.b("Winter glow");   // no translucent halo behind the logo here
                Modules.LOGO.setB("Winter glow", false);
                Avatar.draw(ctx, tx + 9, cy, 8, 1f, Modules.LOGO.idx("Model"), false);
                Modules.LOGO.setB("Winter glow", wg);
                tx += 22;
            }
            String t = VAL[NAME];
            float sweep = anim ? (now % 3200L) / 3200f * 1.7f - 0.35f : -1f;   // highlight running through the name
            for (int k = 0; k < t.length(); k++) {
                String ch = t.substring(k, k + 1);
                float kk = t.length() <= 1 ? 0f : k / (float) (t.length() - 1);
                float sh = Math.max(0f, 1f - Math.abs(kk - sweep) * 3.2f);
                ctx.drawTextWithShadow(tr, ch, tx, ty, Theme.mix(Theme.mix(a1, a2, kk), 0xFFFFFFFF, sh * 0.85f));
                tx += tr.getWidth(ch);
            }
            return;
        }
        int tx = x;
        if (icons) {
            icon(ctx, i, tx, cy, a1, pulse);
            tx += ICON + IGAP;
        }
        ctx.drawTextWithShadow(tr, VAL[i], tx, ty, VCOL[i]);
        tx += tr.getWidth(VAL[i]);
        if (!UNIT[i].isEmpty()) {
            ctx.drawText(tr, UNIT[i], tx + 3, ty, Theme.TEXT_DIM, false);
            tx += 3 + tr.getWidth(UNIT[i]);
        }
        if (i == FPS && Modules.WATERMARK.b("FPS graph")) {
            int gx = tx + 6, base = cy + GRAPH_H / 2 + 1, mx = 1;
            for (int v : HIST) mx = Math.max(mx, v);
            for (int k = 0; k < GRAPH_W; k++) {
                int hh = HIST[k] <= 0 ? 0 : Math.max(1, HIST[k] * GRAPH_H / mx);
                int col = HIST[k] >= 60 ? GOOD : (HIST[k] >= 30 ? OK : BAD);
                ctx.fill(gx + k, base - GRAPH_H, gx + k + 1, base, Theme.alpha(0xFFFFFFFF, 0.05f));
                if (hh > 0) ctx.fill(gx + k, base - hh, gx + k + 1, base, Theme.alpha(col, 0.55f + 0.45f * k / GRAPH_W));
            }
        }
    }

    /** Ready-made looks; "Custom" leaves your own settings alone. */
    private static void applyPreset(Module M, int p) {
        switch (p) {
            case 1 -> {   // Glass
                M.setIdx("Style", 0); M.setF("Rounding", 11); M.setF("Opacity", 0.55); M.setF("Spacing", 5);
                M.setB("Border", true); M.setB("Accent line", false); M.setB("Animated", true);
            }
            case 2 -> {   // Neon
                M.setIdx("Style", 0); M.setF("Rounding", 8); M.setF("Opacity", 0.95); M.setF("Spacing", 4);
                M.setB("Border", true); M.setB("Accent line", true); M.setB("Animated", true);
            }
            case 3 -> {   // Classic
                M.setIdx("Style", 1); M.setF("Rounding", 7); M.setF("Opacity", 0.96);
                M.setB("Border", true); M.setB("Accent line", true); M.setB("Animated", true);
            }
            case 4 -> {   // Minimal
                M.setIdx("Style", 2); M.setB("Border", false); M.setB("Accent line", false);
            }
            default -> {}
        }
    }

    /** Small pixel icons, 10 px wide, centered on cy. */
    private static void icon(DrawContext c, int i, int x, int cy, int col, float pulse) {
        int dark = 0xFF0C0C10;
        switch (i) {
            case USER -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                Theme.round(c, x - 1, cy - 6, ICON + 2, ICON + 2, 3, Theme.alpha(0xFFFFFFFF, 0.14f));
                PlayerSkinDrawer.draw(c, mc.player.getSkin(), x, cy - 5, ICON);
            }
            case SERVER -> {   // cloud
                Theme.round(c, x, cy - 1, 10, 5, 2, col);
                Theme.round(c, x + 1, cy - 4, 5, 5, 2, col);
                Theme.round(c, x + 4, cy - 5, 5, 6, 2, col);
            }
            case FPS -> {      // monitor
                Theme.round(c, x, cy - 5, 10, 7, 2, col);
                Theme.round(c, x + 1, cy - 4, 8, 5, 1, dark);
                c.fill(x + 3, cy - 2, x + 7, cy - 1, Theme.alpha(col, 0.5f + 0.5f * pulse));
                c.fill(x + 4, cy + 2, x + 6, cy + 3, col);
                c.fill(x + 2, cy + 3, x + 8, cy + 4, col);
            }
            case PING -> {     // signal bars, lit by how good the connection is
                int lit = pingMs < 70 ? 3 : (pingMs < 150 ? 2 : 1);
                int q = VCOL[PING], off = Theme.alpha(0xFFFFFFFF, 0.14f);
                c.fill(x, cy + 1, x + 3, cy + 5, lit >= 1 ? q : off);
                c.fill(x + 4, cy - 2, x + 7, cy + 5, lit >= 2 ? q : off);
                c.fill(x + 8, cy - 5, x + 10, cy + 5, lit >= 3 ? q : off);
            }
            case TIME -> {     // clock
                Theme.round(c, x, cy - 5, 10, 10, 5, col);
                Theme.round(c, x + 1, cy - 4, 8, 8, 4, dark);
                c.fill(x + 5, cy - 3, x + 6, cy + 1, col);
                c.fill(x + 5, cy, x + 8, cy + 1, col);
            }
            default -> {}
        }
    }

    private Watermark() {}
}

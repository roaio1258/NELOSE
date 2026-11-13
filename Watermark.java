package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Watermark {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm"), FMT_S = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String[] CH = new String[Nelose.NAME.length()];
    private static final String[] VAL = new String[3], UNIT = new String[3];
    private static final int[] DOT = new int[3];
    static {
        for (int i = 0; i < CH.length; i++) CH[i] = Nelose.NAME.substring(i, i + 1);
    }
    private static final int GOOD = 0xFF6EE7A0, OK = 0xFFFBBF24, BAD = 0xFFFB7185;

    /** Bottom edge of the watermark block, used by the info panel. */
    public static int bottom = 8;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        bottom = 8;
        if (!Modules.WATERMARK.on || mc.options.hudHidden || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;

        int n = 0;
        if (Modules.FPS.on) {
            int fps = mc.getCurrentFps();
            VAL[n] = String.valueOf(fps); UNIT[n] = "fps"; DOT[n] = fps >= 60 ? GOOD : (fps >= 30 ? OK : BAD); n++;
        }
        if (Modules.PING.on) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null
                : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            int ms = e == null ? 0 : e.getLatency();
            VAL[n] = String.valueOf(ms); UNIT[n] = "ms"; DOT[n] = ms < 60 ? GOOD : (ms < 150 ? OK : BAD); n++;
        }
        if (Modules.TIME.on) {
            VAL[n] = LocalTime.now().format(Modules.TIME.b("Seconds") ? FMT_S : FMT); UNIT[n] = ""; DOT[n] = 0; n++;
        }

        int pad = 7, gap = 10, h = 24;
        int x = 8, y = 8;
        String name = Nelose.NAME;
        boolean logo = Modules.LOGO.on;
        int logoW = logo ? 24 : 0;
        int w = pad + logoW + tr.getWidth(name) + gap;
        for (int i = 0; i < n; i++) {
            w += (DOT[i] != 0 ? 8 : 0) + tr.getWidth(VAL[i]) + (UNIT[i].isEmpty() ? 0 : 3 + tr.getWidth(UNIT[i])) + gap + 1;
        }
        w += pad - gap;

        int a1 = Theme.accent(), a2 = Theme.accent2();
        long now = System.currentTimeMillis();
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 900.0));

        HudLayout.begin(ctx, "watermark", x, y, w, h);
        if (Modules.WATERMARK.b("Glow")) Theme.glow(ctx, x, y, w, h, 7, a1, 3);
        // card: accent-tinted border that breathes, dark gradient body, soft top highlight
        Theme.round(ctx, x, y, w, h, 7, Theme.mix(Theme.BORDER, a1, 0.22f + 0.18f * pulse));
        Theme.roundGrad(ctx, x + 1, y + 1, w - 2, h - 2, 6, 0xFF17171D, 0xFF0A0A0E, 0.96f);
        ctx.fill(x + 7, y + 1, x + w - 7, y + 2, Theme.alpha(0xFFFFFFFF, 0.07f));

        // bottom accent line with a light running along it
        int lx = x + 9, lw = w - 18, ly = y + h - 3;
        Theme.gradBar(ctx, lx, ly, lw, 1, a2, a1, lw, 0.22f);
        int seg = Math.min(34, lw), sx = lx + (int) ((lw + seg) * ((now % 2600L) / 2600f)) - seg;
        int l = Math.max(lx, sx), r = Math.min(lx + lw, sx + seg);
        if (r > l) Theme.gradBar(ctx, l, ly, r - l, 1, a1, 0xFFFFFFFF, seg, 0.85f);

        // logo without the blizzard: the falling snow made a streak above the watermark
        if (logo) Avatar.draw(ctx, x + pad + 9, y + h / 2, 8, 1f, Modules.LOGO.idx("Model"), false);
        int tx = x + pad + logoW, ty = y + (h - 8) / 2;
        float sweep = (now % 3200L) / 3200f * 1.7f - 0.35f;   // highlight running through the name
        for (int i = 0; i < CH.length; i++) {
            float k = CH.length <= 1 ? 0f : i / (float) (CH.length - 1);
            float sh = Math.max(0f, 1f - Math.abs(k - sweep) * 3.2f);
            int col = Theme.mix(Theme.mix(a1, a2, k), 0xFFFFFFFF, sh * 0.85f);
            ctx.drawTextWithShadow(tr, CH[i], tx, ty, col);
            tx += tr.getWidth(CH[i]);
        }
        tx += gap;

        for (int i = 0; i < n; i++) {
            ctx.fill(tx - 6, y + 6, tx - 5, y + h - 6, Theme.alpha(0xFFFFFFFF, 0.10f));
            if (DOT[i] != 0) {
                Theme.round(ctx, tx - 1, ty + 1, 6, 6, 3, Theme.alpha(DOT[i], 0.18f + 0.12f * pulse));   // soft halo
                Theme.round(ctx, tx, ty + 2, 4, 4, 2, DOT[i]);
                tx += 8;
            }
            ctx.drawTextWithShadow(tr, VAL[i], tx, ty, Theme.TEXT);
            tx += tr.getWidth(VAL[i]);
            if (!UNIT[i].isEmpty()) {
                ctx.drawText(tr, UNIT[i], tx + 3, ty, Theme.TEXT_DIM, false);
                tx += 3 + tr.getWidth(UNIT[i]);
            }
            tx += gap + 1;
        }
        HudLayout.end(ctx);
        bottom = y + h;
    }

    private Watermark() {}
}

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

        int pad = 7, gap = 9, h = 22;
        int x = 8, y = 8;
        String name = Nelose.NAME;
        boolean logo = Modules.LOGO.on;
        int logoW = logo ? 22 : 0;
        int w = pad + logoW + tr.getWidth(name) + gap;
        for (int i = 0; i < n; i++) {
            w += (DOT[i] != 0 ? 8 : 0) + tr.getWidth(VAL[i]) + (UNIT[i].isEmpty() ? 0 : 3 + tr.getWidth(UNIT[i])) + gap + 1;
        }
        w += pad - gap;

        int a1 = Theme.accent(), a2 = Theme.accent2();

        if (Modules.WATERMARK.b("Glow")) Theme.glow(ctx, x, y, w, h, 6, a1, 3);
        Theme.card(ctx, x, y, w, h, 6, 0xEC0A0A0D, Theme.BORDER);
        ctx.fill(x + 6, y + 2, x + w - 6, y + 3, Theme.alpha(0xFFFFFFFF, 0.05f));            // soft top highlight
        Theme.gradBar(ctx, x + 6, y + 1, w - 12, 1, a1, a2, w - 12, 1f);                       // accent line
        Theme.gradBar(ctx, x + 10, y + h - 2, w - 20, 1, a2, a1, w - 20, 0.28f);

        if (logo) Avatar.draw(ctx, x + pad + 8, y + h / 2, 8, 1f);
        int tx = x + pad + logoW, ty = y + (h - 8) / 2 + 1;
        for (int i = 0; i < CH.length; i++) {   // name with a gradient
            int col = Theme.mix(a1, a2, CH.length <= 1 ? 0f : i / (float) (CH.length - 1));
            ctx.drawTextWithShadow(tr, CH[i], tx, ty, col);
            tx += tr.getWidth(CH[i]);
        }
        tx += gap;

        for (int i = 0; i < n; i++) {
            ctx.fill(tx - 5, y + 6, tx - 4, y + h - 6, Theme.alpha(0xFFFFFFFF, 0.12f));
            if (DOT[i] != 0) {
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
        bottom = y + h;
    }

    private Watermark() {}
}

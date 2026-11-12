package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class Watermark {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm"), FMT_S = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Bottom edge of the watermark block, used by the info panel. */
    public static int bottom = 8;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        bottom = 8;
        if (!Modules.WATERMARK.on || mc.options.hudHidden || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;

        List<String> parts = new ArrayList<>();
        if (Modules.FPS.on) parts.add(mc.getCurrentFps() + " fps");
        if (Modules.PING.on) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null
                : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            parts.add((e == null ? 0 : e.getLatency()) + " ms");
        }
        if (Modules.TIME.on) parts.add(LocalTime.now().format(Modules.TIME.b("Seconds") ? FMT_S : FMT));

        int pad = 6, gap = 8;
        int x = 8, y = 8, h = 20;
        String name = Nelose.NAME;
        boolean logo = Modules.LOGO.on;
        int logoW = logo ? 22 : 0;
        int w = pad + logoW + tr.getWidth(name) + gap;
        for (String p : parts) w += tr.getWidth(p) + gap + 3;
        w += pad - gap;

        int a1 = Theme.accent(), a2 = Theme.accent2();

        if (Modules.WATERMARK.b("Glow")) Theme.glow(ctx, x, y, w, h, 5, a1, 5);
        Theme.card(ctx, x, y, w, h, 5, 0xE60B0B0E, Theme.BORDER);
        for (int i = 0; i < w - 10; i++) {
            ctx.fill(x + 5 + i, y + 1, x + 6 + i, y + 2, Theme.mix(a1, a2, i / (float) (w - 10)));
        }

        if (logo) Avatar.draw(ctx, x + pad + 8, y + h / 2, 8, 1f);
        int tx = x + pad + logoW, ty = y + (h - 8) / 2 + 1;
        ctx.drawTextWithShadow(tr, name, tx, ty, a1);
        tx += tr.getWidth(name) + gap;

        for (String p : parts) {
            ctx.fill(tx - 4, y + 5, tx - 3, y + h - 5, Theme.BORDER);
            ctx.drawTextWithShadow(tr, p, tx, ty, Theme.TEXT);
            tx += tr.getWidth(p) + gap + 3;
        }
        bottom = y + h;
    }

    private Watermark() {}
}

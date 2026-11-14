package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Red pill at the top of the screen: combat countdown ("PvP 25s") and the real time. */
public final class PvpTimer {
    private static long endMs;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static void reset() {
        endMs = System.currentTimeMillis() + (long) (Modules.PVP_TIMER.f("Duration") * 1000);
    }

    /** You hit something. Only real players start the timer. */
    public static void onHit(Entity e) {
        if (Modules.PVP_TIMER.on && e instanceof PlayerEntity && !FakePlayer.isFake(e)) reset();
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) { endMs = 0; return; }
        if (!mc.player.isAlive()) { endMs = 0; return; }
        // a player hit you
        if (Modules.PVP_TIMER.on && mc.player.hurtTime > 0 && mc.player.getAttacker() instanceof PlayerEntity a
            && a != mc.player && !FakePlayer.isFake(a)) reset();
    }

    public static void render(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        long now = System.currentTimeMillis();
        boolean fight = now < endMs;
        if (!fight && !Modules.PVP_TIMER.b("Always show")) return;

        boolean clock = Modules.PVP_TIMER.b("Show clock");
        String label = fight ? "PvP" : "Safe";
        String sec = fight ? (int) Math.ceil((endMs - now) / 1000.0) + "s" : "";
        String time = LocalTime.now().format(FMT);

        int h = 20, pad = 10;
        int w = pad + 8 + tr.getWidth(label);
        if (fight) w += 6 + tr.getWidth(sec) + 10;
        if (clock) w += 16 + 11 + tr.getWidth(time);
        w += pad;
        int x = mc.getWindow().getScaledWidth() / 2 - w / 2, y = 6;

        HudLayout.begin(c, "pvp", x, y, w, h);
        int fill = fight ? 0xF0561424 : 0xE60B0B0E;
        int border = fight ? 0xFFB0364A : Theme.BORDER;
        Theme.card(c, x, y, w, h, 10, fill, border);

        float pulse = fight ? 0.65f + 0.35f * (float) Math.sin(now / 160.0) : 1f;
        Theme.round(c, x + pad, y + 7, 6, 6, 3, Theme.alpha(fight ? 0xFFFF5A5F : 0xFF34D399, pulse));
        int tx = x + pad + 14;
        c.drawTextWithShadow(tr, label, tx, y + 6, 0xFFFFFFFF);
        tx += tr.getWidth(label);
        if (fight) {
            int bw = tr.getWidth(sec) + 10;
            Theme.round(c, tx + 6, y + 3, bw, 14, 5, 0xFFFF6B6B);
            c.drawTextWithShadow(tr, sec, tx + 11, y + 6, 0xFFFFFFFF);
            tx += 6 + bw;
        }
        if (clock) {
            tx += 16;
            Theme.round(c, tx, y + 5, 10, 10, 5, 0xFFF2F2F6);
            c.fill(tx + 5, y + 7, tx + 6, y + 11, 0xFF561424);
            c.fill(tx + 5, y + 10, tx + 8, y + 11, 0xFF561424);
            c.drawTextWithShadow(tr, time, tx + 15, y + 6, 0xFFFFFFFF);
        }
        HudLayout.end(c);
    }

    private PvpTimer() {}
}

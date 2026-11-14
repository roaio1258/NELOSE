package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PvP information tools: combo and reach counter, crit indicator, totem warning, damage popups. */
public final class PvpHud {
    private static int combo, crits;
    private static long lastHit;
    private static float reach;

    private static Entity lastTarget;
    private static float lastHp;
    private static final class Pop { float dmg; long t; }
    private static final List<Pop> POPS = new ArrayList<>();

    private static boolean critReady(PlayerEntity p) {
        return p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.hasVehicle();
    }

    /** Called from Features.onAttack for every hit you land. */
    public static void onHit(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastHit > (long) (Modules.COMBO.f("Reset time") * 1000)) { combo = 0; crits = 0; }
        combo++;
        lastHit = now;
        reach = mc.player.distanceTo(e);
        if (critReady(mc.player)) crits++;
    }

    public static void render(DrawContext c, MinecraftClient mc, TextRenderer tr, int a1, int a2) {
        long now = System.currentTimeMillis();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int cx = sw / 2, cy = sh / 2;

        if (Modules.COMBO.on) {
            if (mc.player.hurtTime > 0 && Modules.COMBO.b("Reset when hurt")) combo = 0;
            long left = (long) (Modules.COMBO.f("Reset time") * 1000) - (now - lastHit);
            boolean show = combo > 0 && left > 0;
            boolean prev = !show && (mc.currentScreen instanceof NeloseScreen || mc.currentScreen instanceof ChatScreen);
            if (show || prev) {
                int n = prev ? 5 : combo;
                float a = prev ? 1f : Math.min(1f, left / 300f);
                float r = prev ? 2.87f : reach;
                int ct = prev ? 2 : crits;
                String num = String.valueOf(n);
                boolean reachOn = Modules.COMBO.b("Reach"), critOn = Modules.COMBO.b("Crits");
                String sub = (reachOn ? String.format(Locale.ROOT, "Reach %.2f", r) : "") + (reachOn && critOn ? "   " : "") + (critOn ? "Crits " + ct : "");
                int w = Math.max(88, tr.getWidth(sub) + 18), h = sub.isEmpty() ? 20 : 31;
                int x = cx - w / 2, y = cy + 56;
                HudLayout.begin(c, "combo", x, y, w, h);
                Theme.card(c, x, y, w, h, 6, Theme.alpha(0xFF0B0B0E, 0.92f * a), Theme.alpha(Theme.mix(Theme.BORDER, a1, 0.35f), a));
                c.drawTextWithShadow(tr, "Combo", x + 8, y + 6, Theme.alpha(Theme.TEXT_DIM, a));
                c.drawTextWithShadow(tr, num + "x", x + w - 8 - tr.getWidth(num + "x"), y + 6, Theme.alpha(Theme.mix(a1, a2, Math.min(1f, n / 10f)), a));
                if (!sub.isEmpty()) c.drawText(tr, sub, x + w / 2 - tr.getWidth(sub) / 2, y + 19, Theme.alpha(Theme.TEXT_DIM, a), false);
                HudLayout.end(c);
            }
        }

        if (Modules.CRIT_HELPER.on && mc.currentScreen == null && mc.options.getPerspective().isFirstPerson() && critReady(mc.player)) {
            float pulse = (float) (0.65 + 0.35 * Math.sin(now / 120.0));
            int col = Theme.alpha(0xFF6EE7A0, pulse);
            if (Modules.CRIT_HELPER.idx("Style") == 1) {
                Theme.round(c, cx - 3, cy + 18, 6, 6, 3, col);
            } else {
                c.drawCenteredTextWithShadow(tr, "CRIT", cx, cy + 18, col);
            }
        }

        if (Modules.TOTEM_WARN.on && mc.currentScreen == null && !mc.player.isDead()) {
            boolean combat = mc.player.hurtTime > 0 || now - Features.targetTime < 15000;
            if (!Modules.TOTEM_WARN.b("Only in combat") || combat) {
                boolean has = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
                if (!has && Modules.TOTEM_WARN.idx("Check") == 1) {
                    var inv = mc.player.getInventory();
                    for (int i = 0; i < inv.size() && !has; i++) has = inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING);
                }
                if (!has) {
                    float pulse = (float) (0.6 + 0.4 * Math.sin(now / 200.0));
                    String t = Modules.TOTEM_WARN.idx("Check") == 1 ? "NO TOTEMS" : "NO TOTEM IN OFFHAND";
                    int w = tr.getWidth(t) + 20, x = cx - w / 2, y = cy - 52;
                    Theme.card(c, x, y, w, 16, 6, Theme.alpha(0xFF1A0B0E, 0.92f), Theme.alpha(0xFFFB7185, 0.5f + 0.4f * pulse));
                    c.drawCenteredTextWithShadow(tr, t, cx, y + 4, Theme.alpha(0xFFFB7185, 0.7f + 0.3f * pulse));
                }
            }
        }

        // damage popups: watch the health of your current target
        Entity t = Features.target;
        if (t instanceof LivingEntity le && le.isAlive()) {
            float hp = le.getHealth();
            if (le == lastTarget && hp < lastHp - 0.05f && Modules.DAMAGE_POP.on) {
                Pop p = new Pop();
                p.dmg = lastHp - hp; p.t = now;
                POPS.add(p);
                if (POPS.size() > 6) POPS.remove(0);
            }
            lastTarget = le; lastHp = hp;
        } else {
            lastTarget = null;
        }
        long dur = (long) Modules.DAMAGE_POP.f("Duration");
        POPS.removeIf(p -> now - p.t > dur);
        if (Modules.DAMAGE_POP.on && mc.currentScreen == null) {
            for (Pop p : POPS) {
                float k = (now - p.t) / (float) dur;
                String s = String.format(Locale.ROOT, "-%.1f", p.dmg);
                c.drawTextWithShadow(tr, s, cx + 14 + (int) (k * 6), cy - 10 - (int) (k * 22), Theme.alpha(p.dmg >= 6f ? 0xFFFBBF24 : 0xFFFB7185, 1f - k));
            }
        }
    }

    /** Armor and held item of the target under the Target HUD card. */
    public static void gear(DrawContext c, TextRenderer tr, LivingEntity le, int x, int y) {
        net.minecraft.entity.EquipmentSlot[] slots = {
            net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET};
        List<ItemStack> items = new ArrayList<>();
        for (var s : slots) items.add(le.getEquippedStack(s));
        items.add(le.getMainHandStack());
        items.add(le.getOffHandStack());
        int w = items.size() * 18 + 6;
        Theme.card(c, x, y, w, 24, 5, 0xE00B0B0E, Theme.BORDER);
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            int ix = x + 4 + i * 18;
            c.fill(ix, y + 4, ix + 16, y + 20, Theme.alpha(0xFFFFFFFF, 0.05f));
            if (st.isEmpty()) continue;
            c.drawItem(st, ix, y + 4);
            c.drawStackOverlay(tr, st, ix, y + 4);
        }
    }

    private PvpHud() {}
}

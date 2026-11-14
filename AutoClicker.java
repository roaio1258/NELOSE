package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Hold a mouse button and it clicks for you at a random CPS (up to 40).
 * Left: attacks the mob or player you aim at. Right: places blocks / uses the held item.
 */
public final class AutoClicker {
    private static double acc;

    public static void tick(MinecraftClient mc) {
        if (!Modules.AUTO_CLICKER.on || mc.player == null || mc.interactionManager == null || mc.currentScreen != null) {
            acc = 0;
            return;
        }
        boolean right = Modules.AUTO_CLICKER.idx("Button") == 1;
        if (!(right ? mc.options.useKey : mc.options.attackKey).isPressed() || (!right && mc.player.isUsingItem())) {
            acc = 0;
            return;
        }

        Entity t = mc.targetedEntity;
        if (!right) {
            if (!(t instanceof LivingEntity) || !t.isAlive()
                || (Modules.AUTO_CLICKER.b("Players only") && !(t instanceof PlayerEntity)) || FakePlayer.isFake(t)) {
                acc = 0;   // blocks and air are left to normal mining
                return;
            }
            if (Modules.AUTO_CLICKER.b("Respect cooldown") && mc.player.getAttackCooldownProgress(0f) < 0.9f) return;
        }

        double min = Modules.AUTO_CLICKER.f("Min CPS"), max = Math.max(min, Modules.AUTO_CLICKER.f("Max CPS"));
        acc += (min + Math.random() * (max - min)) / 20.0;
        int clicks = Math.min(3, (int) acc);   // above 20 CPS more than one click per tick
        acc -= clicks;
        if (acc > 1.0) acc = 1.0;
        for (int i = 0; i < clicks; i++) {
            if (right) rightClick(mc); else {
                mc.interactionManager.attackEntity(mc.player, t);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private static void rightClick(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            if (r.isAccepted()) { mc.player.swingHand(Hand.MAIN_HAND); return; }
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private AutoClicker() {}
}

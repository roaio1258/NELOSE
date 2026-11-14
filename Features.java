package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side logic that runs every tick. */
public final class Features {
    public static KeyBinding zoomKey, pearlKey, fastFirework, fastSnowball, fastPotion;
    private static boolean maceWasReady;
    private static int deadTicks;
    public static double bps;

    /** Last entity you hit, used by Target ESP, hit marker and kill effect. */
    public static Entity target;
    public static long targetTime, hitTime;
    private static boolean killDone, wasGround;

    private static double lx, lz;
    private static boolean hasLast, nvApplied, sprintApplied, zooming;
    private static int savedFov = -1;
    private static double curFov;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { hasLast = false; FakePlayer.clear(); return; }

        // speed in blocks per second (smoothed)
        double x = mc.player.getX(), z = mc.player.getZ();
        if (hasLast) {
            double v = Math.hypot(x - lx, z - lz) * 20.0;
            bps += (v - bps) * 0.25;
        }
        lx = x; lz = z; hasLast = true;

        // Fullbright through a client-side night vision effect
        if (Modules.FULLBRIGHT.on) {
            StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 220) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 460, 0, true, false, false));
            }
            nvApplied = true;
        } else if (nvApplied) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            nvApplied = false;
        }

        // Auto sprint: sprint whenever you move forward and nothing forbids it
        if (Modules.AUTO_SPRINT.on) {
            boolean can = mc.options.forwardKey.isPressed()
                && !mc.player.isSneaking()
                && !mc.player.isUsingItem()
                && !mc.player.horizontalCollision
                && (!Modules.AUTO_SPRINT.b("Hunger check") || mc.player.getHungerManager().getFoodLevel() > 6);
            mc.options.sprintKey.setPressed(can);
            sprintApplied = true;
        } else if (sprintApplied) {
            mc.options.sprintKey.setPressed(false);
            sprintApplied = false;
        }

        // Auto respawn
        if (Modules.AUTO_RESPAWN.on && mc.currentScreen instanceof DeathScreen) {
            if (++deadTicks >= (int) Modules.AUTO_RESPAWN.f("Delay")) {
                mc.player.requestRespawn();
                mc.setScreen(null);
                deadTicks = 0;
            }
        } else deadTicks = 0;

        effects(mc);
        atmosphere(mc);
        keys(mc);
        mace(mc);
        FakePlayer.tick(mc);

        // Smooth zoom is done per frame in Zoom (render hook); this tick version is only a fallback
        if (!Zoom.hooked) zoomFallback(mc);
    }

    private static void zoomFallback(MinecraftClient mc) {
        boolean want = Modules.ZOOM.on && zoomKey != null && zoomKey.isPressed() && mc.currentScreen == null;
        if (want && !zooming) {
            savedFov = mc.options.getFov().getValue();
            curFov = savedFov;
            zooming = true;
        }
        if (zooming) {
            double target = want ? Modules.ZOOM.f("Zoom FOV") : savedFov;
            curFov += (target - curFov) * Modules.ZOOM.f("Smoothness");
            mc.options.getFov().setValue((int) Math.max(30, Math.min(110, Math.round(curFov))));
            if (!want && Math.abs(curFov - savedFov) < 0.6) {
                mc.options.getFov().setValue(savedFov);
                zooming = false;
            }
        }
    }

    // ---------- attack hook ----------
    public static void onAttack(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !(e instanceof LivingEntity)) return;
        target = e;
        targetTime = hitTime = System.currentTimeMillis();
        killDone = false;
        FakePlayer.onHit(e);
        hitSound(mc);
        if (Modules.HIT_PARTICLES.on) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < (int) Modules.HIT_PARTICLES.f("Amount"); i++) {
                spawn(mc, hitParticle(i),
                    e.getX(), e.getBodyY(0.5), e.getZ(),
                    r.nextDouble(-0.5, 0.5), r.nextDouble(0.1, 0.6), r.nextDouble(-0.5, 0.5));
            }
        }
    }

    /** The entity Target ESP should draw on, or null. */
    public static Entity espTarget(MinecraftClient mc) {
        if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) return le;
        if (target != null && target.isAlive() && System.currentTimeMillis() - targetTime < 3000) return target;
        return null;
    }

    // ---------- particle effects ----------
    private static void spawn(MinecraftClient mc, ParticleEffect p, double x, double y, double z, double vx, double vy, double vz) {
        mc.world.addParticleClient(p, x, y, z, vx, vy, vz);
    }

    // ---------- hit sounds (vanilla sounds, no extra files needed) ----------
    private static void hitSound(MinecraftClient mc) {
        if (!Modules.HIT_SOUNDS.on) return;
        SoundEvent[] all = {
            SoundEvents.BLOCK_BELL_USE, SoundEvents.BLOCK_WOOD_HIT, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP,
            SoundEvents.ENTITY_ITEM_PICKUP, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundEvents.ENTITY_ARROW_HIT_PLAYER
        };
        SoundEvent ev = all[Modules.HIT_SOUNDS.idx("Sound")];
        mc.getSoundManager().play(PositionedSoundInstance.master(ev, Modules.HIT_SOUNDS.f("Pitch"), Modules.HIT_SOUNDS.f("Volume")));
    }

    // ---------- atmosphere particles ----------
    private static void atmosphere(MinecraftClient mc) {
        if (!Modules.ATMOSPHERE.on || mc.world == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        if (Modules.ATMOSPHERE.b("Snow")) {
            for (int i = 0; i < (int) Modules.ATMOSPHERE.f("Snow amount"); i++) {
                spawn(mc, ParticleTypes.SNOWFLAKE, px + r.nextDouble(-12, 12), py + r.nextDouble(3, 9), pz + r.nextDouble(-12, 12),
                    r.nextDouble(-0.02, 0.02), -0.06, r.nextDouble(-0.02, 0.02));
            }
        }
        if (Modules.ATMOSPHERE.b("Air particles") && mc.player.age % 2 == 0) {
            spawn(mc, ParticleTypes.WHITE_ASH, px + r.nextDouble(-8, 8), py + r.nextDouble(0, 5), pz + r.nextDouble(-8, 8),
                r.nextDouble(-0.01, 0.01), 0.01, r.nextDouble(-0.01, 0.01));
        }
    }

    // ---------- key actions: pearl / fast use ----------
    private static int hotbarSlot(MinecraftClient mc, Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private static void useFromHotbar(MinecraftClient mc, Item item, boolean back) {
        int slot = hotbarSlot(mc, item);
        if (slot < 0 || mc.interactionManager == null) return;
        var inv = mc.player.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (back) inv.setSelectedSlot(prev);
    }

    private static void keys(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        if (pearlKey != null) while (pearlKey.wasPressed()) { if (Modules.CLICK_PEARL.on) useFromHotbar(mc, Items.ENDER_PEARL, Modules.CLICK_PEARL.b("Switch back")); }
        if (fastFirework != null) while (fastFirework.wasPressed()) { if (Modules.FAST_USE.on) useFromHotbar(mc, Items.FIREWORK_ROCKET, Modules.FAST_USE.b("Switch back")); }
        if (fastSnowball != null) while (fastSnowball.wasPressed()) { if (Modules.FAST_USE.on) useFromHotbar(mc, Items.SNOWBALL, Modules.FAST_USE.b("Switch back")); }
        if (fastPotion != null) while (fastPotion.wasPressed()) { if (Modules.FAST_USE.on) useFromHotbar(mc, Items.SPLASH_POTION, Modules.FAST_USE.b("Switch back")); }
    }

    // ---------- mace ready message ----------
    private static void mace(MinecraftClient mc) {
        boolean ready = Modules.MACE_HELPER.on && mc.player.getMainHandStack().isOf(Items.MACE)
            && !mc.player.isOnGround() && mc.player.fallDistance >= Modules.MACE_HELPER.f("Min fall");
        if (ready && !maceWasReady && Modules.MACE_HELPER.b("Chat message")) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Nelose] Mace smash is ready"));
        }
        maceWasReady = ready;
    }

    private static ParticleEffect hitParticle(int i) {
        return switch (Modules.HIT_PARTICLES.idx("Type")) {
            case 1 -> ParticleTypes.ENCHANTED_HIT;
            case 2 -> ParticleTypes.HEART;
            case 3 -> i % 3 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT;
            default -> ParticleTypes.CRIT;
        };
    }

    private static ParticleEffect killParticle() {
        return switch (Modules.KILL_EFFECT.idx("Type")) {
            case 1 -> ParticleTypes.FIREWORK;
            case 2 -> ParticleTypes.SOUL_FIRE_FLAME;
            case 3 -> ParticleTypes.HEART;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
    }

    private static ParticleEffect dust(float size) {
        return new DustParticleEffect(Theme.accent() & 0xFFFFFF, size);
    }

    private static void effects(MinecraftClient mc) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();

        // kill effect
        if (Modules.KILL_EFFECT.on && target != null && !killDone && !target.isAlive()
            && System.currentTimeMillis() - targetTime < 4000) {
            killDone = true;
            for (int i = 0; i < (int) Modules.KILL_EFFECT.f("Amount"); i++) {
                spawn(mc, killParticle(), target.getX(), target.getBodyY(0.5), target.getZ(),
                    r.nextDouble(-0.4, 0.4), r.nextDouble(0.1, 0.7), r.nextDouble(-0.4, 0.4));
            }
        }

        // jump circles
        boolean ground = mc.player.isOnGround();
        if (Modules.JUMP_CIRCLES.on && wasGround && !ground && mc.player.getVelocity().y > 0.1) {
            int n = (int) Modules.JUMP_CIRCLES.f("Particles");
            double sp = Modules.JUMP_CIRCLES.f("Spread");
            if (Modules.JUMP_CIRCLES.idx("Mode") == 1) {
                // magnetic filings: radial spikes of dust that rise like iron filings around a magnet
                for (int s = 0; s < 10; s++) {
                    double a = s * Math.PI * 2 / 10 + r.nextDouble(-0.15, 0.15);
                    int len = 3 + r.nextInt(4);
                    for (int k = 0; k < len; k++) {
                        double d = 0.25 + k * 0.12;
                        spawn(mc, dust(0.8f), px + Math.cos(a) * d, py + 0.05 + k * 0.06, pz + Math.sin(a) * d,
                            Math.cos(a) * sp * 0.3, 0.02 + k * 0.01, Math.sin(a) * sp * 0.3);
                    }
                }
            } else for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                spawn(mc, dust(1.1f), px + Math.cos(a) * 0.3, py + 0.05, pz + Math.sin(a) * 0.3,
                    Math.cos(a) * sp, 0.0, Math.sin(a) * sp);
            }
        }
        wasGround = ground;

        // trail
        if (Modules.TRAIL.on && bps > 1.0) {
            for (int i = 0; i < (int) Modules.TRAIL.f("Density"); i++) {
                spawn(mc, dust(Modules.TRAIL.f("Size")), px + r.nextDouble(-0.25, 0.25), py + 0.1 + r.nextDouble(0.5), pz + r.nextDouble(-0.25, 0.25), 0, 0.01, 0);
            }
        }

        aura(mc, px, py, pz, r);
    }

    private static ParticleEffect dustC(int rgb, float size) {
        return new DustParticleEffect(rgb & 0xFFFFFF, size);
    }

    /** Aura: three styles, colours blend from the theme accent to the second accent. */
    private static void aura(MinecraftClient mc, double px, double py, double pz, ThreadLocalRandom r) {
        if (!Modules.AURA.on) return;
        double rad = Modules.AURA.f("Radius");
        double sp = Modules.AURA.f("Speed");
        int dens = Math.max(1, (int) Modules.AURA.f("Density"));
        boolean spark = Modules.AURA.b("Sparkles");
        int style = Modules.AURA.idx("Style");
        double age = mc.player.age;
        double ph = mc.player.getHeight();
        double t = age * sp;
        int c1 = Theme.accent(), c2 = Theme.accent2();

        if (style == 0) {                                   // double helix that slides up the body
            int n = dens * 2;
            for (int strand = 0; strand < 2; strand++) {
                for (int j = 0; j < n; j++) {
                    double f = ((age * 0.035 + j / (double) n) % 1.0);
                    double a = t * 1.4 + f * 7.0 + strand * Math.PI;
                    double rr = rad * (0.82 + 0.18 * Math.sin(f * Math.PI));
                    int col = Theme.mix(c1, c2, (float) f);
                    spawn(mc, dustC(col, 0.8f), px + Math.cos(a) * rr, py + 0.05 + f * (ph + 0.15), pz + Math.sin(a) * rr, 0, 0, 0);
                }
            }
            if (spark && r.nextInt(3) == 0) {
                double a = r.nextDouble(Math.PI * 2);
                spawn(mc, ParticleTypes.END_ROD, px + Math.cos(a) * rad, py + r.nextDouble(ph), pz + Math.sin(a) * rad, 0, 0.015, 0);
            }
        } else if (style == 1) {                            // halo above the head + pulsing ring at the feet
            int n = dens * 4;
            double hy = py + ph + 0.3 + 0.05 * Math.sin(age * 0.2);
            for (int i = 0; i < n; i++) {
                double a = t * 1.2 + i * Math.PI * 2 / n;
                spawn(mc, dustC(Theme.mix(c1, c2, i / (float) n), 0.7f), px + Math.cos(a) * rad * 0.55, hy, pz + Math.sin(a) * rad * 0.55, 0, 0, 0);
            }
            double pulse = (age * 0.04) % 1.0;
            for (int i = 0; i < dens * 3; i++) {
                double a = i * Math.PI * 2 / (dens * 3) - t;
                double rr = rad * (0.4 + 0.9 * pulse);
                spawn(mc, dustC(Theme.mix(c2, c1, (float) pulse), 0.6f), px + Math.cos(a) * rr, py + 0.05, pz + Math.sin(a) * rr, 0, 0, 0);
            }
            if (spark && r.nextInt(4) == 0)
                spawn(mc, ParticleTypes.END_ROD, px + r.nextDouble(-0.3, 0.3), hy, pz + r.nextDouble(-0.3, 0.3), 0, -0.01, 0);
        } else {                                            // three tilted orbits
            for (int o = 0; o < 3; o++) {
                double tilt = o * Math.PI / 3.0, yaw = o * 2.1;
                for (int k = 0; k < dens; k++) {
                    double a = t * (1.0 + o * 0.25) + k * Math.PI * 2 / dens + o * 2.1;
                    double lx = Math.cos(a) * rad, ly = Math.sin(a) * rad;
                    double yy = ly * Math.cos(tilt), zz = ly * Math.sin(tilt);
                    double wx = lx * Math.cos(yaw) - zz * Math.sin(yaw), wz = lx * Math.sin(yaw) + zz * Math.cos(yaw);
                    spawn(mc, dustC(Theme.mix(c1, c2, o / 2f), 0.8f), px + wx, py + ph * 0.55 + yy * 0.8, pz + wz, 0, 0, 0);
                }
            }
            if (spark && r.nextInt(3) == 0)
                spawn(mc, ParticleTypes.END_ROD, px + r.nextDouble(-rad, rad), py + r.nextDouble(ph), pz + r.nextDouble(-rad, rad), 0, 0.01, 0);
        }
    }

    private Features() {}
}

package nelose;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** BedWars helpers: bed and resource labels, counters, void warning, radar and quick-use keys. */
public final class BedWars {
    public static KeyBinding fireballKey, eggKey, waterKey, blocksKey, swordKey, pickKey;

    private static final List<BlockPos> BEDS = new ArrayList<>();
    private static int scanTimer;
    private static boolean voidDanger, voidSounded;

    private static final Item[] RES = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD};
    private static final String[] RES_NAME = {"Iron", "Gold", "Diamond", "Emerald"};
    private static final int[] RES_COL = {0xFFD8D8D8, 0xFFFBBF24, 0xFF4EE2EC, 0xFF34D399};

    // ---------- tick ----------
    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { BEDS.clear(); voidDanger = false; return; }
        if (Modules.BED_FINDER.on) {
            if (--scanTimer <= 0) { scanTimer = 30; scanBeds(mc); }
        } else BEDS.clear();
        voidCheck(mc);
        quickKeys(mc);
        eagle(mc);
        bridge(mc);
    }

    // ---------- auto bridge: place a block under the spot you are walking onto ----------
    private static int bridgeCd;

    private static void bridge(MinecraftClient mc) {
        if (!Modules.AUTO_BRIDGE.on || mc.currentScreen != null || mc.interactionManager == null) return;
        if (bridgeCd > 0) { bridgeCd--; return; }
        var p = mc.player;
        if (Modules.AUTO_BRIDGE.b("Only on ground") && !p.isOnGround()) return;
        if (p.isTouchingWater() || p.isClimbing()) return;

        // walking direction from the keys (so it works while standing still at the edge), else from velocity
        boolean backOnly = Modules.AUTO_BRIDGE.idx("Direction") == 1;
        float fwd = (mc.options.forwardKey.isPressed() ? 1f : 0f) - (mc.options.backKey.isPressed() ? 1f : 0f);
        float side = (mc.options.leftKey.isPressed() ? 1f : 0f) - (mc.options.rightKey.isPressed() ? 1f : 0f);
        if (backOnly && fwd >= 0) return;
        double yaw = Math.toRadians(p.getYaw());
        double mx = fwd * -Math.sin(yaw) + side * Math.cos(yaw);
        double mz = fwd * Math.cos(yaw) + side * Math.sin(yaw);
        double len = Math.hypot(mx, mz);
        if (len < 0.01) { Vec3d v = p.getVelocity(); mx = v.x; mz = v.z; len = Math.hypot(mx, mz); }
        double d = Modules.AUTO_BRIDGE.f("Lookahead");
        double dx = len > 0.003 ? mx / len * d : 0, dz = len > 0.003 ? mz / len * d : 0;

        // try the spot ahead first, then the block right under your feet
        BlockPos[] spots = {
            BlockPos.ofFloored(p.getX() + dx, p.getY() - 0.5, p.getZ() + dz),
            BlockPos.ofFloored(p.getX(), p.getY() - 0.5, p.getZ())
        };
        BlockHitResult hit = null;
        for (BlockPos target : spots) {
            if (!mc.world.getBlockState(target).isReplaceable()) continue;
            for (Direction dir : Direction.values()) {
                BlockPos n = target.offset(dir);
                BlockState st = mc.world.getBlockState(n);
                if (st.isAir() || st.isReplaceable()) continue;
                Direction face = dir.getOpposite();
                Vec3d at = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                hit = new BlockHitResult(at, face, n, false);
                break;
            }
            if (hit != null) break;
        }
        if (hit == null) return;

        if (!isBlock(p.getMainHandStack())) {
            if (!Modules.AUTO_BRIDGE.b("Auto switch")) return;
            select(mc, 0);
            if (!isBlock(p.getMainHandStack())) return;
        }
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        if (Modules.AUTO_BRIDGE.b("Swing")) p.swingHand(Hand.MAIN_HAND);
        bridgeCd = (int) Modules.AUTO_BRIDGE.f("Delay");
    }

    // ---------- eagle: sneak at the edge, release when away from it ----------
    private static boolean eagleSneaking;

    private static void eagle(MinecraftClient mc) {
        boolean edge = false;
        if (Modules.EAGLE.on && mc.currentScreen == null) {
            var p = mc.player;
            boolean ground = p.isOnGround() || !Modules.EAGLE.b("Only on ground");
            boolean back = !Modules.EAGLE.b("Only moving back") || mc.options.backKey.isPressed();
            if (ground && back && !p.isTouchingWater() && !p.isClimbing()) {
                // look a little ahead along the movement; no floor within a step below means we are at the edge
                double d = Modules.EAGLE.f("Edge distance");
                Vec3d v = p.getVelocity();
                double len = Math.hypot(v.x, v.z);
                double dx = 0, dz = 0;
                if (len > 0.003) { dx = v.x / len * d; dz = v.z / len * d; }
                Box probe = p.getBoundingBox().offset(dx, -0.6, dz);
                edge = mc.world.isSpaceEmpty(p, probe);
            }
        }
        if (edge) {
            mc.options.sneakKey.setPressed(true);
            eagleSneaking = true;
        } else if (eagleSneaking) {
            // hand control back: sneak only if the real key is held
            int code = KeyBindingHelper.getBoundKeyOf(mc.options.sneakKey).getCode();
            boolean held = code >= 0 && GLFW.glfwGetKey(mc.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
            mc.options.sneakKey.setPressed(held);
            eagleSneaking = false;
        }
    }

    private static void scanBeds(MinecraftClient mc) {
        BEDS.clear();
        BlockPos p = mc.player.getBlockPos();
        int r = (int) Modules.BED_FINDER.f("Range");
        int vr = Math.min(r, 24);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -vr; dy <= vr; dy++) {
                    m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    BlockState st = mc.world.getBlockState(m);
                    if (st.isIn(BlockTags.BEDS) && st.get(BedBlock.PART) == BedPart.HEAD) BEDS.add(m.toImmutable());
                }
            }
        }
    }

    // ---------- void alert ----------
    private static void voidCheck(MinecraftClient mc) {
        boolean danger = false;
        if (Modules.VOID_ALERT.on && !mc.player.isOnGround() && !mc.player.isTouchingWater()
            && mc.player.fallDistance >= Modules.VOID_ALERT.f("Min fall")) {
            danger = true;
            BlockPos.Mutable m = new BlockPos.Mutable();
            int bx = mc.player.getBlockX(), bz = mc.player.getBlockZ();
            int bottom = mc.world.getBottomY();
            for (int y = mc.player.getBlockY() - 1; y >= bottom; y--) {
                m.set(bx, y, bz);
                if (!mc.world.getBlockState(m).isAir()) { danger = false; break; }
            }
        }
        if (danger && !voidSounded && Modules.VOID_ALERT.b("Sound")) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1f));
        }
        voidSounded = danger;
        voidDanger = danger;
    }

    // ---------- quick keys ----------
    private static void quickKeys(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        boolean on = Modules.BW_QUICK.on;
        boolean back = Modules.BW_QUICK.b("Switch back");
        if (fireballKey != null) while (fireballKey.wasPressed()) { if (on) Features.useFromHotbar(mc, Items.FIRE_CHARGE, back); }
        if (eggKey != null) while (eggKey.wasPressed()) { if (on) Features.useFromHotbar(mc, Items.EGG, back); }
        if (waterKey != null) while (waterKey.wasPressed()) { if (on) Features.useFromHotbar(mc, Items.WATER_BUCKET, back); }
        if (blocksKey != null) while (blocksKey.wasPressed()) { if (on) select(mc, 0); }
        if (swordKey != null) while (swordKey.wasPressed()) { if (on) select(mc, 1); }
        if (pickKey != null) while (pickKey.wasPressed()) { if (on) select(mc, 2); }
    }

    private static boolean isBlock(ItemStack st) {
        return st.getItem() instanceof BlockItem && !st.isOf(Items.TNT) && !st.isIn(ItemTags.BEDS);
    }

    /** kind: 0 blocks, 1 sword, 2 pickaxe. Selects the first matching hotbar slot. */
    private static void select(MinecraftClient mc, int kind) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            boolean ok = switch (kind) {
                case 0 -> isBlock(st);
                case 1 -> st.isIn(ItemTags.SWORDS);
                default -> st.isIn(ItemTags.PICKAXES);
            };
            if (ok) { inv.setSelectedSlot(i); return; }
        }
    }

    // ---------- rendering ----------
    public static void render(DrawContext c, MinecraftClient mc, TextRenderer tr, float delta) {
        if (mc.player == null || mc.world == null) return;

        if ((Modules.BED_FINDER.on || Modules.RESOURCE_ESP.on) && mc.options.getPerspective().isFirstPerson()) {
            Esp.setup(delta);
            if (Modules.BED_FINDER.on) beds(c, mc, tr);
            if (Modules.RESOURCE_ESP.on) resources(c, mc, tr, delta);
        }
        if (Modules.RESOURCE_HUD.on) resourceHud(c, mc, tr);
        if (Modules.BLOCK_COUNTER.on) blockCounter(c, mc, tr);
        if (Modules.PLAYER_RADAR.on) radar(c, mc, tr);
        if (voidDanger) voidBanner(c, mc, tr);
    }

    private static void label(DrawContext c, TextRenderer tr, double sx, double sy, String txt, int col) {
        int w = tr.getWidth(txt) + 8, x = (int) sx - w / 2, y = (int) sy - 6;
        Theme.card(c, x, y, w, 13, 4, 0xE60B0B0E, col);
        c.drawTextWithShadow(tr, txt, x + 4, y + 3, col);
    }

    private static void beds(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        List<BlockPos> list = new ArrayList<>(BEDS);
        Vec3d pp = mc.player.getPos();
        list.sort((a, b) -> Double.compare(a.getSquaredDistance(pp), b.getSquaredDistance(pp)));
        int max = (int) Modules.BED_FINDER.f("Max labels");
        boolean dist = Modules.BED_FINDER.b("Distance");
        for (int i = 0; i < list.size() && i < max; i++) {
            BlockPos b = list.get(i);
            double[] s = Esp.project(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
            if (s == null) continue;
            String txt = "Bed";
            if (dist) txt += " " + String.format(Locale.ROOT, "%.0fm", Math.sqrt(b.getSquaredDistance(pp)));
            label(c, tr, s[0], s[1], txt, 0xFFE5334B);
        }
    }

    private static void resources(DrawContext c, MinecraftClient mc, TextRenderer tr, float delta) {
        double range = Modules.RESOURCE_ESP.f("Range");
        String[] keys = {"Iron", "Gold", "Diamond", "Emerald"};
        int shown = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity ie) || mc.player.distanceTo(ie) > range) continue;
            ItemStack st = ie.getStack();
            for (int i = 0; i < RES.length; i++) {
                if (!st.isOf(RES[i]) || !Modules.RESOURCE_ESP.b(keys[i])) continue;
                Vec3d p = ie.getLerpedPos(delta);
                double[] s = Esp.project(p.x, p.y + 0.6, p.z);
                if (s == null) break;
                label(c, tr, s[0], s[1], RES_NAME[i] + " x" + st.getCount(), RES_COL[i]);
                shown++;
                break;
            }
            if (shown >= 16) return;
        }
    }

    private static int count(MinecraftClient mc, Item item) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) n += st.getCount();
        }
        return n;
    }

    private static void resourceHud(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        String[] keys = {"Iron", "Gold", "Diamond", "Emerald"};
        List<Integer> sel = new ArrayList<>();
        for (int i = 0; i < RES.length; i++) if (Modules.RESOURCE_HUD.b(keys[i])) sel.add(i);
        if (sel.isEmpty()) return;
        int x = 8, y = mc.getWindow().getScaledHeight() / 2 - (sel.size() * 18 + 8) / 2;
        int w = 58, h = sel.size() * 18 + 8;
        HudLayout.begin(c, "bwres", x, y, w, h);
        Theme.card(c, x, y, w, h, 5, 0xE60B0B0E, Theme.BORDER);
        for (int k = 0; k < sel.size(); k++) {
            int i = sel.get(k);
            int n = count(mc, RES[i]);
            int iy = y + 4 + k * 18;
            c.drawItem(new ItemStack(RES[i]), x + 5, iy);
            c.drawTextWithShadow(tr, String.valueOf(n), x + 26, iy + 5, n == 0 ? Theme.TEXT_DIM : RES_COL[i]);
        }
        HudLayout.end(c);
    }

    private static void blockCounter(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        var inv = mc.player.getInventory();
        int total = 0;
        ItemStack icon = ItemStack.EMPTY;
        ItemStack held = mc.player.getMainHandStack();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (isBlock(st)) { total += st.getCount(); if (icon.isEmpty()) icon = st; }
        }
        if (isBlock(held)) icon = held;
        int x = mc.getWindow().getScaledWidth() / 2 + 14, y = mc.getWindow().getScaledHeight() - 62;
        String txt = total + " blocks";
        int w = tr.getWidth(txt) + 30, h = 22;
        boolean low = total <= (int) Modules.BLOCK_COUNTER.f("Low warning");
        int col = low ? 0xFFE5334B : Theme.TEXT;
        HudLayout.begin(c, "bwblocks", x, y, w, h);
        Theme.card(c, x, y, w, h, 5, 0xE60B0B0E, low ? 0xFFE5334B : Theme.BORDER);
        if (!icon.isEmpty()) c.drawItem(icon, x + 5, y + 3);
        c.drawTextWithShadow(tr, txt, x + 25, y + 7, col);
        HudLayout.end(c);
    }

    private static void radar(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        double range = Modules.PLAYER_RADAR.f("Range");
        int rows = (int) Modules.PLAYER_RADAR.f("Rows");
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || FakePlayer.isFake(p) || mc.player.distanceTo(p) > range) continue;
            list.add(p);
        }
        list.sort((a, b) -> Float.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)));
        int n = Math.min(list.size(), rows);
        int w = 124, h = n * 11 + 19;
        int x = mc.getWindow().getScaledWidth() - w - 8, y = 40;
        HudLayout.begin(c, "bwradar", x, y, w, h);
        Theme.card(c, x, y, w, h, 5, 0xE60B0B0E, Theme.BORDER);
        c.drawTextWithShadow(tr, "Players: " + list.size(), x + 7, y + 5, Theme.TEXT);
        for (int i = 0; i < n; i++) {
            PlayerEntity p = list.get(i);
            String name = p.getName().getString();
            if (name.length() > 9) name = name.substring(0, 9);
            float hp = p.getHealth() + p.getAbsorptionAmount();
            String r = String.format(Locale.ROOT, "%.0fm  %.0f", mc.player.distanceTo(p), hp);
            int rc = hp <= 6 ? 0xFFE5334B : hp <= 12 ? 0xFFFBBF24 : 0xFF34D399;
            c.drawText(tr, name, x + 7, y + 19 + i * 11, Theme.TEXT, false);
            c.drawText(tr, r, x + w - 7 - tr.getWidth(r), y + 19 + i * 11, rc, false);
        }
        HudLayout.end(c);
    }

    private static void voidBanner(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 90.0);
        String txt = "VOID!";
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(sw / 2f, sh / 2f - 40);
        c.getMatrices().scale(2f, 2f);
        c.drawTextWithShadow(tr, txt, -tr.getWidth(txt) / 2, 0, Theme.alpha(0xFFE5334B, pulse));
        c.getMatrices().popMatrix();
    }

    private BedWars() {}
}

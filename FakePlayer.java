package nelose;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side dummy players for testing Target ESP, hit effects and HUD.
 * They exist only on your screen: nothing is sent to the server and nobody else sees them.
 */
public final class FakePlayer {
    private static final String[] NAMES = {"Dummy", "Steve", "Bot", "Target"};
    private static final EquipmentSlot[] GEAR = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final List<OtherClientPlayerEntity> LIST = new ArrayList<>();
    private static ClientWorld world;
    private static int count = -1, nameIdx = -1;

    public static boolean isFake(Entity e) {
        return e instanceof OtherClientPlayerEntity o && LIST.contains(o);
    }

    public static void clear() {
        for (OtherClientPlayerEntity e : LIST) e.discard();
        LIST.clear();
        world = null;
        count = -1;
        nameIdx = -1;
    }

    /** Called from Features.onAttack: the dummy loses a little health and regenerates. */
    public static void onHit(Entity e) {
        if (isFake(e) && e instanceof LivingEntity le) le.setHealth(Math.max(1f, le.getHealth() - 2f));
    }

    public static void tick(MinecraftClient mc) {
        boolean want = Modules.FAKE_PLAYER.on && mc.player != null && mc.world != null;
        if (!want || mc.world != world) clear();
        if (!want) return;

        int n = (int) Modules.FAKE_PLAYER.f("Count"), ni = Modules.FAKE_PLAYER.idx("Name");
        if (LIST.isEmpty() || n != count || ni != nameIdx) {
            clear();
            spawn(mc, n, ni);
        }
        boolean face = Modules.FAKE_PLAYER.b("Face you");
        for (OtherClientPlayerEntity e : LIST) {
            if (e.getHealth() < e.getMaxHealth()) e.setHealth(Math.min(e.getMaxHealth(), e.getHealth() + 0.25f));
            if (face) {
                float yaw = (float) (Math.toDegrees(Math.atan2(mc.player.getZ() - e.getZ(), mc.player.getX() - e.getX())) - 90.0);
                e.setYaw(yaw);
                e.setHeadYaw(yaw);
                e.setBodyYaw(yaw);
            }
        }
    }

    private static void spawn(MinecraftClient mc, int n, int ni) {
        world = mc.world;
        count = n;
        nameIdx = ni;
        double dist = Modules.FAKE_PLAYER.f("Distance");
        double yawRad = Math.toRadians(mc.player.getYaw());
        double fwdX = -Math.sin(yawRad), fwdZ = Math.cos(yawRad);
        for (int i = 0; i < n; i++) {
            String name = NAMES[ni] + (n > 1 ? String.valueOf(i + 1) : "");
            GameProfile gp = new GameProfile(UUID.nameUUIDFromBytes(("NeloseFake" + i).getBytes(StandardCharsets.UTF_8)), name);
            OtherClientPlayerEntity e = new OtherClientPlayerEntity(mc.world, gp);
            e.setId(-7000 - i);

            double a = n == 1 ? 0 : (i - (n - 1) / 2.0) * 0.9;
            double dx = fwdX * Math.cos(a) - fwdZ * Math.sin(a), dz = fwdX * Math.sin(a) + fwdZ * Math.cos(a);
            double px = mc.player.getX() + dx * dist, pz = mc.player.getZ() + dz * dist;
            double py = ground(mc, px, mc.player.getY(), pz);
            e.setPosition(px, py, pz);
            float yaw = mc.player.getYaw() + 180f;
            e.setYaw(yaw);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);

            if (Modules.FAKE_PLAYER.b("Copy gear")) {
                for (EquipmentSlot s : GEAR) e.equipStack(s, mc.player.getEquippedStack(s).copy());
            }
            mc.world.addEntity(e);
            LIST.add(e);
        }
    }

    /** Finds the floor under the spot so the dummy does not float or sit inside a block. */
    private static double ground(MinecraftClient mc, double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        for (int k = 0; k < 3 && !mc.world.getBlockState(new BlockPos(ix, iy, iz)).isAir(); k++) iy++;
        for (int k = 0; k < 5 && mc.world.getBlockState(new BlockPos(ix, iy - 1, iz)).isAir(); k++) iy--;
        return iy;
    }

    private FakePlayer() {}
}

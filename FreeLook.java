package nelose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Free look: the camera turns with the mouse while your player keeps its own direction
 * (so you walk and hit where you were facing). The mixins in nelose.mixin read this state.
 * "Auto F5" switches to third person when it starts and back when it ends.
 */
public final class FreeLook {
    /** True while the camera is detached from the player. */
    public static boolean active;
    public static float yaw, pitch;
    private static boolean was, changedView;
    private static Perspective before = Perspective.FIRST_PERSON;

    public static void tick(MinecraftClient mc) {
        Module M = Modules.FREE_LOOK;
        boolean want = M.on && mc.player != null && mc.world != null;
        if (want && !was) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            before = mc.options.getPerspective();
            changedView = false;
            if (M.b("Auto F5")) {
                Perspective target = M.idx("View") == 1 ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK;
                if (before != target) { mc.options.setPerspective(target); changedView = true; }
            }
        } else if (!want && was) {
            if (changedView && M.b("Return view")) mc.options.setPerspective(before);
            changedView = false;
        }
        active = want;
        was = want;
    }

    /** Called instead of the normal head turn while active (deltas are already scaled by sensitivity). */
    public static void rotate(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
    }

    private FreeLook() {}
}

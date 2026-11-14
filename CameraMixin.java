package nelose.mixin;

import nelose.FreeLook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void moveBy(float surge, float heave, float sway);
    @Shadow protected abstract float clipToSpace(float distance);

    /** After vanilla placed the camera, turn it to the free look angles and move it back out of the player. */
    @Inject(method = "update", at = @At("TAIL"))
    private void nelose$freeLook(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (!FreeLook.active || focusedEntity == null) return;
        if (inverseView) setRotation(FreeLook.yaw + 180f, -FreeLook.pitch);
        else setRotation(FreeLook.yaw, FreeLook.pitch);
        if (thirdPerson) {
            setPos(focusedEntity.getCameraPosVec(tickProgress));
            moveBy(-clipToSpace(4.0f), 0f, 0f);
        }
    }
}

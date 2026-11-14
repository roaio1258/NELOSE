package nelose.mixin;

import nelose.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** While free look is on, the mouse turns the camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void nelose$freeLook(double dx, double dy, CallbackInfo ci) {
        if (FreeLook.active && (Object) this == MinecraftClient.getInstance().player) {
            FreeLook.rotate(dx, dy);
            ci.cancel();
        }
    }
}

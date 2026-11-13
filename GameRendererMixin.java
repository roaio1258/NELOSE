package nelose.mixin;

import nelose.Zoom;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void nelose$zoom(CallbackInfoReturnable cir) {
        double f = Zoom.factor();
        if (f >= 0.9999) return;
        Object v = cir.getReturnValue();
        if (v instanceof Float fl) cir.setReturnValue((float) (fl * f));
        else if (v instanceof Double d) cir.setReturnValue(d * f);
    }
}

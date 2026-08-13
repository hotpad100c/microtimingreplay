package ml.mypals.microtimingreplay.mixin.client;

import ml.mypals.microtimingreplay.client.MTRClientConfig;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(EntityRenderDispatcher.class)
public abstract class MarkerVisibilityMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void mtr$hideMarkers(E entity, Frustum frustum, double camX, double camY,
                                                    double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (MTRClientConfig.hideMarkers() && DisplayUtils.isMarker(entity)) {
            cir.setReturnValue(false);
        }
    }
}

package ml.mypals.microtimingreplay.mixin.client;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("SameParameterValue")
@Mixin(Display.class)
public abstract class DisplayMixin extends Entity {

    @Shadow protected boolean updateRenderState;

    public DisplayMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow protected abstract void updateRenderSubState(boolean interpolate, float partialTicks);


    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void mtr$forceRenderStateIfPaused(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        if (this.level().isClientSide() && this.updateRenderState) {
            if (!this.level().tickRateManager().runsNormally()) {
                this.updateRenderState = false;
                this.updateRenderSubState(false, 0.0F);
            }
        }
    }
}

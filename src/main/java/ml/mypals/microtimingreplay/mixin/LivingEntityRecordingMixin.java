package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.EntitySetHealthEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import ml.mypals.microtimingreplay.util.PlayerProxy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityRecordingMixin {

    @Shadow public abstract float getHealth();
    @Shadow public abstract float getMaxHealth();

    @Inject(method = "setHealth", at = @At("HEAD"))
    private void mtr$onSetHealth(float newHealth, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) return;
        if (entity.getTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) return;

        float oldHealth = this.getHealth();
        if (Math.abs(oldHealth - newHealth) < 1e-4f) return;

        if (MTRState.isRecording(entity.level())) {
            if (!RecordingFilterConfig.isEnabled("entity_set_health")) return;
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().location().toString();
                if (!activeProfile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

                    EntitySetHealthEvent event = new EntitySetHealthEvent(
                            currentTick,
                            PlayerProxy.replayUuid(entity).toString(),
                            PlayerProxy.typeKey(entity),
                            oldHealth,
                            newHealth,
                            this.getMaxHealth(),
                            entity.getX(), entity.getY(), entity.getZ(),
                            dim
                    );

                    MTRState.pushEvent(event);
                    MTRState.popEvent();
                }
            }
        }
    }
}

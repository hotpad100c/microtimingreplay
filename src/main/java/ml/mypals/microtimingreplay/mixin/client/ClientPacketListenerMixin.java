package ml.mypals.microtimingreplay.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    /**
     * A stand-in only ever moves because the replay said so, so smoothing that move over
     * three ticks would show a position the recording never had. Snap instead of lerp.
     */
    @WrapOperation(method = "handleTeleportEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V"))
    private void mtr$snapReplayEntity(Entity entity, double x, double y, double z,
                                      float yRot, float xRot, int steps, Operation<Void> original) {
        if (EntityReplayManager.isReplayEntity(entity)) {
            entity.moveTo(x, y, z, yRot, xRot);
            entity.setOldPosAndRot();
            return;
        }
        original.call(entity, x, y, z, yRot, xRot, steps);
    }
}

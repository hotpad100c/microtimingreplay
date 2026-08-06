package ml.mypals.microtimingreplay.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @WrapOperation(method = "handleEntityPositionSync", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/phys/Vec3;FF)V"))
    private void mtr$snapReplayEntity(Entity entity, Vec3 position, float yRot, float xRot, Operation<Void> original) {
        if (EntityReplayManager.isReplayEntity(entity)) {
            entity.snapTo(position, yRot, xRot);
            return;
        }
        original.call(entity, position, yRot, xRot);
    }
}

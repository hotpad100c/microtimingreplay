package ml.mypals.microtimingreplay.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

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

    @WrapOperation(method = "handleTeleportEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private boolean mtr$snapReplayEntityOnTeleport(PositionMoveRotation change, Set<Relative> relatives,
                                                   Entity entity, boolean interpolate,
                                                   Operation<Boolean> original) {
        if (EntityReplayManager.isReplayEntity(entity)) {
            return original.call(change, relatives, entity, false);
        }
        return original.call(change, relatives, entity, interpolate);
    }
}

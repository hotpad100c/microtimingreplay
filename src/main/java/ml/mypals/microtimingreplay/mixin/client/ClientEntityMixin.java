package ml.mypals.microtimingreplay.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientEntityMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void mtr$onClientEntityTick(Entity entity, CallbackInfo ci) {

        if (entity.level().isClientSide()) {
            boolean isReplayEntity = (entity.getCustomName() != null && "MTRReplayEntity".equals(entity.getCustomName().getString()));
            if (!isReplayEntity && entity.getTeam() != null && "MTRReplayEntity".equals(entity.getTeam().getName())) {
                isReplayEntity = true;
            }
            if (isReplayEntity) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
                entity.xo = entity.getX();
                entity.yo = entity.getY();
                entity.zo = entity.getZ();
                entity.yRotO = entity.getYRot();
                entity.xRotO = entity.getXRot();
                ci.cancel();
            }
        }
    }
}

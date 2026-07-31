package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.event.PostGameEventEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameEventDispatcher.class)
public class GameEventDispatcherMixin {

    @WrapOperation(method = "lambda$post$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/GameEventListener;handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean mtr$onListen1(GameEventListener instance, ServerLevel serverLevel, Holder<GameEvent> gameEventHolder, GameEvent.Context context, Vec3 vec3, Operation<Boolean> original) {
        boolean bl = original.call(instance, serverLevel, gameEventHolder, context, vec3);
        if (bl && MTRState.isRecording(serverLevel)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = serverLevel.dimension().identifier().toString();
            if (profile != null && !profile.outsideAreaVec3(vec3, dim)) {
                MTRState.recordStep(
                        new PostGameEventEvent(
                                serverLevel.getServer().getTickCount() - MTRState.getRecordStartTick(),
                                vec3.x(), vec3.y(), vec3.z(),
                                context.affectedState() != null ? Block.getId(context.affectedState()) : -1,
                                context.sourceEntity() != null ? context.sourceEntity().getStringUUID() : "",
                                dim
                        )
                );
            }
        }
        return bl;
    }

    @WrapOperation(method = "handleGameEventMessagesInQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/GameEventListener;handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean mtr$onListen2(GameEventListener instance, ServerLevel serverLevel, Holder<GameEvent> gameEventHolder, GameEvent.Context context, Vec3 vec3, Operation<Boolean> original) {
        boolean bl = original.call(instance, serverLevel, gameEventHolder, context, vec3);
        if (bl && MTRState.isRecording(serverLevel)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = serverLevel.dimension().identifier().toString();
            if (profile != null && !profile.outsideAreaVec3(vec3, dim)) {
                MTRState.recordStep(
                        new PostGameEventEvent(
                                serverLevel.getServer().getTickCount() - MTRState.getRecordStartTick(),
                                vec3.x(), vec3.y(), vec3.z(),
                                context.affectedState() != null ? Block.getId(context.affectedState()) : -1,
                                context.sourceEntity() != null ? context.sourceEntity().getStringUUID() : "",
                                dim
                        )
                );
            }
        }
        return bl;
    }
}

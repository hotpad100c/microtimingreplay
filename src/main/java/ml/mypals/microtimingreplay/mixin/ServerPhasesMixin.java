package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.event.LevelTickEvent;
import ml.mypals.microtimingreplay.event.PhaseEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ServerPhasesMixin {

    @Shadow
    private int tickCount;

    @Shadow
    public abstract ServerLevel overworld();

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void mtr$onTickLevelPhase(ServerLevel level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        if (MTRState.isRecording(level)) {
            String dim = level.dimension().identifier().toString();
            MTRState.pushEvent(new LevelTickEvent(
                    this.tickCount - MTRState.getRecordStartTick(),
                    "LevelTickPhase",
                    dim
            ));
            original.call(level, hasTimeLeft);
            MTRState.popEvent();
        } else {
            original.call(level, hasTimeLeft);
        }
    }
    @WrapMethod(method = "waitUntilNextTick")
    private void mtr$onTickAsyncTaskPhase(Operation<Void> original) {
        if (MTRState.isRecording(this.overworld())) {
            MTRState.pushEvent(new PhaseEvent(
                    this.tickCount - MTRState.getRecordStartTick(),
                    "AsyncTaskPhase"
            ));
            original.call();
            MTRState.popEvent();
        } else {
            original.call();
        }
    }
}

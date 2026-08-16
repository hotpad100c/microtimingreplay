package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.event.LevelTickEvent;
import ml.mypals.microtimingreplay.event.PhaseEvent;
import ml.mypals.microtimingreplay.event.PhaseType;
import net.minecraft.server.network.ServerConnectionListener;
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
    // 1.21.1 has no PacketProcessor; queued packets are drained by the connection listener.
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V"))
    private void mtr$onProcessQueuedPackets(ServerConnectionListener instance, Operation<Void> original) {
        if (MTRState.isRecording(null) && PhaseType.PACKET_PROCESS.enabled()) {
            MTRState.pushEvent(new PhaseEvent(
                    this.tickCount - MTRState.getRecordStartTick(),
                    PhaseType.PACKET_PROCESS
            ));
            try {
                original.call(instance);
            } finally {
                MTRState.popEvent();
            }
        } else {
            original.call(instance);
        }
    }
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void mtr$onTickLevelPhase(ServerLevel level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        if (MTRState.isRecording(level)) {
            if (!PhaseType.LEVEL_TICK.enabled()) {
                original.call(level, hasTimeLeft);
                return;
            }
            String dim = level.dimension().location().toString();
            MTRState.pushEvent(new LevelTickEvent(
                    this.tickCount - MTRState.getRecordStartTick(),
                    PhaseType.LEVEL_TICK,
                    dim
            ));
            try {
                original.call(level, hasTimeLeft);
            } finally {
                MTRState.popEvent();
            }
        } else {
            original.call(level, hasTimeLeft);
        }
    }
    @WrapMethod(method = "waitUntilNextTick")
    private void mtr$onTickAsyncTaskPhase(Operation<Void> original) {
        if (MTRState.isRecording(null) && PhaseType.ASYNC_TASK.enabled()) {
            MTRState.pushEvent(new PhaseEvent(
                    this.tickCount - MTRState.getRecordStartTick(),
                    PhaseType.ASYNC_TASK
            ));
            try {
                original.call();
            } finally {
                MTRState.popEvent();
            }
        } else {
            original.call();
        }
    }

    // 1.21.1 ticks the players through the player list rather than a server-side helper.
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;tick()V"))
    private void mtr$onTickPlayerPhase(PlayerList instance, Operation<Void> original) {
        if (MTRState.isRecording(null)) {
            if (!PhaseType.PLAYER_TICK.enabled()) {
                original.call(instance);
                return;
            }
            MTRState.pushEvent(new PhaseEvent(
                    this.tickCount - MTRState.getRecordStartTick(),
                    PhaseType.PLAYER_TICK
            ));
            try {
                original.call(instance);
            } finally {
                MTRState.popEvent();
            }
        } else {
            original.call(instance);
        }
    }
}

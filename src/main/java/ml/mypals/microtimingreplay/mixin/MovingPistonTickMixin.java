package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.MovingPistonEvent;
import ml.mypals.microtimingreplay.event.MovingPistonTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public abstract class MovingPistonTickMixin {

    // Note: progress and progressO are private, so we access them only
    // via the public API (getProgress(1.0f) = current progress after tick)
    // For newProgress = progress + 0.5F, we compute it from getProgress(1.0f)

    /**
     * Inject AFTER "float newProgress = entity.progress + 0.5F;" is assigned
     * but BEFORE entity.progress = newProgress.
     * We target the invocation of moveCollidedEntities which happens right after newProgress is computed.
     */
    @Inject(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;moveCollidedEntities(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FLnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;)V"
        ),
        slice = @Slice(
            // Only in the else-branch (progress < 1.0), not the >= 1.0 finalization branch
            from = @At(value = "FIELD",
                target = "Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;progressO:F",
                ordinal = 0)
        )
    )
    private static void mtr$onPistonTick_Progress(Level level, BlockPos pos, BlockState state,
                                                  PistonMovingBlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!MTRState.isRecording(level)) return;

        // At this injection point, entity.progress has NOT yet been updated.
        // getProgress(1.0f) returns the current progress (before the +0.5 step).
        float currentProgress = entity.getProgress(1.0f);
        float newProgress = Math.min(currentProgress + 0.5f, 1.0f);
        long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

        MTRState.recordStep(new MovingPistonTickEvent(
                currentTick,
                pos,
                Math.min(newProgress, 1.0f),
                Block.getId(entity.getMovedState()),
                entity.getDirection(),
                entity.isExtending(),
                entity.isSourcePiston(),
                level.dimension().identifier().toString()
        ));
    }

    /**
     * Inject at HEAD of the progressO >= 1.0 finalization block branch
     * (the tick branch where the block is finalized and setBlock is called).
     * We target the removeBlockEntity call which is the first thing in the >= 1.0 branch.
     */
    @Inject(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V",
            ordinal = 0
        )
    )
    private static void mtr$onPistonTickFinish(Level level, BlockPos pos, BlockState state,
                                                PistonMovingBlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!MTRState.isRecording(level)) return;

        long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

        MTRState.recordStep(new MovingPistonEvent(
                currentTick,
                pos,
                Block.getId(entity.getMovedState()),
                entity.getDirection(),
                entity.isExtending(),
                entity.isSourcePiston(),
                true, // despawn
                level.dimension().identifier().toString()
        ));
    }

    /**
     * Inject at HEAD of finalTick() - fired when piston is force-finalized
     * (e.g. chunk unload or player interaction).
     */
    @Inject(method = "finalTick", at = @At("HEAD"))
    private void mtr$onFinalTick(CallbackInfo ci) {
        PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;
        if (!MTRState.isRecording(level)) return;
        // Only fire if the block entity hasn't been finalized yet (getProgress(1.0f) < 1.0f)
        if (self.getProgress(1.0f) >= 1.0f) return;

        long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

        MTRState.recordStep(new MovingPistonEvent(
                currentTick,
                self.getBlockPos(),
                Block.getId(self.getMovedState()),
                self.getDirection(),
                self.isExtending(),
                self.isSourcePiston(),
                true, // despawn
                level.dimension().identifier().toString()
        ));
    }
}

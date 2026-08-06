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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public class MovingPistonTickMixin {


    @Inject(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;moveCollidedEntities(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FLnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;)V"
        ),
        slice = @Slice(
            from = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;progressO:F",
                    ordinal = 0, opcode = Opcodes.PUTFIELD)
        )
    )
    private static void mtr$onPistonTick_Progress(Level level, BlockPos pos, BlockState state,
                                                  PistonMovingBlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!MTRState.isRecording(level)) return;

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


    @Inject(method = "finalTick", at = @At("HEAD"))
    private void mtr$onFinalTick(CallbackInfo ci) {
        PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;
        if (!MTRState.isRecording(level)) return;
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

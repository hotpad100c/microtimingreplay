package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.UpdateEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NeighborUpdater.class)
public interface NeighborUpdaterMixin {

    @Inject(method = "executeUpdate", at = @At("HEAD"))
    private static void mtr$onExecuteUpdateHead(Level level, BlockState state, BlockPos pos, Block changedBlock, BlockPos neighborPos, boolean movedByPiston, CallbackInfo ci) {
        if (MTRState.isRecording(level)) {
            if (!RecordingFilterConfig.isEnabled("neighbor_update")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().location().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                long tick = level.getServer() != null ? level.getServer().getTickCount() - MTRState.getRecordStartTick() : 0;
                MTRState.pushEvent(new UpdateEvent(tick, "NeighbourUpdate", pos));
            }
        }
    }

    @Inject(method = "executeUpdate", at = @At("RETURN"))
    private static void mtr$onExecuteUpdateReturn(Level level, BlockState state, BlockPos pos, Block changedBlock, BlockPos neighborPos, boolean movedByPiston, CallbackInfo ci) {
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().location().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "executeShapeUpdate", at = @At("HEAD"))
    private static void mtr$onExecuteShapeUpdateHead(LevelAccessor level, Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int updateFlags, int updateLimit, CallbackInfo ci) {
        if (level instanceof Level realLevel && MTRState.isRecording(realLevel)) {
            if (!RecordingFilterConfig.isEnabled("shape_update")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = realLevel.dimension().location().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                long tick = realLevel.getServer() != null ? realLevel.getServer().getTickCount() - MTRState.getRecordStartTick() : 0;
                MTRState.pushEvent(new UpdateEvent(tick, "ShapeUpdate", pos));
            }
        }
    }

    @Inject(method = "executeShapeUpdate", at = @At("RETURN"))
    private static void mtr$onExecuteShapeUpdateReturn(LevelAccessor level, Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int updateFlags, int updateLimit, CallbackInfo ci) {
        if (level instanceof Level realLevel && MTRState.isRecording(realLevel)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = realLevel.dimension().location().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }
}
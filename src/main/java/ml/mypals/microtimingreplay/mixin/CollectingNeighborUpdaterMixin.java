package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.UpdateEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CollectingNeighborUpdater.class)
public class CollectingNeighborUpdaterMixin {
    @Shadow @Final private Level level;

    @Inject(method = "shapeUpdate", at = @At("HEAD"))
    private void mtr$onShapeUpdateHead(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int updateFlags, int updateLimit, CallbackInfo ci) {
        if (MTRState.isRecording(this.level)) {
            if (!RecordingFilterConfig.isEnabled("shape_update")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = this.level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                long tick = this.level.getServer() != null ? this.level.getServer().getTickCount() - MTRState.getRecordStartTick() : 0;
                MTRState.pushEvent(new UpdateEvent(tick, "ShapeUpdate", pos));
            }
        }
    }

    @Inject(method = "shapeUpdate", at = @At("RETURN"))
    private void mtr$onShapeUpdateReturn(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int updateFlags, int updateLimit, CallbackInfo ci) {
        if (MTRState.isRecording(this.level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = this.level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }
}

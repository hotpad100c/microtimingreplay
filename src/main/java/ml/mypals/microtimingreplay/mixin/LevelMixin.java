package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.SetBlockEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void mtr$onSetBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (MTRState.getCurrentState() == MTRState.State.RECORDING) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                if (!activeProfile.isInArea(pos)) return;
                BlockState oldState = this.getBlockState(pos);
                int oldStateId = Block.getId(oldState);
                int newStateId = Block.getId(state);
                long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick(); 
                
                SetBlockEvent event = new SetBlockEvent(
                    currentTick,
                    pos.getX(), pos.getY(), pos.getZ(),
                    oldStateId, newStateId
                );
                
                activeProfile.addEvent(currentTick, event);
            }
        }
    }
}

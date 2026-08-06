package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.BlockEntityTickEvent;
import ml.mypals.microtimingreplay.event.EntitySpawnEvent;
import ml.mypals.microtimingreplay.event.MovingPistonEvent;
import ml.mypals.microtimingreplay.event.SetBlockEvent;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin implements ScheduledTickAccess {

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow
    @Final
    private boolean isClientSide;

    @Shadow
    public abstract ResourceKey<Level> dimension();

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void mtr$onSetBlock(BlockPos pos, BlockState blockState, int updateFlags, int updateLimit, CallbackInfoReturnable<Boolean> cir) {
        if (MTRState.isRecording((Level) (Object) this)) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                if (activeProfile.outsideArea(pos, this.dimension().identifier().toString())) return;
                BlockState oldState = this.getBlockState(pos);
                int oldStateId = Block.getId(oldState);
                int newStateId = Block.getId(blockState);
                long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

                SetBlockEvent event = new SetBlockEvent(
                    currentTick, updateFlags, updateLimit,
                    pos.getX(), pos.getY(), pos.getZ(),
                    oldStateId, newStateId, this.dimension().identifier().toString()
                );

                MTRState.recordStep(event);
            }
        }
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void mtr$onSetBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (isClientSide) return;
        if (!(blockEntity instanceof PistonMovingBlockEntity piston)) return;
        if (!MTRState.isRecording((Level) (Object) this)) return;

        long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
        MTRState.recordStep(new MovingPistonEvent(
                currentTick,
                piston.getBlockPos(),
                Block.getId(piston.getMovedState()),
                piston.getDirection(),
                piston.isExtending(),
                piston.isSourcePiston(),
                false, // spawn
                this.dimension().identifier().toString()
        ));
    }


    @WrapOperation(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void mtr$onTickBlockEntity(TickingBlockEntity ticker, Operation<Void> original) {
        Level level = (Level) (Object) this;
        if (!MTRState.isRecording(level)) {
            original.call(ticker);
            return;
        }

        MTRProfile profile = MTRState.getActiveProfile();
        String dim = this.dimension().identifier().toString();
        if (profile == null || profile.outsideArea(ticker.getPos(), dim)) {
            original.call(ticker);
            return;
        }

        MTRState.pushEvent(new BlockEntityTickEvent(
                MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick(),
                ticker.getType(),
                ticker.getPos(),
                dim
        ));
        try {
            original.call(ticker);
        } finally {
            MTRState.popEvent();
        }
    }
}

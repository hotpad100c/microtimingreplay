package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.BlockEntityCreationEvent;
import ml.mypals.microtimingreplay.event.BlockEntityTickEvent;
import ml.mypals.microtimingreplay.event.MovingPistonEvent;
import ml.mypals.microtimingreplay.event.SetBlockEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelMixin implements ScheduledTickAccess {

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow
    @Final
    private boolean isClientSide;

    @Shadow
    public abstract ResourceKey<Level> dimension();

    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean mtr$onSetBlock(BlockPos pos, BlockState blockState, int updateFlags, int updateLimit, Operation<Boolean> original) {
        if (MTRState.isRecording((Level) (Object) this)) {
            if (!RecordingFilterConfig.isEnabled("set_block")) {
                return original.call(pos, blockState, updateFlags, updateLimit);
            }
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                if (activeProfile.outsideArea(pos, this.dimension().identifier().toString())) {
                    return original.call(pos, blockState, updateFlags, updateLimit);
                }
                BlockState oldState = this.getBlockState(pos);
                int oldStateId = Block.getId(oldState);
                int newStateId = Block.getId(blockState);
                long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

                SetBlockEvent event = new SetBlockEvent(
                    currentTick, updateFlags, updateLimit,
                    pos.getX(), pos.getY(), pos.getZ(),
                    oldStateId, newStateId, false, this.dimension().identifier().toString()
                );

                MTRState.pushEvent(event);
                boolean succeed = false;
                try {
                    succeed = original.call(pos, blockState, updateFlags, updateLimit);
                    return succeed;
                } finally {
                    event.succeed = succeed;
                    MTRState.popEvent();
                }
            }
        }
        return original.call(pos, blockState, updateFlags, updateLimit);
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void mtr$onSetBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (isClientSide || blockEntity == null) return;
        Level level = (Level) (Object) this;
        if (!MTRState.isRecording(level)) return;

        String dim = this.dimension().identifier().toString();
        MTRProfile activeProfile = MTRState.getActiveProfile();

        if (blockEntity instanceof PistonMovingBlockEntity piston) {
            if (RecordingFilterConfig.isEnabled("moving_piston_start")) {
                long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
                MTRState.recordStep(new MovingPistonEvent(
                        currentTick,
                        piston.getBlockPos(),
                        Block.getId(piston.getMovedState()),
                        piston.getDirection(),
                        piston.isExtending(),
                        piston.isSourcePiston(),
                        false, // spawn
                        dim
                ));
            }
        } else {
            if (RecordingFilterConfig.isEnabled("block_entity_creation")) {
                BlockPos pos = blockEntity.getBlockPos();
                if (activeProfile != null && !activeProfile.outsideArea(pos, dim)) {
                    long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
                    String typeKey = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();

                    MTRState.recordStep(new BlockEntityCreationEvent(
                            currentTick,
                            pos,
                            typeKey,
                            dim
                    ));
                }
            }
        }
    }


    @WrapOperation(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void mtr$onTickBlockEntity(TickingBlockEntity ticker, Operation<Void> original) {
        Level level = (Level) (Object) this;
        if (!MTRState.isRecording(level) || !RecordingFilterConfig.isEnabled("block_entity_tick")) {
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

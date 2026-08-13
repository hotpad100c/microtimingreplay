package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.PistonStructureEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {

    @Shadow @Final private Level level;
    @Shadow @Final private BlockPos pistonPos;
    @Shadow @Final private boolean extending;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;
    @Unique
    private BlockPos mtr$blockingPos;

    @WrapOperation(method = "resolve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonBaseBlock;isPushable(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;ZLnet/minecraft/core/Direction;)Z"))
    private boolean mtr$trackStartObstruction(BlockState state, Level level, BlockPos pos, Direction direction,
                                              boolean canBreak, Direction pistonDirection,
                                              Operation<Boolean> original) {
        boolean pushable = original.call(state, level, pos, direction, canBreak, pistonDirection);
        if (!pushable) {
            mtr$blockingPos = pos;
        }
        return pushable;
    }

    @WrapOperation(method = "addBlockLine", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonBaseBlock;isPushable(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;ZLnet/minecraft/core/Direction;)Z"))
    private boolean mtr$trackLineObstruction(BlockState state, Level level, BlockPos pos, Direction direction,
                                             boolean canBreak, Direction pistonDirection,
                                             Operation<Boolean> original) {
        boolean pushable = original.call(state, level, pos, direction, canBreak, pistonDirection);
        if (!pushable && canBreak) {
            mtr$blockingPos = pos;
        }
        return pushable;
    }

    @Inject(method = "resolve", at = @At("HEAD"))
    private void mtr$onResolveHead(CallbackInfoReturnable<Boolean> cir) {
        mtr$blockingPos = null;
    }

    @Inject(method = "resolve", at = @At("RETURN"))
    private void mtr$onResolveReturn(CallbackInfoReturnable<Boolean> cir) {
        try {
            mtr$record(cir.getReturnValue());
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private void mtr$record(boolean resolved) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!MTRState.isRecording(serverLevel) || !RecordingFilterConfig.isEnabled("piston_structure")) {
            return;
        }
        String dim = serverLevel.dimension().identifier().toString();
        MTRProfile profile = MTRState.getActiveProfile();
        if (profile == null || profile.outsideArea(this.pistonPos, dim)) {
            return;
        }

        String blockingBlock = "";
        if (!resolved && mtr$blockingPos != null) {
            BlockState blockingState = serverLevel.getBlockState(mtr$blockingPos);
            blockingBlock = BuiltInRegistries.BLOCK.getKey(blockingState.getBlock()).toString();
        }

        long tick = serverLevel.getServer().getTickCount() - MTRState.getRecordStartTick();
        MTRState.recordStep(new PistonStructureEvent(
                tick,
                this.pistonPos,
                this.pushDirection,
                this.extending,
                resolved,
                new ArrayList<>(this.toPush),
                new ArrayList<>(this.toDestroy),
                resolved ? null : mtr$blockingPos,
                blockingBlock,
                dim
        ));
    }
}

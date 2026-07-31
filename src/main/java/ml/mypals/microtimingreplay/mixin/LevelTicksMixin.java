package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.AddScheduleTickEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelTicks.class)
public class LevelTicksMixin {

    @Inject(method = "schedule", at = @At("HEAD"))
    private void mtr$onSchedule(ScheduledTick<?> tick, CallbackInfo ci) {
        if (MTRState.getCurrentState() == MTRState.State.RECORDING && MicroTimingReplay.server != null) {
            String dimension = "";
            for (ServerLevel level : MicroTimingReplay.server.getAllLevels()) {
                if (level.getBlockTicks() == (Object) this || level.getFluidTicks() == (Object) this) {
                    dimension = level.dimension().identifier().toString();
                    break;
                }
            }
            if (dimension.isEmpty()) return;

            MTRProfile profile = MTRState.getActiveProfile();
            if (profile != null && profile.outsideArea(tick.pos(), dimension)) {
                return;
            }
            long trigger = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
            String typeId;
            if (tick.type() instanceof Block b) {
                typeId = BuiltInRegistries.BLOCK.getKey(b).toString();
            } else if (tick.type() instanceof Fluid f) {
                typeId = BuiltInRegistries.FLUID.getKey(f).toString();
            } else {
                typeId = tick.type().toString();
            }
            MTRState.recordStep(new AddScheduleTickEvent(trigger, tick.pos().getX(), tick.pos().getY(), tick.pos().getZ(), typeId, tick.triggerTick(), tick.priority().getValue(), tick.subTickOrder(), dimension));
        }
    }
}

package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.ItemTransferEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HopperBlockEntity.class)
public abstract class HopperTransferMixin {

    @Unique
    private static BlockPos getContainerPos(Container container) {
        if (container instanceof BlockEntity be) {
            return be.getBlockPos();
        } else if (container instanceof Entity entity) {
            return entity.blockPosition();
        }
        return null;
    }

    @Unique
    private static Level getContainerLevel(Container container) {
        if (container instanceof BlockEntity be) {
            return be.getLevel();
        } else if (container instanceof Entity entity) {
            return entity.level();
        }
        return null;
    }

    @WrapMethod(
        method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"
    )
    private static ItemStack mtr$onAddItem(Container source, Container destination, ItemStack stack, Direction direction, Operation<ItemStack> original) {
        Level level = getContainerLevel(destination);
        if (level == null) level = getContainerLevel(source);

        if (level != null && !level.isClientSide() && MTRState.isRecording(level)) {
            if (RecordingFilterConfig.isEnabled("item_transfer")) {
                MTRProfile profile = MTRState.getActiveProfile();
                BlockPos sourcePos = getContainerPos(source);
                BlockPos destPos = getContainerPos(destination);
                String dim = level.dimension().identifier().toString();

                if (profile != null && sourcePos != null && destPos != null) {
                    if (!profile.outsideArea(sourcePos, dim) || !profile.outsideArea(destPos, dim)) {
                        int startCount = stack.getCount();
                        ItemStack result = original.call(source, destination, stack, direction);
                        int transferredCount = startCount - result.getCount();

                        if (transferredCount > 0) {
                            long tick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
                            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

                            MTRState.recordStep(new ItemTransferEvent(
                                tick,
                                sourcePos,
                                destPos,
                                itemId,
                                transferredCount,
                                new CompoundTag(),
                                dim
                            ));
                        }
                        return result;
                    }
                }
            }
        }
        return original.call(source, destination, stack, direction);
    }
}

package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRComponent;

import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SetBlockEvent extends BlockPosEvent {
    public static final String TYPE = "setBlock";
    public int bitFlag = 0;
    public int updateLimit = 0;
    
    private final int oldStateId;
    private final int newStateId;

    public SetBlockEvent(long tick,int bitFlag,int updateLimit, int x, int y, int z, int oldStateId, int newStateId, String dimension) {
        super(tick, TYPE, new BlockPos(x, y, z), dimension);
        this.bitFlag = bitFlag;
        this.updateLimit = updateLimit;
        this.oldStateId = oldStateId;
        this.newStateId = newStateId;
    }

    public int getOldStateId() { return oldStateId; }
    public int getNewStateId() { return newStateId; }
    public int getBitFlag(){return bitFlag;}
    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.GREEN;
    }

    @Override
    public void apply(ServerLevel level, boolean forward) {
        int stateId = forward ? getNewStateId() : getOldStateId();
        BlockState state = Block.stateById(stateId);
        BlockPos pos = new BlockPos(getX(), getY(), getZ());
        level.setBlock(pos, state, 2 | 816, 0);
        super.apply(level, forward);
    }

    @Override
    public void display(ServerLevel level) {
        BlockPos pos = getPos();
        MTRMarker.spawnBlockDisplay(level, pos, Blocks.LIME_STAINED_GLASS.defaultBlockState(), 1.005F, ChatFormatting.GREEN);

        BlockState oldState = Block.stateById(getOldStateId());
        BlockState newState = Block.stateById(getNewStateId());

        Component text = DisplayUtils.formatStateDiff(oldState, newState).copy()
                .append(Component.literal("\n" + bitFlag).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | "))
                .append(Component.literal("" + updateLimit).withStyle(ChatFormatting.YELLOW));

        MTRMarker.spawnTextDisplay(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, text, 0.7f);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("oldStateId", oldStateId);
        tag.putInt("newStateId", newStateId);
        tag.putInt("flag", bitFlag);
        tag.putInt("limit", updateLimit);
        
        if (!getChildren().isEmpty()) {
            ListTag childList = new ListTag();
            for (MTREvent child : getChildren()) {
                childList.add(child.writeNBT());
            }
            tag.put("children", childList);
        }
        
        return tag;
    }
    @Override
    public MutableComponent fillHoverText() {
        BlockState oldState = Block.stateById(getOldStateId());
        BlockState newState = Block.stateById(getNewStateId());

        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.setblock_title",
                "SetBlockState @ [%d, %d, %d]",
                getX(), getY(), getZ()
        ).append(Component.literal("\n")).withStyle(ChatFormatting.AQUA);

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        text.append(DisplayUtils.formatStateDiff(oldState, newState));

        text.append(Component.literal("\n\n")).append(MTRComponent.translatable(
                "mtr.tooltip.flags",
                "Flags (%d):",
                bitFlag
        ).withStyle(ChatFormatting.GOLD));

        String[] flagNames = {
            "NOTIFY_NEIGHBORS (1)",
            "NOTIFY_LISTENERS (2)",
            "NO_REDRAW (4)",
            "REDRAW_ON_MAIN_THREAD (8)",
            "FORCE_STATE (16)",
            "SKIP_DROPS (32)",
            "MOVED (64)",
            "SKIP_LIGHTING_UPDATES (128)",
            "SKIP_BLOCK_ENTITY_SIDEEFFECTS (256)"
        };

        for (int i = 0; i < flagNames.length; i++) {
            boolean active = (bitFlag & (1 << i)) != 0;
            text.append(Component.literal("\n  bit " + i + " [" + (active ? "1" : "0") + "]: ")
                    .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .append(Component.literal(flagNames[i])
                    .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }

        if (updateLimit > 0) {
            text.append(Component.literal("\n")).append(MTRComponent.translatable(
                    "mtr.tooltip.recursion_limit",
                    "Recursion Limit: %d",
                    updateLimit
            ).withStyle(ChatFormatting.YELLOW));
        }

        return text;
    }

    public static SetBlockEvent readNBT(CompoundTag tag) {
        long tick = tag.getLong("tick").orElse(0L);
        int bitFlag= tag.getInt("flag").orElse(0);
        int updateLimit= tag.getInt("limit").orElse(0);
        int x = tag.getInt("x").orElse(0);
        int y = tag.getInt("y").orElse(0);
        int z = tag.getInt("z").orElse(0);
        int oldStateId = tag.getInt("oldStateId").orElse(0);
        int newStateId = tag.getInt("newStateId").orElse(0);
        SetBlockEvent event = new SetBlockEvent(tick,bitFlag,updateLimit, x, y, z, oldStateId, newStateId, tag.getString("dimension").orElse(""));
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}


package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public class BlockEntityTickEvent extends BlockPosEvent {
    public static final String TYPE = "blockEntityTick";

    private final String blockEntityType;

    public BlockEntityTickEvent(long tick, String blockEntityType, BlockPos pos, String dimension) {
        super(tick, TYPE, pos, dimension);
        this.blockEntityType = blockEntityType != null ? blockEntityType : "unknown";
    }

    public String getBlockEntityType() { return blockEntityType; }

    @Override
    public boolean isQueueScope() {
        return true;
    }

    @Override
    public String filterId() {
        return "block_entity_tick";
    }

    @Override
    public ChatFormatting getColor() {
        return getChildren().isEmpty() ? ChatFormatting.RED : ChatFormatting.YELLOW;
    }

    @Override
    public MutableComponent getScoreboardText() {
        MutableComponent comp = MTRComponent.translatable(
                "mtr.scoreboard.event.leaf.blockentitytick",
                "[Block Entity Tick] " + blockEntityType);
        appendPosText(comp);
        if (getChildren().isEmpty()) {
            return comp.append(Component.literal(" ❌"));
        }
        return comp;
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.block_entity_tick_title",
                "Block Entity Tick @ [%d, %d, %d]",
                getX(), getY(), getZ()
        ).append(Component.literal("\n")).withStyle(getColor());

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
                .append(MTRComponent.translatable("mtr.tooltip.target", "Type: %s", blockEntityType).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.sub_events", "Sub-events: %d", getChildren().size()).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("blockEntityType", blockEntityType);
        return tag;
    }

    public static BlockEntityTickEvent readNBT(CompoundTag tag) {
        BlockEntityTickEvent event = new BlockEntityTickEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("blockEntityType").orElse("unknown"),
                new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0)),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRNbt;


import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public class QueueEvent extends BlockPosEvent {
    public static final String TYPE = "queue";
    
    private final String queueName;

    public QueueEvent(long tick, String queueName, BlockPos pos, String dimension) {
        super(tick, TYPE, pos, dimension);
        this.queueName = queueName;
    }

    @Override
    public boolean isQueueScope() {
        return true;
    }

    @Override
    public String filterId() {
        // The mixins name the queue; map it back to the filter entry that gated it.
        return switch (queueName == null ? "" : queueName) {
            case "ExecuteBlockEvent" -> "execute_block_event";
            case "ExecuteBlockTick" -> "block_tick";
            case "ExecuteFluidTick" -> "fluid_tick";
            default -> null;
        };
    }


    @Override
    public MutableComponent getScoreboardText() {
        String keyName = queueName != null ? queueName.toLowerCase() : "unknown";
        String fallback = queueName != null ? queueName : "Queue";
        if (!fallback.isEmpty()) fallback = fallback.substring(0, 1).toUpperCase() + fallback.substring(1);
        MutableComponent comp = MTRComponent.translatable("mtr.scoreboard.event.queue." + keyName, fallback);
        appendPosText(comp);
        if (getChildren().isEmpty()) {
            return comp.append(Component.literal("∅"));
        }
        return comp;
    }

    @Override
    public ChatFormatting getColor() {
        return getChildren().isEmpty() ? ChatFormatting.RED : ChatFormatting.YELLOW;
    }

    @Override
    public MutableComponent fillHoverText() {
        return MTRComponent.translatable("mtr.tooltip.queue_title", "Queue @ [%d, %d, %d]", getX(), getY(), getZ())
                .append(Component.literal("\n")).withStyle(getColor())
                .append(MTRComponent.translatable("mtr.tooltip.queue_name", "Queue Name: %s", queueName != null ? queueName : "unknown").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.sub_events", "Sub-events: %d", getChildren().size()).withStyle(ChatFormatting.WHITE));
    }

    public String getQueueName() {
        return queueName;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("queueName", queueName);
        return tag;
    }

    public static QueueEvent readNBT(CompoundTag tag) {
        QueueEvent event = new QueueEvent(tag.getLong("tick"), MTRNbt.getString(tag, "queueName", "unknown"), new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag.getString("dimension"));
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

package ml.mypals.microtimingreplay.event;


import ml.mypals.microtimingreplay.config.MTRGameRules;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

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
    public boolean saveEvenWithoutAction(MinecraftServer server) {
        return !server.getGameRules().get(MTRGameRules.SKIP_EMPTY_QUEUE);
    }
    public void display(ServerLevel level) {
        MTRMarker.spawnBlockDisplay(level, getPos(), Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), 1.005F, getColor());
    }


    @Override
    public MutableComponent getScoreboardText() {
        String keyName = queueName != null ? queueName.toLowerCase() : "unknown";
        String fallback = queueName != null ? queueName : "Queue";
        if (!fallback.isEmpty()) fallback = fallback.substring(0, 1).toUpperCase() + fallback.substring(1);
        MutableComponent comp = MTRComponent.translatable("mtr.scoreboard.event.queue." + keyName, fallback);
        appendPosText(comp);
        if (getChildren().isEmpty()) {
            return comp.append(Component.literal(" ❌"));
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
        QueueEvent event = new QueueEvent(tag.getLong("tick").orElse(0L), tag.getString("queueName").orElse("unknown"), new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0)), tag.getString("dimension").orElse(""));
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

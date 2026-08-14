package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRComponent;

import net.minecraft.network.chat.MutableComponent;


import ml.mypals.microtimingreplay.marker.MTRMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

public class AddScheduleTickEvent extends BlockPosEvent {
    public static final String TYPE = "addScheduleTick";
    
    private final String typeId;
    private final long triggerTick;
    private final int priority;
    private final long subTickOrder;
    private final boolean shouldFail;

    public AddScheduleTickEvent(long tick, int x, int y, int z, String typeId,
                                long triggerTick, int priority,
                                long subTickOrder, String dimension, boolean shouldFail) {
        super(tick, TYPE, new BlockPos(x, y, z), dimension);
        this.typeId = typeId;
        this.triggerTick = triggerTick;
        this.priority = priority;
        this.subTickOrder = subTickOrder;
        this.shouldFail = shouldFail;
    }

    public String getTypeId() { return typeId; }
    public long getTriggerTick() { return triggerTick; }
    public int getPriority() { return priority; }
    public long getSubTickOrder() { return subTickOrder; }
    public boolean shouldFail() { return shouldFail; }


    @Override
    public String filterId() {
        return "add_schedule_tick";
    }

    @Override
    public ChatFormatting getColor() {
        return shouldFail ? ChatFormatting.GRAY : ChatFormatting.YELLOW;
    }

    @Override
    public MutableComponent fillHoverText() {
        String prioName = switch (priority) {
            case -3 -> "EXTREMELY_HIGH";
            case -2 -> "HIGH";
            case -1 -> "NORMAL";
            case 0 -> "LOW";
            case 1 -> "EXTREMELY_LOW";
            default -> "CUSTOM";
        };

        long delay = triggerTick - getTick();

        MutableComponent text = MTRComponent.translatable("mtr.tooltip.schedule_tick_title", "Scheduled Tile Tick @ [%d, %d, %d]", getX(), getY(), getZ())
                .append(Component.literal("\n")).withStyle( shouldFail ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW);

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }
        text
                .append(MTRComponent.translatable("mtr.tooltip.target", "Target: %s", typeId != null ? typeId : "unknown").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.trigger_tick", "Trigger Tick: %d (Delay: %dgt)", triggerTick, delay).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.priority", "Priority: %s (%d)", prioName, priority).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.subtick_order", "SubTick Order: %d", subTickOrder).withStyle(ChatFormatting.DARK_GRAY));

        return shouldFail ? text.append(Component.literal(" \n ✖").withStyle(ChatFormatting.RED)) : text;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("typeId", typeId != null ? typeId : "unknown");
        tag.putLong("triggerTick", triggerTick);
        tag.putInt("priority", priority);
        tag.putLong("subTickOrder", subTickOrder);
        tag.putBoolean("shouldFail", shouldFail);
        return tag;
    }

    public static AddScheduleTickEvent readNBT(CompoundTag tag) {
        AddScheduleTickEvent event = new AddScheduleTickEvent(
                tag.getLong("tick").orElse(0L),
                tag.getInt("x").orElse(0),
                tag.getInt("y").orElse(0),
                tag.getInt("z").orElse(0),
                tag.getString("typeId").orElse("unknown"),
                tag.getLong("triggerTick").orElse(0L),
                tag.getInt("priority").orElse(0),
                tag.getLong("subTickOrder").orElse(0L),
                tag.getString("dimension").orElse(""),
                tag.getBoolean("shouldFail").orElse(false)
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        super.display(level, scale);
        BlockPos pos = getPos();
        Component text = Component.literal(getTypeId()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("\n"))
                .append(Component.literal( " Tri: " + triggerTick))
                .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal( " Pri: " + priority)
                .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal( " Sub: " + subTickOrder)));

         MTRMarker.spawnTextDisplay(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, text, 0.7f);
    }
}

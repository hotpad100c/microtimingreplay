package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRNbt;

import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class LevelTickEvent extends PhaseEvent {
    public static final String TYPE = "levelTick";

    private String dimension = "";

    public LevelTickEvent(long tick, String phaseName, @Nullable String dimension) {
        super(tick, phaseName);
        this.dimension = dimension != null ? dimension : "";
    }

    public LevelTickEvent(long tick, PhaseType phase, @Nullable String dimension) {
        this(tick, phase.phaseName(), dimension);
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension != null ? dimension : "";
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.AQUA;
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent comp = MTRComponent.translatable("mtr.tooltip.phase_title", "Tick Phase @ Tick %d", getTick())
                .append(Component.literal("\n")).withStyle(ChatFormatting.AQUA)
                .append(MTRComponent.translatable("mtr.tooltip.phase_name", "Phase: %s", getPhaseName() != null ?
                        super.getScoreboardText().getString() : "unknown").withStyle(ChatFormatting.AQUA));

        if (dimension != null && !dimension.isEmpty()) {
            comp.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", dimension).withStyle(ChatFormatting.WHITE));
        }

        comp.append(Component.literal("\n"))
            .append(MTRComponent.translatable("mtr.tooltip.sub_events", "Sub-events: %d", getChildren().size()).withStyle(ChatFormatting.WHITE));

        return comp;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return Component.literal(" [" + dimension + "]").withStyle(ChatFormatting.WHITE);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("type", TYPE);
        if (dimension != null && !dimension.isEmpty()) {
            tag.putString("dimension", dimension);
        }
        return tag;
    }

    public static LevelTickEvent readNBT(CompoundTag tag) {
        LevelTickEvent event = new LevelTickEvent(
            tag.getLong("tick"),
            MTRNbt.getString(tag, "phaseName", "LevelTickPhase"),
            tag.getString("dimension")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

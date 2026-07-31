package ml.mypals.microtimingreplay.event;


import ml.mypals.microtimingreplay.MTRGameRules;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public class PhaseEvent extends MTREvent {
    public static final String TYPE = "phase";
    
    private final String phaseName;

    public PhaseEvent(long tick, String phaseName) {
        super(TYPE, tick);
        this.phaseName = phaseName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public boolean saveEvenWithoutAction(MinecraftServer server) {
        return !server.getGameRules().get(MTRGameRules.SKIP_EMPTY_PHASE);
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.LIGHT_PURPLE;
    }

    @Override
    public MutableComponent fillHoverText() {
        return MTRComponent.translatable("mtr.tooltip.phase_title", "Tick Phase @ Tick %d", getTick())
                .append(Component.literal("\n")).withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(MTRComponent.translatable("mtr.tooltip.phase_name", "Phase: %s",
                        phaseName != null ? getScoreboardText().getString() : "unknown").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.sub_events", "Sub-events: %d", getChildren().size()).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public MutableComponent getScoreboardText() {
        String keyName = getPhaseName() != null ? getPhaseName().toLowerCase() : "unknown";
        String fallback = getPhaseName() != null ? getPhaseName() : "Phase";
        if (fallback.length() > 0) fallback = fallback.substring(0, 1).toUpperCase() + fallback.substring(1);
        return MTRComponent.translatable(
            "mtr.scoreboard.event.phase." + keyName, 
            fallback
        ).append(Component.literal(" @T " + getTick()));
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType());
        tag.putLong("tick", getTick());
        tag.putString("phaseName", phaseName);
        
        if (!getChildren().isEmpty()) {
            ListTag childList = new ListTag();
            for (MTREvent child : getChildren()) {
                childList.add(child.writeNBT());
            }
            tag.put("children", childList);
        }
        return tag;
    }

    public static PhaseEvent readNBT(CompoundTag tag) {
        PhaseEvent event = new PhaseEvent(tag.getLong("tick").orElse(0L), tag.getString("phaseName").orElse("unknown"));
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

package ml.mypals.microtimingreplay.event;


import ml.mypals.microtimingreplay.config.MTRGameRules;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;


public class UpdateEvent extends MTREvent {
    public static final String TYPE = "update";
    private String updateName;
    private int x, y, z;
    private int sx, sy, sz;

    public UpdateEvent() {}

    public UpdateEvent(long tick, String updateName, BlockPos pos) {
        super(TYPE, tick);
        this.updateName = updateName;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public String getUpdateName() {
        return updateName;
    }

    @Override
    public boolean saveEvenWithoutAction(MinecraftServer server) {
        return !server.getGameRules().get(MTRGameRules.SKIP_EMPTY_UPDATE);
    }

    @Override
    public ChatFormatting getColor() {
        String name = getUpdateName().toLowerCase();
        if (name.contains("neighbour")) return ChatFormatting.RED;
        if (name.contains("shape")) return ChatFormatting.AQUA;
        return ChatFormatting.WHITE;
    }

    @Override
    public MutableComponent fillHoverText() {
        return MTRComponent.translatable("mtr.tooltip.update_title", "Block Update @ [%d, %d, %d]", getX(), getY(), getZ())
                .append(Component.literal("\n")).withStyle(getColor())
                .append(MTRComponent.translatable("mtr.tooltip.method", "Method: %s", updateName != null ? updateName : "unknown").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.sub_events", "Sub-events: %d", getChildren().size()).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public MutableComponent getScoreboardText() {
        String keyName = getUpdateName().toLowerCase();
        return appendPosText(MTRComponent.translatable(
            "mtr.scoreboard.event.update." + keyName, 
            getUpdateName()
        ));
    }
    public MutableComponent appendPosText(MutableComponent mutableComponent){
        return mutableComponent.append(Component.literal(" @[" + getX() + "," + getY() + "," + getZ() + "]")
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/tp @p " + getX() + " " + getY() + " " + getZ())
                ))
        );
    }

    @Override
    public void apply(ServerLevel level, boolean forward) {
        super.apply(level, forward);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType());
        tag.putLong("tick", getTick());
        tag.putString("updateName", updateName);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        
        if (!getChildren().isEmpty()) {
            ListTag childList = new ListTag();
            for (MTREvent child : getChildren()) {
                childList.add(child.writeNBT());
            }
            tag.put("children", childList);
        }
        return tag;
    }

    public static UpdateEvent readNBT(CompoundTag tag) {
        UpdateEvent event = new UpdateEvent(
            tag.getLong("tick").orElse(0L),
            tag.getString("updateName").orElse("unknown"),
            new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0))
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}


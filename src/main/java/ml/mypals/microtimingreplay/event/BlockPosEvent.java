package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRNbt;

import net.minecraft.ChatFormatting;

import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class BlockPosEvent extends MTREvent {
    public static final String TYPE = "invisibleStep";
    private final int x;
    private final int y;
    private final int z;
    private String dimension = "";


    public BlockPosEvent(long tick, @Nullable String stepName, BlockPos pos, @Nullable String dimension) {
        super(stepName == null ? TYPE : stepName, tick);
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.dimension = dimension != null ? dimension : "";
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public BlockPos getPos() { return new BlockPos(x,y,z); }
    public String getDimension() { return dimension; }
    public void setDimension(String dim) { this.dimension = dim != null ? dim : ""; }

    @Override
    public void applySelf(ServerLevel level, boolean forward) {
        ServerLevel targetLevel = level;
        if (dimension != null && !dimension.isEmpty()) {
            for (ServerLevel sl : level.getServer().getAllLevels()) {
                if (sl.dimension().location().toString().equals(dimension)) {
                    targetLevel = sl;
                    break;
                }
            }
        }
        BlockPos pos = getPos();
        if (!targetLevel.hasChunkAt(pos)) {
            targetLevel.getChunk(pos);
        }
    }
    
    @Override
    public BlockPos getMarkerPos() {
        return getPos();
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = Component.literal(getType() + " @ [" + x + ", " + y + ", " + z + "]");
        if (dimension != null && !dimension.isEmpty()) {
            text.append(Component.literal("\n")).append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", dimension).withStyle(ChatFormatting.GOLD));
        }
        return text;
    }

    @Override
    public MutableComponent getScoreboardText() {
        String keyName = getType().toLowerCase();
        return appendPosText(MTRComponent.translatable(
            "mtr.scoreboard.event.leaf." + keyName, 
            getType()
        ));
    }

    public MutableComponent appendPosText(MutableComponent mutableComponent){
        String tpCmd = (dimension != null && !dimension.isEmpty())
            ? "/execute in " + dimension + " run tp @p " + getX() + " " + getY() + " " + getZ()
            : "/tp @p " + getX() + " " + getY() + " " + getZ();
        return mutableComponent.append(Component.literal(" @[" + getX() + "," + getY() + "," + getZ() + "]")
            .withStyle(style -> style.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd)
            ))
        );
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType());
        tag.putLong("tick", getTick());
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        if (dimension != null && !dimension.isEmpty()) {
            tag.putString("dimension", dimension);
        }
        
        if (!getChildren().isEmpty()) {
            ListTag childList = new ListTag();
            for (MTREvent child : getChildren()) {
                childList.add(child.writeNBT());
            }
            tag.put("children", childList);
        }
        return tag;
    }

    public static BlockPosEvent readNBT(CompoundTag tag) {
        BlockPosEvent event = new BlockPosEvent(
            tag.getLong("tick"),
            MTRNbt.getString(tag, "type", "unknown"),
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
            tag.getString("dimension")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

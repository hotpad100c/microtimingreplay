package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRNbt;

import net.minecraft.ChatFormatting;

import java.util.Locale;

import ml.mypals.microtimingreplay.util.MTRComponent;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class Vec3PosEvent extends MTREvent {
    private final double x;
    private final double y;
    private final double z;
    private String dimension = "";

    public Vec3PosEvent(long tick, @Nullable String stepName, Vec3 pos, @Nullable String dimension) {
        super(stepName == null ? "Vec3Event" : stepName, tick);
        this.x = pos.x();
        this.y = pos.y();
        this.z = pos.z();
        this.dimension = dimension != null ? dimension : "";
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public Vec3 getPos() { return new Vec3(x,y,z); }
    public String getDimension() { return dimension; }
    public void setDimension(String dim) { this.dimension = dim != null ? dim : ""; }

    @Override
    public void applySelf(ServerLevel level, boolean forward) {
        ServerLevel targetLevel = level;
        if (dimension != null && !dimension.isEmpty() && level.getServer() != null) {
            for (ServerLevel sl : level.getServer().getAllLevels()) {
                if (sl.dimension().location().toString().equals(dimension)) {
                    targetLevel = sl;
                    break;
                }
            }
        }
        BlockPos pos = BlockPos.containing(getPos());
        if (!targetLevel.hasChunkAt(pos)) {
            targetLevel.getChunk(pos);
        }
    }
    
    @Override
    public BlockPos getMarkerPos() {
        return BlockPos.containing(getPos());
    }

    public static String formatExactCoord(double val) {
        if (val == (long) val) {
            return String.valueOf((long) val);
        }
        return String.valueOf(val);
    }

    public static MutableComponent formatColoredVec3Block(double x, double y, double z) {
        return Component.literal("  ")
                .append(Component.literal("X: ").withStyle(ChatFormatting.RED))
                .append(Component.literal(formatExactCoord(x) + ", \n  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Y: ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(formatExactCoord(y) + ", \n  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Z: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(formatExactCoord(z)).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = Component.literal(getType()).withStyle(getColor())
                .append(Component.literal(" \n@[\n").withStyle(ChatFormatting.GRAY))
                .append(formatColoredVec3Block(x, y, z))
                .append(Component.literal("\n]").withStyle(ChatFormatting.GRAY));

        if (dimension != null && !dimension.isEmpty()) {
            text.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", dimension).withStyle(ChatFormatting.GOLD));
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

    public static String formatCoord(double val) {
        if (val == (long) val) {
            return String.valueOf((long) val);
        }
        String s = String.format(Locale.ROOT, "%.2f", val);
        if (s.endsWith(".00")) return s.substring(0, s.length() - 3);
        if (s.endsWith("0")) return s.substring(0, s.length() - 1);
        return s;
    }

    public MutableComponent appendPosText(MutableComponent mutableComponent){
        String posStr = " @[" + formatCoord(getX()) + "," + formatCoord(getY()) + "," + formatCoord(getZ()) + "]";
        String tpCmd = (dimension != null && !dimension.isEmpty())
            ? "/execute in " + dimension + " run tp @p " + formatCoord(getX()) + " " + formatCoord(getY()) + " " + formatCoord(getZ())
            : "/tp @p " + formatCoord(getX()) + " " + formatCoord(getY()) + " " + formatCoord(getZ());
        return mutableComponent.append(Component.literal(posStr)
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
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
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

    public static Vec3PosEvent readNBT(CompoundTag tag) {
        Vec3PosEvent event = new Vec3PosEvent(
            tag.getLong("tick"),
            MTRNbt.getString(tag, "type", "unknown"),
            new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
            tag.getString("dimension")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

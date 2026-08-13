package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PistonStructureEvent extends BlockPosEvent {

    public static final String TYPE = "pistonStructure";

    private final int directionId;
    private final boolean extending;
    private final boolean resolved;
    private final long[] toPush;
    private final long[] toDestroy;
    // Null when the failure reason is that the structure was too big.
    private final @Nullable BlockPos blockingPos;
    private final String blockingBlock;

    public PistonStructureEvent(long tick, BlockPos pistonPos, Direction pushDirection, boolean extending,
                                boolean resolved, List<BlockPos> toPush, List<BlockPos> toDestroy,
                                @Nullable BlockPos blockingPos, @Nullable String blockingBlock, String dimension) {
        super(tick, TYPE, pistonPos, dimension);
        this.directionId = pushDirection.get3DDataValue();
        this.extending = extending;
        this.resolved = resolved;
        this.toPush = packPositions(toPush);
        this.toDestroy = packPositions(toDestroy);
        this.blockingPos = blockingPos;
        this.blockingBlock = blockingBlock != null ? blockingBlock : "";
    }

    private static long[] packPositions(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return new long[0];
        }
        long[] packed = new long[positions.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = positions.get(i).asLong();
        }
        return packed;
    }

    public Direction getPushDirection() {
        return Direction.from3DDataValue(directionId);
    }

    public boolean isExtending() {
        return extending;
    }

    public boolean isResolved() {
        return resolved;
    }

    public int getPushCount() {
        return toPush.length;
    }

    public int getDestroyCount() {
        return toDestroy.length;
    }

    public @Nullable BlockPos getBlockingPos() {
        return blockingPos;
    }

    @Override
    public ChatFormatting getColor() {
        return resolved ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return appendPosText(MTRComponent.translatable(
                "mtr.scoreboard.event.leaf.pistonstructure", "Piston Structure"));
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable(
                        resolved ? "mtr.tooltip.piston_structure_title_ok" : "mtr.tooltip.piston_structure_title_fail",
                        resolved ? "Piston Structure (movable)" : "Piston Structure (blocked)")
                .withStyle(getColor());

        text.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.piston_structure_origin", "Origin: [%d, %d, %d]",
                        getX(), getY(), getZ()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.facing", "Facing: %s",
                        getPushDirection().getName().toUpperCase()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.piston_structure_push", "Blocks to push: %d",
                        toPush.length).withStyle(ChatFormatting.WHITE));

        if (resolved) {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.piston_structure_destroy", "Blocks to destroy: %d",
                            toDestroy.length).withStyle(ChatFormatting.RED));
        } else if (blockingPos != null) {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.piston_structure_blocked_by",
                            "Blocked by: %s @ [%d, %d, %d]", blockingBlock,
                            blockingPos.getX(), blockingPos.getY(), blockingPos.getZ()).withStyle(ChatFormatting.RED));
        } else {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.piston_structure_blocked_invalid",
                            "Blocked: over limit or invalid push").withStyle(ChatFormatting.RED));
        }

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s",
                            getDimension()).withStyle(ChatFormatting.GOLD));
        }

        return text;
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        Direction dir = getPushDirection();
        ChatFormatting pushColor = resolved ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        BlockState pushGlass = DisplayUtils.getGlassState(pushColor);
        for (Run run : pushRuns(dir)) {
            spawnPillar(level, run.start(), run.length(), dir, pushGlass, pushColor);
        }

        BlockState redGlass = DisplayUtils.getGlassState(ChatFormatting.RED);
        if (resolved) {
            for (long packed : toDestroy) {
                MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(BlockPos.of(packed)),
                        redGlass, MARKER_SCALE, ChatFormatting.RED);
            }
        } else if (blockingPos != null) {
            MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(blockingPos),
                    redGlass, MARKER_SCALE, ChatFormatting.RED);
        }
    }

    private record Run(BlockPos start, int length) {}

    private List<Run> pushRuns(Direction dir) {
        List<Run> runs = new ArrayList<>();
        if (toPush.length == 0) {
            return runs;
        }
        Set<Long> occupied = new HashSet<>(toPush.length * 2);
        for (long packed : toPush) {
            occupied.add(packed);
        }
        Direction back = dir.getOpposite();
        for (long packed : toPush) {
            BlockPos pos = BlockPos.of(packed);
            if (occupied.contains(pos.relative(back).asLong())) {
                continue; // 不是段首，等段首那次迭代把它一起吃掉
            }
            int length = 1;
            BlockPos cursor = pos.relative(dir);
            while (occupied.contains(cursor.asLong())) {
                length++;
                cursor = cursor.relative(dir);
            }
            runs.add(new Run(pos, length));
        }
        return runs;
    }

    private static void spawnPillar(ServerLevel level, BlockPos start, int length, Direction dir,
                                    BlockState glass, ChatFormatting color) {
        float half = (length - 1) / 2.0f;
        Vec3 anchor = Vec3.atLowerCornerOf(start).add(
                dir.getStepX() * half, dir.getStepY() * half, dir.getStepZ() * half);

        float along = length + (MARKER_SCALE - 1.0f);
        Vector3f scale = switch (dir.getAxis()) {
            case X -> new Vector3f(along, MARKER_SCALE, MARKER_SCALE);
            case Y -> new Vector3f(MARKER_SCALE, along, MARKER_SCALE);
            case Z -> new Vector3f(MARKER_SCALE, MARKER_SCALE, along);
        };
        MTRMarker.spawnBlockDisplay(level, anchor, glass, scale, color);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("directionId", directionId);
        tag.putBoolean("extending", extending);
        tag.putBoolean("resolved", resolved);
        tag.putLongArray("toPush", toPush);
        tag.putLongArray("toDestroy", toDestroy);
        if (blockingPos != null) {
            tag.putLong("blockingPos", blockingPos.asLong());
            tag.putString("blockingBlock", blockingBlock);
        }
        return tag;
    }

    public static PistonStructureEvent readNBT(CompoundTag tag) {
        PistonStructureEvent event = new PistonStructureEvent(
                tag.getLong("tick").orElse(0L),
                new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0)),
                Direction.from3DDataValue(tag.getInt("directionId").orElse(0)),
                tag.getBoolean("extending").orElse(false),
                tag.getBoolean("resolved").orElse(false),
                unpackPositions(tag.getLongArray("toPush").orElse(new long[0])),
                unpackPositions(tag.getLongArray("toDestroy").orElse(new long[0])),
                tag.getLong("blockingPos").map(BlockPos::of).orElse(null),
                tag.getString("blockingBlock").orElse(""),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }

    private static List<BlockPos> unpackPositions(long[] packed) {
        List<BlockPos> positions = new ArrayList<>(packed.length);
        for (long value : packed) {
            positions.add(BlockPos.of(value));
        }
        return positions;
    }
}

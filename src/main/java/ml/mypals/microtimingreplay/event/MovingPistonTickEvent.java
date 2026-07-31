package ml.mypals.microtimingreplay.event;


import net.minecraft.core.registries.BuiltInRegistries;

import com.mojang.math.Transformation;
import ml.mypals.microtimingreplay.marker.PistonDisplayManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

public class MovingPistonTickEvent extends BlockPosEvent {

    public static final String TYPE = "movingPistonTick";
    private final float progress;
    private final int directionId;
    private final int movedBlockStateId;
    private final boolean extending;
    private final boolean isSourcePiston;

    public MovingPistonTickEvent(long tick, BlockPos pos, float progress, int movedBlockStateId,
            Direction direction, boolean extending, boolean isSourcePiston, String dimension) {
        super(tick, TYPE, pos, dimension);
        this.progress = progress;
        this.movedBlockStateId = movedBlockStateId;
        this.directionId = direction.get3DDataValue();
        this.extending = extending;
        this.isSourcePiston = isSourcePiston;
    }

    public Direction getDirection() {
        return Direction.from3DDataValue(directionId);
    }

    public boolean isExtending() {
        return extending;
    }

    public boolean isSourcePiston() {
        return isSourcePiston;
    }

    @Override
    public MutableComponent fillHoverText() {
        BlockState movedState = Block.stateById(movedBlockStateId);
        String blockKey = BuiltInRegistries.BLOCK.getKey(movedState.getBlock()).toString();

        Component opText = MTRComponent.translatable(extending ? "mtr.tooltip.piston.push" : "mtr.tooltip.piston.pull", extending ? "PUSH (Extending)" : "PULL (Retracting)");

        MutableComponent text = MTRComponent.translatable("mtr.tooltip.moving_piston_tick_title", "Moving Piston Tick @ [%d, %d, %d]", getX(), getY(), getZ())
                .append(Component.literal("\n")).withStyle(ChatFormatting.LIGHT_PURPLE);

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
                .append(MTRComponent.translatable("mtr.tooltip.progress", "Progress: %.1f%%", progress * 100.0f).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.operation", "Operation: %s", opText.getString()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.facing", "Facing: %s", getDirection().getName().toUpperCase()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.moved_block", "Moved Block: %s", blockKey).withStyle(ChatFormatting.AQUA));
    }

    /** Mirrors PistonMovingBlockEntity.getExtendedProgress() */
    private float getExtendedProgress(float p) {
        return extending ? (p - 1.0f) : (1.0f - p);
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.LIGHT_PURPLE;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return appendPosText(MTRComponent.translatable("mtr.scoreboard.event.leaf.pistontick",
                "Piston Tick")
        );
    }

    public void display(ServerLevel level) {
        BlockPos pos = getPos();
        List<UUID> uuids = PistonDisplayManager.getPistonDisplayUUIDs(pos);
        if (uuids == null || uuids.isEmpty())
            return;

        Direction direction = getDirection();
        // Direction movementDir = extending ? direction : direction.getOpposite();
        float extProg = getExtendedProgress(progress);

        float xOff = direction.getStepX() * extProg;
        float yOff = direction.getStepY() * extProg;
        float zOff = direction.getStepZ() * extProg;

        BlockState movedState = Block.stateById(movedBlockStateId);

        float clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);

        if (isSourcePiston && !extending) {
            // [0]=head at renderPos, [1]=base at basePos
            if (!uuids.isEmpty()) {
                // Update piston head SHORT property based on progress
                boolean shortHead = clampedProgress >= 0.5f;
                var headEntity = level.getEntity(uuids.getFirst());
                if (headEntity instanceof Display.BlockDisplay bd) {
                    PistonType ptype = movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
                    Direction facing = direction;
                    BlockState headState = Blocks.PISTON_HEAD.defaultBlockState()
                            .setValue(PistonHeadBlock.TYPE, ptype)
                            .setValue(PistonHeadBlock.FACING, facing)
                            .setValue(PistonHeadBlock.SHORT, shortHead);
                    bd.setBlockState(headState);
                    PistonDisplayManager.applyOffset(bd, xOff, yOff, zOff);
                }
            }
            if (uuids.size() >= 2) {
                // Base stays at its pos, no offset
                var baseEntity = level.getEntity(uuids.get(1));
                if (baseEntity instanceof Display.BlockDisplay bd) {
                    PistonDisplayManager.applyOffset(bd, 0, 0, 0);
                }
            }

        } else if (movedState.is(Blocks.PISTON_HEAD)) {
            // Moving piston head: update SHORT based on progress
            if (!uuids.isEmpty()) {
                var entity = level.getEntity(uuids.getFirst());
                if (entity instanceof Display.BlockDisplay bd) {
                    boolean shortHead = clampedProgress <= 0.5f;
                    BlockState headState = movedState.setValue(PistonHeadBlock.SHORT, shortHead);
                    bd.setBlockState(headState);
                    PistonDisplayManager.applyOffset(bd, xOff, yOff, zOff);
                }
            }
        } else {
            // Regular block: just update offset
            if (!uuids.isEmpty()) {
                var entity = level.getEntity(uuids.getFirst());
                if (entity instanceof Display.BlockDisplay bd) {
                    PistonDisplayManager.applyOffset(bd, xOff, yOff, zOff);
                }
            }
        }
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putFloat("progress", progress);
        tag.putInt("directionId", directionId);
        tag.putInt("stateId", movedBlockStateId);
        tag.putBoolean("extending", extending);
        tag.putBoolean("isSourcePiston", isSourcePiston);
        return tag;
    }

    public static MovingPistonTickEvent readNBT(CompoundTag tag) {
        MovingPistonTickEvent event = new MovingPistonTickEvent(
                tag.getLong("tick").orElse(0L),
                new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0)),
                tag.getFloat("progress").orElse(0.0f),
                tag.contains("stateId") ? tag.getInt("stateId").orElse(0) : tag.getInt("movedBlockStateId").orElse(0),
                Direction.from3DDataValue(tag.getInt("directionId").orElse(0)),
                tag.getBoolean("extending").orElse(false),
                tag.getBoolean("isSourcePiston").orElse(false),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

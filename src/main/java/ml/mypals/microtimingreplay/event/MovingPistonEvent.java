package ml.mypals.microtimingreplay.event;


import net.minecraft.core.registries.BuiltInRegistries;

import ml.mypals.microtimingreplay.marker.PistonDisplayManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3f;

public class MovingPistonEvent extends BlockPosEvent {

    public static final String TYPE = "movingPiston";
    private final int movedBlockStateId;
    private final int directionId;
    private final boolean extending;
    private final boolean isSourcePiston;
    private final boolean despawn;

    public MovingPistonEvent(long tick, BlockPos pos, int movedBlockStateId, Direction direction,
                              boolean extending, boolean isSourcePiston, boolean despawn, String dimension) {
        super(tick, TYPE, pos, dimension);
        this.movedBlockStateId = movedBlockStateId;
        this.directionId = direction.get3DDataValue();
        this.extending = extending;
        this.isSourcePiston = isSourcePiston;
        this.despawn = despawn;
    }


    public Direction getDirection() { return Direction.from3DDataValue(directionId); }
    public boolean isExtending() { return extending; }
    public boolean isSourcePiston() { return isSourcePiston; }
    public boolean isDespawn() { return despawn; }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.LIGHT_PURPLE;
    }

    @Override
    public MutableComponent fillHoverText() {
        BlockState movedState = Block.stateById(movedBlockStateId);
        String blockKey = BuiltInRegistries.BLOCK.getKey(movedState.getBlock()).toString();

        Component spawnText = MTRComponent.translatable(despawn ? "mtr.tooltip.piston.despawn" : "mtr.tooltip.piston.spawn", despawn ? "[Despawn]" : "[Spawn]");
        Component opText = MTRComponent.translatable(extending ? "mtr.tooltip.piston.push" : "mtr.tooltip.piston.pull", extending ? "PUSH (Extending)" : "PULL (Retracting)");
        Component roleText = MTRComponent.translatable(isSourcePiston ? "mtr.tooltip.piston.role_base" : "mtr.tooltip.piston.role_block", isSourcePiston ? "Piston Head / Base" : "Moved Structure Block");

        MutableComponent text = MTRComponent.translatable("mtr.tooltip.moving_piston_title", "Moving Piston %s @ [%d, %d, %d]", spawnText.getString(), getX(), getY(), getZ())
                .append(Component.literal("\n")).withStyle(ChatFormatting.LIGHT_PURPLE);

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
                .append(MTRComponent.translatable("mtr.tooltip.operation", "Operation: %s", opText.getString()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.facing", "Facing: %s", getDirection().getName().toUpperCase()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.moved_block", "Moved Block: %s", blockKey).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.role", "Role: %s", roleText.getString()).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public MutableComponent getScoreboardText() {
        String key = despawn ? "mtr.scoreboard.event.leaf.pistonremove" : "mtr.scoreboard.event.leaf.pistonspawn";
        String fallback = despawn ? "Piston Remove" : "Piston Spawn";
        return appendPosText(MTRComponent.translatable(key, fallback));
    }

    public void display(ServerLevel level, Vector3f scale) {
        // PistonDisplayManager draws these as real block displays.
        BlockPos pos = getPos();
        if (isDespawn()) {
            PistonDisplayManager.removePistonDisplays(pos, level);
        } else {
            spawnPistonDisplays(level, pos);
        }
    }

    private void spawnPistonDisplays(ServerLevel level, BlockPos pos) {
        BlockState movedState = Block.stateById(movedBlockStateId);
        Direction facing = getDirection();

        float extProg = extending ? (0.0f - 1.0f) : (1.0f - 0.0f);
        float xOff = facing.getStepX() * extProg;
        float yOff = facing.getStepY() * extProg;
        float zOff = facing.getStepZ() * extProg;

        List<UUID> oldUuids = PistonDisplayManager.claimOldPistonDisplay(pos);
        if (oldUuids != null && !oldUuids.isEmpty()) {
            PistonDisplayManager.registerPistonDisplays(pos, oldUuids);
            return;
        }

        List<UUID> uuids = new ArrayList<>();

        if (isSourcePiston && !extending) {
            PistonType pistonType = movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;

            BlockState pistonHeadState = Blocks.PISTON_HEAD.defaultBlockState()
                    .setValue(PistonHeadBlock.TYPE, pistonType)
                    .setValue(PistonHeadBlock.FACING, facing)
                    .setValue(PistonHeadBlock.SHORT, false);
            UUID headUuid = PistonDisplayManager.spawnStaticBlockDisplay(level, pos, pistonHeadState, xOff, yOff, zOff);
            uuids.add(headUuid);

            BlockState baseState = movedState.hasProperty(PistonBaseBlock.EXTENDED) ?
                    movedState.setValue(PistonBaseBlock.EXTENDED, true) :
                    (pistonType == PistonType.STICKY ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState()
                            .setValue(PistonBaseBlock.FACING, facing)
                            .setValue(PistonBaseBlock.EXTENDED, true);
            UUID baseUuid = PistonDisplayManager.spawnStaticBlockDisplay(level, pos, baseState, 0, 0, 0);
            uuids.add(baseUuid);

        } else if (movedState.is(Blocks.PISTON_HEAD)) {
            BlockState headState = movedState.setValue(PistonHeadBlock.SHORT, false);
            UUID headUuid = PistonDisplayManager.spawnStaticBlockDisplay(level, pos, headState, xOff, yOff, zOff);
            uuids.add(headUuid);

        } else {
            UUID uuid = PistonDisplayManager.spawnStaticBlockDisplay(level, pos, movedState, xOff, yOff, zOff);
            uuids.add(uuid);
        }

        PistonDisplayManager.registerPistonDisplays(pos, uuids);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("stateId", movedBlockStateId);
        tag.putInt("directionId", directionId);
        tag.putBoolean("extending", extending);
        tag.putBoolean("isSourcePiston", isSourcePiston);
        tag.putBoolean("despawn", isDespawn());
        return tag;
    }

    public static MovingPistonEvent readNBT(CompoundTag tag) {
        MovingPistonEvent event = new MovingPistonEvent(
                tag.getLong("tick").orElse(0L),
                new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0)),
                tag.contains("stateId") ? tag.getInt("stateId").orElse(0) : tag.getInt("movedBlockStateId").orElse(0),
                Direction.from3DDataValue(tag.getInt("directionId").orElse(0)),
                tag.getBoolean("extending").orElse(false),
                tag.getBoolean("isSourcePiston").orElse(false),
                tag.getBoolean("despawn").orElse(false),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

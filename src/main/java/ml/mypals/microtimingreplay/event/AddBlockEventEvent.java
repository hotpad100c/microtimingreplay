package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRNbt;

import ml.mypals.microtimingreplay.util.MTRComponent;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.piston.PistonBaseBlock;


import ml.mypals.microtimingreplay.marker.MTRMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class AddBlockEventEvent extends BlockPosEvent {

    public static final String TYPE = "addBlockEvent";

    private final int blockStateId;
    private final int b0, b1;
    private final boolean shouldFail;

    public AddBlockEventEvent(long tick, int x, int y, int z, int blockStateId, int b0, int b1,
                              String dimension, boolean shouldFail) {
        super(tick, TYPE, new BlockPos(x, y, z), dimension);
        this.blockStateId = blockStateId;
        this.b0 = b0;
        this.b1 = b1;
        this.shouldFail = shouldFail;
    }

    public int getBlockStateId() { return blockStateId; }
    public int getB0() { return b0; }
    public int getB1() { return b1; }
   public boolean shouldFail() { return shouldFail; }


    @Override
    public String filterId() {
        return "add_block_event";
    }

    @Override
    public ChatFormatting getColor() {
        return shouldFail ? ChatFormatting.GRAY : ChatFormatting.YELLOW;
    }

    @Override
    public MutableComponent fillHoverText() {
        BlockState state = Block.stateById(blockStateId);
        Block block = state.getBlock();
        String blockKey = BuiltInRegistries.BLOCK.getKey(block).toString();

        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.block_event_title",
                "Execute Block Event @ [%d, %d, %d]",
                getX(), getY(), getZ()
        ).append(Component.literal("\n")).withStyle(ChatFormatting.YELLOW);

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        text.append(MTRComponent.translatable("mtr.tooltip.target", "Target: %s", blockKey).withStyle(ChatFormatting.AQUA))
        .append(Component.literal("\n"));

        if (block instanceof PistonBaseBlock) {
            String eventTypeName = switch (b0) {
                case 0 -> "EXTEND (0)";
                case 1 -> "RETRACT (1)";
                case 2 -> "RETRACT_DROP (2)";
                default -> "UNKNOWN (" + b0 + ")";
            };
            Direction dir = Direction.from3DDataValue(b1);

            text.append(MTRComponent.translatable("mtr.tooltip.piston_action", "Piston Action: %s", eventTypeName).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.direction", "Direction: %s", dir.getName().toUpperCase() + " (" + b1 + ")").withStyle(ChatFormatting.GOLD));
        } else {
            text.append(MTRComponent.translatable("mtr.tooltip.event_id", "Event ID (b0): %d", b0).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.event_param", "Event Param (b1): %d", b1).withStyle(ChatFormatting.WHITE));
        }

        return shouldFail ? text.append(Component.literal(" \n ✖").withStyle(ChatFormatting.RED)) : text;
    }

    public static AddBlockEventEvent readNBT(CompoundTag tag) {
        int stateId;
        if (tag.contains("blockName")) {
            Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(MTRNbt.getString(tag, "blockName", "minecraft:air"))).orElse(Blocks.AIR);
            stateId = Block.getId(block.defaultBlockState());
        } else {
            stateId = tag.getInt("blockStateId");
        }

        AddBlockEventEvent event = new AddBlockEventEvent(
            tag.getLong("tick"),
            tag.getInt("x"), tag.getInt("y"), tag.getInt("z"),
            stateId,
            tag.getInt("b0"), tag.getInt("b1"),
            tag.getString("dimension"),
            tag.getBoolean("shouldFail")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("blockStateId", blockStateId);
        tag.putInt("b0", b0);
        tag.putInt("b1", b1);
        tag.putBoolean("shouldFail", shouldFail);
        return tag;
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        super.display(level, scale);
        BlockPos pos = getPos();
        
        BlockState state = Block.stateById(blockStateId);
        String translationKey = state.getBlock().getDescriptionId();
        
        Component text = Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("\n"))
                .append(Component.literal( " Dir: " + b0 ))
                .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal( " Dat: " + b1));
        MTRMarker.spawnTextDisplay(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, text, 0.7f);
    }
}

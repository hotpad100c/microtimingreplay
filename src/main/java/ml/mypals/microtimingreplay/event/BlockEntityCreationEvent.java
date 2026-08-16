package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class BlockEntityCreationEvent extends BlockPosEvent {
    public static final String TYPE = "blockEntityCreation";

    private final String blockEntityType;

    public BlockEntityCreationEvent(long tick, BlockPos pos, String blockEntityType, String dimension) {
        super(tick, TYPE, pos, dimension);
        this.blockEntityType = blockEntityType != null ? blockEntityType : "unknown";
    }

    public String getBlockEntityType() {
        return blockEntityType;
    }


    @Override
    public String filterId() {
        return "block_entity_creation";
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.AQUA;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return appendPosText(MTRComponent.translatable(
                "mtr.scoreboard.event.leaf.blockentitycreation",
                "[Block Entity Creation] " + blockEntityType
        ));
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.block_entity_creation_title",
                "BlockEntity Creation @ [%d, %d, %d]",
                getX(), getY(), getZ()
        ).append(Component.literal("\n")).withStyle(getColor());

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text.append(MTRComponent.translatable("mtr.tooltip.target", "Type: %s", blockEntityType).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        if (level == null || getDimension() == null) return;
        MTRProfile profile = ml.mypals.microtimingreplay.MTRState.getActiveProfile();
        if (profile != null && profile.outsideArea(getPos(), getDimension())) return;

        MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(getPos()),
                Blocks.CYAN_STAINED_GLASS.defaultBlockState(), scale, ChatFormatting.AQUA);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("blockEntityType", blockEntityType != null ? blockEntityType : "");
        return tag;
    }

    public static BlockEntityCreationEvent readNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(
                tag.getInt("x"),
                tag.getInt("y"),
                tag.getInt("z")
        );
        BlockEntityCreationEvent event = new BlockEntityCreationEvent(
                tag.getLong("tick"),
                pos,
                tag.getString("blockEntityType"),
                tag.getString("dimension")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

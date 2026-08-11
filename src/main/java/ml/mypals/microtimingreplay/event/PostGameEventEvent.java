package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.util.MTRComponent;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class PostGameEventEvent extends Vec3PosEvent {

    public static final String TYPE = "postGameEvent";
    
    private final int blockState;//-1 = null
    private final String entityUUID; //Empty = null


    public PostGameEventEvent(long tick, double x, double y, double z, int blockState, String entityUUID, String dimension) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.blockState = blockState;
        this.entityUUID = entityUUID;
    }

    public BlockState getBlock() { return blockState == -1? null : Block.stateById(blockState); }
    public String getEntityUUid() { return entityUUID.isEmpty() ? null: entityUUID; }


    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.DARK_AQUA;
    }

    public static PostGameEventEvent readNBT(CompoundTag tag) {
        PostGameEventEvent event = new PostGameEventEvent(
            tag.getLong("tick").orElse(0L),
            tag.getDouble("x").orElse(0d),
            tag.getDouble("y").orElse(0d),
            tag.getDouble("z").orElse(0d),
            tag.getInt("blockState").orElse(-1),
            tag.getString("entityUUID").orElse(""),
            tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("blockState", blockState);
        tag.putString("entityUUID", entityUUID != null ? entityUUID : "");
        return tag;
    }

    @Override
    public void display(ServerLevel level, Vector3f engineScale) {
        Vec3 pos = getPos();
        Vector3f scale = new Vector3f(0.3f, 0.3f, 0.3f);
        Vec3 boxOrigin = pos.subtract(scale.x() / 2.0, scale.y() / 2.0, scale.z() / 2.0);
        MTRMarker.spawnBlockDisplay(level, boxOrigin, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), scale, ChatFormatting.DARK_AQUA);

        MutableComponent mutableComponent = Component.literal("PostGameEvent").withStyle(ChatFormatting.DARK_AQUA);
        BlockState state = getBlock();
        String uuid = getEntityUUid();
        if(state != null){
            mutableComponent.append("\n").append(DisplayUtils.getNamesFormatState(state));
        }
        if(uuid != null){
            mutableComponent.append("\nEntity: ").append(Component.literal(uuid).withStyle(ChatFormatting.YELLOW));
        }

        MTRMarker.spawnTextDisplay(level, pos.x(), pos.y() + 0.8, pos.z(), mutableComponent, 0.7f);
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = Component.literal("PostGameEvent").withStyle(getColor())
                .append(Component.literal(" \n@[\n").withStyle(ChatFormatting.GRAY))
                .append(formatColoredVec3Block(getX(), getY(), getZ()))
                .append(Component.literal("\n]").withStyle(ChatFormatting.GRAY));

        BlockState state = getBlock();
        String uuid = getEntityUUid();
        if (state != null) {
            text.append(Component.literal("\nBlockState: ").withStyle(ChatFormatting.GRAY))
                .append(DisplayUtils.getNamesFormatState(state));
        }
        if (uuid != null) {
            text.append(Component.literal("\nEntity: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(uuid).withStyle(ChatFormatting.YELLOW));
        }

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD));
        }
        return text;
    }
}

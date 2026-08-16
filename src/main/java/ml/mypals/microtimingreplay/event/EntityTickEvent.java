package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class EntityTickEvent extends Vec3PosEvent {
    public static final String TYPE = "entityTick";

    private final String entityUuid;
    private final String entityType;

    public EntityTickEvent(long tick, String entityUuid, String entityType, double x, double y, double z, String dimension) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.entityUuid = entityUuid;
        this.entityType = entityType != null ? entityType : "unknown";
    }

    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }

    @Override
    public boolean isQueueScope() {
        return true;
    }

    @Override
    public String filterId() {
        return "entity_tick";
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.GOLD;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return appendPosText(MTRComponent.translatable("mtr.scoreboard.event.leaf.entitytick", "[Entity Tick] " + entityType));
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.entity_tick_title",
                "Entity Tick @ [%.2f, %.2f, %.2f]",
                getX(), getY(), getZ()
        ).append(Component.literal("\n")).withStyle(getColor());

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
        .append(MTRComponent.translatable("mtr.tooltip.target", "Type: %s", entityType).withStyle(ChatFormatting.AQUA))
        .append(Component.literal("\nUUID: ").withStyle(ChatFormatting.GRAY))
        .append(Component.literal(entityUuid != null ? entityUuid : "null").withStyle(ChatFormatting.YELLOW));
    }


    @Override
    public void display(ServerLevel level, Vector3f scale) {
        // Entity events are shown by making the replay entity glow,
        // do nothing here!
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("entityUuid", entityUuid != null ? entityUuid : "");
        tag.putString("entityType", entityType != null ? entityType : "");
        return tag;
    }

    public static EntityTickEvent readNBT(CompoundTag tag) {
        EntityTickEvent event = new EntityTickEvent(
                tag.getLong("tick"),
                tag.getString("entityUuid"),
                tag.getString("entityType"),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getString("dimension")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

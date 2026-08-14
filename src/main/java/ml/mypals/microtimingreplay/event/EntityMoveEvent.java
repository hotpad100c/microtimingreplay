package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import org.joml.Vector3f;

public class EntityMoveEvent extends Vec3PosEvent {
    public static final String TYPE = "entityMove";

    private final String entityUuid;
    private final String entityType;
    private final double oldX, oldY, oldZ;
    private final float yaw;
    private final float pitch;
    private final double dx, dy, dz;

    public EntityMoveEvent(long tick, String entityUuid, String entityType,
                           double oldX, double oldY, double oldZ,
                           double newX, double newY, double newZ,
                           float yaw, float pitch,
                           double dx, double dy, double dz, String dimension) {
        this(tick, TYPE, entityUuid, entityType, oldX, oldY, oldZ, newX, newY, newZ, yaw, pitch, dx, dy, dz, dimension);
    }

    protected EntityMoveEvent(long tick, String type, String entityUuid, String entityType,
                              double oldX, double oldY, double oldZ,
                              double newX, double newY, double newZ,
                              float yaw, float pitch,
                              double dx, double dy, double dz, String dimension) {
        super(tick, type, new Vec3(newX, newY, newZ), dimension);
        this.entityUuid = entityUuid;
        this.entityType = entityType != null ? entityType : "unknown";
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldZ = oldZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public double getOldX() { return oldX; }
    public double getOldY() { return oldY; }
    public double getOldZ() { return oldZ; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public Vec3 getVelocity() { return new Vec3(dx, dy, dz); }


    @Override
    public String filterId() {
        return "entity_move";
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.GOLD;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return appendPosText(MTRComponent.translatable("mtr.scoreboard.event.leaf.entitymove", "[Entity Move] " + entityType));
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable("mtr.tooltip.entity_move_title", "Entity Move [%s]", entityType)
                .withStyle(getColor())
                .append(Component.literal("\n"));

        text.append(MTRComponent.translatable("mtr.tooltip.entity.from", "From @[\n")).withStyle(ChatFormatting.GRAY)
            .append(formatColoredVec3Block(oldX, oldY, oldZ))
            .append(Component.literal("\n]\n").withStyle(ChatFormatting.GRAY));

        text.append(MTRComponent.translatable("mtr.tooltip.entity.to", "To @[\n")).withStyle(ChatFormatting.GRAY)
            .append(formatColoredVec3Block(getX(), getY(), getZ()))
            .append(Component.literal("\n]\n").withStyle(ChatFormatting.GRAY));

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
        .append(MTRComponent.translatable("mtr.tooltip.target", "Type: %s", entityType).withStyle(ChatFormatting.AQUA))
        .append(Component.literal("\nUUID: ").withStyle(ChatFormatting.GRAY))
        .append(Component.literal(entityUuid != null ? entityUuid : "null").withStyle(ChatFormatting.YELLOW))
        .append(Component.literal(String.format(java.util.Locale.ROOT, "\nVelocity: (%s, %s, %s)", formatExactCoord(dx), formatExactCoord(dy), formatExactCoord(dz))).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public void applySelf(ServerLevel level, boolean forward) {
        if (entityUuid == null || entityUuid.isEmpty()) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(entityUuid);
        } catch (IllegalArgumentException e) {
            return;
        }

        Entity entity = EntityReplayManager.getEntity(level, uuid);
        if (entity != null) {
            if (forward) {
                entity.absSnapTo(getX(), getY(), getZ(), yaw, pitch);
            } else {
                entity.absSnapTo(oldX, oldY, oldZ, yaw, pitch);
            }
            entity.setDeltaMovement(0,0,0);
            EntityReplayManager.syncEntityPosition(level, entity);
        }
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
        tag.putDouble("oldX", oldX);
        tag.putDouble("oldY", oldY);
        tag.putDouble("oldZ", oldZ);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putDouble("dx", dx);
        tag.putDouble("dy", dy);
        tag.putDouble("dz", dz);
        return tag;
    }

    public static EntityMoveEvent readNBT(CompoundTag tag) {
        EntityMoveEvent event = new EntityMoveEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("entityUuid").orElse(""),
                tag.getString("entityType").orElse(""),
                tag.getDouble("oldX").orElse(0.0),
                tag.getDouble("oldY").orElse(0.0),
                tag.getDouble("oldZ").orElse(0.0),
                tag.getDouble("x").orElse(0.0),
                tag.getDouble("y").orElse(0.0),
                tag.getDouble("z").orElse(0.0),
                tag.getFloat("yaw").orElse(0.0f),
                tag.getFloat("pitch").orElse(0.0f),
                tag.getDouble("dx").orElse(0.0),
                tag.getDouble("dy").orElse(0.0),
                tag.getDouble("dz").orElse(0.0),
                tag.getString("dimension").orElse("unknown")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

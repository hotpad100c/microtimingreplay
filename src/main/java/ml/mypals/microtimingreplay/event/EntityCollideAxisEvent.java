package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

public class EntityCollideAxisEvent extends Vec3PosEvent {
    public static final String TYPE = "entityCollideAxis";

    private final String entityUuid;
    private final String entityType;
    private final String axis;
    private final double oldX, oldY, oldZ;
    private final float yaw;
    private final float pitch;
    private final double attemptedDistance;
    private final double collisionDistance;

    public EntityCollideAxisEvent(long tick, String entityUuid, String entityType,
                                  String axis,
                                  double oldX, double oldY, double oldZ,
                                  double newX, double newY, double newZ,
                                  float yaw, float pitch,
                                  double attemptedDistance, double collisionDistance,
                                  String dimension) {
        super(tick, TYPE, new Vec3(newX, newY, newZ), dimension);
        this.entityUuid = entityUuid != null ? entityUuid : "";
        this.entityType = entityType != null ? entityType : "unknown";
        this.axis = axis != null ? axis : "X";
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldZ = oldZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.attemptedDistance = attemptedDistance;
        this.collisionDistance = collisionDistance;
    }

    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public String getAxis() { return axis; }
    public double getOldX() { return oldX; }
    public double getOldY() { return oldY; }
    public double getOldZ() { return oldZ; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public double getAttemptedDistance() { return attemptedDistance; }
    public double getCollisionDistance() { return collisionDistance; }

    @Override
    public ChatFormatting getColor() {
        return switch (axis.toUpperCase()) {
            case "X" -> ChatFormatting.RED;
            case "Y" -> ChatFormatting.GREEN;
            case "Z" -> ChatFormatting.BLUE;
            default -> ChatFormatting.YELLOW;
        };
    }

    @Override
    public MutableComponent getScoreboardText() {
        String text = String.format("[%s-Axis] (%.2f -> %.2f)", axis, getOldX(), getX());
        return Component.literal(text);
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable("mtr.tooltip.entity_collide_axis_title", "Move Axis [%s-Axis]", axis)
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
            .append(Component.literal(entityUuid).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.format(java.util.Locale.ROOT, "\nAttempted: %s | Resolved: %s", formatExactCoord(attemptedDistance), formatExactCoord(collisionDistance))).withStyle(ChatFormatting.WHITE));
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
            entity.setDeltaMovement(0, 0, 0);
            EntityReplayManager.syncEntityPosition(level, entity);
        }
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        display(level);
    }

    @Override
    public void display(ServerLevel level) {
        if (level == null || getDimension() == null) return;
        Vec3 start = new Vec3(oldX, oldY, oldZ);
        Vec3 end = getPos();

        BlockState glassState = DisplayUtils.getGlassState(getColor());
        DisplayUtils.spawnLineDisplay(level, start, end, glassState, 0.25f, getColor());

        Vec3 mid = start.add(end).scale(0.5);
        String labelText = String.format("[%s-Axis] %.2f -> %.2f", axis, attemptedDistance, collisionDistance);
        MTRMarker.spawnTextDisplay(level, mid.add(0, 0.4, 0), Component.literal(labelText).withStyle(getColor()), 0.7f);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("entityUuid", entityUuid != null ? entityUuid : "");
        tag.putString("entityType", entityType != null ? entityType : "");
        tag.putString("axis", axis != null ? axis : "X");
        tag.putDouble("oldX", oldX);
        tag.putDouble("oldY", oldY);
        tag.putDouble("oldZ", oldZ);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putDouble("attemptedDistance", attemptedDistance);
        tag.putDouble("collisionDistance", collisionDistance);
        return tag;
    }

    public static EntityCollideAxisEvent readNBT(CompoundTag tag) {
        EntityCollideAxisEvent event = new EntityCollideAxisEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("entityUuid").orElse(""),
                tag.getString("entityType").orElse(""),
                tag.getString("axis").orElse("X"),
                tag.getDouble("oldX").orElse(0.0),
                tag.getDouble("oldY").orElse(0.0),
                tag.getDouble("oldZ").orElse(0.0),
                tag.getDouble("x").orElse(0.0),
                tag.getDouble("y").orElse(0.0),
                tag.getDouble("z").orElse(0.0),
                tag.getFloat("yaw").orElse(0.0f),
                tag.getFloat("pitch").orElse(0.0f),
                tag.getDouble("attemptedDistance").orElse(0.0),
                tag.getDouble("collisionDistance").orElse(0.0),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

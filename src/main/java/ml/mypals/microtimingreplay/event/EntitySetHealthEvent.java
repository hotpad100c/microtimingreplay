package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.UUID;

public class EntitySetHealthEvent extends Vec3PosEvent {
    public static final String TYPE = "entitySetHealth";

    private final String entityUuid;
    private final String entityType;
    private final float oldHealth;
    private final float newHealth;
    private final float maxHealth;

    public EntitySetHealthEvent(long tick, String entityUuid, String entityType,
                                float oldHealth, float newHealth, float maxHealth,
                                double x, double y, double z,
                                String dimension) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.entityUuid = entityUuid != null ? entityUuid : "";
        this.entityType = entityType != null ? entityType : "unknown";
        this.oldHealth = oldHealth;
        this.newHealth = newHealth;
        this.maxHealth = maxHealth;
    }

    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public float getOldHealth() { return oldHealth; }
    public float getNewHealth() { return newHealth; }
    public float getMaxHealth() { return maxHealth; }

    @Override
    public ChatFormatting getColor() {
        return newHealth < oldHealth ? ChatFormatting.RED : ChatFormatting.GREEN;
    }

    @Override
    public MutableComponent getScoreboardText() {
        // Same as the collide-axis event: %.1f never worked through the translation
        // formatter, so round here and pass strings.
        return MTRComponent.translatable(
                "mtr.scoreboard.event.leaf." + TYPE.toLowerCase(Locale.ROOT),
                "Set Health %s (%s -> %s)",
                entityType, format1(oldHealth), format1(newHealth));
    }

    private static String format1(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable("mtr.tooltip.entity_set_health_title", "Set Health [%s]", entityType)
                .withStyle(getColor())
                .append(Component.literal(" \n@[\n").withStyle(ChatFormatting.GRAY))
                .append(formatColoredVec3Block(getX(), getY(), getZ()))
                .append(Component.literal("\n]").withStyle(ChatFormatting.GRAY));

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD));
        }

        return text
            .append(Component.literal("\n"))
            .append(MTRComponent.translatable("mtr.tooltip.target", "Type: %s", entityType).withStyle(ChatFormatting.AQUA))
            .append(Component.literal("\nUUID: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(entityUuid).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.format(Locale.ROOT, "\nHealth: %s -> %s (Max: %s)",
                    formatExactCoord(oldHealth), formatExactCoord(newHealth), formatExactCoord(maxHealth))).withStyle(ChatFormatting.WHITE));
    }

    @Override
    public void applySelf(ServerLevel level, boolean forward) {
        if (entityUuid == null || entityUuid.isEmpty()) return;
        try {
            UUID uuid = UUID.fromString(entityUuid);
            Entity entity = EntityReplayManager.getEntity(level, uuid);
            if (entity != null) {
                entity.setGlowingTag(true);
            }

        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        display(level);
    }

    @Override
    public void display(ServerLevel level) {
        if (level == null || getDimension() == null) return;
        Vec3 pos = getPos();

        float bbHeight = 1.8f;
        if (entityUuid != null && !entityUuid.isEmpty()) {
            try {
                UUID uuid = UUID.fromString(entityUuid);
                Entity entity = EntityReplayManager.getEntity(level, uuid);
                if (entity != null) {
                    entity.setGlowingTag(true);
                    bbHeight = entity.getBbHeight();
                }
            } catch (IllegalArgumentException ignored) {}
        }

        String labelText = String.format(Locale.ROOT, "[HP] %.1f -> %.1f", oldHealth, newHealth);
        MTRMarker.spawnTextDisplay(level, pos.x(), pos.y() + bbHeight + 0.4, pos.z(),
                Component.literal(labelText).withStyle(getColor()), 0.8f);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("entityUuid", entityUuid != null ? entityUuid : "");
        tag.putString("entityType", entityType != null ? entityType : "");
        tag.putFloat("oldHealth", oldHealth);
        tag.putFloat("newHealth", newHealth);
        tag.putFloat("maxHealth", maxHealth);
        return tag;
    }

    public static EntitySetHealthEvent readNBT(CompoundTag tag) {
        EntitySetHealthEvent event = new EntitySetHealthEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("entityUuid").orElse(""),
                tag.getString("entityType").orElse(""),
                tag.getFloat("oldHealth").orElse(0.0f),
                tag.getFloat("newHealth").orElse(0.0f),
                tag.getFloat("maxHealth").orElse(0.0f),
                tag.getDouble("x").orElse(0.0),
                tag.getDouble("y").orElse(0.0),
                tag.getDouble("z").orElse(0.0),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.util.MTRNbt;


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

public class EntitySpawnEvent extends Vec3PosEvent {
    public static final String TYPE = "entitySpawn";

    private final String entityUuid;
    private final String entityType;
    private final int EID;
    private final CompoundTag nbt;
    private final float yaw;
    private final float pitch;
    private final boolean despawn;


    public EntitySpawnEvent(long tick, String entityUuid, String entityType, CompoundTag nbt,
                            double x, double y, double z, float yaw, float pitch, boolean despawn,
                            String dimension, int eid) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.entityUuid = entityUuid;
        this.entityType = entityType != null ? entityType : "unknown";
        this.nbt = nbt != null ? nbt : new CompoundTag();
        this.yaw = yaw;
        this.pitch = pitch;
        this.despawn = despawn;
        this.EID = eid;
    }



    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public CompoundTag getNbt() { return nbt; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isDespawn() { return despawn; }


    @Override
    public String filterId() {
        return isDespawn() ? "entity_despawn" : "entity_spawn";
    }

    @Override
    public ChatFormatting getColor() {
        return despawn ? ChatFormatting.DARK_RED : ChatFormatting.GREEN;
    }

    @Override
    public MutableComponent getScoreboardText() {
        String key = despawn ? "mtr.scoreboard.event.leaf.entitydespawn" : "mtr.scoreboard.event.leaf.entityspawn";
        String fallback = despawn ? "[Entity Despawn] " + entityType : "[Entity Spawn] " + entityType;
        return appendPosText(MTRComponent.translatable(key, fallback));
    }

    @Override
    public MutableComponent fillHoverText() {
        String spawnLabel = despawn ? "[Despawn / Leave]" : "[Spawn / Enter]";

        MutableComponent text = Component.literal("Entity " + spawnLabel + " [" + entityType + "]").withStyle(getColor())
                .append(Component.literal(" \n@[\n").withStyle(ChatFormatting.GRAY))
                .append(formatColoredVec3Block(getX(), getY(), getZ()))
                .append(Component.literal("\n]").withStyle(ChatFormatting.GRAY));

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD));
        }

        return text
            .append(Component.literal("\nUUID: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(entityUuid != null ? entityUuid : "null").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\nEID: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(EID)).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public void applySelf(ServerLevel level, boolean forward) {
        if (entityUuid == null || entityUuid.isEmpty()
                || !level.dimension().location().toString().equals(getDimension())) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(entityUuid);
        } catch (IllegalArgumentException e) {
            return;
        }

        boolean shouldSpawn = forward != despawn;

        if (shouldSpawn) {
            if (EntityReplayManager.getEntity(level, uuid) == null) {
                EntityReplayManager.spawnStandIn(level, uuid, nbt, getX(), getY(), getZ(), yaw, pitch);
            }
        } else {
            EntityReplayManager.removeEntity(level, uuid);
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
        if (nbt != null) tag.put("entityNbt", nbt);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putBoolean("despawn", despawn);
        tag.putInt("eid", EID);
        return tag;
    }

    public static EntitySpawnEvent readNBT(CompoundTag tag) {
        EntitySpawnEvent event = new EntitySpawnEvent(
                tag.getLong("tick"),
                tag.getString("entityUuid"),
                tag.getString("entityType"),
                tag.getCompound("entityNbt"),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getFloat("yaw"),
                tag.getFloat("pitch"),
                tag.getBoolean("despawn"),
                tag.getString("dimension"),
                MTRNbt.getInt(tag, "eid", -1)
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

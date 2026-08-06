package ml.mypals.microtimingreplay.event;

import net.minecraft.core.UUIDUtil;

import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.Optional;
import java.util.UUID;
import org.joml.Vector3f;

public class EntitySpawnEvent extends Vec3PosEvent {
    public static final String TYPE = "entitySpawn";

    private final String entityUuid;
    private final String entityType;
    private final CompoundTag nbt;
    private final float yaw;
    private final float pitch;
    private final boolean despawn;


    public EntitySpawnEvent(long tick, String entityUuid, String entityType, CompoundTag nbt,
                            double x, double y, double z, float yaw, float pitch, boolean despawn, String dimension) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.entityUuid = entityUuid;
        this.entityType = entityType != null ? entityType : "unknown";
        this.nbt = nbt != null ? nbt : new CompoundTag();
        this.yaw = yaw;
        this.pitch = pitch;
        this.despawn = despawn;
    }

    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public CompoundTag getNbt() { return nbt; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isDespawn() { return despawn; }

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
        Component spawnText = MTRComponent.translatable(
                despawn ? "mtr.tooltip.entity.despawn" : "mtr.tooltip.entity.spawn",
                despawn ? "[Despawn / Leave]" : "[Spawn / Enter]"
        );

        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.entity_spawn_title",
                "Entity %s @ [%.2f, %.2f, %.2f]",
                spawnText.getString(), getX(), getY(), getZ()
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
    public void applySelf(ServerLevel level, boolean forward) {
        if (entityUuid == null || entityUuid.isEmpty()
                || !level.dimension().identifier().toString().equals(getDimension())) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(entityUuid);
        } catch (IllegalArgumentException e) {
            return;
        }

        boolean shouldSpawn = forward != despawn;

        if (shouldSpawn) {
            if (EntityReplayManager.getEntity(level, uuid) == null && nbt != null) {
                CompoundTag copyNbt = nbt.copy();
                copyNbt.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid));

                ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), copyNbt);
                Optional<Entity> loaded = EntityType.create(input, level, EntitySpawnReason.LOAD);
                if (loaded.isPresent()) {
                    Entity entity = loaded.get();
                    entity.absSnapTo(getX(), getY(), getZ(), yaw, pitch);
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.setNoGravity(true);
                    entity.noPhysics = true;
                    entity.setInvulnerable(true);
                    if (entity instanceof Mob mob) {
                        mob.setNoAi(true);
                    }
                    level.addFreshEntity(entity);
                    EntityReplayManager.registerEntity(level, uuid, entity);
                    EntityReplayManager.syncEntityPosition(level, entity);
                }
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
        return tag;
    }

    public static EntitySpawnEvent readNBT(CompoundTag tag) {
        EntitySpawnEvent event = new EntitySpawnEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("entityUuid").orElse(""),
                tag.getString("entityType").orElse(""),
                tag.getCompound("entityNbt").orElse(new CompoundTag()),
                tag.getDouble("x").orElse(0.0),
                tag.getDouble("y").orElse(0.0),
                tag.getDouble("z").orElse(0.0),
                tag.getFloat("yaw").orElse(0.0f),
                tag.getFloat("pitch").orElse(0.0f),
                tag.getBoolean("despawn").orElse(false),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

package ml.mypals.microtimingreplay.replay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EntityReplayManager {
    public static final String REPLAY_ENTITY_TAG = "mtr_replay_entity";
    public static final String REPLAY_ENTITY_NAME = "MTRReplayEntity";

    private record LeveledEntity(ServerLevel level, Entity entity) {
    }


    private static final Map<String, Map<UUID, LeveledEntity>> BY_SESSION = new HashMap<>();

    private static Map<UUID, LeveledEntity> entities() {
        return BY_SESSION.computeIfAbsent(ReplayContext.key(), k -> new HashMap<>());
    }

    public static void forgetSession(String sessionId) {
        BY_SESSION.remove(sessionId == null ? "" : sessionId);
    }

    //Identifies a replay entity from both server and client
    public static boolean isReplayEntity(Entity entity) {
        if (entity == null)
            return false;
        Component name = entity.getCustomName();
        return name != null && REPLAY_ENTITY_NAME.equals(name.getString());
    }

    public static void registerEntity(ServerLevel level, UUID uuid, Entity entity) {
        if (uuid != null && entity != null) {
            entity.addTag(REPLAY_ENTITY_TAG);
            entity.addTag("mtr_replay_marker");
            entity.setCustomName(Component.literal(REPLAY_ENTITY_NAME));
            entity.setCustomNameVisible(false);
            entities().put(uuid, new LeveledEntity(level, entity));
        }
    }


    public static Entity getEntity(ServerLevel level, UUID uuid) {
        if (uuid == null)
            return null;
        LeveledEntity cached = entities().get(uuid);
        if (cached != null) {
            if (cached.level() == level || level == null) {
                Entity e = cached.entity();
                if (e != null && e.isAlive() && !e.isRemoved())
                    return e;
            }
        }
        if (level != null) {
            Entity found = level.getEntity(uuid);
            if (found != null && !found.isRemoved()) {
                entities().put(uuid, new LeveledEntity(level, found));
                return found;
            }
        }
        return null;
    }

    public static void removeEntity(ServerLevel level, UUID uuid) {
        if (uuid == null)
            return;
        LeveledEntity cached = entities().get(uuid);
        if (cached != null) {
            if (level != null && cached.level() != level) {
                return;
            }
            entities().remove(uuid);
            Entity entity = cached.entity();
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
                return;
            }
        }
        if (level != null) {
            Entity found = level.getEntity(uuid);
            if (found != null) {
                found.discard();
            }
        }
    }

    public static void clearGlows(ServerLevel level) {
        for (LeveledEntity le : entities().values()) {
            if (le == null)
                continue;
            if (level != null && le.level() != level)
                continue;
            Entity entity = le.entity();
            if (entity != null && !entity.isRemoved()) {
                entity.setGlowingTag(false);
            }
        }
    }

    public static void clearAll(ServerLevel level) {
        if (level == null) {
            List<LeveledEntity> toRemove = new ArrayList<>(entities().values());
            for (LeveledEntity le : toRemove) {
                if (le != null && le.entity() != null && !le.entity().isRemoved()) {
                    le.entity().discard();
                }
            }
            entities().clear();
        } else {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, LeveledEntity> entry : entities().entrySet()) {
                LeveledEntity le = entry.getValue();
                if (le != null && le.level() == level) {
                    if (le.entity() != null && !le.entity().isRemoved()) {
                        le.entity().discard();
                    }
                    toRemove.add(entry.getKey());
                }
            }
            for (UUID id : toRemove)
                entities().remove(id);

            List<Entity> levelEntities = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity.entityTags().contains(REPLAY_ENTITY_TAG)
                        || entity.entityTags().contains("mtr_replay_marker")) {
                    levelEntities.add(entity);
                }
            }
            for (Entity entity : levelEntities)
                entity.discard();
        }
    }

    /**
     * Spawns a replay stand-in at an explicit position, reusing the recorded UUID.
     * Used by {@code EntitySpawnEvent} when the replay steps over a spawn.
     */
    public static Entity spawnStandIn(ServerLevel level, UUID uuid, CompoundTag nbt,
                                      double x, double y, double z, float yaw, float pitch) {
        if (level == null || uuid == null || nbt == null || nbt.isEmpty()) return null;

        CompoundTag copy = nbt.copy();
        copy.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid));

        Entity entity = load(level, copy);
        if (entity == null) return null;

        entity.absSnapTo(x, y, z, yaw, pitch);
        finishStandIn(level, uuid, entity);
        return entity;
    }

    /**
     * Spawns a replay stand-in from saved NBT that already carries its own position
     * and UUID — the world backup taken when recording started.
     * <p>
     * These are the entities that were already in the area before the first tick, so
     * they belong to the replay's starting state rather than to any recorded event.
     */
    public static Entity spawnStandInFromNbt(ServerLevel level, CompoundTag nbt) {
        if (level == null || nbt == null || nbt.isEmpty()) return null;

        Entity entity = load(level, nbt);
        if (entity == null) return null;

        finishStandIn(level, entity.getUUID(), entity);
        return entity;
    }

    private static Entity load(ServerLevel level, CompoundTag nbt) {
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt);
        return EntityType.create(input, level, EntitySpawnReason.LOAD).orElse(null);
    }

    /**
     * Inert on every axis: no gravity, no collision, no AI, invulnerable. Registering
     * it also adds {@link #REPLAY_ENTITY_TAG}, which is what stops the server from
     * ticking it at all — a stand-in must only ever move when the replay says so.
     */
    private static void finishStandIn(ServerLevel level, UUID uuid, Entity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        level.addFreshEntity(entity);
        registerEntity(level, uuid, entity);
        syncEntityPosition(level, entity);
    }

    public static void syncEntityPosition(ServerLevel level, Entity entity) {
        if (level != null && entity != null && MicroTimingReplay.server != null) {
            entity.setOldPosAndRot();
            entity.setDeltaMovement(Vec3.ZERO);
            var teleportPacket = ClientboundTeleportEntityPacket.teleport(entity.getId(),
                    PositionMoveRotation.of(entity), Set.of(), entity.onGround());
            for (ServerPlayer player : MicroTimingReplay.server.getPlayerList().getPlayers()) {
                if (player.level() == level) {
                    player.connection.send(teleportPacket);
                }
            }
        }
    }
}

package ml.mypals.microtimingreplay.replay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import net.minecraft.server.level.ServerPlayer;

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

    private static final Map<UUID, LeveledEntity> REPLAY_ENTITIES = new HashMap<>();

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
            REPLAY_ENTITIES.put(uuid, new LeveledEntity(level, entity));
        }
    }


    public static Entity getEntity(ServerLevel level, UUID uuid) {
        if (uuid == null)
            return null;
        LeveledEntity cached = REPLAY_ENTITIES.get(uuid);
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
                REPLAY_ENTITIES.put(uuid, new LeveledEntity(level, found));
                return found;
            }
        }
        return null;
    }

    public static void removeEntity(ServerLevel level, UUID uuid) {
        if (uuid == null)
            return;
        LeveledEntity cached = REPLAY_ENTITIES.get(uuid);
        if (cached != null) {
            if (level != null && cached.level() != level) {
                return;
            }
            REPLAY_ENTITIES.remove(uuid);
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
        for (LeveledEntity le : REPLAY_ENTITIES.values()) {
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
            List<LeveledEntity> toRemove = new ArrayList<>(REPLAY_ENTITIES.values());
            for (LeveledEntity le : toRemove) {
                if (le != null && le.entity() != null && !le.entity().isRemoved()) {
                    le.entity().discard();
                }
            }
            REPLAY_ENTITIES.clear();
        } else {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, LeveledEntity> entry : REPLAY_ENTITIES.entrySet()) {
                LeveledEntity le = entry.getValue();
                if (le != null && le.level() == level) {
                    if (le.entity() != null && !le.entity().isRemoved()) {
                        le.entity().discard();
                    }
                    toRemove.add(entry.getKey());
                }
            }
            for (UUID id : toRemove)
                REPLAY_ENTITIES.remove(id);

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

package ml.mypals.microtimingreplay.marker;

import net.minecraft.world.entity.Entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Brightness;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PistonDisplayManager {

    private static final Map<BlockPos, List<UUID>> pistonDisplays = new HashMap<>();
    private static final Map<BlockPos, List<UUID>> stagedOldPistonDisplays = new HashMap<>();

    public static void beginRenderPistons() {
        stagedOldPistonDisplays.clear();
        stagedOldPistonDisplays.putAll(pistonDisplays);
        pistonDisplays.clear();
    }

    private static Entity findEntityInAllLevels(ServerLevel defaultLevel, UUID uuid) {
        if (defaultLevel == null || uuid == null) return null;
        if (defaultLevel.getServer() != null) {
            for (ServerLevel sl : defaultLevel.getServer().getAllLevels()) {
                Entity e = sl.getEntity(uuid);
                if (e != null) return e;
            }
        }
        return defaultLevel.getEntity(uuid);
    }

    public static void endRenderPistons(ServerLevel level) {
        List<Entity> toDiscard = new ArrayList<>();
        for (List<UUID> uuids : stagedOldPistonDisplays.values()) {
            for (UUID uuid : uuids) {
                Entity entity = findEntityInAllLevels(level, uuid);
                if (entity != null) toDiscard.add(entity);
            }
        }
        for (Entity e : toDiscard) {
            e.discard();
        }
        stagedOldPistonDisplays.clear();
    }

    public static void clearAll(ServerLevel level) {
        List<Entity> toDiscard = new ArrayList<>();
        for (List<UUID> uuids : pistonDisplays.values()) {
            for (UUID uuid : uuids) {
                Entity entity = findEntityInAllLevels(level, uuid);
                if (entity != null) toDiscard.add(entity);
            }
        }
        for (Entity e : toDiscard) {
            e.discard();
        }
        pistonDisplays.clear();
        stagedOldPistonDisplays.clear();
    }

    public static void clearGlows(ServerLevel level) {
        for (List<UUID> uuids : pistonDisplays.values()) {
            for (UUID uuid : uuids) {
                Entity entity = findEntityInAllLevels(level, uuid);
                if (entity != null) entity.setGlowingTag(false);
            }
        }
    }

    public static List<UUID> claimOldPistonDisplay(BlockPos pos) {
        return stagedOldPistonDisplays.remove(pos);
    }

    public static void registerPistonDisplays(BlockPos pos, List<UUID> uuids) {
        pistonDisplays.put(pos, new ArrayList<>(uuids));
    }

    public static void removePistonDisplays(BlockPos pos, ServerLevel level) {
        List<UUID> uuids = pistonDisplays.remove(pos);
        if (uuids == null) {
            uuids = stagedOldPistonDisplays.remove(pos);
        }
        if (uuids == null) return;
        for (UUID uuid : uuids) {
            Entity entity = findEntityInAllLevels(level, uuid);
            if (entity != null) entity.discard();
        }
    }

    public static List<UUID> getPistonDisplayUUIDs(BlockPos pos) {
        return pistonDisplays.get(pos);
    }

    public static UUID spawnStaticBlockDisplay(ServerLevel level, BlockPos blockPos, BlockState state, float xOff, float yOff, float zOff) {
        Display.BlockDisplay entity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        entity.setPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        entity.setBlockState(state);
        entity.setBrightnessOverride(new Brightness(15, 15));
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);

        Transformation transform = new Transformation(
                new Vector3f(xOff, yOff, zOff),
                new Quaternionf(),
                new Vector3f(1, 1, 1),
                new Quaternionf()
        );
        entity.setTransformation(transform);
        entity.setTransformationInterpolationDuration(0);
        entity.setTransformationInterpolationDelay(0);

        entity.addTag("mtr_piston_display");
        entity.addTag("mtr_replay_marker");
        entity.setGlowingTag(false);
        level.addFreshEntity(entity);
        return entity.getUUID();
    }

    public static void applyOffset(Display.BlockDisplay bd, float xOff, float yOff, float zOff) {
        Transformation transform = new Transformation(
                new Vector3f(xOff, yOff, zOff),
                new Quaternionf(),
                new Vector3f(1, 1, 1),
                new Quaternionf()
        );
        bd.setTransformation(transform);
        bd.setTransformationInterpolationDuration(0);
        bd.setTransformationInterpolationDelay(0);
    }
}

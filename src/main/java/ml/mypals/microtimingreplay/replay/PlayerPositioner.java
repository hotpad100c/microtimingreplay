package ml.mypals.microtimingreplay.replay;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerPositioner {
    private static final double MIN_EYE_DISTANCE = 2.0;
    private static final double[] EYE_DISTANCES = {5.0, 7.0, 10.0, 14.0};
    private static final double[] PITCH_BANDS_DEGREES = {25.0, 45.0, 10.0, 65.0, 0.0, -20.0};
    private static final int YAW_STEPS = 12;
    private static final int POSITION_LOOKBACK = 512;
    private record Anchor(GameType gameMode, ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot) {}

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

    public static boolean isFollowing(ServerPlayer player) {
        return player != null && ANCHORS.containsKey(player.getUUID());
    }

    public static void enable(ServerPlayer player) {
        if (player == null || isFollowing(player)) return;

        ANCHORS.put(player.getUUID(), new Anchor(
                player.gameMode.getGameModeForPlayer(),
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot()));

        player.setGameMode(GameType.SPECTATOR);
    }

    public static void disable(ServerPlayer player) {
        if (player == null) return;

        Anchor anchor = ANCHORS.remove(player.getUUID());
        if (anchor == null) return;

        ServerLevel level = player.level().getServer().getLevel(anchor.dimension());
        if (level != null) {
            player.teleportTo(level, anchor.position().x, anchor.position().y, anchor.position().z,
                    Set.<RelativeMovement>of(), anchor.yRot(), anchor.xRot());
        }
        player.setGameMode(anchor.gameMode());
    }

    public static void disableAll(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            disable(player);
        }
    }

    public static boolean focusOnCursor(ServerPlayer player, ReplaySession session, ServerLevel defaultLevel) {
        if (!isFollowing(player) || session == null) return false;

        ReplaySession.ReplayAction action = session.currentPositionedAction(POSITION_LOOKBACK);
        if (action == null) return false;

        BlockPos pos = action.event().getMarkerPos();
        if (pos == null) return false;

        ServerLevel level = ReplaySession.resolveLevel(defaultLevel, action.event());
        return focusOn(player, level, Vec3.atCenterOf(pos));
    }

    public static boolean focusOn(ServerPlayer player, ServerLevel level, Vec3 target) {
        Vec3 eye = findVantagePoint(player, level, target);
        if (eye == null) return false;

        double feetY = eye.y - player.getEyeHeight();
        float[] rotation = lookAt(eye, target);

        player.teleportTo(level, eye.x, feetY, eye.z, Set.<RelativeMovement>of(), rotation[0], rotation[1]);
        return true;
    }

    private static Vec3 findVantagePoint(ServerPlayer player, ServerLevel level, Vec3 target) {
        Vec3 currentEye = player.getEyePosition();

        for (double distance : EYE_DISTANCES) {
            List<Vec3> candidates = candidatesAt(target, distance, currentEye);
            for (Vec3 candidate : candidates) {
                if (canSee(player, level, candidate, target)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static List<Vec3> candidatesAt(Vec3 target, double distance, Vec3 currentEye) {
        List<Vec3> candidates = new ArrayList<>(PITCH_BANDS_DEGREES.length * YAW_STEPS + 1);

        Vec3 toCurrent = currentEye.subtract(target);
        if (toCurrent.lengthSqr() > 1.0E-4) {
            candidates.add(target.add(toCurrent.normalize().scale(distance)));
        }

        for (double pitch : PITCH_BANDS_DEGREES) {
            double pitchRad = Math.toRadians(pitch);
            double horizontal = Math.cos(pitchRad);
            double vertical = Math.sin(pitchRad);

            for (int step = 0; step < YAW_STEPS; step++) {
                double yawRad = 2 * Math.PI * step / YAW_STEPS;
                Vec3 direction = new Vec3(horizontal * Math.cos(yawRad), vertical, horizontal * Math.sin(yawRad));
                candidates.add(target.add(direction.scale(distance)));
            }
        }

        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(currentEye)));
        return candidates;
    }

    private static boolean canSee(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 target) {
        if (eye.distanceToSqr(target) < MIN_EYE_DISTANCE * MIN_EYE_DISTANCE) return false;

        BlockPos eyeBlock = BlockPos.containing(eye);
        if (!level.getBlockState(eyeBlock).getCollisionShape(level, eyeBlock).isEmpty()) {
            return false;
        }

        BlockHitResult hit = level.clip(
                new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return true;

        return hit.getBlockPos().equals(BlockPos.containing(target));
    }

    private static float[] lookAt(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[] {yaw, pitch};
    }
    public static void onDisconnect(ServerPlayer player) {
        try {
            disable(player);
        } catch (Exception e) {
            MicroTimingReplay.LOGGER.error("Failed to restore camera-follow state for {}", player.getName().getString(), e);
        }
    }
}

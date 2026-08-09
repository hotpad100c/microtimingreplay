package ml.mypals.microtimingreplay.replay;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every running replay, and which one each player is watching.
 * <p>
 * Sessions are keyed by profile name — one replay per profile — and a player is
 * subscribed to at most one at a time, so the stepping commands always have an
 * unambiguous target.
 */
public final class ReplayManager {

    private ReplayManager() {}

    private static final Map<String, ReplaySession> sessions = new LinkedHashMap<>();
    private static final Map<UUID, String> subscriptions = new LinkedHashMap<>();

    public static boolean isAnyRunning() {
        return !sessions.isEmpty();
    }

    public static List<String> runningProfileNames() {
        return List.copyOf(sessions.keySet());
    }

    public static ReplaySession get(String profileName) {
        return profileName == null ? null : sessions.get(profileName);
    }

    public static boolean isRunning(String profileName) {
        return get(profileName) != null;
    }

    /**
     * @return the new session, or {@code null} if this profile is already replaying
     */
    public static ReplaySession start(MTRProfile profile) {
        if (profile == null || sessions.containsKey(profile.getName())) {
            return null;
        }
        // Lay down the recorded starting state before the session flattens and renders.
        MTRState.beginReplayWorld(profile);
        ReplaySession session = new ReplaySession(profile);
        sessions.put(profile.getName(), session);
        return session;
    }

    public static void stop(String profileName) {
        ReplaySession session = sessions.remove(profileName);
        if (session == null) return;

        MTRProfile profile = session.getProfile();
        session.stopReplay();
        MTRState.endReplayWorld(profile);
        // Everything the session filed away under its id.
        EntityReplayManager.forgetSession(profileName);
        ml.mypals.microtimingreplay.marker.PistonDisplayManager.forgetSession(profileName);
        ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager.forgetSession(profileName);
        subscriptions.values().removeIf(profileName::equals);
    }

    public static void stopAll() {
        for (String name : List.copyOf(sessions.keySet())) {
            stop(name);
        }
    }

    /**
     * Other replays already writing blocks where this one is about to. Their recordings
     * will fight over the same positions, and each restores its own {@code _replay}
     * backup on stop — so the last one to stop wins.
     */
    public static List<String> overlappingWith(MTRProfile profile) {
        List<String> clashing = new ArrayList<>();
        for (ReplaySession session : sessions.values()) {
            MTRProfile other = session.getProfile();
            if (other == null || other.getName().equals(profile.getName())) continue;
            if (areasIntersect(profile, other)) {
                clashing.add(other.getName());
            }
        }
        return clashing;
    }

    private static boolean areasIntersect(MTRProfile a, MTRProfile b) {
        for (MTRProfile.Area x : a.getAreas()) {
            for (MTRProfile.Area y : b.getAreas()) {
                if (!MTRProfile.matchDimension(x.dimension, y.dimension)) continue;
                if (x.x1 <= y.x2 && x.x2 >= y.x1
                        && x.y1 <= y.y2 && x.y2 >= y.y1
                        && x.z1 <= y.z2 && x.z2 >= y.z1) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── subscriptions ────────────────────────────────────────────────────────

    /** The session this player is watching, or {@code null}. */
    public static ReplaySession subscribedSession(ServerPlayer player) {
        if (player == null) return null;
        String name = subscriptions.get(player.getUUID());
        return name == null ? null : sessions.get(name);
    }

    /**
     * Moves the player to {@code profileName}, leaving whatever they were watching.
     *
     * @return the session subscribed to, or {@code null} if it is not running
     */
    public static ReplaySession subscribe(ServerPlayer player, String profileName) {
        ReplaySession target = get(profileName);
        if (player == null || target == null) return null;

        unsubscribe(player);
        subscriptions.put(player.getUUID(), profileName);
        target.subscribe(player);
        return target;
    }

    public static void unsubscribe(ServerPlayer player) {
        if (player == null) return;
        String previous = subscriptions.remove(player.getUUID());
        if (previous == null) return;
        ReplaySession session = sessions.get(previous);
        if (session != null) {
            session.unsubscribe(player);
        }
    }

    /** Drives every running session's auto-replay. */
    public static void tickAll(MinecraftServer server) {
        if (sessions.isEmpty() || server == null) return;
        ServerLevel overworld = server.overworld();
        for (ReplaySession session : List.copyOf(sessions.values())) {
            session.tickAutoReplay(overworld);
        }
    }

    public static void onServerStopping() {
        try {
            stopAll();
        } catch (Exception e) {
            MicroTimingReplay.LOGGER.error("Failed to stop replays cleanly", e);
        }
    }
}

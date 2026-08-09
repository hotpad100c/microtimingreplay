package ml.mypals.microtimingreplay;

import ml.mypals.microtimingreplay.replay.EntityReplayManager;

import ml.mypals.microtimingreplay.event.MTREvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.record.RecordingBossBar;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import ml.mypals.microtimingreplay.replay.ReplayManager;
import ml.mypals.microtimingreplay.replay.WorldBackupManager;
import ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.level.Level;

import java.util.Stack;

public class MTRState {
    /**
     *  Only recording is global mode now;
     *  replays are tracked by {@code ReplayManager}.
     **/
    public enum State {
        IDLE,
        RECORDING
    }

    private static State currentState = State.IDLE;
    private static MTRProfile activeProfile = null;
    private static long recordStartTick = 0;
    private static long recordTargetTick = -1;
    public static final Stack<MTREvent> currentEventStack = new Stack<>();

    public static State getCurrentState() {
        return currentState;
    }

    public static boolean isRecording(Level level) {
        if (currentState != State.RECORDING)
            return false;
        if (level != null && level.isClientSide())
            return false;
        if (MicroTimingReplay.server == null)
            return false;
        if (activeProfile == null)
            return false;
        return level == null || !activeProfile.outsideDimensionOrArea(level.dimension().identifier().toString());
    }

    public static MTRProfile getActiveProfile() {
        return activeProfile;
    }

    public static long getRecordStartTick() {
        return recordStartTick;
    }

    public static boolean startRecording(String profileName, MinecraftServer server, int advance) {
        // Recording is exclusive, and it cannot share the world with a replay: both
        // rewrite the same blocks.
        if (currentState != State.IDLE || ReplayManager.isAnyRunning()) {
            return false;
        }
        MTRProfile profile = ProfileManager.loadProfile(profileName);
        if (profile == null)
            return false;

        currentState = State.RECORDING;
        activeProfile = profile;
        recordStartTick = server.getTickCount() - profile.getTicksRecorded();
        recordTargetTick = server.getTickCount() + advance;
        currentEventStack.clear();

        if (profile.getTicksRecorded() == 0) {
            // Captures the whole starting population, players included (as mannequin
            // stand-ins). Replay start restores it wholesale, so nothing that was
            // already here needs a spawn event of its own.
            WorldBackupManager.backup(activeProfile, "record", true);
        }

        RecordingBossBar.start(server, profile.getName(), advance);

        ServerTickRateManager manager = server.tickRateManager();
        manager.stepGameIfPaused(advance);

        return true;
    }

    public static boolean isTimeFrozen(MinecraftServer server) {
        return server != null && server.tickRateManager().isFrozen();
    }

    public static void stopRecording() {
        if (currentState == State.RECORDING && activeProfile != null) {
            long ticks = MicroTimingReplay.server.getTickCount() - recordStartTick;
            activeProfile.setTicksRecorded((int) ticks);
            ProfileManager.saveProfile(activeProfile);
            StackTraceManager.collectAndSaveForProfile(activeProfile);
            RecordingBossBar.stop();
            currentState = State.IDLE;
            activeProfile = null;
            recordTargetTick = -1;
            currentEventStack.clear();
        }
    }

    public static void checkAutoStop(MinecraftServer server) {
        if (currentState == State.RECORDING) {

            if (!currentEventStack.isEmpty()) {
                MicroTimingReplay.LOGGER.warn(
                        "Event stack was not empty at end of tick ({} entries left); discarding residue.",
                        currentEventStack.size());
                currentEventStack.clear();
            }
            if (recordTargetTick != -1) {
                RecordingBossBar.tick(server, recordTargetTick - server.getTickCount());
                if (server.getTickCount() >= recordTargetTick) {
                    stopRecording();
                }
            }
        }
        // Independent of recording: replays run on their own and there can be several.
        ReplayManager.tickAll(server);
    }

    /** Replays cannot start mid-recording — both rewrite the same blocks. */
    public static boolean canStartReplay() {
        return currentState != State.RECORDING;
    }

    /**
     * Puts one profile's area into the state its recording started from, saving the
     * live scene first so {@link #endReplayWorld} can hand it back.
     */
    public static void beginReplayWorld(MTRProfile profile) {
        WorldBackupManager.backup(profile, "replay", false);
        // Entities present before the first recorded tick come back here as inert
        // stand-ins, so they need no spawn events of their own.
        WorldBackupManager.restore(profile, "record", true);
    }

    public static void endReplayWorld(MTRProfile profile) {
        if (profile != null) {
            WorldBackupManager.restore(profile, "replay", false);
        }
    }

    public static void pushEvent(MTREvent event) {
        if (currentState != State.RECORDING)
            return;

        if (currentEventStack.isEmpty()) {
            activeProfile.addEvent(MicroTimingReplay.server.getTickCount() - recordStartTick, event);
        } else {
            currentEventStack.peek().addChild(event);
        }
        currentEventStack.push(event);
    }

    public static void popEvent() {
        if (currentState != State.RECORDING || currentEventStack.isEmpty())
            return;

        MTREvent popped = currentEventStack.pop();
        if (popped.getChildren().isEmpty() && !popped.saveEvenWithoutAction(MicroTimingReplay.server)) {
            if (!currentEventStack.isEmpty()) {
                currentEventStack.peek().removeChild(popped);
            } else {
                long currentTick = MicroTimingReplay.server.getTickCount() - recordStartTick;
                activeProfile.removeEvent(currentTick, popped);
            }
        }
    }

    public static void recordStep(MTREvent step) {
        if (currentState != State.RECORDING)
            return;

        if (currentEventStack.isEmpty()) {
            activeProfile.addEvent(MicroTimingReplay.server.getTickCount() - recordStartTick, step);
        } else {
            currentEventStack.peek().addChild(step);
        }
    }

    public static void stoppingServer(MinecraftServer server) {
        if (currentState == State.RECORDING) {
            stopRecording();
        }
        ReplayManager.onServerStopping();
    }
}

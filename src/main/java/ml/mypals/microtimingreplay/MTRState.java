package ml.mypals.microtimingreplay;

import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.commands.TickCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;

public class MTRState {
    public enum State {
        IDLE,
        RECORDING,
        REPLAYING
    }

    private static State currentState = State.IDLE;
    private static MTRProfile activeProfile = null;
    private static long recordStartTick = 0;
    private static long recordTargetTick = -1;

    public static State getCurrentState() {
        return currentState;
    }

    public static MTRProfile getActiveProfile() {
        return activeProfile;
    }

    public static long getRecordStartTick() {
        return recordStartTick;
    }

    public static boolean startRecording(String profileName, MinecraftServer server, int advance) {
        if (currentState != State.IDLE) {
            return false;
        }
        MTRProfile profile = ProfileManager.loadProfile(profileName);
        if (profile == null) return false;

        currentState = State.RECORDING;
        activeProfile = profile;
        recordStartTick = server.getTickCount();
        recordTargetTick = recordStartTick + advance;
        ServerTickRateManager manager = server.tickRateManager();

        return manager.stepGameIfPaused(advance);
    }

    public static void stopRecording() {
        if (currentState == State.RECORDING && activeProfile != null) {
            long ticks = MicroTimingReplay.server.getTickCount() - recordStartTick;
            activeProfile.setTicksRecorded(activeProfile.getTicksRecorded() + (int) ticks);
            ProfileManager.saveProfile(activeProfile);
            currentState = State.IDLE;
            activeProfile = null;
            recordTargetTick = -1;
        }
    }

    public static void checkAutoStop(MinecraftServer server) {
        if (currentState == State.RECORDING && recordTargetTick != -1) {
            if (server.getTickCount() >= recordTargetTick) {
                stopRecording();
            }
        }
    }

    public static boolean startReplaying(String profileName) {
        if (currentState != State.IDLE) {
            return false;
        }
        MTRProfile profile = ProfileManager.loadProfile(profileName);
        if (profile == null) return false;

        currentState = State.REPLAYING;
        activeProfile = profile;
        return true;
    }

    public static void stopReplaying() {
        if (currentState == State.REPLAYING) {
            currentState = State.IDLE;
            activeProfile = null;
        }
    }
}

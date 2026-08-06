package ml.mypals.microtimingreplay;

import ml.mypals.microtimingreplay.event.EntitySpawnEvent;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;

import ml.mypals.microtimingreplay.event.MTREvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import ml.mypals.microtimingreplay.replay.ReplayEngine;
import ml.mypals.microtimingreplay.replay.WorldBackupManager;
import ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.level.Level;

import java.util.Stack;

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
        if (currentState != State.IDLE) {
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
            WorldBackupManager.backup(activeProfile, "record");
        }

        scanExistingEntities(server, profile);

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
            if (recordTargetTick != -1 && server.getTickCount() >= recordTargetTick) {
                stopRecording();
            }
        } else if (currentState == State.REPLAYING) {
            ReplayEngine.tickAutoReplay(server.overworld());
        }
    }

    public static boolean startReplaying(String profileName) {
        if (currentState != State.IDLE) {
            return false;
        }
        MTRProfile profile = ProfileManager.loadProfile(profileName);
        if (profile == null)
            return false;

        currentState = State.REPLAYING;
        activeProfile = profile;
        WorldBackupManager.backup(activeProfile, "replay");
        WorldBackupManager.restore(activeProfile, "record");
        return true;
    }

    public static void stopReplaying() {
        if (currentState == State.REPLAYING) {
            WorldBackupManager.restore(activeProfile, "replay");
            currentState = State.IDLE;
            activeProfile = null;
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
        } else if (currentState == State.REPLAYING) {
            ReplayEngine.stopReplay();
            stopReplaying();
        }
    }

    private static void scanExistingEntities(MinecraftServer server, MTRProfile profile) {
        if (server == null || profile == null)
            return;
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player)
                    continue;
                if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG))
                    continue;

                if (!profile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = server.getTickCount() - recordStartTick;
                    ProblemReporter reporter = ProblemReporter.DISCARDING;
                    TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
                    entity.saveWithoutId(output);
                    CompoundTag nbt = output.buildResult();
                    String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    nbt.putString("id", entityTypeKey);

                    EntitySpawnEvent event = new EntitySpawnEvent(
                            currentTick,
                            entity.getUUID().toString(),
                            entityTypeKey,
                            nbt,
                            entity.getX(), entity.getY(), entity.getZ(),
                            entity.getYRot(), entity.getXRot(),
                            false,dim);

                    recordStep(event);
                }
            }
        }
    }
}

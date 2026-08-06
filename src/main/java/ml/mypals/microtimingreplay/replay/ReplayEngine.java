package ml.mypals.microtimingreplay.replay;

import ml.mypals.microtimingreplay.event.MovingPistonEvent;
import ml.mypals.microtimingreplay.event.MovingPistonTickEvent;

import ml.mypals.microtimingreplay.config.MTRGameRules;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.*;
import ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.marker.MarkerManager;
import ml.mypals.microtimingreplay.marker.PistonDisplayManager;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.TickFrame;
import ml.mypals.microtimingreplay.replay.scoreboard.TimelineGenerator;
import ml.mypals.microtimingreplay.replay.scoreboard.VirtualScoreboardManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

public class ReplayEngine {

    public enum ActionType {
        ENTER, LEAF, EXIT
    }


    public record ReplayAction(TickFrame frame, MTREvent event, ActionType type, PhaseEvent phase, MTREvent queue,
            UpdateEvent update, int visibleStepIndex) {
    }

    public static final List<String> STEP_UNITS = List.of("ticks", "phase", "queue", "updates", "steps");

    public static boolean isUnitInvalid(String unit) {
        return unit == null || !STEP_UNITS.contains(unit.toLowerCase(Locale.ROOT));
    }

    private static MTRProfile currentProfile;

    private static final Set<UUID> subscribers = new HashSet<>();
    private static ServerBossEvent bossBar;

    private static final List<ReplayAction> flatActions = new ArrayList<>();
    private static int actionCursor = 0;
    private static long totalTick = 0;
    private static long currentVirtualTick = 0;
    private static boolean isEmpty = false;


    public static void startReplay(MTRProfile profile, ServerLevel level) {
        currentProfile = profile;
        flatActions.clear();
        actionCursor = 0;
        isEmpty = profile.getFrames().isEmpty();
        StackTraceManager.loadForProfile(profile.getName());

        if (!isEmpty) {
            totalTick = profile.getFrames().getLast().getTick();
            int stepCounter = 1;
            for (TickFrame frame : profile.getFrames()) {
                for (MTREvent event : frame.getEvents()) {
                    stepCounter = flatten(frame, event, null, null, null, stepCounter);
                }
            }
        }

        bossBar = new ServerBossEvent(UUID.randomUUID(), MTRComponent.translatable("mtr.bossbar.start", "Replay: %s", profile.getName()),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        updateBossBar();
    }

    private static int flatten(TickFrame frame, MTREvent event, PhaseEvent p, MTREvent q, UpdateEvent u, int stepCounter) {
        if (event instanceof PhaseEvent phase)
            p = phase;
        else if (event.isQueueScope())
            q = event;
        else if (event instanceof UpdateEvent update)
            u = update;

        if (event.getChildren().isEmpty()) {
            if (StackTraceManager.get(stepCounter) == null && event.getStackTrace() != null && !event.getStackTrace().isEmpty()) {
                StackTraceManager.record(stepCounter, event.getStackTrace());
            }
            flatActions.add(new ReplayAction(frame, event, ActionType.LEAF, p, q, u, stepCounter++));
        } else {
            if (StackTraceManager.get(stepCounter) == null && event.getStackTrace() != null && !event.getStackTrace().isEmpty()) {
                StackTraceManager.record(stepCounter, event.getStackTrace());
            }
            flatActions.add(new ReplayAction(frame, event, ActionType.ENTER, p, q, u, stepCounter++));
            for (MTREvent child : event.getChildren()) {
                stepCounter = flatten(frame, child, p, q, u, stepCounter);
            }
            flatActions.add(new ReplayAction(frame, event, ActionType.EXIT, p, q, u, -1));
        }
        return stepCounter;
    }

    public static void stopReplay() {
        stopAutoReplay();
        if (bossBar != null) {
            for (ServerPlayer player : onlineSubscribers()) {
                VirtualScoreboardManager.removeScoreboard(player);
            }
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        subscribers.clear();
        currentProfile = null;

        if (MicroTimingReplay.server != null) {
            for (ServerLevel sl : MicroTimingReplay.server.getAllLevels()) {
                List<Entity> toDiscard = new ArrayList<>();
                for (Entity entity : sl.getAllEntities()) {
                    if (entity.entityTags().contains("mtr_replay_marker") ||
                        entity.entityTags().contains("mtr_marker") ||
                        entity.entityTags().contains("mtr_area_marker") ||
                        entity.entityTags().contains("mtr_dynamic_marker") ||
                        entity.entityTags().contains("mtr_piston_display") ||
                        entity.entityTags().contains("mtr_replay_entity")) {
                        toDiscard.add(entity);
                    }
                }
                for (Entity entity : toDiscard) {
                    entity.discard();
                }
                MarkerManager.clearAll(sl);
                PistonDisplayManager.clearAll(sl);
            }
            // Level is set to null, it will clear every tracked replay entity at once.
            EntityReplayManager.clearAll(null);
        }
    }

    public static List<ReplayAction> getFlatActionsSnapshot() {
        return List.copyOf(flatActions);
    }

    public static int getActionCursor() {
        return actionCursor;
    }

    public static class AutoReplayTask {
        public final String direction;
        public final String unit;
        public final int delayTicks;
        public final int totalSteps;
        public int executedSteps = 0;
        public int tickCounter = 0;

        public AutoReplayTask(String direction, String unit, int delayTicks, int totalSteps) {
            this.direction = direction;
            this.unit = unit;
            this.delayTicks = delayTicks;
            this.totalSteps = totalSteps;
        }
    }

    private static AutoReplayTask activeAutoTask = null;

    public static void startAutoReplay(String direction, String unit, int delayTicks, int totalSteps) {
        activeAutoTask = new AutoReplayTask(direction, unit, delayTicks, totalSteps);
    }

    public static void stopAutoReplay() {
        activeAutoTask = null;
    }

    public static boolean isAutoReplayActive() {
        return activeAutoTask != null;
    }

    public static void tickAutoReplay(ServerLevel level) {
        if (activeAutoTask == null || currentProfile == null || level == null) return;

        activeAutoTask.tickCounter++;
        if (activeAutoTask.tickCounter >= activeAutoTask.delayTicks) {
            activeAutoTask.tickCounter = 0;

            int taken = 0;
            if ("forward".equalsIgnoreCase(activeAutoTask.direction)) {
                taken = stepForward(level, 1, activeAutoTask.unit);
            } else if ("backward".equalsIgnoreCase(activeAutoTask.direction)) {
                taken = stepBackward(level, 1, activeAutoTask.unit);
            }

            activeAutoTask.executedSteps++;

            if (taken == 0 || activeAutoTask.executedSteps >= activeAutoTask.totalSteps) {
                activeAutoTask = null;
            }
        }
    }

    public static void subscribe(ServerPlayer player) {
        if (player == null) return;
        subscribers.add(player.getUUID());
        if (bossBar != null) {
            bossBar.addPlayer(player);
            VirtualScoreboardManager.setupScoreboard(player);
            updateScoreboards();
        }
    }

    public static void unsubscribe(ServerPlayer player) {
        if (player == null) return;
        subscribers.remove(player.getUUID());
        if (bossBar != null) {
            bossBar.removePlayer(player);
            VirtualScoreboardManager.removeScoreboard(player);
        }
    }

    public static boolean isSubscribed(ServerPlayer player) {
        return player != null && subscribers.contains(player.getUUID());
    }

    private static List<ServerPlayer> onlineSubscribers() {
        if (subscribers.isEmpty() || MicroTimingReplay.server == null)
            return List.of();

        List<ServerPlayer> online = new ArrayList<>(subscribers.size());
        subscribers.removeIf(uuid -> {
            ServerPlayer player = MicroTimingReplay.server.getPlayerList().getPlayer(uuid);
            if (player == null) return true;
            online.add(player);
            return false;
        });
        return online;
    }

    private static void updateScoreboards() {
        if (subscribers.isEmpty() || isEmpty)
            return;

        ReplayAction currentAction = null;
        TickFrame currentFrame = null;

        if (actionCursor > 0 && actionCursor <= flatActions.size()) {
            currentAction = flatActions.get(actionCursor - 1);
            currentFrame = currentAction.frame;
        } else if (actionCursor == 0 && !flatActions.isEmpty()) {
            currentFrame = flatActions.getFirst().frame;
        }

        if (currentFrame == null)
            return;

        Map<MTREvent, Integer> stepMap = new HashMap<>();
        for (ReplayAction action : flatActions) {
            if (action.frame() == currentFrame) {
                if (action.type() != ActionType.EXIT) {
                    stepMap.put(action.event(), action.visibleStepIndex());
                }
            }
        }

        List<TimelineGenerator.ScoreLine> lines = TimelineGenerator.generateTimeline(currentFrame, currentAction, stepMap);
        for (ServerPlayer player : onlineSubscribers()) {
            VirtualScoreboardManager.updateLines(player, lines);
        }
    }

    private static void updateBossBar() {
        if (bossBar == null)
            return;

        // A profile can hold frames that flattened to nothing; treat that as empty too
        // so the progress calculation below never divides by zero.
        if (isEmpty || flatActions.isEmpty()) {
            bossBar.setColor(BossEvent.BossBarColor.RED);
            bossBar.setName(MTRComponent.translatable(
                "mtr.bossbar.title.empty",
                "Replay [%s] Tick: %d / %d (empty)",
                currentProfile.getName(), currentVirtualTick, totalTick
            ));
            return;
        }

        List<MTREvent> activeStack = new ArrayList<>();
        for (int i = 0; i < actionCursor; i++) {
            ReplayAction a = flatActions.get(i);
            if (a.type == ActionType.ENTER)
                activeStack.add(a.event);
            else if (a.type == ActionType.EXIT)
                activeStack.remove(a.event);
        }

        MTREvent nearestParent = activeStack.isEmpty() ? null : activeStack.getLast();
        BossEvent.BossBarColor color = BossEvent.BossBarColor.BLUE;
        MutableComponent extraDesc = Component.literal("");
        if (nearestParent != null) {
            ChatFormatting fmt = nearestParent.getColor();
            if (fmt == ChatFormatting.RED)
                color = BossEvent.BossBarColor.RED;
            else if (fmt == ChatFormatting.YELLOW)
                color = BossEvent.BossBarColor.YELLOW;
            else if (fmt == ChatFormatting.LIGHT_PURPLE || fmt == ChatFormatting.DARK_PURPLE)
                color = BossEvent.BossBarColor.PURPLE;
            else if (fmt == ChatFormatting.GREEN)
                color = BossEvent.BossBarColor.GREEN;

            switch (nearestParent) {
                case UpdateEvent u -> extraDesc = Component.literal(" | ").append(MTRComponent.translatable("mtr.scoreboard.event.update." + u.getUpdateName(), u.getUpdateName()));
                case QueueEvent q -> extraDesc = Component.literal(" | ").append(MTRComponent.translatable("mtr.scoreboard.event.queue." + q.getQueueName(), q.getQueueName()));
                case BlockEntityTickEvent be -> extraDesc = Component.literal(" | ").append(MTRComponent.translatable("mtr.scoreboard.event.leaf.blockentitytick", "Block Entity Tick"));
                case EntityTickEvent et -> extraDesc = Component.literal(" | ").append(MTRComponent.translatable("mtr.scoreboard.event.leaf.entitytick", "Entity Tick"));
                case PhaseEvent p -> extraDesc = Component.literal(" | ").append(MTRComponent.translatable("mtr.scoreboard.event.phase." + p.getPhaseName(), p.getPhaseName()));
                default -> {
                }
            }
        }

        bossBar.setColor(color);
        bossBar.setName(MTRComponent.translatable(
            "mtr.bossbar.title.playing",
            "Replay [%s] Tick: %d / %d | Step: %d / %d%s",
            currentProfile.getName(), currentVirtualTick, totalTick, actionCursor, flatActions.size(), extraDesc
        ));
        bossBar.setProgress((float) actionCursor / flatActions.size());
    }

    private static void renderCurrentState(ServerLevel level, int startCursor) {
        MarkerManager.clearAll(level);

        PistonDisplayManager.beginRenderPistons();

        int maxLen = Math.min(actionCursor, flatActions.size());
        for (int i = 0; i < maxLen; i++) {
            ReplayAction a = flatActions.get(i);
            if (a.type != ActionType.LEAF) continue;
            if (a.event instanceof MovingPistonEvent mpe) {
                mpe.display(resolveLevel(level, mpe));
            } else if (a.event instanceof MovingPistonTickEvent mpte) {
                mpte.display(resolveLevel(level, mpte));
            }
        }

        List<MTREvent> activeStack = new ArrayList<>();
        for (int i = 0; i < maxLen; i++) {
            ReplayAction a = flatActions.get(i);
            if (a.type == ActionType.ENTER)
                activeStack.add(a.event);
            else if (a.type == ActionType.EXIT)
                activeStack.remove(a.event);
        }

        Set<BlockPos> innerOccupied = new HashSet<>();
        Map<BlockPos, Integer> pillarCounts = new HashMap<>();

        MTREvent exitEvent = null;
        MTREvent leafEvent = null;

        if (!level.getServer().getGameRules().get(MTRGameRules.SKIP_DELTA_CHANGES) && startCursor != actionCursor) {
            int min = Math.min(startCursor, actionCursor);
            int max = Math.max(startCursor, actionCursor);
            for (int i = min; i < max; i++) {
                ReplayAction currentAction = flatActions.get(i);
                if (currentAction.type == ActionType.LEAF) {
                    MTREvent evt = currentAction.event;
                    if (evt instanceof BlockPosEvent b) {
                        BlockPos posKey = new BlockPos(b.getX(), b.getY(), b.getZ());
                        if (innerOccupied.add(posKey) && i != actionCursor - 1) {
                            b.display(resolveLevel(level, b));
                        }
                    } else if (evt instanceof Vec3PosEvent v) {
                        BlockPos posKey = new BlockPos((int) v.getX(), (int) v.getY(), (int) v.getZ());
                        if (innerOccupied.add(posKey) && i != actionCursor - 1) {
                            v.display(resolveLevel(level, v));
                        }
                    }
                }
            }
            if (actionCursor > 0 && actionCursor <= flatActions.size()) {
                ReplayAction currentAction = flatActions.get(actionCursor - 1);
                if (currentAction.type == ActionType.LEAF) leafEvent = currentAction.event;
                else if (currentAction.type == ActionType.EXIT) exitEvent = currentAction.event;
            }
        } else {
            if (actionCursor > 0 && actionCursor <= flatActions.size()) {
                ReplayAction currentAction = flatActions.get(actionCursor - 1);
                if (currentAction.type == ActionType.LEAF) {
                    leafEvent = currentAction.event;
                    if (leafEvent instanceof BlockPosEvent b) {
                        innerOccupied.add(new BlockPos(b.getX(), b.getY(), b.getZ()));
                    }
                    else if (leafEvent instanceof Vec3PosEvent v)
                        innerOccupied.add(new BlockPos((int) v.getX(), (int) v.getY(), (int) v.getZ()));
                } else if (currentAction.type == ActionType.EXIT) {
                    exitEvent = currentAction.event;
                    if (exitEvent instanceof UpdateEvent u) {
                        innerOccupied.add(new BlockPos(u.getX(), u.getY(), u.getZ()));
                    } else if (exitEvent instanceof QueueEvent q) {
                        innerOccupied.add(new BlockPos(q.getX(), q.getY(), q.getZ()));
                    }
                }
            }
        }

        for (int i = activeStack.size() - 1; i >= 0; i--) {
            MTREvent parent = activeStack.get(i);
            if (parent instanceof EntityTickEvent || parent instanceof EntityMoveEvent || parent instanceof EntitySpawnEvent) {
                continue;
            }
            BlockPos pos = null;
            if (parent instanceof UpdateEvent u)
                pos = new BlockPos(u.getX(), u.getY(), u.getZ());
            else if (parent instanceof QueueEvent q)
                pos = new BlockPos(q.getX(), q.getY(), q.getZ());
            else if (parent instanceof BlockPosEvent b)
                pos = new BlockPos(b.getX(), b.getY(), b.getZ());
            else if (parent instanceof Vec3PosEvent v)
                pos = BlockPos.containing(v.getX(), v.getY(), v.getZ());

            boolean isPillar = false;
            int pCount = 0;
            if (pos != null) {
                if (innerOccupied.contains(pos)) {
                    isPillar = true;
                    pCount = pillarCounts.getOrDefault(pos, 0);
                    pillarCounts.put(pos, pCount + 1);
                }
                innerOccupied.add(pos);
            }
            renderParentMarker(resolveLevel(level, parent), parent, false, isPillar, pCount);
        }

        PistonDisplayManager.clearGlows(level);
        EntityReplayManager.clearGlows(level);

        if (leafEvent != null) {
            renderLeafMarker(resolveLevel(level, leafEvent), leafEvent);
        } else if (exitEvent != null) {
            BlockPos pos = null;
            switch (exitEvent) {
                case UpdateEvent u -> pos = new BlockPos(u.getX(), u.getY(), u.getZ());
                case QueueEvent q -> pos = new BlockPos(q.getX(), q.getY(), q.getZ());
                case BlockPosEvent b -> pos = new BlockPos(b.getX(), b.getY(), b.getZ());
                case Vec3PosEvent v -> pos = BlockPos.containing(v.getX(), v.getY(), v.getZ());
                default -> {
                }
            }

            boolean isPillar = false;
            int pCount = 0;
            if (pos != null) {
                if (innerOccupied.contains(pos)) {
                    isPillar = true;
                    pCount = pillarCounts.getOrDefault(pos, 0);
                    pillarCounts.put(pos, pCount + 1);
                }
            }
            renderParentMarker(resolveLevel(level, exitEvent), exitEvent, true, isPillar, pCount);
        }

        PistonDisplayManager.endRenderPistons(level);
    }

    private static void renderParentMarker(ServerLevel level, MTREvent event, boolean isExit, boolean isPillar,
            int pCount) {
        if (event instanceof EntityTickEvent || event instanceof EntityMoveEvent || event instanceof EntitySpawnEvent) {
            return;
        }

        Vector3f scale;
        if (isPillar) {
            float width = 0.1f + pCount * 0.15f;
            float height = 1.1f + pCount * 0.1f;
            scale = new Vector3f(width, height, width);
        } else {
            scale = new Vector3f(1.005f, 1.005f, 1.005f);
        }

        BlockPos pos = null;
        if (event instanceof UpdateEvent u)
            pos = new BlockPos(u.getX(), u.getY(), u.getZ());
        else if (event instanceof QueueEvent q)
            pos = new BlockPos(q.getX(), q.getY(), q.getZ());
        else if (event instanceof BlockPosEvent b)
            pos = new BlockPos(b.getX(), b.getY(), b.getZ());
        else if (event instanceof Vec3PosEvent v)
            pos = BlockPos.containing(v.getX(), v.getY(), v.getZ());

        if (pos != null) {
            if (isExit) {
                MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(pos),
                        Blocks.GRAY_STAINED_GLASS.defaultBlockState(), scale, ChatFormatting.GRAY);
            } else {
                ChatFormatting color = event.getColor();
                BlockState blockState = DisplayUtils.getGlassState(color);
                MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(pos), blockState, scale, color);
            }
        }
    }

    private static void renderLeafMarker(ServerLevel level, MTREvent event) {
        ServerLevel targetLevel = resolveLevel(level, event);
        if (event instanceof MovingPistonEvent || event instanceof MovingPistonTickEvent) {
            BlockPos pos = ((BlockPosEvent) event).getPos();
            List<UUID> uuids = PistonDisplayManager.getPistonDisplayUUIDs(pos);
            if (uuids != null) {
                for (UUID uuid : uuids) {
                    var entity = targetLevel.getEntity(uuid);
                    if (entity != null) entity.setGlowingTag(true);
                }
            }
            return;
        }

        if (event instanceof EntityMoveEvent moveEvt) {
            try {
                UUID uuid = UUID.fromString(moveEvt.getEntityUuid());
                Entity entity = EntityReplayManager.getEntity(targetLevel, uuid);
                if (entity != null) entity.setGlowingTag(true);
            } catch (Exception ignored) {}
        } else if (event instanceof EntityTickEvent tickEvt) {
            try {
                UUID uuid = UUID.fromString(tickEvt.getEntityUuid());
                Entity entity = EntityReplayManager.getEntity(targetLevel, uuid);
                if (entity != null) entity.setGlowingTag(true);
            } catch (Exception ignored) {}
        } else if (event instanceof EntitySpawnEvent spawnEvt) {
            try {
                UUID uuid = UUID.fromString(spawnEvt.getEntityUuid());
                Entity entity = EntityReplayManager.getEntity(targetLevel, uuid);
                if (entity != null) entity.setGlowingTag(true);
            } catch (Exception ignored) {}
        }

        if (event instanceof BlockPosEvent blockPosEvent) {
            blockPosEvent.display(targetLevel);
        } else if (event instanceof Vec3PosEvent vec3PosEvent) {
            vec3PosEvent.display(targetLevel);
        }
    }

    private static boolean shouldBreak(ServerLevel level, ReplayAction action, String unit, PhaseEvent targetPhase,
            MTREvent targetQueue, UpdateEvent targetUpdate) {
        switch (unit) {
            case "updates" -> {
                if (targetUpdate != null)
                    return action.update != targetUpdate;
                return action.type == ActionType.ENTER && action.event instanceof UpdateEvent;
            }
            case "queue" -> {
                if (targetQueue != null)
                    return action.queue != targetQueue;
                return action.type == ActionType.ENTER && action.event.isQueueScope();
            }
            case "phase" -> {
                if (targetPhase != null)
                    return action.phase != targetPhase;
                return action.type == ActionType.ENTER && action.event instanceof PhaseEvent;
            }
        }

        return false;
    }

    private static boolean isValidStep(ServerLevel level, ReplayAction action) {
        if (level.getServer().getGameRules().get(MTRGameRules.STEP_IGNORE_UPDATES)) {
            if (action.event instanceof UpdateEvent)
                return false;
        }
        if (level.getServer().getGameRules().get(MTRGameRules.STEP_IGNORE_EXITING)) {
            return action.type != ActionType.EXIT;
        }
        return true;
    }


    private static ServerLevel resolveLevel(ServerLevel defaultLevel, MTREvent event) {
        if (!event.hasDimension()) return defaultLevel;
        String dim = event.getDimension();
        if (dim == null || dim.isEmpty()) return defaultLevel;
        MinecraftServer server = defaultLevel.getServer();
        String[] parts = dim.split(":", 2);
        if (parts.length != 2) return defaultLevel;
        ResourceKey<Level> key = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(parts[0], parts[1])
        );
        ServerLevel resolved = server.getLevel(key);
        return resolved != null ? resolved : defaultLevel;
    }

    public static int stepForward(ServerLevel level, int amount, String unit) {
        if (currentProfile == null)
            return 0;
        actionCursor = Math.clamp(actionCursor, 0, flatActions.size());
        int taken = 0;
        int startCursor = actionCursor;

        while (taken < amount && actionCursor < flatActions.size()) {
            ReplayAction startAction = flatActions.get(actionCursor);
            PhaseEvent targetPhase = startAction.phase;
            MTREvent targetQueue = startAction.queue;
            UpdateEvent targetUpdate = startAction.update;

            while (true) {
                ReplayAction action = flatActions.get(actionCursor);
                currentVirtualTick = action.frame.getTick();
                ServerLevel actionLevel = resolveLevel(level, action.event);

                if (action.type == ActionType.LEAF) {
                    action.event.apply(actionLevel, true);
                }

                actionCursor++;

                if (unit.equals("steps")) {
                    if (isValidStep(level, action)) {
                        break;
                    }
                    if (actionCursor >= flatActions.size())
                        break;
                } else {
                    if (actionCursor >= flatActions.size())
                        break;
                    if (shouldBreak(level, flatActions.get(actionCursor), unit, targetPhase, targetQueue,
                            targetUpdate)) {
                        break;
                    }
                }
            }

            taken++;
        }

        renderCurrentState(level, startCursor);
        updateBossBar();
        updateScoreboards();
        if (taken > 0)
            level.getServer().tickRateManager().stepGameIfPaused(2);
        return taken;
    }

    public static int stepBackward(ServerLevel level, int amount, String unit) {
        if (currentProfile == null)
            return 0;
        actionCursor = Math.clamp(actionCursor, 0, flatActions.size());
        int taken = 0;
        int startCursor = actionCursor;

        while (taken < amount && actionCursor > 0) {
            ReplayAction startAction = flatActions.get(actionCursor - 1);
            PhaseEvent targetPhase = startAction.phase;
            MTREvent targetQueue = startAction.queue;
            UpdateEvent targetUpdate = startAction.update;

            do {
                actionCursor--;
                ReplayAction action = flatActions.get(actionCursor);
                currentVirtualTick = action.frame.getTick();
                ServerLevel actionLevel = resolveLevel(level, action.event);

                if (action.type == ActionType.LEAF) {
                    action.event.apply(actionLevel, false);
                }

                if (actionCursor == 0)
                    break;

                if (unit.equals("steps")) {
                    ReplayAction prevAction = flatActions.get(actionCursor - 1);
                    if (isValidStep(level, prevAction))
                        break;
                } else {
                    ReplayAction prevAction = flatActions.get(actionCursor - 1);
                    if (shouldBreak(level, prevAction, unit, targetPhase, targetQueue, targetUpdate))
                        break;
                }

            } while (actionCursor > 0);

            taken++;
        }

        renderCurrentState(level, startCursor);
        updateBossBar();
        updateScoreboards();
        if (taken > 0)
            level.getServer().tickRateManager().stepGameIfPaused(2);
        return taken;
    }

    public static int jumpToStep(ServerLevel level, int targetVisibleStep) {
        if (currentProfile == null)
            return 0;
        actionCursor = Math.clamp(actionCursor, 0, flatActions.size());

        int targetCursor = -1;
        for (int i = 0; i < flatActions.size(); i++) {
            if (flatActions.get(i).visibleStepIndex == targetVisibleStep) {
                targetCursor = i + 1;
                break;
            }
        }

        if (targetCursor == -1) {
            return 0;
        }

        int startCursor = actionCursor;

        while (actionCursor < targetCursor) {
            ReplayAction action = flatActions.get(actionCursor);
            if (action.type == ActionType.LEAF) {
                action.event.apply(resolveLevel(level, action.event), true);
            }
            actionCursor++;
        }

        while (actionCursor > targetCursor) {
            actionCursor--;
            ReplayAction action = flatActions.get(actionCursor);
            if (action.type == ActionType.LEAF) {
                action.event.apply(resolveLevel(level, action.event), false);
            }
        }

        if (actionCursor > 0) {
            currentVirtualTick = flatActions.get(actionCursor - 1).frame.getTick();
        } else if (!flatActions.isEmpty()) {
            currentVirtualTick = flatActions.getFirst().frame.getTick();
        }

        renderCurrentState(level, startCursor);
        updateBossBar();
        updateScoreboards();
        level.getServer().tickRateManager().stepGameIfPaused(2);
        
        return Math.abs(actionCursor - startCursor);
    }

    public static int tickForward(ServerLevel level, int ticks) {
        if (currentProfile == null)
            return 0;
        actionCursor = Math.clamp(actionCursor, 0, flatActions.size());
        long targetTick = currentVirtualTick + ticks;
        long startTick = currentVirtualTick;
        int takenActions = 0;
        int startCursor = actionCursor;

        while (actionCursor < flatActions.size()) {
            ReplayAction nextAction = flatActions.get(actionCursor);
            if (nextAction.frame.getTick() > targetTick)
                break;

            ReplayAction action = flatActions.get(actionCursor);
            currentVirtualTick = action.frame.getTick();

            if (action.type == ActionType.LEAF) {
                action.event.apply(resolveLevel(level, action.event), true);
            }

            actionCursor++;
            takenActions++;
        }

        renderCurrentState(level, startCursor);
        updateBossBar();
        updateScoreboards();
        if (takenActions > 0)
            level.getServer().tickRateManager().stepGameIfPaused(2);
        return (int) (currentVirtualTick - startTick);
    }

    public static int tickBackward(ServerLevel level, int ticks) {
        if (currentProfile == null)
            return 0;
        actionCursor = Math.clamp(actionCursor, 0, flatActions.size());
        long targetTick = Math.max(0, currentVirtualTick - ticks);
        long startTick = currentVirtualTick;
        int takenActions = 0;
        int startCursor = actionCursor;

        while (actionCursor > 0) {
            ReplayAction prevAction = flatActions.get(actionCursor - 1);
            if (prevAction.frame.getTick() < targetTick)
                break;

            actionCursor--;
            ReplayAction action = flatActions.get(actionCursor);
            currentVirtualTick = action.frame.getTick();

            if (action.type == ActionType.LEAF) {
                action.event.apply(resolveLevel(level, action.event), false);
            }

            takenActions++;
        }

        renderCurrentState(level, startCursor);
        updateBossBar();
        updateScoreboards();
        if (takenActions > 0)
            level.getServer().tickRateManager().stepGameIfPaused(2);
        return (int) (startTick - currentVirtualTick);
    }
}
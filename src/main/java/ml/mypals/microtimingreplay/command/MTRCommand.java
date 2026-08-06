package ml.mypals.microtimingreplay.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.replay.dialog.StackTraceScreenGenerator;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import ml.mypals.microtimingreplay.replay.ReplayEngine;
import ml.mypals.microtimingreplay.replay.WorldBackupManager;
import ml.mypals.microtimingreplay.replay.dialog.TimelineScreenGenerator;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

public class MTRCommand {

    private static String currentInfoProfile = null;

    public static String getCurrentInfoProfile() {
        return currentInfoProfile;
    }

    public static final SuggestionProvider<CommandSourceStack> PROFILE_SUGGESTION = (context, builder) -> SharedSuggestionProvider.suggest(ProfileManager.listProfiles(), builder);

    public static final SuggestionProvider<CommandSourceStack> AREA_SUGGESTION = (context, builder) -> {
        try {
            String profileName = StringArgumentType.getString(context, "name");
            return SharedSuggestionProvider.suggest(ProfileManager.listAreaNames(profileName), builder);
        } catch (IllegalArgumentException ignored) {

        }
        return Suggestions.empty();
    };

    public static final SuggestionProvider<CommandSourceStack> UNIT_SUGGESTION =
            (context, builder) -> SharedSuggestionProvider.suggest(ReplayEngine.STEP_UNITS, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("mtr")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
            .then(Commands.literal("profile")
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(MTRCommand::createProfile)))
                .then(Commands.literal("delete")
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                        .executes(MTRCommand::deleteProfile)))
                .then(Commands.literal("info")
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                        .executes(MTRCommand::infoProfile)))
                .then(Commands.literal("area")
                    .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                            .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                    .executes(c -> addArea(c, null))
                                    .then(Commands.argument("area_name", StringArgumentType.word())
                                        .executes(c -> addArea(c, StringArgumentType.getString(c, "area_name"))))))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                            .then(Commands.argument("area_name", StringArgumentType.word()).suggests(AREA_SUGGESTION)
                                .executes(MTRCommand::removeArea))))
                    .then(Commands.literal("modify")
                        .then(Commands.literal("pos")
                            .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                                .then(Commands.argument("area_name", StringArgumentType.word()).suggests(AREA_SUGGESTION)
                                    .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                            .executes(MTRCommand::modifyAreaPos))))))
                        .then(Commands.literal("rename")
                            .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                                .then(Commands.argument("area_name", StringArgumentType.word()).suggests(AREA_SUGGESTION)
                                    .then(Commands.argument("new_name", StringArgumentType.word())
                                        .executes(MTRCommand::renameArea))))))
                )
            )
            .then(Commands.literal("record")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(MTRCommand::startRecord)))
                .then(Commands.literal("stop")
                    .executes(MTRCommand::stopRecord))
                .then(Commands.literal("clear")
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                        .executes(MTRCommand::clearRecord)))
            )
            .then(Commands.literal("replay")
                .then(Commands.literal("start")
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PROFILE_SUGGESTION)
                        .executes(MTRCommand::startReplay)))
                .then(Commands.literal("stop")
                    .executes(MTRCommand::stopReplay))
                .then(Commands.literal("subscribe")
                    .executes(MTRCommand::subscribeReplay))
                .then(Commands.literal("unsubscribe")
                    .executes(MTRCommand::unsubscribeReplay))
                .then(Commands.literal("screen")
                    .executes(c -> openReplayScreen(c, 0))
                    .then(Commands.argument("page", IntegerArgumentType.integer(0))
                        .executes(c -> openReplayScreen(c, IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("forward")
                    .then(Commands.argument("unit", StringArgumentType.word())
                        .suggests(UNIT_SUGGESTION)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(c -> executeForward(c, IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "unit"))))
                        .executes(c -> executeForward(c, 1, StringArgumentType.getString(c, "unit")))))
                .then(Commands.literal("backward")
                    .then(Commands.argument("unit", StringArgumentType.word())
                        .suggests(UNIT_SUGGESTION)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(c -> executeBackward(c, IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "unit"))))
                        .executes(c -> executeBackward(c, 1, StringArgumentType.getString(c, "unit")))))
                .then(Commands.literal("jump")
                    .then(Commands.argument("step", IntegerArgumentType.integer(1))
                        .executes(c -> executeJump(c, IntegerArgumentType.getInteger(c, "step")))))
                .then(Commands.literal("dump")
                    .then(Commands.argument("step", IntegerArgumentType.integer(1))
                        .executes(c -> executeDumpStackTrace(c, IntegerArgumentType.getInteger(c, "step")))))
                .then(Commands.literal("auto")
                    .then(Commands.literal("stop")
                        .executes(MTRCommand::stopAutoReplay))
                    .then(Commands.argument("direction", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"forward", "backward"}, builder))
                        .then(Commands.argument("unit", StringArgumentType.word())
                            .suggests(UNIT_SUGGESTION)
                            .then(Commands.argument("delay", IntegerArgumentType.integer(1))
                                .then(Commands.argument("steps", IntegerArgumentType.integer(1))
                                    .executes(c -> executeAutoReplay(c,
                                            StringArgumentType.getString(c, "direction"),
                                            StringArgumentType.getString(c, "unit"),
                                            IntegerArgumentType.getInteger(c, "delay"),
                                            IntegerArgumentType.getInteger(c, "steps")
                                    )))))))
            )
        );
    }

    private static void sendReplayFeedback(CommandSourceStack source, Component message) {
        if (source.isPlayer()) {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                player.sendSystemMessage(message, true);
                return;
            }
        }
        source.sendSuccess(() -> message, true);
    }

    private static int createProfile(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (ProfileManager.hasProfile(name)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.already_exists", "Profile already exists: %s", name));
            return 0;
        }

        MTRProfile profile = new MTRProfile(name);
        ProfileManager.saveProfile(profile);

        context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.profile.created", "Profile created: %s", name), true);
        return infoProfile(context);
    }

    private static int deleteProfile(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!ProfileManager.hasProfile(name)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }

        if (ProfileManager.deleteProfile(name)) {
            context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.profile.deleted", "Profile deleted: %s", name), true);
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.delete_failed", "Failed to delete profile: %s", name));
            return 0;
        }
    }

        private static int infoProfile(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile != null) {
            ServerLevel level = context.getSource().getLevel();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Display) {
                    if (entity.entityTags().contains("mtr_area_marker")) {
                        entity.discard();
                    }
                }
            }

            if (name.equals(currentInfoProfile)) {
                currentInfoProfile = null;
                context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.profile.hid_markers", "Hid area markers for profile: %s", name), false);
            } else {
                currentInfoProfile = name;
                String currentDim = level.dimension().identifier().toString();
                for (MTRProfile.Area area : profile.getAreas()) {
                    if (area.dimension.equals(currentDim)) {
                        MTRMarker.spawnAreaMarker(level, area);
                    }
                }
                
                context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.profile.info", "Profile: %s\n- Created: %d\n- Ticks: %d\n- Frames: %d\n- Areas: %d", profile.getName(), profile.getCreatedAt(), profile.getTicksRecorded(), profile.getFrames().size(), profile.getAreas().size()), false);
            }
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }
    }

    public static void refreshAreaMarkers(ServerLevel level, MTRProfile profile) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Display) {
                if (entity.entityTags().contains("mtr_area_marker")) {
                    entity.discard();
                }
            }
        }
        String currentDim = level.dimension().identifier().toString();
        for (MTRProfile.Area area : profile.getAreas()) {
            if (area.dimension.equals(currentDim)) {
                MTRMarker.spawnAreaMarker(level, area);
            }
        }
    }

    private static int addArea(CommandContext<CommandSourceStack> context, String areaName) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }

        BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(context, "pos2");

        String assignedName = profile.addArea(areaName, pos1, pos2, context.getSource().getLevel().dimension().identifier().toString());
        if (assignedName != null) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.area.added", "Area added to profile '%s' with ID/Name: %s", name, assignedName), true);
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.area.add_failed", "Failed to add area. Name '%s' may already exist.", areaName));
            return 0;
        }
    }

    private static int removeArea(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String areaName = StringArgumentType.getString(context, "area_name");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }

        if (profile.removeArea(areaName)) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.area.removed_from_profile", "Area '%s' removed from profile '%s'", areaName, name), true);
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.area.not_found", "Area not found: %s", areaName));
            return 0;
        }
    }

    private static int modifyAreaPos(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String areaName = StringArgumentType.getString(context, "area_name");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }

        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "pos2");

        if (profile.modifyAreaPos(areaName, pos1, pos2)) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.area.modified_coords", "Area '%s' coordinates updated.", areaName), true);
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.area.not_found", "Area not found: %s", areaName));
            return 0;
        }
    }

    private static int renameArea(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String areaName = StringArgumentType.getString(context, "area_name");
        String newName = StringArgumentType.getString(context, "new_name");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }

        if (profile.renameArea(areaName, newName)) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.area.renamed", "Area '%s' renamed to '%s'", areaName, newName), true);
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.area.rename_failed", "Failed to rename area. Name '%s' may already exist.", newName));
            return 0;
        }
    }

    private static int startRecord(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int advance = IntegerArgumentType.getInteger(context, "ticks");
        
        if (!MTRState.startRecording(name, context.getSource().getServer(), advance)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.record.start_failed", "Failed to start recording (Already running or profile not found)"));
            return 0;
        }

        context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.record.started_ticks", "Started recording for %d ticks", advance), true);
        if (!MTRState.isTimeFrozen(context.getSource().getServer())) {
            context.getSource().sendSystemMessage(MTRComponent.translatable(
                    "commands.mtr.record.not_frozen_hint",
                    "Time is not frozen, so the world was not stepped. Recording is running and will stop on schedule; run /tick freeze first for a controlled capture."));
        }
        return 1;
    }

    private static int clearRecord(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        
        if (profile == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }
        
        if (MTRState.getCurrentState() != MTRState.State.IDLE && MTRState.getActiveProfile() != null && MTRState.getActiveProfile().getName().equals(name)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.record.cannot_clear_active", "Cannot clear profile while it is active (recording or replaying)"));
            return 0;
        }
        
        profile.getFrames().clear();
        profile.setTicksRecorded(0);
        ProfileManager.saveProfile(profile);
        WorldBackupManager.deleteBackups(profile.getName());
        
        context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.record.cleared", "Record cleared for profile: %s", name), true);
        return 1;
    }

    private static int stopRecord(CommandContext<CommandSourceStack> context) {
        if (MTRState.getCurrentState() == MTRState.State.RECORDING) {
            MTRProfile profile = MTRState.getActiveProfile();
            String profileName = profile != null ? profile.getName() : "unknown";
            MTRState.stopRecording();
            context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.record.stopped_profile", "Stopped recording profile: %s", profileName), true);
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.record.not_recording", "Not currently recording."));
            return 0;
        }
    }

    private static int startReplay(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.profile.not_found", "Profile not found: %s", name));
            return 0;
        }

        if (MTRState.startReplaying(name)) {
            ReplayEngine.startReplay(profile, context.getSource().getLevel());

            ReplayEngine.subscribe(context.getSource().getPlayer());

            sendReplayFeedback(context.getSource(), MTRComponent.translatable("commands.mtr.replay.started", "Started replaying profile: %s", name));
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.cannot_start_state", "Cannot start replay. Current state: %s", MTRState.getCurrentState().toString()));
            return 0;
        }
    }

    private static int stopReplay(CommandContext<CommandSourceStack> context) {
        if (MTRState.getCurrentState() == MTRState.State.REPLAYING) {
            MTRProfile profile = MTRState.getActiveProfile();
            String profileName = profile != null ? profile.getName() : "unknown";

            MTRState.stopReplaying();
            ReplayEngine.stopReplay();

            sendReplayFeedback(context.getSource(), MTRComponent.translatable("commands.mtr.replay.stopped", "Stopped replaying profile: %s", profileName));
            return 1;
        } else {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }
    }

    private static int subscribeReplay(CommandContext<CommandSourceStack> context) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.no_active_subscribe", "No active replay to subscribe to."));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.player_only", "This command can only be executed by a player."));
            return 0;
        }
        ReplayEngine.subscribe(player);
        context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.replay.subscribed_bossbar", "Subscribed to BossBar."), false);
        return 1;
    }

    private static int executeBackward(CommandContext<CommandSourceStack> context, int num, String unit) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }
        if (ReplayEngine.isUnitInvalid(unit)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.unknown_unit", "Unknown unit: %s", unit));
            return 0;
        }

        int taken = 0;
        if (unit.equals("ticks")) {
            taken = ReplayEngine.tickBackward(context.getSource().getLevel(), num);
        } else {
            taken = ReplayEngine.stepBackward(context.getSource().getLevel(), num, unit);
        }
        
        int finalTaken = taken;
        sendReplayFeedback(context.getSource(), MTRComponent.translatable("commands.mtr.replay.stepped_backward_unit", "Replayed backward %d %s", finalTaken, unit));
        return 1;
    }

    private static int executeForward(CommandContext<CommandSourceStack> context, int num, String unit) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }
        if (ReplayEngine.isUnitInvalid(unit)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.unknown_unit", "Unknown unit: %s", unit));
            return 0;
        }

        int taken;
        if (unit.equals("ticks")) {
            taken = ReplayEngine.tickForward(context.getSource().getLevel(), num);
        } else {
            taken = ReplayEngine.stepForward(context.getSource().getLevel(), num, unit);
        }
        
        int finalTaken = taken;
        sendReplayFeedback(context.getSource(), MTRComponent.translatable("commands.mtr.replay.stepped_forward_unit", "Replayed forward %d %s", finalTaken, unit));
        return 1;
    }

    private static int openReplayScreen(CommandContext<CommandSourceStack> context, int page) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.player_only", "This command can only be executed by a player."));
            return 0;
        }
        TimelineScreenGenerator.openTimeline(player, page);
        return 1;
    }
    private static int unsubscribeReplay(CommandContext<CommandSourceStack> context) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.no_active_unsubscribe", "No active replay to unsubscribe from."));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.player_only", "This command can only be executed by a player."));
            return 0;
        }
        ReplayEngine.unsubscribe(player);
        context.getSource().sendSuccess(() -> MTRComponent.translatable("commands.mtr.replay.unsubscribed_bossbar", "Unsubscribed from BossBar."), false);
        return 1;
    }

    private static int executeJump(CommandContext<CommandSourceStack> context, int targetStep) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }

        int taken = ReplayEngine.jumpToStep(context.getSource().getLevel(), targetStep);
        sendReplayFeedback(context.getSource(), MTRComponent.translatable("commands.mtr.replay.jumped", "Replay jumped to step %d (delta %d actions)", targetStep, taken));
        return 1;
    }

    private static int executeAutoReplay(CommandContext<CommandSourceStack> context, String direction, String unit, int delay, int steps) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }

        if (!direction.equalsIgnoreCase("forward") && !direction.equalsIgnoreCase("backward")) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.invalid_direction", "Invalid direction: %s. Use 'forward' or 'backward'.", direction));
            return 0;
        }

        if (ReplayEngine.isUnitInvalid(unit)) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.unknown_unit", "Unknown unit: %s", unit));
            return 0;
        }

        ReplayEngine.startAutoReplay(direction, unit, delay, steps);

        sendReplayFeedback(context.getSource(), MTRComponent.translatable(
                "commands.mtr.replay.auto_started",
                "Auto replay started: %s %d step(s) with unit '%s' every %d tick(s).",
                direction, steps, unit, delay
        ));

        return 1;
    }

    private static int stopAutoReplay(CommandContext<CommandSourceStack> context) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay."));
            return 0;
        }

        if (!ReplayEngine.isAutoReplayActive()) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.replay.auto_not_running", "No active auto replay running."));
            return 0;
        }

        ReplayEngine.stopAutoReplay();
        sendReplayFeedback(context.getSource(), MTRComponent.translatable(
                "commands.mtr.replay.auto_stopped",
                "Auto replay stopped."
        ));
        return 1;
    }

    private static int executeDumpStackTrace(CommandContext<CommandSourceStack> context, int step) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MTRComponent.translatable("commands.mtr.player_only", "This command can only be executed by a player."));
            return 0;
        }
        StackTraceScreenGenerator.openStackTrace(player, step);
        return 1;
    }
}

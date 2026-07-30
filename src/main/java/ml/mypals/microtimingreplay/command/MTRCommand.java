package ml.mypals.microtimingreplay.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import ml.mypals.microtimingreplay.replay.ReplayEngine;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MTRCommand {

    public static final SuggestionProvider<CommandSourceStack> PROFILE_SUGGESTION = (context, builder) -> {
        return SharedSuggestionProvider.suggest(ProfileManager.listProfiles(), builder);
    };

    public static final SuggestionProvider<CommandSourceStack> AREA_SUGGESTION = (context, builder) -> {
        try {
            String profileName = StringArgumentType.getString(context, "name");
            MTRProfile profile = ProfileManager.loadProfile(profileName);
            if (profile != null) {
                return SharedSuggestionProvider.suggest(profile.getAreas().stream().map(a -> a.name), builder);
            }
        } catch (IllegalArgumentException ignored) {
            // "name" might not be parsed yet
        }
        return Suggestions.empty();
    };

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
                .then(Commands.literal("forward")
                    .then(Commands.literal("steps")
                        .then(Commands.argument("num", IntegerArgumentType.integer(1))
                            .executes(c -> replayAction(c, true, true))))
                    .then(Commands.literal("ticks")
                        .then(Commands.argument("num", IntegerArgumentType.integer(1))
                            .executes(c -> replayAction(c, true, false)))))
                .then(Commands.literal("backward")
                    .then(Commands.literal("steps")
                        .then(Commands.argument("num", IntegerArgumentType.integer(1))
                            .executes(c -> replayAction(c, false, true))))
                    .then(Commands.literal("ticks")
                        .then(Commands.argument("num", IntegerArgumentType.integer(1))
                            .executes(c -> replayAction(c, false, false)))))
            )
        );
    }

    private static int createProfile(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (ProfileManager.hasProfile(name)) {
            context.getSource().sendFailure(Component.literal("Profile already exists: " + name));
            return 0;
        }

        MTRProfile profile = new MTRProfile(name);
        ProfileManager.saveProfile(profile);

        context.getSource().sendSuccess(() -> Component.literal("Profile created: " + name), true);
        return 1;
    }

    private static int deleteProfile(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!ProfileManager.hasProfile(name)) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        if (ProfileManager.deleteProfile(name)) {
            context.getSource().sendSuccess(() -> Component.literal("Profile deleted: " + name), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to delete profile: " + name));
            return 0;
        }
    }

    private static int infoProfile(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile != null) {
            context.getSource().sendSuccess(() -> Component.literal(
                String.format("Profile: %s\nCreated: %d\nTicks: %d\nFrames: %d\nAreas: %d", 
                    profile.getName(), profile.getCreatedAt(), profile.getTicksRecorded(), 
                    profile.getFrames().size(), profile.getAreas().size())
            ), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }
    }

    private static int addArea(CommandContext<CommandSourceStack> context, String areaName) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(context, "pos2");

        String assignedName = profile.addArea(areaName, pos1, pos2);
        if (assignedName != null) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> Component.literal("Area added to profile '" + name + "' with ID/Name: " + assignedName), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to add area. Name '" + areaName + "' may already exist."));
            return 0;
        }
    }

    private static int removeArea(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String areaName = StringArgumentType.getString(context, "area_name");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        if (profile.removeArea(areaName)) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> Component.literal("Area '" + areaName + "' removed from profile '" + name + "'"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Area not found: " + areaName));
            return 0;
        }
    }

    private static int modifyAreaPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        String areaName = StringArgumentType.getString(context, "area_name");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        BlockPos pos1 = BlockPosArgument.getBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgument.getBlockPos(context, "pos2");

        if (profile.modifyAreaPos(areaName, pos1, pos2)) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> Component.literal("Area '" + areaName + "' coordinates updated."), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Area not found: " + areaName));
            return 0;
        }
    }

    private static int renameArea(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String areaName = StringArgumentType.getString(context, "area_name");
        String newName = StringArgumentType.getString(context, "new_name");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        if (profile.renameArea(areaName, newName)) {
            ProfileManager.saveProfile(profile);
            context.getSource().sendSuccess(() -> Component.literal("Area '" + areaName + "' renamed to '" + newName + "'"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to rename area. Area may not exist or new name is already taken."));
            return 0;
        }
    }

    private static int startRecord(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        if (MTRState.startRecording(name,context.getSource().getServer(),ticks)) {
            context.getSource().sendSuccess(() -> Component.literal("Started recording profile: " + name + " for " + ticks + " ticks."), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Cannot start recording. Current state: " + MTRState.getCurrentState()));
            return 0;
        }
    }

    private static int stopRecord(CommandContext<CommandSourceStack> context) {
        if (MTRState.getCurrentState() == MTRState.State.RECORDING) {
            MTRProfile profile = MTRState.getActiveProfile();
            String profileName = profile != null ? profile.getName() : "unknown";
            MTRState.stopRecording();
            context.getSource().sendSuccess(() -> Component.literal("Stopped recording profile: " + profileName), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Not currently recording."));
            return 0;
        }
    }

    private static int startReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MTRProfile profile = ProfileManager.loadProfile(name);
        if (profile == null) {
            context.getSource().sendFailure(Component.literal("Profile not found: " + name));
            return 0;
        }

        if (MTRState.startReplaying(name)) {
            context.getSource().getServer().tickRateManager().setFrozen(true);
            ReplayEngine.startReplay(profile, context.getSource().getLevel());
            subscribeReplay(context);
            context.getSource().sendSuccess(() -> Component.literal("Started replaying profile: " + name + ", (freezing the time...)"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Cannot start replay. Current state: " + MTRState.getCurrentState()));
            return 0;
        }
    }

    private static int stopReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (MTRState.getCurrentState() == MTRState.State.REPLAYING) {
            context.getSource().getServer().tickRateManager().setFrozen(false);
            unsubscribeReplay(context);
            MTRProfile profile = MTRState.getActiveProfile();
            String profileName = profile != null ? profile.getName() : "unknown";
            MTRState.stopReplaying();
            ReplayEngine.stopReplay();
            context.getSource().sendSuccess(() -> Component.literal("Stopped replaying profile: " + profileName + ", (unfroze the time...)"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Not currently replaying."));
            return 0;
        }
    }

    private static int subscribeReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(Component.literal("No active replay to subscribe to."));
            return 0;
        }
        ReplayEngine.subscribe(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.literal("Subscribed to BossBar."), false);
        return 1;
    }

    private static int unsubscribeReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(Component.literal("No active replay to unsubscribe from."));
            return 0;
        }
        ReplayEngine.unsubscribe(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.literal("Unsubscribed from BossBar."), false);
        return 1;
    }

    private static int replayAction(CommandContext<CommandSourceStack> context, boolean forward, boolean isSteps) {
        if (MTRState.getCurrentState() != MTRState.State.REPLAYING) {
            context.getSource().sendFailure(Component.literal("You must start a replay first."));
            return 0;
        }
        
        int num = IntegerArgumentType.getInteger(context, "num");
        int taken = 0;
        
        if (forward) {
            if (isSteps) {
                taken = ReplayEngine.stepForward(context.getSource().getLevel(), num);
            } else {
                taken = ReplayEngine.tickForward(context.getSource().getLevel(), num);
            }
        } else {
            if (isSteps) {
                taken = ReplayEngine.stepBackward(context.getSource().getLevel(), num);
            } else {
                taken = ReplayEngine.tickBackward(context.getSource().getLevel(), num);
            }
        }
        
        int finalTaken = taken;
        String dir = forward ? "Forward" : "Backward";
        String type = isSteps ? "steps" : "ticks";
        context.getSource().sendSuccess(() -> Component.literal(String.format("Replay moved %s %d %s.", dir, finalTaken, type)), true);
        
        return 1;
    }
}

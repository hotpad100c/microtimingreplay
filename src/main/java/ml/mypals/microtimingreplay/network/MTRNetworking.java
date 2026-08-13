package ml.mypals.microtimingreplay.network;

import ml.mypals.microtimingreplay.config.RecordingEventRegistry;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.replay.PlayerPositioner;
import ml.mypals.microtimingreplay.replay.ReplayContext;
import ml.mypals.microtimingreplay.replay.ReplayManager;
import ml.mypals.microtimingreplay.replay.ReplaySession;
import ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server half of the client add-on protocol: payload registration, the receive
 * handlers, and the push helpers the replay engine calls when state moves.
 *
 * <p>Every handler that changes something re-checks {@link Permissions#COMMANDS_ADMIN}.
 * The command tree gates on it too, but packets do not go through the command tree —
 * without this any client could drive somebody else's replay.
 */
public class MTRNetworking {

    /** A replay is big, but not unbounded-big; refuse to serialise past this. */
    private static final int MAX_TIMELINE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_DETAILS_BYTES = 4 * 1024 * 1024;

    /** Keeps one scroll gesture from asking the server for arbitrarily much work. */
    private static final int MAX_STEP_AMOUNT = 4096;

    /**
     * Runs on both sides — the mod's main entrypoint is environment-agnostic, and both
     * ends must agree on the type table before any handler is registered.
     */
    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.HelloC2S.TYPE, MTRPayloads.HelloC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.SubscribeC2S.TYPE, MTRPayloads.SubscribeC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.RequestTimelineC2S.TYPE, MTRPayloads.RequestTimelineC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.RequestDetailsC2S.TYPE, MTRPayloads.RequestDetailsC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.StepC2S.TYPE, MTRPayloads.StepC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.JumpC2S.TYPE, MTRPayloads.JumpC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.SetCameraFollowC2S.TYPE, MTRPayloads.SetCameraFollowC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.RequestFilterC2S.TYPE, MTRPayloads.RequestFilterC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.SetFilterC2S.TYPE, MTRPayloads.SetFilterC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MTRPayloads.ResetFilterC2S.TYPE, MTRPayloads.ResetFilterC2S.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(MTRPayloads.HelloS2C.TYPE, MTRPayloads.HelloS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MTRPayloads.SessionsS2C.TYPE, MTRPayloads.SessionsS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MTRPayloads.CursorS2C.TYPE, MTRPayloads.CursorS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MTRPayloads.FilterS2C.TYPE, MTRPayloads.FilterS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MTRPayloads.OpenScreenS2C.TYPE, MTRPayloads.OpenScreenS2C.CODEC);
        // Both blow past the 1 MiB vanilla payload cap; Fabric splits these for us.
        PayloadTypeRegistry.clientboundPlay().registerLarge(MTRPayloads.TimelineS2C.TYPE, MTRPayloads.TimelineS2C.CODEC, MAX_TIMELINE_BYTES);
        PayloadTypeRegistry.clientboundPlay().registerLarge(MTRPayloads.DetailsS2C.TYPE, MTRPayloads.DetailsS2C.CODEC, MAX_DETAILS_BYTES);
    }

    public static void registerServerHandlers() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Restore before they go: game mode is saved to player data, so a player who
            // logs out mid-follow would otherwise come back stuck in spectator.
            PlayerPositioner.onDisconnect(handler.getPlayer());
            MTRClientTracker.forget(handler.getPlayer());
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.HelloC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MTRClientTracker.onHello(player, payload.protocol());
            ServerPlayNetworking.send(player, new MTRPayloads.HelloS2C(MTRPayloads.PROTOCOL_VERSION, isAllowed(player)));
            sendSessions(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.SubscribeC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!isAllowed(player)) return;

            if (payload.profile().isEmpty()) {
                ReplayManager.unsubscribe(player);
            } else {
                ReplayManager.subscribe(player, payload.profile());
            }
            sendSessions(player);
            sendTimeline(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.RequestTimelineC2S.TYPE,
                (payload, context) -> sendTimeline(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.RequestDetailsC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ReplaySession session = ReplayManager.subscribedSession(player);
            if (session == null) return;
            sendDetails(player, session, payload.step());
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.StepC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!isAllowed(player)) return;
            if (ReplaySession.isUnitInvalid(payload.unit())) return;

            ReplaySession session = ReplayManager.subscribedSession(player);
            if (session == null || !session.isRunning()) return;

            session.advance(player.level(), Math.clamp(payload.amount(), 1, MAX_STEP_AMOUNT),
                    payload.unit(), payload.forward());
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.JumpC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!isAllowed(player)) return;

            ReplaySession session = ReplayManager.subscribedSession(player);
            if (session == null || !session.isRunning()) return;

            session.jumpToStep(player.level(), payload.step());
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.SetCameraFollowC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!isAllowed(player)) return;

            if (!payload.enabled()) {
                PlayerPositioner.disable(player);
            } else {
                ReplaySession session = ReplayManager.subscribedSession(player);
                // Following nothing would just strand them in spectator.
                if (session == null || !session.isRunning()) return;

                PlayerPositioner.enable(player);
                PlayerPositioner.focusOnCursor(player, session, player.level());
            }
            sendSessions(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.RequestFilterC2S.TYPE,
                (payload, context) -> sendFilter(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.SetFilterC2S.TYPE, (payload, context) -> {
            if (!isAllowed(context.player())) return;
            if (RecordingEventRegistry.get(payload.eventId()) == null) return;

            RecordingFilterConfig.setEnabled(payload.eventId(), payload.enabled());
            broadcastFilter(context.server());
        });

        ServerPlayNetworking.registerGlobalReceiver(MTRPayloads.ResetFilterC2S.TYPE, (payload, context) -> {
            if (!isAllowed(context.player())) return;

            RecordingFilterConfig.resetToDefaults();
            broadcastFilter(context.server());
        });
    }

    public static boolean isAllowed(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    // ── pushes ───────────────────────────────────────────────────────────────

    public static void sendSessions(ServerPlayer player) {
        if (!MTRClientTracker.hasClient(player)) return;

        ReplaySession subscribed = ReplayManager.subscribedSession(player);
        ServerPlayNetworking.send(player, new MTRPayloads.SessionsS2C(
                ReplayManager.runningProfileNames(),
                subscribed == null ? "" : subscribed.sessionId(),
                PlayerPositioner.isFollowing(player)));
    }

    public static void broadcastSessions(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSessions(player);
        }
    }

    public static void sendTimeline(ServerPlayer player) {
        if (!MTRClientTracker.hasClient(player)) return;

        ReplaySession session = ReplayManager.subscribedSession(player);
        if (session == null || !session.isRunning()) {
            ServerPlayNetworking.send(player, new MTRPayloads.TimelineS2C("", -1, List.of()));
            return;
        }

        TimelineSnapshot.Result snapshot = TimelineSnapshot.build(session);
        ServerPlayNetworking.send(player,
                new MTRPayloads.TimelineS2C(session.sessionId(), snapshot.cursorRow(), snapshot.rows()));
    }

    public static void broadcastCursor(ReplaySession session, List<ServerPlayer> subscribers) {
        if (session == null || subscribers.isEmpty()) return;

        MTRPayloads.CursorS2C payload = null;
        for (ServerPlayer player : subscribers) {
            if (!MTRClientTracker.hasClient(player)) continue;
            if (payload == null) {
                // Built lazily: most sessions have no add-on clients watching, and this
                // runs on every single step.
                ReplaySession.RowCursor cursor = session.rowCursor();
                payload = new MTRPayloads.CursorS2C(
                        session.sessionId(),
                        cursor.cursorRow(),
                        session.getCurrentVirtualTick(),
                        session.getTotalTick(),
                        cursor.totalRows());
            }
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void sendDetails(ServerPlayer player, ReplaySession session, int step) {
        if (!MTRClientTracker.hasClient(player)) return;

        Component hover = Component.empty();
        Optional<BlockPos> focus = Optional.empty();
        for (ReplaySession.ReplayAction action : session.getFlatActionsSnapshot()) {
            if (action.type() != ReplaySession.ActionType.EXIT && action.visibleStepIndex() == step) {
                hover = action.event().fillHoverText();
                // Null for events with no place in the world; the client then keeps its old focus.
                focus = Optional.ofNullable(action.event().getMarkerPos());
                break;
            }
        }

        // Traces live in a per-session map keyed off the replay context.
        List<String> trace = ReplayContext.call(session.sessionId(), () -> StackTraceManager.get(step));
        ServerPlayNetworking.send(player,
                new MTRPayloads.DetailsS2C(step, hover, trace == null ? List.of() : trace, focus));
    }

    /**
     * Serves {@code /mtr replay screen} for an add-on client: ship the data, then ask
     * for the screen. Returns false when the player has no add-on, so the caller falls
     * back to the dialog.
     */
    public static boolean openTimelineScreen(ServerPlayer player) {
        if (!MTRClientTracker.hasClient(player)) return false;

        sendTimeline(player);
        ServerPlayNetworking.send(player, new MTRPayloads.OpenScreenS2C(MTRPayloads.OpenScreenS2C.TIMELINE));
        return true;
    }

    public static boolean openFilterScreen(ServerPlayer player) {
        if (!MTRClientTracker.hasClient(player)) return false;

        sendFilter(player);
        ServerPlayNetworking.send(player, new MTRPayloads.OpenScreenS2C(MTRPayloads.OpenScreenS2C.FILTER));
        return true;
    }

    public static void sendFilter(ServerPlayer player) {
        if (!MTRClientTracker.hasClient(player)) return;

        RecordingFilterConfig.ensureLoaded();
        List<MTRPayloads.FilterRow> rows = new ArrayList<>();
        for (RecordingEventRegistry.EventEntry entry : RecordingEventRegistry.getAll()) {
            rows.add(new MTRPayloads.FilterRow(entry.id(), entry.category(), entry.defaultName(),
                    RecordingFilterConfig.isEnabled(entry.id())));
        }
        ServerPlayNetworking.send(player, new MTRPayloads.FilterS2C(rows));
    }

    /** The filter is world-wide state, so everyone looking at it should see the change. */
    private static void broadcastFilter(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendFilter(player);
        }
    }
}

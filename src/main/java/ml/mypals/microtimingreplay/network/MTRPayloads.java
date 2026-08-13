package ml.mypals.microtimingreplay.network;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.config.RecordMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every packet the optional client add-on speaks. The mod stays server-authoritative:
 * these only replace the command round-trip and the server-built dialogs, they never
 * let the client decide anything the server would not have done itself.
 *
 * <p>A vanilla client sends none of these, so the server keeps its dialog path — see
 * {@link MTRClientTracker}.
 */
public class MTRPayloads {

    /** Bumped whenever the wire format changes incompatibly. */
    public static final int PROTOCOL_VERSION = 1;

    // Name deliberately not "type": inside the records below, CustomPacketPayload's own
    // abstract type() would shadow it.
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(MicroTimingReplay.id(path));
    }

    // ── client → server ──────────────────────────────────────────────────────

    /** Sent once on join; how the server learns this player can be talked to. */
    public record HelloC2S(int protocol) implements CustomPacketPayload {
        public static final Type<HelloC2S> TYPE = MTRPayloads.payloadType("hello_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloC2S> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeVarInt(payload.protocol()),
                buf -> new HelloC2S(buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** An empty {@code profile} means "unsubscribe". */
    public record SubscribeC2S(String profile) implements CustomPacketPayload {
        public static final Type<SubscribeC2S> TYPE = MTRPayloads.payloadType("subscribe_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, SubscribeC2S> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeUtf(payload.profile(), 256),
                buf -> new SubscribeC2S(buf.readUtf(256)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Asks for the whole flattened timeline of the session this player is watching. */
    public record RequestTimelineC2S() implements CustomPacketPayload {
        public static final RequestTimelineC2S INSTANCE = new RequestTimelineC2S();
        public static final Type<RequestTimelineC2S> TYPE = MTRPayloads.payloadType("request_timeline_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestTimelineC2S> CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Hover text and call stack for one row. Kept out of the bulk timeline transfer —
     * a large recording has six figures of rows and almost none of them get inspected.
     */
    public record RequestDetailsC2S(int step) implements CustomPacketPayload {
        public static final Type<RequestDetailsC2S> TYPE = MTRPayloads.payloadType("request_details_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestDetailsC2S> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeVarInt(payload.step()),
                buf -> new RequestDetailsC2S(buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The scroll wheel, and the timeline screen's nav buttons. */
    public record StepC2S(String unit, int amount, boolean forward) implements CustomPacketPayload {
        public static final Type<StepC2S> TYPE = MTRPayloads.payloadType("step_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, StepC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.unit(), 32);
                    buf.writeVarInt(payload.amount());
                    buf.writeBoolean(payload.forward());
                },
                buf -> new StepC2S(buf.readUtf(32), buf.readVarInt(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record JumpC2S(int step) implements CustomPacketPayload {
        public static final Type<JumpC2S> TYPE = MTRPayloads.payloadType("jump_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, JumpC2S> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeVarInt(payload.step()),
                buf -> new JumpC2S(buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Opt in to having the camera flown to each step. Off by default — it takes the
     * player's game mode and position over, which is not something to do uninvited.
     */
    public record SetCameraFollowC2S(boolean enabled) implements CustomPacketPayload {
        public static final Type<SetCameraFollowC2S> TYPE = MTRPayloads.payloadType("set_camera_follow_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCameraFollowC2S> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBoolean(payload.enabled()),
                buf -> new SetCameraFollowC2S(buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestFilterC2S() implements CustomPacketPayload {
        public static final RequestFilterC2S INSTANCE = new RequestFilterC2S();
        public static final Type<RequestFilterC2S> TYPE = MTRPayloads.payloadType("request_filter_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestFilterC2S> CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetFilterC2S(String eventId, RecordMode mode) implements CustomPacketPayload {
        public static final Type<SetFilterC2S> TYPE = MTRPayloads.payloadType("set_filter_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.eventId(), 128);
                    buf.writeByte(payload.mode().toWire());
                },
                buf -> new SetFilterC2S(buf.readUtf(128), RecordMode.fromWire(buf.readByte())));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResetFilterC2S() implements CustomPacketPayload {
        public static final ResetFilterC2S INSTANCE = new ResetFilterC2S();
        public static final Type<ResetFilterC2S> TYPE = MTRPayloads.payloadType("reset_filter_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetFilterC2S> CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── server → client ──────────────────────────────────────────────────────

    /**
     * @param allowed whether this player passes the same permission check {@code /mtr} requires.
     *                The client greys out its panel when false; the server re-checks anyway.
     */
    public record HelloS2C(int protocol, boolean allowed) implements CustomPacketPayload {
        public static final Type<HelloS2C> TYPE = MTRPayloads.payloadType("hello_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.protocol());
                    buf.writeBoolean(payload.allowed());
                },
                buf -> new HelloS2C(buf.readVarInt(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Running replays, which one this player is watching ({@code ""} for none), and
     * whether their camera is currently being flown around. The server owns the follow
     * flag, so the client shows this rather than what it last asked for.
     */
    public record SessionsS2C(List<String> running, String subscribed, boolean cameraFollow)
            implements CustomPacketPayload {
        public static final Type<SessionsS2C> TYPE = MTRPayloads.payloadType("sessions_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, SessionsS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.running().size());
                    for (String name : payload.running()) {
                        buf.writeUtf(name, 256);
                    }
                    buf.writeUtf(payload.subscribed(), 256);
                    buf.writeBoolean(payload.cameraFollow());
                },
                buf -> {
                    int count = buf.readVarInt();
                    List<String> running = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        running.add(buf.readUtf(256));
                    }
                    return new SessionsS2C(running, buf.readUtf(256), buf.readBoolean());
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * One timeline row. {@code depth} is the real nesting depth from the ENTER/EXIT
     * pairing, not the phase/queue/update triple the scoreboard uses, so the client
     * can fold any level.
     *
     * @param kind 0 = scope (has children, foldable), 1 = leaf
     */
    public record TimelineRow(int step, long tick, int depth, byte kind, int color, Component label) {}

    /**
     * The whole timeline in one logical payload — registered {@code large}, so Fabric
     * splits it across packets and the 1 MiB vanilla payload cap does not apply.
     *
     * @param cursorRow index into {@code rows} of the row the replay currently sits on, or -1
     */
    public record TimelineS2C(String profile, int cursorRow, List<TimelineRow> rows) implements CustomPacketPayload {
        public static final Type<TimelineS2C> TYPE = MTRPayloads.payloadType("timeline_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, TimelineS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.profile(), 256);
                    buf.writeVarInt(payload.cursorRow());
                    buf.writeVarInt(payload.rows().size());
                    long previousTick = 0;
                    for (TimelineRow row : payload.rows()) {
                        buf.writeVarInt(row.step());
                        // Ticks only ever move forward through the list, so the delta
                        // is almost always 0 and costs a single byte.
                        buf.writeVarLong(row.tick() - previousTick);
                        previousTick = row.tick();
                        buf.writeVarInt(row.depth());
                        buf.writeByte(row.kind());
                        buf.writeInt(row.color());
                        ComponentSerialization.STREAM_CODEC.encode(buf, row.label());
                    }
                },
                buf -> {
                    String profile = buf.readUtf(256);
                    int cursorRow = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<TimelineRow> rows = new ArrayList<>(count);
                    long previousTick = 0;
                    for (int i = 0; i < count; i++) {
                        int step = buf.readVarInt();
                        long tick = previousTick + buf.readVarLong();
                        previousTick = tick;
                        rows.add(new TimelineRow(
                                step, tick, buf.readVarInt(), buf.readByte(), buf.readInt(),
                                ComponentSerialization.STREAM_CODEC.decode(buf)));
                    }
                    return new TimelineS2C(profile, cursorRow, rows);
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pushed after every advance so the HUD and the open timeline follow along. */
    public record CursorS2C(String profile, int cursorRow, long tick, long totalTick, int totalRows)
            implements CustomPacketPayload {
        public static final Type<CursorS2C> TYPE = MTRPayloads.payloadType("cursor_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, CursorS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.profile(), 256);
                    buf.writeVarInt(payload.cursorRow());
                    buf.writeVarLong(payload.tick());
                    buf.writeVarLong(payload.totalTick());
                    buf.writeVarInt(payload.totalRows());
                },
                buf -> new CursorS2C(buf.readUtf(256), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Answer to {@link RequestDetailsC2S}. Large: a deep call stack is not small.
     *
     * @param focus the event's marker block, or empty when the event has no place in the
     *              world (phases, level ticks). The client keeps its previous focus in that
     *              case rather than snapping the camera to the origin.
     */
    public record DetailsS2C(int step, Component hover, List<String> stackTrace, Optional<BlockPos> focus)
            implements CustomPacketPayload {
        public static final Type<DetailsS2C> TYPE = MTRPayloads.payloadType("details_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, DetailsS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.step());
                    ComponentSerialization.STREAM_CODEC.encode(buf, payload.hover());
                    buf.writeVarInt(payload.stackTrace().size());
                    for (String line : payload.stackTrace()) {
                        buf.writeUtf(line, 512);
                    }
                    buf.writeBoolean(payload.focus().isPresent());
                    payload.focus().ifPresent(buf::writeBlockPos);
                },
                buf -> {
                    int step = buf.readVarInt();
                    Component hover = ComponentSerialization.STREAM_CODEC.decode(buf);
                    int count = buf.readVarInt();
                    List<String> lines = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        lines.add(buf.readUtf(512));
                    }
                    Optional<BlockPos> focus = buf.readBoolean()
                            ? Optional.of(buf.readBlockPos())
                            : Optional.empty();
                    return new DetailsS2C(step, hover, lines, focus);
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Tells the add-on to bring a screen up. Only ever sent in response to the player's
     * own {@code /mtr ... screen} command, so the two entry points land in the same UI
     * instead of the command dropping the player into the legacy dialog.
     */
    public record OpenScreenS2C(byte screen) implements CustomPacketPayload {
        public static final byte TIMELINE = 0;
        public static final byte FILTER = 1;

        public static final Type<OpenScreenS2C> TYPE = MTRPayloads.payloadType("open_screen_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenS2C> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeByte(payload.screen()),
                buf -> new OpenScreenS2C(buf.readByte()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * @param option true for the plain on/off replay switches, which only flip between
     *               {@link RecordMode#OFF} and {@link RecordMode#ALL}
     */
    public record FilterRow(String id, String category, String defaultName, RecordMode mode, boolean option) {
        public boolean enabled() {
            return mode.records();
        }
    }

    /** The server's authoritative view of {@code event_filter.json}. */
    public record FilterS2C(List<FilterRow> rows) implements CustomPacketPayload {
        public static final Type<FilterS2C> TYPE = MTRPayloads.payloadType("filter_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, FilterS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.rows().size());
                    for (FilterRow row : payload.rows()) {
                        buf.writeUtf(row.id(), 128);
                        buf.writeUtf(row.category(), 128);
                        buf.writeUtf(row.defaultName(), 256);
                        buf.writeByte(row.mode().toWire());
                        buf.writeBoolean(row.option());
                    }
                },
                buf -> {
                    int count = buf.readVarInt();
                    List<FilterRow> rows = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        rows.add(new FilterRow(buf.readUtf(128), buf.readUtf(128), buf.readUtf(256),
                                RecordMode.fromWire(buf.readByte()), buf.readBoolean()));
                    }
                    return new FilterS2C(rows);
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

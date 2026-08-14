package ml.mypals.microtimingreplay.config;

import java.util.Locale;

/**
 * How much of one event kind the recorder keeps.
 *
 * <p>Replaces both the old on/off filter flag and the {@code skip_empty_*} game rules: those
 * were two knobs describing one decision, and they lived in two different places.
 *
 * <p>{@link #NON_EMPTY} only bites on events that can hold children — phases, queues, updates,
 * entity ticks. Leaf events never pass through the drop check, so choosing it for one of those
 * behaves exactly like {@link #ALL}.
 */
public enum RecordMode {
    /** Not recorded at all; the mixin does not even build the event. */
    OFF,
    /**
     * Recorded in full, but hidden everywhere: no timeline row, no marker, and stepping walks
     * straight past it. The action still runs, so the world state is identical either way.
     *
     * <p>Nothing about this is baked into the recording — switch back to {@link #NON_EMPTY} or
     * {@link #ALL} and the same file shows the events again.
     */
    NO_DISPLAY,
    /** Recorded, but dropped again if it turned out to contain nothing. */
    NON_EMPTY,
    /** Recorded unconditionally, empty or not. */
    ALL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean records() {
        return this != OFF;
    }

    /** Cycles by how much you see: nothing, recorded-but-hidden, non-empty only, everything. */
    public RecordMode next() {
        return switch (this) {
            case OFF -> NO_DISPLAY;
            case NO_DISPLAY -> NON_EMPTY;
            case NON_EMPTY -> ALL;
            case ALL -> OFF;
        };
    }

    /** Whether events of this kind appear in the timeline, draw markers, and stop a step. */
    public boolean displays() {
        return this != OFF && this != NO_DISPLAY;
    }

    public static RecordMode byId(String id, RecordMode fallback) {
        if (id == null) return fallback;
        for (RecordMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id)) return mode;
        }
        return fallback;
    }

    public byte toWire() {
        return (byte) ordinal();
    }

    public static RecordMode fromWire(byte wire) {
        RecordMode[] values = values();
        return wire >= 0 && wire < values.length ? values[wire] : OFF;
    }
}

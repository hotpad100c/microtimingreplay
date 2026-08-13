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

    public RecordMode next() {
        return switch (this) {
            case OFF -> NON_EMPTY;
            case NON_EMPTY -> ALL;
            case ALL -> OFF;
        };
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

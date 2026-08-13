package ml.mypals.microtimingreplay.config;

import ml.mypals.microtimingreplay.event.PhaseType;

import java.util.*;

public class RecordingEventRegistry {

    /** Whether an entry filters recording or is one of the plain on/off replay switches. */
    public enum EntryKind {
        /** Cycles through all three {@link RecordMode}s. */
        EVENT,
        /** Only ever {@link RecordMode#OFF} or {@link RecordMode#ALL}; shown as a checkbox. */
        OPTION
    }

    public record EventEntry(
        String id,
        String category,
        String defaultName,
        RecordMode defaultMode,
        EntryKind kind
    ) {
        public boolean isOption() {
            return kind == EntryKind.OPTION;
        }
    }

    private static final Map<String, EventEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Registers a new recordable event entry.
     * Easily call this method to add new events in the future.
     */
    public static EventEntry register(String id, String category, String defaultName, RecordMode defaultMode) {
        EventEntry entry = new EventEntry(id, category, defaultName, defaultMode, EntryKind.EVENT);
        ENTRIES.put(id, entry);
        return entry;
    }

    /**
     * Registers a plain on/off switch. These are not events — they are the replay-stepping
     * knobs that used to be game rules — but they live in the same per-world file and the same
     * screen, so they go through the same registry.
     */
    public static EventEntry registerOption(String id, String category, String defaultName, boolean defaultValue) {
        EventEntry entry = new EventEntry(id, category, defaultName,
                defaultValue ? RecordMode.ALL : RecordMode.OFF, EntryKind.OPTION);
        ENTRIES.put(id, entry);
        return entry;
    }

    public static Collection<EventEntry> getAll() {
        return ENTRIES.values();
    }

    public static EventEntry get(String id) {
        return ENTRIES.get(id);
    }

    static {
        // Block & Piston
        register("set_block", "Block", "Set Block", RecordMode.ALL);
        register("moving_piston_start", "Block", "Moving Piston Start", RecordMode.ALL);
        register("moving_piston_tick", "Block", "Moving Piston Tick", RecordMode.ALL);
        register("moving_piston_despawn", "Block", "Moving Piston Despawn", RecordMode.ALL);
        register("block_entity_creation", "Block", "BlockEntity Creation", RecordMode.OFF);
        register("block_entity_tick", "Block", "Block Entity Tick", RecordMode.NON_EMPTY);
        register("piston_structure", "Block", "Piston Structure Resolve", RecordMode.OFF);
        // Container
        register("item_transfer", "Container", "Item Transfer", RecordMode.OFF);

        // Queues
        register("add_schedule_tick", "Schedule", "Add Schedule Tick", RecordMode.ALL);
        register("block_tick", "Schedule", "Execute Block Tick", RecordMode.NON_EMPTY);
        register("fluid_tick", "Schedule", "Execute Fluid Tick", RecordMode.NON_EMPTY);
        register("add_block_event", "Schedule", "Add Block Event", RecordMode.ALL);
        register("execute_block_event", "Schedule", "Execute Block Event", RecordMode.NON_EMPTY);

        // Updates
        register("neighbor_update", "Redstone", "Neighbor Update", RecordMode.NON_EMPTY);
        register("shape_update", "Redstone", "Shape Update", RecordMode.NON_EMPTY);

        // Entity
        register("entity_spawn", "Entity", "Entity Spawn", RecordMode.ALL);
        register("entity_despawn", "Entity", "Entity Despawn", RecordMode.ALL);
        register("entity_move", "Entity", "Entity Move", RecordMode.ALL);
        register("entity_collide_axis", "Entity", "Entity Collide Axis", RecordMode.OFF);
        register("entity_set_health", "Entity", "Entity Set Health", RecordMode.OFF);
        register("entity_tick", "Entity", "Entity Tick", RecordMode.ALL);


        // GameEvent
        register("post_game_event", "GameEvent", "Post Game Event", RecordMode.ALL);
        register("received_game_event", "GameEvent", "Received Game Event", RecordMode.ALL);

        // Network
        register("network_packet", "Network", "Network Packet", RecordMode.OFF);

        // Phase —— 遍历 PhaseType，新增阶段自动获得独立开关，不用在这里补登记
        for (PhaseType phase : PhaseType.values()) {
            register(phase.filterId(), "Phase", phase.defaultName(), phase.defaultMode());
        }

        // Replay stepping. These were game rules until they moved here, so the whole mod is
        // configured in one place; the defaults match what the game rules used to default to.
        registerOption("step_ignore_updates", "Replay", "Step Skips Updates", true);
        registerOption("step_ignore_exiting", "Replay", "Step Skips Exits", true);
        registerOption("skip_delta_changes", "Replay", "Hide Intermediate Changes", true);
    }
}

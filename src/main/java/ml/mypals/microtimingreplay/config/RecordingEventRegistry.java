package ml.mypals.microtimingreplay.config;

import java.util.*;

public class RecordingEventRegistry {

    public record EventEntry(
        String id,
        String category,
        String defaultName,
        boolean defaultEnabled
    ) {}

    private static final Map<String, EventEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Registers a new recordable event entry.
     * Easily call this method to add new events in the future.
     */
    public static EventEntry register(String id, String category, String defaultName, boolean defaultEnabled) {
        EventEntry entry = new EventEntry(id, category, defaultName, defaultEnabled);
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
        register("set_block", "Block", "Set Block", true);
        register("moving_piston_start", "Block", "Moving Piston Start", true);
        register("moving_piston_tick", "Block", "Moving Piston Tick", true);
        register("moving_piston_despawn", "Block", "Moving Piston Despawn", true);

        // Container
        register("item_transfer", "Container", "Item Transfer", false);

        // Schedule & Queues
        register("add_schedule_tick", "Schedule", "Add Schedule Tick", true);
        register("block_tick", "Schedule", "Execute Block Tick", true);
        register("fluid_tick", "Schedule", "Execute Fluid Tick", true);
        register("add_block_event", "Schedule", "Add Block Event", true);
        register("execute_block_event", "Schedule", "Execute Block Event", true);

        // Redstone & Updates
        register("neighbor_update", "Redstone", "Neighbor Update", true);
        register("shape_update", "Redstone", "Shape Update", true);

        // Entity
        register("entity_spawn", "Entity", "Entity Spawn", true);
        register("entity_despawn", "Entity", "Entity Despawn", true);
        register("entity_move", "Entity", "Entity Move", true);
        register("entity_tick", "Entity", "Entity Tick", true);
        register("block_entity_tick", "Entity", "Block Entity Tick", true);

        // GameEvent & Vibration
        register("post_game_event", "GameEvent", "Post Game Event", true);
        register("received_game_event", "GameEvent", "Received Game Event", true);

        // Phase
        register("phase_player_tick", "Phase", "Player Tick Phase", true);
        register("phase_level_tick", "Phase", "Level Tick Phase", true);
    }
}

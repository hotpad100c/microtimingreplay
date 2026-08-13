package ml.mypals.microtimingreplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.profile.WorldScopedStorage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RecordingFilterConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, RecordMode> MODES = new HashMap<>();
    private static String cachedWorldKey = null;

    private static File getConfigFile() {
        return new File(WorldScopedStorage.getWorldDir(), "event_filter.json");
    }

    public static synchronized void ensureLoaded() {
        if (MicroTimingReplay.server == null) return;
        File file = getConfigFile();
        String currentKey = file.getAbsolutePath();
        if (cachedWorldKey != null && cachedWorldKey.equals(currentKey)) {
            return;
        }

        MODES.clear();
        cachedWorldKey = currentKey;

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("enabledEvents") && json.get("enabledEvents").isJsonObject()) {
                    JsonObject eventsObj = json.getAsJsonObject("enabledEvents");
                    for (String key : eventsObj.keySet()) {
                        RecordMode mode = readMode(key, eventsObj.get(key));
                        if (mode != null) MODES.put(key, mode);
                    }
                }
            } catch (Exception e) {
                MicroTimingReplay.LOGGER.error("Failed to read event_filter.json, fallback to defaults", e);
            }
        } else {
            save();
        }
    }

    /**
     * Reads one entry, accepting the pre-tri-state format where every value was a boolean.
     *
     * <p>A legacy {@code true} becomes the entry's own default rather than {@link RecordMode#ALL}:
     * back then "on" meant "on, and let the matching {@code skip_empty_*} game rule decide about
     * empties", and the defaults carry what those game rules defaulted to. Mapping it to
     * {@code ALL} would silently start keeping every empty phase and queue.
     */
    private static RecordMode readMode(String eventId, JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        try {
            if (value.getAsJsonPrimitive().isBoolean()) {
                if (!value.getAsBoolean()) return RecordMode.OFF;
                RecordingEventRegistry.EventEntry entry = RecordingEventRegistry.get(eventId);
                RecordMode legacyOn = entry != null ? entry.defaultMode() : RecordMode.ALL;
                return legacyOn == RecordMode.OFF ? RecordMode.ALL : legacyOn;
            }
            return RecordMode.byId(value.getAsString(), null);
        } catch (Exception e) {
            return null;
        }
    }

    public static RecordMode mode(String eventId) {
        ensureLoaded();
        RecordMode stored = MODES.get(eventId);
        if (stored != null) return stored;
        RecordingEventRegistry.EventEntry entry = RecordingEventRegistry.get(eventId);
        return entry == null ? RecordMode.ALL : entry.defaultMode();
    }

    /** Whether this event gets recorded at all. Says nothing about empties — see {@link #mode}. */
    public static boolean isEnabled(String eventId) {
        return mode(eventId).records();
    }

    /** For the {@link RecordingEventRegistry.EntryKind#OPTION} entries, which are plain switches. */
    public static boolean optionEnabled(String optionId) {
        return mode(optionId).records();
    }

    public static void setMode(String eventId, RecordMode mode) {
        ensureLoaded();
        MODES.put(eventId, mode);
        save();
    }

    public static void setEnabled(String eventId, boolean enabled) {
        setMode(eventId, enabled ? RecordMode.ALL : RecordMode.OFF);
    }

    /**
     * Advances one entry to its next state. Options only have two, so they flip; events walk
     * off → non-empty → all → off.
     */
    public static void cycle(String eventId) {
        RecordingEventRegistry.EventEntry entry = RecordingEventRegistry.get(eventId);
        RecordMode current = mode(eventId);
        if (entry != null && entry.isOption()) {
            setMode(eventId, current.records() ? RecordMode.OFF : RecordMode.ALL);
        } else {
            setMode(eventId, current.next());
        }
    }

    public static void resetToDefaults() {
        ensureLoaded();
        MODES.clear();
        for (RecordingEventRegistry.EventEntry entry : RecordingEventRegistry.getAll()) {
            MODES.put(entry.id(), entry.defaultMode());
        }
        save();
    }

    public static synchronized void save() {
        if (MicroTimingReplay.server == null) return;
        File file = getConfigFile();
        JsonObject root = new JsonObject();
        JsonObject eventsObj = new JsonObject();

        for (RecordingEventRegistry.EventEntry entry : RecordingEventRegistry.getAll()) {
            eventsObj.addProperty(entry.id(), MODES.getOrDefault(entry.id(), entry.defaultMode()).id());
        }

        root.add("enabledEvents", eventsObj);

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to save event_filter.json", e);
        }
    }
}

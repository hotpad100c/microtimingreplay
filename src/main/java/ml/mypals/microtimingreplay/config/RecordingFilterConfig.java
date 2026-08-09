package ml.mypals.microtimingreplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private static final Map<String, Boolean> ENABLED_MAP = new HashMap<>();
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

        ENABLED_MAP.clear();
        cachedWorldKey = currentKey;

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("enabledEvents") && json.get("enabledEvents").isJsonObject()) {
                    JsonObject eventsObj = json.getAsJsonObject("enabledEvents");
                    for (String key : eventsObj.keySet()) {
                        ENABLED_MAP.put(key, eventsObj.get(key).getAsBoolean());
                    }
                }
            } catch (Exception e) {
                MicroTimingReplay.LOGGER.error("Failed to read event_filter.json, fallback to defaults", e);
            }
        } else {
            save();
        }
    }

    public static boolean isEnabled(String eventId) {
        ensureLoaded();
        if (ENABLED_MAP.containsKey(eventId)) {
            return ENABLED_MAP.get(eventId);
        }
        RecordingEventRegistry.EventEntry entry = RecordingEventRegistry.get(eventId);
        return entry == null || entry.defaultEnabled();
    }

    public static void setEnabled(String eventId, boolean enabled) {
        ensureLoaded();
        ENABLED_MAP.put(eventId, enabled);
        save();
    }

    public static void toggle(String eventId) {
        setEnabled(eventId, !isEnabled(eventId));
    }

    public static void resetToDefaults() {
        ensureLoaded();
        ENABLED_MAP.clear();
        for (RecordingEventRegistry.EventEntry entry : RecordingEventRegistry.getAll()) {
            ENABLED_MAP.put(entry.id(), entry.defaultEnabled());
        }
        save();
    }

    public static synchronized void save() {
        if (MicroTimingReplay.server == null) return;
        File file = getConfigFile();
        JsonObject root = new JsonObject();
        JsonObject eventsObj = new JsonObject();

        for (RecordingEventRegistry.EventEntry entry : RecordingEventRegistry.getAll()) {
            boolean enabled = ENABLED_MAP.getOrDefault(entry.id(), entry.defaultEnabled());
            eventsObj.addProperty(entry.id(), enabled);
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

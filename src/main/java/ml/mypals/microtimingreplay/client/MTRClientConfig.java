package ml.mypals.microtimingreplay.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.replay.ReplaySession;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client-only preferences. Unlike the recording filter these are per-player, not
 * per-world, so they live next to the other configs rather than under a world key.
 */
@Environment(EnvType.CLIENT)
public class MTRClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What one notch of the wheel means. Offered as a ladder so a click can cycle it. */
    public static final List<Integer> STEP_AMOUNTS = List.of(1, 2, 5, 10, 25, 50, 100, 250);

    private static String stepUnit = "steps";
    private static int stepAmount = 1;
    private static boolean invertScroll = false;
    private static boolean followCursor = true;
    private static int panelHoldMillis = 0;
    private static boolean hideMarkers = false;

    private static File configFile() {
        return new File(new File(FabricLoader.getInstance().getConfigDir().toFile(), "mtr"), "client.json");
    }

    public static String stepUnit() {
        return stepUnit;
    }

    public static void setStepUnit(String unit) {
        if (!ReplaySession.isUnitInvalid(unit)) {
            stepUnit = unit;
            save();
        }
    }

    public static void cycleStepUnit(int direction) {
        int index = ReplaySession.STEP_UNITS.indexOf(stepUnit);
        if (index < 0) index = 0;
        int size = ReplaySession.STEP_UNITS.size();
        setStepUnit(ReplaySession.STEP_UNITS.get(Math.floorMod(index + direction, size)));
    }

    public static int stepAmount() {
        return stepAmount;
    }

    public static void setStepAmount(int amount) {
        stepAmount = Math.clamp(amount, 1, 4096);
        save();
    }

    public static void cycleStepAmount(int direction) {
        int index = STEP_AMOUNTS.indexOf(stepAmount);
        if (index < 0) index = 0;
        setStepAmount(STEP_AMOUNTS.get(Math.floorMod(index + direction, STEP_AMOUNTS.size())));
    }

    public static boolean invertScroll() {
        return invertScroll;
    }

    public static void setInvertScroll(boolean value) {
        invertScroll = value;
        save();
    }

    /** Whether the timeline screen scrolls itself to keep the replay cursor in view. */
    public static boolean followCursor() {
        return followCursor;
    }

    public static void setFollowCursor(boolean value) {
        followCursor = value;
        save();
    }

    /**
     * How long the key must be held before the panel appears; zero shows it at once.
     * The default binding is unbound in vanilla so no delay is needed, but rebinding
     * onto a key you also use for something else makes this worth turning up.
     */
    public static int panelHoldMillis() {
        return panelHoldMillis;
    }

    public static void setPanelHoldMillis(int millis) {
        panelHoldMillis = Math.clamp(millis, 0, 2000);
        save();
    }

    public static boolean hideMarkers() {
        return hideMarkers;
    }

    public static void setHideMarkers(boolean value) {
        hideMarkers = value;
        save();
    }

    public static void load() {
        File file = configFile();
        if (!file.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("stepUnit")) {
                String unit = json.get("stepUnit").getAsString();
                if (!ReplaySession.isUnitInvalid(unit)) stepUnit = unit;
            }
            if (json.has("stepAmount")) stepAmount = Math.clamp(json.get("stepAmount").getAsInt(), 1, 4096);
            if (json.has("invertScroll")) invertScroll = json.get("invertScroll").getAsBoolean();
            if (json.has("followCursor")) followCursor = json.get("followCursor").getAsBoolean();
            if (json.has("panelHoldMillis")) panelHoldMillis = Math.clamp(json.get("panelHoldMillis").getAsInt(), 0, 2000);
            if (json.has("hideMarkers")) hideMarkers = json.get("hideMarkers").getAsBoolean();
        } catch (Exception e) {
            MicroTimingReplay.LOGGER.error("Failed to read client.json, falling back to defaults", e);
        }
    }

    public static void save() {
        File file = configFile();
        JsonObject root = new JsonObject();
        root.addProperty("stepUnit", stepUnit);
        root.addProperty("stepAmount", stepAmount);
        root.addProperty("invertScroll", invertScroll);
        root.addProperty("followCursor", followCursor);
        root.addProperty("panelHoldMillis", panelHoldMillis);
        root.addProperty("hideMarkers", hideMarkers);

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to save client.json", e);
        }
    }
}

package ml.mypals.microtimingreplay.replay.stackTrace;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.MTREvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.ReplayContext;
import ml.mypals.microtimingreplay.profile.TickFrame;
import ml.mypals.microtimingreplay.profile.WorldScopedStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.StringTag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StackTraceManager {


    private static final Map<String, Map<Integer, List<String>>> BY_SESSION = new HashMap<>();

    private static Map<Integer, List<String>> traces() {
        return BY_SESSION.computeIfAbsent(ReplayContext.key(), k -> new HashMap<>());
    }

    public static void forgetSession(String sessionId) {
        BY_SESSION.remove(sessionId == null ? "" : sessionId);
    }

    public static void clear() {
        traces().clear();
    }

    public static void record(int stepIndex, List<String> stackTrace) {
        if (stackTrace != null && !stackTrace.isEmpty()) {
            traces().put(stepIndex, new ArrayList<>(stackTrace));
        }
    }

    public static List<String> get(int stepIndex) {
        return traces().get(stepIndex);
    }

    public static Map<Integer, List<String>> getAll() {
        return traces();
    }

    public static CompoundTag writeNBT() {
        CompoundTag root = new CompoundTag();
        ListTag stepsList = new ListTag();

        for (Map.Entry<Integer, List<String>> entry : traces().entrySet()) {
            CompoundTag stepTag = new CompoundTag();
            stepTag.putInt("step", entry.getKey());

            ListTag linesTag = new ListTag();
            for (String line : entry.getValue()) {
                linesTag.add(StringTag.valueOf(line));
            }
            stepTag.put("lines", linesTag);
            stepsList.add(stepTag);
        }

        root.put("steps", stepsList);
        return root;
    }

    public static void readNBT(CompoundTag root) {
        traces().clear();
        if (root == null || !root.contains("steps")) return;

        ListTag stepsList = root.getList("steps").orElse(new ListTag());
        for (int i = 0; i < stepsList.size(); i++) {
            CompoundTag stepTag = stepsList.getCompound(i).orElse(new CompoundTag());
            int step = stepTag.getInt("step").orElse(-1);
            if (step >= 0 && stepTag.contains("lines")) {
                ListTag linesTag = stepTag.getList("lines").orElse(new ListTag());
                List<String> lines = new ArrayList<>();
                for (int j = 0; j < linesTag.size(); j++) {
                    linesTag.getString(j).ifPresent(lines::add);
                }
                traces().put(step, lines);
            }
        }
    }

    public static void saveToFile(File file) {
        if (file == null) return;
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            CompoundTag tag = writeNBT();
            NbtIo.writeCompressed(tag, file.toPath());
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to save stack traces to {}", file, e);
        }
    }

    public static void loadFromFile(File file) {
        traces().clear();
        if (file == null || !file.exists()) return;
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            readNBT(tag);
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to load stack traces from {}", file, e);
        }
    }

    private static File getProfileFile(String profileName) {
        return WorldScopedStorage.getStackTraceFile(profileName);
    }

    public static void saveForProfile(String profileName) {
        if (profileName != null && !profileName.isEmpty()) {
            saveToFile(getProfileFile(profileName));
        }
    }

    public static void collectAndSaveForProfile(MTRProfile profile) {
        if (profile == null) return;
        StackTraceManager.clear();
        int stepCounter = 1;
        for (TickFrame frame : profile.getFrames()) {
            for (MTREvent event : frame.getEvents()) {
                stepCounter = collectEventStackTraces(event, stepCounter);
            }
        }
        saveForProfile(profile.getName());
    }

    private static int collectEventStackTraces(MTREvent event, int stepCounter) {
        if (event == null) return stepCounter;
        if (event.getStackTrace() != null && !event.getStackTrace().isEmpty()) {
            record(stepCounter, event.getStackTrace());
        }
        stepCounter++;
        for (MTREvent child : event.getChildren()) {
            stepCounter = collectEventStackTraces(child, stepCounter);
        }
        return stepCounter;
    }

    public static void loadForProfile(String profileName) {
        if (profileName != null && !profileName.isEmpty()) {
            loadFromFile(getProfileFile(profileName));
        }
    }

    public static void deleteForProfile(String profileName) {
        if (profileName != null && !profileName.isEmpty()) {
            File f = getProfileFile(profileName);
            if (f.exists()) f.delete();
        }
    }
}

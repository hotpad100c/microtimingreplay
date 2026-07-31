package ml.mypals.microtimingreplay.replay.stackTrace;

import ml.mypals.microtimingreplay.event.MTREvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.TickFrame;
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

    private static final Map<Integer, List<String>> STACK_TRACES = new HashMap<>();

    public static void clear() {
        STACK_TRACES.clear();
    }

    public static void record(int stepIndex, List<String> stackTrace) {
        if (stackTrace != null && !stackTrace.isEmpty()) {
            STACK_TRACES.put(stepIndex, new ArrayList<>(stackTrace));
        }
    }

    public static List<String> get(int stepIndex) {
        return STACK_TRACES.get(stepIndex);
    }

    public static Map<Integer, List<String>> getAll() {
        return STACK_TRACES;
    }

    public static CompoundTag writeNBT() {
        CompoundTag root = new CompoundTag();
        ListTag stepsList = new ListTag();

        for (Map.Entry<Integer, List<String>> entry : STACK_TRACES.entrySet()) {
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
        STACK_TRACES.clear();
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
                STACK_TRACES.put(step, lines);
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
            e.printStackTrace();
        }
    }

    public static void loadFromFile(File file) {
        STACK_TRACES.clear();
        if (file == null || !file.exists()) return;
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            readNBT(tag);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getProfileFile(String profileName) {
        File dir = new File(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile(), "mtr_stacktrace");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, profileName + ".dat");
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

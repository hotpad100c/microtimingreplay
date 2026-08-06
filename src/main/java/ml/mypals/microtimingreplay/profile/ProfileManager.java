package ml.mypals.microtimingreplay.profile;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.replay.WorldBackupManager;
import ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileManager {
    private static final File PROFILES_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mtr_profiles");

    private record AreaNameCache(long lastModified, List<String> names) {}

    private static final Map<String, AreaNameCache> AREA_NAME_CACHE = new HashMap<>();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void init() {
        if (!PROFILES_DIR.exists()) {
            PROFILES_DIR.mkdirs();
        }
    }

    public static MTRProfile createProfile(String name) {
        if (hasProfile(name)) {
            return null;
        }
        MTRProfile profile = new MTRProfile(name);
        saveProfile(profile);
        return profile;
    }

    public static void saveProfile(MTRProfile profile) {
        Path path = new File(PROFILES_DIR, profile.getName() + ".dat").toPath();
        try {
            NbtIo.writeCompressed(profile.writeNBT(), path);
            AREA_NAME_CACHE.remove(profile.getName());
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to save profile: {}", profile.getName(), e);
        }
    }

    public static List<String> listAreaNames(String name) {
        File file = new File(PROFILES_DIR, name + ".dat");
        if (!file.exists()) {
            AREA_NAME_CACHE.remove(name);
            return List.of();
        }

        long modified = file.lastModified();
        AreaNameCache cached = AREA_NAME_CACHE.get(name);
        if (cached != null && cached.lastModified() == modified) {
            return cached.names();
        }

        MTRProfile profile = loadProfile(name);
        if (profile == null) {
            return List.of();
        }
        List<String> names = profile.getAreas().stream().map(a -> a.name).toList();
        AREA_NAME_CACHE.put(name, new AreaNameCache(modified, names));
        return names;
    }

    public static MTRProfile loadProfile(String name) {
        Path path = new File(PROFILES_DIR, name + ".dat").toPath();
        if (!path.toFile().exists()) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return MTRProfile.readNBT(tag);
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to load profile: {}", name, e);
            return null;
        }
    }

    public static boolean deleteProfile(String name) {
        File file = new File(PROFILES_DIR, name + ".dat");
        boolean deleted = file.exists() && file.delete();
        if (deleted) {
            AREA_NAME_CACHE.remove(name);
            WorldBackupManager.deleteBackups(name);
            StackTraceManager.deleteForProfile(name);
        }
        return deleted;
    }

    public static boolean hasProfile(String name) {
        File file = new File(PROFILES_DIR, name + ".dat");
        return file.exists();
    }

    public static List<String> listProfiles() {
        List<String> profiles = new ArrayList<>();
        File[] files = PROFILES_DIR.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".dat")) {
                    profiles.add(file.getName().substring(0, file.getName().length() - 4));
                }
            }
        }
        return profiles;
    }
}

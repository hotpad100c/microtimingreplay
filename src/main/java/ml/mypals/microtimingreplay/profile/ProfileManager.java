package ml.mypals.microtimingreplay.profile;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileManager {
    private record AreaNameCache(long lastModified, List<String> names) {}

    private static final Map<String, AreaNameCache> AREA_NAME_CACHE = new HashMap<>();

    public static void init() {
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
        Path path = WorldScopedStorage.getProfileFile(profile.getName()).toPath();
        try {
            NbtIo.writeCompressed(profile.writeNBT(), path);
            AREA_NAME_CACHE.remove(profile.getName());
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to save profile: {}", profile.getName(), e);
        }
    }

    public static List<String> listAreaNames(String name) {
        File file = WorldScopedStorage.getProfileFile(name);
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
        Path path = WorldScopedStorage.getProfileFile(name).toPath();
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
        boolean deleted = WorldScopedStorage.deleteProfile(name);
        if (deleted) {
            AREA_NAME_CACHE.remove(name);
        }
        return deleted;
    }

    public static boolean hasProfile(String name) {
        return WorldScopedStorage.hasProfile(name);
    }

    public static List<String> listProfiles() {
        return WorldScopedStorage.listProfileNames();
    }

    public static List<String> listLegacyProfiles() {
        return WorldScopedStorage.listLegacyProfileNames();
    }

    public static boolean migrateLegacyProfile(String name) {
        boolean migrated = WorldScopedStorage.migrateLegacyProfile(name);
        if (migrated) {
            AREA_NAME_CACHE.remove(name);
        }
        return migrated;
    }
}

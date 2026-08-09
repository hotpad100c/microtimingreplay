package ml.mypals.microtimingreplay.profile;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public final class WorldScopedStorage {
    private static final File CONFIG_DIR = FabricLoader.getInstance().getConfigDir().toFile();
    private static final String ROOT = "mtr";
    private static final String LEGACY_PROFILES = "mtr_profiles";
    private static final String LEGACY_BACKUPS = "mtr_backups";
    private static final String LEGACY_STACKTRACE = "mtr_stacktrace";

    private WorldScopedStorage() {
    }


    /** {@code config/mtr/<world-key>/} — everything recorded in the loaded world. */
    public static File getWorldDir() {
        File directory = new File(new File(CONFIG_DIR, ROOT), getWorldKey());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    /** {@code config/mtr/<world-key>/<name>/} — one profile and all of its files. */
    public static File getProfileDir(String profileName) {
        File directory = new File(getWorldDir(), profileName);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    public static File getProfileFile(String profileName) {
        return new File(getProfileDir(profileName), "profile.dat");
    }

    public static File getBackupFile(String profileName, String suffix) {
        return new File(getProfileDir(profileName), suffix + ".dat");
    }

    public static File getStackTraceFile(String profileName) {
        return new File(getProfileDir(profileName), "trace.dat");
    }

    public static List<String> listProfileNames() {
        List<String> names = new ArrayList<>();
        File[] entries = getWorldDir().listFiles();
        if (entries == null) return names;
        for (File entry : entries) {
            if (entry.isDirectory() && new File(entry, "profile.dat").isFile()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    public static boolean hasProfile(String profileName) {
        return new File(new File(getWorldDir(), profileName), "profile.dat").isFile();
    }

    public static boolean deleteProfile(String profileName) {
        File directory = new File(getWorldDir(), profileName);
        if (!directory.isDirectory()) return false;
        deleteRecursively(directory);
        return !directory.exists();
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    public static void migrateCategoryLayout() {
        File legacyWorldDir = new File(new File(CONFIG_DIR, LEGACY_PROFILES), getWorldKey());
        File[] entries = legacyWorldDir.listFiles();
        if (entries == null) return;

        for (File entry : entries) {
            if (!entry.isFile() || !entry.getName().endsWith(".dat")) continue;
            String name = entry.getName().substring(0, entry.getName().length() - 4);
            if (hasProfile(name)) continue;

            try {
                moveIfPresent(entry.toPath(), getProfileFile(name).toPath());
                moveIfPresent(legacyWorldFile(LEGACY_BACKUPS, name + "_record.dat"), getBackupFile(name, "record").toPath());
                moveIfPresent(legacyWorldFile(LEGACY_BACKUPS, name + "_replay.dat"), getBackupFile(name, "replay").toPath());
                moveIfPresent(legacyWorldFile(LEGACY_BACKUPS, name + "_worldgen.dat"), getBackupFile(name, "worldgen").toPath());
                moveIfPresent(legacyWorldFile(LEGACY_STACKTRACE, name + ".dat"), getStackTraceFile(name).toPath());
                MicroTimingReplay.LOGGER.info("Moved profile '{}' into the per-profile storage layout", name);
            } catch (IOException e) {
                MicroTimingReplay.LOGGER.error("Failed to move profile '{}' into the new storage layout", name, e);
            }
        }
    }

    private static Path legacyWorldFile(String category, String fileName) {
        return new File(new File(new File(CONFIG_DIR, category), getWorldKey()), fileName).toPath();
    }

    public static File getLegacyProfileFile(String profileName) {
        return new File(new File(CONFIG_DIR, LEGACY_PROFILES), profileName + ".dat");
    }

    public static List<String> listLegacyProfileNames() {
        List<String> names = new ArrayList<>();
        File[] entries = new File(CONFIG_DIR, LEGACY_PROFILES).listFiles();
        if (entries == null) return names;
        for (File entry : entries) {
            if (entry.isFile() && entry.getName().endsWith(".dat")) {
                names.add(entry.getName().substring(0, entry.getName().length() - 4));
            }
        }
        return names;
    }

    public static boolean migrateLegacyProfile(String profileName) {
        File legacyProfile = getLegacyProfileFile(profileName);
        if (!legacyProfile.exists() || hasProfile(profileName)) {
            return false;
        }

        File legacyDir = new File(CONFIG_DIR, LEGACY_BACKUPS);
        try {
            copyIfPresent(legacyProfile.toPath(), getProfileFile(profileName).toPath());
            copyIfPresent(new File(legacyDir, profileName + "_record.dat").toPath(), getBackupFile(profileName, "record").toPath());
            copyIfPresent(new File(legacyDir, profileName + "_replay.dat").toPath(), getBackupFile(profileName, "replay").toPath());
            copyIfPresent(new File(new File(CONFIG_DIR, LEGACY_STACKTRACE), profileName + ".dat").toPath(),
                    getStackTraceFile(profileName).toPath());
            return true;
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to migrate legacy profile: {}", profileName, e);
            return false;
        }
    }

    private static void copyIfPresent(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        }
    }

    private static void moveIfPresent(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.createDirectories(target.getParent());
            Files.move(source, target);
        }
    }

    private static String getWorldKey() {
        if (MicroTimingReplay.server == null) {
            throw new IllegalStateException("Cannot access MTR storage before the server has started");
        }

        Path worldRoot = MicroTimingReplay.server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize();
        Path fileName = worldRoot.getFileName();
        String readableName = sanitize(fileName == null ? "world" : fileName.toString());
        return readableName + "-" + shortHash(worldRoot.toString());
    }

    private static String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isEmpty() ? "world" : sanitized;
    }

    private static String shortHash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                result.append(String.format("%02x", bytes[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

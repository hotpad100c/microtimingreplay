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

/**
 * 将录制数据限制在服务器当前加载的存档中。
 *
 * <p>旧版 MTR 直接将 profile 放在分类目录下。旧文件会被保留；玩家可使用
 * {@code /mtr profile migrate <name>} 将指定 profile 复制到当前存档。</p>
 */
public final class WorldScopedStorage {
    private static final File CONFIG_DIR = FabricLoader.getInstance().getConfigDir().toFile();

    private WorldScopedStorage() {
    }

    public static void initCategory(String category) {
        getCategoryDir(category).mkdirs();
    }

    public static File getProfileFile(String profileName) {
        return new File(getWorldDir("mtr_profiles"), profileName + ".dat");
    }

    public static File getLegacyProfileFile(String profileName) {
        return new File(getCategoryDir("mtr_profiles"), profileName + ".dat");
    }

    public static File getBackupFile(String profileName, String suffix) {
        return new File(getWorldDir("mtr_backups"), profileName + "_" + suffix + ".dat");
    }

    public static File getStackTraceFile(String profileName) {
        return new File(getWorldDir("mtr_stacktrace"), profileName + ".dat");
    }

    public static File getWorldDir(String category) {
        File directory = new File(getCategoryDir(category), getWorldKey());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    public static File getCategoryDir(String category) {
        File directory = new File(CONFIG_DIR, category);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    /**
     * 将一个旧版 profile 及其关联文件复制到当前存档的命名空间。
     * 已存在的当前存档数据绝不覆盖。
     */
    public static boolean migrateLegacyProfile(String profileName) {
        File legacyProfile = getLegacyProfileFile(profileName);
        File scopedProfile = getProfileFile(profileName);
        File legacyRecordBackup = new File(getCategoryDir("mtr_backups"), profileName + "_record.dat");
        File legacyReplayBackup = new File(getCategoryDir("mtr_backups"), profileName + "_replay.dat");
        File legacyStackTrace = new File(getCategoryDir("mtr_stacktrace"), profileName + ".dat");
        File scopedRecordBackup = getBackupFile(profileName, "record");
        File scopedReplayBackup = getBackupFile(profileName, "replay");
        File scopedStackTrace = getStackTraceFile(profileName);

        // 先完整检查目标，避免复制到一半才发现已有数据而留下不完整迁移。
        if (!legacyProfile.exists()
                || scopedProfile.exists()
                || (legacyRecordBackup.exists() && scopedRecordBackup.exists())
                || (legacyReplayBackup.exists() && scopedReplayBackup.exists())
                || (legacyStackTrace.exists() && scopedStackTrace.exists())) {
            return false;
        }

        try {
            copyIfPresent(legacyProfile.toPath(), scopedProfile.toPath());
            copyIfPresent(legacyRecordBackup.toPath(), scopedRecordBackup.toPath());
            copyIfPresent(legacyReplayBackup.toPath(), scopedReplayBackup.toPath());
            copyIfPresent(legacyStackTrace.toPath(), scopedStackTrace.toPath());
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

    private static String getWorldKey() {
        if (MicroTimingReplay.server == null) {
            throw new IllegalStateException("Cannot access MTR storage before the server has started");
        }

        Path worldRoot = MicroTimingReplay.server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize();
        Path fileName = worldRoot.getFileName();
        // 目录名便于人工辨认；绝对路径的短哈希避免不同位置的同名存档冲突。
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

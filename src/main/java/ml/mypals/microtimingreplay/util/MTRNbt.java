package ml.mypals.microtimingreplay.util;

import net.minecraft.nbt.CompoundTag;

/**
 * Reads with a fallback that is not the type's zero value.
 *
 * <p>Since 1.21.5 the {@link CompoundTag} getters answer with an {@code Optional} that is
 * empty when a key is absent or holds the wrong type. These wrappers keep the fallback
 * plumbing out of the event readers, exactly as they did for 1.21.1's zero-value getters.
 */
public final class MTRNbt {

    private MTRNbt() {
    }

    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag.getString(key).orElse(fallback);
    }

    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.getInt(key).orElse(fallback);
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        // Booleans are stored as bytes, so the getter refuses wrong types just like the rest.
        return tag.getBoolean(key).orElse(fallback);
    }
}

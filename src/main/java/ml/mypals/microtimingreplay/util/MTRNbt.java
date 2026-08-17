package ml.mypals.microtimingreplay.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Reads with a fallback that is not the type's zero value.
 *
 * <p>1.21.1's {@link CompoundTag} getters answer with the zero value when a key is absent
 * or holds the wrong type, so a caller that wants a different default has to test for the
 * key first. These wrappers keep that test out of the event readers.
 */
public final class MTRNbt {

    private MTRNbt() {
    }

    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        // Booleans are stored as bytes, so that is what the presence test has to ask for.
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }
}

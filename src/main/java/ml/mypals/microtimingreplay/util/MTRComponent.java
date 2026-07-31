package ml.mypals.microtimingreplay.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class MTRComponent {
    public static MutableComponent translatable(String key, String fallbackEn, Object... args) {
        return Component.translatableWithFallback(key, fallbackEn, args);
    }
}

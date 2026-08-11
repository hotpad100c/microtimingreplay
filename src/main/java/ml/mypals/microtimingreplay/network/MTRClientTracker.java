package ml.mypals.microtimingreplay.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players run the optional client add-on, so the server knows whether to push
 * packets or fall back to the built-in dialogs. A vanilla client never sends the
 * hello packet and therefore never appears here.
 */
public class MTRClientTracker {

    private static final Map<UUID, Integer> PROTOCOLS = new ConcurrentHashMap<>();

    public static void onHello(ServerPlayer player, int protocol) {
        PROTOCOLS.put(player.getUUID(), protocol);
    }

    public static void forget(ServerPlayer player) {
        PROTOCOLS.remove(player.getUUID());
    }

    public static boolean hasClient(ServerPlayer player) {
        if (player == null) return false;
        Integer protocol = PROTOCOLS.get(player.getUUID());
        return protocol != null && protocol == MTRPayloads.PROTOCOL_VERSION;
    }
}

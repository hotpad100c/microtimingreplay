package ml.mypals.microtimingreplay.client;

import ml.mypals.microtimingreplay.client.screen.MTRPanelScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Optional client add-on. The mod itself stays server-side and fully usable without
 * this — everything here replaces a command round-trip or a server-built dialog with
 * something nicer, and degrades to the old path when the server is vanilla or the
 * player lacks permission.
 */
@Environment(EnvType.CLIENT)
public class MTRClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MTRClientConfig.load();
        MTRKeys.register();
        MTRClientNetworking.registerClientHandlers();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientReplayState.reset();
            // The server replies with HelloS2C only if it speaks our protocol, which is
            // how the client learns whether to offer the panel at all.
            MTRClientNetworking.sendHello();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientReplayState.reset());

        ClientTickEvents.END_CLIENT_TICK.register(MTRClient::openPanelWhenHeld);
    }

    /** When the bound key went down, or 0 while it is up. */
    private static long heldSince = 0;

    /**
     * Hold the key with no screen up and the panel appears; it closes itself when the
     * key comes back up (see {@link MTRPanelScreen#tick()}).
     *
     * <p>The configurable hold threshold is there for players who rebind onto a key
     * they also use in play: the panel is a screen, so a stray trigger takes the mouse.
     */
    private static void openPanelWhenHeld(Minecraft minecraft) {
        if (!MTRKeys.isPanelHeld()) {
            heldSince = 0;
            return;
        }
        if (minecraft.screen != null || minecraft.player == null) return;
        if (!ClientReplayState.serverHasMod()) return;

        long now = System.currentTimeMillis();
        if (heldSince == 0) {
            heldSince = now;
            return;
        }
        if (now - heldSince < MTRClientConfig.panelHoldMillis()) return;

        minecraft.setScreen(new MTRPanelScreen());
    }
}

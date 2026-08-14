package ml.mypals.microtimingreplay.client;

import ml.mypals.microtimingreplay.client.screen.TimelineScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class TimelineAutoHide {

    private TimelineAutoHide() {
    }

    private static boolean pendingRestore = false;

    public static void markHidden() {
        pendingRestore = true;
    }

    public static void forget() {
        pendingRestore = false;
    }

    private static boolean wantsCleanView(Minecraft minecraft) {
        if (minecraft.options.hideGui) return true;
        return minecraft.player != null && !minecraft.player.isSpectator();
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            pendingRestore = false;
            return;
        }

        if (minecraft.screen instanceof TimelineScreen) {
            pendingRestore = false;
            if (ClientReplayState.cameraFollow() && !minecraft.player.isSpectator()) {
                pendingRestore = true;
                minecraft.setScreen(null);
            }
            return;
        }

        if (!pendingRestore || minecraft.screen != null) return;

        if (!wantsCleanView(minecraft) && ClientReplayState.cameraFollow()) {
            pendingRestore = false;
            minecraft.setScreen(new TimelineScreen());
        }
    }
}

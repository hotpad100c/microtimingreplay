package ml.mypals.microtimingreplay.record;

import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RecordingBossBar {

    private RecordingBossBar() {}

    private static ServerBossEvent bossBar;
    private static String profileName = "";
    private static long totalTicks = 0;

    public static void start(MinecraftServer server, String profile, long ticks) {
        stop();
        if (server == null) return;

        profileName = profile != null ? profile : "";
        totalTicks = Math.max(1, ticks);

        bossBar = new ServerBossEvent(
                title(totalTicks),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(1.0f);
        syncPlayers(server);
    }

    public static void tick(MinecraftServer server, long remainingTicks) {
        if (bossBar == null || server == null) return;

        long remaining = Math.max(0, remainingTicks);
        bossBar.setName(title(remaining));
        bossBar.setProgress(Math.clamp((float) remaining / totalTicks, 0.0f, 1.0f));
        syncPlayers(server);
    }

    public static void stop() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        profileName = "";
        totalTicks = 0;
    }

    private static net.minecraft.network.chat.Component title(long remaining) {
        return MTRComponent.translatable(
                "mtr.bossbar.recording",
                "Recording [%s] %d gt left",
                profileName, remaining);
    }

    private static void syncPlayers(MinecraftServer server) {
        Set<ServerPlayer> online = new HashSet<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!online.contains(player)) {
                bossBar.removePlayer(player);
            }
        }
        for (ServerPlayer player : online) {
            bossBar.addPlayer(player);
        }
    }
}

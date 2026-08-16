package ml.mypals.microtimingreplay.marker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class MarkerManager {

    public static void clearAll(ServerLevel level, String sessionId) {
        if (level == null) return;
        String sessionTag = sessionId == null ? null : MTRMarker.sessionTag(sessionId);
        Iterable<ServerLevel> levels = level.getServer().getAllLevels();
        for (ServerLevel sl : levels) {
            List<Entity> toDiscard = new ArrayList<>();
            for (Entity entity : sl.getAllEntities()) {
                if (entity instanceof Display) {
                    if (entity.getTags().contains("mtr_piston_display")) continue;
                    if (sessionTag != null && !entity.getTags().contains(sessionTag)) continue;

                    if (entity.getTags().contains("mtr_replay_marker") ||
                        entity.getTags().contains("mtr_marker")) {
                        toDiscard.add(entity);
                    }
                }
            }
            for (Entity entity : toDiscard) {
                entity.discard();
            }
        }
    }
}
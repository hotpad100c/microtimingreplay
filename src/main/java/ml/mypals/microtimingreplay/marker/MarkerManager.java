package ml.mypals.microtimingreplay.marker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class MarkerManager {

    private MarkerManager() {}

    public static void clearAll(ServerLevel level) {
        if (level == null) return;
        Iterable<ServerLevel> levels = level.getServer() != null ? level.getServer().getAllLevels() : List.of(level);
        for (ServerLevel sl : levels) {
            List<Entity> toDiscard = new ArrayList<>();
            for (Entity entity : sl.getAllEntities()) {
                if (entity instanceof Display) {
                    if (entity.entityTags().contains("mtr_piston_display")) continue;

                    if (entity.entityTags().contains("mtr_replay_marker") ||
                        entity.entityTags().contains("mtr_marker") ||
                        entity.entityTags().contains("mtr_area_marker") ||
                        entity.entityTags().contains("mtr_dynamic_marker")) {
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
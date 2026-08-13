package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.config.RecordMode;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;

import java.util.Locale;

public enum PhaseType {
    PACKET_PROCESS("PacketProcessPhase", "Packet Process Phase", RecordMode.NON_EMPTY),
    ASYNC_TASK("AsyncTaskPhase", "Async Task Phase", RecordMode.NON_EMPTY),
    LEVEL_TICK("LevelTickPhase", "Level Tick Phase", RecordMode.NON_EMPTY),
    PLAYER_TICK("PlayerTickPhase", "Player Tick Phase", RecordMode.NON_EMPTY),
    CHUNK_TICK("ChunkTickPhase", "Chunk Tick Phase", RecordMode.NON_EMPTY),
    BLOCK_EVENT("BlockEventPhase", "Block Event Phase", RecordMode.NON_EMPTY),
    SCHEDULED_TICK("ScheduledTickPhase", "Scheduled Tick Phase", RecordMode.NON_EMPTY),
    RANDOM_TICK("RandomTickPhase", "Random Tick Phase", RecordMode.NON_EMPTY),
    ICE_AND_SNOW("IceAndSnowPhase", "Ice And Snow Phase", RecordMode.NON_EMPTY),
    BLOCK_ENTITY("BlockEntityPhase", "Block Entity Phase", RecordMode.NON_EMPTY),
    ENTITY_TICK("EntityTickPhase", "Entity Tick Phase", RecordMode.NON_EMPTY),
    DRAGON_FIGHT("DragonFightPhase", "Dragon Fight Phase", RecordMode.NON_EMPTY);

    private final String phaseName;
    private final String filterId;
    private final String defaultName;
    private final RecordMode defaultMode;

    PhaseType(String phaseName, String defaultName, RecordMode defaultMode) {
        this.phaseName = phaseName;
        this.filterId = "phase_" + name().toLowerCase(Locale.ROOT);
        this.defaultName = defaultName;
        this.defaultMode = defaultMode;
    }

    public String phaseName() {
        return phaseName;
    }

    public String filterId() {
        return filterId;
    }

    public String defaultName() {
        return defaultName;
    }

    public RecordMode defaultMode() {
        return defaultMode;
    }

    public boolean enabled() {
        return RecordingFilterConfig.isEnabled(filterId);
    }

    public static PhaseType byPhaseName(String phaseName) {
        for (PhaseType type : values()) {
            if (type.phaseName.equals(phaseName)) {
                return type;
            }
        }
        return null;
    }
}

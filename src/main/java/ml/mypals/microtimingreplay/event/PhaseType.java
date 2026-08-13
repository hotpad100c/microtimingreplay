package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.config.RecordingFilterConfig;

import java.util.Locale;

public enum PhaseType {
    PACKET_PROCESS("PacketProcessPhase", "Packet Process Phase", true),
    ASYNC_TASK("AsyncTaskPhase", "Async Task Phase", true),
    LEVEL_TICK("LevelTickPhase", "Level Tick Phase", true),
    PLAYER_TICK("PlayerTickPhase", "Player Tick Phase", true),
    CHUNK_TICK("ChunkTickPhase", "Chunk Tick Phase", true),
    BLOCK_EVENT("BlockEventPhase", "Block Event Phase", true),
    SCHEDULED_TICK("ScheduledTickPhase", "Scheduled Tick Phase", true),
    RANDOM_TICK("RandomTickPhase", "Random Tick Phase", true),
    ICE_AND_SNOW("IceAndSnowPhase", "Ice And Snow Phase", true),
    BLOCK_ENTITY("BlockEntityPhase", "Block Entity Phase", true),
    ENTITY_TICK("EntityTickPhase", "Entity Tick Phase", true),
    DRAGON_FIGHT("DragonFightPhase", "Dragon Fight Phase", true);

    private final String phaseName;
    private final String filterId;
    private final String defaultName;
    private final boolean defaultEnabled;

    PhaseType(String phaseName, String defaultName, boolean defaultEnabled) {
        this.phaseName = phaseName;
        this.filterId = "phase_" + name().toLowerCase(Locale.ROOT);
        this.defaultName = defaultName;
        this.defaultEnabled = defaultEnabled;
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

    public boolean defaultEnabled() {
        return defaultEnabled;
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

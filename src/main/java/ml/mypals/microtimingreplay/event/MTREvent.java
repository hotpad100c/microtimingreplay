package ml.mypals.microtimingreplay.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public abstract class MTREvent {
    private String type;
    private long tick;

    public MTREvent() {}

    public MTREvent(String type, long tick) {
        this.type = type;
        this.tick = tick;
    }

    public String getType() {
        return type;
    }

    public long getTick() {
        return tick;
    }

    public void apply(ServerLevel level, boolean forward) {
        // Base implementation does nothing
    }

    public abstract CompoundTag writeNBT();

    public static MTREvent readNBT(CompoundTag tag) {
        String eventType = tag.getString("type").orElse("unknown");
        if ("setBlock".equals(eventType)) {
            return SetBlockEvent.readNBT(tag);
        }
        return new MTREvent(eventType, tag.getLong("tick").orElse(0L)) {
            @Override
            public CompoundTag writeNBT() {
                CompoundTag fallback = new CompoundTag();
                fallback.putString("type", getType());
                fallback.putLong("tick", getTick());
                return fallback;
            }
        };
    }
}

package ml.mypals.microtimingreplay.profile;

import ml.mypals.microtimingreplay.event.MTREvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public class TickFrame {
    private long tick;
    private List<MTREvent> events;

    public TickFrame() {}

    public TickFrame(long tick) {
        this.tick = tick;
        this.events = new ArrayList<>();
    }

    public long getTick() {
        return tick;
    }

    public List<MTREvent> getEvents() {
        return events;
    }

    public void addEvent(MTREvent event) {
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        this.events.add(event);
    }

    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("tick", tick);
        ListTag eventsList = new ListTag();
        for (MTREvent event : events) {
            eventsList.add(event.writeNBT());
        }
        tag.put("events", eventsList);
        return tag;
    }

    public static TickFrame readNBT(CompoundTag tag) {
        TickFrame frame = new TickFrame(tag.getLong("tick").orElse(0L));
        ListTag eventsList = tag.getList("events").orElse(new ListTag());
        for (int i = 0; i < eventsList.size(); i++) {
            frame.addEvent(MTREvent.readNBT(eventsList.getCompound(i).orElse(new CompoundTag())));
        }
        return frame;
    }
}

package ml.mypals.microtimingreplay.profile;

import ml.mypals.microtimingreplay.event.MTREvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public class MTRProfile {
    private String name;
    private long createdAt;
    // Ticks recorded so far
    private int ticksRecorded;
    private List<TickFrame> frames;

    // Multi-Area Selection
    public static class Area {
        public String name;
        public int x1, y1, z1;
        public int x2, y2, z2;

        public Area(String name, int ax, int ay, int az, int bx, int by, int bz) {
            this.name = name;
            this.x1 = Math.min(ax, bx);
            this.y1 = Math.min(ay, by);
            this.z1 = Math.min(az, bz);
            this.x2 = Math.max(ax, bx);
            this.y2 = Math.max(ay, by);
            this.z2 = Math.max(az, bz);
        }

        public CompoundTag writeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putInt("x1", x1);
            tag.putInt("y1", y1);
            tag.putInt("z1", z1);
            tag.putInt("x2", x2);
            tag.putInt("y2", y2);
            tag.putInt("z2", z2);
            return tag;
        }

        public static Area readNBT(CompoundTag tag) {
            return new Area(
                tag.getString("name").orElse(""),
                tag.getInt("x1").orElse(0), tag.getInt("y1").orElse(0), tag.getInt("z1").orElse(0),
                tag.getInt("x2").orElse(0), tag.getInt("y2").orElse(0), tag.getInt("z2").orElse(0)
            );
        }
    }

    private List<Area> areas = new ArrayList<>();

    public MTRProfile(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.ticksRecorded = 0;
        this.frames = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getTicksRecorded() {
        return ticksRecorded;
    }

    public void setTicksRecorded(int ticksRecorded) {
        this.ticksRecorded = ticksRecorded;
    }

    public List<TickFrame> getFrames() {
        return frames;
    }

    public void addEvent(long tick, MTREvent event) {
        if (frames.isEmpty() || frames.get(frames.size() - 1).getTick() != tick) {
            frames.add(new TickFrame(tick));
        }
        frames.get(frames.size() - 1).addEvent(event);
    }

    public List<Area> getAreas() {
        return areas;
    }

    public Area getArea(String name) {
        for (Area area : areas) {
            if (area.name.equals(name)) return area;
        }
        return null;
    }

    public String addArea(String name, BlockPos p1, BlockPos p2) {
        if (name == null || name.isEmpty()) {
            int maxId = 0;
            for (Area area : areas) {
                try {
                    int id = Integer.parseInt(area.name);
                    if (id > maxId) maxId = id;
                } catch (NumberFormatException ignored) {}
            }
            name = String.valueOf(maxId + 1);
        }
        // If it already exists, don't overwrite blindly
        if (getArea(name) != null) return null;

        areas.add(new Area(name, p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ()));
        return name;
    }

    public boolean modifyAreaPos(String name, BlockPos p1, BlockPos p2) {
        Area area = getArea(name);
        if (area == null) return false;
        area.x1 = Math.min(p1.getX(), p2.getX());
        area.y1 = Math.min(p1.getY(), p2.getY());
        area.z1 = Math.min(p1.getZ(), p2.getZ());
        area.x2 = Math.max(p1.getX(), p2.getX());
        area.y2 = Math.max(p1.getY(), p2.getY());
        area.z2 = Math.max(p1.getZ(), p2.getZ());
        return true;
    }

    public boolean renameArea(String oldName, String newName) {
        Area area = getArea(oldName);
        if (area == null) return false;
        if (getArea(newName) != null) return false; // Name taken
        area.name = newName;
        return true;
    }

    public boolean removeArea(String name) {
        return areas.removeIf(a -> a.name.equals(name));
    }

    public boolean isInArea(BlockPos pos) {
        if (areas.isEmpty()) return false;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (Area area : areas) {
            if (x >= area.x1 && x <= area.x2 && y >= area.y1 && y <= area.y2 && z >= area.z1 && z <= area.z2) {
                return true;
            }
        }
        return false;
    }

    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putLong("createdAt", createdAt);
        tag.putInt("ticksRecorded", ticksRecorded);
        
        ListTag framesList = new ListTag();
        for (TickFrame frame : frames) {
            framesList.add(frame.writeNBT());
        }
        tag.put("frames", framesList);
        
        ListTag areasList = new ListTag();
        for (Area area : areas) {
            areasList.add(area.writeNBT());
        }
        tag.put("areas", areasList);
        
        return tag;
    }

    public static MTRProfile readNBT(CompoundTag tag) {
        MTRProfile profile = new MTRProfile(tag.getString("name").orElse("unknown"));
        profile.createdAt = tag.getLong("createdAt").orElse(0L);
        profile.ticksRecorded = tag.getInt("ticksRecorded").orElse(0);
        
        ListTag framesList = tag.getList("frames").orElse(new ListTag());
        for (int i = 0; i < framesList.size(); i++) {
            profile.frames.add(TickFrame.readNBT(framesList.getCompound(i).orElse(new CompoundTag())));
        }
        
        ListTag areasList = tag.getList("areas").orElse(new ListTag());
        for (int i = 0; i < areasList.size(); i++) {
            profile.areas.add(Area.readNBT(areasList.getCompound(i).orElse(new CompoundTag())));
        }
        
        return profile;
    }
}

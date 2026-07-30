package ml.mypals.microtimingreplay.event;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SetBlockEvent extends MTREvent {
    private int x, y, z;
    private int oldStateId;
    private int newStateId;

    public SetBlockEvent() {}

    public SetBlockEvent(long tick, int x, int y, int z, int oldStateId, int newStateId) {
        super("setBlock", tick);
        this.x = x;
        this.y = y;
        this.z = z;
        this.oldStateId = oldStateId;
        this.newStateId = newStateId;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getOldStateId() { return oldStateId; }
    public int getNewStateId() { return newStateId; }

    @Override
    public void apply(ServerLevel level, boolean forward) {
        int stateId = forward ? getNewStateId() : getOldStateId();
        BlockState state = Block.stateById(stateId);
        BlockPos pos = new BlockPos(getX(), getY(), getZ());
        level.setBlock(pos, state, 2 | 816, 0);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType());
        tag.putLong("tick", getTick());
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putInt("oldStateId", oldStateId);
        tag.putInt("newStateId", newStateId);
        return tag;
    }

    public static SetBlockEvent readNBT(CompoundTag tag) {
        long tick = tag.getLong("tick").orElse(0L);
        int x = tag.getInt("x").orElse(0);
        int y = tag.getInt("y").orElse(0);
        int z = tag.getInt("z").orElse(0);
        int oldStateId = tag.getInt("oldStateId").orElse(0);
        int newStateId = tag.getInt("newStateId").orElse(0);
        return new SetBlockEvent(tick, x, y, z, oldStateId, newStateId);
    }
}

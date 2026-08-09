package ml.mypals.microtimingreplay.event;

public class MTREvents {
    static {
        MTREvent.register(SetBlockEvent.TYPE, SetBlockEvent::readNBT);
        MTREvent.register(PhaseEvent.TYPE, PhaseEvent::readNBT);
        MTREvent.register(LevelTickEvent.TYPE, LevelTickEvent::readNBT);
        MTREvent.register(QueueEvent.TYPE, QueueEvent::readNBT);
        MTREvent.register(UpdateEvent.TYPE, UpdateEvent::readNBT);
        MTREvent.register(BlockPosEvent.TYPE, BlockPosEvent::readNBT);
        MTREvent.register(AddBlockEventEvent.TYPE, AddBlockEventEvent::readNBT);
        MTREvent.register(AddScheduleTickEvent.TYPE, AddScheduleTickEvent::readNBT);
        MTREvent.register(PostGameEventEvent.TYPE, PostGameEventEvent::readNBT);
        MTREvent.register(ReceivedGameEventEvent.TYPE, ReceivedGameEventEvent::readNBT);
        MTREvent.register(MovingPistonEvent.TYPE, MovingPistonEvent::readNBT);
        MTREvent.register(MovingPistonTickEvent.TYPE, MovingPistonTickEvent::readNBT);
        MTREvent.register(EntitySpawnEvent.TYPE, EntitySpawnEvent::readNBT);
        MTREvent.register(EntityTickEvent.TYPE, EntityTickEvent::readNBT);
        MTREvent.register(BlockEntityTickEvent.TYPE, BlockEntityTickEvent::readNBT);
        MTREvent.register(EntityMoveEvent.TYPE, EntityMoveEvent::readNBT);
        MTREvent.register(ItemTransferEvent.TYPE, ItemTransferEvent::readNBT);
    }

    public static void init() {
    }
}

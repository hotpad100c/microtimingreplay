package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.config.MTRGameRules;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class EntityTickEvent extends Vec3PosEvent {
    public static final String TYPE = "entityTick";

    private final String entityUuid;
    private final String entityType;

    public EntityTickEvent(long tick, String entityUuid, String entityType, double x, double y, double z, String dimension) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.entityUuid = entityUuid;
        this.entityType = entityType != null ? entityType : "unknown";
    }

    public String getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }

    @Override
    public boolean isQueueScope() {
        return true;
    }

    @Override
    public boolean saveEvenWithoutAction(MinecraftServer server) {
        return !server.getGameRules().get(MTRGameRules.SKIP_EMPTY_ENTITY_TICK);
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.GOLD;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return appendPosText(MTRComponent.translatable("mtr.scoreboard.event.leaf.entitytick", "[Entity Tick] " + entityType));
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.entity_tick_title",
                "Entity Tick @ [%.2f, %.2f, %.2f]",
                getX(), getY(), getZ()
        ).append(Component.literal("\n")).withStyle(getColor());

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
        .append(MTRComponent.translatable("mtr.tooltip.target", "Type: %s", entityType).withStyle(ChatFormatting.AQUA))
        .append(Component.literal("\nUUID: ").withStyle(ChatFormatting.GRAY))
        .append(Component.literal(entityUuid != null ? entityUuid : "null").withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void apply(ServerLevel level, boolean forward) {
        super.apply(level, forward);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("entityUuid", entityUuid != null ? entityUuid : "");
        tag.putString("entityType", entityType != null ? entityType : "");
        return tag;
    }

    public static EntityTickEvent readNBT(CompoundTag tag) {
        EntityTickEvent event = new EntityTickEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("entityUuid").orElse(""),
                tag.getString("entityType").orElse(""),
                tag.getDouble("x").orElse(0.0),
                tag.getDouble("y").orElse(0.0),
                tag.getDouble("z").orElse(0.0),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

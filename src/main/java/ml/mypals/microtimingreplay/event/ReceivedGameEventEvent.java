package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@SuppressWarnings("GrazieInspectionRunner")
public class ReceivedGameEventEvent extends Vec3PosEvent {

    public static final String TYPE = "receivedGameEvent";
    
    private final double originX;
    private final double originY;
    private final double originZ;
    private final String sourceUUID; //Empty = null
    private final String projectileOwnerUUID;

    public ReceivedGameEventEvent(long tick, double listenerX, double listenerY, double listenerZ, double originX, double originY, double originZ, String sourceUUID, String projectileOwnerUUID, String dimension) {
        super(tick, TYPE, new Vec3(listenerX, listenerY, listenerZ), dimension);
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sourceUUID = sourceUUID;
        this.projectileOwnerUUID = projectileOwnerUUID;
    }

    public Vec3 getOrigin() { return new Vec3(originX, originY, originZ); }
    public String getSourceUUID() { return sourceUUID.isEmpty() ? null : sourceUUID; }
    public String getProjectileOwnerUUID() { return projectileOwnerUUID.isEmpty() ? null : projectileOwnerUUID; }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.DARK_AQUA;
    }

    public static ReceivedGameEventEvent readNBT(CompoundTag tag) {
        ReceivedGameEventEvent event = new ReceivedGameEventEvent(
            tag.getLong("tick").orElse(0L),
            tag.getDouble("x").orElse(0d),
            tag.getDouble("y").orElse(0d),
            tag.getDouble("z").orElse(0d),
            tag.getDouble("originX").orElse(0d),
            tag.getDouble("originY").orElse(0d),
            tag.getDouble("originZ").orElse(0d),
            tag.getString("sourceUUID").orElse(""),
            tag.getString("projectileOwnerUUID").orElse(""),
            tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putDouble("originX", originX);
        tag.putDouble("originY", originY);
        tag.putDouble("originZ", originZ);
        tag.putString("sourceUUID", sourceUUID != null ? sourceUUID : "");
        tag.putString("projectileOwnerUUID", projectileOwnerUUID != null ? projectileOwnerUUID : "");
        return tag;
    }

    @Override
    public void display(ServerLevel level) {
        Vec3 pos = getPos();

        Vector3f scale = new Vector3f(1.005f, 1.005f, 1.005f);
        Vec3 boxOrigin = pos.subtract(scale.x() / 2.0, scale.y() / 2.0, scale.z() / 2.0);
        MTRMarker.spawnBlockDisplay(level, boxOrigin, Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), scale, ChatFormatting.DARK_AQUA);
        MutableComponent mutableComponent = Component.literal("ReceivedGameEvent").withStyle(ChatFormatting.DARK_AQUA);
        
        String srcId = getSourceUUID();
        if (srcId != null) {
            mutableComponent.append("\nSourceEntity: ").append(Component.literal(srcId).withStyle(ChatFormatting.YELLOW));
        }
        
        MTRMarker.spawnTextDisplay(level, pos.x(), pos.y() + 0.8, pos.z(), mutableComponent, 0.7f);

        Vector3f originScale = new Vector3f(0.5f, 0.5f, 0.5f);
        Vec3 originPos = getOrigin().subtract(0.25, 0.25, 0.25);
        MTRMarker.spawnBlockDisplay(level, originPos, Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(), originScale, ChatFormatting.LIGHT_PURPLE);
        MTRMarker.spawnTextDisplay(level, getOrigin().x(), getOrigin().y() + 0.5, getOrigin().z(), Component.literal("Vibration Origin").withStyle(ChatFormatting.LIGHT_PURPLE), 0.5f);
    }
}

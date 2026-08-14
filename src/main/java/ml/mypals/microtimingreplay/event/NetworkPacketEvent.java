package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.util.MTRComponent;
import ml.mypals.microtimingreplay.util.PacketDetailFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class NetworkPacketEvent extends Vec3PosEvent {
    public static final String TYPE = "networkPacket";

    private final String packetName;
    private final String direction;
    private final String player;
    private final String packetDetail;

    public NetworkPacketEvent(long tick, String packetName, String direction, String player, String packetDetail, double x, double y, double z, String dimension) {
        super(tick, TYPE, new Vec3(x, y, z), dimension);
        this.packetName = packetName != null ? packetName : "UnknownPacket";
        this.direction = direction != null ? direction : "RECEIVE";
        this.player = player != null ? player : "";
        this.packetDetail = packetDetail != null ? packetDetail : "";
    }

    public String getPacketName() {
        return packetName;
    }

    public String getDirection() {
        return direction;
    }

    public String getPlayer() {
        return player;
    }

    public String getPacketDetail() {
        return packetDetail;
    }


    @Override
    public String filterId() {
        return "network_packet";
    }

    @Override
    public ChatFormatting getColor() {
        return "SEND".equalsIgnoreCase(direction) ? ChatFormatting.DARK_RED : ChatFormatting.DARK_AQUA;
    }

    @Override
    public MutableComponent getScoreboardText() {
        return MTRComponent.translatable(
                "mtr.scoreboard.event.leaf.networkpacket",
                "NetworkPacket " + direction,
                direction
        );
    }

    @Override
    public MutableComponent fillHoverText() {
        MutableComponent text = MTRComponent.translatable("mtr.tooltip.network_packet_title", "Network Packet").withStyle(getColor());

        text.append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.network_packet_direction", "Direction: %s", direction).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(MTRComponent.translatable("mtr.tooltip.network_packet_name", "Packet: %s", packetName).withStyle(ChatFormatting.YELLOW));

        if (player != null && !player.isEmpty()) {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.network_packet_player", "Player: %s", player).withStyle(ChatFormatting.AQUA));
        }

        if (packetDetail != null && !packetDetail.isEmpty()) {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.network_packet_details", "Details:").withStyle(ChatFormatting.GRAY));
            for (String line : packetDetail.split("\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                text.append(Component.literal("\n  "));
                int split = line.indexOf(PacketDetailFormatter.KV_SEPARATOR);
                if (split < 0) {
                    text.append(Component.literal(line).withStyle(ChatFormatting.WHITE));
                    continue;
                }
                String value = line.substring(split + PacketDetailFormatter.KV_SEPARATOR.length());
                text.append(Component.literal(line.substring(0, split)).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(PacketDetailFormatter.KV_SEPARATOR).withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(value).withStyle(valueColor(value)));
            }
        }

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD));
        }

        return text;
    }

    /** 按值的形状挑个颜色，纯启发式：detail 存的是字符串，渲染时已经没有类型信息了。 */
    private static ChatFormatting valueColor(String value) {
        if (value.isEmpty() || "null".equals(value) || "empty".equals(value)) {
            return ChatFormatting.DARK_GRAY;
        }
        char first = value.charAt(0);
        if (first == '[' || first == '{') {
            return ChatFormatting.GRAY;
        }
        if ("true".equals(value)) {
            return ChatFormatting.GREEN;
        }
        if ("false".equals(value)) {
            return ChatFormatting.RED;
        }
        if (first == '-' || (first >= '0' && first <= '9')) {
            return ChatFormatting.GOLD;
        }
        if (value.indexOf(':') >= 0) {
            return ChatFormatting.YELLOW;
        }
        // 全大写下划线的基本都是枚举常量
        if (value.equals(value.toUpperCase(java.util.Locale.ROOT)) && value.chars().anyMatch(Character::isLetter)) {
            return ChatFormatting.LIGHT_PURPLE;
        }
        return ChatFormatting.WHITE;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putString("packetName", packetName != null ? packetName : "");
        tag.putString("direction", direction != null ? direction : "");
        tag.putString("player", player != null ? player : "");
        tag.putString("packetDetail", packetDetail != null ? packetDetail : "");
        return tag;
    }

    public static NetworkPacketEvent readNBT(CompoundTag tag) {
        NetworkPacketEvent event = new NetworkPacketEvent(
                tag.getLong("tick").orElse(0L),
                tag.getString("packetName").orElse(""),
                tag.getString("direction").orElse(""),
                tag.getString("player").orElse(""),
                tag.getString("packetDetail").orElse(""),
                tag.getDouble("x").orElse(0.0),
                tag.getDouble("y").orElse(0.0),
                tag.getDouble("z").orElse(0.0),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
    @Override
    public void display(ServerLevel level, Vector3f scale) {
    }
}

package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.PacketSendListener;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.NetworkPacketEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.util.PacketDetailFormatter;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ServerNetworkPacketMixin {

    @Shadow
    private PacketListener packetListener;

    // 三个 send 重载最终都汇入这个三参版本；单参的 send(Packet) 服务端从来不调，
    // ServerCommonPacketListenerImpl 直接调双参/三参，所以只钩单参会一条都记不到。
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At("HEAD"))
    private void mtr$onSendPacket(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci) {
        mtr$recordPacket(packet);
    }

    @WrapMethod(method = "genericsFtw")
    private static <T extends PacketListener> void mtr$onReceivePacket(Packet<T> packet, PacketListener listener, Operation<Void> original) {
        if (listener instanceof ServerGamePacketListenerImpl impl) {
            ServerPlayer player = impl.player;
            player.level();
            Level level = player.level();
            if (MTRState.isRecording(level) && RecordingFilterConfig.isEnabled("network_packet")) {
                String dim = level.dimension().location().toString();
                MTRProfile profile = MTRState.getActiveProfile();
                Vec3 pos = player.position();
                if (profile != null && !profile.outsideAreaVec3(pos, dim)) {
                    long tick = level.getServer().getTickCount() - MTRState.getRecordStartTick();
                    String packetName = packet.getClass().getSimpleName();
                    NetworkPacketEvent event = new NetworkPacketEvent(
                            tick,
                            packetName,
                            "RECEIVE",
                            player.getScoreboardName(),
                            PacketDetailFormatter.describe(packet),
                            pos.x, pos.y, pos.z,
                            dim
                    );
                    MTRState.pushEvent(event);
                    try {
                        original.call(packet, listener);
                        return;
                    } finally {
                        MTRState.popEvent();
                    }
                }
            }
        }
        original.call(packet, listener);
    }

    @Unique
    private void mtr$recordPacket(Packet<?> packet) {
        if (this.packetListener instanceof ServerGamePacketListenerImpl impl) {
            ServerPlayer player = impl.player;
            player.level();
            Level level = player.level();
            if (MTRState.isRecording(level) && RecordingFilterConfig.isEnabled("network_packet")) {
                String dim = level.dimension().location().toString();
                MTRProfile profile = MTRState.getActiveProfile();
                Vec3 pos = player.position();
                if (profile != null && !profile.outsideAreaVec3(pos, dim)) {
                    long tick = level.getServer().getTickCount() - MTRState.getRecordStartTick();
                    String packetName = packet.getClass().getSimpleName();
                    NetworkPacketEvent event = new NetworkPacketEvent(
                            tick,
                            packetName,
                            "SEND",
                            player.getScoreboardName(),
                            PacketDetailFormatter.describe(packet),
                            pos.x, pos.y, pos.z,
                            dim
                    );
                    MTRState.recordStep(event);
                }
            }
        }
    }
}

package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.event.EntityTickEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import ml.mypals.microtimingreplay.util.PlayerProxy;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerTickMixin {

    @Shadow
    public ServerPlayer player;

    // 1.21.1 folds the player tick straight into the listener's own tick().
    @WrapMethod(method = "tick")
    private void mtr$onTickPlayer(Operation<Void> original) {
        Level level = this.player.level();
        if (!MTRState.isRecording(level)) {
            original.call();
            return;
        }

        MTRProfile profile = MTRState.getActiveProfile();
        String dim = level.dimension().location().toString();
        if (profile == null || profile.outsideAreaVec3(this.player.position(), dim)) {
            original.call();
            return;
        }

        MTRState.pushEvent(new EntityTickEvent(
                level.getServer().getTickCount() - MTRState.getRecordStartTick(),
                PlayerProxy.replayUuid(this.player).toString(),
                PlayerProxy.typeKey(this.player),
                this.player.getX(), this.player.getY(), this.player.getZ(),
                dim
        ));
        try {
            original.call();
        } finally {
            MTRState.popEvent();
        }
    }
}

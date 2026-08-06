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

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerTickMixin {

    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "tickPlayer")
    private boolean mtr$onTickPlayer(Operation<Boolean> original) {
        Level level = this.player.level();
        if (!MTRState.isRecording(level)) {
            return original.call();
        }

        MTRProfile profile = MTRState.getActiveProfile();
        String dim = level.dimension().identifier().toString();
        if (profile == null || profile.outsideAreaVec3(this.player.position(), dim)) {
            return original.call();
        }

        MTRState.pushEvent(new EntityTickEvent(
                level.getServer().getTickCount() - MTRState.getRecordStartTick(),
                this.player.getUUID().toString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(this.player.getType()).toString(),
                this.player.getX(), this.player.getY(), this.player.getZ(),
                dim
        ));
        try {
            return original.call();
        } finally {
            MTRState.popEvent();
        }
    }
}

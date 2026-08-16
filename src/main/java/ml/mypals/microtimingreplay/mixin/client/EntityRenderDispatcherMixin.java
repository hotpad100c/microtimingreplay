package ml.mypals.microtimingreplay.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    /**
     * A stand-in is placed exactly where the replay wants it and never moves between ticks,
     * so interpolating it against the frame's partial tick only smears it. 1.21.1 has no
     * render-state extraction step, so the partial tick is pinned on the render call itself.
     */
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float mtr$freezeReplayEntityPartialTick(float partialTicks, Entity entity,
                                                    double x, double y, double z,
                                                    float rotationYaw, float unusedPartialTicks,
                                                    PoseStack poseStack, MultiBufferSource buffers,
                                                    int packedLight) {
        return EntityReplayManager.isReplayEntity(entity) ? 1f : partialTicks;
    }
}

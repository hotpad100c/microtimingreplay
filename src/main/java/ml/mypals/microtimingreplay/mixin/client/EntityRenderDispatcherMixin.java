package ml.mypals.microtimingreplay.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    /**
     * A stand-in is placed exactly where the replay wants it and never moves between ticks,
     * so interpolating it against the frame's partial tick only smears it. Since 1.21.2 the
     * partial tick is pinned during render-state extraction, before any renderer runs.
     */
    @WrapMethod(method = "extractEntity")
    private EntityRenderState mtr$freezeReplayEntityPartialTick(Entity entity, float partialTicks, Operation<EntityRenderState> original) {
        if (EntityReplayManager.isReplayEntity(entity)) {
            return original.call(entity, 1f);
        }
        return original.call(entity, partialTicks);
    }
}

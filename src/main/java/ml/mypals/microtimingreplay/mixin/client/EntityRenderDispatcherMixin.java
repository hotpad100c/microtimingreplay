package ml.mypals.microtimingreplay.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @WrapMethod(method = "extractEntity")
    private EntityRenderState mtr$onClientEntityTick(Entity entity, float partialTicks, Operation<EntityRenderState> original) {
   
        if (entity.level().isClientSide()) {
            PlayerTeam team = entity.getTeam();
            if (team != null && "MTRReplayEntity".equals(team.getName())) {
                return original.call(entity, 1f);
            }
        }
        return original.call(entity, partialTicks);
    }
}

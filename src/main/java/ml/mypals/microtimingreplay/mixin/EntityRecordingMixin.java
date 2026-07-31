package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.event.EntityMoveEvent;
import ml.mypals.microtimingreplay.event.EntitySpawnEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityRecordingMixin {

    @Inject(method = "onRemoval", at = @At("HEAD"))
    private void mtr$onEntityRemovedFromWorld(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) return;
        if (entity.level().isClientSide()) return;
        if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) return;

        if (MTRState.isRecording(entity.level())) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().identifier().toString();
                if (!activeProfile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
                    String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

                    EntitySpawnEvent event = new EntitySpawnEvent(
                        currentTick,
                        entity.getUUID().toString(),
                        entityTypeKey,
                        new CompoundTag(),
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot(),
                        true, // despawn
                        dim
                    );

                    MTRState.recordStep(event);
                }
            }
        }
    }

    @WrapMethod(method = "move")
    private void mtr$onEntityMove(MoverType type, Vec3 vec, Operation<Void> original) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player || entity.level().isClientSide() || entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) {
            original.call(type, vec);
            return;
        }

        if (MTRState.isRecording(entity.level())) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().identifier().toString();
                Vec3 oldPos = entity.position();

                original.call(type, vec);

                Vec3 newPos = entity.position();

                if (oldPos.distanceToSqr(newPos) > 1e-7) {
                    if (!activeProfile.outsideAreaVec3(oldPos, dim) || !activeProfile.outsideAreaVec3(newPos, dim)) {
                        long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
                        String uuid = entity.getUUID().toString();
                        String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                        Vec3 delta = entity.getDeltaMovement();

                        EntityMoveEvent moveEvent = new EntityMoveEvent(
                            currentTick, uuid, entityTypeKey,
                            oldPos.x(), oldPos.y(), oldPos.z(),
                            newPos.x(), newPos.y(), newPos.z(),
                            entity.getYRot(), entity.getXRot(),
                            delta.x(), delta.y(), delta.z(),
                            dim
                        );

                        MTRState.recordStep(moveEvent);
                    }
                }
                return;
            }
        }
        original.call(type, vec);
    }
}

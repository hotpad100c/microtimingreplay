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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ml.mypals.microtimingreplay.util.PlayerProxy;

@Mixin(Entity.class)
public abstract class EntityRecordingMixin {

    @Inject(method = "onRemoval", at = @At("HEAD"))
    private void mtr$onEntityRemovedFromWorld(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()) return;
        if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) return;

        if (MTRState.isRecording(entity.level())) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().identifier().toString();
                if (!activeProfile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

                    // Snapshot even though it is leaving: stepping backwards over this
                    // event has to be able to rebuild it.
                    EntitySpawnEvent event = new EntitySpawnEvent(
                        currentTick,
                        PlayerProxy.replayUuid(entity).toString(),
                        PlayerProxy.typeKey(entity),
                        PlayerProxy.snapshotNbt(entity, entity.level().registryAccess()),
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
        if (entity.level().isClientSide() || entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) {
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
                        String uuid = PlayerProxy.replayUuid(entity).toString();
                        String entityTypeKey = PlayerProxy.typeKey(entity);
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

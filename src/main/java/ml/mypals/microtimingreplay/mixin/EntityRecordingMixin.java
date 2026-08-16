package ml.mypals.microtimingreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.event.EntityCollideAxisEvent;
import ml.mypals.microtimingreplay.event.EntityMoveEvent;
import ml.mypals.microtimingreplay.event.EntitySpawnEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import ml.mypals.microtimingreplay.util.PlayerProxy;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(Entity.class)
public abstract class EntityRecordingMixin {

    // 1.21.1 has no onRemoval hook; remove(RemovalReason) is the single funnel for despawns.
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void mtr$onEntityRemovedFromWorld(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()) return;
        if (entity.getTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) return;

        if (MTRState.isRecording(entity.level())) {
            if (!RecordingFilterConfig.isEnabled("entity_despawn")) return;
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().location().toString();
                if (!activeProfile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();

                    EntitySpawnEvent event = new EntitySpawnEvent(
                        currentTick,
                        PlayerProxy.replayUuid(entity).toString(),
                        PlayerProxy.typeKey(entity),
                        PlayerProxy.snapshotNbt(entity, entity.level().registryAccess()),
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot(),
                        true, // despawn
                        dim,entity.getId()
                    );

                    MTRState.recordStep(event);
                }
            }
        }
    }

    @WrapMethod(method = "move")
    private void mtr$onEntityMove(MoverType type, Vec3 vec, Operation<Void> original) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide() || entity.getTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) {
            original.call(type, vec);
            return;
        }

        if (MTRState.isRecording(entity.level())) {
            boolean recordMove = RecordingFilterConfig.isEnabled("entity_move");
            boolean recordAxis = RecordingFilterConfig.isEnabled("entity_collide_axis");

            if (!recordMove && !recordAxis) {
                original.call(type, vec);
                return;
            }

            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().location().toString();
                Vec3 oldPos = entity.position();

                original.call(type, vec);

                Vec3 newPos = entity.position();

                if (oldPos.distanceToSqr(newPos) > 1e-7 || vec.lengthSqr() > 1e-7) {
                    if (!activeProfile.outsideAreaVec3(oldPos, dim) || !activeProfile.outsideAreaVec3(newPos, dim)) {
                        long currentTick = MicroTimingReplay.server.getTickCount() - MTRState.getRecordStartTick();
                        String uuid = PlayerProxy.replayUuid(entity).toString();
                        String entityTypeKey = PlayerProxy.typeKey(entity);
                        Vec3 delta = entity.getDeltaMovement();

                        if (recordMove) {
                            EntityMoveEvent moveEvent = new EntityMoveEvent(
                                currentTick, uuid, entityTypeKey,
                                oldPos.x(), oldPos.y(), oldPos.z(),
                                newPos.x(), newPos.y(), newPos.z(),
                                entity.getYRot(), entity.getXRot(),
                                delta.x(), delta.y(), delta.z(),
                                dim
                            );

                            MTRState.pushEvent(moveEvent);
                            try {
                                if (recordAxis) {
                                    recordAxisEvents(entity, currentTick, uuid, entityTypeKey, oldPos, newPos, vec, dim);
                                }
                            } finally {
                                MTRState.popEvent();
                            }
                        } else if (recordAxis) {
                            recordAxisEvents(entity, currentTick, uuid, entityTypeKey, oldPos, newPos, vec, dim);
                        }
                    }
                }
                return;
            }
        }
        original.call(type, vec);
    }

    /**
     * The order {@code Entity.collide} resolves axes in: vertical first, then the smaller
     * horizontal component last. 1.21.1 has no {@code Direction.axisStepOrder}, so the
     * recorder mirrors the same choice here.
     */
    @Unique
    private static Direction.Axis[] mtr$axisStepOrder(Vec3 movement) {
        return Math.abs(movement.x) < Math.abs(movement.z)
                ? new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X}
                : new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z};
    }

    @Unique
    private void recordAxisEvents(Entity entity, long tick, String uuid, String typeKey, Vec3 oldPos,
                                  Vec3 newPos, Vec3 reqVec, String dim) {
        Vec3 actual = newPos.subtract(oldPos);
        Vec3 curr = oldPos;

        float yRot = entity.getYRot();
        float xRot = entity.getXRot();

        for (Direction.Axis axis : mtr$axisStepOrder(reqVec)) {
            double req = reqVec.get(axis);
            double act = actual.get(axis);

            if (Math.abs(req) > 1e-6 || Math.abs(act) > 1e-6) {
                Vec3 next = curr.add(
                        axis == Direction.Axis.X ? act : 0,
                        axis == Direction.Axis.Y ? act : 0,
                        axis == Direction.Axis.Z ? act : 0
                );

                MTRState.recordStep(new EntityCollideAxisEvent(
                        tick, uuid, typeKey, axis.getName().toUpperCase(Locale.ROOT),
                        curr.x(), curr.y(), curr.z(),
                        next.x(), next.y(), next.z(),
                        yRot, xRot,
                        req, act, dim
                ));
                curr = next;
            }
        }
    }
}

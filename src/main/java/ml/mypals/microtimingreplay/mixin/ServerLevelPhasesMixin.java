package ml.mypals.microtimingreplay.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.storage.TagValueOutput;

import ml.mypals.microtimingreplay.event.EntitySpawnEvent;
import ml.mypals.microtimingreplay.event.EntityTickEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.event.PhaseEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTickList;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPhasesMixin {

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "runBlockEvents", at = @At("HEAD"))
    private void mtr$onRunBlockEventHead(CallbackInfo ci) {
        if (MTRState.isRecording((ServerLevel) (Object) this)) {
            MTRState.pushEvent(new PhaseEvent(
                this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                "BlockEventPhase"
            ));
        }
    }

    @Inject(method = "runBlockEvents", at = @At("RETURN"))
    private void mtr$onRunBlockEventReturn(CallbackInfo ci) {
        if (MTRState.isRecording((ServerLevel) (Object) this)) {
            MTRState.popEvent();
        }
    }

    // Because mappings might vary, we'll try injecting into doBlockEvent first.
    @Inject(method = "tick", at = @At(ordinal = 0, target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", value = "INVOKE", shift = At.Shift.BEFORE))
    private void mtr$onDoBlockTileTickHead(BooleanSupplier haveTime, CallbackInfo ci) {
        if (MTRState.isRecording((ServerLevel) (Object) this)) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "ScheduledTickPhase"
            ));
        }
    }

    @Inject(method = "tick", at = @At(ordinal = 1, target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", value = "INVOKE", shift = At.Shift.AFTER))
    private void mtr$onDoBlockTileTickReturn(BooleanSupplier haveTime, CallbackInfo ci) {
        if (MTRState.isRecording((ServerLevel) (Object) this)) {
            MTRState.popEvent();
        }
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickPrecipitation(Lnet/minecraft/core/BlockPos;)V"))
    private void mtr$onIceAndSnow(ServerLevel instance, BlockPos pos, Operation<Void> original) {
        if (MTRState.isRecording((ServerLevel) (Object) this) && !MTRState.getActiveProfile().outsideArea(pos, instance.dimension().identifier().toString())) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "IceAndSnowPhase"
            ));
            original.call(instance, pos);
            MTRState.popEvent();
        }else {
            original.call(instance, pos);
        }
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void mtr$onRandomTickBlock(BlockState instance, ServerLevel serverLevel, BlockPos pos, RandomSource randomSource, Operation<Void> original) {
        if (MTRState.isRecording((ServerLevel) (Object) this) && !MTRState.getActiveProfile().outsideArea(pos, ((ServerLevel) (Object) this) .dimension().identifier().toString())) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "RandomTickPhase"
            ));
            original.call(instance, serverLevel, pos, randomSource);
            MTRState.popEvent();
        }else {
            original.call(instance, serverLevel, pos, randomSource);
        }
    }
    @WrapMethod(method = "tickNonPassenger")
    private void mtr$onTickEntity(Entity entity, Operation<Void> original) {
        if (entity instanceof Player) {
            original.call(entity);
            return;
        }

        if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) {
            return;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            if (profile != null) {
                String dim = level.dimension().identifier().toString();
                Vec3 oldPos = new Vec3(entity.xo, entity.yo, entity.zo);
                Vec3 newPos = entity.position();

                boolean wasInside = !profile.outsideAreaVec3(oldPos, dim);
                boolean isInside = !profile.outsideAreaVec3(newPos, dim);

                long currentTick = this.getServer().getTickCount() - MTRState.getRecordStartTick();
                String uuid = entity.getUUID().toString();
                String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

                if (!wasInside && isInside) {
                    // Entity entered recorded area
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    entity.saveWithoutId(output);
                    CompoundTag nbt = output.buildResult();
                    nbt.putString("id", entityTypeKey);
                    MTRState.recordStep(new EntitySpawnEvent(
                        currentTick, uuid, entityTypeKey, nbt,
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot(), false,dim
                    ));
                } else if (wasInside && !isInside) {
                    // Entity left recorded area
                    MTRState.recordStep(new EntitySpawnEvent(
                        currentTick, uuid, entityTypeKey, new CompoundTag(),
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot(), true,dim
                    ));
                }

                if (isInside) {
                    EntityTickEvent tickEvent = new EntityTickEvent(
                        currentTick, uuid, entityTypeKey,
                        entity.getX(), entity.getY(), entity.getZ(),dim
                    );

                    MTRState.pushEvent(tickEvent);
                    original.call(entity);
                    MTRState.popEvent();
                    return;
                }
            }
        }
        original.call(entity);
    }


    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickBlockEntities()V"))
    private void mtr$onTickBlockEntity(ServerLevel instance, Operation<Void> original) {
        if (MTRState.isRecording(instance)) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "BlockEntityPhase"
            ));
            original.call(instance);
            MTRState.popEvent();
        }else {
            original.call(instance);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;tick()V"))
    private void mtr$onTickDragonFight(EnderDragonFight instance, Operation<Void> original) {
        if (MTRState.isRecording(this.getServer().getLevel(Level.END))) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "DragonFightPhase"
            ));
            original.call(instance);
            MTRState.popEvent();
        }else {
            original.call(instance);
        }
    }


    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tick(Ljava/util/function/BooleanSupplier;Z)V"))
    private void mtr$onTickChunk(ServerChunkCache instance, BooleanSupplier haveTime, boolean tickChunks, Operation<Void> original) {
        if (MTRState.isRecording(this.getServer().getLevel(Level.END))) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "ChunkTickPhase"
            ));
            original.call(instance, haveTime, tickChunks);
            MTRState.popEvent();
        }else {
            original.call(instance, haveTime, tickChunks);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void mtr$onTickEntityListPhase(EntityTickList instance, Consumer<Entity> action, Operation<Void> original) {
        if (MTRState.isRecording((ServerLevel) (Object) this)) {
            MTRState.pushEvent(new PhaseEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "EntityTickPhase"
            ));
            original.call(instance, action);
            MTRState.popEvent();
        } else {
            original.call(instance, action);
        }
    }
}

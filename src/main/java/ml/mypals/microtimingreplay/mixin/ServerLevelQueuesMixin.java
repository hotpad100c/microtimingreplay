package ml.mypals.microtimingreplay.mixin;

import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.event.AddBlockEventEvent;
import ml.mypals.microtimingreplay.event.EntitySpawnEvent;
import ml.mypals.microtimingreplay.event.PostGameEventEvent;
import ml.mypals.microtimingreplay.event.QueueEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.EntityReplayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelQueuesMixin {

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void mtr$onEntityAddedToWorld(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player) return;
        if (entity.level().isClientSide()) return;
        if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) return;

        if (MTRState.isRecording(entity.level())) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().identifier().toString();
                if (!activeProfile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = this.getServer().getTickCount() - MTRState.getRecordStartTick();
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess());
                    entity.saveWithoutId(output);
                    CompoundTag nbt = output.buildResult();
                    String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    nbt.putString("id", entityTypeKey);

                    EntitySpawnEvent event = new EntitySpawnEvent(
                            currentTick,
                            entity.getUUID().toString(),
                            entityTypeKey,
                            nbt,
                            entity.getX(), entity.getY(), entity.getZ(),
                            entity.getYRot(), entity.getXRot(),
                            false,
                            dim
                    );

                    MTRState.recordStep(event);
                }
            }
        }
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"))
    private void mtr$onDoBlockEventHead(BlockEventData eventData, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(eventData.pos(), dim)) {
                MTRState.pushEvent(new QueueEvent(
                    this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                    "ExecuteBlockEvent",
                    eventData.pos(),
                    dim
                ));
            }
        }
    }

    @Inject(method = "doBlockEvent", at = @At("RETURN"))
    private void mtr$onDoBlockEventReturn(BlockEventData eventData, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(eventData.pos(), dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void mtr$onDoBlockTileTickHead(BlockPos pos, Block type, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.pushEvent(new QueueEvent(
                        this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                        "ExecuteBlockTick",
                        pos,
                        dim
                ));
            }
        }
    }

    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void mtr$onDoBlockTileTickReturn(BlockPos pos, Block type, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void mtr$onDoFluidTileTickHead(BlockPos pos, Fluid type, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.pushEvent(new QueueEvent(
                        this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                        "ExecuteFluidTick",
                        pos,
                        dim
                ));
            }
        }
    }

    @Inject(method = "tickFluid", at = @At("RETURN"))
    private void mtr$onDoFluidTileTickReturn(BlockPos pos, Fluid type, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "gameEvent", at = @At("HEAD"))
    public void mtr$onGameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideAreaVec3(position, dim)) {
                MTRState.pushEvent(new PostGameEventEvent(
                        this.getServer().getTickCount() - MTRState.getRecordStartTick(),
                        position.x(), position.y(), position.z(),
                        context.affectedState() != null ? Block.getId(context.affectedState()) : -1,
                        context.sourceEntity() != null ? context.sourceEntity().getStringUUID() : "",
                        dim
                ));
            }
        }
    }

    @Inject(method = "gameEvent", at = @At("RETURN"))
    public void mtr$EndGameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().identifier().toString();
            if (profile != null && !profile.outsideAreaVec3(position, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "blockEvent", at = @At("HEAD"))
    private void mtr$onBlockEvent(BlockPos pos, Block block, int b0, int b1, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = level.dimension().identifier().toString();
                if (activeProfile.outsideArea(pos, dim)) return;
                long currentTick = this.getServer().getTickCount() - MTRState.getRecordStartTick();
                int blockStateId = Block.getId(block.defaultBlockState());
                
                AddBlockEventEvent event = new AddBlockEventEvent(
                    currentTick,
                    pos.getX(), pos.getY(), pos.getZ(),
                    blockStateId, b0, b1,
                    dim
                );
                MTRState.recordStep(event);
            }
        }
    }
}

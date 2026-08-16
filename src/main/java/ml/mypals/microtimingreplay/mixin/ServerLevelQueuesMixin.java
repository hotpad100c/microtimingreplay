package ml.mypals.microtimingreplay.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import ml.mypals.microtimingreplay.MTRState;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ml.mypals.microtimingreplay.util.PlayerProxy;

@Mixin(ServerLevel.class)
public abstract class ServerLevelQueuesMixin {

    @Shadow
    public abstract MinecraftServer getServer();

    /**
     * The pending block-event queue. Shadowed because {@code blockEvent} silently drops an
     * entry the set already holds, and that drop is only visible from here.
     */
    @Shadow
    @Final
    private ObjectLinkedOpenHashSet<BlockEventData> blockEvents;

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void mtr$onEntityAddedToWorld(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity.level().isClientSide()) return;
        if (entity.getTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) return;

        if (MTRState.isRecording(entity.level())) {
            if (!RecordingFilterConfig.isEnabled("entity_spawn")) return;
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = entity.level().dimension().location().toString();
                if (!activeProfile.outsideAreaVec3(entity.position(), dim)) {
                    long currentTick = this.getServer().getTickCount() - MTRState.getRecordStartTick();

                    EntitySpawnEvent event = new EntitySpawnEvent(
                            currentTick,
                            PlayerProxy.replayUuid(entity).toString(),
                            PlayerProxy.typeKey(entity),
                            PlayerProxy.snapshotNbt(entity, entity.level().registryAccess()),
                            entity.getX(), entity.getY(), entity.getZ(),
                            entity.getYRot(), entity.getXRot(),
                            false,
                            dim,entity.getId()
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
            if (!RecordingFilterConfig.isEnabled("execute_block_event")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().location().toString();
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
            String dim = level.dimension().location().toString();
            if (profile != null && !profile.outsideArea(eventData.pos(), dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void mtr$onDoBlockTileTickHead(BlockPos pos, Block type, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            if (!RecordingFilterConfig.isEnabled("block_tick")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().location().toString();
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
            String dim = level.dimension().location().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void mtr$onDoFluidTileTickHead(BlockPos pos, Fluid type, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            if (!RecordingFilterConfig.isEnabled("fluid_tick")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().location().toString();
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
            String dim = level.dimension().location().toString();
            if (profile != null && !profile.outsideArea(pos, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "gameEvent", at = @At("HEAD"))
    public void mtr$onGameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            if (!RecordingFilterConfig.isEnabled("post_game_event")) return;
            MTRProfile profile = MTRState.getActiveProfile();
            String dim = level.dimension().location().toString();
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
            String dim = level.dimension().location().toString();
            if (profile != null && !profile.outsideAreaVec3(position, dim)) {
                MTRState.popEvent();
            }
        }
    }

    @Inject(method = "blockEvent", at = @At("HEAD"))
    private void mtr$onBlockEvent(BlockPos pos, Block block, int b0, int b1, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (MTRState.isRecording(level)) {
            if (!RecordingFilterConfig.isEnabled("add_block_event")) return;
            MTRProfile activeProfile = MTRState.getActiveProfile();
            if (activeProfile != null) {
                String dim = level.dimension().location().toString();
                if (activeProfile.outsideArea(pos, dim)) return;
                long currentTick = this.getServer().getTickCount() - MTRState.getRecordStartTick();
                int blockStateId = Block.getId(block.defaultBlockState());

                // blockEvents is a Set of a record keyed on all four values, so an identical
                // entry already in the queue makes this call a no-op. Unlike scheduled ticks,
                // which collide on position and type alone, a different b0/b1 here is a
                // genuinely new event and does get queued.
                boolean shouldFail = this.blockEvents.contains(new BlockEventData(pos, block, b0, b1));

                AddBlockEventEvent event = new AddBlockEventEvent(
                    currentTick,
                    pos.getX(), pos.getY(), pos.getZ(),
                    blockStateId, b0, b1,
                    dim, shouldFail
                );
                MTRState.recordStep(event);
            }
        }
    }
}

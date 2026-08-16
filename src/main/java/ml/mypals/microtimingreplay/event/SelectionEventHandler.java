package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.command.MTRCommand;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionEventHandler {
    private static final Map<UUID, BlockPos> pos1Map = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Map = new HashMap<>();
    private static final Map<UUID, UUID> dynamicMarkers = new HashMap<>();
    private static final Map<UUID, BlockPos> lastP1Map = new HashMap<>();
    private static final Map<UUID, BlockPos> lastP2Map = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID pUuid = player.getUUID();
                if (pos1Map.containsKey(pUuid) || pos2Map.containsKey(pUuid)) {
                    BlockPos p1 = pos1Map.get(pUuid);
                    BlockPos p2 = pos2Map.get(pUuid);
                    
                    if (p1 == null || p2 == null) {
                        BlockPos dynamicPos;
                        HitResult hit = player.pick(4.0, 0.0f, false);
                        if (hit.getType() == HitResult.Type.BLOCK) {
                            dynamicPos = ((BlockHitResult) hit).getBlockPos();
                        } else {
                            Vec3 eyePos = player.getEyePosition();
                            Vec3 lookVec = player.getViewVector(1.0f);
                            Vec3 endPos = eyePos.add(lookVec.x * 4, lookVec.y * 4, lookVec.z * 4);
                            dynamicPos = BlockPos.containing(endPos.x, endPos.y, endPos.z);
                        }
                        
                        if (p1 == null) p1 = dynamicPos;
                        if (p2 == null) p2 = dynamicPos;
                    }
                    
                    UUID markerUuid = dynamicMarkers.get(pUuid);
                    BlockDisplay existing = null;
                    if (markerUuid != null && player.level() instanceof ServerLevel markerLevel) {
                        Entity e = markerLevel.getEntity(markerUuid);
                        if (e instanceof BlockDisplay) {
                            existing = (BlockDisplay) e;
                        }
                    }
                    
                    if (player.level() instanceof ServerLevel sl) {
                        BlockPos lastP1 = lastP1Map.get(pUuid);
                        BlockPos lastP2 = lastP2Map.get(pUuid);
                        
                        if (existing == null || !p1.equals(lastP1) || !p2.equals(lastP2)) {
                            BlockDisplay updated = MTRMarker.spawnOrUpdateDynamicAreaMarker(sl, existing, p1, p2);
                            dynamicMarkers.put(pUuid, updated.getUUID());
                            lastP1Map.put(pUuid, p1);
                            lastP2Map.put(pUuid, p2);
                        }
                    }
                }
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (player.getItemInHand(hand).getItem() != Items.PURPLE_DYE) return InteractionResult.PASS;

            String infoProfile = MTRCommand.getCurrentInfoProfile();
            if (infoProfile == null) return InteractionResult.PASS;

            MTRProfile profile = ProfileManager.loadProfile(infoProfile);
            if (profile == null) return InteractionResult.PASS;

            if (player.isShiftKeyDown()) {
                BlockPos p1 = pos1Map.get(player.getUUID());
                BlockPos p2 = pos2Map.get(player.getUUID());
                if (p1 != null && p2 != null) {
                    String assignedName = profile.addArea(null, p1, p2, world.dimension().location().toString());
                    if (assignedName != null) {
                        ProfileManager.saveProfile(profile);
                        player.sendSystemMessage(Component.literal("Area confirmed and saved as: " + assignedName));
                        pos1Map.remove(player.getUUID());
                        pos2Map.remove(player.getUUID());
                        
                        UUID markerId = dynamicMarkers.remove(player.getUUID());
                        lastP1Map.remove(player.getUUID());
                        lastP2Map.remove(player.getUUID());
                        if (markerId != null && world instanceof ServerLevel) {
                            MTRMarker.removeEntity((ServerLevel) world, markerId);
                        }
                        
                        if (world instanceof ServerLevel) {
                            MTRCommand.refreshAreaMarkers((ServerLevel) world, profile);
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("Failed to add area."));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("Incomplete selection. Need 2 points."));
                }
                return InteractionResult.SUCCESS;
            } else {
                BlockPos pos = hitResult.getBlockPos();
                pos2Map.put(player.getUUID(), pos);
                player.sendSystemMessage(Component.literal("Pos 2 set to: " + pos.toShortString()));
                player.swing(hand);
                return InteractionResult.SUCCESS;
            }
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (player.getMainHandItem().getItem() != Items.PURPLE_DYE) return InteractionResult.PASS;

            String infoProfile = MTRCommand.getCurrentInfoProfile();
            if (infoProfile == null) return InteractionResult.PASS;

            if (player.isShiftKeyDown()) {
                pos1Map.remove(player.getUUID());
                pos2Map.remove(player.getUUID());
                
                UUID markerId = dynamicMarkers.remove(player.getUUID());
                lastP1Map.remove(player.getUUID());
                lastP2Map.remove(player.getUUID());
                if (markerId != null && world instanceof ServerLevel) {
                    MTRMarker.removeEntity((ServerLevel) world, markerId);
                }
                
                player.sendSystemMessage(Component.literal("Selection cleared."));
                player.swing(hand);
                return InteractionResult.SUCCESS;
            } else {
                pos1Map.put(player.getUUID(), pos);
                player.sendSystemMessage(Component.literal("Pos 1 set to: " + pos.toShortString()));
                player.swing(hand);
                return InteractionResult.SUCCESS;
            }
        });
    }
}

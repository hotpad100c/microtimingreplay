package ml.mypals.microtimingreplay.replay;


import net.minecraft.world.level.storage.ValueInput;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WorldBackupManager {
    private static final File BACKUPS_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mtr_backups");

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void init() {
        if (!BACKUPS_DIR.exists()) {
            BACKUPS_DIR.mkdirs();
        }
    }

    public static void backup(MTRProfile profile, String suffix) {
        if (MicroTimingReplay.server == null) return;
        
        CompoundTag rootTag = new CompoundTag();
        ListTag areasTag = new ListTag();

        for (MTRProfile.Area area : profile.getAreas()) {
            ServerLevel level = null;
            for (ServerLevel sl : MicroTimingReplay.server.getAllLevels()) {
                if (sl.dimension().identifier().toString().equals(area.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) continue;

            CompoundTag areaTag = new CompoundTag();
            areaTag.putString("name", area.name);
            areaTag.putString("dimension", area.dimension);
            
            ListTag blocksTag = new ListTag();
            
            int minX = Math.min(area.x1, area.x2);
            int minY = Math.min(area.y1, area.y2);
            int minZ = Math.min(area.z1, area.z2);
            int maxX = Math.max(area.x1, area.x2);
            int maxY = Math.max(area.y1, area.y2);
            int maxZ = Math.max(area.z1, area.z2);
            
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        
                        CompoundTag blockTag = new CompoundTag();
                        blockTag.putInt("x", x);
                        blockTag.putInt("y", y);
                        blockTag.putInt("z", z);
                        blockTag.putInt("stateId", Block.getId(state));
                        
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null) {
                            blockTag.put("nbt", be.saveWithFullMetadata(level.registryAccess()));
                        }
                        
                        blocksTag.add(blockTag);
                    }
                }
            }
            
            areaTag.put("blocks", blocksTag);

            // Backup non-player real entities in area
            ListTag entitiesTag = new ListTag();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player) continue;
                if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) continue;

                double ex = entity.getX();
                double ey = entity.getY();
                double ez = entity.getZ();

                if (ex >= minX && ex <= maxX + 1 && ey >= minY && ey <= maxY + 1 && ez >= minZ && ez <= maxZ + 1) {
                    ProblemReporter reporter = ProblemReporter.DISCARDING;
                    TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
                    entity.saveWithoutId(output);
                    CompoundTag entityNbt = output.buildResult();
                    String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    entityNbt.putString("id", entityTypeKey);
                    entitiesTag.add(entityNbt);
                }
            }
            areaTag.put("entities", entitiesTag);

            areasTag.add(areaTag);
        }
        
        rootTag.put("areas", areasTag);
        
        Path path = new File(BACKUPS_DIR, profile.getName() + "_" + suffix + ".dat").toPath();
        try {
            NbtIo.writeCompressed(rootTag, path);
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to backup world for profile: {}", profile.getName(), e);
        }
    }

    public static void restore(MTRProfile profile, String suffix) {
        if (MicroTimingReplay.server == null) return;

        Path path = new File(BACKUPS_DIR, profile.getName() + "_" + suffix + ".dat").toPath();
        if (!path.toFile().exists()) return;
        
        try {
            CompoundTag rootTag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            ListTag areasTag = rootTag.getList("areas").orElse(new ListTag()); 
            
            for (int i = 0; i < areasTag.size(); i++) {
                CompoundTag areaTag = areasTag.getCompound(i).orElse(new CompoundTag());
                String dimension = areaTag.getString("dimension").orElse("");
                
                ServerLevel level = null;
                for (ServerLevel sl : MicroTimingReplay.server.getAllLevels()) {
                    if (sl.dimension().identifier().toString().equals(dimension)) {
                        level = sl;
                        break;
                    }
                }
                if (level == null) continue;
                
                ListTag blocksTag = areaTag.getList("blocks").orElse(new ListTag());

                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

                for (int j = 0; j < blocksTag.size(); j++) {
                    CompoundTag blockTag = blocksTag.getCompound(j).orElse(new CompoundTag());
                    int x = blockTag.getInt("x").orElse(0);
                    int y = blockTag.getInt("y").orElse(0);
                    int z = blockTag.getInt("z").orElse(0);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }

                // Clear current non-player real entities in area before restoring
                if (minX <= maxX) {
                    List<Entity> toRemove = new ArrayList<>();
                    for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof Player) continue;
                        if (entity.entityTags().contains(EntityReplayManager.REPLAY_ENTITY_TAG)) continue;

                        double ex = entity.getX();
                        double ey = entity.getY();
                        double ez = entity.getZ();

                        if (ex >= minX && ex <= maxX + 1 && ey >= minY && ey <= maxY + 1 && ez >= minZ && ez <= maxZ + 1) {
                            toRemove.add(entity);
                        }
                    }
                    for (Entity e : toRemove) {
                        e.discard();
                    }
                }

                // Restore blocks
                for (int j = 0; j < blocksTag.size(); j++) {
                    CompoundTag blockTag = blocksTag.getCompound(j).orElse(new CompoundTag());
                    int x = blockTag.getInt("x").orElse(0);
                    int y = blockTag.getInt("y").orElse(0);
                    int z = blockTag.getInt("z").orElse(0);
                    int stateId = blockTag.getInt("stateId").orElse(0);
                    
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = Block.stateById(stateId);
                    
                    level.setBlock(pos, state, 2 | 816, 0);
                    
                    if (blockTag.contains("nbt")) {
                        CompoundTag nbt = blockTag.getCompound("nbt").orElse(null);
                        if (nbt != null) {
                            BlockEntity be = BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
                            if (be != null) {
                                level.setBlockEntity(be);
                            }
                        }
                    }
                }


            }
        } catch (IOException e) {
            MicroTimingReplay.LOGGER.error("Failed to restore world for profile: {}", profile.getName(), e);
        }
    }

    public static void deleteBackups(String profileName) {
        File recordBackup = new File(BACKUPS_DIR, profileName + "_record.dat");
        if (recordBackup.exists()) recordBackup.delete();
        File replayBackup = new File(BACKUPS_DIR, profileName + "_replay.dat");
        if (replayBackup.exists()) replayBackup.delete();
    }
}

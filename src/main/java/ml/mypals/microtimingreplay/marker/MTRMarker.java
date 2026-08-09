package ml.mypals.microtimingreplay.marker;

import com.mojang.math.Transformation;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.replay.ReplayContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public final class MTRMarker {

    private MTRMarker() {}

    public static final String SESSION_TAG_PREFIX = "mtr_s_";

    public static String sessionTag(String sessionId) {
        return SESSION_TAG_PREFIX + sessionId;
    }

    /**
     * Markers carry the tag of the session that spawned them. Each replay clears and
     * redraws on every step, so without this one session's step would wipe another's
     * markers.
     */
    private static void tagMarker(net.minecraft.world.entity.Entity entity) {
        entity.addTag("mtr_marker");
        entity.addTag("mtr_replay_marker");
        String session = ReplayContext.current();
        if (session != null) {
            entity.addTag(sessionTag(session));
        }
    }

    public static void spawnTextDisplay(ServerLevel level, double x, double y, double z, Component text, float scale) {
        TextDisplay entity = new TextDisplay(EntityType.TEXT_DISPLAY, level);
        entity.setPos(x, y, z);
        entity.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        entity.setFlags((byte)(entity.getFlags() | Display.TextDisplay.FLAG_SEE_THROUGH));
        entity.setTextOpacity((byte) 255);
        entity.setBackgroundColor(0x80000000);
        entity.setBrightnessOverride(new Brightness(15, 15));

        Transformation transform = new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
        entity.setTransformation(transform);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        tagMarker(entity);

        level.addFreshEntity(entity);
        entity.setText(text);
    }
    public static void spawnTextDisplay(ServerLevel level, Vec3 pos, Component text, float scale) {
        spawnTextDisplay(level, pos.x(),pos.y(),pos.z(), text, scale);
    }
    public static void spawnBlockDisplay(ServerLevel level, Vec3 pos, BlockState blockState, float scale, ChatFormatting teamColor) {
        spawnBlockDisplay(level, pos, blockState, new Vector3f(scale, scale, scale), teamColor);
    }

    public static void spawnBlockDisplay(ServerLevel level, BlockPos pos, BlockState blockState, float scale, ChatFormatting teamColor) {
        spawnBlockDisplay(level, new Vec3(pos.getX(),pos.getY(),pos.getZ()), blockState, new Vector3f(scale, scale, scale), teamColor);
    }

    public static void spawnAreaMarker(ServerLevel level, MTRProfile.Area area) {
        BlockDisplay entity = new BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        
        float width = Math.abs(area.x2 - area.x1) + 1.02f;
        float height = Math.abs(area.y2 - area.y1) + 1.02f;
        float depth = Math.abs(area.z2 - area.z1) + 1.02f;
        
        Vector3f scale = new Vector3f(width, height, depth);
        
        float minX = Math.min(area.x1, area.x2) - 0.01f;
        float minY = Math.min(area.y1, area.y2) - 0.01f;
        float minZ = Math.min(area.z1, area.z2) - 0.01f;
        
        entity.setPos(minX, minY, minZ);
        entity.setBlockState(Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
        entity.setBrightnessOverride(new Brightness(15, 15));
        
        Transformation transform = new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), scale, new Quaternionf());
        entity.setTransformation(transform);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.addTag("mtr_area_marker");
        entity.addTag("mtr_replay_marker");
        
        entity.setGlowingTag(true);
        if (ChatFormatting.LIGHT_PURPLE.getColor() != null) {
            entity.setGlowColorOverride(ChatFormatting.LIGHT_PURPLE.getColor());
        }
        level.addFreshEntity(entity);
    }

    public static BlockDisplay spawnOrUpdateDynamicAreaMarker(ServerLevel level, BlockDisplay existing, BlockPos p1, BlockPos p2) {
        if (existing == null || existing.isRemoved()) {
            existing = new BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            existing.setBlockState(Blocks.MAGENTA_STAINED_GLASS.defaultBlockState());
            existing.setBrightnessOverride(new Brightness(15, 15));
            existing.setNoGravity(true);
            existing.setInvulnerable(true);
            existing.setSilent(true);
            existing.addTag("mtr_dynamic_marker");
            existing.addTag("mtr_replay_marker");
            existing.setGlowingTag(true);
            if (ChatFormatting.LIGHT_PURPLE.getColor() != null) {
                existing.setGlowColorOverride(ChatFormatting.LIGHT_PURPLE.getColor());
            }
            level.addFreshEntity(existing);
        }
        
        float width = Math.abs(p2.getX() - p1.getX()) + 1.02f;
        float height = Math.abs(p2.getY() - p1.getY()) + 1.02f;
        float depth = Math.abs(p2.getZ() - p1.getZ()) + 1.02f;
        
        Vector3f scale = new Vector3f(width, height, depth);
        
        float minX = Math.min(p1.getX(), p2.getX()) - 0.01f;
        float minY = Math.min(p1.getY(), p2.getY()) - 0.01f;
        float minZ = Math.min(p1.getZ(), p2.getZ()) - 0.01f;
        
        existing.setPos(minX, minY, minZ);
        Transformation transform = new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), scale, new Quaternionf());
        existing.setTransformation(transform);
        
        existing.setTransformationInterpolationDuration(1);
        existing.setTransformationInterpolationDelay(0);
        existing.setPosRotInterpolationDuration(1);
        
        return existing;
    }

    public static void spawnBlockDisplay(ServerLevel level, Vec3 pos, BlockState blockState, Vector3f scale, ChatFormatting teamColor) {
        BlockDisplay entity = new BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        float offsetX = -(scale.x() - 1.0f) / 2.0f;
        float offsetY = -(scale.y() - 1.0f) / 2.0f;
        float offsetZ = -(scale.z() - 1.0f) / 2.0f;
        entity.setPos(pos.x() + offsetX, pos.y() + offsetY, pos.z() + offsetZ);
        entity.setBlockState(blockState);
        entity.setBrightnessOverride(new Brightness(15, 15));

        Transformation transform = new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), scale, new Quaternionf());
        entity.setTransformation(transform);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        tagMarker(entity);
        if (teamColor != null && teamColor.getColor() != null) {
            entity.setGlowingTag(true);
            entity.setGlowColorOverride(teamColor.getColor());
            level.addFreshEntity(entity);
        } else {
            level.addFreshEntity(entity);
        }

    }

    public static void removeEntity(ServerLevel level, UUID uuid) {
        var entity = level.getEntity(uuid);
        if (entity != null) entity.discard();
    }
}
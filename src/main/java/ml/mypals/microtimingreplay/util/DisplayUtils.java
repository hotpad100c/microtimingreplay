package ml.mypals.microtimingreplay.util;

import com.mojang.math.Transformation;
import ml.mypals.microtimingreplay.replay.ReplayContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DisplayUtils {

    public static final String SESSION_TAG_PREFIX = "mtr_s_";

    public static String sessionTag(String sessionId) {
        return SESSION_TAG_PREFIX + sessionId;
    }

    public static void tagMarker(Entity entity) {
        entity.addTag("mtr_marker");
        entity.addTag("mtr_replay_marker");
        String session = ReplayContext.current();
        if (session != null) {
            entity.addTag(sessionTag(session));
        }
    }

    public static BlockState getGlassState(ChatFormatting color) {
        if (color == null) return Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        return switch (color) {
            case RED -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case GREEN -> Blocks.GREEN_STAINED_GLASS.defaultBlockState();
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
            case AQUA, DARK_AQUA -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case BLUE, DARK_BLUE -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
            case LIGHT_PURPLE, DARK_PURPLE -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case GRAY, DARK_GRAY -> Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            case BLACK -> Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            default -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        };
    }

    public static BlockDisplay spawnBlockDisplay(ServerLevel level, Vec3 pos, BlockState blockState, Vector3f scale, ChatFormatting teamColor) {
        return spawnOrientedBlockDisplay(level, pos, blockState, scale, new Quaternionf(), teamColor);
    }

    public static BlockDisplay spawnBlockDisplay(ServerLevel level, Vec3 pos, BlockState blockState, float scale, ChatFormatting teamColor) {
        return spawnBlockDisplay(level, pos, blockState, new Vector3f(scale, scale, scale), teamColor);
    }

    public static BlockDisplay spawnBlockDisplay(ServerLevel level, BlockPos pos, BlockState blockState, float scale, ChatFormatting teamColor) {
        return spawnBlockDisplay(level, new Vec3(pos.getX(), pos.getY(), pos.getZ()), blockState, new Vector3f(scale, scale, scale), teamColor);
    }

    public static BlockDisplay spawnOrientedBlockDisplay(ServerLevel level, Vec3 pos, BlockState blockState, Vector3f scale, Quaternionf rotation, ChatFormatting teamColor) {
        BlockDisplay entity = new BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        float offsetX = -(scale.x() - 1.0f) / 2.0f;
        float offsetY = -(scale.y() - 1.0f) / 2.0f;
        float offsetZ = -(scale.z() - 1.0f) / 2.0f;
        entity.setPos(pos.x() + offsetX, pos.y() + offsetY, pos.z() + offsetZ);
        entity.setBlockState(blockState);
        entity.setBrightnessOverride(new Brightness(15, 15));

        Transformation transform = new Transformation(new Vector3f(0, 0, 0), rotation, scale, new Quaternionf());
        entity.setTransformation(transform);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setViewRange(10.0f);
        entity.setWidth(100.0f);
        entity.setHeight(100.0f);
        tagMarker(entity);

        if (teamColor != null && teamColor.getColor() != null) {
            entity.setGlowingTag(true);
            entity.setGlowColorOverride(teamColor.getColor());
        }

        level.addFreshEntity(entity);
        return entity;
    }


    public static BlockDisplay spawnLineDisplay(ServerLevel level, Vec3 start, Vec3 end, BlockState blockState, float thickness, ChatFormatting teamColor) {
        Vec3 diff = end.subtract(start);
        double length = diff.length();

        Vec3 mid = start.add(end).scale(0.5);

        Quaternionf rotation = new Quaternionf();
        if (length > 1e-6) {
            Vector3f from = new Vector3f(0, 0, 1);
            Vector3f to = new Vector3f((float) diff.x, (float) diff.y, (float) diff.z).normalize();
            rotation.rotateTo(from, to);
        }

        float actualLen = (float) Math.max(0.05, length);
        Vector3f scale = new Vector3f(thickness, thickness, actualLen);

        BlockDisplay entity = new BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        entity.setPos(start);
        entity.setBlockState(blockState);
        entity.setBrightnessOverride(new Brightness(15, 15));

        Transformation transform = new Transformation(new Vector3f(0,0,0), rotation, scale, new Quaternionf());
        entity.setTransformation(transform);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setViewRange(10.0f);
        entity.setWidth(100.0f);
        entity.setHeight(100.0f);
        tagMarker(entity);

        if (teamColor != null && teamColor.getColor() != null) {
            entity.setGlowingTag(true);
            entity.setGlowColorOverride(teamColor.getColor());
        }

        level.addFreshEntity(entity);
        return entity;
    }

    public static TextDisplay spawnTextDisplay(ServerLevel level, double x, double y, double z, Component text, float scale) {
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
        entity.setViewRange(10.0f);
        entity.setWidth(100.0f);
        entity.setHeight(100.0f);
        tagMarker(entity);

        level.addFreshEntity(entity);
        entity.setText(text);
        return entity;
    }

    public static TextDisplay spawnTextDisplay(ServerLevel level, Vec3 pos, Component text, float scale) {
        return spawnTextDisplay(level, pos.x(), pos.y(), pos.z(), text, scale);
    }

    public static Component getNamesFormatState(BlockState state) {
        MutableComponent result = Component.literal(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
        ).withStyle(ChatFormatting.AQUA);

        Stream<Property.Value<?>> stream = state.getValues();
        List<Property.Value<?>> values = stream.toList();
        if (values.isEmpty()) {
            return result;
        }

        result.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));

        boolean first = true;
        for (Property.Value<?> entry : values) {
            if (!first) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            }
            first = false;

            Property<?> prop = entry.property();
            Comparable<?> value = entry.value();

            result.append(Component.literal(prop.getName()).withStyle(ChatFormatting.YELLOW));
            result.append(Component.literal("=").withStyle(ChatFormatting.GRAY));
            result.append(Component.literal(getValueName(prop, value)).withStyle(ChatFormatting.GREEN));
        }

        result.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        return result;
    }

    @SuppressWarnings({"unchecked"})
    private static <T extends Comparable<T>> String getValueName(Property<T> prop, Comparable<?> value) {
        return prop.getName((T) value);
    }

    public static Component formatStateDiff(BlockState oldState, BlockState newState) {
        boolean sameBlock = oldState.getBlock() == newState.getBlock();

        if (!sameBlock) {
            return formatBlockName(oldState)
                    .append(Component.literal("\n↓\n").withStyle(ChatFormatting.GRAY))
                    .append(formatBlockName(newState));
        }

        MutableComponent oldComp = formatBlockName(oldState);
        MutableComponent newComp = formatBlockName(newState);

        List<Component> oldChanged = new ArrayList<>();
        List<Component> newChanged = new ArrayList<>();

        for (Property<?> prop : oldState.getProperties()) {
            Comparable<?> oldVal = oldState.getValue(prop);
            Comparable<?> newVal = newState.getValue(prop);

            if (!Objects.equals(oldVal, newVal)) {
                oldChanged.add(formatProperty(prop, oldVal, ChatFormatting.RED));
                newChanged.add(formatProperty(prop, newVal, ChatFormatting.GREEN));
            }
        }

        if (!oldChanged.isEmpty()) {
            oldComp.append(Component.literal("\n"));
            oldComp.append(formatPropertiesBracket(oldChanged));

            newComp.append(Component.literal("\n"));
            newComp.append(formatPropertiesBracket(newChanged));
        }

        return oldComp
                .append(Component.literal("\n↓\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(newComp);
    }

    private static MutableComponent formatBlockName(BlockState state) {
        return Component.literal(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                .withStyle(ChatFormatting.AQUA);
    }

    private static Component formatProperty(Property<?> prop, Comparable<?> value, ChatFormatting valueColor) {
        return Component.literal(prop.getName()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("=").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(getValueName(prop, value)).withStyle(valueColor));
    }

    private static Component formatPropertiesBracket(List<Component> props) {
        MutableComponent result = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY);

        for (int i = 0; i < props.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            }
            result.append(props.get(i));
        }

        result.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        return result;
    }
}

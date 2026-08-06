package ml.mypals.microtimingreplay.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DisplayUtils {
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

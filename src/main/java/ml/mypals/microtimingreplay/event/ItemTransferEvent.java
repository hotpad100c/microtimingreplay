package ml.mypals.microtimingreplay.event;

import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ItemTransferEvent extends BlockPosEvent {
    public static final String TYPE = "itemTransfer";

    private final BlockPos targetPos;
    private final String itemId;
    private final int count;
    private final CompoundTag itemNbt;

    public ItemTransferEvent(long tick, BlockPos sourcePos, BlockPos targetPos,
                             String itemId, int count, CompoundTag itemNbt, String dimension) {
        super(tick, TYPE, sourcePos, dimension);
        this.targetPos = targetPos != null ? targetPos : sourcePos;
        this.itemId = itemId != null ? itemId : "minecraft:air";
        this.count = count;
        this.itemNbt = itemNbt != null ? itemNbt : new CompoundTag();
    }

    public BlockPos getSourcePos() {
        return getPos();
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    public ItemStack createItemStack(ServerLevel level) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(itemId)).orElse(Items.PAPER);
        return new ItemStack(item, count);
    }


    @Override
    public String filterId() {
        return "item_transfer";
    }

    @Override
    public ChatFormatting getColor() {
        return ChatFormatting.GOLD;
    }

    @Override
    public MutableComponent getScoreboardText() {
        ItemStack stack = createItemStack(null);
        String name = stack.getHoverName().getString();
        return appendPosText(MTRComponent.translatable(
                "mtr.scoreboard.event.leaf.itemtransfer",
                "[Item Transfer] " + name + " x" + count,
                name, count
        ));
    }

    @Override
    public MutableComponent fillHoverText() {
        ItemStack stack = createItemStack(null);
        String name = stack.getHoverName().getString();
        String fromPosStr = String.format("@[%d,%d,%d]", getX(), getY(), getZ());
        String toPosStr = String.format("@[%d,%d,%d]", targetPos.getX(), targetPos.getY(), targetPos.getZ());

        MutableComponent text = MTRComponent.translatable(
                "mtr.tooltip.item_transfer_title",
                "Item Transfer %s -> %s",
                fromPosStr, toPosStr
        ).append(Component.literal("\n")).withStyle(getColor());

        if (getDimension() != null && !getDimension().isEmpty()) {
            text.append(MTRComponent.translatable("mtr.tooltip.dimension", "Dimension: %s", getDimension()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        }

        return text
            .append(MTRComponent.translatable("mtr.tooltip.item_transfer_item", "Item: %s x%d", name, count).withStyle(ChatFormatting.AQUA))
            .append(Component.literal("\nFrom: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(fromPosStr).withStyle(ChatFormatting.RED))
            .append(Component.literal("\nTo: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(toPosStr).withStyle(ChatFormatting.GREEN));
    }

    @Override
    public void display(ServerLevel level, Vector3f scale) {
        if (level == null || getDimension() == null) return;
        MTRProfile profile = ml.mypals.microtimingreplay.MTRState.getActiveProfile();
        if (profile != null && profile.outsideArea(getPos(), getDimension()) && profile.outsideArea(targetPos, getDimension())) {
            return;
        }

        // 1. Source container: Red Glass BlockDisplay
        MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(getPos()),
                Blocks.RED_STAINED_GLASS.defaultBlockState(), scale, ChatFormatting.RED);

        // 2. Target container: Green Glass BlockDisplay
        MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(targetPos),
                Blocks.GREEN_STAINED_GLASS.defaultBlockState(), scale, ChatFormatting.GREEN);

        // 3. Floating ItemDisplay at midpoint
        double midX = (getX() + targetPos.getX()) / 2.0 + 0.5;
        double midY = (getY() + targetPos.getY()) / 2.0 + 0.75;
        double midZ = (getZ() + targetPos.getZ()) / 2.0 + 0.5;

        ItemStack stack = createItemStack(level);
        Display.ItemDisplay itemDisplay = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        itemDisplay.setPos(midX, midY, midZ);
        itemDisplay.setItemStack(stack);

        Transformation transform = new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf()
        );
        itemDisplay.setTransformation(transform);
        itemDisplay.setBrightnessOverride(new Brightness(15, 15));
        itemDisplay.setNoGravity(true);
        itemDisplay.setInvulnerable(true);
        itemDisplay.setCustomName(Component.literal(stack.getHoverName().getString() + " x" + count).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        itemDisplay.setCustomNameVisible(true);

        // Must go through tagMarker: it also stamps the session tag, and MarkerManager
        // skips any Display that lacks one — tagging by hand here leaked these forever.
        DisplayUtils.tagMarker(itemDisplay);

        level.addFreshEntity(itemDisplay);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("toX", targetPos.getX());
        tag.putInt("toY", targetPos.getY());
        tag.putInt("toZ", targetPos.getZ());
        tag.putString("itemId", itemId != null ? itemId : "");
        tag.putInt("count", count);
        if (itemNbt != null) tag.put("itemNbt", itemNbt);
        return tag;
    }

    public static ItemTransferEvent readNBT(CompoundTag tag) {
        BlockPos sourcePos = new BlockPos(
                tag.getInt("x").orElse(0),
                tag.getInt("y").orElse(0),
                tag.getInt("z").orElse(0)
        );
        BlockPos targetPos = new BlockPos(
                tag.getInt("toX").orElse(0),
                tag.getInt("toY").orElse(0),
                tag.getInt("toZ").orElse(0)
        );
        ItemTransferEvent event = new ItemTransferEvent(
                tag.getLong("tick").orElse(0L),
                sourcePos,
                targetPos,
                tag.getString("itemId").orElse("minecraft:air"),
                tag.getInt("count").orElse(1),
                tag.getCompound("itemNbt").orElse(new CompoundTag()),
                tag.getString("dimension").orElse("")
        );
        MTREvent.readChildrenNBT(event, tag);
        return event;
    }
}

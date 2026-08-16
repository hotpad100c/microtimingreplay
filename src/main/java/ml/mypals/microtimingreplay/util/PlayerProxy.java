package ml.mypals.microtimingreplay.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PlayerProxy {
    /**
     * 1.21.1 has no mannequin entity, so a player is stood in for by an armour stand
     * wearing their own head — close enough to tell two recorded players apart, and it
     * takes the same inert treatment every other stand-in gets.
     */
    public static final String PLAYER_STAND_IN_ID = "minecraft:armor_stand";

    public static UUID replayUuid(Entity entity) {
        if (entity instanceof Player) {
            return UUID.nameUUIDFromBytes(("mtr:player:" + entity.getUUID()).getBytes(StandardCharsets.UTF_8));
        }
        return entity.getUUID();
    }

     public static String typeKey(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    public static CompoundTag snapshotNbt(Entity entity, RegistryAccess registries) {
        if (entity instanceof Player player) {
            return playerStandInNbt(player, registries);
        }

        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        nbt.putString("id", typeKey(entity));
        nbt.putIntArray("UUID", UUIDUtil.uuidToIntArray(replayUuid(entity)));
        return nbt;
    }

    private static CompoundTag playerStandInNbt(Player player, RegistryAccess registries) {
        CompoundTag playerNbt = player.saveWithoutId(new CompoundTag());

        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", PLAYER_STAND_IN_ID);

        for (String key : new String[]{"Pos", "Rotation"}) {
            Tag value = playerNbt.get(key);
            if (value != null) {
                nbt.put(key, value);
            }
        }

        nbt.putIntArray("UUID", UUIDUtil.uuidToIntArray(replayUuid(player)));
        nbt.putBoolean("NoGravity", true);
        nbt.putBoolean("Invulnerable", true);
        nbt.putBoolean("ShowArms", true);

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));

        // Armour stand order is feet, legs, chest, head.
        ListTag armor = new ListTag();
        armor.add(player.getItemBySlot(EquipmentSlot.FEET).saveOptional(registries));
        armor.add(player.getItemBySlot(EquipmentSlot.LEGS).saveOptional(registries));
        armor.add(player.getItemBySlot(EquipmentSlot.CHEST).saveOptional(registries));
        armor.add(head.saveOptional(registries));
        nbt.put("ArmorItems", armor);

        return nbt;
    }
}

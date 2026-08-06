package ml.mypals.microtimingreplay.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.TagValueOutput;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PlayerProxy {
    public static final String MANNEQUIN_ID = "minecraft:mannequin";

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
            return mannequinNbt(player, registries);
        }

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        entity.saveWithoutId(output);
        CompoundTag nbt = output.buildResult();
        nbt.putString("id", typeKey(entity));
        nbt.putIntArray("UUID", UUIDUtil.uuidToIntArray(replayUuid(entity)));
        return nbt;
    }

    private static CompoundTag mannequinNbt(Player player, RegistryAccess registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        player.saveWithoutId(output);
        CompoundTag playerNbt = output.buildResult();

        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", MANNEQUIN_ID);

        for (String key : new String[]{"Pos", "Rotation", "equipment"}) {
            Tag value = playerNbt.get(key);
            if (value != null) {
                nbt.put(key, value);
            }
        }

        nbt.putIntArray("UUID", UUIDUtil.uuidToIntArray(replayUuid(player)));

        ResolvableProfile.CODEC
                .encodeStart(NbtOps.INSTANCE, player.getProfile())
                .result()
                .ifPresent(profile -> nbt.put("profile", profile));
        nbt.putBoolean("immovable", true);
        return nbt;
    }
}

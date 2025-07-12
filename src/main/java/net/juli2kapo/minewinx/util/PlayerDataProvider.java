package net.juli2kapo.minewinx.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public class PlayerDataProvider {

    public static void setElement(Player player, String element) {
        CompoundTag persistentData = player.getPersistentData();
        persistentData.putString("minewinx_element", element);
    }

    public static String getElement(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        return persistentData.getString("minewinx_element");
    }

    public static void setStage(Player player, int stage) {
        CompoundTag persistentData = player.getPersistentData();
        persistentData.putInt("minewinx_stage", stage);
    }

    public static int getStage(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        return persistentData.getInt("minewinx_stage");
    }

    public static void setTransformed(Player player, boolean transformed) {
        CompoundTag persistentData = player.getPersistentData();
        persistentData.putBoolean("minewinx_transformed", transformed);
    }

    public static boolean isTransformed(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        return persistentData.getBoolean("minewinx_transformed");
    }
}
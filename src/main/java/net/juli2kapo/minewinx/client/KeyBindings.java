package net.juli2kapo.minewinx.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

public class KeyBindings {
    public static final String KEY_CATEGORY_MINEWINX = "key.category.minewinx";
    public static final String KEY_TRANSFORM = "key.minewinx.transform";
    public static final String KEY_USE_POWER_1 = "key.minewinx.use_power";
    public static final String KEY_USE_POWER_2 = "key.minewinx.use_power_2";
    public static final String KEY_USE_POWER_3 = "key.minewinx.use_power_3";


    public static final KeyMapping TRANSFORM_KEY = new KeyMapping(
            KEY_TRANSFORM,
            KeyConflictContext.IN_GAME,
            InputConstants.getKey(InputConstants.KEY_G, -1),
            KEY_CATEGORY_MINEWINX
    );


    public static final KeyMapping USE_POWER_KEY1 = new KeyMapping(
            KEY_USE_POWER_1,
            KeyConflictContext.IN_GAME,
            InputConstants.getKey(InputConstants.KEY_Z, -1),
            KEY_CATEGORY_MINEWINX
    );

    public static final KeyMapping USE_POWER_KEY2 = new KeyMapping(
            KEY_USE_POWER_2,
            KeyConflictContext.IN_GAME,
            InputConstants.getKey(InputConstants.KEY_X, -1),
            KEY_CATEGORY_MINEWINX
    );

    public static final KeyMapping USE_POWER_KEY3 = new KeyMapping(
            KEY_USE_POWER_3,
            KeyConflictContext.IN_GAME,
            InputConstants.getKey(InputConstants.KEY_C, -1),
            KEY_CATEGORY_MINEWINX
    );

}
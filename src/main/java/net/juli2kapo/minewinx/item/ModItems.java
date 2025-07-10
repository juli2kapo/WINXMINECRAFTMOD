package net.juli2kapo.minewinx.item;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.item.custom.ManaRadarItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MineWinx.MOD_ID);

    //public static final RegistryObject<Item> AMULET =
    //        ITEMS.register("amulet", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> LOWQMANACRYSTAL =
            ITEMS.register("low_quality_mana_crystal", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> MEDIUMQMANACRYSTAL =
            ITEMS.register("medium_quality_mana_crystal", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> HIGHQMANACRYSTAL =
            ITEMS.register("high_quality_mana_crystal", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> MANARADAR =
            ITEMS.register("mana_radar", ()-> new ManaRadarItem( new Item.Properties().durability(100)));



    public static final RegistryObject<Item> FIRESTAGE1 =
            ITEMS.register("fire_stage_1", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> FIRESTAGE2 =
            ITEMS.register("fire_stage_2", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> FIRESTAGE3 =
            ITEMS.register("fire_stage_3", ()-> new Item( new Item.Properties()));

    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}

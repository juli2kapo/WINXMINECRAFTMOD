package net.juli2kapo.minewinx.item;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.item.custom.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MineWinx.MOD_ID);

    // Mana Crystals
    public static final RegistryObject<Item> LOWQMANACRYSTAL =
            ITEMS.register("low_quality_mana_crystal", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> MEDIUMQMANACRYSTAL =
            ITEMS.register("medium_quality_mana_crystal", ()-> new Item( new Item.Properties()));

    public static final RegistryObject<Item> HIGHQMANACRYSTAL =
            ITEMS.register("high_quality_mana_crystal", ()-> new Item( new Item.Properties()));

    // Tools
    public static final RegistryObject<Item> MANARADAR =
            ITEMS.register("mana_radar", ()-> new ManaRadarItem( new Item.Properties().durability(100)));

    // Element Seeds
    public static final RegistryObject<Item> FIRESTAGE1 =
            ITEMS.register("fire_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Fire", 1));
    public static final RegistryObject<Item> FIRESTAGE2 =
            ITEMS.register("fire_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Fire", 2));
    public static final RegistryObject<Item> FIRESTAGE3 =
            ITEMS.register("fire_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Fire", 3));

    public static final RegistryObject<Item> WATERSTAGE1 =
            ITEMS.register("water_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Water", 1));
    public static final RegistryObject<Item> WATERSTAGE2 =
            ITEMS.register("water_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Water", 2));
    public static final RegistryObject<Item> WATERSTAGE3 =
            ITEMS.register("water_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Water", 3));

    public static final RegistryObject<Item> NATURESTAGE1 =
            ITEMS.register("nature_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Nature", 1));
    public static final RegistryObject<Item> NATURESTAGE2 =
            ITEMS.register("nature_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Nature", 2));
    public static final RegistryObject<Item> NATURESTAGE3 =
            ITEMS.register("nature_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Nature", 3));

    public static final RegistryObject<Item> ICESTAGE1 =
            ITEMS.register("ice_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Ice", 1));
    public static final RegistryObject<Item> ICESTAGE2 =
            ITEMS.register("ice_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Ice", 2));
    public static final RegistryObject<Item> ICESTAGE3 =
            ITEMS.register("ice_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Ice", 3));

    public static final RegistryObject<Item> MUSICSTAGE1 =
            ITEMS.register("music_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Music", 1));
    public static final RegistryObject<Item> MUSICSTAGE2 =
            ITEMS.register("music_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Music", 2));
    public static final RegistryObject<Item> MUSICSTAGE3 =
            ITEMS.register("music_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Music", 3));

    public static final RegistryObject<Item> TECHNOLOGYSTAGE1 =
            ITEMS.register("technology_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Technology", 1));
    public static final RegistryObject<Item> TECHNOLOGYSTAGE2 =
            ITEMS.register("technology_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Technology", 2));
    public static final RegistryObject<Item> TECHNOLOGYSTAGE3 =
            ITEMS.register("technology_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Technology", 3));

    public static final RegistryObject<Item> DARKSTAGE1 =
            ITEMS.register("dark_stage_1", ()-> new ElementSeed( new Item.Properties().durability(1), "Dark", 1));
    public static final RegistryObject<Item> DARKSTAGE2 =
            ITEMS.register("dark_stage_2", ()-> new ElementSeed( new Item.Properties().durability(1), "Dark", 2));
    public static final RegistryObject<Item> DARKSTAGE3 =
            ITEMS.register("dark_stage_3", ()-> new ElementSeed( new Item.Properties().durability(1), "Dark", 3));

    // Illusion Items
    public static final RegistryObject<Item> SKELETON_ILLUSION =
            ITEMS.register("skeleton_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.SKELETON));
    public static final RegistryObject<Item> CREEPER_ILLUSION =
            ITEMS.register("creeper_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.CREEPER));
    public static final RegistryObject<Item> ZOMBIE_ILLUSION =
            ITEMS.register("zombie_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.ZOMBIE));
    public static final RegistryObject<Item> DROWNED_ILLUSION =
            ITEMS.register("drowned_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.DROWNED));
    public static final RegistryObject<Item> VINDICATOR_ILLUSION =
            ITEMS.register("vindicator_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.VINDICATOR));
    public static final RegistryObject<Item> PILLAGER_ILLUSION =
            ITEMS.register("pillager_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.PILLAGER));
    public static final RegistryObject<Item> IRON_GOLEM_ILLUSION =
            ITEMS.register("iron_golem_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.IRON_GOLEM));
    public static final RegistryObject<Item> ENDERMAN_ILLUSION =
            ITEMS.register("enderman_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.ENDERMAN));
    public static final RegistryObject<Item> SPIDER_ILLUSION =
            ITEMS.register("spider_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.SPIDER));
    public static final RegistryObject<Item> WITHER_SKELETON_ILLUSION =
            ITEMS.register("wither_skeleton_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.WITHER_SKELETON));
    public static final RegistryObject<Item> COW_ILLUSION =
            ITEMS.register("cow_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.COW));
    public static final RegistryObject<Item> CAT_ILLUSION =
            ITEMS.register("cat_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.CAT));
    public static final RegistryObject<Item> CHICKEN_ILLUSION =
            ITEMS.register("chicken_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.CHICKEN));
    public static final RegistryObject<Item> HORSE_ILLUSION =
            ITEMS.register("horse_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.HORSE));
    public static final RegistryObject<Item> RABBIT_ILLUSION =
            ITEMS.register("rabbit_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.RABBIT));
    public static final RegistryObject<Item> VILLAGER_ILLUSION =
            ITEMS.register("villager_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.VILLAGER));
    public static final RegistryObject<Item> PIG_ILLUSION =
            ITEMS.register("pig_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.PIG));
    public static final RegistryObject<Item> SHEEP_ILLUSION =
            ITEMS.register("sheep_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.SHEEP));
    public static final RegistryObject<Item> WOLF_ILLUSION =
            ITEMS.register("wolf_illusion", ()-> new IllusionCreator( new Item.Properties().durability(1), EntityType.WOLF));


    public static final RegistryObject<Item> PLAYERILLUSION =
            ITEMS.register("player_illusion", ()-> new PlayerIllusion( new Item.Properties().durability(1)));

    // Utility
    public static final RegistryObject<Item> CLEANSER =
            ITEMS.register("cleanser", ()-> new ElementAndStageRemover( new Item.Properties().durability(1)));
    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
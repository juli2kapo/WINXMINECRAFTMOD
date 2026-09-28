package net.juli2kapo.factoryascent.gear;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/**
 * Things you carry and wear from the early and middle ages: bronze armour, the Bronze Backpack
 * and Grappling Hook (Bronze), the Item Magnet and Night-Vision Goggles (Electric), plus the
 * small materials that came with the new machines (tin nuggets from the Sieve, scrap and scrap
 * boxes from the Recycler).
 */
public final class GearContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);

    public static final ResourceKey<EquipmentAsset> BRONZE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "bronze"));
    public static final ResourceKey<EquipmentAsset> GOGGLES_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "night_vision_goggles"));
    private static final TagKey<Item> BRONZE_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/bronze"));

    /** Iron's protection, a little less durable, a touch of toughness, more enchantable. */
    public static final ArmorMaterial BRONZE = new ArmorMaterial(13,
            Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2, ArmorType.BODY, 5),
            14, SoundEvents.ARMOR_EQUIP_IRON, 0.5f, 0f, BRONZE_INGOTS, BRONZE_ASSET);
    private static final ArmorMaterial GOGGLES = new ArmorMaterial(5, Map.of(ArmorType.HELMET, 1), 0,
            SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0f, TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("leather")), GOGGLES_ASSET);

    public static final Supplier<DataComponentType<Boolean>> MAGNET_ON = COMPONENTS.registerComponentType("magnet_on",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredItem<Item> BRONZE_HELMET = armor("bronze_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> BRONZE_CHESTPLATE = armor("bronze_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> BRONZE_LEGGINGS = armor("bronze_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> BRONZE_BOOTS = armor("bronze_boots", ArmorType.BOOTS);
    public static final DeferredItem<BackpackItem> BRONZE_BACKPACK = ITEMS.registerItem("bronze_backpack", BackpackItem::new,
            p -> p.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    public static final DeferredItem<GrapplingHookItem> GRAPPLING_HOOK = ITEMS.registerItem("grappling_hook", GrapplingHookItem::new,
            p -> p.durability(160).repairable(BRONZE_INGOTS));
    public static final DeferredItem<ItemMagnetItem> ITEM_MAGNET = ITEMS.registerItem("item_magnet", ItemMagnetItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<NightVisionGogglesItem> NIGHT_VISION_GOGGLES = ITEMS.registerItem("night_vision_goggles",
            NightVisionGogglesItem::new, p -> p.stacksTo(1)
                    .attributes(GOGGLES.createAttributes(ArmorType.HELMET))
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                            .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).setAsset(GOGGLES_ASSET).build()));
    public static final DeferredItem<Item> TIN_NUGGET = ITEMS.registerSimpleItem("tin_nugget");
    public static final DeferredItem<Item> SCRAP = ITEMS.registerSimpleItem("scrap");
    public static final DeferredItem<ScrapBoxItem> SCRAP_BOX = ITEMS.registerItem("scrap_box", ScrapBoxItem::new);

    public static final Supplier<MenuType<BackpackMenu>> BACKPACK_MENU = MENUS.register("backpack",
            () -> IMenuTypeExtension.create(BackpackMenu::fromNetwork));

    private static DeferredItem<Item> armor(String name, ArmorType type) {
        return ITEMS.registerItem(name, Item::new, p -> p.humanoidArmor(BRONZE, type));
    }

    private GearContent() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(GearContent::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access,
                ModComponents.ENERGY.get(), ItemMagnetItem.CAPACITY, ItemMagnetItem.CAPACITY / 40, 0), ITEM_MAGNET.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access,
                ModComponents.ENERGY.get(), NightVisionGogglesItem.CAPACITY, NightVisionGogglesItem.CAPACITY / 50, 0),
                NIGHT_VISION_GOGGLES.get());
    }

    /** Tools tab, in age order. */
    public static List<Supplier<? extends ItemLike>> toolItems() {
        return List.of(BRONZE_HELMET, BRONZE_CHESTPLATE, BRONZE_LEGGINGS, BRONZE_BOOTS, BRONZE_BACKPACK, GRAPPLING_HOOK,
                ITEM_MAGNET, NIGHT_VISION_GOGGLES);
    }

    /** Materials tab. */
    public static List<Supplier<? extends ItemLike>> materialItems() {
        return List.of(TIN_NUGGET, SCRAP, SCRAP_BOX);
    }
}

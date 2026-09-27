package net.juli2kapo.factoryascent.registry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.item.ForgeHammerItem;
import net.juli2kapo.factoryascent.item.MoldItem;
import net.juli2kapo.factoryascent.item.TieredBlockItem;
import net.juli2kapo.factoryascent.item.UpgradeItem;
import net.juli2kapo.factoryascent.item.UpgradeKitItem;
import net.juli2kapo.factoryascent.item.WrenchItem;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);

    /** Plain crafting materials, by registry name, in creative-tab order. */
    public static final Map<String, DeferredItem<Item>> MATERIALS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<MoldItem>> MOLDS = new LinkedHashMap<>();
    public static final EnumMap<Tier, DeferredItem<Item>> CIRCUITS = new EnumMap<>(Tier.class);
    public static final EnumMap<Tier, DeferredItem<Item>> FRAMES = new EnumMap<>(Tier.class);
    public static final EnumMap<Tier, DeferredItem<UpgradeKitItem>> UPGRADE_KITS = new EnumMap<>(Tier.class);

    public static final DeferredItem<Item> RAW_TIN = material("raw_tin");
    public static final DeferredItem<Item> RAW_BAUXITE = material("raw_bauxite");
    public static final DeferredItem<Item> RAW_TITANIUM = material("raw_titanium");

    static {
        for (String dust : new String[]{"iron", "copper", "gold", "tin", "coal", "quartz", "bauxite", "titanium"}) {
            material(dust + "_dust");
        }
        for (String ingot : new String[]{"tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"}) {
            material(ingot + "_ingot");
        }
        material("silicon");
        material("silicon_wafer");
        for (String plate : new String[]{"iron", "copper", "tin", "bronze", "steel", "aluminum", "titanium"}) {
            material(plate + "_plate");
        }
        for (String gear : new String[]{"iron", "bronze", "steel", "titanium"}) {
            material(gear + "_gear");
        }
        material("iron_rod");
        material("steel_rod");
        material("copper_wire");
        material("gold_wire");
        material("motor");
        material("heating_coil");
        for (String mold : new String[]{"plate", "gear", "rod", "wire"}) {
            MOLDS.put(mold + "_mold", ITEMS.registerItem(mold + "_mold", MoldItem::new, p -> p.stacksTo(1)));
        }
        for (Tier tier : Tier.VALUES) {
            CIRCUITS.put(tier, ITEMS.registerSimpleItem(tier.id() + "_circuit"));
            FRAMES.put(tier, ITEMS.registerSimpleItem(tier.id() + "_machine_frame"));
            if (tier != Tier.BASIC) {
                UPGRADE_KITS.put(tier, ITEMS.registerItem(tier.id() + "_upgrade_kit", p -> new UpgradeKitItem(tier, p),
                        p -> p.stacksTo(16).rarity(tier == Tier.ULTIMATE ? Rarity.EPIC : tier == Tier.ELITE ? Rarity.RARE : Rarity.UNCOMMON)));
            }
        }
    }

    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade",
            p -> new UpgradeItem(UpgradeItem.Kind.SPEED, p), p -> p.stacksTo(16));
    public static final DeferredItem<UpgradeItem> ENERGY_UPGRADE = ITEMS.registerItem("energy_upgrade",
            p -> new UpgradeItem(UpgradeItem.Kind.ENERGY, p), p -> p.stacksTo(16));
    public static final DeferredItem<ForgeHammerItem> FORGE_HAMMER = ITEMS.registerItem("forge_hammer",
            ForgeHammerItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1));

    static {
        for (MachineType type : MachineType.VALUES) {
            for (Tier tier : Tier.VALUES) {
                var block = ModBlocks.machine(type, tier);
                ITEMS.registerItem(block.getId().getPath(), p -> new TieredBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix());
            }
        }
        for (Tier tier : Tier.VALUES) {
            var cable = ModBlocks.POWER_CABLES.get(tier);
            ITEMS.registerItem(cable.getId().getPath(), p -> new TieredBlockItem(cable.get(), p), p -> p.useBlockDescriptionPrefix());
            var pipe = ModBlocks.ITEM_PIPES.get(tier);
            ITEMS.registerItem(pipe.getId().getPath(), p -> new TieredBlockItem(pipe.get(), p), p -> p.useBlockDescriptionPrefix());
        }
        ModBlocks.SIMPLE.values().forEach(ITEMS::registerSimpleBlockItem);
    }

    private static DeferredItem<Item> material(String name) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name);
        MATERIALS.put(name, item);
        return item;
    }

    private ModItems() {}
}

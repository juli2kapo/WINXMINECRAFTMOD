package net.juli2kapo.factoryascent.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.juli2kapo.factoryascent.item.ForgeHammerItem;
import net.juli2kapo.factoryascent.item.MoldItem;
import net.juli2kapo.factoryascent.item.UpgradeItem;
import net.juli2kapo.factoryascent.item.WrenchItem;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);

    /** Plain crafting materials, by registry name, in creative-tab order. */
    public static final Map<String, DeferredItem<Item>> MATERIALS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<MoldItem>> MOLDS = new LinkedHashMap<>();
    /** Tools and equipment (bronze tools, drill…), in creative-tab order. */
    public static final Map<String, DeferredItem<? extends Item>> TOOLS = new LinkedHashMap<>();

    /** Bronze: between iron and diamond in speed, a little below iron in durability, repairs with bronze. */
    public static final ToolMaterial BRONZE = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 225, 6.5f, 2f, 16,
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/bronze")));

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
        material("coke");
        material("fire_clay");
        material("fire_brick");
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
        material("basic_circuit");
        material("advanced_circuit");
        material("machine_frame");
        material("advanced_machine_frame");
        for (String mold : new String[]{"plate", "gear", "rod", "wire"}) {
            MOLDS.put(mold + "_mold", ITEMS.registerItem(mold + "_mold", MoldItem::new, p -> p.stacksTo(1)));
        }
    }

    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade",
            p -> new UpgradeItem(UpgradeItem.Kind.SPEED, p), p -> p.stacksTo(16));
    public static final DeferredItem<UpgradeItem> ENERGY_UPGRADE = ITEMS.registerItem("energy_upgrade",
            p -> new UpgradeItem(UpgradeItem.Kind.ENERGY, p), p -> p.stacksTo(16));
    public static final DeferredItem<ForgeHammerItem> FORGE_HAMMER = tool("forge_hammer",
            ITEMS.registerItem("forge_hammer", ForgeHammerItem::new, p -> p.stacksTo(1)));
    public static final DeferredItem<WrenchItem> WRENCH = tool("wrench",
            ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1)));
    public static final DeferredItem<Item> BRONZE_PICKAXE = tool("bronze_pickaxe",
            ITEMS.registerItem("bronze_pickaxe", Item::new, p -> p.pickaxe(BRONZE, 1f, -2.8f)));
    public static final DeferredItem<Item> BRONZE_AXE = tool("bronze_axe",
            ITEMS.registerItem("bronze_axe", Item::new, p -> p.axe(BRONZE, 6f, -3.1f)));
    public static final DeferredItem<Item> BRONZE_SHOVEL = tool("bronze_shovel",
            ITEMS.registerItem("bronze_shovel", Item::new, p -> p.shovel(BRONZE, 1.5f, -3f)));
    public static final DeferredItem<Item> BRONZE_HOE = tool("bronze_hoe",
            ITEMS.registerItem("bronze_hoe", Item::new, p -> p.hoe(BRONZE, -2f, -1f)));
    public static final DeferredItem<Item> BRONZE_SWORD = tool("bronze_sword",
            ITEMS.registerItem("bronze_sword", Item::new, p -> p.sword(BRONZE, 3f, -2.4f)));
    public static final DeferredItem<ElectricDrillItem> ELECTRIC_DRILL = tool("electric_drill",
            ITEMS.registerItem("electric_drill", ElectricDrillItem::new, p -> p.stacksTo(1)));

    static {
        for (MachineType type : MachineType.VALUES) blockItem(ModBlocks.machine(type));
        for (Tier tier : Tier.VALUES) {
            blockItem(ModBlocks.POWER_CABLES.get(tier));
            blockItem(ModBlocks.ITEM_PIPES.get(tier));
        }
        blockItem(ModBlocks.WOODEN_CRATE);
        blockItem(ModBlocks.BRONZE_CRATE);
        ModBlocks.SIMPLE.values().forEach(b -> blockItem(b));
    }

    private static void blockItem(net.neoforged.neoforge.registries.DeferredBlock<?> block) {
        ITEMS.registerItem(block.getId().getPath(), p -> new FactoryBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix());
    }

    private static <T extends Item> DeferredItem<T> tool(String name, DeferredItem<T> item) {
        TOOLS.put(name, item);
        return item;
    }

    private static DeferredItem<Item> material(String name) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name);
        MATERIALS.put(name, item);
        return item;
    }

    private ModItems() {}
}

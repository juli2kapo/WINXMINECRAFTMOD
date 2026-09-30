package net.juli2kapo.factoryascent.space;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/**
 * Entry point of space travel and personal gear: the orbit dimension ({@link Orbit},
 * {@link SpaceRules}), crewed launches ({@link CrewLaunch}), the Return Pod, the Astronaut Suit
 * ({@link SuitItems}) with its Oxygen Compressor and the Oxygen Sealer, and the jetpacks
 * ({@link Jetpack}) up to the Jet Suit.
 */
public final class SpaceContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FactoryAscent.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FactoryAscent.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, FactoryAscent.MOD_ID);

    // ---------------------------------------------------------------- components

    /** Air in a suit chestplate, in ticks of breathing. */
    public static final Supplier<DataComponentType<Integer>> OXYGEN = COMPONENTS.registerComponentType("oxygen",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** A jetpack's hover mode. */
    public static final Supplier<DataComponentType<Boolean>> JETPACK_HOVER = COMPONENTS.registerComponentType("jetpack_hover",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** Where a player launched from: the Return Pod brings them back above it. */
    public static final Supplier<AttachmentType<GlobalPos>> RETURN_POINT = ATTACHMENTS.register("return_point",
            () -> AttachmentType.builder(() -> GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO))
                    .serialize(GlobalPos.MAP_CODEC).copyOnDeath().build());

    // ---------------------------------------------------------------- blocks

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 8f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    public static final DeferredBlock<ReturnPodBlock> RETURN_POD = BLOCKS.registerBlock("return_pod",
            ReturnPodBlock::new, () -> metal().mapColor(MapColor.SNOW).noOcclusion().lightLevel(s -> 5));
    public static final DeferredBlock<OxygenMachineBlock> OXYGEN_COMPRESSOR = BLOCKS.registerBlock("oxygen_compressor",
            p -> new OxygenMachineBlock(p, OxygenMachineBlock.Kind.COMPRESSOR),
            () -> metal().mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> s.getValue(OxygenMachineBlock.LIT) ? 7 : 0));
    public static final DeferredBlock<OxygenMachineBlock> OXYGEN_SEALER = BLOCKS.registerBlock("oxygen_sealer",
            p -> new OxygenMachineBlock(p, OxygenMachineBlock.Kind.SEALER),
            () -> metal().mapColor(MapColor.COLOR_CYAN).lightLevel(s -> s.getValue(OxygenMachineBlock.LIT) ? 10 : 0));
    public static final DeferredBlock<OxygenMachineBlock> AIR_VENT = BLOCKS.registerBlock("air_vent",
            p -> new OxygenMachineBlock(p, OxygenMachineBlock.Kind.VENT),
            () -> metal().mapColor(MapColor.COLOR_LIGHT_GRAY).lightLevel(s -> s.getValue(OxygenMachineBlock.LIT) ? 6 : 0));

    public static final DeferredItem<BlockItem> RETURN_POD_ITEM = ITEMS.registerItem("return_pod",
            p -> new FactoryBlockItem(RETURN_POD.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> OXYGEN_COMPRESSOR_ITEM = ITEMS.registerItem("oxygen_compressor",
            p -> new FactoryBlockItem(OXYGEN_COMPRESSOR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> OXYGEN_SEALER_ITEM = ITEMS.registerItem("oxygen_sealer",
            p -> new FactoryBlockItem(OXYGEN_SEALER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> AIR_VENT_ITEM = ITEMS.registerItem("air_vent",
            p -> new FactoryBlockItem(AIR_VENT.get(), p), p -> p.useBlockDescriptionPrefix());

    public static final Supplier<BlockEntityType<OxygenCompressorBlockEntity>> OXYGEN_COMPRESSOR_BE = BLOCK_ENTITIES.register(
            "oxygen_compressor", () -> new BlockEntityType<>(OxygenCompressorBlockEntity::new, OXYGEN_COMPRESSOR.get()));
    public static final Supplier<BlockEntityType<OxygenSealerBlockEntity>> OXYGEN_SEALER_BE = BLOCK_ENTITIES.register(
            "oxygen_sealer", () -> new BlockEntityType<>(OxygenSealerBlockEntity::new, OXYGEN_SEALER.get(), AIR_VENT.get()));

    public static final Supplier<MenuType<OxygenCompressorMenu>> OXYGEN_COMPRESSOR_MENU = MENUS.register(
            "oxygen_compressor", () -> IMenuTypeExtension.create(OxygenCompressorMenu::fromNetwork));

    // ---------------------------------------------------------------- items

    private static Item.Properties suit(Item.Properties p, ArmorType type) {
        return p.humanoidArmor(SuitItems.MATERIAL, type).component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
    }

    public static final DeferredItem<SuitItems.SuitPieceItem> ASTRONAUT_HELMET = ITEMS.registerItem("astronaut_helmet",
            p -> new SuitItems.SuitPieceItem(p, ArmorType.HELMET), p -> suit(p, ArmorType.HELMET));
    public static final DeferredItem<SuitItems.SuitChestItem> ASTRONAUT_SUIT = ITEMS.registerItem("astronaut_suit",
            SuitItems.SuitChestItem::new, p -> suit(p, ArmorType.CHESTPLATE));
    public static final DeferredItem<SuitItems.SuitPieceItem> ASTRONAUT_LEGGINGS = ITEMS.registerItem("astronaut_leggings",
            p -> new SuitItems.SuitPieceItem(p, ArmorType.LEGGINGS), p -> suit(p, ArmorType.LEGGINGS));
    public static final DeferredItem<SuitItems.SuitPieceItem> ASTRONAUT_BOOTS = ITEMS.registerItem("astronaut_boots",
            p -> new SuitItems.SuitPieceItem(p, ArmorType.BOOTS), p -> suit(p, ArmorType.BOOTS));
    public static final DeferredItem<JetSuitItem> JET_SUIT = ITEMS.registerItem("jet_suit",
            JetSuitItem::new, p -> suit(p, ArmorType.CHESTPLATE));
    /**
     * Jetpacks sit in the chest slot with the harness as their armour look (equipment asset
     * {@code jetpack}); the pack itself is a 3D model drawn by the client's jetpack layer.
     */
    private static Item.Properties jetpack(Item.Properties p) {
        return p.stacksTo(1).component(DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.CHEST)
                .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON).setAsset(JETPACK_ASSET).build());
    }

    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> JETPACK_ASSET =
            net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "jetpack"));
    public static final DeferredItem<JetpackItem> ELECTRIC_JETPACK = ITEMS.registerItem("electric_jetpack",
            p -> new JetpackItem(p, Jetpack.Tier.ELECTRIC), SpaceContent::jetpack);
    public static final DeferredItem<JetpackItem> ADVANCED_JETPACK = ITEMS.registerItem("advanced_jetpack",
            p -> new JetpackItem(p, Jetpack.Tier.ADVANCED), SpaceContent::jetpack);
    public static final DeferredItem<CrewCapsuleItem> CREW_CAPSULE = ITEMS.registerItem("crew_capsule",
            CrewCapsuleItem::new, p -> p.stacksTo(1));

    // ---------------------------------------------------------------- entity & recipe

    public static final DeferredHolder<EntityType<?>, EntityType<RocketSeatEntity>> ROCKET_SEAT = ENTITIES.registerEntityType(
            "rocket_seat", RocketSeatEntity::new, MobCategory.MISC,
            b -> b.sized(0.6f, 0.6f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(2));

    public static final Supplier<RecipeSerializer<TransmuteRecipe>> JET_SUIT_RECIPE =
            RECIPE_SERIALIZERS.register("jet_suit", () -> JetSuitRecipe.SERIALIZER);

    private SpaceContent() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, SpaceConfig.SPEC, FactoryAscent.MOD_ID + "-space-server.toml");
        net.juli2kapo.factoryascent.space.planet.PlanetContent.register(modBus);
        net.juli2kapo.factoryascent.space.station.StationContent.register(modBus);
        modBus.addListener(SpacePayloads::register);
        modBus.addListener(SpaceContent::registerCapabilities);
        SpaceEvents.register();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.Energy.BLOCK, OXYGEN_COMPRESSOR_BE.get(), (be, side) -> be.energyHandler());
        e.registerBlockEntity(Capabilities.Energy.BLOCK, OXYGEN_SEALER_BE.get(), (be, side) -> be.energyHandler());
        for (var item : List.of(ELECTRIC_JETPACK, ADVANCED_JETPACK, JET_SUIT)) {
            int capacity = ((Jetpack.Gear) item.get()).jetTier().capacity;
            e.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access,
                    ModComponents.ENERGY.get(), capacity, capacity / 100, 0), item.get());
        }
    }

    /** Space items for the Orbital creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> orbitalItems() {
        List<Supplier<? extends ItemLike>> out = new java.util.ArrayList<>(List.of(CREW_CAPSULE, RETURN_POD_ITEM, ASTRONAUT_HELMET,
                ASTRONAUT_SUIT, ASTRONAUT_LEGGINGS, ASTRONAUT_BOOTS, JET_SUIT, OXYGEN_COMPRESSOR_ITEM, OXYGEN_SEALER_ITEM, AIR_VENT_ITEM));
        out.addAll(net.juli2kapo.factoryascent.space.station.StationContent.creativeItems());
        out.addAll(net.juli2kapo.factoryascent.space.planet.PlanetContent.creativeItems());
        return out;
    }

    /** Jetpacks for the Tools creative tab. */
    public static List<Supplier<? extends ItemLike>> toolItems() {
        return List.of(ELECTRIC_JETPACK, ADVANCED_JETPACK);
    }

    /** Grants a code-triggered advancement (criterion "done"). */
    public static void award(ServerPlayer player, String key) {
        AdvancementHolder holder = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, key));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "done");
        }
    }
}

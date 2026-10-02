package net.juli2kapo.factoryascent.capsule;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Capsule machines (Industrial age): the Size Chamber (resizes and heals the mob inside a Mob
 * Capsule with FE) and the Mob Releaser (lets every captured mob in it out at once on a redstone
 * pulse, at a set point in front of it).
 */
public final class CapsuleContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> UTILITY_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "utility"));

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(4.0f, 8f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(DeviceBlock.ACTIVE) ? 7 : 0);
    }

    public static final DeferredBlock<SizeChamberBlock> SIZE_CHAMBER = BLOCKS.registerBlock("size_chamber",
            SizeChamberBlock::new, () -> props(MapColor.COLOR_CYAN));
    public static final DeferredBlock<MobReleaserBlock> MOB_RELEASER = BLOCKS.registerBlock("mob_releaser",
            MobReleaserBlock::new, () -> props(MapColor.COLOR_MAGENTA));

    public static final DeferredItem<BlockItem> SIZE_CHAMBER_ITEM = ITEMS.registerItem("size_chamber",
            p -> new FactoryBlockItem(SIZE_CHAMBER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> MOB_RELEASER_ITEM = ITEMS.registerItem("mob_releaser",
            p -> new FactoryBlockItem(MOB_RELEASER.get(), p), p -> p.useBlockDescriptionPrefix());

    public static final Supplier<BlockEntityType<SizeChamberBlockEntity>> SIZE_CHAMBER_BE = BLOCK_ENTITIES.register(
            "size_chamber", () -> new BlockEntityType<>(SizeChamberBlockEntity::new, SIZE_CHAMBER.get()));
    public static final Supplier<BlockEntityType<MobReleaserBlockEntity>> MOB_RELEASER_BE = BLOCK_ENTITIES.register(
            "mob_releaser", () -> new BlockEntityType<>(MobReleaserBlockEntity::new, MOB_RELEASER.get()));

    public static final Supplier<MenuType<SizeChamberMenu>> SIZE_CHAMBER_MENU = MENUS.register("size_chamber",
            () -> IMenuTypeExtension.create(SizeChamberMenu::fromNetwork));
    public static final Supplier<MenuType<MobReleaserMenu>> MOB_RELEASER_MENU = MENUS.register("mob_releaser",
            () -> IMenuTypeExtension.create(MobReleaserMenu::fromNetwork));

    private CapsuleContent() {}

    static void register(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, CapsuleConfig.SPEC, FactoryAscent.MOD_ID + "-capsule-server.toml");
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.Item.BLOCK, SIZE_CHAMBER_BE.get(), (be, side) -> be.inventory);
            e.registerBlockEntity(Capabilities.Energy.BLOCK, SIZE_CHAMBER_BE.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.Item.BLOCK, MOB_RELEASER_BE.get(), (be, side) -> be.inventory);
            e.registerBlockEntity(Capabilities.Energy.BLOCK, MOB_RELEASER_BE.get(), (be, side) -> be.energy());
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(UTILITY_TAB)) creativeItems().forEach(i -> e.accept(i.get()));
        });
    }

    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(SIZE_CHAMBER_ITEM, MOB_RELEASER_ITEM);
    }
}

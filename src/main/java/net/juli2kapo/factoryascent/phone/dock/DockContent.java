package net.juli2kapo.factoryascent.phone.dock;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.capsule.DeviceBlock;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.minecraft.core.component.DataComponentType;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Linking the Factory Phone through an intermediary: the Link Card (records one block) and the
 * Phone Dock (a desk computer that writes cards into the phone, lists and removes its links, and
 * charges it).
 */
public final class DockContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "tools"));

    /** The block a Link Card recorded. */
    public static final Supplier<DataComponentType<PhoneMemory.Link>> LINK_CARD = COMPONENTS.registerComponentType("link_card",
            b -> b.persistent(PhoneMemory.Link.CODEC).networkSynchronized(PhoneMemory.Link.STREAM_CODEC));

    public static final DeferredBlock<PhoneDockBlock> PHONE_DOCK = BLOCKS.registerBlock("phone_dock", PhoneDockBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f, 6f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(DeviceBlock.ACTIVE) ? 6 : 2));
    public static final DeferredItem<BlockItem> PHONE_DOCK_ITEM = ITEMS.registerItem("phone_dock",
            p -> new FactoryBlockItem(PHONE_DOCK.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<LinkCardItem> LINK_CARD_ITEM = ITEMS.registerItem("link_card", LinkCardItem::new,
            p -> p.stacksTo(LinkCardItem.BLANK_STACK));

    public static final Supplier<BlockEntityType<PhoneDockBlockEntity>> PHONE_DOCK_BE = BLOCK_ENTITIES.register("phone_dock",
            () -> new BlockEntityType<>(PhoneDockBlockEntity::new, PHONE_DOCK.get()));
    public static final Supplier<MenuType<PhoneDockMenu>> PHONE_DOCK_MENU = MENUS.register("phone_dock",
            () -> IMenuTypeExtension.create(PhoneDockMenu::fromNetwork));

    private DockContent() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.Item.BLOCK, PHONE_DOCK_BE.get(), (be, side) -> be.inventory);
            e.registerBlockEntity(Capabilities.Energy.BLOCK, PHONE_DOCK_BE.get(), (be, side) -> be.energy());
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(TOOLS_TAB)) creativeItems().forEach(i -> e.accept(i.get()));
        });
    }

    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(PHONE_DOCK_ITEM, LINK_CARD_ITEM);
    }
}

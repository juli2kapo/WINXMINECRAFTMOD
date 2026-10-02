package net.juli2kapo.factoryascent.storagenet;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry object of the storage network: blocks, items, block entities, menus and the cell component. */
public final class StorageContent {
    private StorageContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);

    // ---------------------------------------------------------------- data components

    /** Items stored in a cell; travels with the cell item. */
    public static final Supplier<DataComponentType<CellContents>> CELL_CONTENTS = COMPONENTS.registerComponentType("cell_contents",
            b -> b.persistent(CellContents.CODEC).networkSynchronized(CellContents.STREAM_CODEC));

    // ---------------------------------------------------------------- blocks

    private static BlockBehaviour.Properties device() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion();
    }

    public static final DeferredBlock<StorageCableBlock> CABLE = BLOCKS.registerBlock("storage_cable", StorageCableBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.8f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<StorageDeviceBlock> CONTROLLER = BLOCKS.registerBlock("storage_controller",
            p -> new StorageDeviceBlock(StorageDeviceBlock.Kind.CONTROLLER, p), () -> device().lightLevel(StorageDeviceBlock::light));
    public static final DeferredBlock<StorageDriveBlock> DRIVE = BLOCKS.registerBlock("storage_drive",
            StorageDriveBlock::new, StorageContent::device);
    public static final DeferredBlock<StorageDeviceBlock> TERMINAL = BLOCKS.registerBlock("storage_terminal",
            p -> new StorageDeviceBlock(StorageDeviceBlock.Kind.TERMINAL, p), () -> device().lightLevel(StorageDeviceBlock::light));
    public static final DeferredBlock<StorageDeviceBlock> INTERFACE = BLOCKS.registerBlock("storage_interface",
            p -> new StorageDeviceBlock(StorageDeviceBlock.Kind.INTERFACE, p), StorageContent::device);

    // ---------------------------------------------------------------- items

    public static final DeferredItem<StorageBlockItem> CABLE_ITEM = blockItem(CABLE);
    public static final DeferredItem<StorageBlockItem> CONTROLLER_ITEM = blockItem(CONTROLLER);
    public static final DeferredItem<StorageBlockItem> DRIVE_ITEM = blockItem(DRIVE);
    public static final DeferredItem<StorageBlockItem> TERMINAL_ITEM = blockItem(TERMINAL);
    public static final DeferredItem<StorageBlockItem> INTERFACE_ITEM = blockItem(INTERFACE);

    public static final DeferredItem<StorageCellItem> CELL_1K = ITEMS.registerItem("storage_cell_1k",
            p -> new StorageCellItem(1_024, StorageCellItem.MAX_TYPES, p), p -> p.stacksTo(1));
    public static final DeferredItem<StorageCellItem> CELL_4K = ITEMS.registerItem("storage_cell_4k",
            p -> new StorageCellItem(4_096, StorageCellItem.MAX_TYPES, p), p -> p.stacksTo(1));

    private static DeferredItem<StorageBlockItem> blockItem(DeferredBlock<?> block) {
        return ITEMS.registerItem(block.getId().getPath(), p -> new StorageBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix());
    }

    // ---------------------------------------------------------------- block entities

    public static final Supplier<BlockEntityType<StorageCableBlockEntity>> CABLE_BE = BLOCK_ENTITIES.register("storage_cable",
            () -> new BlockEntityType<>(StorageCableBlockEntity::new, CABLE.get()));
    public static final Supplier<BlockEntityType<StorageControllerBlockEntity>> CONTROLLER_BE = BLOCK_ENTITIES.register("storage_controller",
            () -> new BlockEntityType<>(StorageControllerBlockEntity::new, CONTROLLER.get()));
    public static final Supplier<BlockEntityType<StorageDriveBlockEntity>> DRIVE_BE = BLOCK_ENTITIES.register("storage_drive",
            () -> new BlockEntityType<>(StorageDriveBlockEntity::new, DRIVE.get()));
    public static final Supplier<BlockEntityType<StorageTerminalBlockEntity>> TERMINAL_BE = BLOCK_ENTITIES.register("storage_terminal",
            () -> new BlockEntityType<>(StorageTerminalBlockEntity::new, TERMINAL.get()));
    public static final Supplier<BlockEntityType<StorageInterfaceBlockEntity>> INTERFACE_BE = BLOCK_ENTITIES.register("storage_interface",
            () -> new BlockEntityType<>(StorageInterfaceBlockEntity::new, INTERFACE.get()));

    // ---------------------------------------------------------------- menus

    public static final Supplier<MenuType<DriveMenu>> DRIVE_MENU = MENUS.register("storage_drive",
            () -> IMenuTypeExtension.create(DriveMenu::fromNetwork));
    public static final Supplier<MenuType<TerminalMenu>> TERMINAL_MENU = MENUS.register("storage_terminal",
            () -> IMenuTypeExtension.create(TerminalMenu::fromNetwork));

    /** Creative tab order: the way a player builds a network. */
    static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(CONTROLLER_ITEM, CABLE_ITEM, DRIVE_ITEM, CELL_1K, CELL_4K, TERMINAL_ITEM, INTERFACE_ITEM);
    }
}

package net.juli2kapo.factoryascent.storage;

import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Crate contents live in the block entity and travel with the item (the {@code minecraft:container}
 * component, copied by the loot table), so breaking a crate never spills it.
 */
public class CrateBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items;

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRATE.get(), pos, state);
        this.items = NonNullList.withSize(rows(state) * 9, ItemStack.EMPTY);
    }

    private static int rows(BlockState state) {
        return state.getBlock() instanceof CrateBlock crate ? crate.rows() : 3;
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        int rows = rows(getBlockState());
        MenuType<ChestMenu> type = rows >= 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3;
        return new ChestMenu(type, containerId, inventory, this, rows >= 6 ? 6 : 3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, false);
    }

    /** Contents stay inside the dropped crate item instead of spilling. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }
}

package net.juli2kapo.factoryascent.nuclear;

import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The Waste Barrel's 27 shielded slots (radioactive items only). */
public class WasteBarrelBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public WasteBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.WASTE_BARREL_BE.get(), pos, state);
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
        return 27;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return Radiation.isRadioactive(stack);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new Menu(containerId, inventory, this);
    }

    /** A chest screen whose barrel slots only take radioactive items. */
    private static final class Menu extends ChestMenu {
        Menu(int id, Inventory inventory, WasteBarrelBlockEntity barrel) {
            super(MenuType.GENERIC_9x3, id, inventory, barrel, 3);
        }

        @Override
        protected Slot addSlot(Slot slot) {
            if (slot.container instanceof WasteBarrelBlockEntity) {
                slot = new Slot(slot.container, slot.getContainerSlot(), slot.x, slot.y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return Radiation.isRadioactive(stack);
                    }
                };
            }
            return super.addSlot(slot);
        }
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

    /** Contents stay inside the dropped barrel instead of spilling. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }
}

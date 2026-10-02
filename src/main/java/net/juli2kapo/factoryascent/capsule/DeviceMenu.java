package net.juli2kapo.factoryascent.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The menu of a {@link DeviceBlockEntity}: its slots, the player inventory, a few synced numbers
 * and screen buttons ({@link #clickMenuButton}, validated on the server by {@link #button}).
 */
public abstract class DeviceMenu<T extends DeviceBlockEntity> extends AbstractContainerMenu {
    protected final @Nullable T device;
    protected final BlockPos pos;
    protected final ContainerData data;
    private final int deviceSlots;

    protected DeviceMenu(MenuType<?> type, int id, Inventory inventory, @Nullable T device, BlockPos pos,
                         ItemStacksResourceHandler handler, int[][] slots, ContainerData data, int invX, int invY) {
        super(type, id);
        this.device = device;
        this.pos = pos;
        this.data = data;
        this.deviceSlots = slots.length;
        for (int i = 0; i < slots.length; i++) {
            addSlot(new ResourceHandlerSlot(handler, handler::set, i, slots[i][0], slots[i][1]));
        }
        addStandardInventorySlots(inventory, invX, invY);
        addDataSlots(data);
    }

    /** The client's copy of the device's slots (its client block entity), or a blank stand-in. */
    protected static ItemStacksResourceHandler clientHandler(Inventory inventory, BlockPos pos, int size) {
        if (inventory.player.level().getBlockEntity(pos) instanceof DeviceBlockEntity be && be.inventory.size() == size) {
            return be.inventory;
        }
        return new ItemStacksResourceHandler(size);
    }

    protected static <B extends DeviceBlockEntity> @Nullable B clientDevice(Inventory inventory, BlockPos pos, Class<B> type) {
        var be = inventory.player.level().getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    /** A screen button pressed (server side). Return true if something changed. */
    protected abstract boolean button(ServerPlayer player, T device, int id);

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (device == null || !(player instanceof ServerPlayer sp)) return false;
        return button(sp, device, id);
    }

    @Override
    public boolean stillValid(Player player) {
        return device != null && Container.stillValidBlockEntity(device, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = slots.size();
        boolean moved = index < deviceSlots ? moveItemStackTo(stack, deviceSlots, end, true)
                : moveItemStackTo(stack, 0, deviceSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}

package net.juli2kapo.factoryascent.ender;

import java.util.function.BooleanSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The pearl slot of the Ender Anchor and Ender Beacon screens. It shows the pearl inside the
 * chamber and takes one when the chamber is empty; a loaded pearl can not be taken back out
 * (as in the world: it is lost when the chamber breaks).
 */
public class PearlSlot extends Slot {
    public PearlSlot(Container container, int x, int y) {
        super(container, 0, x, y);
    }

    /** Server side: a live view of the chamber. */
    static Container chamber(BooleanSupplier hasPearl, BooleanSupplier insert, Runnable onChange) {
        return new Container() {
            @Override
            public int getContainerSize() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return !hasPearl.getAsBoolean();
            }

            @Override
            public ItemStack getItem(int slot) {
                return hasPearl.getAsBoolean() ? new ItemStack(Items.ENDER_PEARL) : ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItem(int slot, int count) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                if (stack.is(Items.ENDER_PEARL) && !hasPearl.getAsBoolean()) insert.getAsBoolean();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                onChange.run();
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clearContent() {}
        };
    }

    /** Client side: holds whatever the server says is in the chamber. */
    static Container clientChamber() {
        return new SimpleContainer(1);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL) && !hasItem();
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }
}

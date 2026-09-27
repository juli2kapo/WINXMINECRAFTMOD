package net.juli2kapo.factoryascent.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The full machine inventory. Player slots and machine logic use this directly; automation goes
 * through {@link AutomationItemHandler}, which only allows inserting inputs and extracting outputs.
 */
public class MachineInventory extends ItemStacksResourceHandler {
    private final AbstractMachineBlockEntity owner;
    private final MachineSlots slots;

    public MachineInventory(AbstractMachineBlockEntity owner, MachineSlots slots) {
        super(slots.size());
        this.owner = owner;
        this.slots = slots;
    }

    public MachineSlots slots() {
        return slots;
    }

    /** Live stack in a slot. Mutating it outside a transaction is fine; call {@link #changed} after. */
    public ItemStack stack(int index) {
        return stacks.get(index);
    }

    public void setStack(int index, ItemStack stack) {
        ItemStack old = stacks.set(index, stack);
        onContentsChanged(index, old);
    }

    public void changed(int index) {
        owner.onInventoryChanged(index);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return owner.isItemValid(index, resource);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.UPGRADE) return 1;
        return super.getCapacity(index, resource);
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        owner.onInventoryChanged(index);
    }
}

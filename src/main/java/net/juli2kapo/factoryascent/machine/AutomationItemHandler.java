package net.juli2kapo.factoryascent.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** What pipes, hoppers and belts see: insert into inputs/fuel only, extract from outputs only. */
public class AutomationItemHandler implements ResourceHandler<ItemResource> {
    private final AbstractMachineBlockEntity owner;
    private final MachineInventory inventory;

    public AutomationItemHandler(AbstractMachineBlockEntity owner, MachineInventory inventory) {
        this.owner = owner;
        this.inventory = inventory;
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return inventory.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inventory.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return inventory.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        SlotRole role = inventory.slots().role(index);
        return (role == SlotRole.INPUT || role == SlotRole.FUEL) && owner.canAutomationInsert(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!isValid(index, resource)) return 0;
        return inventory.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (inventory.slots().role(index) != SlotRole.OUTPUT) return 0;
        return inventory.extract(index, resource, amount, transaction);
    }
}

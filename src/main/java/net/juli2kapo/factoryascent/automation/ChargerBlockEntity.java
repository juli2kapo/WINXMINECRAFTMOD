package net.juli2kapo.factoryascent.automation;

import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Charges whatever FE item sits in its input (anything exposing the item energy capability:
 * the drill, the size rays, the magnet, the goggles, jetpacks, other mods' tools) and moves it to
 * the output when full, so a pipe can feed it a row of tools and collect them charged.
 */
public class ChargerBlockEntity extends AbstractMachineBlockEntity {
    public static final int CAPACITY = 200_000;

    private int chargePermille;

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.CHARGER, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(CAPACITY, 4096, 0);
    }

    private int rate() {
        return (int) Math.max(1, type.baseEnergy() * speedMultiplier() * Config.MACHINE_SPEED.get());
    }

    private @Nullable EnergyHandler target() {
        if (inventory.stack(slots.firstInput()).isEmpty()) return null;
        return ItemAccess.forHandlerIndex(inventory, slots.firstInput()).getCapability(Capabilities.Energy.ITEM);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        EnergyHandler item = target();
        if (item == null) {
            chargePermille = 0;
            status = STATUS_IDLE;
            return false;
        }
        long cap = item.getCapacityAsLong();
        if (cap > 0 && item.getAmountAsLong() >= cap) {
            chargePermille = 1000;
            // Full: hand it out.
            if (inventory.stack(slots.firstOutput()).isEmpty()) {
                inventory.setStack(slots.firstOutput(), inventory.stack(slots.firstInput()).copy());
                inventory.setStack(slots.firstInput(), ItemStack.EMPTY);
                status = STATUS_IDLE;
            } else {
                status = STATUS_OUTPUT_FULL;
            }
            return false;
        }
        if (energy.energy() <= 0) {
            status = STATUS_NO_POWER;
            chargePermille = cap <= 0 ? 0 : (int) (item.getAmountAsLong() * 1000 / cap);
            return false;
        }
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = item.insert(Math.min(energy.energy(), rate()), tx);
            tx.commit();
        }
        if (moved > 0) energy.consume(moved);
        lastEnergyRate = moved;
        EnergyHandler after = target();
        chargePermille = after == null || after.getCapacityAsLong() <= 0 ? 0
                : (int) Math.min(1000, after.getAmountAsLong() * 1000 / after.getCapacityAsLong());
        status = moved > 0 ? STATUS_WORKING : STATUS_IDLE;
        return moved > 0;
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.INPUT) {
            return ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Energy.ITEM) != null;
        }
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.INPUT && inventory.stack(index).isEmpty() && isItemValid(index, resource);
    }

    /** Charge of the item being charged. */
    @Override
    public int progressPermille() {
        return chargePermille;
    }
}

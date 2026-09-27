package net.juli2kapo.factoryascent.generator;

import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Stores energy. Accepts on every face except the front; the front only outputs. That one-way
 * rule stops energy from bouncing back and forth between cells on the same network.
 */
public class EnergyCellBlockEntity extends AbstractMachineBlockEntity {
    private final EnergyHandler inputView = new View(true);
    private final EnergyHandler outputView = new View(false);
    private int lastIn;
    private int lastOut;
    private int previousEnergy;

    public EnergyCellBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static int capacity(Tier tier) {
        return 100_000 << (2 * tier.ordinal());
    }

    /** Matches the cable of the same tier: 512 / 2048 / 8192 / 32768 FE/t. */
    public static int transferRate(Tier tier) {
        return 512 << (2 * tier.ordinal());
    }

    private Tier cellTier() {
        return type.tier() == null ? Tier.LV : type.tier();
    }

    @Override
    protected void configureEnergy() {
        energy.configure(capacity(cellTier()), transferRate(cellTier()), transferRate(cellTier()));
    }

    private Direction front() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        int before = energy.energy();
        lastIn = Math.max(0, before - previousEnergy);
        lastOut = EnergyUtil.push(neighbors, energy, transferRate(cellTier()), front());
        chargeItem();
        previousEnergy = energy.energy();
        lastEnergyRate = lastIn - lastOut;
        status = lastOut > 0 || lastIn > 0 ? STATUS_WORKING : STATUS_IDLE;
        return energy.energy() > 0;
    }

    /** Tops up whatever sits in the charging slot (drills, other mods' batteries…). */
    private void chargeItem() {
        int slot = slots.firstFuel();
        if (inventory.stack(slot).isEmpty() || energy.energy() <= 0) return;
        EnergyHandler target = ItemAccess.forHandlerIndex(inventory, slot).getCapability(Capabilities.Energy.ITEM);
        if (target == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            int moved = target.insert(Math.min(energy.energy(), transferRate(cellTier())), tx);
            tx.commit();
            if (moved > 0) energy.consume(moved);
        }
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.FUEL) {
            return ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Energy.ITEM) != null;
        }
        return super.isItemValid(index, resource);
    }

    /** Energy that arrived since the previous tick (FE/t). */
    @Override
    public int extraA() {
        return lastIn;
    }

    /** Energy pushed out of the front last tick (FE/t). */
    @Override
    public int extraB() {
        return lastOut;
    }

    @Override
    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        if (side == null) return energy;
        return side == front() ? outputView : inputView;
    }

    private final class View implements EnergyHandler {
        private final boolean input;

        View(boolean input) {
            this.input = input;
        }

        @Override
        public long getAmountAsLong() {
            return energy.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return energy.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return input ? energy.insert(amount, transaction) : 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return input ? 0 : energy.extract(amount, transaction);
        }
    }
}

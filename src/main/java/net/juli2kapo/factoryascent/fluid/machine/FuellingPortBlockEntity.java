package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.CellFluidHandler;
import net.juli2kapo.factoryascent.fluid.FluidContainers;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Orbital age: the bridge between liquid rocket fuel and everything that burns Rocket Fuel
 * items. Every {@link CellFluidHandler#ROCKET_FUEL_ITEM} mB of rocket fuel in its tank counts as
 * one Rocket Fuel: it tops up a Launch Controller next to it by itself, a Shuttle docked at a pad
 * or port it touches takes fuel from it as from a chest, and item pipes can pull Rocket Fuel out.
 */
public class FuellingPortBlockEntity extends FluidMachineBlockEntity {
    public static final int BUCKET_IN = 0, BUCKET_OUT = 1;
    final FluidTank fuel;
    private final ResourceHandler<ItemResource> fuelItems = new FuelItems();
    private int delivered;

    public FuellingPortBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.FUELLING_PORT, pos, state);
        fuel = addTank(new FluidTank(32_000, r -> r.getFluid().isSame(ModFluids.ROCKET_FUEL.source()), this::setChanged).input());
        finishTanks();
    }

    public FluidTank fuel() {
        return fuel;
    }

    /** Rocket Fuel items the tank is worth. */
    public int itemsWorth() {
        return fuel.amount() / CellFluidHandler.ROCKET_FUEL_ITEM;
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return slot == BUCKET_IN && (resource.is(ModFluids.ROCKET_FUEL.bucket()) || resource.is(OrbitalContent.ROCKET_FUEL.get()));
    }

    /** Pipes and shuttles see Rocket Fuel items they can take; the slot inventory stays private. */
    @Override
    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return fuelItems;
    }

    @Override
    protected boolean tick(ServerLevel level) {
        FluidContainers.process(fuel, inventory.stack(BUCKET_IN), inventory.stack(BUCKET_OUT),
                s -> inventory.setStack(BUCKET_OUT, s), inventory::changed, true, false);
        boolean fed = false;
        if (level.getGameTime() % 10 == 0 && itemsWorth() > 0) {
            for (Direction d : Direction.values()) {
                if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof LaunchControllerBlockEntity)) continue;
                ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(d), d.getOpposite());
                if (target == null) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int in = target.insert(ItemResource.of(OrbitalContent.ROCKET_FUEL.get()), 1, tx);
                    if (in == 1) {
                        tx.commit();
                        fuel.drain(CellFluidHandler.ROCKET_FUEL_ITEM);
                        delivered++;
                        fed = true;
                        break;
                    }
                }
            }
        }
        progress = fuel.capacity() == 0 ? 0 : fuel.amount() * 1000 / fuel.capacity();
        status = fed ? ST_RUNNING : itemsWorth() > 0 ? ST_IDLE : ST_NO_FUEL;
        if (fed) setChanged();
        return fed || itemsWorth() > 0;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = itemsWorth();
        extra[1] = delivered;
    }

    /** One slot holding as many Rocket Fuel items as the tank is worth; extract only. */
    private final class FuelItems implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return itemsWorth() > 0 ? ItemResource.of(OrbitalContent.ROCKET_FUEL.get()) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return itemsWorth();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return fuel.capacity() / CellFluidHandler.ROCKET_FUEL_ITEM;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return false;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!resource.is(OrbitalContent.ROCKET_FUEL.get())) return 0;
            int n = Math.min(amount, itemsWorth());
            if (n <= 0) return 0;
            int mb = n * CellFluidHandler.ROCKET_FUEL_ITEM;
            int out = fuel.extract(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(ModFluids.ROCKET_FUEL.source()), mb, transaction);
            return out / CellFluidHandler.ROCKET_FUEL_ITEM;
        }
    }
}

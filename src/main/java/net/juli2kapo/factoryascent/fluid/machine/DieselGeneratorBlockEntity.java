package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidContainers;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.power.PowerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Automation age: a diesel engine and alternator. Burns {@code dieselPerTick} mB of diesel a tick
 * for {@code dieselOutput} FE/t (a bucket: 300 kFE). Diesel comes by pipe or bucket. Needs air.
 */
public class DieselGeneratorBlockEntity extends FluidMachineBlockEntity {
    public static final int BUCKET_IN = 0, BUCKET_OUT = 1;
    final FluidTank diesel;
    public Boolean testAirless;

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.DIESEL_GENERATOR, pos, state);
        diesel = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(ModFluids.DIESEL.source()), this::setChanged).input());
        finishTanks();
        int out = output();
        energy.configure(Math.max(50_000, out * 200), 0, out * 2);
    }

    public static int output() {
        return (int) Math.ceil(FluidsConfig.get(FluidsConfig.DIESEL_OUTPUT) * PowerConfig.generatorMultiplier());
    }

    public FluidTank diesel() {
        return diesel;
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return slot == BUCKET_IN && resource.is(ModFluids.DIESEL.bucket());
    }

    @Override
    protected boolean tick(ServerLevel level) {
        FluidContainers.process(diesel, inventory.stack(BUCKET_IN), inventory.stack(BUCKET_OUT),
                s -> inventory.setStack(BUCKET_OUT, s), inventory::changed, true, false);
        boolean airless = testAirless != null ? testAirless : PowerRules.isAirless(level);
        int burn = FluidsConfig.get(FluidsConfig.DIESEL_PER_TICK);
        int out = output();
        if (!airless && diesel.amount() >= burn && energy.space() >= out) {
            diesel.drain(burn);
            lastRate = energy.produce(out);
        } else {
            lastRate = 0;
        }
        progress = diesel.capacity() == 0 ? 0 : diesel.amount() * 1000 / diesel.capacity();
        if (airless) status = ST_NO_AIR;
        else if (lastRate > 0) status = ST_RUNNING;
        else if (energy.space() < out) status = ST_FULL;
        else status = ST_NO_FUEL;
        if (lastRate > 0) setChanged();
        return lastRate > 0;
    }
}

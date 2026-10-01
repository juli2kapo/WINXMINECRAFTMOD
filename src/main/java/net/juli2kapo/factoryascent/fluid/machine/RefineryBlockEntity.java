package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Automation age multiblock: the Refinery. The controller is the base of a distillation column
 * of three Refinery Tower sections stacked on top of it. It heats crude oil and splits each
 * 1000 mB into {@link #DIESEL} mB of diesel, {@link #ROCKET_FUEL} mB of rocket fuel, one Plastic
 * and two Tar (the rest is gas burnt to heat the column), in {@code refineryTicks} ticks at
 * {@code refineryEnergy} FE/t. Pipes fill the crude tank and take the products from the
 * controller or any tower section.
 */
public class RefineryBlockEntity extends FluidMachineBlockEntity {
    public static final int TOWER = 3, BATCH = 1000, DIESEL = 500, ROCKET_FUEL = 250, PLASTIC = 1, TAR = 2;
    public static final int PLASTIC_SLOT = 0, TAR_SLOT = 1;

    final FluidTank crude;
    final FluidTank diesel;
    final FluidTank rocketFuel;
    private boolean formed;
    private int work;

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.REFINERY, pos, state);
        crude = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(ModFluids.CRUDE_OIL.source()), this::setChanged).input());
        diesel = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(ModFluids.DIESEL.source()), this::setChanged).output());
        rocketFuel = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(ModFluids.ROCKET_FUEL.source()), this::setChanged).output());
        finishTanks();
        energy.configure(40_000, 2_000, 0);
    }

    public FluidTank crude() {
        return crude;
    }

    public FluidTank diesel() {
        return diesel;
    }

    public FluidTank rocketFuel() {
        return rocketFuel;
    }

    public boolean formed() {
        return formed;
    }

    public static boolean structureValid(Level level, BlockPos pos) {
        for (int i = 1; i <= TOWER; i++) {
            if (!level.getBlockState(pos.above(i)).is(FluidContent.REFINERY_TOWER.get())) return false;
        }
        return true;
    }

    @Override
    protected boolean tick(ServerLevel level) {
        if (level.getGameTime() % 10 == 0 || !formed && level.getGameTime() % 2 == 0) {
            boolean now = structureValid(level, worldPosition);
            if (now != formed) {
                formed = now;
                BlockState s = getBlockState();
                if (s.getValue(FluidMachineBlock.FORMED) != now) level.setBlock(worldPosition, s.setValue(FluidMachineBlock.FORMED, now), Block.UPDATE_ALL);
                if (now) {
                    for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(8))) {
                        FluidContent.award(p, "automation_refinery");
                    }
                }
            }
        }
        lastRate = 0;
        if (!formed) {
            status = ST_INCOMPLETE;
            return false;
        }
        int ticks = FluidsConfig.get(FluidsConfig.REFINERY_TICKS);
        if (work == 0) {
            boolean room = diesel.space() >= DIESEL && rocketFuel.space() >= ROCKET_FUEL
                    && inventory.canAddOutput(PLASTIC_SLOT, new ItemStack(FluidContent.PLASTIC.get(), PLASTIC))
                    && inventory.canAddOutput(TAR_SLOT, new ItemStack(FluidContent.TAR.get(), TAR));
            if (crude.amount() < BATCH) {
                status = ST_NO_INPUT;
                progress = 0;
                return false;
            }
            if (!room) {
                status = ST_FULL;
                return false;
            }
        }
        int use = FluidsConfig.get(FluidsConfig.REFINERY_ENERGY);
        if (energy.energy() < use) {
            status = ST_NO_POWER;
            return false;
        }
        energy.consume(use);
        lastRate = use;
        if (++work >= ticks) {
            work = 0;
            crude.drain(BATCH);
            diesel.forceFill(ModFluids.DIESEL.source(), DIESEL);
            rocketFuel.forceFill(ModFluids.ROCKET_FUEL.source(), ROCKET_FUEL);
            inventory.addOutput(PLASTIC_SLOT, new ItemStack(FluidContent.PLASTIC.get(), PLASTIC));
            inventory.addOutput(TAR_SLOT, new ItemStack(FluidContent.TAR.get(), TAR));
        }
        progress = work * 1000 / Math.max(1, ticks);
        status = ST_RUNNING;
        setChanged();
        return true;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = formed ? 1 : 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work = input.getIntOr("work", 0);
        formed = input.getBooleanOr("formed", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("work", work);
        output.putBoolean("formed", formed);
    }
}

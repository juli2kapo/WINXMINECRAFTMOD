package net.juli2kapo.factoryascent.generator;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Generates power from every lava source block touching it. The lava is never consumed. */
public class GeothermalGeneratorBlockEntity extends AbstractMachineBlockEntity {
    private int lavaSources;
    private Direction[] outputSides = Direction.values();
    private float energyCarry;

    public GeothermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.GEOTHERMAL_GENERATOR, pos, state);
    }

    @Override
    protected void configureEnergy() {
        int out = type.baseEnergy() / 2 * 5;
        energy.configure(Math.max(20_000, out * 200), 0, out * 2);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            int count = 0;
            List<Direction> sides = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                FluidState fluid = level.getFluidState(worldPosition.relative(dir));
                if (fluid.is(FluidTags.LAVA) && fluid.isSource()) count++;
                else sides.add(dir);
            }
            lavaSources = Math.min(count, 5);
            outputSides = sides.toArray(Direction[]::new);
        }
        float out = (float) (type.baseEnergy() / 2f * lavaSources * Config.GENERATOR_OUTPUT.get());
        float produced = out + energyCarry;
        int whole = (int) produced;
        energyCarry = produced - whole;
        lastEnergyRate = energy.produce(whole);
        EnergyUtil.push(neighbors, energy, Math.max(1, Math.round(out * 2)), outputSides);
        status = lavaSources > 0 ? STATUS_WORKING : STATUS_IDLE;
        return lavaSources > 0;
    }

    @Override
    public int extraA() {
        return lavaSources;
    }
}

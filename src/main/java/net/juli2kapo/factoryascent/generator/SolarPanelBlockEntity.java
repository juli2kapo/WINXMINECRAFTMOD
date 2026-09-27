package net.juli2kapo.factoryascent.generator;

import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Free power in daylight under open sky; half output in rain. Pushes down and sideways. */
public class SolarPanelBlockEntity extends AbstractMachineBlockEntity {
    private static final Direction[] OUTPUT_SIDES = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private int sunlightPercent;
    private float energyCarry;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.SOLAR_PANEL, pos, state);
    }

    @Override
    protected void configureEnergy() {
        int out = type.baseEnergy();
        energy.configure(Math.max(4_000, out * 400), 0, out * 2);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            if (!level.canSeeSky(worldPosition.above()) || !level.dimensionType().hasSkyLight() || level.isDarkOutside()) {
                sunlightPercent = 0;
            } else {
                sunlightPercent = level.isRaining() ? 50 : 100;
            }
        }
        float out = (float) (type.baseEnergy() * sunlightPercent / 100.0 * Config.GENERATOR_OUTPUT.get());
        float produced = out + energyCarry;
        int whole = (int) produced;
        energyCarry = produced - whole;
        lastEnergyRate = energy.produce(whole);
        EnergyUtil.push(neighbors, energy, Math.max(1, Math.round(out * 2)), OUTPUT_SIDES);
        status = sunlightPercent > 0 ? STATUS_WORKING : STATUS_IDLE;
        return sunlightPercent > 0;
    }

    @Override
    public int extraA() {
        return sunlightPercent;
    }
}

package net.juli2kapo.factoryascent.power;

import net.juli2kapo.factoryascent.util.SkyAccess;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Automation age: a big tilted array of high-efficiency cells. {@code solarArrayOutput} FE/t (six
 * Solar Panels) in daylight under open sky, half in rain, and x1.5 above the atmosphere (airless
 * dimensions have no weather to dim it).
 */
public class SolarArrayBlockEntity extends PowerBlockEntity {
    private static final Direction[] OUTPUT_SIDES = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private int sunPercent;
    private boolean sky;

    public SolarArrayBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.SOLAR_ARRAY).get(), pos, state, 0);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.SOLAR_ARRAY.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of();
    }

    @Override
    protected Direction[] outputSides() {
        return OUTPUT_SIDES;
    }

    /** Percent of full output under the current sky at {@code pos}. */
    public static int sunPercent(ServerLevel level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight() || !SkyAccess.canSeeSky(level, pos.above())) return 0;
        boolean airless = PowerRules.isAirless(level);
        if (!airless && level.isDarkOutside()) return 0;
        if (airless) return 150;
        return level.isRaining() ? 50 : 100;
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            sky = level.dimensionType().hasSkyLight() && SkyAccess.canSeeSky(level, worldPosition.above());
            sunPercent = sunPercent(level, worldPosition);
        }
        float out = (float) (PowerConfig.get(PowerConfig.SOLAR_ARRAY_OUTPUT) * sunPercent / 100.0 * PowerConfig.generatorMultiplier());
        if (out > 0) generate(out);
        else lastRate = 0;
        status = !sky ? ST_NO_SKY : sunPercent <= 0 ? ST_NIGHT : energy.space() <= 0 ? ST_FULL : ST_RUNNING;
        return sunPercent > 0;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = sunPercent;
    }
}

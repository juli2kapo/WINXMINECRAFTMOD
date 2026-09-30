package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Electric age: a nacelle with a 5-block three-bladed rotor, standing on a mast of at least
 * {@link #MIN_MAST} Turbine Masts. Unlike the stone-age Windmill it makes FE: {@code windTurbineOutput}
 * at 96+ blocks above sea level (15% at sea level, scaling in between), x1.3 in rain and x1.8 in
 * thunderstorms. The 5x5 disc its blades sweep in front of it must be clear. The mast carries the
 * power down: it comes out into the cable or machine under the foot of the mast.
 */
public class WindTurbineBlockEntity extends PowerBlockEntity {
    public static final int MIN_MAST = 4, MAX_MAST = 32;
    private int masts;
    private boolean clear;
    private float output;
    private int height;
    private BlockCapabilityCache<EnergyHandler, Direction> footCache;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.WIND_TURBINE).get(), pos, state, 0);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.WIND_TURBINE.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of();
    }

    /** The mast carries the power down: it comes out into whatever sits under the foot of the mast. */
    @Override
    protected Direction[] outputSides() {
        return new Direction[0];
    }

    private void pushDownTheMast(ServerLevel level) {
        if (masts < MIN_MAST || energy.energy() <= 0) return;
        BlockPos foot = worldPosition.below(masts + 1);
        if (footCache == null || !footCache.pos().equals(foot)) {
            footCache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, foot, Direction.UP);
        }
        EnergyHandler target = footCache.getCapability();
        if (target == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            int moved = target.insert(Math.min(energy.energy(), maxOutput() * 2), tx);
            tx.commit();
            if (moved > 0) energy.consume(moved);
        }
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        if (level.getGameTime() % 40 == 0 || masts == 0 && level.getGameTime() % 10 == 0) {
            masts = countMasts(level, worldPosition);
            clear = rotorClear(level);
            height = worldPosition.getY() - level.getSeaLevel();
            output = masts < MIN_MAST || !clear ? 0 : windOutput(level);
        }
        if (output > 0) generate(output);
        else lastRate = 0;
        pushDownTheMast(level);
        if (masts < MIN_MAST) status = ST_NO_MAST;
        else if (!clear) status = ST_BLOCKED;
        else if (airless(level)) status = ST_NO_AIR;
        else if (output <= 0) status = ST_IDLE;
        else status = energy.space() <= 0 ? ST_FULL : ST_RUNNING;
        return output > 0;
    }

    /** Turbine Mast blocks stacked straight under a turbine at {@code pos}. */
    public static int countMasts(ServerLevel level, BlockPos pos) {
        int n = 0;
        BlockPos p = pos.below();
        while (n < MAX_MAST && level.getBlockState(p).is(PowerContent.TURBINE_MAST.get())) {
            n++;
            p = p.below();
        }
        return n;
    }

    private boolean rotorClear(ServerLevel level) {
        Direction front = getBlockState().getValue(PowerBlock.FACING);
        BlockPos hub = worldPosition.relative(front);
        Direction side = front.getClockWise();
        for (int dy = -2; dy <= 2; dy++) {
            for (int ds = -2; ds <= 2; ds++) {
                if (Math.abs(dy) == 2 && Math.abs(ds) == 2) continue; // the blade tips sweep a circle
                BlockPos p = hub.above(dy).relative(side, ds);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
            }
        }
        return true;
    }

    private float windOutput(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight() || airless(level)) return 0;
        float h = Math.max(0.15f, Math.min(1f, (worldPosition.getY() - level.getSeaLevel()) / 96f));
        float weather = level.isThundering() ? 1.8f : level.isRaining() ? 1.3f : 1f;
        return (float) (PowerConfig.get(PowerConfig.WIND_TURBINE_OUTPUT) * h * weather * PowerConfig.generatorMultiplier());
    }

    public float output() {
        return output;
    }

    @Override
    protected void writeExtra(int[] extra) {
        int max = Math.max(1, maxOutput());
        extra[0] = Math.round(output * 1000 / max);
        extra[1] = masts;
        extra[2] = height;
        extra[3] = clear ? 1 : 0;
    }
}

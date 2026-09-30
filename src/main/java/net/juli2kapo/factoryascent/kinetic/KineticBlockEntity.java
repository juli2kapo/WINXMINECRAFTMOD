package net.juli2kapo.factoryascent.kinetic;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Water Wheel and Windmill: stone-age mechanical power. Neither makes FE; instead they turn the
 * handle of every hand-cranked machine (Quern, Sieve) touching them, slowly and for free. The
 * rotation is split between the machines they drive.
 *
 * <p>Water Wheel: 0.2 work points per tick for each still water block touching its sides or
 * bottom, twice that for flowing water, up to {@link #WATER_MAX}. Windmill: faster the higher
 * it stands (from sea level to y 140) and in rain or storms, but its sails need the 3×3 of air
 * in front of it to turn at all. A player cranking by hand earns up to 2 points per tick.
 */
public class KineticBlockEntity extends AbstractMachineBlockEntity {
    public static final float WATER_MAX = 0.8f;
    public static final float WIND_MIN = 0.15f, WIND_MAX = 0.6f;
    private static final int CHECK_INTERVAL = 20;

    private float output;
    private int driven;
    /** Client only: the rotor's angle and speed (degrees, degrees per tick) for {@code KineticRenderer}. */
    public float rotorAngle;
    public float rotorSpeed;
    public float rotorLastTime = -1;
    private int checkCooldown;

    public KineticBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(0, 0, 0);
    }

    @Override
    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        return null;
    }

    private Direction facing() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    /** Work points per tick this wheel or windmill makes right now. */
    public float output() {
        return output;
    }

    public int driven() {
        return driven;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (--checkCooldown <= 0) {
            checkCooldown = CHECK_INTERVAL;
            output = (float) ((type == MachineType.WATER_WHEEL ? waterOutput(level) : windOutput(level)) * Config.MACHINE_SPEED.get());
        }
        if (output <= 0) {
            driven = 0;
            status = STATUS_IDLE;
            return false;
        }
        List<ProcessingMachineBlockEntity> targets = new ArrayList<>(2);
        List<net.juli2kapo.factoryascent.power.KineticSink> sinks = new ArrayList<>(1);
        for (Direction dir : Direction.values()) {
            var be = level.getBlockEntity(worldPosition.relative(dir));
            if (be instanceof ProcessingMachineBlockEntity machine && machine.type().power() == MachineType.Power.MANUAL) {
                targets.add(machine);
            } else if (be instanceof net.juli2kapo.factoryascent.power.KineticSink sink) {
                sinks.add(sink); // the Kinetic Dynamo (power ladder)
            }
        }
        driven = targets.size() + sinks.size();
        for (ProcessingMachineBlockEntity machine : targets) machine.driveKinetic(output / driven);
        for (var sink : sinks) sink.driveKinetic(output / driven);
        status = driven > 0 ? STATUS_WORKING : STATUS_IDLE;
        return true;
    }

    private float waterOutput(ServerLevel level) {
        float points = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            FluidState fluid = level.getFluidState(worldPosition.relative(dir));
            if (fluid.is(FluidTags.WATER)) points += fluid.isSource() ? 0.2f : 0.4f;
        }
        return Math.min(WATER_MAX, points);
    }

    private float windOutput(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) return 0;
        // The sails sweep the 3×3 in front of the tower: anything solid there jams them.
        Direction front = facing();
        BlockPos hub = worldPosition.relative(front);
        Direction side = front.getClockWise();
        for (int dy = -1; dy <= 1; dy++) {
            for (int ds = -1; ds <= 1; ds++) {
                BlockPos p = hub.above(dy).relative(side, ds);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return 0;
            }
        }
        float height = Math.max(0f, Math.min(1f, (worldPosition.getY() - level.getSeaLevel()) / 80f));
        float wind = WIND_MIN + (WIND_MAX - WIND_MIN) * height;
        if (level.isThundering()) wind *= 1.5f;
        else if (level.isRaining()) wind *= 1.25f;
        return Math.min(WIND_MAX * 1.5f, wind);
    }

    /** The current output as a percentage ×10 of a player cranking steadily (1 point per tick). */
    @Override
    public int extraA() {
        return Math.round(output * 1000);
    }

    /** How many hand-cranked machines it drives. */
    @Override
    public int extraB() {
        return driven;
    }

    /** For the screen's turning wheel: the rotor angle in degrees. */
    @Override
    public int progressPermille() {
        return level == null ? 0 : (int) ((level.getGameTime() * output * 12) % 360);
    }
}

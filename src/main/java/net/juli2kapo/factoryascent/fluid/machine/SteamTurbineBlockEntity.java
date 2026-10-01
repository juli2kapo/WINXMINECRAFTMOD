package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Industrial age: a big steam turbine for a fission reactor's Coolant Port (or a bank of
 * Boilers). The rotor spins up with the steam it gets (a few seconds) and then turns up to
 * {@code turbineMaxSteam} mB/t of steam into {@code steamFePerMb} FE each.
 */
public class SteamTurbineBlockEntity extends FluidMachineBlockEntity {
    final FluidTank steam;
    /** 0..1 rotor speed. */
    private float speed;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.STEAM_TURBINE, pos, state);
        steam = addTank(new FluidTank(64_000, r -> r.getFluid().isSame(ModFluids.STEAM.source()), this::setChanged).input());
        finishTanks();
        int peak = maxSteam() * FluidsConfig.get(FluidsConfig.STEAM_FE_PER_MB);
        energy.configure(Math.max(100_000, peak * 50), 0, peak * 2);
    }

    public static int maxSteam() {
        return FluidsConfig.get(FluidsConfig.TURBINE_MAX_STEAM);
    }

    public FluidTank steam() {
        return steam;
    }

    public float speed() {
        return speed;
    }

    @Override
    protected boolean tick(ServerLevel level) {
        int fePerMb = FluidsConfig.get(FluidsConfig.STEAM_FE_PER_MB);
        int want = Math.min(steam.amount(), maxSteam());
        // the rotor follows the steam flow; it only makes what its speed allows
        float target = maxSteam() <= 0 ? 0 : (float) want / maxSteam();
        speed += (target - speed) * (target > speed ? 0.02f : 0.05f);
        if (speed < 0.001f) speed = 0;
        int use = Math.min(want, Math.max(want > 0 ? 1 : 0, Math.round(maxSteam() * speed)));
        float mult = (float) PowerConfig.generatorMultiplier();
        int room = energy.space();
        use = Math.min(use, (int) (room / Math.max(1f, fePerMb * mult)));
        if (use > 0) {
            steam.drain(use);
            lastRate = energy.produce(Math.round(use * fePerMb * mult));
        } else {
            lastRate = 0;
        }
        progress = Math.round(speed * 1000);
        if (lastRate > 0) status = speed < 0.9f * target ? ST_SPINNING_UP : ST_RUNNING;
        else if (energy.space() <= 0) status = ST_FULL;
        else status = ST_NO_INPUT;
        setChanged();
        return lastRate > 0;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = Math.round(speed * 1000);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        speed = input.getFloatOr("speed", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", speed);
    }
}

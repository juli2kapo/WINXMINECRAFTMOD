package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidContainers;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.power.PowerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Electric age: a fire-tube boiler with no engine of its own. Fuel heats it (about 16 s to
 * boiling); from 100 C on it boils water (piped in, buckets, or water source blocks touching it)
 * into steam, up to {@code boilerSteam} mB/t at full pressure (180 C), 1 mB of water to 1 mB of
 * steam. Steam goes out to pipes, Steam Engines and Steam Turbines next to it. Nothing burns
 * without air.
 */
public class BoilerBlockEntity extends FluidMachineBlockEntity {
    public static final int FUEL = 0, BUCKET_IN = 1, BUCKET_OUT = 2;
    public static final float AMBIENT = 20f, BOILING = 100f, FULL = 180f, MAX = 200f;
    private static final float HEAT = 0.4f, LOSS = 0.15f;

    final FluidTank water;
    final FluidTank steam;
    private float temperature = AMBIENT, burnLeft, burnTotal, pressure;
    private int sources;
    /** GameTests: pretend there is (or isn't) air here. */
    public Boolean testAirless;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.BOILER, pos, state);
        water = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(Fluids.WATER), this::setChanged).input());
        steam = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(ModFluids.STEAM.source()), this::setChanged).output());
        finishTanks();
    }

    public FluidTank water() {
        return water;
    }

    public FluidTank steam() {
        return steam;
    }

    public float temperature() {
        return temperature;
    }

    public void setTemperature(float t) {
        temperature = t;
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        if (slot == FUEL) return level != null && stack.getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0 && FluidContainers.drain(stack) == null;
        if (slot == BUCKET_IN) return stack.is(Items.WATER_BUCKET);
        return false;
    }

    @Override
    protected boolean tick(ServerLevel level) {
        boolean airless = testAirless != null ? testAirless : PowerRules.isAirless(level);
        if (level.getGameTime() % 20 == 0) {
            int n = 0;
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                FluidState f = level.getFluidState(worldPosition.relative(d));
                if (f.is(FluidTags.WATER) && f.isSource()) n++;
            }
            sources = n;
        }
        if (sources > 0) water.forceFill(Fluids.WATER, sources * 25);
        FluidContainers.process(water, inventory.stack(BUCKET_IN), inventory.stack(BUCKET_OUT),
                s -> inventory.setStack(BUCKET_OUT, s), inventory::changed, true, false);
        if (burnLeft <= 0 && !airless && temperature < MAX - 1 && steam.space() > 0 && !water.isEmpty()) {
            ItemStack fuel = inventory.stack(FUEL);
            int burn = fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
            if (burn > 0) {
                var remainder = fuel.getItem().getCraftingRemainder(fuel);
                fuel.shrink(1);
                if (fuel.isEmpty() && remainder != null) inventory.setStack(FUEL, remainder.create());
                else inventory.changed();
                burnTotal = (float) (burn * FluidsConfig.get(FluidsConfig.BOILER_BURN_TIME));
                burnLeft = burnTotal;
            }
        }
        boolean burning = burnLeft > 0 && !airless;
        if (airless) burnLeft = 0;
        if (burning) {
            burnLeft--;
            temperature += HEAT;
        }
        temperature -= LOSS * (temperature - AMBIENT) / (MAX - AMBIENT) + (burning ? 0 : 0.05f);
        pressure = water.isEmpty() ? 0f : Math.max(0f, Math.min(1f, (temperature - BOILING) / (FULL - BOILING)));
        int boil = Math.min(Math.round(FluidsConfig.get(FluidsConfig.BOILER_STEAM) * pressure), Math.min(water.amount(), steam.space()));
        if (boil > 0) {
            water.drain(boil);
            steam.forceFill(ModFluids.STEAM.source(), boil);
            temperature -= 0.1f * pressure;
        }
        lastRate = boil;
        temperature = Math.max(AMBIENT, Math.min(MAX, temperature));
        progress = burnTotal <= 0 ? 0 : Math.round(burnLeft / burnTotal * 1000);
        if (airless) status = ST_NO_AIR;
        else if (boil > 0) status = ST_RUNNING;
        else if (steam.space() <= 0) status = ST_FULL;
        else if (water.isEmpty()) status = ST_NO_WATER;
        else if (burning) status = ST_HEATING;
        else status = ST_NO_FUEL;
        setChanged();
        return boil > 0 || burning;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = Math.round(temperature * 10);
        extra[1] = Math.round(pressure * 100);
        extra[2] = sources;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        temperature = input.getFloatOr("temperature", AMBIENT);
        burnLeft = input.getFloatOr("burn_left", 0f);
        burnTotal = input.getFloatOr("burn_total", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("temperature", temperature);
        output.putFloat("burn_left", burnLeft);
        output.putFloat("burn_total", burnTotal);
    }
}

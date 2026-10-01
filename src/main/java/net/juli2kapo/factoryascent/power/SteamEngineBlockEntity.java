package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Electric age: a fire-tube boiler, a piston and a flywheel. Fuel heats the boiler (about 16 s
 * to boiling, 35 s to full pressure); from 100 C on, steam turns the flywheel and output climbs
 * with the pressure to {@code steamEngineOutput} FE/t at 180 C. Every 50 FE of steam boils 1 mB
 * of water: water buckets in its slot, or water source blocks touching it (25 mB/t each).
 * Nothing burns without air.
 *
 * <p>Fluids: pipes can feed it water, and steam from a separate Boiler: with steam in its steam
 * chest the engine runs on that (no fire needed), {@code steamFePerMb} FE per mB of steam, up to
 * its rated output.
 */
public class SteamEngineBlockEntity extends PowerBlockEntity {
    public static final int FUEL = 0, WATER_IN = 1, BUCKET_OUT = 2;
    public static final int WATER_CAPACITY = 8000, STEAM_CAPACITY = 8000;
    public static final float AMBIENT = 20f, BOILING = 100f, FULL_PRESSURE = 180f, MAX_TEMP = 200f;
    private static final float HEAT = 0.4f, LOSS = 0.15f, STEAM_DRAW = 0.1f;

    private float temperature = AMBIENT;
    private float burnLeft;
    private float burnTotal;
    private float water;
    private int waterSources;
    private float pressure;
    private float steam;
    private boolean onSteam;
    private final net.juli2kapo.factoryascent.fluid.FloatTanks fluids = new net.juli2kapo.factoryascent.fluid.FloatTanks(this::setChanged,
            new net.juli2kapo.factoryascent.fluid.FloatTanks.Tank(() -> net.minecraft.world.level.material.Fluids.WATER,
                    f -> f.isSame(net.minecraft.world.level.material.Fluids.WATER) ? 1 : 0, () -> water, v -> water = v, () -> WATER_CAPACITY, true, false),
            new net.juli2kapo.factoryascent.fluid.FloatTanks.Tank(net.juli2kapo.factoryascent.fluid.ModFluids.STEAM::source,
                    f -> f.isSame(net.juli2kapo.factoryascent.fluid.ModFluids.STEAM.source()) ? 1 : 0, () -> steam, v -> steam = v,
                    () -> STEAM_CAPACITY, true, false));

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.STEAM_ENGINE).get(), pos, state, 3);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.STEAM_ENGINE.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of(new SlotSpec(FUEL, 44, 53, false), new SlotSpec(WATER_IN, 116, 17, false),
                new SlotSpec(BUCKET_OUT, 116, 53, true));
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        if (slot == FUEL) return level != null && resource.toStack(1).getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0
                && !resource.is(Items.WATER_BUCKET);
        return slot == WATER_IN && resource.is(Items.WATER_BUCKET);
    }

    @Override
    protected Direction[] outputSides() {
        return new Direction[] {Direction.DOWN, Direction.UP, getBlockState().getValue(PowerBlock.FACING).getOpposite(),
                getBlockState().getValue(PowerBlock.FACING).getClockWise(), getBlockState().getValue(PowerBlock.FACING).getCounterClockWise()};
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        boolean airless = airless(level);
        if (level.getGameTime() % 20 == 0) {
            int n = 0;
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP) continue;
                FluidState fluid = level.getFluidState(worldPosition.relative(dir));
                if (fluid.is(FluidTags.WATER) && fluid.isSource()) n++;
            }
            waterSources = n;
        }
        water = Math.min(WATER_CAPACITY, water + waterSources * 25f);
        ItemStack bucket = inventory.stack(WATER_IN);
        if (bucket.is(Items.WATER_BUCKET) && water <= WATER_CAPACITY - 1000
                && inventory.canAddOutput(BUCKET_OUT, new ItemStack(Items.BUCKET))) {
            bucket.shrink(1);
            inventory.changed();
            inventory.addOutput(BUCKET_OUT, new ItemStack(Items.BUCKET));
            water += 1000;
        }
        float out = (float) (PowerConfig.get(PowerConfig.STEAM_ENGINE_OUTPUT) * PowerConfig.generatorMultiplier());
        // Steam piped in from a Boiler: the piston runs on it directly, no fire needed.
        int fePerMb = net.juli2kapo.factoryascent.fluid.FluidsConfig.get(net.juli2kapo.factoryascent.fluid.FluidsConfig.STEAM_FE_PER_MB);
        float fromSteam = Math.min(out, steam * fePerMb);
        onSteam = fromSteam >= 1f && energy.space() > 0;
        if (onSteam) {
            int made = generate(fromSteam);
            steam = Math.max(0f, steam - made / (float) fePerMb);
            status = ST_RUNNING;
            temperature = Math.max(AMBIENT, temperature - 0.05f);
            setChanged();
            return true;
        }
        if (burnLeft <= 0 && !airless && temperature < MAX_TEMP - 1 && energy.space() > 0 && water > 0) {
            ItemStack fuel = inventory.stack(FUEL);
            int burn = fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
            if (burn > 0) {
                var remainder = fuel.getItem().getCraftingRemainder(fuel);
                fuel.shrink(1);
                if (fuel.isEmpty() && remainder != null) inventory.setStack(FUEL, remainder.create());
                else inventory.changed();
                burnTotal = (float) (burn * PowerConfig.get(PowerConfig.STEAM_ENGINE_BURN_TIME));
                burnLeft = burnTotal;
            }
        }
        boolean burning = burnLeft > 0 && !airless;
        if (airless) burnLeft = 0;
        if (burning) {
            burnLeft--;
            temperature += HEAT;
        }
        temperature -= LOSS * (temperature - AMBIENT) / (MAX_TEMP - AMBIENT) + (burning ? 0 : 0.05f);
        pressure = water > 0 ? Math.max(0f, Math.min(1f, (temperature - BOILING) / (FULL_PRESSURE - BOILING))) : 0f;
        float produce = out * pressure;
        if (produce > 0 && energy.space() > 0) {
            generate(produce);
            water = Math.max(0f, water - produce / 50f);
            temperature -= STEAM_DRAW * pressure;
        } else {
            lastRate = 0;
        }
        temperature = Math.max(AMBIENT, Math.min(MAX_TEMP, temperature));
        if (airless) status = ST_NO_AIR;
        else if (lastRate > 0) status = ST_RUNNING;
        else if (energy.space() <= 0) status = ST_FULL;
        else if (water <= 0) status = ST_NO_WATER;
        else if (burning) status = ST_HEATING;
        else status = ST_NO_FUEL;
        setChanged();
        return lastRate > 0 || burning;
    }

    public float temperature() {
        return temperature;
    }

    public float water() {
        return water;
    }

    public float pressure() {
        return pressure;
    }

    /** GameTests: skip the warm-up. */
    public void setTemperature(float t) {
        temperature = t;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = Math.round(temperature * 10);
        extra[1] = Math.round(water);
        extra[2] = WATER_CAPACITY;
        extra[3] = burnTotal <= 0 ? 0 : Math.round(burnLeft / burnTotal * 1000);
        extra[4] = Math.round(pressure * 100);
        extra[5] = waterSources;
        extra[6] = Math.round(steam);
        extra[7] = STEAM_CAPACITY;
    }

    public float steam() {
        return steam;
    }

    public boolean onSteam() {
        return onSteam;
    }

    /** What pipes see: a water inlet and a steam inlet. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidHandler() {
        return fluids;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        temperature = input.getFloatOr("temperature", AMBIENT);
        burnLeft = input.getFloatOr("burn_left", 0f);
        burnTotal = input.getFloatOr("burn_total", 0f);
        water = input.getFloatOr("water", 0f);
        steam = input.getFloatOr("steam", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("temperature", temperature);
        output.putFloat("burn_left", burnLeft);
        output.putFloat("burn_total", burnTotal);
        output.putFloat("water", water);
        output.putFloat("steam", steam);
    }
}

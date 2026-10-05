package net.juli2kapo.factoryascent.trains;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Bronze age tank engine. Its firebox burns furnace fuel from the four bunker slots (hoppers can
 * fill them) to boil water from its 10,000 mB tank (buckets, pipes through a Train Station). Steam
 * pressure builds while the fire burns and the regulator is open; pulling uses fuel and water in
 * proportion to the throttle. Smoke from the chimney, steam from the cylinders on every chuff,
 * and a whistle (H).
 *
 * <p>Gauges: A = pressure (‰), B = water (mB), C = fire left (‰ of the current fuel item).
 */
public class SteamLocomotive extends Locomotive implements Container {
    public static final int WATER_CAPACITY = 10_000;
    public static final int FUEL_SLOTS = 4;
    private static final Vec3 CAB = new Vec3(0, 6 / 16.0, -12 / 16.0);

    private final FluidTank water = new FluidTank(WATER_CAPACITY, FluidTank.only(Fluids.WATER), () -> {});
    private double burnLeft, burnTotal = 1;
    private double pressure;
    private double waterDebt;

    public SteamLocomotive(EntityType<? extends SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public double length() {
        return 46 / 16.0;
    }

    @Override
    public double mass() {
        return 4.0;
    }

    @Override
    public int inventorySize() {
        return FUEL_SLOTS;
    }

    @Override
    protected Vec3 cab() {
        return CAB;
    }

    @Override
    public float wheelRadius() {
        return 5f / 16f;
    }

    @Override
    public double topSpeed() {
        return TrainConfig.steamSpeed();
    }

    @Override
    public double power() {
        return 0.02;
    }

    @Override
    public double traction() {
        return water.amount() > 0 ? pressure : 0;
    }

    @Override
    public boolean fuelled() {
        if (water.amount() <= 0) return false;
        if (burnLeft > 0 || pressure > 0.05) return true;
        for (int i = 0; i < FUEL_SLOTS; i++) if (burnTime(items.get(i)) > 0) return true;
        return false;
    }

    public FluidTank water() {
        return water;
    }

    public double pressure() {
        return pressure;
    }

    public void setPressure(double p) {
        pressure = Mth.clamp(p, 0, 1);
    }

    @Override
    public void burn(double effort) {
        if (TrainConfig.steamFuelUse() > 0) burnLeft -= effort;
        waterDebt += effort * TrainConfig.steamWaterPerTick();
        if (waterDebt >= 1) {
            int use = (int) waterDebt;
            water.drain(use);
            waterDebt -= use;
        }
    }

    private int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING, level().fuelValues());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) && burnTime(stack) > 0;
    }

    /** The fire takes a new fuel item when the old one is spent and the regulator is open. */
    private boolean refuel() {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            ItemStack stack = items.get(i);
            int burn = burnTime(stack);
            if (burn <= 0) continue;
            var remainder = stack.getItem().getCraftingRemainder(stack);
            stack.shrink(1);
            if (stack.isEmpty() && remainder != null) items.set(i, remainder.create());
            double use = Math.max(1e-6, TrainConfig.steamFuelUse());
            burnTotal = burn / use;
            burnLeft += burnTotal;
            return true;
        }
        return false;
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        boolean working = Math.abs(throttle()) > 0.001;
        if (working && burnLeft <= 0 && water.amount() > 0) refuel();
        boolean fire = burnLeft > 0 && water.amount() > 0;
        if (fire && working) pressure = Math.min(1, pressure + 0.012);
        else pressure = Math.max(0, pressure - (fire ? 0.0005 : 0.003));
        setGauges((int) Math.round(pressure * 1000), water.amount(), burnLeft <= 0 ? 0 : (int) Math.round(Math.min(1, burnLeft / burnTotal) * 1000));
    }

    @Override
    protected Component noFuelMessage() {
        return Component.translatable(water.amount() <= 0 ? "message.factoryascent.train.no_water" : "message.factoryascent.train.no_coal");
    }

    @Override
    protected Holder<SoundEvent> hornSound() {
        return SoundEvents.NOTE_BLOCK_FLUTE;
    }

    @Override
    protected void blowHorn() {
        // a steam whistle: two flute notes a third apart, loud
        level().playSound(null, getX(), getY() + 2.5, getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 4f, 1.19f);
        level().playSound(null, getX(), getY() + 2.5, getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 4f, 1.5f);
        level().playSound(null, getX(), getY() + 2.5, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6f, 1.8f);
    }

    // ---------------------------------------------------------------- client effects

    private float lastChuff;

    @Override
    protected void clientTick() {
        Vec3 f = front();
        Vec3 side = new Vec3(f.z, 0, -f.x);
        float speed = Math.abs(syncedSpeed());
        boolean working = Math.abs(throttle()) > 0.01 && gaugeA() > 50;
        // chimney smoke: heavy when pulling, a wisp when standing in steam
        Vec3 chimney = position().add(f.scale(17 / 16.0)).add(0, 33 / 16.0, 0);
        if (gaugeC() > 0 || working) {
            int every = working ? 2 : 6;
            if (tickCount % every == 0) {
                level().addParticle(working ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, chimney.x + (random.nextDouble() - 0.5) * 0.15,
                        chimney.y, chimney.z + (random.nextDouble() - 0.5) * 0.15, -f.x * speed * 0.5, 0.06 + 0.04 * Math.abs(throttle()), -f.z * speed * 0.5);
            }
            if (working && tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, chimney.x, chimney.y + 0.2, chimney.z, -f.x * speed * 0.3, 0.05, -f.z * speed * 0.3);
            }
        }
        // chuff: four exhaust beats per wheel turn, a steam puff from the cylinders and a soft hiss
        float turns = wheel / (Mth.TWO_PI) * 4f;
        if (working && Math.floor(turns) != Math.floor(lastChuff)) {
            Vec3 cyl = position().add(f.scale(13 / 16.0)).add(0, 3 / 16.0, 0);
            for (int s : new int[] {1, -1}) {
                Vec3 p = cyl.add(side.scale(s * 8 / 16.0));
                level().addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, side.x * s * 0.06, 0.02, side.z * s * 0.06);
            }
            level().addParticle(ParticleTypes.LARGE_SMOKE, chimney.x, chimney.y, chimney.z, 0, 0.18, 0);
            level().playLocalSound(getX(), getY() + 1, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL,
                    0.12f + 0.1f * Math.abs((float) throttle()), 1.7f + random.nextFloat() * 0.3f, false);
        }
        lastChuff = turns;
        // safety valve / whistle steam
        if (syncedInput() != 0 && random.nextInt(3) == 0 && speed < 0.02) {
            level().addParticle(ParticleTypes.CLOUD, getX() + f.x * 0.2, getY() + 30 / 16.0, getZ() + f.z * 0.2, 0, 0.1, 0);
        }
    }

    // ---------------------------------------------------------------- water by bucket

    @Override
    protected InteractionResult interactWith(Player player, InteractionHand hand, ItemStack held) {
        if (FluidUtil.getFirstStackContained(held).is(Fluids.WATER) || held.is(net.minecraft.world.item.Items.BUCKET)) {
            if (!level().isClientSide()) FluidUtil.interactWithFluidHandler(player, hand, blockPosition(), water, null);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ---------------------------------------------------------------- item form and save

    @Override
    protected void clearCargo() {
        super.clearCargo();
        water.setContents(Fluids.EMPTY, 0);
    }

    @Override
    public void writeToItem(ItemStack stack) {
        super.writeToItem(stack);
        if (!water.isEmpty()) stack.set(FluidContent.TANK_CONTENTS.get(), SimpleFluidContent.copyOf(water.stack()));
    }

    @Override
    public void readFromItem(ItemStack stack) {
        super.readFromItem(stack);
        SimpleFluidContent c = stack.get(FluidContent.TANK_CONTENTS.get());
        if (c != null && !c.isEmpty()) {
            FluidStack fs = c.copy();
            water.setContents(fs.getFluid(), fs.getAmount());
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        water.save(output, "Water");
        output.putDouble("Burn", burnLeft);
        output.putDouble("BurnTotal", burnTotal);
        output.putDouble("Pressure", pressure);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        water.load(input, "Water");
        burnLeft = input.getDoubleOr("Burn", 0);
        burnTotal = Math.max(1, input.getDoubleOr("BurnTotal", 1));
        pressure = input.getDoubleOr("Pressure", 0);
    }
}

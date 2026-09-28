package net.juli2kapo.factoryascent.ships;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Electric age motor ship: helm (in the wheelhouse) + three passengers, a 54-slot hold, a
 * searchlight (J), a horn (H), and an electric motor fed by a 200k FE battery. The battery charges
 * from its power slot, which takes furnace fuel (burnt by an on-board generator at the Combustion
 * Generator's 40 FE per burn tick) or any FE item (drained), and from anything that pushes FE into
 * the ship ({@link Capabilities.Energy#ENTITY}). Full ahead uses {@link ShipConfig#MOTOR_FE_PER_TICK}.
 */
public class MotorShip extends SeaShip {
    public static final int CAPACITY = 200_000;
    public static final double BASE_SPEED = 0.5;
    private static final double DRAG = 0.028;
    private static final int POWER_SLOT = 54;
    private static final Vec3[] SEATS = {new Vec3(0, 15 / 16.0, -23 / 16.0), new Vec3(8.5 / 16, 15 / 16.0, -6 / 16.0),
            new Vec3(-8.5 / 16, 15 / 16.0, -6 / 16.0), new Vec3(0, 15 / 16.0, 44 / 16.0)};

    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, 5000, 5000);
    private boolean noPowerWarned;

    public MotorShip(EntityType<? extends MotorShip> type, Level level) {
        super(type, level);
    }

    @Override
    public int cargoSize() {
        return 54;
    }

    @Override
    public int fuelSlot() {
        return POWER_SLOT;
    }

    @Override
    protected Vec3[] seats() {
        return SEATS;
    }

    @Override
    public int fuel() {
        return battery.getAmountAsInt();
    }

    @Override
    public int fuelCapacity() {
        return CAPACITY;
    }

    @Override
    protected void setFuel(int value) {
        battery.set(Math.max(0, Math.min(CAPACITY, value)));
    }

    public EnergyHandler energyHandler() {
        return battery;
    }

    @Override
    public boolean acceptsFuel(ItemStack stack) {
        return burnTime(stack) > 0 || ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Energy.ITEM) != null;
    }

    private int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING, level().fuelValues());
    }

    @Override
    protected double draft() {
        return 0.3;
    }

    @Override
    protected double hullLength() {
        return 8.0;
    }

    @Override
    protected double drag() {
        return DRAG;
    }

    @Override
    protected float turnAcceleration() {
        return 0.6f;
    }

    @Override
    protected float maxTurnRate() {
        return 2.8f;
    }

    @Override
    protected double cruiseSpeed() {
        return BASE_SPEED;
    }

    /** -1 astern, 0 stop, 1 ahead. */
    private int throttle() {
        return pressed(IN_FORWARD) ? 1 : pressed(IN_BACK) ? -1 : 0;
    }

    @Override
    protected double thrust() {
        int throttle = throttle();
        if (throttle == 0) return 0;
        int need = ShipMath.motorEnergyPerTick(throttle, ShipConfig.motorFePerTick());
        if (battery.getAmountAsInt() < need) {
            if (!noPowerWarned && pilot() != null) {
                pilot().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.factoryascent.ship.no_power")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                noPowerWarned = true;
            }
            return 0;
        }
        noPowerWarned = false;
        battery.set(battery.getAmountAsInt() - need);
        double top = BASE_SPEED * ShipConfig.seaSpeed();
        return throttle > 0 ? top * DRAG : -top * 0.4 * DRAG;
    }

    @Override
    protected void afterMove() {
        refill();
    }

    /** Tops the battery up from the power slot. */
    private void refill() {
        ItemStack stack = items.get(POWER_SLOT);
        if (stack.isEmpty()) return;
        int space = CAPACITY - battery.getAmountAsInt();
        int burn = burnTime(stack);
        if (burn > 0) {
            int energy = ShipMath.energyFromBurnTime(burn);
            if (space >= energy || battery.getAmountAsInt() < CAPACITY / 4) {
                var remainder = stack.getItem().getCraftingRemainder(stack);
                stack.shrink(1);
                if (stack.isEmpty() && remainder != null) items.set(POWER_SLOT, remainder.create());
                battery.set(Math.min(CAPACITY, battery.getAmountAsInt() + energy));
            }
            return;
        }
        if (space <= 0) return;
        EnergyHandler source = ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(this), POWER_SLOT).getCapability(Capabilities.Energy.ITEM);
        if (source == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            int got = source.extract(Math.min(space, 2000), tx);
            tx.commit();
            if (got > 0) battery.set(battery.getAmountAsInt() + got);
        }
    }

    @Override
    protected @Nullable Holder<SoundEvent> hornSound() {
        return SoundEvents.RAID_HORN;
    }

    @Override
    protected void animate() {
        int in = syncedInput();
        float target = syncedFuel() <= 0 ? 0 : (in & IN_FORWARD) != 0 ? 1f : (in & IN_BACK) != 0 ? -0.6f : 0f;
        thrust += (target - thrust) * 0.08f;
        spin += thrust * 48f;
    }

    @Override
    protected void clientEffects() {
        super.clientEffects();
        // funnel smoke while the engine runs, propeller wash behind the stern
        int in = syncedInput();
        boolean running = (in & (IN_FORWARD | IN_BACK)) != 0 && syncedFuel() > 0;
        Vec3 fwd = forward();
        if (tickCount % (running ? 2 : 8) == 0) {
            Vec3 p = position().add(fwd.scale(-44 / 16.0));
            level().addParticle(running ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x + (random.nextDouble() - 0.5) * 0.4,
                    getY() + 46 / 16.0, p.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.08, 0);
        }
        if (running && afloat()) {
            Vec3 p = position().add(fwd.scale(-4.2));
            for (int i = 0; i < 2; i++) {
                level().addParticle(ParticleTypes.BUBBLE, p.x + (random.nextDouble() - 0.5) * 0.6, getY() + 0.1,
                        p.z + (random.nextDouble() - 0.5) * 0.6, -fwd.x * 0.2, 0.05, -fwd.z * 0.2);
                level().addParticle(ParticleTypes.SPLASH, p.x + (random.nextDouble() - 0.5), getY() + draft() + 0.1,
                        p.z + (random.nextDouble() - 0.5), 0, 0.1, 0);
            }
        }
    }
}

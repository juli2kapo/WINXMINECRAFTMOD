package net.juli2kapo.factoryascent.trains;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Automation age diesel-electric: traction motors run from a 200k FE battery, which an on-board
 * diesel generator keeps topped up from a 16,000 mB diesel tank. Charge it with FE (Train Station,
 * anything that pushes FE into it, or a charged item in its power slot) or fill it with diesel
 * (buckets/cells, pipes through a Train Station). Faster than steam, with a headlight (J) and a
 * horn (H).
 *
 * <p>Gauges: A = battery (FE), B = diesel (mB), C = generator running (0/1).
 */
public class DieselLocomotive extends Locomotive implements Container {
    public static final int BATTERY = 200_000;
    public static final int TANK = 16_000;
    /** FE per tick the generator makes while it runs. */
    public static final int GENERATOR = 300;
    private static final Vec3 CAB = new Vec3(0, 9 / 16.0, 14 / 16.0);

    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(BATTERY, 10_000, 10_000);
    private final FluidTank diesel = new FluidTank(TANK, FluidTank.only(ModFluids.DIESEL.source()), () -> {});
    private double feDebt;
    private int dieselDebt;
    private boolean generating;

    public DieselLocomotive(EntityType<? extends DieselLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public double length() {
        return 52 / 16.0;
    }

    @Override
    public double mass() {
        return 5.0;
    }

    @Override
    public int inventorySize() {
        return 1;
    }

    @Override
    protected Vec3 cab() {
        return CAB;
    }

    @Override
    public double topSpeed() {
        return TrainConfig.dieselSpeed();
    }

    @Override
    public double power() {
        return 0.04;
    }

    @Override
    public double traction() {
        return battery.getAmountAsInt() >= TrainConfig.dieselFePerTick() || TrainConfig.dieselFePerTick() == 0 ? 1 : 0;
    }

    @Override
    public boolean fuelled() {
        return traction() > 0 || diesel.amount() > 0;
    }

    public EnergyHandler energyHandler() {
        return battery;
    }

    public FluidTank diesel() {
        return diesel;
    }

    public int energy() {
        return battery.getAmountAsInt();
    }

    public void setEnergy(int fe) {
        battery.set(Math.max(0, Math.min(BATTERY, fe)));
    }

    @Override
    public void burn(double effort) {
        feDebt += effort * TrainConfig.dieselFePerTick();
        if (feDebt >= 1) {
            int use = (int) Math.min(feDebt, battery.getAmountAsInt());
            battery.set(battery.getAmountAsInt() - use);
            feDebt -= (int) feDebt;
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) && ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Energy.ITEM) != null;
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        // the generator starts below 60% charge and runs until full (or out of diesel)
        int fe = battery.getAmountAsInt();
        if (fe < BATTERY * 0.6 && !diesel.isEmpty()) generating = true;
        if (fe >= BATTERY - GENERATOR || diesel.isEmpty()) generating = false;
        if (generating) {
            dieselDebt += GENERATOR;
            int mb = dieselDebt / TrainConfig.dieselFePerMb();
            if (mb > 0) {
                dieselDebt -= mb * TrainConfig.dieselFePerMb();
                diesel.drain(mb);
            }
            battery.set(Math.min(BATTERY, fe + GENERATOR));
        }
        drainSlot();
        setGauges(battery.getAmountAsInt(), diesel.amount(), generating ? 1 : 0);
    }

    /** Empties a charged item in the power slot into the battery. */
    private void drainSlot() {
        if (items.get(0).isEmpty()) return;
        int space = BATTERY - battery.getAmountAsInt();
        if (space <= 0) return;
        EnergyHandler source = ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(this), 0).getCapability(Capabilities.Energy.ITEM);
        if (source == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            int got = source.extract(Math.min(space, 5000), tx);
            tx.commit();
            if (got > 0) battery.set(battery.getAmountAsInt() + got);
        }
    }

    @Override
    protected Component noFuelMessage() {
        return Component.translatable("message.factoryascent.train.no_power");
    }

    @Override
    protected Holder<SoundEvent> hornSound() {
        return SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2);
    }

    @Override
    protected float hornPitch() {
        return 0.8f;
    }

    @Override
    protected void clientTick() {
        Vec3 f = front();
        boolean working = Math.abs(throttle()) > 0.01 || gaugeC() > 0;
        if (working && tickCount % 2 == 0) {
            Vec3 stack = position().add(f.scale(4 / 16.0)).add(0, 29 / 16.0, 0);
            level().addParticle(ParticleTypes.SMOKE, stack.x, stack.y, stack.z, 0, 0.12, 0);
        }
    }

    @Override
    protected InteractionResult interactWith(Player player, InteractionHand hand, ItemStack held) {
        FluidStack inside = FluidUtil.getFirstStackContained(held);
        if (!inside.isEmpty() && inside.is(ModFluids.DIESEL.source()) || held.is(net.minecraft.world.item.Items.BUCKET)) {
            if (!level().isClientSide()) FluidUtil.interactWithFluidHandler(player, hand, blockPosition(), diesel, null);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void clearCargo() {
        super.clearCargo();
        diesel.setContents(net.minecraft.world.level.material.Fluids.EMPTY, 0);
        battery.set(0);
    }

    @Override
    public void writeToItem(ItemStack stack) {
        super.writeToItem(stack);
        if (!diesel.isEmpty()) stack.set(FluidContent.TANK_CONTENTS.get(), SimpleFluidContent.copyOf(diesel.stack()));
        if (battery.getAmountAsInt() > 0) stack.set(TrainContent.TRAIN_ENERGY.get(), battery.getAmountAsInt());
    }

    @Override
    public void readFromItem(ItemStack stack) {
        super.readFromItem(stack);
        SimpleFluidContent c = stack.get(FluidContent.TANK_CONTENTS.get());
        if (c != null && !c.isEmpty()) {
            FluidStack fs = c.copy();
            diesel.setContents(fs.getFluid(), fs.getAmount());
        }
        Integer fe = stack.get(TrainContent.TRAIN_ENERGY.get());
        if (fe != null) setEnergy(fe);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        diesel.save(output, "Diesel");
        output.putInt("Energy", battery.getAmountAsInt());
        output.putBoolean("Generating", generating);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        diesel.load(input, "Diesel");
        setEnergy(input.getIntOr("Energy", 0));
        generating = input.getBooleanOr("Generating", false);
    }
}

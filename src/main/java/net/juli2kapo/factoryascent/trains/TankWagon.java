package net.juli2kapo.factoryascent.trains;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * A tank wagon: 32,000 mB of any one fluid (liquid or gas). It exposes its tank as a fluid handler
 * ({@link Capabilities.Fluid#ENTITY}), so a Train Station (and any pipe-like block that talks to
 * entities) fills and empties it; buckets and cells work by hand. Right-click shows its gauge.
 */
public class TankWagon extends RollingStock {
    public static final int CAPACITY = 32_000;
    private static final EntityDataAccessor<Integer> DATA_FLUID = SynchedEntityData.defineId(TankWagon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AMOUNT = SynchedEntityData.defineId(TankWagon.class, EntityDataSerializers.INT);

    private final FluidTank tank = new FluidTank(CAPACITY, r -> true, this::sync);

    public TankWagon(EntityType<? extends TankWagon> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLUID, 0);
        builder.define(DATA_AMOUNT, 0);
    }

    @Override
    public double length() {
        return 38 / 16.0;
    }

    @Override
    public double mass() {
        return 1.0 + 2.5 * tank.amount() / (double) CAPACITY;
    }

    @Override
    public boolean hasScreen() {
        return true;
    }

    public FluidTank tank() {
        return tank;
    }

    /** Client and server: the fluid in the tank and how much. */
    public Fluid syncedFluid() {
        return BuiltInRegistries.FLUID.byId(entityData.get(DATA_FLUID));
    }

    public int syncedAmount() {
        return entityData.get(DATA_AMOUNT);
    }

    private void sync() {
        if (level() == null || level().isClientSide()) return;
        entityData.set(DATA_FLUID, BuiltInRegistries.FLUID.getId(tank.fluid()));
        entityData.set(DATA_AMOUNT, tank.amount());
        setFill(tank.amount() * 1000 / CAPACITY);
    }

    @Override
    protected void serverTick() {
        if (tickCount % 20 == 0) sync();
    }

    @Override
    protected InteractionResult interactWith(Player player, InteractionHand hand, ItemStack held) {
        if (held.isEmpty() || net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            FluidUtil.interactWithFluidHandler(player, hand, blockPosition(), tank, null);
            sync();
            player.sendOverlayMessage(Component.translatable("message.factoryascent.train.tank", tank.amount(), CAPACITY,
                    tank.isEmpty() ? Component.translatable("gui.factoryascent.train.empty") : tank.fluid().getFluidType().getDescription()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void clearCargo() {
        super.clearCargo();
        tank.setContents(Fluids.EMPTY, 0);
    }

    @Override
    public void writeToItem(ItemStack stack) {
        super.writeToItem(stack);
        if (!tank.isEmpty()) stack.set(FluidContent.TANK_CONTENTS.get(), SimpleFluidContent.copyOf(tank.stack()));
    }

    @Override
    public void readFromItem(ItemStack stack) {
        super.readFromItem(stack);
        SimpleFluidContent c = stack.get(FluidContent.TANK_CONTENTS.get());
        if (c != null && !c.isEmpty()) {
            FluidStack fs = c.copy();
            tank.setContents(fs.getFluid(), fs.getAmount());
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        tank.save(output, "Tank");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        tank.load(input, "Tank");
        sync();
    }
}

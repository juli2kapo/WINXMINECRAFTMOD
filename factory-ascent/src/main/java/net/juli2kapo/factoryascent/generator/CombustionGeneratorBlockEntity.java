package net.juli2kapo.factoryascent.generator;

import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Burns furnace fuel. Higher tiers burn faster (tier speed) and squeeze more energy out of every
 * item (+15% per tier), so upgrading is always better than building more generators.
 */
public class CombustionGeneratorBlockEntity extends AbstractMachineBlockEntity {
    private float burnRemaining;
    private int burnTotal;
    private float energyCarry;

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.COMBUSTION_GENERATOR, pos, state);
    }

    public static float efficiency(Tier tier) {
        return 1f + 0.15f * (tier.level() - 1);
    }

    private float outputPerTick() {
        return (float) (type.baseEnergy() * tier().speed() * efficiency(tier()) * Config.GENERATOR_OUTPUT.get());
    }

    @Override
    protected void applyTier(Tier tier) {
        int out = Math.round(type.baseEnergy() * tier.speed() * efficiency(tier));
        int capacity = Math.max(20_000, out * 400);
        energy.configure(capacity, 0, out * 2);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean burning = false;
        lastEnergyRate = 0;
        float out = outputPerTick();
        if (energy.space() >= out) {
            if (burnRemaining <= 0) {
                ItemStack fuel = inventory.stack(slots.firstFuel());
                int burn = fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
                if (burn > 0) {
                    var remainder = fuel.getItem().getCraftingRemainder(fuel);
                    fuel.shrink(1);
                    if (fuel.isEmpty() && remainder != null) inventory.setStack(slots.firstFuel(), remainder.create());
                    else inventory.changed(slots.firstFuel());
                    burnTotal = burn;
                    burnRemaining = burn;
                }
            }
            if (burnRemaining > 0) {
                burnRemaining -= tier().speed();
                float produced = out + energyCarry;
                int whole = (int) produced;
                energyCarry = produced - whole;
                lastEnergyRate = energy.produce(whole);
                burning = true;
            }
        }
        status = burning ? STATUS_WORKING : energy.space() < out ? STATUS_FULL : STATUS_NO_FUEL;
        EnergyUtil.push(neighbors, energy, Math.round(out * 2), Direction.values());
        if (burning) setChanged();
        return burning;
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.FUEL && level != null) {
            return resource.toStack(1).getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0;
        }
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.FUEL && isItemValid(index, resource);
    }

    @Override
    public int progressPermille() {
        return burnTotal <= 0 ? 0 : Math.max(0, Math.round(burnRemaining / burnTotal * 1000));
    }

    @Override
    public int extraA() {
        return Math.round(outputPerTick());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnRemaining = input.getFloatOr("burn_remaining", 0f);
        burnTotal = input.getIntOr("burn_total", 0);
        energyCarry = input.getFloatOr("energy_carry", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("burn_remaining", burnRemaining);
        output.putInt("burn_total", burnTotal);
        output.putFloat("energy_carry", energyCarry);
    }
}

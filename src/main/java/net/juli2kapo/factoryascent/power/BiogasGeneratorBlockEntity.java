package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Automation age: a digester that rots organic matter (anything a composter takes, plus rotten
 * flesh, spider eyes and poisonous potatoes) into biogas, and a gas engine that burns it:
 * {@code biogasOutput} FE/t for 2 mB of gas a tick. An item gives 50 + 450 x its composting
 * chance mB (wheat 340, a pumpkin 340, a cake 500). Gas doesn't burn without air.
 */
public class BiogasGeneratorBlockEntity extends PowerBlockEntity {
    public static final int GAS_CAPACITY = 8000;
    public static final int DIGEST_TICKS = 30;
    public static final float GAS_PER_TICK = 2f;
    private float gas;
    private int digest;

    public BiogasGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.BIOGAS_GENERATOR).get(), pos, state, 3);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.BIOGAS_GENERATOR.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of(new SlotSpec(0, 44, 17, false), new SlotSpec(1, 44, 35, false), new SlotSpec(2, 44, 53, false));
    }

    /** mB of biogas one item gives, 0 if it isn't organic. */
    public static int gasValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)) return 200;
        var compost = stack.getData(NeoForgeDataMaps.COMPOSTABLES);
        return compost == null ? 0 : Math.round(50 + 450 * compost.chance());
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return gasValue(resource.toStack(1)) > 0;
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        boolean digesting = false;
        if (gas <= GAS_CAPACITY - 500) {
            for (int i = 0; i < 3; i++) {
                ItemStack stack = inventory.stack(i);
                if (gasValue(stack) <= 0) continue;
                digesting = true;
                if (++digest >= DIGEST_TICKS) {
                    digest = 0;
                    gas = Math.min(GAS_CAPACITY, gas + gasValue(stack));
                    stack.shrink(1);
                    inventory.changed();
                }
                break;
            }
        }
        if (!digesting) digest = 0;
        boolean airless = airless(level);
        float out = (float) (PowerConfig.get(PowerConfig.BIOGAS_OUTPUT) * PowerConfig.generatorMultiplier());
        if (!airless && gas >= GAS_PER_TICK && energy.space() > 0) {
            generate(out);
            gas -= GAS_PER_TICK;
        } else {
            lastRate = 0;
        }
        if (airless) status = ST_NO_AIR;
        else if (lastRate > 0) status = ST_RUNNING;
        else if (energy.space() <= 0) status = ST_FULL;
        else if (digesting) status = ST_DIGESTING;
        else status = ST_NO_FUEL;
        if (digesting || lastRate > 0) setChanged();
        return lastRate > 0;
    }

    public float gas() {
        return gas;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = Math.round(gas);
        extra[1] = GAS_CAPACITY;
        extra[2] = digest * 1000 / DIGEST_TICKS;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        gas = input.getFloatOr("gas", 0f);
        digest = input.getIntOr("digest", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("gas", gas);
        output.putInt("digest", digest);
    }
}

package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Automation age: pours lava through a heat exchanger. Lava buckets (1000 mB, the empty bucket
 * comes back out) or magma blocks (250 mB) fill its tank; it drinks 1 mB a tick for
 * {@code magmaticOutput} FE/t, so a bucket lasts 50 seconds. Where the Geothermal Generator
 * wants lava pools, this one is fed by pipes. Heat, not fire: it works without air.
 */
public class MagmaticGeneratorBlockEntity extends PowerBlockEntity {
    public static final int IN = 0, OUT = 1;
    public static final int LAVA_CAPACITY = 4000;
    private float lava;
    private final net.juli2kapo.factoryascent.fluid.FloatTanks fluids = new net.juli2kapo.factoryascent.fluid.FloatTanks(this::setChanged,
            new net.juli2kapo.factoryascent.fluid.FloatTanks.Tank(() -> net.minecraft.world.level.material.Fluids.LAVA,
                    f -> f.isSame(net.minecraft.world.level.material.Fluids.LAVA) ? 1 : 0, () -> lava, v -> lava = v,
                    () -> LAVA_CAPACITY, true, false));

    /** What pipes see: a lava inlet (a Pump on a lava lake feeds it). */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidHandler() {
        return fluids;
    }

    public MagmaticGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.MAGMATIC_GENERATOR).get(), pos, state, 2);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.MAGMATIC_GENERATOR.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of(new SlotSpec(IN, 44, 17, false), new SlotSpec(OUT, 44, 53, true));
    }

    public static int lavaValue(ItemStack stack) {
        if (stack.is(Items.LAVA_BUCKET)) return 1000;
        if (stack.is(Items.MAGMA_BLOCK)) return 250;
        return 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return slot == IN && lavaValue(resource.toStack(1)) > 0;
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        ItemStack in = inventory.stack(IN);
        int value = lavaValue(in);
        if (value > 0 && lava + value <= LAVA_CAPACITY) {
            boolean bucket = in.is(Items.LAVA_BUCKET);
            if (!bucket || inventory.canAddOutput(OUT, new ItemStack(Items.BUCKET))) {
                in.shrink(1);
                inventory.changed();
                if (bucket) inventory.addOutput(OUT, new ItemStack(Items.BUCKET));
                lava += value;
            }
        }
        float out = (float) (PowerConfig.get(PowerConfig.MAGMATIC_OUTPUT) * PowerConfig.generatorMultiplier());
        if (lava >= 1 && energy.space() > 0) {
            generate(out);
            lava -= 1;
            setChanged();
        } else {
            lastRate = 0;
        }
        status = lastRate > 0 ? ST_RUNNING : energy.space() <= 0 ? ST_FULL : ST_NO_FUEL;
        return lastRate > 0;
    }

    public float lava() {
        return lava;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = Math.round(lava);
        extra[1] = LAVA_CAPACITY;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lava = input.getFloatOr("lava", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("lava", lava);
    }
}

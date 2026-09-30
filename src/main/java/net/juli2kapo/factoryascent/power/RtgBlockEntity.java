package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Orbital age: a radioisotope thermoelectric generator. The decay heat of a Radioisotope Pellet
 * (refined from nuclear waste) gives a small, perfectly steady {@code rtgOutput} FE/t for an hour,
 * day or night, in any weather, with or without air: the power source of space stations. Its lead
 * casing keeps the pellets' radiation in.
 */
public class RtgBlockEntity extends PowerBlockEntity {
    private int decayLeft;

    public RtgBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.RTG).get(), pos, state, 1);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.RTG.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of(new SlotSpec(0, 80, 35, false));
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return resource.is(PowerContent.RADIOISOTOPE_PELLET.get());
    }

    private static int pelletTicks() {
        return PowerConfig.get(PowerConfig.RTG_PELLET_SECONDS) * 20;
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        if (decayLeft <= 0) {
            ItemStack pellet = inventory.stack(0);
            if (pellet.is(PowerContent.RADIOISOTOPE_PELLET.get())) {
                pellet.shrink(1);
                inventory.changed();
                decayLeft = pelletTicks();
            }
        }
        if (decayLeft > 0) {
            decayLeft--;
            generate((float) (PowerConfig.get(PowerConfig.RTG_OUTPUT) * PowerConfig.generatorMultiplier()));
            setChanged();
        } else {
            lastRate = 0;
        }
        status = decayLeft > 0 ? ST_RUNNING : ST_NO_PELLET;
        return decayLeft > 0;
    }

    /** Seconds of power left: the pellet decaying now plus the ones waiting in the slot. */
    public int secondsLeft() {
        return (decayLeft + inventory.stack(0).getCount() * pelletTicks()) / 20;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = decayLeft <= 0 ? 0 : (int) ((long) decayLeft * 1000 / pelletTicks());
        extra[1] = secondsLeft();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        decayLeft = input.getIntOr("decay_left", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("decay_left", decayLeft);
    }
}

package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.power.BiogasGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Automation age: a sealed digester tank. Organic items (whatever the Biogas Generator takes,
 * the same amount of gas per item) rot into biogas, piped out to Biogas Generators. Needs no power.
 */
public class BiogasDigesterBlockEntity extends FluidMachineBlockEntity {
    final FluidTank gas;
    private int digest;

    public BiogasDigesterBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.BIOGAS_DIGESTER, pos, state);
        gas = addTank(new FluidTank(16_000, r -> r.getFluid().isSame(ModFluids.BIOGAS.source()), this::setChanged).output());
        finishTanks();
    }

    public FluidTank gas() {
        return gas;
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return BiogasGeneratorBlockEntity.gasValue(resource.toStack(1)) > 0;
    }

    @Override
    protected boolean tick(ServerLevel level) {
        int ticks = FluidsConfig.get(FluidsConfig.BIOGAS_DIGEST_TICKS);
        boolean digesting = false;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = inventory.stack(i);
            int value = BiogasGeneratorBlockEntity.gasValue(stack);
            if (value <= 0 || gas.space() < value) continue;
            digesting = true;
            if (++digest >= ticks) {
                digest = 0;
                gas.forceFill(ModFluids.BIOGAS.source(), value);
                stack.shrink(1);
                inventory.changed();
            }
            break;
        }
        if (!digesting) digest = 0;
        progress = digest * 1000 / Math.max(1, ticks);
        lastRate = 0;
        if (digesting) status = ST_DIGESTING;
        else if (gas.space() < 50) status = ST_FULL;
        else status = ST_NO_INPUT;
        if (digesting) setChanged();
        return digesting;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        digest = input.getIntOr("digest", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("digest", digest);
    }
}

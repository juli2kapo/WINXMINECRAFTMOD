package net.juli2kapo.factoryascent.fusion;

import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Electrolyzer: an ordinary processing machine, except that the empty bucket a water bucket
 * leaves behind moves on into an output slot, so pipes can feed it water buckets forever.
 */
public class ElectrolyzerBlockEntity extends ProcessingMachineBlockEntity {
    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.ELECTROLYZER, pos, state);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean working = super.tickMachine(level);
        for (int i = slots.firstInput(); i < slots.firstMold(); i++) {
            ItemStack in = inventory.stack(i);
            if (!in.is(Items.BUCKET)) continue;
            for (int o = slots.firstUpgrade() - 1; o >= slots.firstOutput() && !in.isEmpty(); o--) {
                ItemStack out = inventory.stack(o);
                if (out.isEmpty()) {
                    inventory.setStack(o, in.copy());
                    in.setCount(0);
                } else if (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize()) {
                    int move = Math.min(in.getCount(), out.getMaxStackSize() - out.getCount());
                    out.grow(move);
                    in.shrink(move);
                    inventory.changed(o);
                }
            }
            inventory.changed(i);
        }
        return working;
    }
}

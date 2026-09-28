package net.juli2kapo.factoryascent.automation;

import net.juli2kapo.factoryascent.machine.MachineInventory;
import net.juli2kapo.factoryascent.machine.MachineSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;

/** Filling a machine's output slots from world drops (breaker, vacuum hopper, farms). */
public final class MachineOutputs {
    private MachineOutputs() {}

    /** Merges the stack into the output slots; returns what did not fit (the argument, shrunk). */
    public static ItemStack insert(MachineInventory inventory, ItemStack stack) {
        MachineSlots slots = inventory.slots();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = slots.firstOutput(); i < slots.firstUpgrade() && !stack.isEmpty(); i++) {
                ItemStack there = inventory.stack(i);
                if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack)) {
                    int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                    if (move > 0) {
                        there.grow(move);
                        stack.shrink(move);
                        inventory.changed(i);
                    }
                } else if (pass == 1 && there.isEmpty()) {
                    inventory.setStack(i, stack.copy());
                    stack.setCount(0);
                }
            }
        }
        return stack;
    }

    /** Inserts, and pops whatever does not fit out on top of the machine (never voids items). */
    public static void insertOrDrop(MachineInventory inventory, ItemStack stack, Level level, BlockPos machine) {
        ItemStack rest = insert(inventory, stack);
        if (!rest.isEmpty()) Block.popResource(level, machine.above(), rest);
    }

    public static int emptySlots(MachineInventory inventory) {
        MachineSlots slots = inventory.slots();
        int n = 0;
        for (int i = slots.firstOutput(); i < slots.firstUpgrade(); i++) if (inventory.stack(i).isEmpty()) n++;
        return n;
    }
}

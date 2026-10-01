package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** One menu for every fluid machine; slots from {@link FluidMachine#slots()}, gauges from {@link FluidMachineData}. */
public class FluidMachineMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 178, PLAYER_INV_Y = 96;

    private final FluidMachineBlockEntity machine;
    private final FluidMachineData data;
    private final int machineSlots;

    public FluidMachineMenu(int id, Inventory inventory, FluidMachineBlockEntity machine) {
        this(id, inventory, machine, machine.data());
    }

    private FluidMachineMenu(int id, Inventory inventory, FluidMachineBlockEntity machine, FluidMachineData data) {
        super(FluidContent.MACHINE_MENU.get(), id);
        this.machine = machine;
        this.data = data;
        var inv = machine.inventory();
        for (FluidMachine.SlotSpec spec : machine.machine().slots()) {
            addSlot(new ResourceHandlerSlot(inv, inv::set, spec.index(), spec.x(), spec.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !spec.output() && machine.isItemValid(spec.index(), ItemResource.of(stack));
                }
            });
        }
        this.machineSlots = machine.machine().slots().size();
        addStandardInventorySlots(inventory, 8, PLAYER_INV_Y);
        addDataSlots(data);
    }

    public static FluidMachineMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof FluidMachineBlockEntity be) {
            return new FluidMachineMenu(id, inventory, be, new FluidMachineData());
        }
        throw new IllegalStateException("No fluid machine at " + pos);
    }

    public FluidMachineBlockEntity machine() {
        return machine;
    }

    public FluidMachineData data() {
        return data;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(machine, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = slots.size();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, end, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < machineSlots && !moved; i++) {
                if (slots.get(i).mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) {
                int hotbar = end - 9;
                if (index < hotbar) {
                    if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, machineSlots, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}

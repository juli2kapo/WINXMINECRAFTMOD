package net.juli2kapo.factoryascent.machine;

import net.juli2kapo.factoryascent.item.MoldItem;
import net.juli2kapo.factoryascent.item.UpgradeItem;
import net.juli2kapo.factoryascent.registry.ModMenus;
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

/** One menu for every machine; the slot set comes from the machine's {@link MachineSlots}. */
public class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_EJECT = 0;

    private final AbstractMachineBlockEntity machine;
    private final MachineData data;
    private final int machineSlotCount;

    public MachineMenu(int id, Inventory playerInventory, AbstractMachineBlockEntity machine) {
        this(id, playerInventory, machine, machine.data);
    }

    private MachineMenu(int id, Inventory playerInventory, AbstractMachineBlockEntity machine, MachineData data) {
        super(ModMenus.MACHINE.get(), id);
        this.machine = machine;
        this.data = data;
        MachineInventory inv = machine.inventory();
        MachineSlots s = inv.slots();
        MachineType type = machine.type();
        for (int i = 0; i < s.inputs(); i++) {
            MachineLayout.Pos p = MachineLayout.input(type, i);
            addSlot(new ResourceHandlerSlot(inv, inv::set, s.firstInput() + i, p.x(), p.y()));
        }
        for (int i = 0; i < s.mold(); i++) {
            MachineLayout.Pos p = MachineLayout.mold(i);
            addSlot(new ResourceHandlerSlot(inv, inv::set, s.firstMold() + i, p.x(), p.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MoldItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int i = 0; i < s.fuel(); i++) {
            MachineLayout.Pos p = MachineLayout.fuel(i);
            addSlot(new ResourceHandlerSlot(inv, inv::set, s.firstFuel() + i, p.x(), p.y()));
        }
        for (int i = 0; i < s.outputs(); i++) {
            MachineLayout.Pos p = MachineLayout.output(type, i);
            addSlot(new ResourceHandlerSlot(inv, inv::set, s.firstOutput() + i, p.x(), p.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int i = 0; i < s.upgrades(); i++) {
            MachineLayout.Pos p = MachineLayout.upgrade(i);
            addSlot(new ResourceHandlerSlot(inv, inv::set, s.firstUpgrade() + i, p.x(), p.y()));
        }
        this.machineSlotCount = s.size();
        addStandardInventorySlots(playerInventory, 8, MachineLayout.PLAYER_INV_Y);
        addDataSlots(data);
    }

    /** Client-side constructor: the server wrote the block position. */
    public static MachineMenu fromNetwork(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof AbstractMachineBlockEntity be) {
            return new MachineMenu(id, playerInventory, be, new MachineData());
        }
        throw new IllegalStateException("No machine at " + pos);
    }

    public AbstractMachineBlockEntity machine() {
        return machine;
    }

    public MachineData data() {
        return data;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(machine, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_TOGGLE_EJECT) {
            machine.toggleAutoEject();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = machineSlotCount;
        int playerEnd = slots.size();
        MachineSlots s = machine.inventory().slots();

        if (index < machineSlotCount) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof UpgradeItem && s.upgrades() > 0) {
                moved = moveItemStackTo(stack, s.firstUpgrade(), s.firstUpgrade() + s.upgrades(), false);
            }
            if (!moved && stack.getItem() instanceof MoldItem && s.mold() > 0) {
                moved = moveItemStackTo(stack, s.firstMold(), s.firstMold() + s.mold(), false);
            }
            if (!moved && s.fuel() > 0 && machine.isItemValid(s.firstFuel(), ItemResource.of(stack))) {
                moved = moveItemStackTo(stack, s.firstFuel(), s.firstFuel() + s.fuel(), false);
            }
            if (!moved && s.inputs() > 0 && machine.canAutomationInsert(s.firstInput(), ItemResource.of(stack))) {
                moved = moveItemStackTo(stack, s.firstInput(), s.firstInput() + s.inputs(), false);
            }
            if (!moved) {
                int hotbarStart = playerEnd - 9;
                if (index < hotbarStart) {
                    if (!moveItemStackTo(stack, hotbarStart, playerEnd, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, playerStart, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}

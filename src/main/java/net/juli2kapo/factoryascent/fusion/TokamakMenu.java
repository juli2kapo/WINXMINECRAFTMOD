package net.juli2kapo.factoryascent.fusion;

import net.juli2kapo.factoryascent.power.PowerContent;
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

/** The tokamak screen: deuterium, helium-3 and tritium in, empty cells out, and the on/off switch. */
public class TokamakMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 196, INV_Y = 114, SLOT_Y = 70, BTN_TOGGLE = 0;
    public static final int[] SLOT_X = {8, 30, 52, 150};

    private final TokamakCoreBlockEntity core;
    private final TokamakData data;

    public TokamakMenu(int id, Inventory inventory, TokamakCoreBlockEntity core) {
        this(id, inventory, core, core.data());
    }

    private TokamakMenu(int id, Inventory inventory, TokamakCoreBlockEntity core, TokamakData data) {
        super(PowerContent.TOKAMAK_MENU.get(), id);
        this.core = core;
        this.data = data;
        var inv = core.inventory();
        for (int i = 0; i < TokamakCoreBlockEntity.SLOTS; i++) {
            final int index = i;
            addSlot(new ResourceHandlerSlot(inv, inv::set, i, SLOT_X[i], SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TokamakCoreBlockEntity.isValid(index, ItemResource.of(stack));
                }
            });
        }
        addStandardInventorySlots(inventory, 8, INV_Y);
        addDataSlots(data);
    }

    public static TokamakMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof TokamakCoreBlockEntity be) {
            return new TokamakMenu(id, inventory, be, new TokamakData());
        }
        throw new IllegalStateException("No tokamak core at " + pos);
    }

    public TokamakData data() {
        return data;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BTN_TOGGLE) return false;
        core.toggle();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(core, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int n = TokamakCoreBlockEntity.SLOTS, end = slots.size();
        if (index < n) {
            if (!moveItemStackTo(stack, n, end, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < n && !moved; i++) {
                if (slots.get(i).mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) {
                int hotbar = end - 9;
                if (index < hotbar) {
                    if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, n, hotbar, false)) {
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

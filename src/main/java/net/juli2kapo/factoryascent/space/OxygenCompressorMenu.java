package net.juli2kapo.factoryascent.space;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Oxygen Compressor's screen: the suit slot, the energy buffer and the suit's air. */
public class OxygenCompressorMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 166;
    public static final int SLOT_X = 80, SLOT_Y = 35;
    public static final int PLAYER_INV_Y = 84;
    public static final int DATA_ENERGY = 0, DATA_COUNT = 1;

    private final OxygenCompressorBlockEntity compressor;
    private final ContainerData data;

    public OxygenCompressorMenu(int id, Inventory inventory, OxygenCompressorBlockEntity compressor) {
        this(id, inventory, compressor, new ContainerData() {
            @Override
            public int get(int index) {
                return index == DATA_ENERGY ? compressor.energy() : 0;
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private OxygenCompressorMenu(int id, Inventory inventory, OxygenCompressorBlockEntity compressor, ContainerData data) {
        super(SpaceContent.OXYGEN_COMPRESSOR_MENU.get(), id);
        this.compressor = compressor;
        this.data = data;
        addSlot(new Slot(compressor.items(), 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SuitItems.holdsOxygen(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addStandardInventorySlots(inventory, 8, PLAYER_INV_Y);
        addDataSlots(data);
    }

    public static OxygenCompressorMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof OxygenCompressorBlockEntity be) {
            return new OxygenCompressorMenu(id, inventory, be, new SimpleContainerData(DATA_COUNT));
        }
        throw new IllegalStateException("No oxygen compressor at " + pos);
    }

    public int energy() {
        return data.get(DATA_ENERGY);
    }

    public ItemStack suit() {
        return getSlot(0).getItem();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(compressor, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = slots.size();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, end, true)) return ItemStack.EMPTY;
        } else if (SuitItems.holdsOxygen(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            int hotbar = end - 9;
            if (index < hotbar) {
                if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 1, hotbar, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}

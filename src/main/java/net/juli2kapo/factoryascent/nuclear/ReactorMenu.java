package net.juli2kapo.factoryascent.nuclear;

import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** The reactor screen: fuel, spent-rod and coolant slots, control-rod buttons and SCRAM. */
public class ReactorMenu extends AbstractContainerMenu {
    public static final int WIDTH = 248, HEIGHT = 228, SLOT_Y = 114, INV_X = 44, INV_Y = 146;
    public static final int BTN_MINUS_10 = 0, BTN_MINUS_1 = 1, BTN_PLUS_1 = 2, BTN_PLUS_10 = 3, BTN_SCRAM = 4, BTN_SET = 100;

    private final ReactorControllerBlockEntity reactor;
    private final ReactorData data;

    public ReactorMenu(int id, Inventory inventory, ReactorControllerBlockEntity reactor) {
        this(id, inventory, reactor, reactor.data());
    }

    private ReactorMenu(int id, Inventory inventory, ReactorControllerBlockEntity reactor, ReactorData data) {
        super(PowerContent.REACTOR_MENU.get(), id);
        this.reactor = reactor;
        this.data = data;
        var inv = reactor.inventory();
        for (int i = 0; i < ReactorControllerBlockEntity.SLOTS; i++) {
            final int index = i;
            addSlot(new ResourceHandlerSlot(inv, inv::set, i, slotX(i), SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !ReactorControllerBlockEntity.isOutputSlot(index) && ReactorControllerBlockEntity.isValid(index, ItemResource.of(stack));
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    /** Item x of a reactor slot: six fuel, three spent, coolant in and out. */
    public static int slotX(int i) {
        if (i < 6) return 8 + 18 * i;
        if (i < 9) return 124 + 18 * (i - 6);
        return i == ReactorControllerBlockEntity.COOLANT_IN ? 186 : 222;
    }

    public static ReactorMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof ReactorControllerBlockEntity be) {
            return new ReactorMenu(id, inventory, be, new ReactorData());
        }
        throw new IllegalStateException("No reactor controller at " + pos);
    }

    public ReactorData data() {
        return data;
    }

    public ReactorControllerBlockEntity reactor() {
        return reactor;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BTN_MINUS_10 -> reactor.setInsertion(reactor.insertion() - 10);
            case BTN_MINUS_1 -> reactor.setInsertion(reactor.insertion() - 1);
            case BTN_PLUS_1 -> reactor.setInsertion(reactor.insertion() + 1);
            case BTN_PLUS_10 -> reactor.setInsertion(reactor.insertion() + 10);
            case BTN_SCRAM -> {
                reactor.toggleScram();
                if (reactor.getLevel() != null) {
                    reactor.getLevel().playSound(null, reactor.getBlockPos(), reactor.scramButton()
                            ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1f, 0.6f);
                }
            }
            default -> {
                if (id < BTN_SET || id > BTN_SET + 100) return false;
                reactor.setInsertion(id - BTN_SET);
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(reactor, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int n = ReactorControllerBlockEntity.SLOTS, end = slots.size();
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

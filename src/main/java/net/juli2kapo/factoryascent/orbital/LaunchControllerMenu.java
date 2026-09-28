package net.juli2kapo.factoryascent.orbital;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Launch Controller's screen: a payload slot (the mounted satellite or missile itself: put
 * one in to mount it, take it out to get it back, not during a launch), a fuel slot that pours
 * Blaze Powder / Rocket Fuel straight into the tank (whatever doesn't fit stays on the cursor), and
 * the pad's fuel, readiness and launch progress. The Launch button ({@link #BUTTON_LAUNCH}) follows
 * the same rules as flint and steel or a redstone pulse; a refusal comes back as a
 * {@link OrbitalPayloads.PadMessage}.
 */
public class LaunchControllerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 190;
    public static final int PAYLOAD_X = 17, PAYLOAD_Y = 30;
    public static final int FUEL_X = 17, FUEL_Y = 66;
    public static final int PLAYER_INV_Y = 108;
    public static final int BUTTON_LAUNCH = 0;

    /** Synced numbers: fuel units, launch tick (-1 idle), pad status. */
    public static final int DATA_FUEL = 0, DATA_TICK = 1, DATA_STATUS = 2, DATA_COUNT = 3;

    private final LaunchControllerBlockEntity controller;
    private final ContainerData data;

    public LaunchControllerMenu(int id, Inventory playerInventory, LaunchControllerBlockEntity controller) {
        this(id, playerInventory, controller, serverData(controller));
    }

    private LaunchControllerMenu(int id, Inventory playerInventory, LaunchControllerBlockEntity controller, ContainerData data) {
        super(OrbitalContent.LAUNCH_CONTROLLER_MENU.get(), id);
        this.controller = controller;
        this.data = data;
        Player player = playerInventory.player;
        addSlot(new PayloadSlot(new PayloadContainer(controller, player), PAYLOAD_X, PAYLOAD_Y));
        addSlot(new FuelSlot(controller, player, FUEL_X, FUEL_Y));
        addStandardInventorySlots(playerInventory, 8, PLAYER_INV_Y);
        addDataSlots(data);
    }

    private static ContainerData serverData(LaunchControllerBlockEntity c) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_FUEL -> c.fuel();
                    case DATA_TICK -> c.launchTick();
                    case DATA_STATUS -> c.status();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public static LaunchControllerMenu fromNetwork(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller) {
            return new LaunchControllerMenu(id, playerInventory, controller, new SimpleContainerData(DATA_COUNT));
        }
        throw new IllegalStateException("No launch controller at " + pos);
    }

    public LaunchControllerBlockEntity controller() {
        return controller;
    }

    public int fuel() {
        return data.get(DATA_FUEL);
    }

    public int launchTick() {
        return data.get(DATA_TICK);
    }

    public int status() {
        return data.get(DATA_STATUS);
    }

    /** The Launch button: same rules as flint and steel; a refusal is sent back to the screen. */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != BUTTON_LAUNCH) return false;
        Component problem = controller.tryLaunch();
        if (problem != null && player instanceof ServerPlayer sp) {
            if (sp.connection.hasChannel(OrbitalPayloads.PadMessage.TYPE)) {
                PacketDistributor.sendToPlayer(sp, new OrbitalPayloads.PadMessage(problem));
            } else {
                sp.sendOverlayMessage(problem);
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(controller, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = slots.size();
        if (index == 0) {
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            ItemStack taken = slot.remove(1);
            if (!moveItemStackTo(taken, 2, end, true)) {
                slot.set(taken); // no room: put it back
                return ItemStack.EMPTY;
            }
            return original;
        }
        if (LaunchControllerBlockEntity.isPayload(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (LaunchControllerBlockEntity.fuelValue(stack) > 0) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            int hotbar = end - 9;
            if (index < hotbar) {
                if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 2, hotbar, false)) {
                return ItemStack.EMPTY;
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

    // ---------------------------------------------------------------- slots

    /** The mounted payload as a one-item container: taking it dismounts, putting one in mounts it. */
    private record PayloadContainer(LaunchControllerBlockEntity controller, Player player) implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return controller.satellite().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return controller.satellite();
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return count <= 0 ? ItemStack.EMPTY : controller.dismount();
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return controller.dismount();
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            controller.mountFromMenu(stack, player.getUUID());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            controller.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(controller, player);
        }

        @Override
        public void clearContent() {
            controller.dismount();
        }
    }

    private final class PayloadSlot extends Slot {
        PayloadSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return LaunchControllerBlockEntity.isPayload(stack) && controller.mountProblem() == null;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !controller.launching();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /**
     * Always empty: fuel put here goes straight into the tank, as many whole items as fit (the rest
     * stays with the player). Only the server fills the tank; the client just waits for the sync.
     */
    private static final class FuelSlot extends Slot {
        private final LaunchControllerBlockEntity controller;
        private final Player player;

        FuelSlot(LaunchControllerBlockEntity controller, Player player, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.controller = controller;
            this.player = player;
        }

        private int room(ItemStack stack) {
            int units = LaunchControllerBlockEntity.fuelValue(stack);
            return units <= 0 ? 0 : (LaunchControllerBlockEntity.FUEL_MAX - controller.fuel()) / units;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return room(stack) > 0;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.max(1, room(stack));
        }

        @Override
        public void set(ItemStack stack) {
            if (!stack.isEmpty() && !player.level().isClientSide()) {
                int units = LaunchControllerBlockEntity.fuelValue(stack);
                int n = Math.min(stack.getCount(), room(stack));
                if (n > 0) controller.addFuel(n * units);
            }
            setChanged();
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}

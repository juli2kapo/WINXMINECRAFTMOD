package net.juli2kapo.factoryascent.ships;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The helm (sea ships) or cockpit (shuttle) screen: the hold on the left, and an instrument panel
 * on the right with the fuel/power slot and the ship's numbers (speed, heading, fuel, wind,
 * altitude, state), synced as {@link ContainerData}. Buttons: horn and lights.
 */
public class ShipMenu extends AbstractContainerMenu {
    public static final int PANEL_W = 96;
    public static final int GRID_X = 8, GRID_Y = 18;
    public static final int BUTTON_HORN = 0, BUTTON_LIGHTS = 1;

    public static final int D_SPEED = 0, D_HEADING = 1, D_FUEL = 2, D_FUEL_MAX = 3, D_ALT = 4, D_STATE = 5, D_AUX = 6,
            D_LIGHTS = 7, D_WIND = 8, D_WIND_STRENGTH = 9, D_FUEL_SCALE = 10, D_COUNT = 11;

    private final AbstractShip ship;
    private final ContainerData data;
    private final int cargo;
    private final int rows;

    public ShipMenu(int id, Inventory inventory, AbstractShip ship) {
        this(id, inventory, ship, serverData(ship));
    }

    private ShipMenu(int id, Inventory inventory, AbstractShip ship, ContainerData data) {
        super(ShipContent.SHIP_MENU.get(), id);
        this.ship = ship;
        this.data = data;
        this.cargo = ship.cargoSize();
        this.rows = (cargo + 8) / 9;
        for (int i = 0; i < cargo; i++) {
            addSlot(new ShipSlot(ship, i, GRID_X + (i % 9) * 18, GRID_Y + (i / 9) * 18));
        }
        if (ship.fuelSlot() >= 0) addSlot(new ShipSlot(ship, ship.fuelSlot(), fuelSlotX(), fuelSlotY()));
        addStandardInventorySlots(inventory, 8, playerInvY());
        addDataSlots(data);
    }

    public static ShipMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        if (inventory.player.level().getEntity(entityId) instanceof AbstractShip ship) {
            return new ShipMenu(id, inventory, ship, new SimpleContainerData(D_COUNT));
        }
        throw new IllegalStateException("No ship with id " + entityId);
    }

    private static ContainerData serverData(AbstractShip ship) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int scale = Math.max(1, ship.fuelCapacity() / 30000 + (ship.fuelCapacity() > 30000 ? 1 : 0));
                long time = ship.level().getGameTime();
                return switch (index) {
                    case D_SPEED -> (int) Math.round(ship.horizontalSpeed() * 20 * 3.6 * 10);
                    case D_HEADING -> ShipMath.compassHeading(ship.getYRot());
                    case D_FUEL -> ship.fuel() / scale;
                    case D_FUEL_MAX -> ship.fuelCapacity() / scale;
                    case D_ALT -> Mth.floor(ship.getY());
                    case D_STATE -> ship.state();
                    case D_AUX -> ship instanceof BronzeCog cog ? Math.round(cog.sailFactor() * 100)
                            : Math.round((float) ship.getDeltaMovement().y * 20 * 10);
                    case D_LIGHTS -> ship.lightsOn() ? 1 : 0;
                    case D_WIND -> Math.floorMod(Math.round(ShipMath.windYaw(time)), 360);
                    case D_WIND_STRENGTH -> Math.round(ShipMath.windStrength(time, ship.level().getRainLevel(1f),
                            ship.level().getThunderLevel(1f)) * 100);
                    case D_FUEL_SCALE -> scale;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return D_COUNT;
            }
        };
    }

    // ---------------------------------------------------------------- layout

    public int rows() {
        return rows;
    }

    public int playerInvY() {
        return GRID_Y + rows * 18 + 14;
    }

    public int imageWidth() {
        return 176 + PANEL_W;
    }

    public int imageHeight() {
        return playerInvY() + 76 + 7;
    }

    public int fuelSlotX() {
        return 176 + 10;
    }

    public int fuelSlotY() {
        return GRID_Y + 2;
    }

    public AbstractShip ship() {
        return ship;
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean hasFuelSlot() {
        return ship.fuelSlot() >= 0;
    }

    // ---------------------------------------------------------------- buttons

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_HORN) {
            ship.action(player, ShipPayloads.ACTION_HORN);
            return true;
        }
        if (id == BUTTON_LIGHTS) {
            ship.action(player, ShipPayloads.ACTION_LIGHTS);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return ship.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int shipSlots = cargo + (hasFuelSlot() ? 1 : 0);
        int end = slots.size();
        if (index < shipSlots) {
            if (!moveItemStackTo(stack, shipSlots, end, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (hasFuelSlot() && ship.acceptsFuel(stack)) moved = moveItemStackTo(stack, cargo, cargo + 1, false);
            if (!moved && !(stack.getItem() instanceof ShipItem)) moved = moveItemStackTo(stack, 0, cargo, false);
            if (!moved) return ItemStack.EMPTY;
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

    /** A hold or fuel slot: asks the ship what fits. */
    private static final class ShipSlot extends Slot {
        private final AbstractShip ship;

        ShipSlot(AbstractShip ship, int index, int x, int y) {
            super(ship, index, x, y);
            this.ship = ship;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ship.canPlaceItem(getContainerSlot(), stack);
        }
    }
}

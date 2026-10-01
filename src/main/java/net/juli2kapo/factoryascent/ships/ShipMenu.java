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
    public static final int PANEL_W = 124;
    public static final int GRID_X = 8, GRID_Y = 18;
    public static final int BUTTON_HORN = 0, BUTTON_LIGHTS = 1;
    /** Shuttle navigation: pick destination {@code BUTTON_DEST + index}, then Engage. */
    public static final int BUTTON_DEST = 10, BUTTON_ENGAGE = 20;
    /** Shuttle navigation: cycle through the team's Distress Beacons (see outpost/DistressBeacons). */
    public static final int BUTTON_BEACON = 15;
    /** Width of the shuttle's Navigation panel, right of the instruments. */
    public static final int NAV_W = 132;

    public static final int D_SPEED = 0, D_HEADING = 1, D_FUEL = 2, D_FUEL_MAX = 3, D_ALT = 4, D_STATE = 5, D_AUX = 6,
            D_LIGHTS = 7, D_WIND = 8, D_WIND_STRENGTH = 9, D_FUEL_SCALE = 10,
            D_NAV_SELECTED = 11, D_NAV_HERE = 12, D_NAV_CRUISE = 13, D_NAV_ION = 14, D_NAV_TARGET = 15,
            /** Distress beacons: how many the viewer's team has, which one is picked (1-based, 0 none), its planet
             * ordinal and x/z, each coordinate split in 16-bit halves (data slots sync as shorts). */
            D_BEACON_COUNT = 16, D_BEACON_INDEX = 17, D_BEACON_PLANET = 18, D_BEACON_X = 19, D_BEACON_XH = 20,
            D_BEACON_Z = 21, D_BEACON_ZH = 22, D_COUNT = 23;

    private final AbstractShip ship;
    private final ContainerData data;
    private final int cargo;
    private final int rows;

    public ShipMenu(int id, Inventory inventory, AbstractShip ship) {
        this(id, inventory, ship, serverData(ship, inventory.player));
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

    private static ContainerData serverData(AbstractShip ship, Player viewer) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= D_BEACON_COUNT) return beaconData(ship, viewer, index);
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
                    case D_NAV_SELECTED -> ship instanceof Shuttle s ? s.selected().ordinal() : 0;
                    case D_NAV_HERE -> ship instanceof Shuttle s && s.here() != null ? s.here().ordinal() : -1;
                    case D_NAV_CRUISE -> ship instanceof Shuttle s && s.cruising() ? Math.round(s.cruiseProgress() * 100) : -1;
                    case D_NAV_ION -> ship instanceof Shuttle s && s.ionDrive() ? 1 : 0;
                    case D_NAV_TARGET -> ship instanceof Shuttle s && s.cruiseTarget() != null ? s.cruiseTarget().ordinal() : -1;
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

    /** The distress beacon slots: the viewer's team's beacons and the shuttle's pick among them. */
    private static int beaconData(AbstractShip ship, Player viewer, int index) {
        if (!(ship instanceof Shuttle shuttle) || ship.level().getServer() == null) return index == D_BEACON_PLANET ? -1 : 0;
        var server = ship.level().getServer();
        String team = net.juli2kapo.factoryascent.orbital.FactoryTeams.get(server).teamOf(viewer.getUUID());
        var list = net.juli2kapo.factoryascent.outpost.DistressBeacons.get(server).forTeam(team);
        var picked = shuttle.beacon();
        int at = net.juli2kapo.factoryascent.outpost.DistressBeacons.indexOf(list, picked);
        var planet = at > 0 ? list.get(at - 1).planet() : null;
        return switch (index) {
            case D_BEACON_COUNT -> list.size();
            case D_BEACON_INDEX -> at;
            case D_BEACON_PLANET -> planet == null ? -1 : planet.ordinal();
            case D_BEACON_X -> at > 0 ? picked.pos().getX() & 0xFFFF : 0;
            case D_BEACON_XH -> at > 0 ? (picked.pos().getX() >> 16) & 0xFFFF : 0;
            case D_BEACON_Z -> at > 0 ? picked.pos().getZ() & 0xFFFF : 0;
            case D_BEACON_ZH -> at > 0 ? (picked.pos().getZ() >> 16) & 0xFFFF : 0;
            default -> 0;
        };
    }

    /** A coordinate synced as two 16-bit halves. */
    public int coordinate(int low, int high) {
        return ((get(high) & 0xFFFF) << 16) | (get(low) & 0xFFFF);
    }

    // ---------------------------------------------------------------- layout

    public int rows() {
        return rows;
    }

    public int playerInvY() {
        return GRID_Y + rows * 18 + 14;
    }

    public int imageWidth() {
        return 176 + PANEL_W + (ship instanceof Shuttle ? NAV_W : 0);
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
        if (ship instanceof Shuttle shuttle && id >= BUTTON_DEST && id < BUTTON_DEST + net.juli2kapo.factoryascent.space.planet.Navigation.Destination.values().length) {
            if (!shuttle.cruising()) shuttle.select(id - BUTTON_DEST);
            return true;
        }
        if (ship instanceof Shuttle shuttle && id == BUTTON_BEACON) {
            if (!shuttle.cruising()) {
                var why = shuttle.selectBeacon(player);
                if (why != null) player.sendOverlayMessage(why.copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            return true;
        }
        if (ship instanceof Shuttle shuttle && id == BUTTON_ENGAGE) {
            var why = shuttle.engage(player);
            if (why != null) player.sendOverlayMessage(why.copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
            else player.closeContainer();
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

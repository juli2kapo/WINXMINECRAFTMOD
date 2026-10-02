package net.juli2kapo.factoryascent.phone.dock;

import net.juli2kapo.factoryascent.capsule.DeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Phone Dock screen: the cradle, the card reader and out tray, and the phone's links. */
public class PhoneDockMenu extends DeviceMenu<PhoneDockBlockEntity> {
    public static final int WIDTH = 196, HEIGHT = 196, INV_Y = 114, INV_X = 18;
    public static final int[][] SLOTS = {{18, 30}, {18, 64}, {18, 88}};
    public static final int D_ENERGY_LO = 0, D_ENERGY_HI = 1, D_CAP_LO = 2, D_CAP_HI = 3, D_RESULT = 4, D_CHARGE = 5,
            D_CHARGING = 6, D_COUNT = 7;
    /** Button ids from here up remove the phone's link with that index. */
    public static final int B_REMOVE = 100;

    PhoneDockMenu(int id, Inventory inventory, PhoneDockBlockEntity be) {
        super(DockContent.PHONE_DOCK_MENU.get(), id, inventory, be, be.getBlockPos(), be.inventory, SLOTS, serverData(be), INV_X, INV_Y);
    }

    private PhoneDockMenu(int id, Inventory inventory, BlockPos pos) {
        super(DockContent.PHONE_DOCK_MENU.get(), id, inventory, clientDevice(inventory, pos, PhoneDockBlockEntity.class), pos,
                clientHandler(inventory, pos, 3), SLOTS, new SimpleContainerData(D_COUNT), INV_X, INV_Y);
    }

    public static PhoneDockMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new PhoneDockMenu(id, inventory, buf.readBlockPos());
    }

    private static ContainerData serverData(PhoneDockBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case D_ENERGY_LO -> be.energyStored() & 0xFFFF;
                    case D_ENERGY_HI -> be.energyStored() >>> 16;
                    case D_CAP_LO -> be.energyCapacity() & 0xFFFF;
                    case D_CAP_HI -> be.energyCapacity() >>> 16;
                    case D_RESULT -> be.result();
                    case D_CHARGE -> be.chargePermille();
                    case D_CHARGING -> be.charging() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return D_COUNT;
            }
        };
    }

    public int energy() {
        return (get(D_ENERGY_LO) & 0xFFFF) | (get(D_ENERGY_HI) << 16);
    }

    public int capacity() {
        return (get(D_CAP_LO) & 0xFFFF) | (get(D_CAP_HI) << 16);
    }

    @Override
    protected boolean button(ServerPlayer player, PhoneDockBlockEntity device, int id) {
        return id >= B_REMOVE && device.removeLink(id - B_REMOVE);
    }
}

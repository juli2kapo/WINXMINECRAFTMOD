package net.juli2kapo.factoryascent.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Mob Releaser screen: nine capsule slots, the release point and a Release All button. */
public class MobReleaserMenu extends DeviceMenu<MobReleaserBlockEntity> {
    public static final int WIDTH = 176, HEIGHT = 186, INV_Y = 104;
    public static final int GRID_X = 12, GRID_Y = 26;
    public static final int D_DISTANCE = 0, D_HEIGHT = 1, D_FILLED = 2, D_LAST = 3, D_MAX_DISTANCE = 4,
            D_ENERGY_LO = 5, D_ENERGY_HI = 6, D_CAP_LO = 7, D_CAP_HI = 8, D_COST = 9, D_NO_POWER = 10, D_COUNT = 11;
    public static final int B_RELEASE = 0, B_FARTHER = 1, B_NEARER = 2, B_UP = 3, B_DOWN = 4;

    MobReleaserMenu(int id, Inventory inventory, MobReleaserBlockEntity be) {
        super(CapsuleContent.MOB_RELEASER_MENU.get(), id, inventory, be, be.getBlockPos(), be.inventory, grid(), serverData(be), 8, INV_Y);
    }

    private MobReleaserMenu(int id, Inventory inventory, BlockPos pos) {
        super(CapsuleContent.MOB_RELEASER_MENU.get(), id, inventory, clientDevice(inventory, pos, MobReleaserBlockEntity.class), pos,
                clientHandler(inventory, pos, MobReleaserBlockEntity.SLOTS), grid(), new SimpleContainerData(D_COUNT), 8, INV_Y);
    }

    public static MobReleaserMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new MobReleaserMenu(id, inventory, buf.readBlockPos());
    }

    private static int[][] grid() {
        int[][] out = new int[MobReleaserBlockEntity.SLOTS][];
        for (int i = 0; i < out.length; i++) out[i] = new int[] {GRID_X + 18 * (i % 3), GRID_Y + 18 * (i / 3)};
        return out;
    }

    private static ContainerData serverData(MobReleaserBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case D_DISTANCE -> be.distance();
                    case D_HEIGHT -> be.height();
                    case D_FILLED -> be.filled();
                    case D_LAST -> be.lastReleased();
                    case D_MAX_DISTANCE -> CapsuleConfig.RELEASER_MAX_DISTANCE.get();
                    case D_ENERGY_LO -> be.energyStored() & 0xFFFF;
                    case D_ENERGY_HI -> be.energyStored() >>> 16;
                    case D_CAP_LO -> be.energyCapacity() & 0xFFFF;
                    case D_CAP_HI -> be.energyCapacity() >>> 16;
                    case D_COST -> Math.min(32_000, MobReleaserBlockEntity.costPerMob() / 10);
                    case D_NO_POWER -> be.noPower() ? 1 : 0;
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

    /** FE per mob (sent in tens of FE to fit a short). */
    public int costPerMob() {
        return get(D_COST) * 10;
    }

    @Override
    protected boolean button(ServerPlayer player, MobReleaserBlockEntity device, int id) {
        switch (id) {
            case B_RELEASE -> device.releaseAll(player.level());
            case B_FARTHER -> device.setDistance(device.distance() + 1);
            case B_NEARER -> device.setDistance(device.distance() - 1);
            case B_UP -> device.setHeight(device.height() + 1);
            case B_DOWN -> device.setHeight(device.height() - 1);
            default -> {
                return false;
            }
        }
        return true;
    }
}

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
    public static final int D_DISTANCE = 0, D_HEIGHT = 1, D_FILLED = 2, D_LAST = 3, D_MAX_DISTANCE = 4, D_COUNT = 5;
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

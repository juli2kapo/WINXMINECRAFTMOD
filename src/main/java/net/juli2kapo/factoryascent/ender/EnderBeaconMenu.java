package net.juli2kapo.factoryascent.ender;

import net.juli2kapo.factoryascent.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Ender Beacon screen: the pearl slot, a name field (renamed through a
 * {@code ScreenPayloads.BeaconRename} packet) and the recall rules from the server config.
 */
public class EnderBeaconMenu extends AbstractContainerMenu {
    public static final int DATA_MAY_EDIT = 0, DATA_RECALL = 1, DATA_COOLDOWN = 2, DATA_CROSS = 3, DATA_COUNT = 4;

    private final @Nullable EnderBeaconBlockEntity beacon;
    private final BlockPos pos;
    private final ContainerData data;
    private final String ownerName;
    private final String name;

    /** Server side. */
    EnderBeaconMenu(int id, Inventory inventory, EnderBeaconBlockEntity beacon) {
        this(id, inventory, beacon, beacon.getBlockPos(), PearlSlot.chamber(beacon::hasPearl, beacon::insertPearl, () -> {}),
                serverData(beacon, inventory.player), "", beacon.name());
    }

    private EnderBeaconMenu(int id, Inventory inventory, @Nullable EnderBeaconBlockEntity beacon, BlockPos pos, Container chamber,
                            ContainerData data, String ownerName, String name) {
        super(EnderContent.ENDER_BEACON_MENU.get(), id);
        this.beacon = beacon;
        this.pos = pos;
        this.data = data;
        this.ownerName = ownerName;
        this.name = name;
        addSlot(new PearlSlot(chamber, ChamberMenus.PEARL_X, ChamberMenus.PEARL_Y));
        addStandardInventorySlots(inventory, ChamberMenus.INV_X, ChamberMenus.PLAYER_INV_Y);
        addDataSlots(data);
    }

    private static ContainerData serverData(EnderBeaconBlockEntity beacon, Player viewer) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_MAY_EDIT -> beacon.mayLink(viewer.getUUID()) ? 1 : 0;
                    case DATA_RECALL -> Config.RECALL_SECONDS.get();
                    case DATA_COOLDOWN -> Config.RECALL_COOLDOWN_SECONDS.get();
                    case DATA_CROSS -> Config.RECALL_CROSS_DIMENSION.get() ? 1 : 0;
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

    static void open(ServerPlayer player, EnderBeaconBlockEntity beacon) {
        String owner = ChamberMenus.ownerName(player.level().getServer(), beacon.owner());
        player.openMenu(beacon, buf -> {
            buf.writeBlockPos(beacon.getBlockPos());
            buf.writeUtf(owner, 64);
            buf.writeUtf(beacon.name(), 64);
        });
    }

    public static EnderBeaconMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String owner = buf.readUtf(64);
        String name = buf.readUtf(64);
        EnderBeaconBlockEntity be = inventory.player.level().getBlockEntity(pos) instanceof EnderBeaconBlockEntity b ? b : null;
        return new EnderBeaconMenu(id, inventory, be, pos, PearlSlot.clientChamber(), new SimpleContainerData(DATA_COUNT), owner, name);
    }

    public BlockPos pos() {
        return pos;
    }

    public String ownerName() {
        return ownerName;
    }

    /** The name when the screen opened (the client block entity has later renames). */
    public String initialName() {
        return name;
    }

    public @Nullable EnderBeaconBlockEntity beacon() {
        return beacon;
    }

    public boolean hasPearl() {
        return getSlot(0).hasItem();
    }

    public boolean mayEdit() {
        return data.get(DATA_MAY_EDIT) != 0;
    }

    public int recallSeconds() {
        return data.get(DATA_RECALL);
    }

    public int cooldownSeconds() {
        return data.get(DATA_COOLDOWN);
    }

    public boolean crossDimension() {
        return data.get(DATA_CROSS) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return beacon != null && Container.stillValidBlockEntity(beacon, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ChamberMenus.quickMove(this, player, index, (stack, range) -> moveItemStackTo(stack, range[0], range[1], false));
    }
}

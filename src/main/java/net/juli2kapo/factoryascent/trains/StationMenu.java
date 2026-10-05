package net.juli2kapo.factoryascent.trains;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The Train Station's settings screen: when to stop, how long, load or unload, and what is docked. */
public class StationMenu extends AbstractContainerMenu {
    public static final int BUTTON_STOP = 0, BUTTON_DWELL_DOWN = 1, BUTTON_DWELL_UP = 2, BUTTON_MODE = 3;
    public static final int DATA = 8;

    private final @Nullable StationBlockEntity station;
    private final BlockPos pos;
    private final ContainerData data;

    public StationMenu(int id, Inventory inventory, StationBlockEntity station, ContainerData data) {
        super(TrainContent.STATION_MENU.get(), id);
        this.station = station;
        this.pos = station.getBlockPos();
        this.data = data;
        addDataSlots(data);
    }

    private StationMenu(int id, BlockPos pos) {
        super(TrainContent.STATION_MENU.get(), id);
        this.station = null;
        this.pos = pos;
        this.data = new SimpleContainerData(DATA);
        addDataSlots(data);
    }

    public static StationMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new StationMenu(id, buf.readBlockPos());
    }

    public int get(int index) {
        return data.get(index);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (station == null || id < 0 || id > BUTTON_MODE) return false;
        station.press(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return station == null || !station.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64;
    }
}

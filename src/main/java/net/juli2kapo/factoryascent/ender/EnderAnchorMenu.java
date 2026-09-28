package net.juli2kapo.factoryascent.ender;

import net.juli2kapo.factoryascent.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * The Ender Anchor screen: the pearl slot, an on/off switch (owner only), and read-outs (area,
 * anchors used by the owner). Values travel as container data; the owner's name is sent once
 * when the screen opens.
 */
public class EnderAnchorMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE = 0;
    public static final int DATA_ENABLED = 0, DATA_RUNNING = 1, DATA_RADIUS = 2, DATA_USED = 3, DATA_ALLOWED = 4,
            DATA_MAY_CONTROL = 5, DATA_COUNT = 6;

    private final @Nullable EnderAnchorBlockEntity anchor;
    private final BlockPos pos;
    private final ContainerData data;
    private final String ownerName;

    /** Server side. */
    EnderAnchorMenu(int id, Inventory inventory, EnderAnchorBlockEntity anchor) {
        this(id, inventory, anchor, anchor.getBlockPos(), PearlSlot.chamber(anchor::hasPearl, anchor::insertPearl, () -> {}),
                serverData(anchor, inventory.player), "");
    }

    private EnderAnchorMenu(int id, Inventory inventory, @Nullable EnderAnchorBlockEntity anchor, BlockPos pos, Container chamber,
                            ContainerData data, String ownerName) {
        super(EnderContent.ENDER_ANCHOR_MENU.get(), id);
        this.anchor = anchor;
        this.pos = pos;
        this.data = data;
        this.ownerName = ownerName;
        addSlot(new PearlSlot(chamber, ChamberMenus.PEARL_X, ChamberMenus.PEARL_Y));
        addStandardInventorySlots(inventory, ChamberMenus.INV_X, ChamberMenus.PLAYER_INV_Y);
        addDataSlots(data);
    }

    private static ContainerData serverData(EnderAnchorBlockEntity anchor, Player viewer) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_ENABLED -> anchor.isEnabled() ? 1 : 0;
                    case DATA_RUNNING -> anchor.isRunning() ? 1 : 0;
                    case DATA_RADIUS -> Config.ANCHOR_RADIUS.get();
                    case DATA_USED -> anchor.getLevel() instanceof net.minecraft.server.level.ServerLevel server && anchor.owner() != null
                            ? AnchorLedger.get(server.getServer()).countFor(anchor.owner()) : 0;
                    case DATA_ALLOWED -> Config.ANCHORS_PER_PLAYER.get();
                    case DATA_MAY_CONTROL -> anchor.mayControl(viewer) ? 1 : 0;
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

    /** Opens the screen, sending the position and the owner's name along. */
    static void open(ServerPlayer player, EnderAnchorBlockEntity anchor) {
        String owner = ChamberMenus.ownerName(player.level().getServer(), anchor.owner());
        player.openMenu(anchor, buf -> {
            buf.writeBlockPos(anchor.getBlockPos());
            buf.writeUtf(owner, 64);
        });
    }

    /** Client side: the server wrote the position and the owner's name. */
    public static EnderAnchorMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String owner = buf.readUtf(64);
        EnderAnchorBlockEntity be = inventory.player.level().getBlockEntity(pos) instanceof EnderAnchorBlockEntity a ? a : null;
        return new EnderAnchorMenu(id, inventory, be, pos, PearlSlot.clientChamber(), new SimpleContainerData(DATA_COUNT), owner);
    }

    public BlockPos pos() {
        return pos;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean hasPearl() {
        return getSlot(0).hasItem();
    }

    public boolean enabled() {
        return data.get(DATA_ENABLED) != 0;
    }

    public boolean running() {
        return data.get(DATA_RUNNING) != 0;
    }

    public int radius() {
        return data.get(DATA_RADIUS);
    }

    public int anchorsUsed() {
        return data.get(DATA_USED);
    }

    /** 0 = no limit. */
    public int anchorsAllowed() {
        return data.get(DATA_ALLOWED);
    }

    public boolean mayControl() {
        return data.get(DATA_MAY_CONTROL) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != BUTTON_TOGGLE || anchor == null) return false;
        if (!anchor.mayControl(player)) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.anchor_not_yours").withStyle(ChatFormatting.RED));
            return false;
        }
        anchor.setEnabled(!anchor.isEnabled());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return anchor != null && Container.stillValidBlockEntity(anchor, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ChamberMenus.quickMove(this, player, index, (stack, range) -> moveItemStackTo(stack, range[0], range[1], false));
    }
}

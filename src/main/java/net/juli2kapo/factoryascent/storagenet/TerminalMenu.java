package net.juli2kapo.factoryascent.storagenet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The terminal: a view of the whole network plus the player inventory. The network grid is not
 * made of slots. The server pushes the item list to the client with {@link TerminalSyncPayload}
 * (a full snapshot on open, then only the changes, checked every {@link #SYNC_INTERVAL} ticks);
 * the client sends clicks back as {@link TerminalClickPayload} and the server does the moving.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int WIDTH = 195, HEIGHT = 232;
    public static final int COLS = 9, ROWS = 6;
    public static final int GRID_X = 8, GRID_Y = 18;
    public static final int PLAYER_INV_Y = 150;
    public static final int SYNC_INTERVAL = 10;

    private final StorageTerminalBlockEntity terminal;
    private final Player player;

    // ---- server-side sync state
    private final Map<ItemResource, Long> sent = new HashMap<>();
    private boolean sentOnce;
    private int cooldown;
    private long lastVersion = -1;
    private StorageNet lastNet;
    private Stats lastStats;

    // ---- client-side view
    private final Map<ItemResource, Long> clientItems = new LinkedHashMap<>();
    private Stats clientStats = new Stats(StorageNet.Status.NO_CONTROLLER.ordinal(), 0, 0, 0, 0);
    private int revision;

    private record Stats(int status, long used, long capacity, int types, int maxTypes) {}

    public TerminalMenu(int id, Inventory playerInventory, StorageTerminalBlockEntity terminal) {
        super(StorageContent.TERMINAL_MENU.get(), id);
        this.terminal = terminal;
        this.player = playerInventory.player;
        addStandardInventorySlots(playerInventory, 8, PLAYER_INV_Y);
    }

    public static TerminalMenu fromNetwork(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal) {
            return new TerminalMenu(id, playerInventory, terminal);
        }
        throw new IllegalStateException("No storage terminal at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(terminal, player);
    }

    // ================================================================ server: sync

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        if (player instanceof ServerPlayer sp) {
            sentOnce = false;
            sync(sp, true);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp) sync(sp, false);
    }

    /** Sends the item list if anything changed. Unforced calls only look every {@link #SYNC_INTERVAL} ticks. */
    private void sync(ServerPlayer sp, boolean force) {
        if (!force && ++cooldown < SYNC_INTERVAL) return;
        cooldown = 0;
        StorageNet net = terminal.network();
        boolean online = net != null && net.isOnline();
        Stats stats = new Stats(net == null ? StorageNet.Status.NO_CONTROLLER.ordinal() : net.status().ordinal(),
                online ? net.usedItems() : 0, online ? net.capacityItems() : 0,
                online ? net.contents().size() : 0, online ? net.typeCapacity() : 0);
        long version = StorageDriveBlockEntity.version();
        if (sentOnce && net == lastNet && version == lastVersion && stats.equals(lastStats)) return;

        Map<ItemResource, Long> current = new HashMap<>();
        if (online) {
            for (StorageNet.Stored s : net.contents()) current.merge(s.resource(), s.count(), Long::sum);
        }
        List<StorageNet.Stored> changes = new ArrayList<>();
        boolean full = !sentOnce;
        if (full) {
            current.forEach((r, n) -> changes.add(new StorageNet.Stored(r, n)));
        } else {
            current.forEach((r, n) -> {
                if (!n.equals(sent.get(r))) changes.add(new StorageNet.Stored(r, n));
            });
            sent.forEach((r, n) -> {
                if (!current.containsKey(r)) changes.add(new StorageNet.Stored(r, 0));
            });
        }
        if (full || !changes.isEmpty() || !stats.equals(lastStats)) {
            int max = TerminalSyncPayload.MAX_ENTRIES;
            int i = 0;
            do {
                List<StorageNet.Stored> part = changes.subList(i, Math.min(changes.size(), i + max));
                PacketDistributor.sendToPlayer(sp, new TerminalSyncPayload(containerId, full && i == 0, List.copyOf(part),
                        stats.status(), stats.used(), stats.capacity(), stats.types(), stats.maxTypes()));
                i += max;
            } while (i < changes.size());
        }
        sent.clear();
        sent.putAll(current);
        sentOnce = true;
        lastNet = net;
        lastVersion = version;
        lastStats = stats;
    }

    // ================================================================ server: clicks

    /** Carries out a grid click. Everything is re-checked here; the client's view is never trusted. */
    void handleClick(ServerPlayer sp, ItemResource resource, int action) {
        StorageNet net = terminal.network();
        if (net == null || !net.isOnline()) return;
        ItemStack carried = getCarried();
        switch (action) {
            case TerminalClickPayload.TAKE_STACK, TerminalClickPayload.TAKE_HALF -> {
                if (resource.isEmpty()) return;
                if (!carried.isEmpty() && !resource.matches(carried)) return;
                int max = resource.getMaxStackSize();
                int room = max - carried.getCount();
                long stored = net.count(resource);
                if (room <= 0 || stored <= 0) return;
                int want = (int) Math.min(room, stored);
                if (action == TerminalClickPayload.TAKE_HALF) want = (int) Math.min(room, (Math.min(stored, max) + 1) / 2);
                int got;
                try (Transaction tx = Transaction.openRoot()) {
                    got = net.extract(resource, want, tx);
                    tx.commit();
                }
                if (got <= 0) return;
                if (carried.isEmpty()) {
                    setCarried(resource.toStack(got));
                } else {
                    carried.grow(got);
                    setCarried(carried);
                }
            }
            case TerminalClickPayload.TAKE_TO_INVENTORY -> {
                if (resource.isEmpty()) return;
                long stored = net.count(resource);
                if (stored <= 0) return;
                int want = (int) Math.min(resource.getMaxStackSize(), stored);
                PlayerInventoryWrapper inv = PlayerInventoryWrapper.of(sp);
                int fits;
                try (Transaction sim = Transaction.openRoot()) {
                    fits = inv.insert(resource, want, sim);
                }
                if (fits <= 0) return;
                try (Transaction tx = Transaction.openRoot()) {
                    int got = net.extract(resource, fits, tx);
                    if (got > 0 && inv.insert(resource, got, tx) == got) tx.commit();
                }
            }
            case TerminalClickPayload.INSERT_CARRIED, TerminalClickPayload.INSERT_ONE -> {
                if (carried.isEmpty()) return;
                int amount = action == TerminalClickPayload.INSERT_ONE ? 1 : carried.getCount();
                int inserted = insert(net, carried, amount);
                if (inserted <= 0) return;
                carried.shrink(inserted);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            default -> {
                return;
            }
        }
        broadcastChanges();
        sync(sp, true);
    }

    private static int insert(StorageNet net, ItemStack stack, int amount) {
        ItemResource resource = ItemResource.of(stack);
        if (resource.isEmpty() || amount <= 0) return 0;
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = net.insert(resource, Math.min(amount, stack.getCount()), tx);
            tx.commit();
            return inserted;
        }
    }

    /** Shift-click in the player inventory: the whole stack goes into the network (server only). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer sp)) return ItemStack.EMPTY;
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        StorageNet net = terminal.network();
        if (net == null || !net.isOnline()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        int inserted = insert(net, stack, stack.getCount());
        if (inserted > 0) {
            stack.shrink(inserted);
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            slot.setChanged();
            sync(sp, true);
        }
        return ItemStack.EMPTY;
    }

    // ================================================================ client

    void applySync(TerminalSyncPayload payload) {
        if (payload.full()) clientItems.clear();
        for (StorageNet.Stored s : payload.changes()) {
            if (s.count() <= 0) {
                clientItems.remove(s.resource());
            } else {
                clientItems.put(s.resource(), s.count());
            }
        }
        clientStats = new Stats(payload.status(), payload.used(), payload.capacity(), payload.types(), payload.maxTypes());
        revision++;
    }

    /** Bumped on every sync so the screen knows to rebuild its sorted list. */
    public int revision() {
        return revision;
    }

    public Map<ItemResource, Long> clientItems() {
        return clientItems;
    }

    public StorageNet.Status clientStatus() {
        StorageNet.Status[] all = StorageNet.Status.values();
        int s = clientStats.status();
        return s >= 0 && s < all.length ? all[s] : StorageNet.Status.NO_CONTROLLER;
    }

    public long clientUsed() {
        return clientStats.used();
    }

    public long clientCapacity() {
        return clientStats.capacity();
    }

    public int clientTypes() {
        return clientStats.types();
    }

    public int clientMaxTypes() {
        return clientStats.maxTypes();
    }
}

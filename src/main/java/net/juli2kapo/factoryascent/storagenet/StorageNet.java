package net.juli2kapo.factoryascent.storagenet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * One connected storage network: every cable and device touching each other. Built by
 * {@link StorageNetManager} and thrown away whenever its shape changes. The items live in the
 * cells of its drives; this class only routes inserts and extracts across them.
 */
public final class StorageNet {
    public enum Status {
        ONLINE, NO_CONTROLLER, MULTIPLE_CONTROLLERS, NO_POWER;

        public String key() {
            return "gui.factoryascent.storage.status." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One item type in the whole network. */
    public record Stored(ItemResource resource, long count) {}

    private final Set<BlockPos> members;
    private final List<StorageControllerBlockEntity> controllers;
    /** Drives in network order: breadth-first from the controller. Items fill them in this order. */
    private final List<StorageDriveBlockEntity> drives;
    private final int devices;
    boolean valid = true;

    private long cachedVersion = -1;
    private List<Stored> cachedContents = List.of();

    StorageNet(Set<BlockPos> members, List<StorageControllerBlockEntity> controllers,
               List<StorageDriveBlockEntity> drives, int devices) {
        this.members = members;
        this.controllers = controllers;
        this.drives = drives;
        this.devices = devices;
    }

    public Set<BlockPos> members() {
        return members;
    }

    public boolean isValid() {
        return valid;
    }

    /** Devices that draw power: everything except cables and the controller itself. */
    public int deviceCount() {
        return devices;
    }

    public List<StorageDriveBlockEntity> drives() {
        return drives;
    }

    public int controllerCount() {
        return controllers.size();
    }

    public Status status() {
        if (controllers.isEmpty()) return Status.NO_CONTROLLER;
        if (controllers.size() > 1) return Status.MULTIPLE_CONTROLLERS;
        StorageControllerBlockEntity c = controllers.get(0);
        return !c.isRemoved() && c.isPowered() ? Status.ONLINE : Status.NO_POWER;
    }

    public boolean isOnline() {
        return status() == Status.ONLINE;
    }

    // ---------------------------------------------------------------- contents

    /** Every item type stored, merged across cells, in drive order. Cached until any cell changes. */
    public List<Stored> contents() {
        long version = StorageDriveBlockEntity.version();
        if (version != cachedVersion) {
            Map<ItemResource, Long> merged = new LinkedHashMap<>();
            forEachCell(cell -> {
                for (CellContents.Entry e : StorageCellItem.contents(cell).entries()) {
                    merged.merge(e.resource(), (long) e.count(), Long::sum);
                }
            });
            List<Stored> list = new ArrayList<>(merged.size());
            merged.forEach((r, n) -> list.add(new Stored(r, n)));
            cachedContents = List.copyOf(list);
            cachedVersion = version;
        }
        return cachedContents;
    }

    public long count(ItemResource resource) {
        long n = 0;
        for (Stored s : contents()) {
            if (s.resource().equals(resource)) n += s.count();
        }
        return n;
    }

    public long usedItems() {
        long[] n = {0};
        forEachCell(cell -> n[0] += StorageCellItem.contents(cell).total());
        return n[0];
    }

    public long capacityItems() {
        long[] n = {0};
        forEachCell(cell -> n[0] += ((StorageCellItem) cell.getItem()).capacity());
        return n[0];
    }

    public int typeCapacity() {
        int[] n = {0};
        forEachCell(cell -> n[0] += ((StorageCellItem) cell.getItem()).maxTypes());
        return n[0];
    }

    private void forEachCell(java.util.function.Consumer<ItemStack> action) {
        for (StorageDriveBlockEntity drive : drives) {
            if (drive.isRemoved()) continue;
            for (int i = 0; i < StorageDriveBlockEntity.SLOTS; i++) {
                ItemStack cell = drive.cell(i);
                if (StorageCellItem.isCell(cell)) action.accept(cell);
            }
        }
    }

    // ---------------------------------------------------------------- insert / extract

    /**
     * Inserts up to {@code amount} into the cells, in drive order: first topping up cells that
     * already hold this type, then starting it in cells with a free type slot. Ignores power;
     * callers check {@link #isOnline()}.
     */
    public int insert(ItemResource resource, int amount, TransactionContext tx) {
        if (amount <= 0 || !StorageCellItem.canStore(resource)) return 0;
        int inserted = 0;
        for (int pass = 0; pass < 2 && inserted < amount; pass++) {
            boolean newTypes = pass == 1;
            for (StorageDriveBlockEntity drive : drives) {
                if (drive.isRemoved()) continue;
                for (int i = 0; i < StorageDriveBlockEntity.SLOTS && inserted < amount; i++) {
                    inserted += drive.insertIntoCell(i, resource, amount - inserted, newTypes, tx);
                }
                if (inserted >= amount) break;
            }
        }
        return inserted;
    }

    /** Extracts up to {@code amount} of the resource from any cells holding it. */
    public int extract(ItemResource resource, int amount, TransactionContext tx) {
        if (amount <= 0 || resource.isEmpty()) return 0;
        int extracted = 0;
        for (int d = drives.size() - 1; d >= 0 && extracted < amount; d--) {
            StorageDriveBlockEntity drive = drives.get(d);
            if (drive.isRemoved()) continue;
            for (int i = StorageDriveBlockEntity.SLOTS - 1; i >= 0 && extracted < amount; i--) {
                extracted += drive.extractFromCell(i, resource, amount - extracted, tx);
            }
        }
        return extracted;
    }
}

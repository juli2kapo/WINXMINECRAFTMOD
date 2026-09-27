package net.juli2kapo.factoryascent.storagenet;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Holds four storage cells. Every change to a cell (through the network) goes through a
 * {@link SnapshotJournal}, so aborted transactions leave the cells untouched. Breaking the drive
 * drops the cells with their contents.
 */
public class StorageDriveBlockEntity extends StorageNodeBlockEntity implements MenuProvider {
    public static final int SLOTS = 4;

    /** Bumped on every change to any drive's cells; lets networks and terminals cache contents. */
    private static long version;

    private ItemStack[] cells = new ItemStack[SLOTS];
    private final Journal journal = new Journal();
    private final CellSlots slots = new CellSlots();

    public StorageDriveBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.DRIVE_BE.get(), pos, state);
        Arrays.fill(cells, ItemStack.EMPTY);
    }

    public static long version() {
        return version;
    }

    private static void bump() {
        version++;
    }

    public ItemStack cell(int slot) {
        return cells[slot];
    }

    /** Direct (non-transactional) replacement, used by the drive GUI and tests. */
    public void setCell(int slot, ItemStack stack) {
        cells[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        bump();
        setChanged();
    }

    /** The four cell slots, for the drive GUI. */
    public CellSlots cellSlots() {
        return slots;
    }

    int insertIntoCell(int slot, ItemResource resource, int amount, boolean allowNewType, TransactionContext tx) {
        ItemStack cell = cells[slot];
        int n = Math.min(amount, StorageCellItem.room(cell, resource, allowNewType));
        if (n <= 0) return 0;
        journal.updateSnapshots(tx);
        replace(slot, StorageCellItem.contents(cell).add(resource, n));
        return n;
    }

    int extractFromCell(int slot, ItemResource resource, int amount, TransactionContext tx) {
        ItemStack cell = cells[slot];
        if (!StorageCellItem.isCell(cell)) return 0;
        CellContents contents = StorageCellItem.contents(cell);
        int n = Math.min(amount, contents.count(resource));
        if (n <= 0) return 0;
        journal.updateSnapshots(tx);
        replace(slot, contents.add(resource, -n));
        return n;
    }

    /** Cells are treated as immutable: every change installs a new stack, so snapshots stay valid. */
    private void replace(int slot, CellContents contents) {
        ItemStack copy = cells[slot].copy();
        StorageCellItem.setContents(copy, contents);
        cells[slot] = copy;
        bump();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DriveMenu(id, inventory, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) return;
        for (int i = 0; i < SLOTS; i++) {
            if (!cells[i].isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cells[i]);
                cells[i] = ItemStack.EMPTY;
            }
        }
        bump();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < SLOTS; i++) {
            cells[i] = input.read("cell" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
        bump();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < SLOTS; i++) {
            if (!cells[i].isEmpty()) output.store("cell" + i, ItemStack.CODEC, cells[i]);
        }
    }

    private final class Journal extends SnapshotJournal<ItemStack[]> {
        @Override
        protected ItemStack[] createSnapshot() {
            return cells.clone();
        }

        @Override
        protected void revertToSnapshot(ItemStack[] snapshot) {
            cells = snapshot;
            bump();
        }

        @Override
        protected void onRootCommit(ItemStack[] originalState) {
            setChanged();
        }
    }

    /** The drive's four slots as a resource handler: one cell per slot, only cells accepted. */
    public final class CellSlots implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return SLOTS;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(cells[index]);
        }

        @Override
        public long getAmountAsLong(int index) {
            return cells[index].getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return 1;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return resource.getItem() instanceof StorageCellItem;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !cells[index].isEmpty() || !isValid(index, resource)) return 0;
            journal.updateSnapshots(tx);
            cells[index] = resource.toStack(1);
            bump();
            return 1;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || cells[index].isEmpty() || !resource.matches(cells[index])) return 0;
            journal.updateSnapshots(tx);
            cells[index] = ItemStack.EMPTY;
            bump();
            return 1;
        }

        /** Direct setter for {@link net.neoforged.neoforge.transfer.item.ResourceHandlerSlot}. */
        public void set(int index, ItemResource resource, int amount) {
            setCell(index, resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(1));
        }
    }
}

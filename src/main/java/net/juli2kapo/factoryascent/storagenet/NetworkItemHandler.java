package net.juli2kapo.factoryascent.storagenet;

import java.util.List;
import java.util.function.Supplier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The whole storage network as one item handler. Index {@code i < n} is the i-th stored item type
 * (amounts can exceed a stack); the last index is always empty and accepts any new item. An
 * offline network (no power, no or several controllers) shows no slots and accepts nothing.
 */
public final class NetworkItemHandler implements ResourceHandler<ItemResource> {
    private final Supplier<@Nullable StorageNet> network;

    public NetworkItemHandler(Supplier<@Nullable StorageNet> network) {
        this.network = network;
    }

    private @Nullable StorageNet online() {
        StorageNet net = network.get();
        return net != null && net.isOnline() ? net : null;
    }

    private List<StorageNet.Stored> contents() {
        StorageNet net = online();
        return net == null ? List.of() : net.contents();
    }

    @Override
    public int size() {
        return online() == null ? 0 : contents().size() + 1;
    }

    @Override
    public ItemResource getResource(int index) {
        List<StorageNet.Stored> list = contents();
        return index >= 0 && index < list.size() ? list.get(index).resource() : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        List<StorageNet.Stored> list = contents();
        return index >= 0 && index < list.size() ? list.get(index).count() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        StorageNet net = online();
        if (net == null) return 0;
        return getAmountAsLong(index) + Math.max(0, net.capacityItems() - net.usedItems());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return StorageCellItem.canStore(resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        List<StorageNet.Stored> list = contents();
        boolean slotFits = index == list.size() || (index >= 0 && index < list.size() && list.get(index).resource().equals(resource));
        return slotFits ? insert(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        StorageNet net = online();
        return net == null ? 0 : net.insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!getResource(index).equals(resource)) return 0;
        return extract(resource, amount, transaction);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        StorageNet net = online();
        return net == null ? 0 : net.extract(resource, amount, transaction);
    }
}

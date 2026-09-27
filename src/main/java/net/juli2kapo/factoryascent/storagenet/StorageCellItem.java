package net.juli2kapo.factoryascent.storagenet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A portable storage cell. It holds up to {@link #capacity()} items in total, of at most
 * {@link #maxTypes()} different types; the contents live in the {@code cell_contents} component.
 */
public class StorageCellItem extends Item {
    public static final int MAX_TYPES = 63;

    private final int capacity;
    private final int maxTypes;

    public StorageCellItem(int capacity, int maxTypes, Properties properties) {
        super(properties);
        this.capacity = capacity;
        this.maxTypes = maxTypes;
    }

    public int capacity() {
        return capacity;
    }

    public int maxTypes() {
        return maxTypes;
    }

    public static boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof StorageCellItem;
    }

    public static CellContents contents(ItemStack stack) {
        return stack.getOrDefault(StorageContent.CELL_CONTENTS.get(), CellContents.EMPTY);
    }

    /** Writes contents onto the stack; empty contents remove the component so empty cells equal new ones. */
    public static void setContents(ItemStack stack, CellContents contents) {
        if (contents.isEmpty()) {
            stack.remove(StorageContent.CELL_CONTENTS.get());
        } else {
            stack.set(StorageContent.CELL_CONTENTS.get(), contents);
        }
    }

    /**
     * How many of {@code resource} this cell could still take (ignoring any amount limit). With
     * {@code allowNewType} false, a cell that does not already hold the type takes none.
     */
    public static int room(ItemStack cell, ItemResource resource, boolean allowNewType) {
        if (!(cell.getItem() instanceof StorageCellItem item) || !canStore(resource)) return 0;
        CellContents c = contents(cell);
        int free = item.capacity - c.total();
        if (free <= 0) return 0;
        if (c.contains(resource)) return free;
        return allowNewType && c.types() < item.maxTypes ? free : 0;
    }

    /** Non-empty cells cannot go into other cells (no nesting). */
    public static boolean canStore(ItemResource resource) {
        if (resource.isEmpty()) return false;
        return !(resource.getItem() instanceof StorageCellItem) || !resource.getComponents().has(StorageContent.CELL_CONTENTS.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CellContents c = contents(stack);
        tooltip.accept(Component.translatable("tooltip.factoryascent.storage_cell.used",
                StorageFormat.compact(c.total()), StorageFormat.compact(capacity)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.storage_cell.types", c.types(), maxTypes)
                .withStyle(ChatFormatting.GRAY));
        if (c.isEmpty()) return;
        List<CellContents.Entry> sorted = new ArrayList<>(c.entries());
        sorted.sort(Comparator.comparingInt(CellContents.Entry::count).reversed());
        int shown = Math.min(5, sorted.size());
        for (int i = 0; i < shown; i++) {
            CellContents.Entry e = sorted.get(i);
            tooltip.accept(Component.literal("  " + e.count() + "x ").append(e.resource().getHoverName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (sorted.size() > shown) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.storage_cell.more", sorted.size() - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

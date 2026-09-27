package net.juli2kapo.factoryascent.storagenet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What a storage cell holds: a list of item types with counts, kept in insertion order. Immutable,
 * stored on the cell item as a data component, so a cell carries its items wherever it goes.
 */
public record CellContents(List<Entry> entries) {
    public static final CellContents EMPTY = new CellContents(List.of());

    public record Entry(ItemResource resource, int count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Entry::resource),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Entry::count)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::resource,
                ByteBufCodecs.VAR_INT, Entry::count,
                Entry::new);
    }

    public static final Codec<CellContents> CODEC = Entry.CODEC.listOf()
            .xmap(list -> new CellContents(List.copyOf(list.stream().filter(e -> !e.resource().isEmpty()).toList())),
                    CellContents::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, CellContents> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(l -> new CellContents(List.copyOf(l)), CellContents::entries);

    public CellContents {
        entries = List.copyOf(entries);
    }

    public int total() {
        int sum = 0;
        for (Entry e : entries) sum += e.count();
        return sum;
    }

    public int types() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int count(ItemResource resource) {
        for (Entry e : entries) {
            if (e.resource().equals(resource)) return e.count();
        }
        return 0;
    }

    public boolean contains(ItemResource resource) {
        return count(resource) > 0;
    }

    /** A copy with {@code delta} more (or fewer, when negative) of the resource; empty entries vanish. */
    public CellContents add(ItemResource resource, int delta) {
        List<Entry> list = new ArrayList<>(entries.size() + 1);
        boolean found = false;
        for (Entry e : entries) {
            if (e.resource().equals(resource)) {
                found = true;
                int n = e.count() + delta;
                if (n > 0) list.add(new Entry(resource, n));
            } else {
                list.add(e);
            }
        }
        if (!found && delta > 0) list.add(new Entry(resource, delta));
        return list.isEmpty() ? EMPTY : new CellContents(list);
    }
}

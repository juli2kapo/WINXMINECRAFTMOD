package net.juli2kapo.factoryascent.ender;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every placed Ender Anchor and who placed it, for the per-player limit and ticket validation. */
public final class AnchorLedger extends SavedData {
    record Entry(UUID owner, GlobalPos pos) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
        ).apply(i, Entry::new));
    }

    private static final Codec<AnchorLedger> CODEC = Entry.CODEC.listOf()
            .xmap(AnchorLedger::new, ledger -> ledger.entries);
    private static final SavedDataType<AnchorLedger> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "ender_anchors"), () -> new AnchorLedger(List.of()), CODEC);

    private final List<Entry> entries;

    private AnchorLedger(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public static AnchorLedger get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public int countFor(UUID owner) {
        return (int) entries.stream().filter(e -> e.owner().equals(owner)).count();
    }

    public boolean contains(GlobalPos pos) {
        return entries.stream().anyMatch(e -> e.pos().equals(pos));
    }

    public void add(UUID owner, GlobalPos pos) {
        if (!contains(pos)) {
            entries.add(new Entry(owner, pos));
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (entries.removeIf(e -> e.pos().equals(pos))) setDirty();
    }
}

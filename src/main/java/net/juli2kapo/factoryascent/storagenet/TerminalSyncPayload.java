package net.juli2kapo.factoryascent.storagenet;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Server → client: what an open terminal shows. {@code full} replaces the client's list (sent on
 * open); otherwise {@code changes} only lists item types whose count changed, count 0 meaning
 * gone. Status and usage figures ride along every time.
 */
public record TerminalSyncPayload(int containerId, boolean full, List<StorageNet.Stored> changes,
                                  int status, long used, long capacity, int types, int maxTypes)
        implements CustomPacketPayload {
    /** Hard cap per packet so a huge network can never overflow the packet size limit. */
    public static final int MAX_ENTRIES = 4096;

    public static final Type<TerminalSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "terminal_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(TerminalSyncPayload::write, TerminalSyncPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBoolean(full);
        buf.writeVarInt(changes.size());
        for (StorageNet.Stored s : changes) {
            ItemResource.STREAM_CODEC.encode(buf, s.resource());
            buf.writeVarLong(s.count());
        }
        buf.writeVarInt(status);
        buf.writeVarLong(used);
        buf.writeVarLong(capacity);
        buf.writeVarInt(types);
        buf.writeVarInt(maxTypes);
    }

    private static TerminalSyncPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean full = buf.readBoolean();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) throw new IllegalArgumentException("Too many terminal entries: " + n);
        List<StorageNet.Stored> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemResource resource = ItemResource.STREAM_CODEC.decode(buf);
            list.add(new StorageNet.Stored(resource, buf.readVarLong()));
        }
        return new TerminalSyncPayload(id, full, list, buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

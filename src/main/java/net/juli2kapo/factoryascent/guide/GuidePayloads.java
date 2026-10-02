package net.juli2kapo.factoryascent.guide;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Manual's packets: {@link Request} (client → server, when the book opens) answered with
 * {@link Unlocks} (the player's finished Factory Ascent advancements, which reveal entries).
 */
public final class GuidePayloads {
    private GuidePayloads() {}

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "guide_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Request payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, unlocks(player));
        }
    }

    public record Unlocks(List<String> done) implements CustomPacketPayload {
        public static final Type<Unlocks> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "guide_unlocks"));
        public static final StreamCodec<FriendlyByteBuf, Unlocks> STREAM_CODEC = StreamCodec.of((buf, u) -> {
            buf.writeVarInt(u.done.size());
            for (String s : u.done) buf.writeUtf(s, 128);
        }, buf -> {
            int n = Math.min(4096, buf.readVarInt());
            List<String> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(buf.readUtf(128));
            return new Unlocks(list);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The player's finished advancements of this mod (paths). */
    public static Unlocks unlocks(ServerPlayer player) {
        List<String> done = new ArrayList<>();
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
                if (!holder.id().getNamespace().equals(FactoryAscent.MOD_ID)) continue;
                if (player.getAdvancements().getOrStartProgress(holder).isDone()) done.add(holder.id().getPath());
            }
        }
        return new Unlocks(done);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(Request.TYPE, Request.STREAM_CODEC, Request::handle)
                .playToClient(Unlocks.TYPE, Unlocks.STREAM_CODEC);
    }
}

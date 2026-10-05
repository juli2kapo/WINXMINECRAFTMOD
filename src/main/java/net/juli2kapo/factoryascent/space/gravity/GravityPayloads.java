package net.juli2kapo.factoryascent.space.gravity;

import io.netty.buffer.ByteBuf;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Gravity packets: the moving client asks to turn, the server answers (and tells the onlookers). */
public final class GravityPayloads {
    private GravityPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** Client → server: "my gravity is now {@code gravity}, with my feet here". */
    public record Request(Direction gravity, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Request> TYPE = typeOf("gravity_request");
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                Direction.STREAM_CODEC, Request::gravity,
                ByteBufCodecs.DOUBLE, Request::x,
                ByteBufCodecs.DOUBLE, Request::y,
                ByteBufCodecs.DOUBLE, Request::z,
                Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Request payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer sp) {
                Gravity.onRequest(sp, payload.gravity(), new Vec3(payload.x(), payload.y(), payload.z()));
            }
        }
    }

    /**
     * Server → client: entity {@code entityId} has gravity {@code gravity}. With {@code force} the
     * receiving player is being corrected (or let go) by the server and moves to the position given.
     */
    public record Sync(int entityId, Direction gravity, boolean force, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = typeOf("gravity_sync");
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::entityId,
                Direction.STREAM_CODEC, Sync::gravity,
                ByteBufCodecs.BOOL, Sync::force,
                ByteBufCodecs.DOUBLE, Sync::x,
                ByteBufCodecs.DOUBLE, Sync::y,
                ByteBufCodecs.DOUBLE, Sync::z,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(Request.TYPE, Request.STREAM_CODEC, Request::handle)
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC);
    }
}

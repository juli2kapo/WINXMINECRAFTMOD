package net.juli2kapo.factoryascent.space;

import io.netty.buffer.ByteBuf;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Packets of space travel and personal gear. The client only ever reports keys; the server decides. */
public final class SpacePayloads {
    private SpacePayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** Client → server: jump is held (true) or released (false) while wearing a jetpack. */
    public record JetpackInput(boolean thrust) implements CustomPacketPayload {
        public static final Type<JetpackInput> TYPE = typeOf("jetpack_input");
        public static final StreamCodec<ByteBuf, JetpackInput> STREAM_CODEC =
                ByteBufCodecs.BOOL.map(JetpackInput::new, JetpackInput::thrust);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(JetpackInput payload, IPayloadContext context) {
            Jetpack.setInput(context.player(), payload.thrust());
        }
    }

    /** Client → server: the hover key was pressed. */
    public record JetpackHover() implements CustomPacketPayload {
        public static final Type<JetpackHover> TYPE = typeOf("jetpack_hover");
        public static final StreamCodec<ByteBuf, JetpackHover> STREAM_CODEC = StreamCodec.unit(new JetpackHover());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(JetpackHover payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer sp) Jetpack.toggleHover(sp);
        }
    }

    /** Server → client: re-entry started; shake the screen and glow for {@code ticks}. */
    public record Reentry(int ticks) implements CustomPacketPayload {
        public static final Type<Reentry> TYPE = typeOf("reentry");
        public static final StreamCodec<ByteBuf, Reentry> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Reentry::new, Reentry::ticks);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client, every half second in an airless dimension: where the player's air comes
     * from ({@link SpaceRules.Breath} ordinal), for the HUD.
     */
    public record Breathing(int breath) implements CustomPacketPayload {
        public static final Type<Breathing> TYPE = typeOf("breathing");
        public static final StreamCodec<ByteBuf, Breathing> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Breathing::new, Breathing::breath);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(JetpackInput.TYPE, JetpackInput.STREAM_CODEC, JetpackInput::handle)
                .playToServer(JetpackHover.TYPE, JetpackHover.STREAM_CODEC, JetpackHover::handle)
                .playToClient(Reentry.TYPE, Reentry.STREAM_CODEC)
                .playToClient(Breathing.TYPE, Breathing.STREAM_CODEC);
    }
}

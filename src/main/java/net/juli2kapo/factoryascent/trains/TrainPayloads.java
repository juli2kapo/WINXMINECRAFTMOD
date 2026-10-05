package net.juli2kapo.factoryascent.trains;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: the driver's keys ({@link Input}: throttle up/down, brake; sent when they change
 * and once a second) and one-shot actions ({@link Action}: horn, lights). Only accepted from the
 * driver of that very locomotive.
 */
public final class TrainPayloads {
    private TrainPayloads() {}

    public record Input(int loco, byte keys) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "train_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::loco, ByteBufCodecs.BYTE, Input::keys, Input::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(int loco, int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "train_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::loco, ByteBufCodecs.VAR_INT, Action::action, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Input.TYPE, Input.STREAM_CODEC, TrainPayloads::onInput);
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, TrainPayloads::onAction);
    }

    private static void onInput(Input payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof Locomotive loco && loco.getId() == payload.loco() && loco.driver() == player) {
            loco.setInput(payload.keys());
        }
    }

    private static void onAction(Action payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof Locomotive loco && loco.getId() == payload.loco() && loco.driver() == player
                && payload.action() != Locomotive.ACTION_STOP) {
            loco.action(player, payload.action());
        }
    }
}

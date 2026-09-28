package net.juli2kapo.factoryascent.ships;

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
 * Client → server: the pilot's keys ({@link Input}, sent when they change and once a second) and
 * one-shot actions ({@link Action}: horn, lights, hatch). The server only accepts them from a
 * passenger of that very ship, and only the helm seat steers.
 */
public final class ShipPayloads {
    public static final int ACTION_HORN = 0, ACTION_LIGHTS = 1, ACTION_HATCH = 2;

    private ShipPayloads() {}

    /** Key bits ({@code AbstractShip.IN_*}) for the ship with this entity id. */
    public record Input(int ship, byte keys) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "ship_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::ship, ByteBufCodecs.BYTE, Input::keys, Input::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(int ship, int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "ship_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::ship, ByteBufCodecs.VAR_INT, Action::action, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Input.TYPE, Input.STREAM_CODEC, ShipPayloads::onInput);
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, ShipPayloads::onAction);
    }

    private static void onInput(Input payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof AbstractShip ship && ship.getId() == payload.ship() && ship.pilot() == player) {
            ship.setInput(payload.keys());
        }
    }

    private static void onAction(Action payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof AbstractShip ship && ship.getId() == payload.ship()) {
            ship.action(player, payload.action());
        }
    }
}

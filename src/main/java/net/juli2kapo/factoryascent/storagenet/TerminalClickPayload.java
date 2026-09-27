package net.juli2kapo.factoryascent.storagenet;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Client → server: a click in the terminal's item grid. The client only says what it clicked and
 * how; the server decides everything (what is really stored, what the player really carries).
 * {@code resource} is empty for the insert actions.
 */
public record TerminalClickPayload(int containerId, ItemResource resource, int action) implements CustomPacketPayload {
    public static final int TAKE_STACK = 0;
    public static final int TAKE_HALF = 1;
    public static final int TAKE_TO_INVENTORY = 2;
    public static final int INSERT_CARRIED = 3;
    public static final int INSERT_ONE = 4;

    public static final Type<TerminalClickPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "terminal_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalClickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalClickPayload::containerId,
            ItemResource.STREAM_CODEC, TerminalClickPayload::resource,
            ByteBufCodecs.VAR_INT, TerminalClickPayload::action,
            TerminalClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server side: only acts on the terminal menu the player really has open. */
    static void handle(TerminalClickPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player
                && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.handleClick(player, payload.resource(), payload.action());
        }
    }
}

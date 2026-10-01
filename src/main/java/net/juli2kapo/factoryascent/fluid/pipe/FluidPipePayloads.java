package net.juli2kapo.factoryascent.fluid.pipe;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Packets of the fluid pipe screen: a view the server sends, the face switch and refresh requests the client sends. */
public final class FluidPipePayloads {
    public static final int SIDE_NOTHING = 0, SIDE_PIPE = 1, SIDE_TANK = 2;
    public static final int INSERT = 0, EXTRACT = 1;

    private FluidPipePayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /**
     * Server → client: a fluid pipe's six faces ({@code modes}: {@code PipeConnection} ordinals,
     * {@code kinds}: {@code SIDE_*}, {@code neighbours}: the block there as an item) and its
     * network: pipe count, destinations, extracting faces, the slowest pipe (mB/s) and the fluid
     * that last went through ({@code fluid}, a registry id or "").
     */
    public record FluidPipeView(boolean open, BlockPos pos, int rate, List<Integer> modes, List<Integer> kinds,
                                List<ItemStack> neighbours, int pipes, int destinations, int extracting, int networkRate,
                                String fluid) implements CustomPacketPayload {
        public static final Type<FluidPipeView> TYPE = typeOf("fluid_pipe_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidPipeView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBoolean(v.open);
                    buf.writeBlockPos(v.pos);
                    buf.writeVarInt(v.rate);
                    for (int i = 0; i < 6; i++) {
                        buf.writeVarInt(v.modes.get(i));
                        buf.writeVarInt(v.kinds.get(i));
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v.neighbours.get(i));
                    }
                    buf.writeVarInt(v.pipes);
                    buf.writeVarInt(v.destinations);
                    buf.writeVarInt(v.extracting);
                    buf.writeVarInt(v.networkRate);
                    buf.writeUtf(v.fluid, 256);
                },
                buf -> {
                    boolean open = buf.readBoolean();
                    BlockPos pos = buf.readBlockPos();
                    int rate = buf.readVarInt();
                    List<Integer> modes = new ArrayList<>(6), kinds = new ArrayList<>(6);
                    List<ItemStack> neighbours = new ArrayList<>(6);
                    for (int i = 0; i < 6; i++) {
                        modes.add(buf.readVarInt());
                        kinds.add(buf.readVarInt());
                        neighbours.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    }
                    return new FluidPipeView(open, pos, rate, modes, kinds, neighbours, buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: set one face to insert or extract. */
    public record FluidPipeAction(BlockPos pos, int side, int mode) implements CustomPacketPayload {
        public static final Type<FluidPipeAction> TYPE = typeOf("fluid_pipe_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidPipeAction> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, FluidPipeAction::pos,
                ByteBufCodecs.VAR_INT, FluidPipeAction::side,
                ByteBufCodecs.VAR_INT, FluidPipeAction::mode,
                FluidPipeAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(FluidPipeAction payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) FluidPipeScreens.handleAction(player, payload.pos(), payload.side(), payload.mode());
        }
    }

    /** Client → server: the open screen wants a fresh view. */
    public record FluidPipeRefresh(BlockPos pos) implements CustomPacketPayload {
        public static final Type<FluidPipeRefresh> TYPE = typeOf("fluid_pipe_refresh");
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidPipeRefresh> STREAM_CODEC =
                BlockPos.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().map(FluidPipeRefresh::new, FluidPipeRefresh::pos);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(FluidPipeRefresh payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) FluidPipeScreens.refresh(player, payload.pos());
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(FluidPipeView.TYPE, FluidPipeView.STREAM_CODEC)
                .playToServer(FluidPipeAction.TYPE, FluidPipeAction.STREAM_CODEC, FluidPipeAction::handle)
                .playToServer(FluidPipeRefresh.TYPE, FluidPipeRefresh.STREAM_CODEC, FluidPipeRefresh::handle);
    }
}

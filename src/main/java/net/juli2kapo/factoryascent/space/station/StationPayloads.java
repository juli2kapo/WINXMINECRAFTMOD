package net.juli2kapo.factoryascent.space.station;

import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Packets of the stations: the Station Core's screen and the Star Chart. */
public final class StationPayloads {
    private StationPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** Server → client: a Station Core's status ({@code open} opens the screen, otherwise it refreshes one). */
    public record StationView(boolean open, BlockPos pos, String name, List<Component> lines) implements CustomPacketPayload {
        public static final Type<StationView> TYPE = typeOf("station_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, StationView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, StationView::open,
                BlockPos.STREAM_CODEC, StationView::pos,
                ByteBufCodecs.stringUtf8(128), StationView::name,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(64)), StationView::lines,
                StationView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the open station screen asks for fresh numbers. */
    public record StationRefresh(BlockPos pos) implements CustomPacketPayload {
        public static final Type<StationRefresh> TYPE = typeOf("station_refresh");
        public static final StreamCodec<RegistryFriendlyByteBuf, StationRefresh> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, StationRefresh::pos, StationRefresh::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(StationRefresh payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                    && player.blockPosition().distSqr(payload.pos()) < 16 * 16
                    && level.getBlockEntity(payload.pos()) instanceof StationCoreBlockEntity core) {
                core.sendView(level, player, false);
            }
        }
    }

    /** One station on the Star Chart. */
    public record ChartStation(String name, String team, Identifier dimension, int x, int z, boolean own) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ChartStation> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(128), ChartStation::name,
                ByteBufCodecs.stringUtf8(64), ChartStation::team,
                Identifier.STREAM_CODEC, ChartStation::dimension,
                ByteBufCodecs.VAR_INT, ChartStation::x,
                ByteBufCodecs.VAR_INT, ChartStation::z,
                ByteBufCodecs.BOOL, ChartStation::own,
                ChartStation::new);
    }

    /** Server → client: open the Star Chart ({@code here} is where the player is). */
    public record StarChart(Identifier here, List<ChartStation> stations) implements CustomPacketPayload {
        public static final Type<StarChart> TYPE = typeOf("star_chart");
        public static final StreamCodec<RegistryFriendlyByteBuf, StarChart> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, StarChart::here,
                ChartStation.STREAM_CODEC.apply(ByteBufCodecs.list(256)), StarChart::stations,
                StarChart::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(StationView.TYPE, StationView.STREAM_CODEC)
                .playToServer(StationRefresh.TYPE, StationRefresh.STREAM_CODEC, StationRefresh::handle)
                .playToClient(StarChart.TYPE, StarChart.STREAM_CODEC);
    }
}

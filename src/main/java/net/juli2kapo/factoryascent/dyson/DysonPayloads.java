package net.juli2kapo.factoryascent.dyson;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Dyson project's packets: the size of the viewer's team swarm (for the sky and the monitor
 * hologram), and the Dyson Monitor screen ({@link MonitorView} out, {@link MonitorRequest} back).
 */
public final class DysonPayloads {
    private DysonPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** Server → client: how many collectors the player's team has in solar orbit, and the target. */
    public record Sync(long collectors, int target) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = typeOf("dyson_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Sync::collectors,
                ByteBufCodecs.VAR_INT, Sync::target,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: what the Dyson Monitor screen shows. {@code open} opens the screen; otherwise
     * it only refreshes an open one.
     *
     * @param potential FE/t the whole swarm beams down in full sunlight
     * @param received  FE/t the team's receivers took, averaged over the last second
     * @param milestones bit mask of reached milestones
     */
    public record MonitorView(boolean open, BlockPos pos, String team, long collectors, int target, long launched,
                              long potential, long received, int milestones) implements CustomPacketPayload {
        public static final Type<MonitorView> TYPE = typeOf("dyson_monitor_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, MonitorView::open,
                BlockPos.STREAM_CODEC, MonitorView::pos,
                ByteBufCodecs.STRING_UTF8, MonitorView::team,
                ByteBufCodecs.VAR_LONG, MonitorView::collectors,
                ByteBufCodecs.VAR_INT, MonitorView::target,
                ByteBufCodecs.VAR_LONG, MonitorView::launched,
                ByteBufCodecs.VAR_LONG, MonitorView::potential,
                ByteBufCodecs.VAR_LONG, MonitorView::received,
                ByteBufCodecs.VAR_INT, MonitorView::milestones,
                MonitorView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the open Dyson Monitor screen asks for fresh numbers. */
    public record MonitorRequest(BlockPos pos) implements CustomPacketPayload {
        public static final Type<MonitorRequest> TYPE = typeOf("dyson_monitor_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorRequest> STREAM_CODEC =
                BlockPos.STREAM_CODEC.map(MonitorRequest::new, MonitorRequest::pos).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(MonitorRequest payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player
                    && player.blockPosition().closerThan(payload.pos(), 16)
                    && player.level().getBlockState(payload.pos()).getBlock() instanceof DysonMonitorBlock) {
                PacketDistributor.sendToPlayer(player, view(player, payload.pos(), false));
            }
        }
    }

    /** The Monitor screen's numbers for the player's team. */
    static MonitorView view(ServerPlayer player, BlockPos pos, boolean open) {
        var server = player.level().getServer();
        String team = DysonService.teamOf(server, player.getUUID());
        DysonSwarm swarm = DysonSwarm.get(server);
        DysonSwarm.Project p = swarm.project(team);
        String name = net.juli2kapo.factoryascent.orbital.FactoryTeams.get(server).displayName(team);
        int mask = 0;
        for (int i = 0; i < DysonService.milestoneCount(); i++) {
            if (DysonService.reached(i, p.collectors())) mask |= 1 << i;
        }
        return new MonitorView(open, pos, name, p.collectors(), DysonSwarm.target(), p.launched(), swarm.swarmPower(team),
                swarm.receivedPerTick(team, server.overworld().getGameTime()), mask);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC)
                .playToClient(MonitorView.TYPE, MonitorView.STREAM_CODEC)
                .playToServer(MonitorRequest.TYPE, MonitorRequest.STREAM_CODEC, MonitorRequest::handle);
    }
}

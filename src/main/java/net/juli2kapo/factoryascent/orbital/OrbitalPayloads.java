package net.juli2kapo.factoryascent.orbital;

import java.util.List;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Orbital age's packets: the Team screen and the Orbital Radar screen. Screens only show what
 * the server sends ({@link TeamView}, {@link RadarView}) and send back what was clicked
 * ({@link TeamAction}, {@link RadarAction}); the server checks everything again.
 */
public final class OrbitalPayloads {
    private OrbitalPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** Hard cap on list sizes so a packet can never grow without bound. */
    private static final int MAX_ROWS = 256;

    /**
     * One line of a list: a satellite of your team, or a radar contact.
     *
     * @param state    radar contacts: one of the {@code CONTACT_*} constants; 0 for team satellites
     * @param progress tracking progress in percent (radar contacts being tracked)
     */
    public record Row(UUID id, Component line, int state, int progress) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Row::id,
                ComponentSerialization.STREAM_CODEC, Row::line,
                ByteBufCodecs.VAR_INT, Row::state,
                ByteBufCodecs.VAR_INT, Row::progress,
                Row::new);
    }

    public static final int CONTACT_OWN = 0;
    public static final int CONTACT_UNKNOWN = 1;
    public static final int CONTACT_TRACKING = 2;
    public static final int CONTACT_LOCKED = 3;
    public static final int CONTACT_TARGET = 4;

    // ---------------------------------------------------------------- team screen

    /**
     * Server → client: what the Team screen shows. {@code open} opens the screen; otherwise it only
     * refreshes an open one. {@code teamName} is empty on a solo team.
     */
    public record TeamView(boolean open, String teamName, List<String> members, List<String> invites,
                           List<String> candidates, List<Row> satellites, Component message) implements CustomPacketPayload {
        public static final Type<TeamView> TYPE = typeOf("team_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, TeamView::open,
                ByteBufCodecs.STRING_UTF8, TeamView::teamName,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ROWS)), TeamView::members,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ROWS)), TeamView::invites,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ROWS)), TeamView::candidates,
                Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), TeamView::satellites,
                ComponentSerialization.STREAM_CODEC, TeamView::message,
                TeamView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: a Team screen button. {@code arg} is a name, or a satellite id for deorbit. */
    public record TeamAction(int action, String arg) implements CustomPacketPayload {
        public static final int REFRESH = 0, CREATE = 1, INVITE = 2, JOIN = 3, LEAVE = 4, DEORBIT = 5;
        public static final Type<TeamAction> TYPE = typeOf("team_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TeamAction::action,
                ByteBufCodecs.stringUtf8(64), TeamAction::arg,
                TeamAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(TeamAction payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) OrbitalConsole.handle(player, payload);
        }
    }

    // ---------------------------------------------------------------- radar screen

    /** Server → client: what an Orbital Radar's screen shows. */
    public record RadarView(boolean open, BlockPos pos, int energy, int capacity, Component header, List<Row> contacts,
                            Component message) implements CustomPacketPayload {
        public static final Type<RadarView> TYPE = typeOf("radar_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, RadarView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, RadarView::open,
                BlockPos.STREAM_CODEC, RadarView::pos,
                ByteBufCodecs.VAR_INT, RadarView::energy,
                ByteBufCodecs.VAR_INT, RadarView::capacity,
                ComponentSerialization.STREAM_CODEC, RadarView::header,
                Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), RadarView::contacts,
                ComponentSerialization.STREAM_CODEC, RadarView::message,
                RadarView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: a radar screen button (refresh, track a contact, pick it as the missile target). */
    public record RadarAction(BlockPos pos, int action, UUID id) implements CustomPacketPayload {
        public static final int REFRESH = 0, TRACK = 1, DESIGNATE = 2, STOP = 3;
        public static final Type<RadarAction> TYPE = typeOf("radar_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, RadarAction> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RadarAction::pos,
                ByteBufCodecs.VAR_INT, RadarAction::action,
                UUIDUtil.STREAM_CODEC, RadarAction::id,
                RadarAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(RadarAction payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) OrbitalRadarBlockEntity.handle(player, payload);
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(TeamView.TYPE, TeamView.STREAM_CODEC)
                .playToClient(RadarView.TYPE, RadarView.STREAM_CODEC)
                .playToServer(TeamAction.TYPE, TeamAction.STREAM_CODEC, TeamAction::handle)
                .playToServer(RadarAction.TYPE, RadarAction.STREAM_CODEC, RadarAction::handle);
    }
}

package net.juli2kapo.factoryascent.xdim;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.xdim.LinkTier.Resource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The link screen's packets: {@link View} (server → client: everything the screen shows; opens it
 * when {@code open}) and {@link Action} (client → server: a button; answered with a fresh View).
 */
public final class XdimPayloads {
    public static final int REFRESH = 0, SET_MODE = 1, TUNE = 2, CREATE = 3, RENAME = 4, SET_PUBLIC = 5, DELETE = 6;
    private static final int MAX_LIST = 64;

    private XdimPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** A channel the viewer may tune to. {@code editable}: the viewer's team owns it. */
    public record ChannelInfo(int id, String name, boolean isPublic, boolean editable, String owner, int endpoints) {}

    /** An endpoint on the viewed channel. {@code stats}: last second, see {@link LinkBlockEntity#lastSecond()}. */
    public record EndpointInfo(String dimension, BlockPos pos, int tier, boolean loaded, boolean anchored, boolean here,
                               long[] stats) {}

    public record View(boolean open, BlockPos pos, int tier, boolean mayEdit, int channel, byte[] modes, int status,
                       boolean anchored, int power, int powerCapacity, long[] stats, long cost, String team,
                       List<ChannelInfo> channels, List<EndpointInfo> endpoints) implements CustomPacketPayload {
        public static final Type<View> TYPE = typeOf("xdim_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of((buf, v) -> {
            buf.writeBoolean(v.open);
            buf.writeBlockPos(v.pos);
            buf.writeVarInt(v.tier);
            buf.writeBoolean(v.mayEdit);
            buf.writeVarInt(v.channel);
            buf.writeByteArray(v.modes);
            buf.writeVarInt(v.status);
            buf.writeBoolean(v.anchored);
            buf.writeVarInt(v.power);
            buf.writeVarInt(v.powerCapacity);
            writeLongs(buf, v.stats);
            buf.writeVarLong(v.cost);
            buf.writeUtf(v.team, 64);
            buf.writeVarInt(v.channels.size());
            for (ChannelInfo c : v.channels) {
                buf.writeVarInt(c.id());
                buf.writeUtf(c.name(), 64);
                buf.writeBoolean(c.isPublic());
                buf.writeBoolean(c.editable());
                buf.writeUtf(c.owner(), 64);
                buf.writeVarInt(c.endpoints());
            }
            buf.writeVarInt(v.endpoints.size());
            for (EndpointInfo e : v.endpoints) {
                buf.writeUtf(e.dimension(), 128);
                buf.writeBlockPos(e.pos());
                buf.writeVarInt(e.tier());
                buf.writeBoolean(e.loaded());
                buf.writeBoolean(e.anchored());
                buf.writeBoolean(e.here());
                writeLongs(buf, e.stats());
            }
        }, buf -> {
            boolean open = buf.readBoolean();
            BlockPos pos = buf.readBlockPos();
            int tier = buf.readVarInt();
            boolean mayEdit = buf.readBoolean();
            int channel = buf.readVarInt();
            byte[] modes = buf.readByteArray(18);
            int status = buf.readVarInt();
            boolean anchored = buf.readBoolean();
            int power = buf.readVarInt(), cap = buf.readVarInt();
            long[] stats = readLongs(buf);
            long cost = buf.readVarLong();
            String team = buf.readUtf(64);
            int nc = Math.min(MAX_LIST, buf.readVarInt());
            List<ChannelInfo> channels = new ArrayList<>();
            for (int i = 0; i < nc; i++) {
                channels.add(new ChannelInfo(buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readUtf(64), buf.readVarInt()));
            }
            int ne = Math.min(MAX_LIST, buf.readVarInt());
            List<EndpointInfo> endpoints = new ArrayList<>();
            for (int i = 0; i < ne; i++) {
                endpoints.add(new EndpointInfo(buf.readUtf(128), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                        buf.readBoolean(), readLongs(buf)));
            }
            return new View(open, pos, tier, mayEdit, channel, modes, status, anchored, power, cap, stats, cost, team, channels, endpoints);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeLongs(RegistryFriendlyByteBuf buf, long[] values) {
        buf.writeVarInt(values.length);
        for (long v : values) buf.writeVarLong(v);
    }

    private static long[] readLongs(RegistryFriendlyByteBuf buf) {
        int n = Math.min(16, buf.readVarInt());
        long[] out = new long[n];
        for (int i = 0; i < n; i++) out[i] = buf.readVarLong();
        return out;
    }

    /** Client → server: one of the screen's buttons. */
    public record Action(BlockPos pos, int action, int a, int b, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = typeOf("xdim_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of((buf, v) -> {
            buf.writeBlockPos(v.pos);
            buf.writeVarInt(v.action);
            buf.writeVarInt(v.a);
            buf.writeVarInt(v.b);
            buf.writeUtf(v.text, 64);
        }, buf -> new Action(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Action payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.blockPosition().closerThan(payload.pos(), 10)) return;
            if (!(player.level().getBlockEntity(payload.pos()) instanceof LinkBlockEntity link)) return;
            apply(player, link, payload.action(), payload.a(), payload.b(), payload.text());
            PacketDistributor.sendToPlayer(player, view(player, link, false));
        }
    }

    /** Carries out a screen action for the player (also used by the GameTests). Returns whether it did anything. */
    public static boolean apply(ServerPlayer player, LinkBlockEntity link, int action, int a, int b, String text) {
        MinecraftServer server = player.level().getServer();
        XdimNetwork net = XdimNetwork.get(server);
        boolean control = link.mayControl(player);
        switch (action) {
            case SET_MODE -> {
                if (!control || a < 0 || a >= 18) return false;
                Direction face = Direction.from3DDataValue(a / 3);
                Resource r = Resource.VALUES[a % 3];
                if (!link.tier().carries(r)) return false;
                link.setMode(face, r, b);
                link.sync();
                return true;
            }
            case TUNE -> {
                if (!control) return false;
                if (a == 0) {
                    link.setChannel(0);
                    return true;
                }
                XdimNetwork.Channel ch = net.channel(a);
                if (!XdimNetwork.mayUse(server, player.getUUID(), ch)) return false;
                if (link.owner() == null) link.setOwner(player.getUUID());
                link.setChannel(a);
                player.level().playSound(null, link.getBlockPos(), SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.7f, 1.4f);
                return true;
            }
            case CREATE -> {
                if (!control) return false;
                XdimNetwork.Channel ch = net.create(player.getUUID(), text, b != 0);
                if (link.owner() == null) link.setOwner(player.getUUID());
                link.setChannel(ch.id());
                player.level().playSound(null, link.getBlockPos(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.8f, 1.2f);
                return true;
            }
            case RENAME -> {
                XdimNetwork.Channel ch = net.channel(a);
                if (!XdimNetwork.mayEdit(server, player.getUUID(), ch) || XdimNetwork.cleanName(text).isEmpty()) return false;
                net.rename(a, text);
                return true;
            }
            case SET_PUBLIC -> {
                XdimNetwork.Channel ch = net.channel(a);
                if (!XdimNetwork.mayEdit(server, player.getUUID(), ch)) return false;
                net.setPublic(a, b != 0);
                return true;
            }
            case DELETE -> {
                XdimNetwork.Channel ch = net.channel(a);
                if (!XdimNetwork.mayEdit(server, player.getUUID(), ch)) return false;
                net.delete(a);
                return true;
            }
            default -> {
                return action == REFRESH;
            }
        }
    }

    public static void open(ServerPlayer player, LinkBlockEntity link) {
        PacketDistributor.sendToPlayer(player, view(player, link, true));
    }

    static View view(ServerPlayer player, LinkBlockEntity link, boolean open) {
        MinecraftServer server = player.level().getServer();
        XdimNetwork net = XdimNetwork.get(server);
        FactoryTeams teams = FactoryTeams.get(server);
        List<ChannelInfo> channels = new ArrayList<>();
        for (XdimNetwork.Channel c : net.visibleTo(server, player.getUUID())) {
            if (channels.size() >= MAX_LIST) break;
            channels.add(new ChannelInfo(c.id(), c.name(), c.isPublic(), teams.sameTeam(player.getUUID(), c.owner()),
                    teams.playerName(c.owner()), net.on(c.id()).size()));
        }
        // the tuned channel stays listed even when the viewer couldn't pick it (someone else's link)
        XdimNetwork.Channel tuned = net.channel(link.channel());
        if (tuned != null && channels.stream().noneMatch(c -> c.id() == tuned.id())) {
            channels.add(0, new ChannelInfo(tuned.id(), tuned.name(), tuned.isPublic(), false, teams.playerName(tuned.owner()),
                    net.on(tuned.id()).size()));
        }
        List<EndpointInfo> endpoints = new ArrayList<>();
        for (XdimNetwork.Endpoint e : net.on(link.channel())) {
            if (endpoints.size() >= MAX_LIST) break;
            LinkBlockEntity other = XdimService.loaded(server, e.pos());
            endpoints.add(new EndpointInfo(e.pos().dimension().identifier().toString(), e.pos().pos(), e.tier().ordinal(),
                    other != null, e.anchored(), other == link, other == null ? new long[6] : other.lastSecond()));
        }
        String team = link.owner() == null ? "" : teams.displayName(teams.teamOf(link.owner()));
        return new View(open, link.getBlockPos(), link.tier().ordinal(), link.mayControl(player), link.channel(), link.modes(),
                link.status(), link.anchored(), link.power().getAmountAsInt(),
                link.tier() == LinkTier.QUANTUM ? link.power().getCapacityAsInt() : 0, link.lastSecond(), link.costLastSecond(),
                team, channels, endpoints);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(View.TYPE, View.STREAM_CODEC)
                .playToServer(Action.TYPE, Action.STREAM_CODEC, Action::handle);
    }
}

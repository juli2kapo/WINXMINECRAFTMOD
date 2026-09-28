package net.juli2kapo.factoryascent.ui;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity;
import net.juli2kapo.factoryascent.ender.RecallCharmItem;
import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Packets of the info/config screens that are not container menus: the Recall Charm, the Electric
 * Drill, and the item pipe / power cable / storage network screens. Screens only show what the
 * server sends ({@code *View}) and send back what was clicked ({@code *Action}); every action is
 * checked again on the server (item in hand, distance to the block, ownership).
 */
@EventBusSubscriber(modid = FactoryAscent.MOD_ID)
public final class ScreenPayloads {
    private ScreenPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** The hand a payload names, or null for garbage. */
    public static @Nullable InteractionHand hand(int ordinal) {
        return ordinal == 0 ? InteractionHand.MAIN_HAND : ordinal == 1 ? InteractionHand.OFF_HAND : null;
    }

    private static final int MAX_ITEMS = 22;

    // ================================================================ drill

    /** Server → client: open the Electric Drill screen for the drill in {@code hand}. */
    public record OpenDrill(int hand) implements CustomPacketPayload {
        public static final Type<OpenDrill> TYPE = typeOf("open_drill");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDrill> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(OpenDrill::new, OpenDrill::hand);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: pick the drill's mining mode (a {@code DrillMode} ordinal). */
    public record DrillAction(int hand, int mode) implements CustomPacketPayload {
        public static final Type<DrillAction> TYPE = typeOf("drill_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, DrillAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DrillAction::hand,
                ByteBufCodecs.VAR_INT, DrillAction::mode,
                DrillAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(DrillAction payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) ElectricDrillItem.handleModeAction(player, payload.hand(), payload.mode());
        }
    }

    // ================================================================ recall charm

    public static final int BEACON_READY = 0, BEACON_NO_PEARL = 1, BEACON_GONE = 2, BEACON_UNKNOWN = 3;

    /**
     * Server → client: what the Recall Charm screen shows. {@code state} is one of the
     * {@code BEACON_*} constants (meaningless when not linked).
     */
    public record CharmView(boolean open, int hand, boolean linked, String name, BlockPos pos, String dimension,
                            boolean sameDimension, int state, int recallSeconds, int cooldownSeconds,
                            boolean crossDimension) implements CustomPacketPayload {
        public static final Type<CharmView> TYPE = typeOf("charm_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, CharmView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBoolean(v.open);
                    buf.writeVarInt(v.hand);
                    buf.writeBoolean(v.linked);
                    buf.writeUtf(v.name, 64);
                    buf.writeBlockPos(v.pos);
                    buf.writeUtf(v.dimension, 256);
                    buf.writeBoolean(v.sameDimension);
                    buf.writeVarInt(v.state);
                    buf.writeVarInt(v.recallSeconds);
                    buf.writeVarInt(v.cooldownSeconds);
                    buf.writeBoolean(v.crossDimension);
                },
                buf -> new CharmView(buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(64),
                        buf.readBlockPos(), buf.readUtf(256), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: a Recall Charm screen button. */
    public record CharmAction(int hand, int action) implements CustomPacketPayload {
        public static final int REFRESH = 0, UNLINK = 1;
        public static final Type<CharmAction> TYPE = typeOf("charm_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, CharmAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CharmAction::hand,
                ByteBufCodecs.VAR_INT, CharmAction::action,
                CharmAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(CharmAction payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) RecallCharmItem.handleAction(player, payload.hand(), payload.action());
        }
    }

    // ================================================================ ender beacon

    /** Client → server: rename the Ender Beacon at {@code pos} (owner only; empty resets the name). */
    public record BeaconRename(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<BeaconRename> TYPE = typeOf("beacon_rename");
        public static final StreamCodec<RegistryFriendlyByteBuf, BeaconRename> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BeaconRename::pos,
                ByteBufCodecs.stringUtf8(64), BeaconRename::name,
                BeaconRename::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(BeaconRename payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) EnderBeaconBlockEntity.handleRename(player, payload.pos(), payload.name());
        }
    }

    // ================================================================ item pipe

    /** What is on one side of a pipe. */
    public static final int SIDE_NOTHING = 0, SIDE_PIPE = 1, SIDE_INVENTORY = 2;

    /**
     * Server → client: an item pipe's six faces ({@code modes}: {@code PipeConnection} ordinals,
     * {@code kinds}: {@code SIDE_*}, {@code neighbours}: the block there as an item, for icons, in
     * {@code Direction} order) and its network.
     */
    public record PipeView(boolean open, BlockPos pos, int rate, List<Integer> modes, List<Integer> kinds,
                           List<ItemStack> neighbours, int pipes, int destinations, int extracting) implements CustomPacketPayload {
        public static final Type<PipeView> TYPE = typeOf("pipe_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, PipeView> STREAM_CODEC = StreamCodec.of(
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
                    return new PipeView(open, pos, rate, modes, kinds, neighbours, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: set one pipe face to insert ({@link #INSERT}) or extract ({@link #EXTRACT}). */
    public record PipeAction(BlockPos pos, int side, int mode) implements CustomPacketPayload {
        public static final int INSERT = 1, EXTRACT = 2;
        public static final Type<PipeAction> TYPE = typeOf("pipe_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, PipeAction> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PipeAction::pos,
                ByteBufCodecs.VAR_INT, PipeAction::side,
                ByteBufCodecs.VAR_INT, PipeAction::mode,
                PipeAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(PipeAction payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                NetworkScreens.handlePipeAction(player, payload.pos(), payload.side(), payload.mode());
            }
        }
    }

    // ================================================================ power cable

    /** Server → client: a power cable's network (energy in FE, flows averaged in FE/t). */
    public record CableView(boolean open, BlockPos pos, long energy, long capacity, long rate, int cableRate, int cables,
                            int producers, int consumers, int storage, long in, long out) implements CustomPacketPayload {
        public static final Type<CableView> TYPE = typeOf("cable_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, CableView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBoolean(v.open);
                    buf.writeBlockPos(v.pos);
                    buf.writeVarLong(v.energy);
                    buf.writeVarLong(v.capacity);
                    buf.writeVarLong(v.rate);
                    buf.writeVarInt(v.cableRate);
                    buf.writeVarInt(v.cables);
                    buf.writeVarInt(v.producers);
                    buf.writeVarInt(v.consumers);
                    buf.writeVarInt(v.storage);
                    buf.writeVarLong(v.in);
                    buf.writeVarLong(v.out);
                },
                buf -> new CableView(buf.readBoolean(), buf.readBlockPos(), buf.readVarLong(), buf.readVarLong(),
                        buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarLong(), buf.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ================================================================ storage network

    /**
     * Server → client: a storage network seen from one of its blocks. {@code status} is a
     * {@code StorageNet.Status} ordinal; {@code top} are the most stored item types with their counts.
     */
    public record StorageView(boolean open, BlockPos pos, int status, int energy, int capacity, int drain, int devices,
                              int drives, int members, long used, long capacityItems, int types, int typeCapacity,
                              List<ItemStack> top, List<Long> counts) implements CustomPacketPayload {
        public static final Type<StorageView> TYPE = typeOf("storage_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, StorageView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBoolean(v.open);
                    buf.writeBlockPos(v.pos);
                    buf.writeVarInt(v.status);
                    buf.writeVarInt(v.energy);
                    buf.writeVarInt(v.capacity);
                    buf.writeVarInt(v.drain);
                    buf.writeVarInt(v.devices);
                    buf.writeVarInt(v.drives);
                    buf.writeVarInt(v.members);
                    buf.writeVarLong(v.used);
                    buf.writeVarLong(v.capacityItems);
                    buf.writeVarInt(v.types);
                    buf.writeVarInt(v.typeCapacity);
                    int n = Math.min(MAX_ITEMS, Math.min(v.top.size(), v.counts.size()));
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v.top.get(i));
                        buf.writeVarLong(v.counts.get(i));
                    }
                },
                buf -> {
                    boolean open = buf.readBoolean();
                    BlockPos pos = buf.readBlockPos();
                    int status = buf.readVarInt(), energy = buf.readVarInt(), capacity = buf.readVarInt(), drain = buf.readVarInt();
                    int devices = buf.readVarInt(), drives = buf.readVarInt(), members = buf.readVarInt();
                    long used = buf.readVarLong(), capItems = buf.readVarLong();
                    int types = buf.readVarInt(), typeCap = buf.readVarInt();
                    int n = Math.min(MAX_ITEMS, buf.readVarInt());
                    List<ItemStack> top = new ArrayList<>(n);
                    List<Long> counts = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        top.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                        counts.add(buf.readVarLong());
                    }
                    return new StorageView(open, pos, status, energy, capacity, drain, devices, drives, members, used,
                            capItems, types, typeCap, top, counts);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: send a fresh view of the pipe / cable / storage block at {@code pos}. */
    public record NetRefresh(BlockPos pos) implements CustomPacketPayload {
        public static final Type<NetRefresh> TYPE = typeOf("net_refresh");
        public static final StreamCodec<RegistryFriendlyByteBuf, NetRefresh> STREAM_CODEC =
                BlockPos.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().map(NetRefresh::new, NetRefresh::pos);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(NetRefresh payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) NetworkScreens.refresh(player, payload.pos());
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(OpenDrill.TYPE, OpenDrill.STREAM_CODEC)
                .playToClient(CharmView.TYPE, CharmView.STREAM_CODEC)
                .playToClient(PipeView.TYPE, PipeView.STREAM_CODEC)
                .playToClient(CableView.TYPE, CableView.STREAM_CODEC)
                .playToClient(StorageView.TYPE, StorageView.STREAM_CODEC)
                .playToServer(DrillAction.TYPE, DrillAction.STREAM_CODEC, DrillAction::handle)
                .playToServer(CharmAction.TYPE, CharmAction.STREAM_CODEC, CharmAction::handle)
                .playToServer(BeaconRename.TYPE, BeaconRename.STREAM_CODEC, BeaconRename::handle)
                .playToServer(PipeAction.TYPE, PipeAction.STREAM_CODEC, PipeAction::handle)
                .playToServer(NetRefresh.TYPE, NetRefresh.STREAM_CODEC, NetRefresh::handle);
    }
}

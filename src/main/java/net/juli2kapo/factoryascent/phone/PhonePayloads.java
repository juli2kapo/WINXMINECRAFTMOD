package net.juli2kapo.factoryascent.phone;

import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The phone's packets. The screen only shows what the server sends ({@link PhoneState} for the
 * phone itself, {@link AppData} for the open app, {@link Notify} for push notifications) and sends
 * back what was pressed ({@link Action}); the server checks everything again.
 */
public final class PhonePayloads {
    private PhonePayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    /** One home-screen icon: the app, whether it needs uplink signal, and its badge number. */
    public record AppInfo(String id, boolean needsSignal, int badge) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AppInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), AppInfo::id,
                ByteBufCodecs.BOOL, AppInfo::needsSignal,
                ByteBufCodecs.VAR_INT, AppInfo::badge,
                AppInfo::new);
    }

    /**
     * Server → client: the phone's status bar and home screen. {@code open} opens the phone screen;
     * otherwise it refreshes an open one ({@code open = false, alive = false} closes it).
     *
     * @param bars    signal bars, 0 = no signal
     * @param battery FE in the phone
     */
    public record PhoneState(boolean open, boolean alive, int bars, int battery, int capacity, PhoneMemory memory,
                             List<AppInfo> apps, String team) implements CustomPacketPayload {
        public static final Type<PhoneState> TYPE = typeOf("phone_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, PhoneState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, PhoneState::open,
                ByteBufCodecs.BOOL, PhoneState::alive,
                ByteBufCodecs.VAR_INT, PhoneState::bars,
                ByteBufCodecs.VAR_INT, PhoneState::battery,
                ByteBufCodecs.VAR_INT, PhoneState::capacity,
                PhoneMemory.STREAM_CODEC, PhoneState::memory,
                AppInfo.STREAM_CODEC.apply(ByteBufCodecs.list(64)), PhoneState::apps,
                ByteBufCodecs.stringUtf8(64), PhoneState::team,
                PhoneState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: what an app shows ({@link PhoneApp#data}); {@code noSignal} instead when the
     * app needs the uplink and there is none. {@code message} is an outcome to flash (may be empty).
     */
    public record AppData(String app, boolean noSignal, CompoundTag data, Component message) implements CustomPacketPayload {
        public static final Type<AppData> TYPE = typeOf("phone_app_data");
        public static final StreamCodec<RegistryFriendlyByteBuf, AppData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), AppData::app,
                ByteBufCodecs.BOOL, AppData::noSignal,
                ByteBufCodecs.TRUSTED_COMPOUND_TAG, AppData::data,
                ComponentSerialization.STREAM_CODEC, AppData::message,
                AppData::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: open an app ({@code action = "open"}), refresh it, press one of its
     * buttons, or a phone-level request ({@code app = ""}: {@code "close"}, {@code "resume"}).
     */
    public record Action(String app, String action, CompoundTag args) implements CustomPacketPayload {
        public static final Type<Action> TYPE = typeOf("phone_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Action::app,
                ByteBufCodecs.stringUtf8(32), Action::action,
                ByteBufCodecs.COMPOUND_TAG, Action::args,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Action payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) PhoneService.handle(player, payload);
        }
    }

    /**
     * Server → client: a push notification (toast + ringtone). {@code level}: 0 info, 1 warning,
     * 2 alarm. {@code ringtone} -1 = silent.
     */
    public record Notify(String app, Component title, Component body, int level, int ringtone) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = typeOf("phone_notify");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Notify::app,
                ComponentSerialization.STREAM_CODEC, Notify::title,
                ComponentSerialization.STREAM_CODEC, Notify::body,
                ByteBufCodecs.VAR_INT, Notify::level,
                ByteBufCodecs.VAR_INT, Notify::ringtone,
                Notify::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sends only to players whose client has the phone's channel (mock players in GameTests don't). */
    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload.type())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(PhoneState.TYPE, PhoneState.STREAM_CODEC)
                .playToClient(AppData.TYPE, AppData.STREAM_CODEC)
                .playToClient(Notify.TYPE, Notify.STREAM_CODEC)
                .playToServer(Action.TYPE, Action.STREAM_CODEC, Action::handle);
    }
}

package net.juli2kapo.factoryascent.phone.client;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.phone.FactoryPhoneItem;
import net.juli2kapo.factoryascent.phone.PhonePayloads;
import net.juli2kapo.factoryascent.phone.PhonePayloads.PhoneState;
import net.juli2kapo.factoryascent.phone.client.apps.DysonView;
import net.juli2kapo.factoryascent.phone.client.apps.MachinesView;
import net.juli2kapo.factoryascent.phone.client.apps.MapView;
import net.juli2kapo.factoryascent.phone.client.apps.PowerView;
import net.juli2kapo.factoryascent.phone.client.apps.RecallView;
import net.juli2kapo.factoryascent.phone.client.apps.SettingsView;
import net.juli2kapo.factoryascent.phone.client.apps.StorageView;
import net.juli2kapo.factoryascent.phone.client.apps.TeamView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the Factory Phone (its own {@code @Mod} class for the client dist): the built-in
 * app views, the packet handlers that open and refresh {@link PhoneScreen}, notifications
 * ({@link PhoneToasts}), the {@code factoryascent:phone_screen_on} item-model property (the phone in
 * your hand lights up while its screen is open), and coming back to the phone after an app opened
 * another screen (survey map, storage terminal, team screen).
 */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class PhoneClient {
    private static @Nullable PhoneState lastState;
    private static final Map<String, CompoundTag> DATA = new HashMap<>();
    /** App to come back to when the screen it opened closes; null when none. */
    private static @Nullable String childApp;
    private static boolean childSeen;
    private static int childTimeout;

    public PhoneClient(IEventBus modBus) {
        registerViews();
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(PhonePayloads.PhoneState.TYPE, (payload, context) -> onState(payload));
            e.register(PhonePayloads.AppData.TYPE, (payload, context) -> onAppData(payload));
            e.register(PhonePayloads.Notify.TYPE, (payload, context) -> PhoneToasts.push(payload));
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CHAT,
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "phone_toasts"), PhoneToasts::hud));
        modBus.addListener((RegisterConditionalItemModelPropertyEvent e) ->
                e.register(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "phone_screen_on"), ScreenOn.MAP_CODEC));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            lastState = null;
            DATA.clear();
            childApp = null;
            PhoneToasts.clear();
        });
    }

    /** The built-in apps' views and accent colours. */
    private static void registerViews() {
        PhoneAppViews.register("map", 0xFF2E9A5E, MapView::new);
        PhoneAppViews.register("storage", 0xFF2D8C9A, StorageView::new);
        PhoneAppViews.register("team", 0xFF3C6FD8, TeamView::new);
        PhoneAppViews.register("machines", 0xFFD07A28, MachinesView::new);
        PhoneAppViews.register("power", 0xFFC8A020, PowerView::new);
        PhoneAppViews.register("recall", 0xFF8E4FD6, RecallView::new);
        PhoneAppViews.register("dyson", 0xFFD8A030, DysonView::new);
        PhoneAppViews.register("settings", 0xFF5A6680, SettingsView::new);
    }

    private static void onState(PhoneState payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.alive() || payload.open()) lastState = payload;
        if (mc.gui.screen() instanceof PhoneScreen screen) {
            if (!payload.open() && !payload.alive()) {
                if (payload.apps().isEmpty()) {
                    mc.gui.setScreen(null); // put away or dead: close
                } else {
                    screen.update(payload);
                }
            } else {
                screen.update(payload);
            }
        } else if (payload.open()) {
            childApp = null;
            mc.gui.setScreen(new PhoneScreen(payload, null));
        }
    }

    private static void onAppData(PhonePayloads.AppData payload) {
        if (!payload.noSignal()) DATA.put(payload.app(), payload.data());
        if (Minecraft.getInstance().gui.screen() instanceof PhoneScreen screen) screen.appData(payload);
    }

    static @Nullable CompoundTag cachedData(String app) {
        return DATA.get(app);
    }

    /** An app is about to open another screen: come back to it when that screen closes. */
    static void expectChildScreen(String app) {
        childApp = app;
        childSeen = false;
        childTimeout = 60;
    }

    /** The player closed the phone themselves: forget any pending child screen. */
    static void cancelChild() {
        childApp = null;
    }

    /** True while the phone screen is being replaced by a screen an app opened (don't end the session). */
    static boolean leavingForChild() {
        return childApp != null;
    }

    /** True while the local player's phone screen (or a screen it opened) is up. */
    public static boolean screenOn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() instanceof PhoneScreen || (childApp != null && childSeen);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PhoneToasts.tick();
        if (childApp == null) return;
        var screen = mc.gui.screen();
        if (screen instanceof PhoneScreen) {
            // the server refused (error shown on the phone): stop waiting after a while
            if (--childTimeout <= 0) childApp = null;
            return;
        }
        if (screen != null) {
            childSeen = true;
            return;
        }
        if (!childSeen) {
            // the phone was closed before the other screen came up: end the session
            childApp = null;
            ClientPacketDistributor.sendToServer(new PhonePayloads.Action("", "close", new CompoundTag()));
            return;
        }
        String app = childApp;
        childApp = null;
        if (mc.player == null || lastState == null || PhoneServiceHolder.noPhoneInHand(mc)) return;
        mc.gui.setScreen(new PhoneScreen(lastState, app));
        ClientPacketDistributor.sendToServer(new PhonePayloads.Action("", "resume", new CompoundTag()));
    }

    /** Small client-side checks on the local player. */
    static final class PhoneServiceHolder {
        static boolean noPhoneInHand(Minecraft mc) {
            return mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof FactoryPhoneItem
                    || mc.player.getOffhandItem().getItem() instanceof FactoryPhoneItem);
        }
    }

    /** {@code factoryascent:phone_screen_on}: the phone in the local player's hand while its screen is open. */
    public record ScreenOn() implements ConditionalItemModelProperty {
        public static final MapCodec<ScreenOn> MAP_CODEC = MapCodec.unit(new ScreenOn());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
            Minecraft mc = Minecraft.getInstance();
            if (owner == null || owner != mc.player || context == ItemDisplayContext.GUI) return false;
            return (owner.getMainHandItem() == stack || owner.getOffhandItem() == stack) && screenOn();
        }

        @Override
        public MapCodec<ScreenOn> type() {
            return MAP_CODEC;
        }
    }

    /** A list tag's compound entries (helper for views). */
    public static java.util.List<CompoundTag> compounds(CompoundTag tag, String key) {
        java.util.List<CompoundTag> out = new java.util.ArrayList<>();
        ListTag list = tag.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) list.getCompound(i).ifPresent(out::add);
        return out;
    }
}

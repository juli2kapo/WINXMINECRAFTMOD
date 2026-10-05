package net.juli2kapo.factoryascent.trains.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.trains.DieselLocomotive;
import net.juli2kapo.factoryascent.trains.Locomotive;
import net.juli2kapo.factoryascent.trains.TrainContent;
import net.juli2kapo.factoryascent.trains.TrainPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the trains (its own client entry point): the renderer and part models, the cab,
 * wagon and station screens, the HUD, the whistle/horn (H) and lights (J) keys, sending the
 * driver's keys (W/S throttle, Space brake) to the server, a longer third-person camera aboard and
 * the diesel engine's drone.
 */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class TrainsClient {
    static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "trains"));
    static final KeyMapping HORN = new KeyMapping("key.factoryascent.train_horn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    static final KeyMapping LIGHTS = new KeyMapping("key.factoryascent.train_lights", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private static int lastKeys = -1, lastLoco = -1, resend;

    public TrainsClient(IEventBus modBus) {
        modBus.addListener(TrainModels::registerModels);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(TrainContent.STEAM_LOCOMOTIVE.get(), TrainRenderer::new);
            e.registerEntityRenderer(TrainContent.DIESEL_LOCOMOTIVE.get(), TrainRenderer::new);
            e.registerEntityRenderer(TrainContent.PASSENGER_CAR.get(), TrainRenderer::new);
            e.registerEntityRenderer(TrainContent.CARGO_WAGON.get(), TrainRenderer::new);
            e.registerEntityRenderer(TrainContent.TANK_WAGON.get(), TrainRenderer::new);
            e.registerEntityRenderer(TrainContent.HOPPER_WAGON.get(), TrainRenderer::new);
        });
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(TrainContent.TRAIN_MENU.get(), TrainScreen::new);
            e.register(TrainContent.STATION_MENU.get(), StationScreen::new);
        });
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(HORN);
            e.register(LIGHTS);
        });
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "train_hud"), TrainHud::render));
        NeoForge.EVENT_BUS.addListener(TrainsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(TrainsClient::onCameraDistance);
        NeoForge.EVENT_BUS.addListener(TrainsClient::onJoin);
    }

    static Locomotive driving() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof Locomotive loco && loco.getFirstPassenger() == p ? loco : null;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Locomotive loco = driving();
        if (loco == null) {
            lastKeys = -1;
            lastLoco = -1;
            while (HORN.consumeClick()) {}
            while (LIGHTS.consumeClick()) {}
            return;
        }
        int keys = 0;
        if (mc.gui.screen() == null) {
            Options o = mc.options;
            if (o.keyUp.isDown()) keys |= Locomotive.IN_FORWARD;
            if (o.keyDown.isDown()) keys |= Locomotive.IN_BACK;
            if (o.keyJump.isDown()) keys |= Locomotive.IN_BRAKE;
        }
        if (keys != lastKeys || loco.getId() != lastLoco || ++resend >= 20) {
            ClientPacketDistributor.sendToServer(new TrainPayloads.Input(loco.getId(), (byte) keys));
            lastKeys = keys;
            lastLoco = loco.getId();
            resend = 0;
        }
        while (HORN.consumeClick()) ClientPacketDistributor.sendToServer(new TrainPayloads.Action(loco.getId(), Locomotive.ACTION_HORN));
        while (LIGHTS.consumeClick()) ClientPacketDistributor.sendToServer(new TrainPayloads.Action(loco.getId(), Locomotive.ACTION_LIGHTS));
    }

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && p.getVehicle() instanceof net.juli2kapo.factoryascent.trains.RollingStock) {
            event.setDistance(Math.max(event.getDistance(), 7f));
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof DieselLocomotive loco) {
            Minecraft.getInstance().getSoundManager().play(new EngineSound(loco));
        }
    }

    /** The diesel's engine drone: idles low while the generator or the motors work, rises with the throttle. */
    static final class EngineSound extends AbstractTickableSoundInstance {
        private final DieselLocomotive loco;

        EngineSound(DieselLocomotive loco) {
            super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.loco = loco;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001f;
            this.x = loco.getX();
            this.y = loco.getY();
            this.z = loco.getZ();
        }

        @Override
        public boolean canPlaySound() {
            return !loco.isSilent();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (loco.isRemoved()) {
                stop();
                return;
            }
            x = loco.getX();
            y = loco.getY() + 1;
            z = loco.getZ();
            float t = (float) Math.abs(loco.throttle());
            boolean running = t > 0.01f || loco.gaugeC() > 0;
            pitch = 0.35f + 0.3f * t;
            volume = running ? 0.25f + 0.35f * t : 0f;
        }
    }
}

package net.juli2kapo.factoryascent.ships.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.ships.AbstractShip;
import net.juli2kapo.factoryascent.ships.MotorShip;
import net.juli2kapo.factoryascent.ships.ShipContent;
import net.juli2kapo.factoryascent.ships.ShipPayloads;
import net.juli2kapo.factoryascent.ships.Shuttle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the ships (its own client entry point, next to FactoryAscentClient): renderers
 * and part models, the helm/cockpit screen, the HUD, key bindings (H horn, J lights, K hatch),
 * sending the pilot's keys to the server, a longer third-person camera aboard, re-entry shake and
 * the engine sounds.
 */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class ShipsClient {
    static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "ships"));
    static final KeyMapping HORN = new KeyMapping("key.factoryascent.ship_horn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    static final KeyMapping LIGHTS = new KeyMapping("key.factoryascent.ship_lights", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    static final KeyMapping HATCH = new KeyMapping("key.factoryascent.ship_hatch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private static int lastKeys = -1;
    private static int lastShip = -1;
    private static int resend;

    public ShipsClient(IEventBus modBus) {
        modBus.addListener(ShipModels::registerModels);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ShipContent.BRONZE_COG.get(), ShipRenderer::new);
            e.registerEntityRenderer(ShipContent.MOTOR_SHIP.get(), ShipRenderer::new);
            e.registerEntityRenderer(ShipContent.SHUTTLE.get(), ShipRenderer::new);
        });
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ShipContent.SHIP_MENU.get(), ShipScreen::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(HORN);
            e.register(LIGHTS);
            e.register(HATCH);
        });
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "ship_hud"), ShipHud::render));
        NeoForge.EVENT_BUS.addListener(ShipsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ShipsClient::onCameraDistance);
        NeoForge.EVENT_BUS.addListener(ShipsClient::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(ShipsClient::onJoin);
    }

    static AbstractShip riding() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof AbstractShip ship ? ship : null;
    }

    /** Samples the movement keys and sends them when they change (and once a second). */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractShip ship = riding();
        if (ship == null) {
            lastKeys = -1;
            lastShip = -1;
            while (HORN.consumeClick()) {}
            while (LIGHTS.consumeClick()) {}
            while (HATCH.consumeClick()) {}
            return;
        }
        boolean pilot = ship.getFirstPassenger() == mc.player;
        int keys = 0;
        if (pilot && mc.screen == null) {
            Options o = mc.options;
            if (o.keyUp.isDown()) keys |= AbstractShip.IN_FORWARD;
            if (o.keyDown.isDown()) keys |= AbstractShip.IN_BACK;
            if (o.keyLeft.isDown()) keys |= AbstractShip.IN_LEFT;
            if (o.keyRight.isDown()) keys |= AbstractShip.IN_RIGHT;
            if (o.keyJump.isDown()) keys |= AbstractShip.IN_UP;
            if (o.keyShift.isDown()) keys |= AbstractShip.IN_DOWN;
            if (o.keySprint.isDown()) keys |= AbstractShip.IN_SPRINT;
        }
        if (pilot && (keys != lastKeys || ship.getId() != lastShip || ++resend >= 20)) {
            ClientPacketDistributor.sendToServer(new ShipPayloads.Input(ship.getId(), (byte) keys));
            lastKeys = keys;
            lastShip = ship.getId();
            resend = 0;
        }
        while (HORN.consumeClick()) ClientPacketDistributor.sendToServer(new ShipPayloads.Action(ship.getId(), ShipPayloads.ACTION_HORN));
        while (LIGHTS.consumeClick()) ClientPacketDistributor.sendToServer(new ShipPayloads.Action(ship.getId(), ShipPayloads.ACTION_LIGHTS));
        while (HATCH.consumeClick()) ClientPacketDistributor.sendToServer(new ShipPayloads.Action(ship.getId(), ShipPayloads.ACTION_HATCH));
    }

    /** Big ships need the third-person camera further back. */
    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        AbstractShip ship = riding();
        if (ship != null) event.setDistance(Math.max(event.getDistance(), ship instanceof MotorShip ? 11f : 9f));
    }

    /** Re-entry buffets the cabin; the engines rumble a little. */
    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!(riding() instanceof Shuttle shuttle)) return;
        float t = shuttle.tickCount + (float) event.getPartialTick();
        float amp = shuttle.state() == Shuttle.STATE_REENTRY ? 1.6f : 0.12f * (shuttle.thrust + shuttle.lift);
        if (amp <= 0.001f) return;
        event.setPitch(event.getPitch() + Mth.sin(t * 5.7f) * amp);
        event.setYaw(event.getYaw() + Mth.cos(t * 4.3f) * amp * 0.6f);
        event.setRoll(event.getRoll() + Mth.sin(t * 3.1f) * amp * 0.8f);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && (event.getEntity() instanceof MotorShip || event.getEntity() instanceof Shuttle)) {
            Minecraft.getInstance().getSoundManager().play(new EngineSound((AbstractShip) event.getEntity()));
        }
    }

    /** A looping engine drone that follows the ship and swells with the throttle. */
    static final class EngineSound extends AbstractTickableSoundInstance {
        private final AbstractShip ship;

        EngineSound(AbstractShip ship) {
            super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.ship = ship;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001f;
            this.x = ship.getX();
            this.y = ship.getY();
            this.z = ship.getZ();
        }

        @Override
        public boolean canPlaySound() {
            return !ship.isSilent();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (ship.isRemoved()) {
                stop();
                return;
            }
            x = ship.getX();
            y = ship.getY() + 1;
            z = ship.getZ();
            float power;
            if (ship instanceof Shuttle) {
                power = Math.max(Math.abs(ship.thrust), ship.lift);
                pitch = 0.45f + 0.35f * power;
                volume = 0.9f * power;
            } else {
                power = Math.abs(ship.thrust);
                pitch = 0.5f + 0.3f * power;
                volume = 0.55f * power;
            }
        }
    }
}

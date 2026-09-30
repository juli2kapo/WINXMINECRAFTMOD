package net.juli2kapo.factoryascent.space.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.Jetpack;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.SpacePayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client side of space travel and personal gear: the crew capsule on the rocket, the jetpacks on
 * players' backs, the orbit sky, the HUD (air, jetpack charge, vacuum warnings, the climb and
 * re-entry), jetpack input and prediction, and the Oxygen Compressor screen.
 */
public final class SpaceClient {
    static final StandaloneModelKey<BlockStateModelPart> CAPSULE = key("crew_capsule_lv");
    static final StandaloneModelKey<BlockStateModelPart> JETPACK_ELECTRIC = key("jetpack_electric_worn");
    static final StandaloneModelKey<BlockStateModelPart> JETPACK_ADVANCED = key("jetpack_advanced_worn");
    /** Where the capsule model's y = 0 sits in rocket pixels (tools/features/space.py CAPSULE_BASE). */
    private static final float CAPSULE_BASE = 110f;

    static final KeyMapping HOVER_KEY = new KeyMapping("key.factoryascent.jetpack_hover", InputConstants.Type.KEYSYM,
            InputConstants.KEY_H, new KeyMapping.Category(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "space_gear")));

    private static boolean sentThrust;
    /** Client ticks of re-entry shaking left, and its length. */
    static int reentryTicks, reentryLength = 1;
    /** Last breathing state the server reported, and when (client ticks). */
    static int breath = -1;
    static long breathAt;

    private SpaceClient() {}

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterStandalone e) -> {
            for (var k : List.of(java.util.Map.entry(CAPSULE, "crew_capsule_lv"),
                    java.util.Map.entry(JETPACK_ELECTRIC, "jetpack_electric_worn"),
                    java.util.Map.entry(JETPACK_ADVANCED, "jetpack_advanced_worn"))) {
                e.register(k.getKey(), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/" + k.getValue())));
            }
            for (var k : List.of(SatelliteSkyClient.SURVEY, SatelliteSkyClient.UPLINK, SatelliteSkyClient.GUARDIAN)) {
                e.register(k, SimpleUnbakedStandaloneModel.simpleModelWrapper(SatelliteSkyClient.modelId(k)));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(SpaceContent.ROCKET_SEAT.get(), NoopRenderer::new));
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> {
            for (var skin : e.getSkins()) {
                var renderer = e.getPlayerRenderer(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
                    addJetpackLayer(avatar);
                }
            }
        });
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(SpaceContent.OXYGEN_COMPRESSOR_MENU.get(), OxygenCompressorScreen::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(HOVER_KEY.getCategory());
            e.register(HOVER_KEY);
        });
        modBus.addListener((RegisterRenderPipelinesEvent e) -> e.registerPipeline(SpaceSkies.PIPELINE));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "space_hud"), SpaceHud::render));
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(SpacePayloads.Reentry.TYPE, (payload, context) -> {
                reentryTicks = payload.ticks();
                reentryLength = Math.max(1, payload.ticks());
            });
            e.register(net.juli2kapo.factoryascent.orbital.SatelliteSky.Payload.TYPE, (payload, context) -> SatelliteSkyClient.receive(payload));
            e.register(net.juli2kapo.factoryascent.space.station.StationPayloads.StationView.TYPE,
                    (payload, context) -> StationScreens.handleView(payload));
            e.register(net.juli2kapo.factoryascent.space.station.StationPayloads.StarChart.TYPE,
                    (payload, context) -> StationScreens.handleChart(payload));
            e.register(SpacePayloads.Breathing.TYPE, (payload, context) -> {
                breath = payload.breath();
                Minecraft mc = Minecraft.getInstance();
                breathAt = mc.level == null ? 0 : mc.level.getGameTime();
            });
        });
        NeoForge.EVENT_BUS.addListener(SpaceClient::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (HOVER_KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null) ClientPacketDistributor.sendToServer(new SpacePayloads.JetpackHover());
            }
            if (reentryTicks > 0) reentryTicks--;
            DustStormClient.tick();
        });
        NeoForge.EVENT_BUS.addListener(SpaceClient::shake);
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterSky e) -> SpaceSkies.afterSky(e));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) -> SatelliteSkyClient.submit(e));
        NeoForge.EVENT_BUS.addListener((ViewportEvent.RenderFog e) -> DustStormClient.fog(e));
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeFogColor e) -> DustStormClient.fogColor(e));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addJetpackLayer(net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
        ((net.minecraft.client.renderer.entity.LivingEntityRenderer) avatar).addLayer(new JetpackLayer(avatar));
    }

    /** Jetpack: report jump to the server and predict the push for our own player. */
    private static void onPlayerTick(PlayerTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e.getEntity() instanceof LocalPlayer player) || player != mc.player) return;
        ItemStack pack = Jetpack.worn(player);
        boolean key = !pack.isEmpty() && mc.gui.screen() == null && mc.options.keyJump.isDown();
        if (key != sentThrust) {
            sentThrust = key;
            ClientPacketDistributor.sendToServer(new SpacePayloads.JetpackInput(key));
        }
        if (pack.isEmpty()) return;
        Jetpack.Mode mode = Jetpack.mode(player, pack, key);
        Vec2 move = player.input.getMoveVector();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        double dx = move.x * cos - move.y * sin, dz = move.y * cos + move.x * sin;
        Jetpack.push(player, Jetpack.tier(pack), mode, dx, dz);
    }

    /** Re-entry rattles the camera; so does the climb in a rocket. */
    private static void shake(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float amp = 0;
        double t = mc.player.tickCount + e.getPartialTick();
        if (reentryTicks > 0) amp = 2.5f * reentryTicks / reentryLength;
        if (mc.player.getVehicle() instanceof net.juli2kapo.factoryascent.space.RocketSeatEntity seat) {
            var pad = mc.level.getBlockEntity(seat.pad());
            if (pad instanceof net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity c) {
                long tick = net.juli2kapo.factoryascent.space.RocketSeatEntity.elapsed(c);
                if (tick >= 0) amp = Math.max(amp, tick >= net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity.LIFTOFF ? 0.9f : 0.25f);
            }
        }
        if (amp <= 0) return;
        e.setYaw(e.getYaw() + amp * (float) Math.sin(t * 2.9));
        e.setPitch(e.getPitch() + amp * (float) Math.cos(t * 3.7));
        e.setRoll(e.getRoll() + amp * 0.6f * (float) Math.sin(t * 5.3));
    }

    /** Draws the crew capsule on the rocket (called by the rocket renderer; pose at the pad plate, centred). */
    public static void submitCapsule(PoseStack pose, SubmitNodeCollector collector, int light) {
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(CAPSULE);
        if (model == null) return;
        pose.pushPose();
        pose.translate(-0.5f, CAPSULE_BASE / 16f, -0.5f);
        collector.submitBlockModel(pose, Sheets.translucentBlockItemSheet(), List.of(model), new int[0],
                light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}

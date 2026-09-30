package net.juli2kapo.factoryascent.power.client;

import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.fusion.client.TokamakScreen;
import net.juli2kapo.factoryascent.nuclear.GeigerCounterItem;
import net.juli2kapo.factoryascent.nuclear.Radiation;
import net.juli2kapo.factoryascent.nuclear.RadiationState;
import net.juli2kapo.factoryascent.nuclear.client.ReactorScreen;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the power ladder: generator, reactor and tokamak screens, the spinning parts
 * and the plasma ring, and radiation: the HUD read-out (with the Geiger counter or when exposed)
 * and the Geiger counter's clicks.
 */
public final class PowerClient {
    static final SpinRenderer.Spec DYNAMO = new SpinRenderer.Spec(SpinRenderer.key("kinetic_dynamo_armature"), 8, 12, 8,
            SpinRenderer.SpinAxis.Y, 14f, 1f, 0.5f);
    static final SpinRenderer.Spec STEAM = new SpinRenderer.Spec(SpinRenderer.key("steam_engine_flywheel"), 17, 8, 9,
            SpinRenderer.SpinAxis.X, 10f, 1f, 1f);
    static final SpinRenderer.Spec WIND = new SpinRenderer.Spec(SpinRenderer.key("wind_turbine_rotor"), 8, 9, -1.5f,
            SpinRenderer.SpinAxis.Z, 5f, 3f, 3.5f);

    private static long lastClick = -100;

    private PowerClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(PowerContent.POWER_MENU.get(), PowerScreen::new);
            e.register(PowerContent.REACTOR_MENU.get(), ReactorScreen::new);
            e.register(PowerContent.TOKAMAK_MENU.get(), TokamakScreen::new);
        });
        modBus.addListener((ModelEvent.RegisterStandalone e) -> {
            for (var entry : Map.of(DYNAMO.model(), "kinetic_dynamo_armature", STEAM.model(), "steam_engine_flywheel",
                    WIND.model(), "wind_turbine_rotor", PlasmaRenderer.RING, "plasma_ring", PlasmaRenderer.FILAMENT, "plasma_filament").entrySet()) {
                e.register(entry.getKey(), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/" + entry.getValue())));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(PowerContent.generatorType(Generator.KINETIC_DYNAMO).get(), c -> new SpinRenderer(DYNAMO, c));
            e.registerBlockEntityRenderer(PowerContent.generatorType(Generator.STEAM_ENGINE).get(), c -> new SpinRenderer(STEAM, c));
            e.registerBlockEntityRenderer(PowerContent.generatorType(Generator.WIND_TURBINE).get(), c -> new SpinRenderer(WIND, c));
            e.registerBlockEntityRenderer(PowerContent.TOKAMAK_CORE_BE.get(), PlasmaRenderer::new);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "radiation_hud"), PowerClient::hud));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> geiger());
    }

    /** Geiger counter: random clicks, faster with the dose rate around you (before the suit's shielding). */
    private static void geiger() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || !GeigerCounterItem.holding(player)) return;
        float exposure = player.getData(PowerContent.RADIATION_STATE.get()).exposure();
        float chance = Math.min(0.85f, 0.015f + exposure * 0.06f);
        if (player.getRandom().nextFloat() < chance) {
            lastClick = mc.level.getGameTime();
            mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), PowerContent.GEIGER_CLICK.get(), SoundSource.PLAYERS,
                    0.5f, 0.9f + player.getRandom().nextFloat() * 0.3f, false);
        }
    }

    /** Top left: trefoil, dose rate and absorbed dose; a sickly green edge while radiation-sick. */
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        RadiationState s = player.getData(PowerContent.RADIATION_STATE.get());
        boolean geiger = GeigerCounterItem.holding(player);
        if (!geiger && s.dose() < 1f && s.rate() < 0.01f) return;
        Font font = mc.font;
        long time = mc.level.getGameTime();
        int sickness = Radiation.sickness(s.dose());
        if (sickness >= 1) {
            // a sickly green glow creeping in from the screen edges, pulsing
            float a = (0.22f + 0.12f * sickness) * (0.65f + 0.35f * Mth.sin(time * 0.15f));
            int w = g.guiWidth(), h = g.guiHeight(), depth = 18 + 8 * sickness;
            for (int i = 0; i < depth; i += 2) {
                int c = ((int) (a * 255 * (depth - i) / depth) << 24) | 0x5FD13B;
                g.fill(i, i, w - i, i + 2, c);
                g.fill(i, h - i - 2, w - i, h - i, c);
                g.fill(i, i + 2, i + 2, h - i - 2, c);
                g.fill(w - i - 2, i + 2, w - i, h - i - 2, c);
            }
        }
        float shown = geiger ? s.exposure() : s.rate();
        int color = shown >= 5 ? 0xFFFF5040 : shown >= 0.5 ? 0xFFFFC040 : 0xFF7FD13B;
        int x = 4, y = 4;
        g.fill(x - 2, y - 2, x + 118, y + (geiger ? 30 : 20), 0x90101418);
        boolean click = geiger && time - lastClick < 2;
        PowerScreen.trefoil(g, x, y, click ? 0xFFFFFFFF : color);
        g.text(font, Component.translatable("hud.factoryascent.rad_rate", Radiation.format(shown)), x + 20, y, color, true);
        g.text(font, Component.translatable("hud.factoryascent.rad_dose", Math.round(s.dose())), x + 20, y + 10,
                sickness >= 0 ? 0xFFFF7060 : 0xFFD0D8E0, true);
        if (geiger) {
            float shield = s.exposure() <= 0 ? 0 : 1f - s.rate() / Math.max(0.0001f, s.exposure());
            Component line = shield > 0.01f ? Component.translatable("hud.factoryascent.rad_shield", Math.round(shield * 100))
                    : Component.translatable("hud.factoryascent.rad_geiger");
            g.text(font, line, x + 20, y + 20, 0xFF9AA4B0, true);
        }
        FactoryGui.bar(g, x, y + 18, 17, 3, Math.min(1f, s.dose() / Radiation.SICKNESS[3]), sickness >= 0 ? 0xFFE05040 : 0xFF7FD13B);
    }
}

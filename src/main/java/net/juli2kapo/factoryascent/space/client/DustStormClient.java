package net.juli2kapo.factoryascent.space.client;

import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.space.planet.PlanetHazards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Martian dust storms, on the client: while {@link PlanetHazards#dustStorm} blows, rusty dust
 * streams past on the wind and the view closes in to a few dozen blocks of orange haze.
 */
final class DustStormClient {
    private static final int DUST = 0xC06A3C, DUST_DARK = 0x8A4A2A;
    private static final float HAZE_R = 0.78f, HAZE_G = 0.48f, HAZE_B = 0.30f;

    private DustStormClient() {}

    static float storm(ClientLevel level, float partial) {
        if (Planet.of(level) != Planet.MARS || !SpaceConfig.get(SpaceConfig.DUST_STORMS)) return 0f;
        return PlanetHazards.dustStorm(level.getGameTime());
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        float storm = storm(level, 0f);
        if (storm <= 0.03f) return;
        RandomSource r = level.getRandom();
        double wind = 0.35 + 0.5 * storm;
        int count = Mth.ceil(30 * storm);
        for (int i = 0; i < count; i++) {
            double x = mc.player.getX() + (r.nextDouble() - 0.5) * 24;
            double y = mc.player.getY() + r.nextDouble() * 8 - 1;
            double z = mc.player.getZ() + (r.nextDouble() - 0.5) * 24;
            DustParticleOptions dust = new DustParticleOptions(r.nextInt(3) == 0 ? DUST_DARK : DUST, 0.7f + r.nextFloat() * 0.9f);
            level.addParticle(dust, x, y, z, wind, (r.nextDouble() - 0.5) * 0.05, wind * 0.3);
        }
    }

    static void fog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (event.getType() != FogType.ATMOSPHERIC && event.getType() != FogType.NONE)) return;
        float storm = storm(mc.level, (float) event.getPartialTick());
        if (storm <= 0.01f) return;
        float far = event.getFarPlaneDistance();
        float target = Mth.lerp(storm, far, 30f);
        event.setFarPlaneDistance(Math.min(far, target));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), 0f));
        event.getFogData().skyEnd = Math.min(event.getFogData().skyEnd, Mth.lerp(storm, event.getFogData().skyEnd, 45f));
    }

    static void fogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float storm = storm(mc.level, (float) event.getPartialTick());
        if (storm <= 0.01f) return;
        float light = 0.45f + 0.55f * SpaceSkies.brightness(mc.level, (float) event.getPartialTick());
        event.setRed(Mth.lerp(storm, event.getRed(), HAZE_R * light));
        event.setGreen(Mth.lerp(storm, event.getGreen(), HAZE_G * light));
        event.setBlue(Mth.lerp(storm, event.getBlue(), HAZE_B * light));
    }
}

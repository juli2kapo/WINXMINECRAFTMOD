package net.juli2kapo.factoryascent.space.planet;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

/**
 * What the planets do to visitors, every tick on the server: landing on one earns its
 * advancement, and Io's surface burns anyone outside a sealed cabin or room whose suit has no
 * Thermal Lining ({@link SpaceConfig#HEAT_DAMAGE} a second). Mars' dust storms are pure weather,
 * computed from the game time on both sides ({@link #dustStorm}).
 */
public final class PlanetHazards {
    /** Damage type of Io's heat ({@code data/factoryascent/damage_type/heat.json}). */
    public static final ResourceKey<DamageType> HEAT =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "heat"));
    /** Length of Mars' weather cycle in ticks: a storm blows for the last third of it. */
    public static final int STORM_CYCLE = 18000;

    private PlanetHazards() {}

    public static void tickPlayer(ServerPlayer player, SpaceRules.Breath breath) {
        Planet planet = Planet.of(player.level());
        if (planet == null) return;
        if (player.tickCount % 20 == 0 && player.onGround() && !player.isSpectator()) {
            SpaceContent.award(player, "planet_" + planet.id);
        }
        if (planet.hot() && player.tickCount % 20 == 0 && burns(player, breath)) {
            ServerLevel level = player.level();
            player.hurtServer(level, heat(level), (float) SpaceConfig.get(SpaceConfig.HEAT_DAMAGE));
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.2, player.getZ(), 4, 0.45, 0.1, 0.45, 0.01);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.too_hot").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /** Whether Io's heat gets through to this player right now. */
    public static boolean burns(ServerPlayer player, SpaceRules.Breath breath) {
        if (player.isCreative() || player.isSpectator()) return false;
        if (SpaceConfig.get(SpaceConfig.HEAT_DAMAGE) <= 0) return false;
        return heatReaches(breath, ThermalLiningItem.lined(player.getItemBySlot(EquipmentSlot.CHEST)));
    }

    /** The rule: a sealed cabin or room shields you; outside, only a lined suit does. */
    public static boolean heatReaches(SpaceRules.Breath breath, boolean lined) {
        if (breath == SpaceRules.Breath.CABIN || breath == SpaceRules.Breath.BUBBLE) return false;
        return !lined;
    }

    public static DamageSource heat(Level level) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(HEAT)
                .map(DamageSource::new).orElseGet(() -> level.damageSources().hotFloor());
    }

    /**
     * How hard a Martian dust storm blows at this game time, 0 (calm) to 1: the storm builds up,
     * rages and dies down over the last third of every {@link #STORM_CYCLE}.
     */
    public static float dustStorm(long gameTime) {
        double phase = Math.floorMod(gameTime, STORM_CYCLE) / (double) STORM_CYCLE;
        double start = 2.0 / 3.0;
        if (phase < start) return 0f;
        double t = (phase - start) / (1 - start);   // 0..1 across the storm
        double envelope = Math.sin(Math.PI * t);    // up and down again
        double gusts = 0.85 + 0.15 * Math.sin(gameTime / 37.0);
        return (float) Math.max(0, Math.min(1, envelope * 1.25 * gusts));
    }
}

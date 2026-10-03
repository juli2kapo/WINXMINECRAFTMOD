package net.juli2kapo.factoryascent.space;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Getting to orbit and back.
 *
 * <p>Orbit ({@link SpaceRules#ORBIT}) is a void over the planet. Each launch site has its own spot
 * up there, straight above it (same x and z, at {@link #DECK_Y}). Nothing is built for you: an
 * arrival lands at the team's station there (a Station Kit launched from the same pad builds one),
 * on whatever else stands there, or stays up in its capsule as a floating crew pod.
 * Gravity is a fraction of normal ({@link SpaceConfig#ORBIT_GRAVITY}) and falls hurt less.
 *
 * <p>Coming back ({@link #reenter}): the Return Pod drops the player back over their launch site
 * (or the world spawn) high in the sky, burning through the atmosphere, then drifting down under
 * a parachute (slow falling) until they land.
 */
public final class Orbit {
    public static final int DECK_Y = 100;
    private static final Identifier GRAVITY_ID = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbit_gravity");
    /** Players falling back from orbit: ticks since re-entry began. */
    private static final Map<UUID, Integer> REENTRY = new ConcurrentHashMap<>();

    private Orbit() {}

    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(SpaceRules.ORBIT);
    }

    /** The spot in orbit above a launch site. */
    public static BlockPos deckCentre(BlockPos launchSite) {
        return new BlockPos(launchSite.getX(), DECK_Y, launchSite.getZ());
    }

    // ---------------------------------------------------------------- arriving

    /**
     * Puts a player who rode a rocket from {@code site} into orbit above it. Returns false (and
     * leaves the player alone) if the orbit dimension isn't loaded. They land at their team's
     * station above the site, or on whatever is built there; with nothing there the capsule stays
     * up as a floating crew pod with them inside ({@link net.juli2kapo.factoryascent.stationkit.CrewPods}).
     */
    public static boolean arrive(ServerPlayer player, ResourceKey<Level> fromDimension, BlockPos site) {
        ServerLevel orbit = level(player.level().getServer());
        if (orbit == null) return false;
        BlockPos centre = deckCentre(site);
        orbit.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        String team = net.juli2kapo.factoryascent.orbital.FactoryTeams.get(orbit.getServer()).teamOf(player.getUUID());
        BlockPos spot = landingSpot(orbit, centre, team);
        boolean pod = spot == null;
        if (pod) spot = net.juli2kapo.factoryascent.stationkit.CrewPods.podSpot(orbit, centre);
        player.setData(SpaceContent.RETURN_POINT.get(), GlobalPos.of(fromDimension, site));
        player.teleport(new TeleportTransition(orbit, Vec3.atBottomCenterOf(spot), Vec3.ZERO, 180f, 10f,
                TeleportTransition.DO_NOTHING));
        orbit.playSound(null, spot, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
        if (pod) {
            net.juli2kapo.factoryascent.stationkit.CrewPods.arrive(orbit, player, spot);
        } else {
            player.sendSystemMessage(Component.translatable("message.factoryascent.orbit_arrived").withStyle(ChatFormatting.AQUA));
        }
        if (!pod && SpaceRules.breathing(player, true) == SpaceRules.Breath.NONE) {
            player.sendSystemMessage(Component.translatable("message.factoryascent.orbit_no_suit").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        SpaceContent.award(player, "space_orbit");
        return true;
    }

    /**
     * Where an arrival stands, or null if nothing is built above the launch site: inside the team's
     * station claiming that spot (next to its Station Core), else on whatever stands in the column
     * above the site.
     */
    public static @Nullable BlockPos landingSpot(ServerLevel orbit, BlockPos centre, String team) {
        var station = net.juli2kapo.factoryascent.space.station.StationRegistry.get(orbit.getServer())
                .claiming(orbit.dimension(), centre, SpaceConfig.get(SpaceConfig.STATION_RADIUS));
        if (station != null && station.team().equals(team)) {
            BlockPos inside = standingSpotNear(orbit, station.pos());
            if (inside != null) return inside;
        }
        return columnSpot(orbit, centre);
    }

    /** The first floor with two blocks of air above it in the column through {@code centre} (±24 blocks), or null. */
    public static @Nullable BlockPos columnSpot(Level orbit, BlockPos centre) {
        for (int y = DECK_Y + 24; y >= DECK_Y - 24; y--) {
            BlockPos p = new BlockPos(centre.getX(), y, centre.getZ());
            if (!orbit.getBlockState(p).isAir() && orbit.getBlockState(p.above()).isAir() && orbit.getBlockState(p.above(2)).isAir()) {
                return p.above();
            }
        }
        return null;
    }

    /** A spot to stand near a block (a Station Core): nearest floor with two air blocks above, within 4 blocks. */
    public static @Nullable BlockPos standingSpotNear(Level level, BlockPos core) {
        for (int r = 0; r <= 4; r++) {
            for (int dy = -1; dy >= -3; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos floor = core.offset(dx, dy, dz);
                        if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                                && level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()
                                && level.getBlockState(floor.above(2)).getCollisionShape(level, floor.above(2)).isEmpty()
                                && !level.getBlockState(floor.above()).isSolid()) {
                            return floor.above();
                        }
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- coming back

    /** The Return Pod: back over the launch site (or the world spawn), with a fiery re-entry. */
    public static void reenter(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        GlobalPos back = player.hasData(SpaceContent.RETURN_POINT.get()) ? player.getData(SpaceContent.RETURN_POINT.get()) : null;
        ServerLevel target = back == null ? null : server.getLevel(back.dimension());
        BlockPos site;
        if (target == null || SpaceRules.isAirless(target)) {
            target = server.overworld();
            site = target.getRespawnData().pos();
        } else {
            site = back.pos();
        }
        target.getChunk(site.getX() >> 4, site.getZ() >> 4);
        int ground = target.getHeight(Heightmap.Types.MOTION_BLOCKING, site.getX(), site.getZ());
        double y = Math.min(ground + 90, target.getMaxY() - 4);
        player.stopRiding();
        player.teleport(new TeleportTransition(target, new Vec3(site.getX() + 0.5, y, site.getZ() + 0.5), Vec3.ZERO,
                player.getYRot(), 35f, TeleportTransition.DO_NOTHING));
        startReentry(player);
    }

    /**
     * Falling out of orbit: anyone who drops below the bottom of the orbit dimension (stepping off
     * a station, leaving a shuttle) re-enters the Overworld straight below where they fell, burning
     * through the atmosphere like a Return Pod ride instead of dying in the void.
     */
    public static void fallOutOfOrbit(ServerPlayer player) {
        if (!player.level().dimension().equals(SpaceRules.ORBIT) || player.isPassenger() || player.isSpectator()) return;
        if (player.getY() > player.level().getMinY() + FALL_OUT_MARGIN) return;
        ServerLevel target = player.level().getServer().overworld();
        int x = player.getBlockX(), z = player.getBlockZ();
        target.getChunk(x >> 4, z >> 4);
        int ground = target.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        double y = Math.min(ground + 90, target.getMaxY() - 4);
        player.teleport(new TeleportTransition(target, new Vec3(player.getX(), y, player.getZ()), Vec3.ZERO,
                player.getYRot(), 35f, TeleportTransition.DO_NOTHING));
        startReentry(player);
    }

    /** How far above the orbit dimension's floor a falling player is sent back down (well before the void). */
    static final int FALL_OUT_MARGIN = 8;

    static void startReentry(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 120, 0, false, false, true));
        player.resetFallDistance();
        REENTRY.put(player.getUUID(), 0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.5f);
        player.sendSystemMessage(Component.translatable("message.factoryascent.reentry").withStyle(ChatFormatting.GOLD));
        if (player.connection != null && player.connection.hasChannel(SpacePayloads.Reentry.TYPE)) {
            PacketDistributor.sendToPlayer(player, new SpacePayloads.Reentry(REENTRY_BURN));
        }
    }

    /** Ticks of burning through the atmosphere (flames, shaking screen). */
    public static final int REENTRY_BURN = 80;

    public static boolean reentering(ServerPlayer player) {
        return REENTRY.containsKey(player.getUUID());
    }

    static void tickReentry(ServerPlayer player) {
        Integer t = REENTRY.get(player.getUUID());
        if (t == null) return;
        t++;
        REENTRY.put(player.getUUID(), t);
        player.resetFallDistance();
        ServerLevel level = player.level();
        if (t < REENTRY_BURN) {
            double x = player.getX(), y = player.getY(), z = player.getZ();
            level.sendParticles(ParticleTypes.FLAME, true, true, x, y + 0.2, z, 12, 0.5, 0.4, 0.5, 0.06);
            level.sendParticles(ParticleTypes.LAVA, true, true, x, y, z, 2, 0.4, 0.2, 0.4, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y + 1.5, z, 3, 0.4, 0.8, 0.4, 0.02);
            if (t % 10 == 1) level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2f, 0.6f);
        } else if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2.4, player.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
        }
        boolean landed = t > 5 && (player.onGround() || player.isInWater() || player.isInLava());
        if (landed || t > 20 * 150 || player.isDeadOrDying()) {
            REENTRY.remove(player.getUUID());
            player.removeEffect(MobEffects.SLOW_FALLING);
            if (landed) {
                player.sendSystemMessage(Component.translatable("message.factoryascent.reentry_landed").withStyle(ChatFormatting.GREEN));
                SpaceContent.award(player, "space_reentry");
            }
        }
    }

    static void forget(UUID player) {
        REENTRY.remove(player);
    }

    // ---------------------------------------------------------------- gravity

    /** Gravity of a dimension as a fraction of normal: orbit and the planets are lighter (config). */
    public static double gravityFor(ResourceKey<Level> dimension) {
        if (dimension.equals(SpaceRules.ORBIT)) return SpaceConfig.orbitGravity();
        net.juli2kapo.factoryascent.space.planet.Planet planet = net.juli2kapo.factoryascent.space.planet.Planet.of(dimension);
        return planet != null ? planet.gravity() : 1.0;
    }

    /**
     * Low gravity (and softer falls) in orbit and on the planets; normal elsewhere. Magnetic Boots
     * hold their wearer to the floor under them at normal gravity (see
     * {@link net.juli2kapo.factoryascent.space.station.MagneticBoots}).
     */
    static void applyGravity(LivingEntity entity) {
        double g = gravityFor(entity.level().dimension());
        double fall = g;
        g = net.juli2kapo.factoryascent.space.station.MagneticBoots.gravity(entity, g);
        modifier(entity.getAttribute(Attributes.GRAVITY), g != 1.0, g - 1.0);
        modifier(entity.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), fall != 1.0, fall - 1.0);
    }

    private static void modifier(@Nullable AttributeInstance attribute, boolean on, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(GRAVITY_ID);
        if (on) {
            if (current != null && current.amount() == amount) return;
            if (current != null) attribute.removeModifier(GRAVITY_ID);
            attribute.addTransientModifier(new AttributeModifier(GRAVITY_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (current != null) {
            attribute.removeModifier(GRAVITY_ID);
        }
    }
}

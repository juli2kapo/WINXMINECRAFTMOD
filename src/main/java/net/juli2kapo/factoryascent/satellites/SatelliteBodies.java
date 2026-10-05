package net.juli2kapo.factoryascent.satellites;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.gear.GearContent;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.Satellite;
import net.juli2kapo.factoryascent.ships.AbstractShip;
import net.juli2kapo.factoryascent.ships.ShipContent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a body ({@link OrbitingSatellite}) in Earth orbit for each of the Overworld's satellites
 * while the orbit chunk under it is ticking (players nearby), and crashes ships into them.
 *
 * <p>Nothing is saved and nothing is chunk-loaded: every half second each satellite's position is
 * computed ({@link SatelliteOrbit}); a body is added where that chunk ticks and removed where it
 * doesn't, or once the satellite left the registry (deorbit, missile, collision).
 */
public final class SatelliteBodies {
    /** Server: the live body of each satellite. */
    private static final Map<UUID, OrbitingSatellite> LIVE = new HashMap<>();
    /** Client: satellites whose body ticked lately (game time), so the sky doesn't draw them twice. */
    private static final Map<UUID, Long> SEEN = new ConcurrentHashMap<>();

    /** Entity damage from the crash is capped (the crew is thrown clear, bruised); debris and bodies are spared. */
    private static final ExplosionDamageCalculator CRASH = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldBlockExplode(Explosion explosion, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                          net.minecraft.world.level.block.state.BlockState state, float power) {
            return false;
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return !(entity instanceof ItemEntity) && !(entity instanceof OrbitingSatellite);
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
            return Math.min(4f, super.getEntityDamageAmount(explosion, entity, exposure));
        }
    };

    private SatelliteBodies() {}

    // ---------------------------------------------------------------- bodies

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        ServerLevel orbit = server.getLevel(SpaceRules.ORBIT);
        if (orbit == null) return;
        if (!SatelliteConfig.physical()) {
            clear();
            return;
        }
        long time = orbit.getGameTime();
        Set<UUID> wanted = new HashSet<>();
        for (OrbitRegistry.Owned owned : OrbitRegistry.get(server).everyOver(Level.OVERWORLD)) {
            Satellite s = owned.satellite();
            wanted.add(s.id());
            OrbitingSatellite body = LIVE.get(s.id());
            if (body != null && (body.isRemoved() || body.level() != orbit)) {
                LIVE.remove(s.id());
                body = null;
            }
            Vec3 pos = SatelliteOrbit.position(server, s, time);
            boolean ticking = orbit.isPositionEntityTicking(BlockPos.containing(pos));
            if (ticking && body == null) {
                body = SatellitesContent.ORBITING_SATELLITE.get().create(orbit, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                if (body == null) continue;
                body.link(s.id(), s.type(), s.launchTime(), SatelliteOrbit.center(server, s), SatelliteConfig.altitude());
                if (orbit.addFreshEntity(body)) LIVE.put(s.id(), body);
            } else if (!ticking && body != null) {
                body.discard();
                LIVE.remove(s.id());
            }
        }
        for (Iterator<Map.Entry<UUID, OrbitingSatellite>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!wanted.contains(e.getKey()) || e.getValue().isRemoved()) {
                e.getValue().discard();
                it.remove();
            }
        }
    }

    /** The live body of a satellite (server), if one is flying now. */
    public static @Nullable OrbitingSatellite body(UUID satellite) {
        OrbitingSatellite b = LIVE.get(satellite);
        return b == null || b.isRemoved() ? null : b;
    }

    public static void clear() {
        LIVE.values().forEach(Entity::discard);
        LIVE.clear();
    }

    /** Client: a body ticked at {@code time}. */
    static void seenOnClient(UUID satellite, long time) {
        SEEN.put(satellite, time);
    }

    /** Client: whether the satellite's body is here and drawn (so the sky leaves it out). */
    public static boolean presentOnClient(UUID satellite, long time) {
        Long t = SEEN.get(satellite);
        return t != null && time - t <= 3;
    }

    // ---------------------------------------------------------------- the crash

    /** What a collision did (for the game tests). */
    public record Crash(@Nullable Satellite satellite, String owner, List<ServerPlayer> crew) {}

    /**
     * A ship touched a satellite's body: the satellite leaves the registry (its team is told, as for
     * a missile), the ship breaks apart (its cargo and some Scrap float where it was; the hull only
     * with {@code wreckDropsShip}), the crew is thrown clear (to fall and re-enter like anyone in
     * orbit) and both go up in one explosion that hurts but never breaks blocks.
     */
    public static Crash collide(ServerLevel level, OrbitingSatellite body, AbstractShip ship) {
        MinecraftServer server = level.getServer();
        UUID id = body.satelliteId();
        var removed = OrbitRegistry.get(server).remove(id);
        LIVE.remove(id);
        Vec3 at = body.position().add(ship.position()).scale(0.5).add(0, 1, 0);
        List<ServerPlayer> crew = new ArrayList<>();
        for (Entity p : ship.getPassengers()) {
            if (p instanceof ServerPlayer sp) crew.add(sp);
        }
        Player pilot = ship.pilot();
        Component shipName = ship.getDisplayName();
        // ---- the wreck: cargo and scrap float away, nothing falls into the void
        for (int i = 0; i < ship.getContainerSize(); i++) {
            ItemStack stack = ship.getItem(i);
            if (!stack.isEmpty()) debris(level, at, stack.copy());
        }
        ship.clearContent();
        if (SatelliteConfig.wreckDropsShip()) debris(level, at, new ItemStack(ShipContent.itemFor(ship.getType())));
        int scrap = SatelliteConfig.wreckScrap();
        if (scrap > 0) debris(level, at, new ItemStack(GearContent.SCRAP.get(), scrap));
        List<Entity> riders = new ArrayList<>(ship.getPassengers());
        ship.kill(level); // removing it throws everyone off
        body.discard();
        level.explode(null, null, CRASH, at.x, at.y, at.z, 4f, false, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, at.x, at.y, at.z, 3, 1.5, 1.0, 1.5, 0);
        level.sendParticles(ParticleTypes.FLAME, true, true, at.x, at.y, at.z, 80, 2.0, 1.5, 2.0, 0.25);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 60, 2.5, 2.0, 2.5, 0.08);
        level.sendParticles(ParticleTypes.FIREWORK, true, true, at.x, at.y, at.z, 120, 1.0, 1.0, 1.0, 0.6);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 6f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 2f, 0.5f);
        for (Entity rider : riders) {
            Vec3 away = rider.position().subtract(body.position()).normalize();
            if (away.lengthSqr() < 1e-4) away = new Vec3(0, 1, 0);
            rider.push(away.x * 0.9, 0.35 + away.y * 0.3, away.z * 0.9);
            rider.hurtMarked = true;
        }
        // ---- the news
        String owner = "";
        Satellite satellite = removed.map(OrbitRegistry.Owned::satellite).orElse(null);
        if (removed.isPresent()) {
            FactoryTeams teams = FactoryTeams.get(server);
            owner = removed.get().team();
            Component type = satellite.type().displayName();
            Component news = pilot != null
                    ? Component.translatable("message.factoryascent.satellite_collision_owner", type, satellite.name(),
                            pilot.getName(), shipName)
                    : Component.translatable("message.factoryascent.satellite_collision_drifting", type, satellite.name(), shipName);
            for (UUID member : teams.members(owner)) {
                ServerPlayer p = server.getPlayerList().getPlayer(member);
                if (p != null && !crew.contains(p)) {
                    p.sendSystemMessage(news.copy().withStyle(ChatFormatting.RED));
                }
            }
            for (ServerPlayer p : crew) {
                p.sendSystemMessage(Component.translatable("message.factoryascent.satellite_collision_crew", type, satellite.name(),
                        shipName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
        }
        AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbital_satellite_collision"));
        if (holder != null) crew.forEach(p -> p.getAdvancements().award(holder, "done"));
        return new Crash(satellite, owner, crew);
    }

    /** A piece of wreckage drifting in orbit (no gravity: it stays where the crash was to be picked up). */
    private static void debris(ServerLevel level, Vec3 at, ItemStack stack) {
        var r = level.getRandom();
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack,
                (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.3);
        item.setNoGravity(level.dimension().equals(SpaceRules.ORBIT));
        item.setPickUpDelay(30);
        level.addFreshEntity(item);
    }
}

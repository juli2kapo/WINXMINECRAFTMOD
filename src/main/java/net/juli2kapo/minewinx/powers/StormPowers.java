package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.TornadoEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StormPowers {

    private static final Map<UUID, FieldState> activeFields = new ConcurrentHashMap<>();
    private static final Map<UUID, RideState> activeRides = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    /**
     * Slot 1: campo de tormenta que sigue a la lanzadora. Mientras dura, caen
     * rayos al azar dentro del radio alrededor de su posición ACTUAL.
     */
    public static void summonStormField(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        double radius;
        int meanStrikeInterval; // en ticks
        int durationTicks;
        switch (stage) {
            case 1 -> { radius = 8.0;  meanStrikeInterval = 15; durationTicks = 15 * 20; }
            case 2 -> { radius = 12.0; meanStrikeInterval = 10; durationTicks = 20 * 20; }
            default -> { radius = 16.0; meanStrikeInterval = 6; durationTicks = 25 * 20; }
        }

        // Re-lanzar refresca la duración (no se apila)
        activeFields.put(player.getUUID(), new FieldState(radius, meanStrikeInterval, durationTicks));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.8F);
    }

    /**
     * Slot 2: invoca un tornado en el punto al que mira la jugadora.
     */
    public static void summonTornado(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        double maxRange = 40.0;
        Vec3 eyePos = player.getEyePosition();
        Vec3 endPos = eyePos.add(player.getViewVector(1.0F).scale(maxRange));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 targetPos = blockHit.getType() == HitResult.Type.MISS ? endPos : blockHit.getLocation();

        // Hasta 3 tornados (según stage), repartidos y errantes: al moverse
        // sueltan a las víctimas en el aire → daño de caída
        int count = Math.min(3, Math.max(1, stage));
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2.0 / count);
            double spread = count == 1 ? 0.0 : 3.5;
            TornadoEntity tornado = new TornadoEntity(ModEntities.TORNADO.get(), level, player, stage);
            tornado.setPos(targetPos.x + Math.cos(angle) * spread, targetPos.y,
                    targetPos.z + Math.sin(angle) * spread);
            serverLevel.addFreshEntity(tornado);
        }
        serverLevel.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    /**
     * Slot 3 (ultimate): Cabalgar la Tormenta. Stormy monta una nube y el viento
     * la arrastra en la dirección en la que mira, soltando rayos sobre los
     * enemigos que sobrevuela.
     *
     * OJO: NO toca mayfly/flying a propósito. El mod prohíbe volar y
     * ServerEvents.tickPlayer limpia el vuelo residual cada tick; acá la
     * velocidad se fija a mano, así que esa limpieza no se entera ni hace falta
     * meterle una excepción.
     */
    public static void rideTheStorm(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        double speed;
        double strikeRadius;
        int meanStrikeInterval;
        int durationTicks;
        float damage;
        switch (stage) {
            case 1 -> { speed = 0.55; strikeRadius = 6.0;  meanStrikeInterval = 20; durationTicks = 6 * 20;  damage = 6.0F; }
            case 2 -> { speed = 0.70; strikeRadius = 8.0;  meanStrikeInterval = 14; durationTicks = 8 * 20;  damage = 8.0F; }
            default -> { speed = 0.85; strikeRadius = 10.0; meanStrikeInterval = 9; durationTicks = 10 * 20; damage = 10.0F; }
        }

        // Re-lanzar refresca la duración (no se apila), igual que el campo
        activeRides.put(player.getUUID(),
                new RideState(speed, strikeRadius, meanStrikeInterval, durationTicks, damage));

        // Empujón inicial hacia arriba para despegar del piso
        player.setDeltaMovement(player.getDeltaMovement().x, 0.6, player.getDeltaMovement().z);
        player.hurtMarked = true;

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.2F);
    }

    /** Movimiento + rayos de las que están cabalgando la tormenta. */
    private static void tickRides(ServerLevel serverLevel) {
        if (activeRides.isEmpty()) return;

        activeRides.entrySet().removeIf(entry -> {
            Player player = serverLevel.getPlayerByUUID(entry.getKey());
            RideState state = entry.getValue();

            if (player == null || !player.isAlive() || state.ticksLeft-- <= 0) {
                if (player != null && player.isAlive()) {
                    // Aterrizaje: se cae, pero sin daño (fallDistance va en 0)
                    player.resetFallDistance();
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.6, 0.3, 0.6, 0.02);
                    serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
                }
                return true;
            }

            // --- Movimiento: la nube la lleva hacia donde mira ---
            Vec3 dir = player.getViewVector(1.0F);
            // Y clampeada: puede subir y bajar mirando, pero no dispararse al
            // cielo ni clavarse en el piso
            double vy = Math.max(-0.15, Math.min(0.35, dir.y * state.speed));

            // Techo: a más de 40 bloques sobre el terreno, obligarla a bajar
            BlockPos ground = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    player.blockPosition());
            if (player.getY() - ground.getY() > 40.0) {
                vy = Math.min(vy, -0.2);
            }

            player.setDeltaMovement(dir.x * state.speed, vy, dir.z * state.speed);
            player.hurtMarked = true;   // sin esto el cliente no recibe la velocidad
            player.hasImpulse = true;
            player.resetFallDistance();

            // Nube bajo los pies
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    player.getX(), player.getY() - 0.2, player.getZ(), 3, 0.35, 0.1, 0.35, 0.01);

            // --- Rayos sobre los enemigos sobrevolados ---
            if (random.nextInt(state.meanStrikeInterval) == 0) {
                java.util.List<net.minecraft.world.entity.LivingEntity> targets =
                        serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                                player.getBoundingBox().inflate(state.strikeRadius, 32.0, state.strikeRadius),
                                e -> net.juli2kapo.minewinx.util.Targeting.isValidTarget(e, player.getUUID()));
                if (!targets.isEmpty()) {
                    // Uno al azar: que se sienta tormenta, no un botón de borrar pantalla
                    net.minecraft.world.entity.LivingEntity victim =
                            targets.get(random.nextInt(targets.size()));
                    strikeAt(serverLevel, victim.getX(), victim.getY(), victim.getZ(),
                            player, state.damage);
                }
            }
            return false;
        });
    }

    /**
     * Llamar desde un handler de ServerTickEvent (igual que SunAndMoonPowers).
     */
    public static void onServerTick(ServerLevel serverLevel) {
        if (activeFields.isEmpty() && activeRides.isEmpty()) return;

        activeFields.entrySet().removeIf(entry -> {
            Player player = serverLevel.getPlayerByUUID(entry.getKey());
            FieldState state = entry.getValue();

            if (player == null || !player.isAlive() || state.ticksLeft-- <= 0) {
                return true; // se fue, murió o expiró
            }

            if (random.nextInt(state.meanStrikeInterval) == 0) {
                // Punto uniforme dentro del disco alrededor de la posición actual
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = state.radius * Math.sqrt(random.nextDouble());
                double x = player.getX() + Math.cos(angle) * dist;
                double z = player.getZ() + Math.sin(angle) * dist;
                BlockPos ground = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
                strikeAt(serverLevel, x, ground.getY(), z, player, 6.0F);
            }
            return false;
        });

        tickRides(serverLevel);
    }

    /**
     * Rayo en un punto: SOLO visual (el rayo vanilla prende fuego incluso a la
     * dueña inmune) y el daño se aplica a mano en un radio chico, respetando
     * las reglas de aliados de {@link net.juli2kapo.minewinx.util.Targeting}.
     */
    private static void strikeAt(ServerLevel serverLevel, double x, double y, double z,
                                 Player owner, float damage) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt == null) return;

        bolt.moveTo(x, y, z);
        bolt.setVisualOnly(true);
        serverLevel.addFreshEntity(bolt);

        java.util.List<net.minecraft.world.entity.LivingEntity> victims =
                serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        new net.minecraft.world.phys.AABB(x - 2.5, y - 1, z - 2.5,
                                x + 2.5, y + 4, z + 2.5),
                        e -> e.isAlive() && !net.juli2kapo.minewinx.util.Targeting.isAlly(e, owner.getUUID())
                                && !e.getUUID().equals(owner.getUUID()));
        for (net.minecraft.world.entity.LivingEntity victim : victims) {
            victim.hurt(serverLevel.damageSources().lightningBolt(), damage);
        }
    }

    private static class RideState {
        final double speed;
        final double strikeRadius;
        final int meanStrikeInterval;
        final float damage;
        int ticksLeft;

        RideState(double speed, double strikeRadius, int meanStrikeInterval, int durationTicks, float damage) {
            this.speed = speed;
            this.strikeRadius = strikeRadius;
            this.meanStrikeInterval = meanStrikeInterval;
            this.ticksLeft = durationTicks;
            this.damage = damage;
        }
    }

    private static class FieldState {
        final double radius;
        final int meanStrikeInterval;
        int ticksLeft;

        FieldState(double radius, int meanStrikeInterval, int durationTicks) {
            this.radius = radius;
            this.meanStrikeInterval = meanStrikeInterval;
            this.ticksLeft = durationTicks;
        }
    }
}

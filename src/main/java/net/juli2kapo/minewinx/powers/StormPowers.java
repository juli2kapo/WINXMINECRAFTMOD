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
     * Llamar desde un handler de ServerTickEvent (igual que SunAndMoonPowers).
     */
    public static void onServerTick(ServerLevel serverLevel) {
        if (activeFields.isEmpty()) return;

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

                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(x, ground.getY(), z);
                    // Solo visual: el rayo vanilla prende fuego (incluso a la
                    // dueña inmune); el daño lo aplicamos a mano, sin incendios
                    bolt.setVisualOnly(true);
                    serverLevel.addFreshEntity(bolt);

                    java.util.List<net.minecraft.world.entity.LivingEntity> victims =
                            serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                                    new net.minecraft.world.phys.AABB(x - 2.5, ground.getY() - 1, z - 2.5,
                                            x + 2.5, ground.getY() + 4, z + 2.5),
                                    e -> e.isAlive() && !net.juli2kapo.minewinx.util.Targeting.isAlly(e, player.getUUID())
                                            && !e.getUUID().equals(player.getUUID()));
                    for (net.minecraft.world.entity.LivingEntity victim : victims) {
                        victim.hurt(serverLevel.damageSources().lightningBolt(), 6.0F);
                    }
                }
            }
            return false;
        });
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

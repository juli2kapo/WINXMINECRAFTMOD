package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.TsunamiEntity;
import net.juli2kapo.minewinx.entity.WaterBlobProjectileEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class WaterPowers {

    private static final java.util.Map<java.util.UUID, GeyserFieldState> activeGeyserFields = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Random geyserRandom = new java.util.Random();
    // Morphix: la energía líquida rosa característica de Aisha
    private static final net.minecraft.core.particles.DustParticleOptions MORPHIX =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.45F, 0.75F), 1.3F);

    /**
     * Slot 3: Campo de Géiseres. Durante un rato, géiseres de morphix erupcionan
     * bajo los enemigos cercanos a Aisha, lanzándolos por el aire — el daño de
     * caída hace el resto. Radio, frecuencia, duración y potencia escalan con stage.
     */
    public static void summonGeyserField(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;
        Level level = player.level();
        if (level.isClientSide()) return;

        double radius;
        int meanInterval;
        int durationTicks;
        double launchPower;
        switch (stage) {
            case 1 -> { radius = 8.0;  meanInterval = 25; durationTicks = 12 * 20; launchPower = 1.1; }
            case 2 -> { radius = 12.0; meanInterval = 18; durationTicks = 16 * 20; launchPower = 1.4; }
            default -> { radius = 16.0; meanInterval = 12; durationTicks = 20 * 20; launchPower = 1.7; }
        }

        // Re-lanzar refresca la duración
        activeGeyserFields.put(player.getUUID(), new GeyserFieldState(radius, meanInterval, durationTicks, launchPower, stage));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 2.0F, 0.7F);
    }

    /**
     * Llamar desde un handler de ServerTickEvent (igual que Storm/SunAndMoon).
     */
    // -------------------------------------------------- repelente de agua

    private static final java.util.Map<net.minecraft.core.BlockPos, RepelledBlock> repelledWater =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int REFILL_DELAY = 80; // 4 s después del último toque

    private record RepelledBlock(net.minecraft.world.level.block.state.BlockState state, long restoreAt) {}

    /**
     * Aparta el agua alrededor y debajo de la entidad (columna 3x3 hasta el
     * fondo). Se llama cada tick desde WaterRepellentEffect: re-cava antes de
     * que el agua de los costados vuelva a fluir al hueco.
     */
    public static void repelWaterAround(ServerLevel level, LivingEntity entity) {
        long now = level.getGameTime();
        net.minecraft.core.BlockPos feet = entity.blockPosition();

        for (int dy = 1; dy >= -24; dy--) {
            boolean anyWaterAtLayer = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    net.minecraft.core.BlockPos pos = feet.offset(dx, dy, dz);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (state.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
                        // guardar el estado original solo la primera vez
                        RepelledBlock previous = repelledWater.get(pos);
                        repelledWater.put(pos.immutable(), new RepelledBlock(
                                previous != null ? previous.state() : state, now + REFILL_DELAY));
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        anyWaterAtLayer = true;
                    } else if (repelledWater.containsKey(pos)) {
                        // mantener el hueco abierto (refrescar el timer)
                        repelledWater.computeIfPresent(pos, (p, rb) -> new RepelledBlock(rb.state(), now + REFILL_DELAY));
                        anyWaterAtLayer = true;
                    }
                }
            }
            // por debajo de los pies, si tocamos una capa sólida sin agua, es el fondo
            if (dy < 0 && !anyWaterAtLayer && !level.getBlockState(feet.offset(0, dy, 0)).isAir()) {
                break;
            }
        }

        // Borde del hueco: salpicaduras para que se lea el "mar abierto"
        if (entity.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SPLASH,
                    entity.getX(), entity.getY() + 0.2, entity.getZ(), 6, 1.4, 0.3, 1.4, 0.02);
        }
    }

    private static void restoreRepelledWater(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        repelledWater.entrySet().removeIf(entry -> {
            if (entry.getValue().restoreAt() > now) return false;
            if (serverLevel.getBlockState(entry.getKey()).isAir()) {
                serverLevel.setBlock(entry.getKey(), entry.getValue().state(), 3);
            }
            return true;
        });
    }

    public static void onServerTick(ServerLevel serverLevel) {
        restoreRepelledWater(serverLevel);

        // 1) Telegrafiados pendientes: burbujeo bajo la víctima y luego erupción
        pendingEruptions.removeIf(pending -> {
            LivingEntity victim = pending.resolve(serverLevel);
            if (victim == null || !victim.isAlive()) return true;

            if (pending.ticksLeft-- > 0) {
                // Aviso: el agua "hierve" bajo la víctima
                serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                        victim.getX(), victim.getY() + 0.1, victim.getZ(), 6, 0.35, 0.05, 0.35, 0.02);
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                        victim.getX(), victim.getY() + 0.1, victim.getZ(), 4, 0.4, 0.05, 0.4, 0.0);
                if (pending.ticksLeft % 4 == 0) {
                    serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                            SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 1.2F, 1.3F);
                }
                return false;
            }
            erupt(serverLevel, victim, pending.launchPower, pending.damage);
            return true;
        });

        // 2) Campos activos: elegir nuevas víctimas
        if (activeGeyserFields.isEmpty()) return;
        activeGeyserFields.entrySet().removeIf(entry -> {
            Player caster = serverLevel.getPlayerByUUID(entry.getKey());
            GeyserFieldState state = entry.getValue();
            if (caster == null || !caster.isAlive() || state.ticksLeft-- <= 0) {
                return true;
            }

            if (geyserRandom.nextInt(state.meanInterval) == 0) {
                java.util.List<LivingEntity> targets =
                        serverLevel.getEntitiesOfClass(LivingEntity.class,
                                caster.getBoundingBox().inflate(state.radius),
                                e -> net.juli2kapo.minewinx.util.Targeting.isValidTarget(e, caster.getUUID()));
                if (!targets.isEmpty()) {
                    LivingEntity victim = targets.get(geyserRandom.nextInt(targets.size()));
                    // Daño directo fuerte: sobre el agua no hay daño de caída
                    // (tsunami + géiser), así que la erupción tiene que doler sola
                    float damage = 3.0F + 2.0F * state.stage; // 5 / 7 / 9
                    pendingEruptions.add(new PendingEruption(victim.getUUID(), 10, state.launchPower, damage));
                }
            }
            return false;
        });
    }

    private static void erupt(ServerLevel level, LivingEntity victim, double launchPower, float damage) {
        victim.setDeltaMovement(victim.getDeltaMovement().x * 0.3, launchPower, victim.getDeltaMovement().z * 0.3);
        victim.hasImpulse = true;
        victim.hurt(level.damageSources().drown(), damage);
        // El agua se aparta de la víctima: cae al fondo seco y come el daño de
        // caída aunque haya tsunami debajo (el hueco se rellena solo)
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.juli2kapo.minewinx.effect.ModEffects.WATER_REPELLENT.get(), 100, 0, false, false, true));

        // Pluma alta y densa (imitando la anatomía del géiser vanilla de 26.2,
        // que no existe en 1.20.1): chorro + vapor arriba + morphix rosa
        for (double y = 0; y <= 5.0; y += 0.4) {
            double spread = 0.15 + y * 0.06; // se abre al subir
            level.sendParticles(ParticleTypes.SPLASH,
                    victim.getX(), victim.getY() + y, victim.getZ(), 12, spread, 0.1, spread, 0.1);
            level.sendParticles(MORPHIX,
                    victim.getX(), victim.getY() + y, victim.getZ(), 2, spread, 0.1, spread, 0.01);
        }
        level.sendParticles(ParticleTypes.CLOUD,
                victim.getX(), victim.getY() + 5.0, victim.getZ(), 10, 0.5, 0.4, 0.5, 0.05);
        level.sendParticles(ParticleTypes.BUBBLE_POP,
                victim.getX(), victim.getY() + 0.3, victim.getZ(), 20, 0.5, 0.2, 0.5, 0.1);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.DOLPHIN_SPLASH, SoundSource.PLAYERS, 2.0F, 0.7F);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.5F, 1.2F);
    }

    private static final java.util.List<PendingEruption> pendingEruptions =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private static class PendingEruption {
        final java.util.UUID victimUUID;
        final double launchPower;
        final float damage;
        int ticksLeft;

        PendingEruption(java.util.UUID victimUUID, int ticksLeft, double launchPower, float damage) {
            this.victimUUID = victimUUID;
            this.ticksLeft = ticksLeft;
            this.launchPower = launchPower;
            this.damage = damage;
        }

        LivingEntity resolve(ServerLevel level) {
            return level.getEntity(victimUUID) instanceof LivingEntity living ? living : null;
        }
    }

    private static class GeyserFieldState {
        final double radius;
        final int meanInterval;
        final double launchPower;
        final int stage;
        int ticksLeft;

        GeyserFieldState(double radius, int meanInterval, int durationTicks, double launchPower, int stage) {
            this.radius = radius;
            this.meanInterval = meanInterval;
            this.ticksLeft = durationTicks;
            this.launchPower = launchPower;
            this.stage = stage;
        }
    }

    public static void startDrowningTarget(Player player) {
        Level world = player.level();
        if (!world.isClientSide) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5F, 1.0F);

            WaterBlobProjectileEntity projectile = new WaterBlobProjectileEntity(ModEntities.WATER_BLOB_PROJECTILE.get(), player, world);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.0F, 0.5F);
            world.addFreshEntity(projectile);
        }
    }

    public static void summonTsunami(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0 || player.level().isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) player.level();

        TsunamiEntity tsunami = new TsunamiEntity(ModEntities.TSUNAMI.get(), serverLevel, player, stage);
        serverLevel.addFreshEntity(tsunami);

        double width = 6.0 + (stage * 2.0);
        double length = 12.0;
        Vec3 playerPos = player.position();
        Vec3 lookVec = player.getLookAngle();
        Vec3 direction = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 waveOrigin = playerPos.subtract(direction.scale(8));

        for (double l = 0; l < length; l += 0.8) {
            double particleHeight = (2.0 + stage) * Mth.sin((float) (l / length * Math.PI));
            if (particleHeight <= 0) continue;
            Vec3 particlePos = waveOrigin.add(direction.scale(l)).add(0, particleHeight, 0);
            serverLevel.sendParticles(ParticleTypes.SPLASH, particlePos.x, particlePos.y, particlePos.z, 10, width / 2, 0.5, width / 2, 0.2);
        }

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, SoundSource.PLAYERS, 1.5F, 1.0F);
    }
}
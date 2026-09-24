package net.juli2kapo.minewinx.entity;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.particles.ModParticles;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public class SporeBombEntity extends ThrowableItemProjectile {

    private int stage = 1;

    public SporeBombEntity(EntityType<? extends SporeBombEntity> type, Level world) {
        super(type, world);
    }

    public SporeBombEntity(EntityType<? extends SporeBombEntity> type, double x, double y, double z, Level world) {
        super(type, x, y, z, world);
    }

    public SporeBombEntity(EntityType<? extends SporeBombEntity> type, LivingEntity owner, Level world) {
        super(type, owner, world);
        if (owner instanceof Player) {
            this.stage = PlayerDataProvider.getStage((Player) owner);
        }
    }

    @Override
    protected Item getDefaultItem() {
        // Objeto visual para el proyectil.
        return Items.SLIME_BALL;
    }

    private ParticleOptions getParticle() {
        return ModParticles.SPORE_PARTICLE.get();
    }

    @Override
    public void handleEntityEvent(byte pId) {
        // Genera partículas en el lado del cliente al impactar.
        if (pId == 3) {
            ParticleOptions particleOptions = this.getParticle();
            for (int i = 0; i < 32; ++i) {
                this.level().addParticle(particleOptions, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        } else {
            super.handleEntityEvent(pId);
        }
    }

    /**
     * Se ejecuta cuando el proyectil impacta.
     */
    @Override
    protected void onHit(HitResult pResult) {
        super.onHit(pResult);
        if (!this.level().isClientSide) {
            this.explode();
            this.level().broadcastEntityEvent(this, (byte) 3); // Notifica a los clientes para mostrar partículas.
            this.discard();
        }
    }

    /**
     * Lógica de la explosión de esporas.
     */
    private void explode() {
        if (this.level().isClientSide) {
            return;
        }

        // El radio y la duración del efecto escalan con el nivel.
        double radius = 4.0D + this.stage * 4; // Radio base de 5, aumenta en 1 por nivel.
        int sleepyDuration = 280 - 80 * this.stage; // Duración base de 280 ticks, disminuye en 80(4s) por nivel.

        // Define el área de efecto.
        AABB areaOfEffect = this.getBoundingBox().inflate(radius, 2.0D, radius);
        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, areaOfEffect);

        java.util.UUID ownerUUID = this.getOwner() == null ? null : this.getOwner().getUUID();
        for (LivingEntity entity : entities) {
            // No dormir a la lanzadora ni a sus aliados/summons
            if (entity.isAlive() && !net.juli2kapo.minewinx.util.Targeting.isAlly(entity, ownerUUID)) {
                entity.addEffect(new MobEffectInstance(ModEffects.SLEEPY.get(), sleepyDuration, 0));
            }
        }

        // Genera una explosión de partículas visible que se expande hacia afuera.
        if (this.level() instanceof ServerLevel serverLevel) {
            ParticleOptions particle = this.getParticle();
            int particleCount = 120 + 60 * this.stage; // Más partículas en niveles altos.
            // Nube de esporas: UN solo paquete (antes eran miles y trababan el juego).
            serverLevel.sendParticles(particle, this.getX(), this.getY(0.5), this.getZ(), particleCount, radius / 2.0, 1.0, radius / 2.0, 0.5);

            // Relleno verde del área, también en un solo paquete.
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(0.1), this.getZ(),
                    (int) (radius * 8), radius / 2.0, 0.1, radius / 2.0, 0.0);

            // Borde del radio: un único anillo. Con count = 0, los offsets de
            // ENTITY_EFFECT son el color (verde), no una dispersión.
            int ringPoints = (int) Math.max(24, radius * 6);
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2 * i / ringPoints;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT, x, this.getY(0.1), z, 0, 0.1, 0.8, 0.2, 1.0);
            }
        }
    }
}
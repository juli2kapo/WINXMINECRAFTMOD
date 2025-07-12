//package net.juli2kapo.minewinx.entity;
//
//import net.juli2kapo.minewinx.effect.ModEffects;
//import net.juli2kapo.minewinx.particles.ModParticles;
//import net.minecraft.core.particles.ParticleOptions;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.effect.MobEffectInstance;
//import net.minecraft.world.entity.EntityType;
//import net.minecraft.world.entity.LivingEntity;
//import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
//import net.minecraft.world.item.Item;
//import net.minecraft.world.item.Items;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.phys.AABB;
//import net.minecraft.world.phys.HitResult;
//
//import java.util.List;
//
//public class SporeBombEntity extends ThrowableItemProjectile {
//
//    public SporeBombEntity(EntityType<? extends ThrowableItemProjectile> type, Level world) {
//        super(type, world);
//    }
//
//    public SporeBombEntity(EntityType<? extends ThrowableItemProjectile> type, Level world, LivingEntity owner) {
//        super(type, owner, world);
//    }
//
//    @Override
//    protected Item getDefaultItem() {
//        // Objeto visual para el proyectil, como una bola de slime.
//        return Items.SLIME_BALL;
//    }
//
//    private ParticleOptions getParticle() {
//        return ModParticles.SPORE_PARTICLE.get();
//    }
//
//    @Override
//    public void handleEntityEvent(byte pId) {
//        if (pId == 3) {
//            ParticleOptions particleoptions = this.getParticle();
//            for(int i = 0; i < 8; ++i) {
//                this.level().addParticle(particleoptions, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
//            }
//        }
//    }
//
//    /**
//     * Se ejecuta cuando el proyectil impacta.
//     */
//    @Override
//    protected void onHit(HitResult pResult) {
//        super.onHit(pResult);
//        if (!this.level().isClientSide) {
//            this.level().broadcastEntityEvent(this, (byte)3); // Evento para partículas
//            explode();
//            this.discard();
//        }
//    }
//
//    /**
//     * Lógica de la explosión de esporas.
//     */
//    private void explode() {
//        if (this.level().isClientSide()) return;
//
//        ServerLevel world = (ServerLevel) this.level();
//        // Genera partículas de explosión en el servidor.
//        world.sendParticles(ModParticles.SPORE_PARTICLE.get(), this.getX(), this.getY(), this.getZ(), 100, 3.0, 3.0, 3.0, 0.1);
//
//        // Define el área de efecto.
//        AABB areaOfEffect = this.getBoundingBox().inflate(5.0D);
//        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, areaOfEffect);
//
//        for (LivingEntity entity : entities) {
//            // Aplica el efecto "Somnoliento" a las entidades en el radio.
//            // entity.addEffect(new MobEffectInstance(ModEffects.SLEEPY.get(), 200, 0)); // 10 segundos
//        }
//    }
//}
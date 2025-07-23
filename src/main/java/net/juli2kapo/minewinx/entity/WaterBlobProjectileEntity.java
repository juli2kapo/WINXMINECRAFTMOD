package net.juli2kapo.minewinx.entity;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class WaterBlobProjectileEntity extends ThrowableItemProjectile {

    private int stage = 1;

    public WaterBlobProjectileEntity(EntityType<? extends WaterBlobProjectileEntity> type, Level world) {
        super(type, world);
    }

    public WaterBlobProjectileEntity(EntityType<? extends WaterBlobProjectileEntity> type, LivingEntity owner, Level world) {
        super(type, owner, world);
        if (owner instanceof Player) {
            this.stage = PlayerDataProvider.getStage((Player) owner);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AIR; // No queremos que renderice un item por defecto
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHitResult = (EntityHitResult) result;
                if (entityHitResult.getEntity() instanceof LivingEntity target) {
                    // El owner no puede ser afectado por su propio proyectil
                    if (target != this.getOwner()) {
                        int duration = 200 + (this.stage * 40); // 10 segundos + 2s por nivel
                        target.addEffect(new MobEffectInstance(ModEffects.DROWNING_TARGET.get(), duration, 0));
                        this.discard(); // Desaparece al impactar
                    }
                }
            } else if (result.getType() == HitResult.Type.BLOCK) {
                // Simplemente desaparece al chocar con un bloque sin hacer nada más
                this.discard();
            }
        }
    }

    @Override
    protected float getGravity() {
        return 0.02f; // Reducir la gravedad para que vuele más recto
    }
}
package net.juli2kapo.minewinx.effect;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class DrowningTargetEffect extends MobEffect {
    public DrowningTargetEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        // Burbujas visibles alrededor de la cabeza (server-side, independiente
        // de la capa de render — así el efecto SIEMPRE se ve sobre el mob)
        if (pLivingEntity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && pLivingEntity.tickCount % 3 == 0) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    pLivingEntity.getX(), pLivingEntity.getEyeY() + 0.15, pLivingEntity.getZ(),
                    3, 0.25, 0.2, 0.25, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                    pLivingEntity.getX(), pLivingEntity.getEyeY() + 0.2, pLivingEntity.getZ(),
                    2, 0.2, 0.15, 0.2, 0.02);
        }

        if (pLivingEntity instanceof Player targetPlayer) {
            int airReduction = 5 * (pAmplifier + 1);
            targetPlayer.setAirSupply(Math.max(-20, targetPlayer.getAirSupply() - airReduction));

            // Aplicar daño cada segundo si el aire se ha agotado.
            if (targetPlayer.getAirSupply() <= 0 && targetPlayer.tickCount % 20 == 0) {
                targetPlayer.hurt(pLivingEntity.damageSources().drown(), 2.0F + pAmplifier);
                targetPlayer.level().playSound(null, targetPlayer.blockPosition(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        } else {
            // Para entidades que no son jugadores, aplicar daño cada segundo.
            if (pLivingEntity.tickCount % 20 == 0) {
                pLivingEntity.hurt(pLivingEntity.damageSources().drown(), 2.0F + pAmplifier);
            }
        }
        super.applyEffectTick(pLivingEntity, pAmplifier);
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }
}
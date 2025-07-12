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
        if (pLivingEntity instanceof Player targetPlayer) {

            targetPlayer.setAirSupply(Math.max(-20, targetPlayer.getAirSupply() - 5));

            // Aplicar daño cada segundo si el aire se ha agotado.
            if (targetPlayer.getAirSupply() <= 0 && targetPlayer.tickCount % 20 == 0) {
                targetPlayer.hurt(pLivingEntity.damageSources().drown(), 2.0F);
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
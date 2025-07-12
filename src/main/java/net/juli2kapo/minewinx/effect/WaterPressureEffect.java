package net.juli2kapo.minewinx.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class WaterPressureEffect extends MobEffect {
    public WaterPressureEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (pLivingEntity.tickCount % 20 == 0) {
            float damage = 1.0F + pAmplifier;
            pLivingEntity.hurt(pLivingEntity.damageSources().drown(), damage);
        }
        super.applyEffectTick(pLivingEntity, pAmplifier);
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }
}
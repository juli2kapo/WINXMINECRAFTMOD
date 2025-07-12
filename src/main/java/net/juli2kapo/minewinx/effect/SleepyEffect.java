package net.juli2kapo.minewinx.effect;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public class SleepyEffect extends MobEffect {
    public SleepyEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (pLivingEntity.getEffect(this) != null && pLivingEntity.getEffect(this).getDuration() == 1) {
            Optional<MobEffect> sleepEffect = BuiltInRegistries.MOB_EFFECT.getOptional(new ResourceLocation(MineWinx.MOD_ID, "sleep"));
            sleepEffect.ifPresent(effect -> pLivingEntity.addEffect(new MobEffectInstance(effect, 200, 0)));
        }
        super.applyEffectTick(pLivingEntity, pAmplifier);
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        // Hace que applyEffectTick() se llame cada tick.
        return true;
    }
}
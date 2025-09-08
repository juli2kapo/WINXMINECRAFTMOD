package net.juli2kapo.minewinx.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class XRayVisionEffect extends MobEffect {
    public XRayVisionEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00FFFF); // Cyan color
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Effect logic handled client-side through rendering
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
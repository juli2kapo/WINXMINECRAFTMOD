package net.juli2kapo.minewinx.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class SleepEffect extends MobEffect {
    public SleepEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        // Aplica efectos para inmovilizar a la entidad.
        pLivingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false, false));
        pLivingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 2, 255, false, false, false));
        pLivingEntity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 2, 255, false, false, false));

        // Forzamos la detención del movimiento y prevenimos el salto.
        Vec3 delta = pLivingEntity.getDeltaMovement();
        pLivingEntity.setDeltaMovement(0, delta.y < 0 ? delta.y : 0, 0); // Permite caer pero no saltar.

        if (pLivingEntity instanceof Player player) {
            if (!player.isCreative() && !player.isSpectator()) {
                if (player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
        }

        super.applyEffectTick(pLivingEntity, pAmplifier);
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }
}
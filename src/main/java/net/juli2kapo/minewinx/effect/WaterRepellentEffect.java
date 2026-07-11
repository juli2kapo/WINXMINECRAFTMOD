package net.juli2kapo.minewinx.effect;

import net.juli2kapo.minewinx.powers.WaterPowers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Repelente de agua: el agua se aparta de quien lo sufre — se abre un hueco
 * bajo la víctima (que se rellena solo después), así el agua no amortigua la
 * caída. Lo aplica el géiser al lanzar: tsunami + géiser = abrir el mar y
 * estrellarlos contra el fondo.
 */
public class WaterRepellentEffect extends MobEffect {

    public WaterRepellentEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            WaterPowers.repelWaterAround(serverLevel, entity);
        }
        super.applyEffectTick(entity, amplifier);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // cada tick: hay que re-cavar antes de que el agua fluya de vuelta
    }
}

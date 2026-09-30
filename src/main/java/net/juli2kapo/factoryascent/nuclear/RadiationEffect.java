package net.juli2kapo.factoryascent.nuclear;

import net.juli2kapo.factoryascent.power.PowerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Radiation sickness. It comes back every second while the absorbed dose is high (milk only
 * hides it for a moment) and hurts more often the worse it gets: every 4 s at level I down to
 * every half second at IV, ignoring armour. From level II on it also makes you hungry.
 */
public class RadiationEffect extends MobEffect {
    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, 0x7FD13B);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        float damage = (float) PowerConfig.get(PowerConfig.RADIATION_DAMAGE);
        if (damage > 0) mob.hurtServer(level, Radiation.damageSource(level), damage);
        if (amplification >= 1 && mob instanceof Player player) player.causeFoodExhaustion(0.4f * amplification);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        int interval = Math.max(10, 80 >> Math.min(3, amplification));
        return tickCount % interval == 0;
    }
}

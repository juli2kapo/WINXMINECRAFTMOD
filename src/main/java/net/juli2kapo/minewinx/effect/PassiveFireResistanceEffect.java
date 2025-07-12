package net.juli2kapo.minewinx.effect;

import net.juli2kapo.minewinx.powers.FirePowers;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class PassiveFireResistanceEffect extends MobEffect {
    public PassiveFireResistanceEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (pLivingEntity instanceof Player player) {
            String element = PlayerDataProvider.getElement(player);
            if (PlayerDataProvider.isTransformed(player) && "fire".equalsIgnoreCase(element)) {
                FirePowers.applyPassiveFireResistance(player);
            } else {
                // Si el jugador ya no cumple las condiciones, se elimina el efecto.
                player.removeEffect(this);
            }
        }
        super.applyEffectTick(pLivingEntity, pAmplifier);
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true; // Se ejecuta cada tick
    }
}
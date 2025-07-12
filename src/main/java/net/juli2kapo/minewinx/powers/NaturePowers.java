package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class NaturePowers {

    /**
     * Lanza un proyectil de bomba de esporas.
     * @param player El jugador que lanza la bomba.
     */
    public static void sporeBomb(Player player) {
        Level world = player.level();
        if (!world.isClientSide()) {
//            SporeBombEntity sporeBomb = new SporeBombEntity(ModEntities.SPOREBOMB.get(), world, player);
//            // Dispara el proyectil desde la posición y dirección del jugador.
//            sporeBomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
//            world.addFreshEntity(sporeBomb);
        }
    }
}
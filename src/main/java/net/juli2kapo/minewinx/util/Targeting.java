package net.juli2kapo.minewinx.util;

import net.juli2kapo.minewinx.entity.plants.PlantEntity;
import net.juli2kapo.minewinx.powers.DarkPowers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Regla única de auto-targeting para los poderes.
 *
 * - Monstruos hostiles: siempre objetivo.
 * - Jugadores: aliados POR DEFECTO (cooperativo sin configurar nada). Solo se
 *   auto-atacan si AMBOS están en equipos de scoreboard DISTINTOS — para un
 *   duelo: /team add rojo, /team add azul y cada uno se une a uno.
 * - Ilusiones: SÍ son objetivo (son señuelos: que se coman los disparos)...
 *   salvo las propias.
 * - Plantas: las propias nunca; las de una dueña enemiga sí (guerra de plantas).
 */
public final class Targeting {

    private Targeting() {}

    public static boolean isValidTarget(LivingEntity candidate, @Nullable UUID ownerUUID) {
        if (!candidate.isAlive() || candidate.isSpectator()) return false;
        if (isAlly(candidate, ownerUUID)) return false;

        if (candidate instanceof Player player) {
            return !player.isCreative();
        }
        if (candidate instanceof PlantEntity) return true;          // planta enemiga
        if (candidate.getTags().contains("Illusion")) return true;  // señuelo enemigo
        return candidate instanceof Monster;
    }

    /** Aliados: la dueña, su equipo, sus plantas y sus propias ilusiones. */
    public static boolean isAlly(LivingEntity candidate, @Nullable UUID ownerUUID) {
        if (ownerUUID == null) return false;
        if (ownerUUID.equals(candidate.getUUID())) return true;

        if (candidate instanceof PlantEntity plant) {
            return ownerUUID.equals(plant.getOwnerUUID());
        }
        if (candidate.getTags().contains("Illusion")) {
            CompoundTag data = candidate.getPersistentData();
            return data.hasUUID(DarkPowers.CREATOR_UUID_TAG)
                    && data.getUUID(DarkPowers.CREATOR_UUID_TAG).equals(ownerUUID);
        }
        if (candidate instanceof Player player) {
            Player owner = candidate.level().getPlayerByUUID(ownerUUID);
            if (owner == null) return true; // dueña ausente: no auto-PvP
            // Sin equipo (cualquiera de los dos) = aliados por defecto.
            // Solo son enemigos si AMBOS tienen equipo y son equipos distintos.
            if (owner.getTeam() == null || player.getTeam() == null) return true;
            return owner.isAlliedTo(player);
        }
        return false;
    }
}

package net.juli2kapo.minewinx.item.custom;

import net.juli2kapo.minewinx.powers.DarkPowers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Crea una ilusión de un mob real: se instancia la clase verdadera del mob
 * (con toda su IA vanilla) y se marca como ilusión. El comportamiento especial
 * (romperse al ser golpeada por un jugador, inmunidad al resto del daño y no
 * atacar a su creadora) vive en ServerEvents, no acá.
 */
public class IllusionCreator extends Item {
    private final EntityType<? extends Mob> mobType;

    public IllusionCreator(Properties properties, EntityType<? extends Mob> mobType) {
        super(properties);
        this.mobType = mobType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ItemStack stack = player.getItemInHand(hand);

            // Mob REAL (Skeleton, Zombie, ...), no un Mob base anónimo: así tiene
            // sus goals de ataque/movimiento y responde a setTarget.
            Mob illusion = mobType.create(serverLevel);
            if (illusion == null) {
                return InteractionResultHolder.fail(stack);
            }

            illusion.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            // finalizeSpawn equipa lo que corresponda (arco del esqueleto incluido)
            illusion.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(illusion.blockPosition()), MobSpawnType.EVENT, null, null);

            illusion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0D);
            illusion.setHealth(1.0F);
            illusion.setPersistenceRequired();
            illusion.setSilent(true);

            serverLevel.addFreshEntity(illusion);
            DarkPowers.markIllusionCreator(illusion, player);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}

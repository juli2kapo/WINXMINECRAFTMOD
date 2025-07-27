package net.juli2kapo.minewinx.powers;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.BiConsumer;

public class ItemsCannonTable {

    private static final Map<Item, BiConsumer<Level, Vec3>> ITEM_EFFECTS = new HashMap<>();

    static {
        ITEM_EFFECTS.put(Items.BONE, (level, pos) -> {
            Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
            skeleton.setPos(pos.x, pos.y, pos.z);
            level.addFreshEntity(skeleton);
        });

        ITEM_EFFECTS.put(Items.TNT, (level, pos) -> {
            PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
            tnt.setPos(pos.x, pos.y + 1, pos.z);
            level.addFreshEntity(tnt);
        });

        ITEM_EFFECTS.put(Items.LAPIS_LAZULI, (level, pos) -> {
            level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos.x - 3, pos.y - 3, pos.z - 3, pos.x + 3, pos.y + 3, pos.z + 3), (entity) -> true)
                    .forEach(entity -> {
                        if (entity != null) {
                            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0)); // 5 seconds, Weakness I
                            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0)); // 5 seconds, Slowness I
                        }
                    });
        });

        ITEM_EFFECTS.put(Items.FEATHER, (level, pos) -> {
            level.getEntitiesOfClass(Player.class, new AABB(pos.x - 2, pos.y - 2, pos.z - 2, pos.x + 2, pos.y + 2, pos.z + 2), (playerEntity) -> true)
                    .forEach(playerEntity -> {
                        if (playerEntity != null) {
                            playerEntity.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 2)); // 10 seconds, Jump Boost III
                        }
                    });
        });


        ITEM_EFFECTS.put(Items.PUFFERFISH, (level, pos) -> {
            level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos.x - 2, pos.y - 2, pos.z - 2, pos.x + 2, pos.y + 2, pos.z + 2), (entity) -> true)
                    .forEach(entity -> {
                        if (entity != null) {
                            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1)); // 6 seconds, Poison II
                            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0)); // 4 seconds, Nausea
                        }
                    });
        });

        ITEM_EFFECTS.put(Items.SLIME_BALL, (level, pos) -> {
            // Spawns a small slime to distract enemies
            Slime slime = new Slime(EntityType.SLIME, level);
            slime.setPos(pos.x, pos.y, pos.z);
            slime.setSize(3, true); // Sets it to the smallest size, true for persistent
            level.addFreshEntity(slime);
        });

    }


    public static void shootCannon(Item item, Player player) {
        Level level = player.level();
        if (level.isClientSide) return;

        Snowball projectile = new Snowball(level, player);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);

        // Guardar el item en la entidad para saber qué efecto aplicar al impactar
        projectile.getPersistentData().putString("CannonItem", item.getDescriptionId());

        level.addFreshEntity(projectile);
    }

    public static void onProjectileImpact(Snowball projectile, Vec3 impactPos) {
        Level level = (Level) projectile.level();
        String itemId = projectile.getPersistentData().getString("CannonItem");
        ITEM_EFFECTS.entrySet().stream()
                .filter(e -> e.getKey().getDescriptionId().equals(itemId))
                .findFirst()
                .ifPresent(e -> e.getValue().accept(level, impactPos));
    }
}
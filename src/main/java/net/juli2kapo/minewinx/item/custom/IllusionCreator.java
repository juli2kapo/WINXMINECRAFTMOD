package net.juli2kapo.minewinx.item.custom;

import net.juli2kapo.minewinx.powers.DarkPowers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

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

            Mob illusion = new Mob(mobType, serverLevel) {
                @Override
                public boolean hurt(DamageSource source, float amount) {
                    if (this.isInvulnerableTo(source)) {
                        return false;
                    }
                    // Solo se destruye si el daño proviene de un jugador
                    if (source.getEntity() instanceof Player) {
                        if (!this.level().isClientSide) {
                            Vec3 pos = this.position();
                            this.level().playSound(null, pos.x, pos.y, pos.z,
                                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);

                            ((ServerLevel) this.level()).sendParticles(ParticleTypes.SMOKE,
                                    pos.x, pos.y + this.getBbHeight() / 2.0, pos.z,
                                    15, 0.3, 0.3, 0.3, 0.1);

                            this.discard();
                        }
                        return true;
                    }
                    return false;
                }
            };

            illusion.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());

            illusion.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(illusion.blockPosition()), MobSpawnType.EVENT, null, null);

            if (mobType == EntityType.SKELETON) {
                illusion.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            }

            illusion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0D);
            illusion.setHealth(1.0F);

            CompoundTag nbt = new CompoundTag();
            illusion.addAdditionalSaveData(nbt);
            nbt.putString("DeathLootTable", "minecraft:empty");
            nbt.putBoolean("Invulnerable", true); // Protege del daño ambiental
            illusion.readAdditionalSaveData(nbt);
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

    static class DisableAiGoal extends Goal {
        private final Mob mob;
        private int ticksToWait;

        public DisableAiGoal(Mob mob, int ticksToWait) {
            this.mob = mob;
            this.ticksToWait = ticksToWait;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isNoAi();
        }

        @Override
        public void tick() {
            if (ticksToWait > 0) {
                --ticksToWait;
            } else {
                mob.setNoAi(true);
            }
        }
    }
}
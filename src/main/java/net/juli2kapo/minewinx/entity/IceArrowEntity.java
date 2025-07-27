// src/main/java/net/juli2kapo/minewinx/entity/IceArrowEntity.java
package net.juli2kapo.minewinx.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class IceArrowEntity extends AbstractArrow {

    public IceArrowEntity(EntityType<? extends IceArrowEntity> type, Level level) {
        super(type, level);
    }

    public IceArrowEntity(EntityType<? extends IceArrowEntity> type, Level level, LivingEntity shooter) {
        super(type, shooter, level);
    }

    public IceArrowEntity(Level pLevel, double pX, double pY, double pZ) {
        super(ModEntities.ICE_ARROW.get(), pX, pY, pZ, pLevel);
    }

    @Override
    protected ItemStack getPickupItem() {
        // Devuelve el ítem que se recoge al recoger la flecha (puedes crear un ítem personalizado)
        return ItemStack.EMPTY;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity entity) {
        super.doPostHurtEffects(entity);
        // Ejemplo: aplicar lentitud al golpear
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        BlockState state = level().getBlockState(hitResult.getBlockPos());
        if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE) || state.is(Blocks.BLUE_ICE)) {
            this.inGround = false; // Ensure it's not marked as inGround
            return;
        }
        super.onHitBlock(hitResult);
    }

    @Override
    public void tick() {
        BlockPos currentBlockPos = this.blockPosition();
        BlockState blockstate = this.level().getBlockState(currentBlockPos);

        // Check if the current block is an ice block.
        // This check needs to happen *before* super.tick() if you want to prevent
        // AbstractArrow's initial inGround check from firing for ice.
        if (blockstate.is(Blocks.PACKED_ICE) || blockstate.is(Blocks.ICE) || blockstate.is(Blocks.BLUE_ICE)) {
            // If the arrow is currently inside an ice block:
            // 1. Ensure it's not marked as inGround.
            this.inGround = false;
            // 2. Prevent the parent's initial block collision setting `inGround = true`.
            //    A simple way is to temporarily set `noPhysics` which bypasses the check,
            //    then restore it after `super.tick()`. This is tricky, so a more direct approach is below.

            // Instead of modifying parent's behavior, let's just ensure our arrow keeps moving
            // when it's in ice. We can manipulate its velocity and state.
            // If its velocity is zeroed, give it back its previous velocity, or a slight push.
            if (this.getDeltaMovement().lengthSqr() < 0.001D) { // If it's stopped or almost stopped
                // This is a rough way to give it some momentum to move through.
                // You might want to store the previous tick's velocity or calculate a direction.
                // For simplicity, let's just push it forward slightly if it's stuck.
                Vec3 forward = this.getLookAngle().scale(0.1); // A small push in its current direction
                this.setDeltaMovement(forward);
                this.inGroundTime = 0; // Reset inGroundTime so it doesn't despawn prematurely
            }
            // After this, allow super.tick() to handle movement and other checks normally.
            // If it hits a *non-ice* block, super.tick() will handle it.
            // If it hits another ice block, this custom logic will reactivate.
        }

        // Call the parent's tick method AFTER our custom ice handling.
        // This ensures that if the arrow *is* in an ice block, we've already handled it.
        // If it's not in an ice block, super.tick() proceeds as normal.
        super.tick();

        // Optional: Debugging logs to confirm behavior
        // System.out.println("Arrow Tick: Pos=" + this.position() + ", Delta=" + this.getDeltaMovement() + ", InGround=" + this.inGround + ", CurrentBlock=" + blockstate.getBlock().getName().getString());
    }

}
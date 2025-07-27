package net.juli2kapo.minewinx.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class IceCrystalEntity extends Entity {

    public IceCrystalEntity(EntityType<? extends IceCrystalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true; // So it doesn't fall
    }

    @Override
    protected void defineSynchedData() {}


    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        // Optional: remove after time
        if (this.tickCount > 100) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {

    }
}

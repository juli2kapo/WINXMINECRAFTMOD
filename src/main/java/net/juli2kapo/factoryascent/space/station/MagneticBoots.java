package net.juli2kapo.factoryascent.space.station;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Magnetic Boots: Astronaut Boots with electromagnets in the soles. In low gravity (orbit, the
 * planets) they pull their wearer onto the floor under them, so a builder walks the station deck
 * at normal gravity instead of floating off with every step and jump. Sneaking switches the magnets
 * off (to drift free, or climb onto a ledge); far from any floor they do nothing.
 */
public final class MagneticBoots {
    /** How far below the feet a floor still holds the boots. */
    public static final double REACH = 1.25;

    private MagneticBoots() {}

    /**
     * The rule: with the boots on, a floor within reach and the magnets on (not sneaking), low
     * gravity is raised to normal; otherwise the base gravity stays.
     */
    public static double gravity(double base, boolean wearing, boolean floorNear, boolean sneaking) {
        if (!wearing || sneaking || !floorNear || base >= 1.0) return base;
        return 1.0;
    }

    public static boolean wearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).is(StationContent.MAGNETIC_BOOTS.get());
    }

    /** A block with a collision shape within {@link #REACH} below the feet. */
    public static boolean floorNear(Level level, LivingEntity entity) {
        if (entity.onGround()) return true;
        double feet = entity.getY();
        for (double dy = 0.05; dy <= REACH; dy += 0.4) {
            BlockPos p = BlockPos.containing(entity.getX(), feet - dy, entity.getZ());
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }

    /** Gravity for this entity right now (only players and armour stands wear boots, but any living entity may). */
    public static double gravity(LivingEntity entity, double base) {
        if (base >= 1.0 || !wearing(entity)) return base;
        boolean sneaking = entity instanceof Player p ? p.isShiftKeyDown() : entity.isShiftKeyDown();
        return gravity(base, true, floorNear(entity.level(), entity), sneaking);
    }
}

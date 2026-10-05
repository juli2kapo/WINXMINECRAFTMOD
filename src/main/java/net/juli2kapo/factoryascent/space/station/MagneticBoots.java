package net.juli2kapo.factoryascent.space.station;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Magnetic Boots: Astronaut Boots with electromagnets in the soles. In low gravity (orbit, the
 * planets) they pull their wearer onto the floor under them, so a builder walks the station deck
 * at normal gravity instead of floating off with every step and jump. Sneaking switches the magnets
 * off (to drift free, or climb onto a ledge); far from any floor they do nothing.
 *
 * <p>In low gravity they also take you onto walls and ceilings. By default (config
 * {@code magneticBootsRotateGravity}) they really turn your gravity: walk into a wall and it
 * becomes your floor, view and all ({@link net.juli2kapo.factoryascent.space.gravity.Gravity}).
 * With that off they fall back to the older, upright version ({@link Hold}): walk into a wall and
 * you climb it like a spider (forward or jump climbs, back climbs down, no keys: you stay put);
 * jump into a ceiling and you hang under it, walking with the normal keys. Sneak to let go.
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

    /** A block with a collision shape within {@link #REACH} below the feet (along a turned gravity, if turned). */
    public static boolean floorNear(Level level, LivingEntity entity) {
        if (entity.onGround()) return true;
        if (net.juli2kapo.factoryascent.space.gravity.Gravity.isTurned(entity)) {
            return net.juli2kapo.factoryascent.space.gravity.Gravity.surfaceNear(level, entity, REACH);
        }
        double feet = entity.getY();
        for (double dy = 0.05; dy <= REACH; dy += 0.4) {
            BlockPos p = BlockPos.containing(entity.getX(), feet - dy, entity.getZ());
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- walls and ceilings

    /** What the boots are holding on to. */
    public enum Hold { NONE, FLOOR, WALL, CEILING }

    /** Vertical speed while climbing a wall (forward / jump), and down it (back). */
    public static final double CLIMB_UP = 0.2, CLIMB_DOWN = -0.15;
    /** Speed pressing the soles up into a ceiling (the collision stops it, so you hang there). */
    public static final double CEILING_PULL = 0.08;

    /**
     * The rule: magnets work only when worn, not sneaking and in low gravity. A ceiling right above
     * the head holds you once you jump into it (and keeps holding while it stays there); a wall
     * beside you holds you when you are off the ground or walk into it; otherwise it is the floor.
     */
    public static Hold hold(boolean wearing, double baseGravity, boolean sneaking, boolean onGround, boolean wall,
                            boolean ceiling, boolean wasOnCeiling, boolean jumping, float forward) {
        if (!wearing || sneaking || baseGravity >= 1.0) return Hold.NONE;
        if (ceiling && !onGround && (wasOnCeiling || jumping)) return Hold.CEILING;
        if (wall && (!onGround || forward > 0)) return Hold.WALL;
        return Hold.FLOOR;
    }

    /** Vertical speed for this tick under a hold (unchanged for the floor and nothing). */
    public static double verticalSpeed(Hold hold, double vy, float forward, boolean jumping) {
        return switch (hold) {
            case WALL -> forward > 0 || jumping ? CLIMB_UP : forward < 0 ? CLIMB_DOWN : 0.0;
            case CEILING -> CEILING_PULL;
            default -> vy;
        };
    }

    /** A solid block touching the entity's sides (not counting the floor under its feet). */
    public static boolean wallNear(Level level, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        AABB sides = new AABB(box.minX - 0.12, box.minY + 0.3, box.minZ - 0.12, box.maxX + 0.12, box.maxY - 0.1, box.maxZ + 0.12);
        return !level.noCollision(entity, sides);
    }

    /** A solid block within half a block above the head. */
    public static boolean ceilingNear(Level level, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        AABB above = new AABB(box.minX + 0.05, box.maxY, box.minZ + 0.05, box.maxX - 0.05, box.maxY + 0.5, box.maxZ - 0.05);
        return !level.noCollision(entity, above);
    }

    /** Players hanging from a ceiling, per side (client and server share a JVM in single player). */
    private static final Map<UUID, Boolean> ON_CEILING_CLIENT = new ConcurrentHashMap<>(), ON_CEILING_SERVER = new ConcurrentHashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(MagneticBoots::onPlayerTick);
    }

    /**
     * Before the player moves: the client (which moves its own player) pins them to the wall or
     * ceiling; the server only forgives the fall distance while they hang on.
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        Map<UUID, Boolean> onCeiling = player.level().isClientSide() ? ON_CEILING_CLIENT : ON_CEILING_SERVER;
        // the real thing (turned gravity) replaces the upright climbing unless configured off
        if (net.juli2kapo.factoryascent.space.gravity.Gravity.enabled() || player.isSpectator() || player.getAbilities().flying || player.isPassenger() || !wearing(player)) {
            onCeiling.remove(player.getUUID());
            return;
        }
        double base = net.juli2kapo.factoryascent.space.Orbit.gravityFor(player.level().dimension());
        if (base >= 1.0) return;
        Level level = player.level();
        boolean was = onCeiling.getOrDefault(player.getUUID(), false);
        Hold hold = hold(true, base, player.isShiftKeyDown(), player.onGround(), wallNear(level, player), ceilingNear(level, player),
                was, player.isJumping(), player.zza);
        if (hold == Hold.CEILING) onCeiling.put(player.getUUID(), true);
        else onCeiling.remove(player.getUUID());
        if (hold != Hold.WALL && hold != Hold.CEILING) return;
        player.resetFallDistance();
        if (level.isClientSide()) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, verticalSpeed(hold, v.y, player.zza, player.isJumping()), v.z);
        }
    }

    /** Gravity for this entity right now (only players and armour stands wear boots, but any living entity may). */
    public static double gravity(LivingEntity entity, double base) {
        if (base >= 1.0 || !wearing(entity)) return base;
        boolean sneaking = entity instanceof Player p ? p.isShiftKeyDown() : entity.isShiftKeyDown();
        return gravity(base, true, floorNear(entity.level(), entity), sneaking);
    }
}

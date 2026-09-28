package net.juli2kapo.factoryascent.space;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The public rules of space, for this package and anything else that goes up there (ships,
 * capsules): which dimensions have no air, and whether an entity can breathe right now.
 *
 * <p>An entity breathes in an airless dimension when it rides a {@link SealedCabin} (or an entity
 * type in the {@link #SEALED_VEHICLES} tag), stands in an Oxygen Sealer's bubble, or wears the whole
 * Astronaut Suit with air left in its chestplate. Everything else suffocates (see
 * {@link SpaceEvents}).
 */
public final class SpaceRules {
    /** The orbit dimension (data pack {@code factoryascent:orbit}). */
    public static final ResourceKey<Level> ORBIT =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbit"));
    /** Vehicles whose riders breathe without a suit, for entity classes that can't implement {@link SealedCabin}. */
    public static final TagKey<EntityType<?>> SEALED_VEHICLES =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "sealed_vehicles"));

    private static final Set<ResourceKey<Level>> AIRLESS = ConcurrentHashMap.newKeySet();
    /** Running Oxygen Sealers per dimension (server side, refreshed by the sealers every tick). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> SEALERS = new ConcurrentHashMap<>();

    static {
        AIRLESS.add(ORBIT);
    }

    private SpaceRules() {}

    /** How an entity is getting its air. */
    public enum Breath {
        /** The dimension has air. */
        AIR,
        /** Riding a sealed vehicle. */
        CABIN,
        /** Inside an Oxygen Sealer's bubble. */
        BUBBLE,
        /** On its suit's tank (drains). */
        SUIT,
        /** Suffocating. */
        NONE
    }

    /** Adds another airless dimension (other content can call this during setup). */
    public static void registerAirless(ResourceKey<Level> dimension) {
        AIRLESS.add(dimension);
    }

    public static boolean isAirless(ResourceKey<Level> dimension) {
        return AIRLESS.contains(dimension);
    }

    /** No breathable air in this level (orbit, and anything added with {@link #registerAirless}). */
    public static boolean isAirless(Level level) {
        return isAirless(level.dimension());
    }

    /** Whether the entity has air right now (always true where the dimension has air). */
    public static boolean canBreathe(LivingEntity entity) {
        return breathing(entity) != Breath.NONE;
    }

    public static Breath breathing(LivingEntity entity) {
        return breathing(entity, isAirless(entity.level()));
    }

    /** Where the entity's air comes from, given whether its surroundings are airless. */
    public static Breath breathing(LivingEntity entity, boolean airless) {
        if (!airless) return Breath.AIR;
        if (inSealedCabin(entity)) return Breath.CABIN;
        if (inOxygenBubble(entity.level(), entity.getEyePosition())) return Breath.BUBBLE;
        if (wearsFullSuit(entity) && SuitItems.oxygen(suitTank(entity)) > 0) return Breath.SUIT;
        return Breath.NONE;
    }

    /** Riding (directly or further up the vehicle chain) a vehicle that holds air. */
    public static boolean inSealedCabin(Entity entity) {
        for (Entity v = entity.getVehicle(); v != null; v = v.getVehicle()) {
            if (v instanceof SealedCabin cabin && cabin.isSealed()) return true;
            if (v.getType().builtInRegistryHolder().is(SEALED_VEHICLES)) return true;
        }
        return false;
    }

    /** All four Astronaut Suit pieces are worn (the chest may be a Jet Suit). */
    public static boolean wearsFullSuit(LivingEntity entity) {
        return SuitItems.isSuitPiece(entity.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD)
                && SuitItems.isSuitPiece(entity.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)
                && SuitItems.isSuitPiece(entity.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS)
                && SuitItems.isSuitPiece(entity.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET);
    }

    /** The worn chestplate if it holds air (a suit or Jet Suit), else empty. */
    public static ItemStack suitTank(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return SuitItems.holdsOxygen(chest) ? chest : ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- sealer bubbles

    /** Inside the bubble of a running Oxygen Sealer (server side). */
    public static boolean inOxygenBubble(Level level, Vec3 pos) {
        Set<BlockPos> sealers = SEALERS.get(level.dimension());
        if (sealers == null || sealers.isEmpty()) return false;
        double r = SpaceConfig.get(SpaceConfig.SEALER_RADIUS) + 0.5;
        for (BlockPos s : sealers) {
            if (pos.distanceToSqr(s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5) <= r * r) return true;
        }
        return false;
    }

    static void setSealer(Level level, BlockPos pos, boolean running) {
        if (level.isClientSide()) return;
        if (running) {
            SEALERS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        } else {
            Set<BlockPos> set = SEALERS.get(level.dimension());
            if (set != null) set.remove(pos);
        }
    }

    static void clearSealers() {
        SEALERS.clear();
    }
}

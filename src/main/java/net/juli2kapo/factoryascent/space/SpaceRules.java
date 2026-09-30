package net.juli2kapo.factoryascent.space;

import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.LongSet;
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
 * type in the {@link #SEALED_VEHICLES} tag), stands in a sealed room an Oxygen Sealer or Air Vent
 * keeps full of air ({@link net.juli2kapo.factoryascent.space.station.AirVolume}), or wears the
 * whole Astronaut Suit with air left in its chestplate. Everything else suffocates (see
 * {@link SpaceEvents}). The planets ({@link net.juli2kapo.factoryascent.space.planet.Planet}) are
 * airless too.
 */
public final class SpaceRules {
    /** The orbit dimension (data pack {@code factoryascent:orbit}). */
    public static final ResourceKey<Level> ORBIT =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbit"));
    /** Vehicles whose riders breathe without a suit, for entity classes that can't implement {@link SealedCabin}. */
    public static final TagKey<EntityType<?>> SEALED_VEHICLES =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "sealed_vehicles"));

    private static final Set<ResourceKey<Level>> AIRLESS = ConcurrentHashMap.newKeySet();
    /** Sealed rooms with air per dimension, by the sealer or vent filling them (server side). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, LongSet>> ZONES = new ConcurrentHashMap<>();

    static {
        AIRLESS.add(ORBIT);
        for (net.juli2kapo.factoryascent.space.planet.Planet p : net.juli2kapo.factoryascent.space.planet.Planet.values()) {
            if (p.airless()) AIRLESS.add(p.key);
        }
    }

    private SpaceRules() {}

    /** How an entity is getting its air. */
    public enum Breath {
        /** The dimension has air. */
        AIR,
        /** Riding a sealed vehicle. */
        CABIN,
        /** Inside a sealed room full of air. */
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

    // ---------------------------------------------------------------- sealed rooms

    /** Inside a sealed room kept full of air by a running Oxygen Sealer or Air Vent (server side). */
    public static boolean inOxygenBubble(Level level, Vec3 pos) {
        return airSourceAt(level, BlockPos.containing(pos)) != null;
    }

    /** The sealer or vent whose room holds this cell, if any. */
    public static @org.jspecify.annotations.Nullable BlockPos airSourceAt(Level level, BlockPos cell) {
        Map<BlockPos, LongSet> zones = ZONES.get(level.dimension());
        if (zones == null || zones.isEmpty()) return null;
        long key = cell.asLong();
        for (var e : zones.entrySet()) {
            if (e.getValue().contains(key)) return e.getKey();
        }
        return null;
    }

    /** Sets (or with null clears) the room a sealer keeps full of air. */
    public static void setAirZone(Level level, BlockPos source, @org.jspecify.annotations.Nullable LongSet cells) {
        if (level.isClientSide()) return;
        if (cells != null && !cells.isEmpty()) {
            ZONES.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(source.immutable(), cells);
        } else {
            Map<BlockPos, LongSet> zones = ZONES.get(level.dimension());
            if (zones != null) zones.remove(source);
        }
    }

    static void clearSealers() {
        ZONES.clear();
    }
}

package net.juli2kapo.factoryascent.space;

import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of space travel and personal gear, listed in {@code ModGameTests.TESTS}. The orbit
 * dimension isn't loaded by the GameTest server, so airless-ness is passed to the breathing tick
 * directly ({@link SpaceEvents#tickBreathing(net.minecraft.world.entity.LivingEntity, boolean)});
 * real trips to orbit are checked in the game.
 */
public final class SpaceGameTests {
    private SpaceGameTests() {}

    private static void wearSuit(net.minecraft.world.entity.LivingEntity e, int oxygen) {
        e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(SpaceContent.ASTRONAUT_HELMET.get()));
        ItemStack chest = new ItemStack(SpaceContent.ASTRONAUT_SUIT.get());
        SuitItems.setOxygen(chest, oxygen);
        e.setItemSlot(EquipmentSlot.CHEST, chest);
        e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SpaceContent.ASTRONAUT_LEGGINGS.get()));
        e.setItemSlot(EquipmentSlot.FEET, new ItemStack(SpaceContent.ASTRONAUT_BOOTS.get()));
    }

    /** Air only drains where there is none; no suit (or an empty one) in vacuum hurts. Orbit is airless, the overworld isn't. */
    public static void suitOxygenDrainsOnlyWhereAirless(GameTestHelper h) {
        h.assertTrue(SpaceRules.isAirless(SpaceRules.ORBIT), "orbit must be airless");
        h.assertTrue(!SpaceRules.isAirless(Level.OVERWORLD) && !SpaceRules.isAirless(h.getLevel()), "the overworld has air");
        Pig suited = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 2, 2));
        wearSuit(suited, 1000);
        suited.tickCount = 20;
        h.assertTrue(SpaceEvents.tickBreathing(suited, false) == SpaceRules.Breath.AIR, "with air around, no suit air is used");
        h.assertTrue(SuitItems.oxygen(suited.getItemBySlot(EquipmentSlot.CHEST)) == 1000, "air must not drain where there is air");
        h.assertTrue(SpaceEvents.tickBreathing(suited, true) == SpaceRules.Breath.SUIT, "in vacuum a full suit breathes from its tank");
        h.assertTrue(SuitItems.oxygen(suited.getItemBySlot(EquipmentSlot.CHEST)) == 980, "one second of air per second, got "
                + SuitItems.oxygen(suited.getItemBySlot(EquipmentSlot.CHEST)));
        float healthy = suited.getHealth();
        h.assertTrue(suited.getHealth() == healthy && suited.getTicksFrozen() == 0, "a suited mob must not be hurt or frozen");

        // helmet off: no air
        suited.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        h.assertTrue(SpaceRules.breathing(suited, true) == SpaceRules.Breath.NONE, "an incomplete suit must not work");

        Pig bare = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 2, 2));
        bare.tickCount = 40;
        float before = bare.getHealth();
        h.assertTrue(SpaceEvents.tickBreathing(bare, true) == SpaceRules.Breath.NONE, "no suit in vacuum: no air");
        h.assertTrue(bare.getHealth() < before, "vacuum must hurt");
        h.assertTrue(bare.getTicksFrozen() > 0, "vacuum must freeze");
        h.assertTrue(SpaceEvents.tickBreathing(bare, false) == SpaceRules.Breath.AIR, "no suit is fine where there is air");

        Pig empty = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(6, 2, 2));
        wearSuit(empty, 0);
        h.assertTrue(SpaceRules.breathing(empty, true) == SpaceRules.Breath.NONE, "a suit without air must not help");
        h.succeed();
    }

    /** A powered compressor fills the suit in its slot and the suit of a player standing next to it. */
    public static void compressorRefillsSuits(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, SpaceContent.OXYGEN_COMPRESSOR.get());
        OxygenCompressorBlockEntity be = h.getBlockEntity(pos, OxygenCompressorBlockEntity.class);
        be.fillEnergy();
        be.items().setItem(0, new ItemStack(SpaceContent.ASTRONAUT_SUIT.get()));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vec3 next = Vec3.atBottomCenterOf(h.absolutePos(pos.east()));
        player.snapTo(next.x, next.y, next.z);
        ItemStack worn = new ItemStack(SpaceContent.JET_SUIT.get());
        player.setItemSlot(EquipmentSlot.CHEST, worn);
        int energy = be.energy();
        h.succeedWhen(() -> {
            h.assertTrue(SuitItems.oxygen(be.items().getItem(0)) >= SpaceConfig.suitOxygen(), "the suit in the slot must fill up, has "
                    + SuitItems.oxygen(be.items().getItem(0)));
            h.assertTrue(SuitItems.oxygen(player.getItemBySlot(EquipmentSlot.CHEST)) > 0, "the worn Jet Suit must get air too");
            h.assertTrue(be.energy() < energy, "filling must use energy");
        });
    }

    /** A powered sealer makes a breathable bubble (no suit needed inside); unpowered, the bubble is gone. */
    public static void sealerMakesAir(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, SpaceContent.OXYGEN_SEALER.get());
        OxygenSealerBlockEntity be = h.getBlockEntity(pos, OxygenSealerBlockEntity.class);
        Pig inside = h.spawnWithNoFreeWill(EntityTypes.PIG, pos.east(2).above());
        h.runAfterDelay(3, () -> {
            h.assertTrue(!be.running(), "an unpowered sealer must not run");
            h.assertTrue(SpaceRules.breathing(inside, true) == SpaceRules.Breath.NONE, "no bubble without power");
            be.fillEnergy();
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(be.running(), "a powered sealer must run");
            BlockPos abs = h.absolutePos(pos);
            h.assertTrue(SpaceRules.inOxygenBubble(h.getLevel(), Vec3.atCenterOf(abs.east(5))), "5 blocks away is inside the bubble");
            int radius = SpaceConfig.get(SpaceConfig.SEALER_RADIUS);
            h.assertTrue(!SpaceRules.inOxygenBubble(h.getLevel(), Vec3.atCenterOf(abs.east(radius + 3))), "beyond the radius is outside");
            h.assertTrue(SpaceRules.breathing(inside, true) == SpaceRules.Breath.BUBBLE, "inside the bubble, no suit needed");
            h.assertTrue(SpaceEvents.tickBreathing(inside, true) == SpaceRules.Breath.BUBBLE && inside.getHealth() == inside.getMaxHealth(),
                    "breathing in the bubble must not hurt");
            h.destroyBlock(pos);
        });
        h.runAfterDelay(8, () -> {
            h.assertTrue(!SpaceRules.inOxygenBubble(h.getLevel(), inside.getEyePosition()), "a removed sealer leaves no bubble");
            h.succeed();
        });
    }

    private static LaunchControllerBlockEntity pad(GameTestHelper h, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(c.offset(dx, 0, dz), dx == 0 && dz == 0 ? OrbitalContent.LAUNCH_CONTROLLER.get() : OrbitalContent.LAUNCH_PAD.get());
            }
        }
        return h.getBlockEntity(c, LaunchControllerBlockEntity.class);
    }

    /**
     * A capsule mounts like any payload, but needs the crew fuel, never launches empty, boards one
     * astronaut who rides a sealed seat, and climbing out before liftoff scrubs the launch and
     * gives the fuel back.
     */
    public static void crewCapsuleRules(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 4);
        LaunchControllerBlockEntity pad = pad(h, c);
        ServerPlayer astronaut = h.makeMockServerPlayerInLevel();
        ServerPlayer second = h.makeMockServerPlayerInLevel();
        Vec3 near = Vec3.atBottomCenterOf(h.absolutePos(c.east(2)));
        astronaut.snapTo(near.x, near.y, near.z);
        second.snapTo(near.x, near.y, near.z);
        h.assertTrue(CrewLaunch.board(astronaut, pad) != null, "nothing to board on an empty pad");
        h.assertTrue(pad.mount(new ItemStack(SpaceContent.CREW_CAPSULE.get()), astronaut.getUUID()) == null, "a capsule must mount");
        h.assertTrue(pad.fuelCost() == SpaceConfig.crewFuel(), "a crewed launch costs the crew fuel");
        pad.addFuel(LaunchControllerBlockEntity.FUEL_PER_LAUNCH);
        if (SpaceConfig.crewFuel() > LaunchControllerBlockEntity.FUEL_PER_LAUNCH) {
            h.assertTrue(pad.status() == LaunchControllerBlockEntity.STATUS_NO_FUEL, "a satellite's worth of fuel is not enough for a crew");
            h.assertTrue(CrewLaunch.board(astronaut, pad) != null && !astronaut.isPassenger(), "boarding without enough fuel must fail");
        }
        pad.addFuel(LaunchControllerBlockEntity.FUEL_MAX - pad.fuel());
        h.assertTrue(pad.tryLaunch() != null && !pad.launching(), "a capsule must not launch without a crew");
        var boarded = CrewLaunch.board(astronaut, pad);
        h.assertTrue(boarded == null, "boarding a fuelled capsule must work: " + (boarded == null ? "" : boarded.getString()));
        h.assertTrue(astronaut.getVehicle() instanceof RocketSeatEntity, "the astronaut rides the capsule seat");
        h.assertTrue(SpaceRules.inSealedCabin(astronaut), "the capsule seat holds air");
        h.assertTrue(pad.launching(), "boarding starts the countdown");
        h.assertTrue(pad.fuel() == LaunchControllerBlockEntity.FUEL_MAX - SpaceConfig.crewFuel(), "the countdown burns the crew fuel");
        h.assertTrue(CrewLaunch.board(second, pad) != null && !second.isPassenger(), "one seat only");
        h.runAfterDelay(10, () -> {
            h.assertTrue(astronaut.getVehicle() instanceof RocketSeatEntity seat && !seat.strappedIn(), "before liftoff the crew may leave");
            astronaut.stopRiding();
        });
        h.runAfterDelay(14, () -> {
            h.assertTrue(!astronaut.isPassenger(), "climbing out must work before liftoff");
            h.assertTrue(!pad.launching(), "climbing out must scrub the launch");
            h.assertTrue(pad.fuel() == LaunchControllerBlockEntity.FUEL_MAX, "a scrubbed launch gives the fuel back");
            h.assertTrue(CrewLaunch.isCapsule(pad.satellite()), "the capsule stays on the pad");
            h.succeed();
        });
    }

    /** After liftoff the astronaut can't climb out mid-air. */
    public static void crewStrappedInAfterLiftoff(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 4);
        LaunchControllerBlockEntity pad = pad(h, c);
        ServerPlayer astronaut = h.makeMockServerPlayerInLevel();
        Vec3 near = Vec3.atBottomCenterOf(h.absolutePos(c.east(2)));
        astronaut.snapTo(near.x, near.y, near.z);
        pad.mount(new ItemStack(SpaceContent.CREW_CAPSULE.get()), astronaut.getUUID());
        pad.addFuel(LaunchControllerBlockEntity.FUEL_MAX);
        var boarded = CrewLaunch.board(astronaut, pad);
        h.assertTrue(boarded == null, "boarding must work: " + (boarded == null ? "" : boarded.getString()));
        h.runAfterDelay(LaunchControllerBlockEntity.LIFTOFF + 5, () -> {
            h.assertTrue(astronaut.getVehicle() instanceof RocketSeatEntity seat && seat.strappedIn(), "strapped in after liftoff");
            astronaut.stopRiding();
            h.assertTrue(astronaut.getVehicle() instanceof RocketSeatEntity, "no getting out after liftoff");
            h.assertTrue(astronaut.getY() > h.absolutePos(c).getY() + RocketSeatEntity.SEAT_Y, "the astronaut climbs with the rocket");
            CrewLaunch.abort(h.getLevel(), h.absolutePos(c)); // don't let the test player fly off to the (missing) orbit
            h.assertTrue(!astronaut.isPassenger(), "the mod itself can take the crew out");
            h.succeed();
        });
    }

    /** Thrust costs energy and pushes up; no charge, no thrust; hover slows a fall. */
    public static void jetpackThrusts(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vec3 air = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 6, 4)));
        player.snapTo(air.x, air.y, air.z);
        ItemStack pack = new ItemStack(SpaceContent.ELECTRIC_JETPACK.get());
        pack.set(ModComponents.ENERGY.get(), 1000);
        player.setItemSlot(EquipmentSlot.CHEST, pack);
        Jetpack.Tier tier = Jetpack.Tier.ELECTRIC;
        player.setDeltaMovement(Vec3.ZERO);
        Jetpack.setInput(player, true);
        h.assertTrue(Jetpack.serverTick(player) == Jetpack.Mode.THRUST, "holding jump with charge must thrust");
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        h.assertTrue(Jetpack.energy(worn) == 1000 - tier.thrustCost(), "thrust must use energy, left " + Jetpack.energy(worn));
        h.assertTrue(player.getDeltaMovement().y > 0.1, "thrust must push up, vy " + player.getDeltaMovement().y);
        worn.set(ModComponents.ENERGY.get(), 0);
        player.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(Jetpack.serverTick(player) == Jetpack.Mode.OFF && player.getDeltaMovement().y == 0, "no charge, no thrust");
        Jetpack.setInput(player, false);
        worn.set(ModComponents.ENERGY.get(), 1000);
        worn.set(SpaceContent.JETPACK_HOVER.get(), true);
        player.setDeltaMovement(0, -1.0, 0);
        h.assertTrue(Jetpack.serverTick(player) == Jetpack.Mode.HOVER, "hover mode in the air must hover");
        h.assertTrue(player.getDeltaMovement().y >= tier.hoverFall - 1e-6, "hover must slow the fall, vy " + player.getDeltaMovement().y);
        h.assertTrue(Jetpack.energy(worn) == 1000 - tier.hoverCost(), "hover must use energy");
        h.succeed();
    }

    /** Suit + Advanced Jetpack → Jet Suit, keeping the suit's air and the jetpack's charge and hover. */
    public static void jetSuitRecipeKeepsComponents(GameTestHelper h) {
        var holder = h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "jet_suit")));
        h.assertTrue(holder.isPresent() && holder.get().value() instanceof CraftingRecipe, "the jet_suit recipe must load");
        CraftingRecipe recipe = (CraftingRecipe) holder.get().value();
        ItemStack suit = new ItemStack(SpaceContent.ASTRONAUT_SUIT.get());
        SuitItems.setOxygen(suit, 1234);
        ItemStack pack = new ItemStack(SpaceContent.ADVANCED_JETPACK.get());
        pack.set(ModComponents.ENERGY.get(), 55_555);
        pack.set(SpaceContent.JETPACK_HOVER.get(), true);
        for (List<ItemStack> order : List.of(List.of(suit, pack), List.of(pack, suit))) {
            CraftingInput input = CraftingInput.of(2, 1, order);
            h.assertTrue(recipe.matches(input, h.getLevel()), "suit + advanced jetpack must match");
            ItemStack out = recipe.assemble(input);
            h.assertTrue(out.is(SpaceContent.JET_SUIT.get()), "the result is a Jet Suit, got " + out);
            h.assertTrue(SuitItems.oxygen(out) == 1234, "the suit's air must carry over, got " + SuitItems.oxygen(out));
            h.assertTrue(Jetpack.energy(out) == 55_555, "the jetpack's charge must carry over, got " + Jetpack.energy(out));
            h.assertTrue(Jetpack.hover(out), "the hover setting must carry over");
        }
        CraftingInput wrong = CraftingInput.of(2, 1, List.of(suit, new ItemStack(SpaceContent.ELECTRIC_JETPACK.get())));
        h.assertTrue(!recipe.matches(wrong, h.getLevel()), "an Electric Jetpack is not enough");
        h.succeed();
    }

    /** The starter deck: 5x5 of steel with lit corners and a Return Pod at the north edge. */
    public static void starterDeck(GameTestHelper h) {
        BlockPos centre = h.absolutePos(new BlockPos(4, 1, 4));
        Orbit.buildDeck(h.getLevel(), centre);
        h.assertTrue(h.getLevel().getBlockState(centre).is(ModBlocks.SIMPLE.get("steel_block").get()), "the deck is steel");
        h.assertTrue(h.getLevel().getBlockState(centre.offset(0, 1, -2)).is(SpaceContent.RETURN_POD.get()), "the Return Pod is on it");
        h.assertTrue(Orbit.deckCentre(new BlockPos(123, 64, -456)).equals(new BlockPos(123, Orbit.DECK_Y, -456)),
                "each launch site has its own spot straight above it");
        h.succeed();
    }
}

package net.juli2kapo.factoryascent.outpost;

import java.util.List;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.juli2kapo.factoryascent.space.planet.Navigation;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.space.planet.PlanetContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of planetary outposts, listed in {@code ModGameTests.TESTS}. The GameTest server has no
 * planet dimensions loaded, so the planet-only rules are checked as pure functions ({@link Ascent},
 * {@link DistressBeacons}) and the blocks in the test arena (which is not a planet).
 */
public final class OutpostGameTests {
    private OutpostGameTests() {}

    private static Navigation.Destination at(Planet p) {
        return Navigation.Destination.at(p.key);
    }

    /** The climb home costs a fraction of the shuttle's trip to Earth orbit, paid with the cheapest fuel first. */
    public static void ascentCosts(GameTestHelper h) {
        var costs = Navigation.Costs.DEFAULT;
        h.assertTrue(Ascent.fuelNeeded(at(Planet.MOON), costs, 0.5) == 1500, "Moon: half of 3000");
        h.assertTrue(Ascent.fuelNeeded(at(Planet.MARS), costs, 0.5) == 3500, "Mars: half of 7000");
        h.assertTrue(Ascent.fuelNeeded(at(Planet.IO), costs, 0.5) == 5500, "Io: half of 11000");
        h.assertTrue(Ascent.fuelNeeded(Navigation.Destination.EARTH_ORBIT, costs, 0.5) == 0, "no climb from orbit");
        h.assertTrue(Ascent.fuelNeeded(null, costs, 0.5) == 0, "no climb from the Overworld");
        h.assertTrue(Ascent.fuelNeeded(at(Planet.MOON), costs, 0) == 0, "fraction 0 makes it free");
        int[] values = {1000, 2000, 4000};
        int[] take = Ascent.pay(1500, values, new int[] {5, 1, 1});
        h.assertTrue(take != null && take[0] == 2 && take[1] == 0 && take[2] == 0, "Rocket Fuel first: 2 of them for 1500");
        take = Ascent.pay(1500, values, new int[] {1, 1, 0});
        h.assertTrue(take != null && take[0] == 0 && take[1] == 1, "one Hydrolox Cell covers it, so the Rocket Fuel is kept");
        take = Ascent.pay(5500, values, new int[] {1, 0, 2});
        h.assertTrue(take != null && take[2] == 2 && take[0] == 0, "two Helium-3 cells for Io, the Rocket Fuel handed back");
        h.assertTrue(Ascent.pay(3500, values, new int[] {1, 1, 0}) == null, "3000 units carried can't pay 3500");
        h.assertTrue(Ascent.worth(values, new int[] {1, 1, 1}) == 7000, "worth adds up");
        h.assertTrue(Ascent.climbSpeed(1, 60) > 0 && Ascent.climbSpeed(60, 60) >= Ascent.climbSpeed(10, 60), "the climb speeds up");
        h.assertTrue(PlanetContent.shuttleFuelValue(new ItemStack(OutpostContent.HYDROLOX_FUEL_CELL.get()), 1000) == 2000,
                "a Hydrolox Cell is worth 2 Rocket Fuel in a shuttle");
        h.succeed();
    }

    /** Off a planet the module refuses to fly and stays put; on one, it takes exactly the fuel the climb costs. */
    public static void ascentModuleBlock(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, OutpostContent.ASCENT_MODULE.get());
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(pos);
        player.snapTo(abs.getX() + 1.5, abs.getY(), abs.getZ() + 0.5);
        var state = h.getLevel().getBlockState(abs);
        state.useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertBlockPresent(OutpostContent.ASCENT_MODULE.get(), pos);
        h.assertFalse(OutpostLaunches.launching(player), "no lift-off outside a planet");
        player.getInventory().add(new ItemStack(OrbitalContent.ROCKET_FUEL.get(), 3));
        player.getInventory().add(new ItemStack(OutpostContent.HYDROLOX_FUEL_CELL.get(), 1));
        h.assertTrue(AscentModuleBlock.payFuel(player.getInventory(), 1500, player), "3 Rocket Fuel and a cell pay 1500");
        h.assertTrue(player.getInventory().countItem(OrbitalContent.ROCKET_FUEL.get()) == 1, "two Rocket Fuel burnt, one left");
        h.assertTrue(player.getInventory().countItem(OutpostContent.HYDROLOX_FUEL_CELL.get()) == 1, "the cell is kept");
        h.assertFalse(AscentModuleBlock.payFuel(player.getInventory(), 5500, player), "3000 left can't pay Io's 5500");
        h.assertTrue(player.getInventory().countItem(OrbitalContent.ROCKET_FUEL.get()) == 1, "a failed payment takes nothing");
        player.discard();
        h.succeed();
    }

    /** Beacons list per team, only on planets, cycle in a stable order, keep to the team's limit, and land rescuers beside them. */
    public static void distressBeaconRules(GameTestHelper h) {
        var moon = new DistressBeacons.Beacon(Planet.MOON.key, new BlockPos(10, 70, -5), "team:a", "Ann", 1);
        var mars = new DistressBeacons.Beacon(Planet.MARS.key, new BlockPos(-300, 80, 40000), "team:a", "Bob", 2);
        var other = new DistressBeacons.Beacon(Planet.IO.key, new BlockPos(0, 60, 0), "team:b", "Cid", 3);
        var home = new DistressBeacons.Beacon(Level.OVERWORLD, new BlockPos(0, 64, 0), "team:a", "Ann", 4);
        List<DistressBeacons.Beacon> all = List.of(home, other, mars, moon);
        List<DistressBeacons.Beacon> mine = DistressBeacons.forTeam(all, "team:a");
        h.assertTrue(mine.equals(List.of(moon, mars)), "team a sees its two planet beacons, oldest first: " + mine);
        h.assertTrue(DistressBeacons.forTeam(all, "team:c").isEmpty(), "a team without beacons sees none");
        h.assertTrue(DistressBeacons.next(mine, null) == moon, "the first pick is the oldest");
        h.assertTrue(DistressBeacons.next(mine, moon.global()) == mars, "then the next");
        h.assertTrue(DistressBeacons.next(mine, mars.global()) == moon, "and around again");
        h.assertTrue(DistressBeacons.next(mine, other.global()) == moon, "a pick that isn't listed starts over");
        h.assertTrue(DistressBeacons.next(List.of(), null) == null, "nothing to pick");
        h.assertTrue(DistressBeacons.indexOf(mine, mars.global()) == 2 && DistressBeacons.indexOf(mine, null) == 0, "1-based index");
        BlockPos spot = DistressBeacons.landingSpot(moon.pos());
        h.assertTrue(spot.distManhattan(moon.pos()) <= 6 && !spot.equals(moon.pos()), "the rescue lands beside the beacon");
        // the registry keeps a team to its limit, dropping the oldest
        DistressBeacons registry = DistressBeacons.get(h.getLevel().getServer());
        String team = "team:gametest_" + h.getLevel().getGameTime();
        var first = new DistressBeacons.Beacon(Planet.MOON.key, new BlockPos(1, 64, 1), team, "T", 1);
        var second = new DistressBeacons.Beacon(Planet.MOON.key, new BlockPos(2, 64, 1), team, "T", 2);
        var third = new DistressBeacons.Beacon(Planet.MARS.key, new BlockPos(3, 64, 1), team, "T", 3);
        h.assertTrue(registry.light(first, 2) == null && registry.light(second, 2) == null, "room for two");
        h.assertTrue(registry.light(third, 2) == first, "the third pushes out the oldest");
        h.assertTrue(registry.forTeam(team).equals(List.of(second, third)), "two left");
        registry.remove(second.dimension(), second.pos());
        registry.remove(third.dimension(), third.pos());
        h.assertTrue(registry.forTeam(team).isEmpty(), "putting them out empties the list");
        h.succeed();
    }

    /** A lit beacon is in the registry until its block is broken. */
    public static void distressBeaconBlock(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, OutpostContent.DISTRESS_BEACON.get());
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos abs = h.absolutePos(pos);
        GlobalPos where = GlobalPos.of(h.getLevel().dimension(), abs);
        DistressBeacons registry = DistressBeacons.get(h.getLevel().getServer());
        h.assertTrue(registry.at(where) == null, "a beacon placed without a player isn't lit");
        DistressBeaconBlock.light(h.getLevel(), abs, player);
        h.assertTrue(registry.at(where) != null, "lighting registers it");
        h.assertTrue(registry.forTeam(registry.at(where).team()).stream().noneMatch(b -> b.pos().equals(abs)),
                "a beacon off any planet calls nobody");
        h.setBlock(pos, Blocks.AIR);
        h.assertTrue(registry.at(where) == null, "breaking it puts it out");
        player.discard();
        h.succeed();
    }

    /** The Fuel Synthesizer turns Martian ice and an Empty Cell into a Hydrolox Cell. */
    public static void fuelSynthesizerMakesHydrolox(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, ModBlocks.machine(MachineType.FUEL_SYNTHESIZER).get());
        AbstractMachineBlockEntity be = h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
        be.energy().produce(be.energy().capacity());
        var slots = be.inventory().slots();
        be.inventory().setStack(slots.firstInput(), new ItemStack(PlanetContent.MARTIAN_ICE.get(), 2));
        be.inventory().setStack(slots.firstInput() + 1, new ItemStack(PowerContent.EMPTY_CELL.get()));
        h.succeedWhen(() -> {
            ItemStack out = be.inventory().stack(slots.firstOutput());
            h.assertTrue(out.is(OutpostContent.HYDROLOX_FUEL_CELL.get()), "expected a Hydrolox Cell, found " + out);
        });
    }

    /** An Oxygen Cell tops up a worn suit and leaves its Empty Cell. */
    public static void oxygenCellFillsSuit(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack suit = new ItemStack(SpaceContent.JET_SUIT.get());
        SuitItems.setOxygen(suit, 0);
        player.setItemSlot(EquipmentSlot.CHEST, suit);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OutpostContent.OXYGEN_CELL.get()));
        OutpostContent.OXYGEN_CELL.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        int air = SuitItems.oxygen(player.getItemBySlot(EquipmentSlot.CHEST));
        h.assertTrue(air == Math.min(OutpostConfig.oxygenCellAir(), net.juli2kapo.factoryascent.space.SpaceConfig.suitOxygen()),
                "the suit got the cell's air, has " + air);
        h.assertTrue(player.getInventory().countItem(OutpostContent.OXYGEN_CELL.get()) == 0, "the cell is used up");
        h.assertTrue(player.getInventory().countItem(PowerContent.EMPTY_CELL.get()) == 1, "the empty cell comes back");
        player.discard();
        h.succeed();
    }
}

package net.juli2kapo.factoryascent.space;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.Satellite;
import net.juli2kapo.factoryascent.orbital.SatelliteSky;
import net.juli2kapo.factoryascent.orbital.SatelliteTrack;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.space.planet.Navigation;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.space.planet.PlanetHazards;
import net.juli2kapo.factoryascent.space.station.MagneticBoots;
import net.juli2kapo.factoryascent.space.station.StationContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the planets, stations and visible satellites, listed in {@code ModGameTests.TESTS}.
 * The GameTest server has no mod dimensions loaded, so the rules are checked as pure functions and
 * on entities in the test arena; real trips are checked in the game.
 */
public final class PlanetGameTests {
    private PlanetGameTests() {}

    /** Every planet is airless with its own gravity; Mars is easier on a suit; only Io burns; storms come and go. */
    public static void planetRules(GameTestHelper h) {
        for (Planet p : Planet.values()) {
            h.assertTrue(SpaceRules.isAirless(p.key), p.id + " has no breathable air");
            h.assertTrue(Planet.of(p.key) == p, "a planet's dimension maps back to it");
            h.assertTrue(Orbit.gravityFor(p.key) == p.gravity() && p.gravity() < 1.0, p.id + " has low gravity");
            h.assertTrue(Navigation.Destination.at(p.key) != null && Navigation.Destination.at(p.key).planet == p, "a planet is a destination");
        }
        h.assertTrue(Math.abs(Planet.MOON.gravity() - 0.166) < 1e-6, "the Moon's gravity is a sixth");
        h.assertTrue(Math.abs(Planet.MARS.gravity() - 0.38) < 1e-6, "Mars' gravity is 0.38");
        h.assertTrue(Orbit.gravityFor(Level.OVERWORLD) == 1.0, "normal gravity at home");
        h.assertTrue(Orbit.gravityFor(SpaceRules.ORBIT) == SpaceConfig.orbitGravity(), "orbit keeps its own gravity");
        h.assertTrue(Navigation.Destination.at(SpaceRules.ORBIT) == Navigation.Destination.EARTH_ORBIT, "orbit is Earth orbit");
        h.assertTrue(Navigation.Destination.at(Level.OVERWORLD) == null, "the Overworld isn't a space destination");
        h.assertTrue(Planet.MARS.suitDrain() < 1.0 && Planet.MOON.suitDrain() == 1.0, "a suit lasts longer on Mars only");
        h.assertTrue(Planet.IO.hot() && !Planet.MOON.hot() && !Planet.MARS.hot(), "only Io is hot");
        h.assertTrue(PlanetHazards.heatReaches(SpaceRules.Breath.SUIT, false), "an unlined suit burns on Io");
        h.assertTrue(!PlanetHazards.heatReaches(SpaceRules.Breath.SUIT, true), "a lined suit keeps the heat out");
        h.assertTrue(!PlanetHazards.heatReaches(SpaceRules.Breath.CABIN, false), "a sealed cabin shields from the heat");
        h.assertTrue(!PlanetHazards.heatReaches(SpaceRules.Breath.BUBBLE, false), "a sealed room shields from the heat");
        boolean calm = false, storm = false;
        for (long t = 0; t < PlanetHazards.STORM_CYCLE; t += 50) {
            float s = PlanetHazards.dustStorm(t);
            h.assertTrue(s >= 0f && s <= 1f, "storm strength stays in 0..1");
            calm |= s == 0f;
            storm |= s > 0.8f;
        }
        h.assertTrue(calm && storm, "Mars has calm days and strong storms");
        h.succeed();
    }

    /** Magnetic Boots: normal gravity on a floor in low gravity; off when sneaking, far from a floor, or not worn. */
    public static void magneticBoots(GameTestHelper h) {
        h.assertTrue(MagneticBoots.gravity(0.25, true, true, false) == 1.0, "boots on a floor: normal gravity");
        h.assertTrue(MagneticBoots.gravity(0.25, true, true, true) == 0.25, "sneaking switches the magnets off");
        h.assertTrue(MagneticBoots.gravity(0.25, true, false, false) == 0.25, "no floor, nothing to hold on to");
        h.assertTrue(MagneticBoots.gravity(0.25, false, true, false) == 0.25, "without boots, low gravity");
        h.assertTrue(MagneticBoots.gravity(1.0, true, true, false) == 1.0, "at home they change nothing");
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vec3 floor = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 1, 4)));
        player.snapTo(floor.x, floor.y, floor.z);
        player.setOnGround(true);
        h.assertTrue(MagneticBoots.gravity(player, 0.166) == 0.166, "no boots worn");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(StationContent.MAGNETIC_BOOTS.get()));
        h.assertTrue(SuitItems.isSuitPiece(player.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET), "the boots count as suit boots");
        h.assertTrue(MagneticBoots.gravity(player, 0.166) == 1.0, "standing on the floor with the boots: held down");
        player.setShiftKeyDown(true);
        h.assertTrue(MagneticBoots.gravity(player, 0.166) == 0.166, "sneaking: magnets off");
        player.setShiftKeyDown(false);
        player.setOnGround(false);
        player.snapTo(floor.x, floor.y + 4.5, floor.z);
        h.assertTrue(MagneticBoots.gravity(player, 0.166) == 0.166, "high above the floor the boots can't reach it");
        h.succeed();
    }

    /**
     * The satellite list a client gets: every satellite over the sky's dimension, your team's named,
     * others' unidentified until locked; Earth orbit shows the Overworld's; tracks stay in the sky.
     */
    public static void satelliteSkyList(GameTestHelper h) {
        UUID me = UUID.randomUUID();
        Satellite mine = new Satellite(SatelliteType.UPLINK, Level.OVERWORLD, 10, "Uplink-1", me);
        Satellite theirs = new Satellite(SatelliteType.SURVEY, Level.OVERWORLD, 20, "Spy-1", UUID.randomUUID());
        Satellite locked = new Satellite(SatelliteType.DEFENSE, Level.OVERWORLD, 30, "Guard-9", UUID.randomUUID());
        List<OrbitRegistry.Owned> over = List.of(new OrbitRegistry.Owned("team:us", mine), new OrbitRegistry.Owned("team:them", theirs),
                new OrbitRegistry.Owned("team:them", locked));
        List<SatelliteSky.Entry> list = SatelliteSky.entriesFor(over, "team:us", Set.of(locked.id()), k -> k.substring(5));
        h.assertTrue(list.size() == 3, "every satellite is listed, even out of reach");
        h.assertTrue(list.get(0).own() && list.get(0).identified() && list.get(0).name().equals("Uplink-1")
                && list.get(0).owner().equals("us"), "our own satellite comes with its name");
        h.assertTrue(!list.get(1).own() && !list.get(1).identified() && list.get(1).name().isEmpty() && list.get(1).owner().isEmpty(),
                "a foreign satellite stays unidentified");
        h.assertTrue(list.get(2).identified() && list.get(2).name().equals("Guard-9") && list.get(2).owner().equals("them"),
                "a radar-locked satellite is identified");
        h.assertTrue(list.get(1).satelliteType() == SatelliteType.SURVEY, "the type is always known");
        h.assertTrue(SatelliteSky.satelliteDimension(SpaceRules.ORBIT) == Level.OVERWORLD, "Earth orbit shows the Overworld's satellites");
        h.assertTrue(SatelliteSky.satelliteDimension(Planet.MARS.key) == Planet.MARS.key, "a planet shows its own");
        for (int i = 0; i < 20; i++) {
            UUID id = UUID.randomUUID();
            double[] a = SatelliteTrack.offset(id, 1234.5, 40, 70), b = SatelliteTrack.offset(id, 1234.5, 40, 70);
            h.assertTrue(a[0] == b[0] && a[1] == b[1] && a[2] == b[2], "a satellite is in the same place for every client");
            h.assertTrue(a[1] >= 40 && a[1] <= 110, "it flies overhead, out of reach");
            double horiz = Math.sqrt(a[0] * a[0] + a[2] * a[2]);
            h.assertTrue(horiz <= SatelliteTrack.HALF_PASS + 150, "and never beyond its pass");
            double[] later = SatelliteTrack.offset(id, 1234.5 + 100, 40, 70);
            h.assertTrue(later[0] != a[0] || later[2] != a[2], "it moves across the sky");
        }
        h.succeed();
    }
}

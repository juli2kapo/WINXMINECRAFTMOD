package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Game test bodies for the Orbital age, listed in {@code ModGameTests.TESTS}. */
public final class OrbitalGameTests {
    private OrbitalGameTests() {}

    /** A unique team name per run: team data is world-wide and tests share the world. */
    private static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** create → join without invite fails → invite → join → same team; leave → solo again. */
    public static void teamCreateInviteJoin(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        String name = uniqueName("Crew");
        h.assertTrue(teams.teamOf(alice).equals(alice.toString()), "a new player must be on their own solo team");
        h.assertTrue(!teams.sameTeam(alice, bob), "two new players must not share a team");
        h.assertTrue(teams.create(alice, name).ok(), "creating a team must work");
        h.assertTrue(teams.create(bob, name) == FactoryTeams.Result.NAME_TAKEN, "a taken name must be refused");
        h.assertTrue(teams.join(bob, name) == FactoryTeams.Result.NOT_INVITED, "joining without an invite must be refused");
        h.assertTrue(teams.invite(bob, alice) == FactoryTeams.Result.NOT_IN_TEAM, "only members can invite");
        h.assertTrue(teams.invite(alice, bob).ok(), "a member must be able to invite");
        h.assertTrue(teams.join(bob, name.toUpperCase()).ok(), "joining with an invite must work (any case)");
        h.assertTrue(teams.sameTeam(alice, bob), "after joining both must be on the same team");
        h.assertTrue(teams.members(teams.teamOf(alice)).size() == 2, "the team must have two members");
        String key = teams.teamOf(bob);
        h.assertTrue(teams.leave(bob).ok(), "leaving must work");
        h.assertTrue(teams.teamOf(bob).equals(bob.toString()), "after leaving, back on the solo team");
        h.assertTrue(teams.teamOf(alice).equals(key), "the team stays for the others");
        h.assertTrue(teams.join(bob, name) == FactoryTeams.Result.NOT_INVITED, "the invite is used up");
        h.succeed();
    }

    /** A satellite on a fuelled, complete pad launches and ends up in orbit for the launcher's team, over this dimension. */
    public static void launchRegistersSatellite(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(c.offset(dx, 0, dz), dx == 0 && dz == 0 ? OrbitalContent.LAUNCH_CONTROLLER.get() : OrbitalContent.LAUNCH_PAD.get());
            }
        }
        var pad = h.getBlockEntity(c, LaunchControllerBlockEntity.class);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        MinecraftServer server = h.getLevel().getServer();
        String team = FactoryTeams.get(server).teamOf(player.getUUID());
        h.assertTrue(pad.isFormed(), "the 3x3 pad must be formed");
        h.assertTrue(pad.tryLaunch() != null, "an empty pad must not launch");
        ItemStack sat = new ItemStack(OrbitalContent.UPLINK_SATELLITE.get());
        sat.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Testbird"));
        h.assertTrue(pad.mount(sat, player.getUUID()) == null, "mounting must work");
        h.assertTrue(pad.tryLaunch() != null, "a pad without fuel must not launch");
        h.assertTrue(pad.addFuel(LaunchControllerBlockEntity.fuelValue(new ItemStack(OrbitalContent.ROCKET_FUEL.get()))),
                "rocket fuel must fill the tank");
        h.assertTrue(pad.tryLaunch() == null, "a fuelled pad must launch");
        h.assertTrue(pad.launching(), "the pad must be launching");
        var dim = h.getLevel().dimension();
        h.succeedWhen(() -> {
            var over = OrbitRegistry.get(server).over(team, dim);
            h.assertTrue(over.stream().anyMatch(s -> s.name().equals("Testbird") && s.type() == SatelliteType.UPLINK),
                    "the satellite must reach orbit for the launcher's team, got " + over);
            h.assertTrue(pad.satellite().isEmpty() && !pad.launching(), "the pad must be empty after launch");
            h.assertTrue(pad.fuel() == 0, "the launch must burn the fuel");
            h.assertTrue(OrbitalSignal.hasCoverage(server, player.getUUID(), dim), "the launcher must have coverage now");
        });
    }

    /** Uplink coverage belongs to the team: a member has it, a non-member doesn't, and not in other dimensions. */
    public static void coverageFollowsTeam(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        ServerPlayer member = h.makeMockServerPlayerInLevel();
        ServerPlayer outsider = h.makeMockServerPlayerInLevel();
        UUID founder = UUID.randomUUID();
        String name = uniqueName("Uplink");
        h.assertTrue(teams.create(founder, name).ok(), "team create");
        h.assertTrue(teams.invite(founder, member.getUUID()).ok() && teams.join(member.getUUID(), name).ok(), "team join");
        var dim = h.getLevel().dimension();
        h.assertTrue(!OrbitalSignal.hasCoverage(member), "no coverage before a launch");
        OrbitRegistry.get(server).add(teams.teamOf(founder),
                new Satellite(SatelliteType.UPLINK, dim, 0L, "Relay", founder));
        h.assertTrue(OrbitalSignal.hasCoverage(member), "a team member must have coverage");
        h.assertTrue(!OrbitalSignal.hasCoverage(outsider), "a non-member must not have coverage");
        var other = dim == net.minecraft.world.level.Level.NETHER ? net.minecraft.world.level.Level.OVERWORLD : net.minecraft.world.level.Level.NETHER;
        h.assertTrue(!OrbitalSignal.hasCoverage(server, member.getUUID(), other), "coverage is per dimension");
        h.assertTrue(!OrbitalSignal.hasSurvey(member), "an uplink is not a survey satellite");
        h.assertTrue(teams.leave(member.getUUID()).ok(), "leave");
        h.assertTrue(!OrbitalSignal.hasCoverage(member), "a player who left loses the team's coverage");
        h.assertTrue(OrbitRegistry.get(server).has(teams.teamOf(founder), dim, SatelliteType.UPLINK), "the satellite stays with the team");
        h.succeed();
    }

    /** With a Survey Satellite over the dimension, the Ground Station turns an empty map into a filled one. */
    public static void groundStationFillsMap(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, OrbitalContent.GROUND_STATION.get());
        BlockPos abs = h.absolutePos(pos);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(GroundStationBlock.makeMap(h.getLevel(), abs, player).isEmpty(), "no map without a survey satellite");
        OrbitRegistry.get(server).add(FactoryTeams.get(server).teamOf(player.getUUID()),
                new Satellite(SatelliteType.SURVEY, h.getLevel().dimension(), 0L, "Eye", player.getUUID()));
        ItemStack map = GroundStationBlock.makeMap(h.getLevel(), abs, player);
        h.assertTrue(map.is(Items.FILLED_MAP), "the station must hand out a filled map, got " + map);
        MapId id = map.get(DataComponents.MAP_ID);
        MapItemSavedData data = h.getLevel().getMapData(id);
        h.assertTrue(data != null, "the map must have data");
        h.assertTrue(data.centerX == abs.getX() && data.centerZ == abs.getZ(), "the map must be centred on the station");
        h.assertTrue(data.scale == SurveyMapper.SCALE, "the map must be at the survey scale");
        h.succeedWhen(() -> {
            h.assertTrue(!SurveyMapper.isPainting(id), "still painting");
            int filled = 0;
            for (byte b : data.colors) if (b != 0) filled++;
            h.assertTrue(filled > 128 * 128 * 9 / 10, "the whole map must be filled in, only " + filled + " pixels are");
        });
    }

    // ---------------------------------------------------------------- deorbit, radar, missiles, automation

    /** Builds the 3×3 Launch Pad around {@code c} and returns its controller. */
    private static LaunchControllerBlockEntity pad(GameTestHelper h, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(c.offset(dx, 0, dz), dx == 0 && dz == 0 ? OrbitalContent.LAUNCH_CONTROLLER.get() : OrbitalContent.LAUNCH_PAD.get());
            }
        }
        return h.getBlockEntity(c, LaunchControllerBlockEntity.class);
    }

    /** A powered radar owned by the player. */
    private static OrbitalRadarBlockEntity radar(GameTestHelper h, BlockPos pos, ServerPlayer owner) {
        h.setBlock(pos, OrbitalContent.ORBITAL_RADAR.get());
        OrbitalRadarBlockEntity radar = h.getBlockEntity(pos, OrbitalRadarBlockEntity.class);
        radar.setOwner(owner.getUUID());
        radar.fill();
        return radar;
    }

    /** A satellite of a fresh solo team (a player who isn't online) over the test's dimension. */
    private static Satellite foreign(GameTestHelper h, UUID owner, SatelliteType type, String name) {
        Satellite s = new Satellite(type, h.getLevel().dimension(), 0L, name, owner);
        OrbitRegistry.get(h.getLevel().getServer()).add(FactoryTeams.soloKey(owner), s);
        return s;
    }

    /** Mounts a missile programmed at the radar, fuels the pad and returns the launch problem (null = launched). */
    private static net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component armAndLaunch(
            GameTestHelper h, LaunchControllerBlockEntity pad, OrbitalRadarBlockEntity radar, ServerPlayer shooter) {
        ItemStack missile = new ItemStack(OrbitalContent.ASAT_MISSILE.get());
        h.assertTrue(OrbitalRadarBlock.program(h.getLevel(), radar, shooter, missile) == null, "programming the missile must work");
        h.assertTrue(AsatMissileItem.target(missile) != null, "the missile must carry its target");
        h.assertTrue(pad.mount(missile, shooter.getUUID()) == null, "mounting the missile must work");
        h.assertTrue(pad.addFuel(LaunchControllerBlockEntity.FUEL_PER_LAUNCH), "fuel");
        return pad.tryLaunch();
    }

    private static String key(net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component c) {
        return c != null && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    /** A team deorbits its own satellite (gone from the registry, coverage lost) but never another team's. */
    public static void deorbitRemovesSatellite(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        OrbitRegistry orbit = OrbitRegistry.get(server);
        var dim = h.getLevel().dimension();
        Satellite mine = new Satellite(SatelliteType.UPLINK, dim, 0L, "Doomed", player.getUUID());
        orbit.add(FactoryTeams.get(server).teamOf(player.getUUID()), mine);
        Satellite theirs = foreign(h, UUID.randomUUID(), SatelliteType.SURVEY, "NotYours");
        h.assertTrue(OrbitalSignal.hasCoverage(player), "coverage before the deorbit");
        h.assertTrue(key(OrbitalConsole.deorbit(server, player.getUUID(), theirs.id())).equals("message.factoryascent.deorbit_not_yours"),
                "deorbiting another team's satellite must be refused");
        h.assertTrue(orbit.find(theirs.id()).isPresent(), "the other team's satellite must stay");
        h.assertTrue(OrbitalConsole.deorbit(server, player.getUUID(), mine.id()) == null, "deorbiting your own satellite must work");
        h.assertTrue(orbit.find(mine.id()).isEmpty(), "the satellite must leave the registry");
        h.assertTrue(!OrbitalSignal.hasCoverage(player), "the uplink's coverage must be gone");
        h.assertTrue(OrbitalConsole.deorbit(server, player.getUUID(), mine.id()) != null, "a satellite can only come down once");
        h.succeed();
    }

    /**
     * The whole ASAT chain: the radar tracks a foreign satellite until it locks (powered, real
     * time), the missile is programmed there, launched from the pad, and the target leaves orbit.
     */
    public static void asatDestroysForeignSatellite(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        OrbitalRadarBlockEntity radar = radar(h, new BlockPos(1, 1, 1), shooter);
        Satellite target = foreign(h, UUID.randomUUID(), SatelliteType.UPLINK, "Victim");
        h.assertTrue(radar.startTracking(target.id()) == null, "tracking a foreign contact must start");
        h.assertTrue(!radar.isLocked(target.id()), "a lock takes time");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(radar.isLocked(target.id()), "the radar must lock on after tracking"))
                .thenExecute(() -> {
                    h.assertTrue(target.id().equals(radar.designated()), "the first lock becomes the target");
                    h.assertTrue(radar.energyStored() < OrbitalRadarBlockEntity.CAPACITY, "tracking must use energy");
                    var problem = armAndLaunch(h, pad, radar, shooter);
                    h.assertTrue(problem == null, "the armed missile must launch, got " + key(problem));
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(!pad.launching(), "still flying");
                    h.assertTrue(OrbitRegistry.get(server).find(target.id()).isEmpty(), "the target must be destroyed");
                    h.assertTrue(pad.satellite().isEmpty(), "the missile must be used up");
                })
                .thenSucceed();
    }

    /** Your own team's satellites can't be tracked, and a missile aimed at one won't launch. */
    public static void asatRefusesOwnTeam(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        OrbitalRadarBlockEntity radar = radar(h, new BlockPos(1, 1, 1), shooter);
        Satellite own = new Satellite(SatelliteType.SURVEY, h.getLevel().dimension(), 0L, "Ours", shooter.getUUID());
        OrbitRegistry.get(server).add(FactoryTeams.get(server).teamOf(shooter.getUUID()), own);
        h.assertTrue(key(radar.startTracking(own.id())).equals("message.factoryascent.radar_own"), "own satellites can't be tracked");
        h.assertTrue(radar.tracking() == null, "nothing must be tracked");
        h.assertTrue(radar.designate(own.id()) != null, "an own satellite can't be picked as target");
        // Even a hand-made missile aimed at it must not fly.
        ItemStack missile = new ItemStack(OrbitalContent.ASAT_MISSILE.get());
        missile.set(OrbitalContent.ASAT_TARGET.get(), new AsatMissileItem.Target(own.id(),
                net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), radar.getBlockPos()), "Ours"));
        h.assertTrue(pad.mount(missile, shooter.getUUID()) == null, "mount");
        h.assertTrue(pad.addFuel(LaunchControllerBlockEntity.FUEL_PER_LAUNCH), "fuel");
        var problem = pad.tryLaunch();
        h.assertTrue(key(problem).equals("message.factoryascent.asat_own_team"), "launching at your own team must be refused, got " + key(problem));
        h.assertTrue(!pad.launching(), "the pad must not launch");
        h.assertTrue(OrbitRegistry.get(server).find(own.id()).isPresent(), "the satellite must stay");
        h.succeed();
    }

    /** A Guardian Satellite of the target's team intercepts the missile and is used up; the target survives. */
    public static void guardianIntercepts(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        OrbitalRadarBlockEntity radar = radar(h, new BlockPos(1, 1, 1), shooter);
        UUID victims = UUID.randomUUID();
        Satellite target = foreign(h, victims, SatelliteType.SURVEY, "Protected");
        Satellite guardian = foreign(h, victims, SatelliteType.DEFENSE, "Aegis");
        radar.forceLock(target.id());
        h.assertTrue(radar.isLocked(target.id()), "lock");
        h.assertTrue(radar.designate(target.id()) == null, "designate");
        var problem = armAndLaunch(h, pad, radar, shooter);
        h.assertTrue(problem == null, "the missile must launch, got " + key(problem));
        OrbitRegistry orbit = OrbitRegistry.get(server);
        h.succeedWhen(() -> {
            h.assertTrue(!pad.launching(), "still flying");
            h.assertTrue(orbit.find(guardian.id()).isEmpty(), "the guardian must be used up");
            h.assertTrue(orbit.find(target.id()).isPresent(), "the target must survive");
        });
    }

    /** With {@code asatEnabled = false} an armed missile doesn't launch; with it on, the same missile would. */
    public static void asatDisabledBlocksLaunch(GameTestHelper h) {
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        OrbitalRadarBlockEntity radar = radar(h, new BlockPos(1, 1, 1), shooter);
        Satellite target = foreign(h, UUID.randomUUID(), SatelliteType.UPLINK, "Safe");
        radar.forceLock(target.id());
        ItemStack missile = new ItemStack(OrbitalContent.ASAT_MISSILE.get());
        h.assertTrue(OrbitalRadarBlock.program(h.getLevel(), radar, shooter, missile) == null, "program");
        h.assertTrue(pad.mount(missile, shooter.getUUID()) == null, "mount");
        h.assertTrue(pad.addFuel(LaunchControllerBlockEntity.FUEL_PER_LAUNCH), "fuel");
        net.minecraft.network.chat.Component problem;
        boolean before = net.juli2kapo.factoryascent.Config.ASAT_ENABLED.get();
        // Set and restored within this call: tests run on the server thread, so no other test sees it.
        net.juli2kapo.factoryascent.Config.ASAT_ENABLED.set(false);
        try {
            problem = pad.tryLaunch();
        } finally {
            net.juli2kapo.factoryascent.Config.ASAT_ENABLED.set(before);
        }
        h.assertTrue(key(problem).equals("message.factoryascent.asat_disabled"), "a disabled server must refuse, got " + key(problem));
        h.assertTrue(!pad.launching(), "the pad must not launch");
        h.assertTrue(pad.missileProblem(h.getLevel()) == null, "with missiles enabled the same missile is good to go");
        h.succeed();
    }

    /**
     * A hopper above the controller feeds it like a player would: one satellite (the second one
     * waits), fuel up to the tank's size, and the payload launches for the controller's owner.
     */
    public static void hopperFeedsController(GameTestHelper h) {
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos c = new BlockPos(4, 1, 4);
        LaunchControllerBlockEntity pad = pad(h, c);
        pad.setOwner(owner.getUUID());
        h.setBlock(c.above(), net.minecraft.world.level.block.Blocks.HOPPER);
        var hopper = h.getBlockEntity(c.above(), net.minecraft.world.level.block.entity.HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(OrbitalContent.UPLINK_SATELLITE.get()));
        hopper.setItem(1, new ItemStack(OrbitalContent.SURVEY_SATELLITE.get()));
        hopper.setItem(2, new ItemStack(OrbitalContent.ROCKET_FUEL.get()));
        hopper.setItem(3, new ItemStack(Items.BLAZE_POWDER, 8));
        h.succeedWhen(() -> {
            h.assertTrue(pad.satellite().is(OrbitalContent.UPLINK_SATELLITE.get()), "the first satellite must be mounted");
            h.assertTrue(pad.fuel() == LaunchControllerBlockEntity.FUEL_MAX, "the tank must fill up, fuel " + pad.fuel());
            h.assertTrue(hopper.getItem(1).is(OrbitalContent.SURVEY_SATELLITE.get()), "the second satellite must wait in the hopper");
            h.assertTrue(hopper.getItem(2).isEmpty(), "the rocket fuel must go in");
            h.assertTrue(hopper.getItem(3).getCount() == 4, "only the blaze powder that fits goes in, left " + hopper.getItem(3));
            h.assertTrue(owner.getUUID().equals(pad.owner()), "the owner launches automated payloads");
        });
    }
}

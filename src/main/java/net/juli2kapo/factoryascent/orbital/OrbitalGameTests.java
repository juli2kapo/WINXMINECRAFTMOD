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
}

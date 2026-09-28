package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    /** A Ground Station owned by the player, and the player's team key. */
    private static GroundStationBlockEntity station(GameTestHelper h, BlockPos pos, ServerPlayer owner) {
        h.setBlock(pos, OrbitalContent.GROUND_STATION.get());
        GroundStationBlockEntity station = h.getBlockEntity(pos, GroundStationBlockEntity.class);
        station.setOwner(owner.getUUID());
        return station;
    }

    /**
     * A station images nothing while its team has no Survey Satellite over the dimension; once one
     * is up it images its own (loaded) chunk into the team's survey with real map colours.
     */
    public static void surveyNeedsSatellite(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        String team = FactoryTeams.get(server).teamOf(player.getUUID());
        var dim = h.getLevel().dimension();
        BlockPos pos = new BlockPos(4, 1, 4);
        station(h, pos, player);
        long here = net.minecraft.world.level.ChunkPos.pack(h.absolutePos(pos));
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    h.assertTrue(SurveyData.get(server, team, dim).count() == 0, "nothing may be imaged without a survey satellite");
                    OrbitRegistry.get(server).add(team, new Satellite(SatelliteType.SURVEY, dim, 0L, "Eye", player.getUUID()));
                })
                .thenWaitUntil(() -> {
                    SurveyData data = SurveyData.get(server, team, dim);
                    h.assertTrue(data.has(here), "the station's own chunk must be imaged");
                    h.assertTrue(data.count() >= 1, "chunks must be imaged, got " + data.count());
                    byte[] px = data.colors(here);
                    int coloured = 0;
                    for (byte b : px) if ((b & 0xFF) >= 4) coloured++;
                    h.assertTrue(coloured > 128, "the imaged chunk must have map colours, only " + coloured + " pixels do");
                })
                .thenSucceed();
    }

    /**
     * The off-thread path that images chunks read from the save gives the same pixels as imaging
     * the loaded chunk (checked on a copy of the test's chunk serialised like the game saves it).
     */
    public static void surveyDiskImageMatchesLive(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        h.setBlock(new BlockPos(4, 1, 4), net.minecraft.world.level.block.Blocks.GOLD_BLOCK);
        h.setBlock(new BlockPos(5, 1, 4), net.minecraft.world.level.block.Blocks.WATER);
        var chunk = level.getChunkAt(abs);
        SurveyData empty = SurveyData.get(level.getServer(), "test:disk_image", level.dimension());
        SurveyScanner.Image live = SurveyScanner.image(level, chunk, empty);
        var tag = net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(level, chunk).write();
        SurveyScanner.Image saved = SurveyScanner.imageSaved(level, java.util.Optional.of(tag), null);
        h.assertTrue(saved != null, "a saved full chunk must be imaged");
        h.assertTrue(java.util.Arrays.equals(live.pixels(), saved.pixels()), "saved and live imaging must give the same pixels");
        h.assertTrue(java.util.Arrays.equals(live.south(), saved.south()), "and the same heights");
        h.assertTrue(java.util.Objects.equals(live.biome(), saved.biome()), "and the same biome: " + live.biome() + " vs " + saved.biome());
        var partial = tag.copy();
        partial.putString("Status", "minecraft:features");
        h.assertTrue(SurveyScanner.imageSaved(level, java.util.Optional.of(partial), null) == null, "chunks that aren't fully generated are skipped");
        h.succeed();
    }

    /** Survey imagery belongs to the station owner's team: another team (even with its own satellite up) doesn't get it. */
    public static void surveyIsPerTeam(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer mapper = h.makeMockServerPlayerInLevel();
        ServerPlayer other = h.makeMockServerPlayerInLevel();
        FactoryTeams teams = FactoryTeams.get(server);
        String mine = teams.teamOf(mapper.getUUID()), theirs = teams.teamOf(other.getUUID());
        var dim = h.getLevel().dimension();
        OrbitRegistry.get(server).add(mine, new Satellite(SatelliteType.SURVEY, dim, 0L, "Mine", mapper.getUUID()));
        OrbitRegistry.get(server).add(theirs, new Satellite(SatelliteType.SURVEY, dim, 0L, "Theirs", other.getUUID()));
        BlockPos pos = new BlockPos(4, 1, 4);
        station(h, pos, mapper);
        long here = net.minecraft.world.level.ChunkPos.pack(h.absolutePos(pos));
        h.succeedWhen(() -> {
            h.assertTrue(SurveyData.get(server, mine, dim).has(here), "the owner's team must get the imagery");
            h.assertTrue(SurveyData.get(server, theirs, dim).count() == 0, "another team must not get it");
            h.assertTrue(!SurveyData.get(server, mine, dim).equals(SurveyData.get(server, theirs, dim)), "separate data per team");
        });
    }

    /** Sneak-using the controller with an empty hand gives the payload back; during a launch it stays. */
    public static void sneakUseReturnsPayload(GameTestHelper h) {
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setShiftKeyDown(true);
        h.assertTrue(pad.mount(new ItemStack(OrbitalContent.SURVEY_SATELLITE.get()), player.getUUID()) == null, "mount");
        LaunchControllerBlock.interact(pad, ItemStack.EMPTY, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(pad.satellite().isEmpty(), "the payload must come off the pad");
        h.assertTrue(player.getInventory().countItem(OrbitalContent.SURVEY_SATELLITE.get()) == 1, "the player must get the satellite back");
        // during a launch it can't be taken back
        h.assertTrue(pad.mount(new ItemStack(OrbitalContent.UPLINK_SATELLITE.get()), player.getUUID()) == null, "mount again");
        h.assertTrue(pad.addFuel(LaunchControllerBlockEntity.FUEL_PER_LAUNCH), "fuel");
        h.assertTrue(pad.tryLaunch() == null, "launch");
        h.assertTrue(key(LaunchControllerBlock.takeBack(pad, player)).equals("message.factoryascent.pad_take_busy"), "busy during a launch");
        h.assertTrue(pad.satellite().is(OrbitalContent.UPLINK_SATELLITE.get()), "the payload stays during a launch");
        h.succeed();
    }

    /** Breaking the controller drops its payload, also in the middle of a launch (which is aborted). */
    public static void breakingControllerDropsPayload(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos idle = new BlockPos(2, 1, 2), busy = new BlockPos(6, 1, 6);
        LaunchControllerBlockEntity a = pad(h, idle);
        LaunchControllerBlockEntity b = pad(h, busy);
        h.assertTrue(a.mount(new ItemStack(OrbitalContent.SURVEY_SATELLITE.get()), player.getUUID()) == null, "mount a");
        h.assertTrue(b.mount(new ItemStack(OrbitalContent.GUARDIAN_SATELLITE.get()), player.getUUID()) == null, "mount b");
        h.assertTrue(b.addFuel(LaunchControllerBlockEntity.FUEL_PER_LAUNCH) && b.tryLaunch() == null, "launch b");
        h.destroyBlock(idle);
        h.destroyBlock(busy);
        h.assertItemEntityPresent(OrbitalContent.SURVEY_SATELLITE.get(), idle, 2.0);
        h.assertItemEntityPresent(OrbitalContent.GUARDIAN_SATELLITE.get(), busy, 2.0);
        h.succeed();
    }

    /** The controller screen's Launch button follows the launch rules; its fuel slot pours fuel into the tank. */
    public static void launchButtonLaunches(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        LaunchControllerMenu menu = new LaunchControllerMenu(1, player.getInventory(), pad);
        h.assertTrue(menu.clickMenuButton(player, LaunchControllerMenu.BUTTON_LAUNCH), "the button must be handled");
        h.assertTrue(!pad.launching(), "an empty pad must not launch");
        menu.getSlot(0).set(new ItemStack(OrbitalContent.UPLINK_SATELLITE.get()));
        h.assertTrue(pad.satellite().is(OrbitalContent.UPLINK_SATELLITE.get()), "the payload slot must mount the satellite");
        h.assertTrue(player.getUUID().equals(pad.owner()), "mounting from the screen makes the player the launcher");
        menu.getSlot(1).set(new ItemStack(OrbitalContent.ROCKET_FUEL.get(), 3));
        h.assertTrue(pad.fuel() == LaunchControllerBlockEntity.FUEL_MAX, "the fuel slot must fill the tank, fuel " + pad.fuel());
        h.assertTrue(menu.getSlot(1).getItem().isEmpty(), "the fuel slot stays empty");
        h.assertTrue(pad.status() == LaunchControllerBlockEntity.STATUS_READY, "the pad must report ready, got " + pad.status());
        h.assertTrue(menu.clickMenuButton(player, LaunchControllerMenu.BUTTON_LAUNCH), "the button must be handled");
        h.assertTrue(pad.launching(), "a ready pad must launch from the button");
        h.assertTrue(!menu.getSlot(0).mayPickup(player), "the payload can't be taken out during the launch");
        h.succeed();
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

    /**
     * A team may shoot down its own satellite: it can be targeted on the radar straight away (no
     * lock), the missile launches, the satellite is destroyed, and the team's own Guardian doesn't
     * intercept it.
     */
    public static void asatDestroysOwnSatellite(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        LaunchControllerBlockEntity pad = pad(h, new BlockPos(4, 1, 4));
        OrbitalRadarBlockEntity radar = radar(h, new BlockPos(1, 1, 1), shooter);
        String team = FactoryTeams.get(server).teamOf(shooter.getUUID());
        Satellite own = new Satellite(SatelliteType.SURVEY, h.getLevel().dimension(), 0L, "Junk", shooter.getUUID());
        Satellite guardian = new Satellite(SatelliteType.DEFENSE, h.getLevel().dimension(), 0L, "OwnAegis", shooter.getUUID());
        OrbitRegistry orbit = OrbitRegistry.get(server);
        orbit.add(team, own);
        orbit.add(team, guardian);
        h.assertTrue(!radar.isLocked(own.id()), "own satellites are not locked");
        h.assertTrue(radar.designate(own.id()) == null, "an own satellite can be targeted without a lock");
        var problem = armAndLaunch(h, pad, radar, shooter);
        h.assertTrue(problem == null, "the missile at an own satellite must launch, got " + key(problem));
        h.succeedWhen(() -> {
            h.assertTrue(!pad.launching(), "still flying");
            h.assertTrue(orbit.find(own.id()).isEmpty(), "the own satellite must be destroyed");
            h.assertTrue(orbit.find(guardian.id()).isPresent(), "the team's own guardian must not intercept");
        });
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

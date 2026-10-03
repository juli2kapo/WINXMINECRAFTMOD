package net.juli2kapo.factoryascent.stationkit;

import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.space.Orbit;
import net.juli2kapo.factoryascent.space.OxygenSealerBlockEntity;
import net.juli2kapo.factoryascent.space.ReturnPodBlock;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.station.StationCoreBlockEntity;
import net.juli2kapo.factoryascent.space.station.StationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** GameTests of the Station Kit, the Cargo Pod and arriving in orbit with no station (registered in ModGameTests). */
public final class StationKitGameTests {
    private StationKitGameTests() {}

    private static void clear(ServerLevel level, BlockPos centre) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-StationKits.HALF - 1, -1, -StationKits.HALF - 1),
                centre.offset(StationKits.HALF + 1, StationKits.HEIGHT + 1, StationKits.HALF + 3))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    /** A deployed kit is a closed module: its sealer fills it with air, its core is claimed for the team. */
    public static void kitDeploysSealedModule(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos centre = h.absolutePos(new BlockPos(4, 1, 3));
        String team = FactoryTeams.soloKey(UUID.randomUUID());
        String name = StationKits.deploy(level, centre, team);
        StationCoreBlockEntity core = (StationCoreBlockEntity) level.getBlockEntity(centre.offset(2, 1, -2));
        h.assertTrue(core != null && core.team().equals(team), "the core must be claimed for the launcher's team");
        h.assertTrue(!name.isEmpty() && name.equals(core.name()), "the station gets a name, got '" + name + "'");
        h.assertTrue(StationRegistry.get(level.getServer()).claiming(level.dimension(), centre, 8) != null, "the station is registered");
        h.assertTrue(level.getBlockState(centre.offset(0, 1, -2)).getBlock() instanceof ReturnPodBlock, "a Return Pod waits inside");
        h.assertTrue(StationKits.obstruction(level, centre) != null, "a second kit can't go where the first one is");
        BlockPos sealerPos = centre.offset(-2, 1, -2);
        h.succeedWhen(() -> {
            OxygenSealerBlockEntity sealer = (OxygenSealerBlockEntity) level.getBlockEntity(sealerPos);
            h.assertTrue(sealer != null && sealer.sealed(), "the module must pass the sealed-room check");
            StationRegistry.get(level.getServer()).remove(level.dimension(), core.getBlockPos());
        });
    }

    /**
     * In the orbit dimension (or, on a server without it, far away in the test level), far from anything: no landing spot (no free deck, a crew pod is
     * left instead), a kit fits; after deploying, arrivals land inside it next to the core, a
     * second kit is refused, and a Cargo Pod launched from below unloads into the core's hold.
     */
    public static void orbitArrivalKitAndCargo(GameTestHelper h) {
        ServerLevel orbit = Orbit.level(h.getLevel().getServer());
        if (orbit == null) orbit = h.getLevel(); // the game test server may run without the data pack dimensions
        int far = 30_000 + (int) (h.getLevel().getGameTime() % 100) * 64;
        BlockPos pad = new BlockPos(far, 64, far);
        BlockPos centre = StationKits.moduleCentre(pad);
        orbit.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        clear(orbit, centre);
        String team = FactoryTeams.soloKey(UUID.randomUUID());
        h.assertTrue(Orbit.landingSpot(orbit, centre, team) == null, "nothing above the pad: no free deck to land on");
        h.assertTrue(CrewPods.podSpot(orbit, centre).equals(centre), "the crew pod floats right above the pad");
        h.assertTrue(StationKits.obstruction(orbit, centre) == null, "empty orbit: the kit fits");
        h.assertTrue(StationKits.stationAbove(orbit.getServer(), orbit.dimension(), pad, team) == null, "no station yet: a Cargo Pod would be refused");
        StationKits.deploy(orbit, centre, team);
        try {
            BlockPos spot = Orbit.landingSpot(orbit, centre, team);
            h.assertTrue(spot != null && spot.getY() == centre.getY() + 1 && Math.abs(spot.getX() - centre.getX()) < StationKits.HALF
                    && Math.abs(spot.getZ() - centre.getZ()) < StationKits.HALF, "arrivals land inside the module, got " + spot);
            h.assertTrue(StationKits.obstruction(orbit, centre) != null, "a second kit is refused");
            StationRegistry.Station station = StationKits.stationAbove(orbit.getServer(), orbit.dimension(), pad, team);
            h.assertTrue(station != null, "the team's station is above the pad");
            h.assertTrue(StationKits.stationAbove(orbit.getServer(), orbit.dimension(), pad, FactoryTeams.soloKey(UUID.randomUUID())) == null,
                    "another team's cargo doesn't go to this station");
            ItemStack pod = new ItemStack(StationKitContent.CARGO_POD.get());
            NonNullList<ItemStack> items = CargoPodItem.contents(pod);
            CargoPodItem.add(items, new ItemStack(Items.IRON_BLOCK), 100);
            CargoPodItem.add(items, new ItemStack(Items.GLASS), 7);
            CargoPodItem.setContents(pod, items);
            h.assertTrue(CargoPodItem.stacks(pod) == 3, "100 iron blocks take two slots, glass one");
            int delivered = StationKits.deliver(orbit, station.pos(), pod);
            StationCoreBlockEntity core = (StationCoreBlockEntity) orbit.getBlockEntity(station.pos());
            int iron = 0, glass = 0;
            for (int i = 0; i < core.cargo().getContainerSize(); i++) {
                ItemStack s = core.cargo().getItem(i);
                if (s.is(Items.IRON_BLOCK)) iron += s.getCount();
                if (s.is(Items.GLASS)) glass += s.getCount();
            }
            h.assertTrue(delivered == 3 && iron == 100 && glass == 7, "the hold got " + iron + " iron, " + glass + " glass");
            h.assertTrue(!CargoPodItem.accepts(new ItemStack(StationKitContent.CARGO_POD.get())), "no pods inside pods");
            h.assertTrue(!CargoPodItem.accepts(new ItemStack(Items.SHULKER_BOX)), "no shulker boxes inside pods");
        } finally {
            BlockPos core = centre.offset(2, 1, -2);
            if (orbit.getBlockEntity(core) instanceof StationCoreBlockEntity c) c.cargo().clearContent();
            StationRegistry.get(orbit.getServer()).remove(orbit.dimension(), core);
            clear(orbit, centre);
        }
        h.succeed();
    }
}

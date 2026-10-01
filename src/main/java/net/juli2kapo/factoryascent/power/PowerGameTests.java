package net.juli2kapo.factoryascent.power;

import net.juli2kapo.factoryascent.fusion.TokamakCoreBlockEntity;
import net.juli2kapo.factoryascent.fusion.TokamakStructure;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.nuclear.Radiation;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlock;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlockEntity;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** GameTests of the power ladder: every generator, the centrifuge, fission, radiation and fusion. */
public final class PowerGameTests {
    private PowerGameTests() {}

    private static <T extends PowerBlockEntity> T generator(GameTestHelper h, BlockPos pos, Generator g, Class<T> type) {
        h.setBlock(pos, PowerContent.generator(g).get().defaultBlockState());
        return h.getBlockEntity(pos, type);
    }

    // ---------------------------------------------------------------- generators

    /** A Water Wheel in water turns the Kinetic Dynamo next to it, which makes FE. */
    public static void dynamoMakesFe(GameTestHelper h) {
        BlockPos wheel = new BlockPos(4, 2, 4);
        h.setBlock(wheel, ModBlocks.machine(MachineType.WATER_WHEEL).get());
        h.setBlock(new BlockPos(3, 2, 4), Blocks.WATER);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.WATER);
        h.setBlock(new BlockPos(4, 1, 4), Blocks.WATER);
        var dynamo = generator(h, new BlockPos(5, 2, 4), Generator.KINETIC_DYNAMO, KineticDynamoBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(dynamo.points() > 0, "dynamo should be driven by the wheel");
            h.assertTrue(dynamo.energy().energy() > 0, "dynamo should have made FE, has " + dynamo.energy().energy());
        });
    }

    /** Coal and a water bucket: the boiler heats past 100 C and the engine makes FE, boiling water. */
    public static void steamEngineHeatsAndGenerates(GameTestHelper h) {
        var engine = generator(h, new BlockPos(4, 1, 4), Generator.STEAM_ENGINE, SteamEngineBlockEntity.class);
        engine.inventory().setStack(SteamEngineBlockEntity.FUEL, new ItemStack(Items.COAL, 4));
        engine.inventory().setStack(SteamEngineBlockEntity.WATER_IN, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(engine.temperature() > SteamEngineBlockEntity.BOILING, "boiler should pass 100 C, is " + engine.temperature());
            h.assertTrue(engine.energy().energy() > 0, "steam engine should have made FE");
            h.assertTrue(engine.water() < 1000, "the engine should be boiling its water");
            h.assertTrue(engine.inventory().stack(SteamEngineBlockEntity.BUCKET_OUT).is(Items.BUCKET), "empty bucket should come back");
        });
    }

    /** Without air nothing burns (orbit), but the RTG keeps going there, in the dark. */
    public static void airlessOnlyRtgWorks(GameTestHelper h) {
        var engine = generator(h, new BlockPos(2, 1, 4), Generator.STEAM_ENGINE, SteamEngineBlockEntity.class);
        engine.testAirless = true;
        engine.inventory().setStack(SteamEngineBlockEntity.FUEL, new ItemStack(Items.COAL, 4));
        engine.inventory().setStack(SteamEngineBlockEntity.WATER_IN, new ItemStack(Items.WATER_BUCKET));
        var rtg = generator(h, new BlockPos(6, 1, 4), Generator.RTG, RtgBlockEntity.class);
        rtg.testAirless = true;
        h.setBlock(new BlockPos(6, 2, 4), Blocks.STONE); // a roof: no sun needed
        rtg.inventory().setStack(0, new ItemStack(PowerContent.RADIOISOTOPE_PELLET.get()));
        h.runAfterDelay(40, () -> {
            h.assertTrue(rtg.energy().energy() > 0, "RTG should make FE where there is no air");
            h.assertTrue(engine.status() == PowerBlockEntity.ST_NO_AIR, "steam engine should report no air");
            h.assertTrue(engine.temperature() <= SteamEngineBlockEntity.AMBIENT + 0.01f, "nothing may burn without air");
            h.assertTrue(engine.inventory().stack(SteamEngineBlockEntity.FUEL).getCount() == 4, "no fuel may be used without air");
            h.succeed();
        });
    }

    /** On four masts with a clear rotor disc the Wind Turbine makes FE; on three it doesn't. */
    public static void windTurbineNeedsMast(GameTestHelper h) {
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(4, y, 5), PowerContent.TURBINE_MAST.get());
        var turbine = generator(h, new BlockPos(4, 5, 5), Generator.WIND_TURBINE, WindTurbineBlockEntity.class);
        // the power comes down the mast into the cell under its foot
        h.setBlock(new BlockPos(4, 0, 5), ModBlocks.machine(MachineType.ENERGY_CELL).get());
        var cell = h.getBlockEntity(new BlockPos(4, 0, 5), AbstractMachineBlockEntity.class);
        h.assertTrue(WindTurbineBlockEntity.countMasts(h.getLevel(), h.absolutePos(new BlockPos(4, 5, 5))) == 4, "four masts");
        h.succeedWhen(() -> {
            h.assertTrue(turbine.status() == PowerBlockEntity.ST_RUNNING || turbine.status() == PowerBlockEntity.ST_FULL,
                    "turbine should run, status " + turbine.status());
            h.assertTrue(cell.energy().energy() > 0, "the turbine's FE should reach the cell under the mast");
        });
    }

    /** Wheat rots into biogas, which burns into FE. */
    public static void biogasFromCrops(GameTestHelper h) {
        var gen = generator(h, new BlockPos(4, 1, 4), Generator.BIOGAS_GENERATOR, BiogasGeneratorBlockEntity.class);
        gen.inventory().setStack(0, new ItemStack(Items.WHEAT, 8));
        h.assertTrue(BiogasGeneratorBlockEntity.gasValue(new ItemStack(Items.WHEAT)) > 0, "wheat is organic");
        h.assertTrue(BiogasGeneratorBlockEntity.gasValue(new ItemStack(Items.COBBLESTONE)) == 0, "cobblestone isn't");
        h.succeedWhen(() -> {
            h.assertTrue(gen.inventory().stack(0).getCount() < 8, "wheat should be digested");
            h.assertTrue(gen.energy().energy() > 0, "biogas should have made FE");
        });
    }

    /** A lava bucket in, an empty bucket out, and FE. */
    public static void magmaticBurnsLava(GameTestHelper h) {
        var gen = generator(h, new BlockPos(4, 1, 4), Generator.MAGMATIC_GENERATOR, MagmaticGeneratorBlockEntity.class);
        gen.inventory().setStack(MagmaticGeneratorBlockEntity.IN, new ItemStack(Items.LAVA_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(gen.inventory().stack(MagmaticGeneratorBlockEntity.OUT).is(Items.BUCKET), "empty bucket should come out");
            h.assertTrue(gen.energy().energy() >= 20 * PowerConfig.get(PowerConfig.MAGMATIC_OUTPUT) / 2, "should make FE");
        });
    }

    /** The Advanced Solar Array makes FE exactly while the sun shines on it. */
    public static void solarArrayFollowsSun(GameTestHelper h) {
        var array = generator(h, new BlockPos(4, 1, 4), Generator.SOLAR_ARRAY, SolarArrayBlockEntity.class);
        var shaded = generator(h, new BlockPos(1, 1, 1), Generator.SOLAR_ARRAY, SolarArrayBlockEntity.class);
        h.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
        h.runAfterDelay(45, () -> {
            int sun = SolarArrayBlockEntity.sunPercent(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)));
            if (sun > 0) h.assertTrue(array.energy().energy() > 0, "sun " + sun + "%: the array should make FE");
            else h.assertTrue(array.energy().energy() == 0, "no sun: no FE");
            h.assertTrue(shaded.energy().energy() == 0, "a roofed array makes nothing");
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- centrifuge

    /** 3 uranium dust -> 1 enriched + 2 depleted uranium. */
    public static void centrifugeEnriches(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, ModBlocks.machine(MachineType.CENTRIFUGE).get());
        var be = h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
        be.energy().produce(be.energy().capacity());
        be.inventory().setStack(0, new ItemStack(PowerContent.URANIUM_DUST.get(), 3));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> {
            h.assertTrue(be.inventory().stack(out).is(PowerContent.ENRICHED_URANIUM.get()), "enriched uranium out, got " + be.inventory().stack(out));
            boolean depleted = false;
            for (int i = out; i < be.inventory().slots().firstUpgrade(); i++) {
                ItemStack s = be.inventory().stack(i);
                if (s.is(PowerContent.DEPLETED_URANIUM.get()) && s.getCount() == 2) depleted = true;
            }
            h.assertTrue(depleted, "two depleted uranium out");
        });
    }

    // ---------------------------------------------------------------- fission

    /**
     * A 5x5x5 reactor with its corner at {@code o}: casing walls, the controller in the middle of the
     * north wall (facing north), and a 3x3x3 core: fuel channels in the four corner columns and the
     * middle one (15), control rods in the other four columns (12, full control).
     */
    public static ReactorControllerBlockEntity buildReactor(GameTestHelper h, BlockPos o) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    BlockPos p = o.offset(x, y, z);
                    boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    if (wall) {
                        h.setBlock(p, PowerContent.REACTOR_CASING.get());
                    } else {
                        int a = x - 1, c = z - 1;
                        boolean rodColumn = (a == 1) != (c == 1);
                        h.setBlock(p, rodColumn ? PowerContent.REACTOR_CONTROL_ROD.get() : PowerContent.REACTOR_FUEL_CHANNEL.get());
                    }
                }
            }
        }
        BlockPos controller = o.offset(2, 2, 0);
        h.setBlock(controller, PowerContent.REACTOR_CONTROLLER.get().defaultBlockState().setValue(ReactorControllerBlock.FACING, Direction.NORTH));
        var be = h.getBlockEntity(controller, ReactorControllerBlockEntity.class);
        be.rescan(h.getLevel());
        h.assertTrue(be.structure().valid(), "reactor structure should be valid: " + be.structure().error());
        h.assertTrue(be.structure().channels() == 15 && be.structure().controlRods() == 12,
                "15 channels and 12 control rods, got " + be.structure().channels() + "/" + be.structure().controlRods());
        return be;
    }

    private static void loadFuel(ReactorControllerBlockEntity r, int rods) {
        for (int i = 0; i < ReactorControllerBlockEntity.FUEL_SLOTS && rods > 0; i++) {
            int n = Math.min(16, rods);
            r.inventory().setStack(i, new ItemStack(PowerContent.FUEL_ROD.get(), n));
            rods -= n;
        }
    }

    private static void run(ReactorControllerBlockEntity r, GameTestHelper h, int ticks) {
        ServerLevel level = h.getLevel();
        for (int i = 0; i < ticks && !r.meltedDown(); i++) r.tickReactor(level);
    }

    /** Fuel makes the core heat up; pushing the control rods in cuts the heat. */
    public static void reactorHeatAndControlRods(GameTestHelper h) {
        var r = buildReactor(h, new BlockPos(2, 1, 2));
        loadFuel(r, 15);
        r.setInsertion(0);
        run(r, h, 100);
        float hot = r.heat();
        float t1 = r.temperature();
        h.assertTrue(r.activeRods() == 15, "15 rods active, got " + r.activeRods());
        h.assertTrue(hot > 0 && t1 > ReactorControllerBlockEntity.AMBIENT + 5, "core should heat with fuel: " + hot + " H/t, " + t1 + " C");
        r.setInsertion(80);
        run(r, h, 20);
        h.assertTrue(r.heat() < hot * 0.5f, "80% insertion should cut the heat: " + r.heat() + " vs " + hot);
        r.setInsertion(100);
        run(r, h, 5);
        h.assertTrue(r.heat() == 0, "fully inserted rods with full authority stop the chain reaction");
        h.succeed();
    }

    /** A redstone signal on the controller SCRAMs the reactor. */
    public static void reactorScramOnRedstone(GameTestHelper h) {
        var r = buildReactor(h, new BlockPos(2, 1, 3));
        loadFuel(r, 15);
        r.setInsertion(0);
        run(r, h, 20);
        h.assertTrue(r.heat() > 0 && !r.scrammed(), "running before the signal");
        h.setBlock(new BlockPos(4, 3, 2), Blocks.REDSTONE_BLOCK); // in front of the controller
        run(r, h, 2);
        h.assertTrue(r.redstoneScram() && r.scrammed(), "redstone should SCRAM the reactor");
        h.assertTrue(r.heat() == 0, "SCRAM stops the heat, still " + r.heat());
        h.setBlock(new BlockPos(4, 3, 2), Blocks.AIR);
        run(r, h, 2);
        h.assertTrue(!r.scrammed() && r.heat() > 0, "removing the signal releases the SCRAM");
        h.succeed();
    }

    /** Above 100 C the coolant boils off the heat: coolant is used and FE made. */
    public static void reactorCoolantMakesPower(GameTestHelper h) {
        var r = buildReactor(h, new BlockPos(2, 1, 2));
        loadFuel(r, 15);
        r.setInsertion(30);
        r.inventory().setStack(ReactorControllerBlockEntity.COOLANT_IN, new ItemStack(Items.WATER_BUCKET, 1));
        r.setTemperature(300);
        run(r, h, 1);
        h.assertTrue(r.coolant() > 900, "a water bucket gives 1000 mB of coolant, got " + r.coolant());
        h.assertTrue(r.inventory().stack(ReactorControllerBlockEntity.COOLANT_OUT).is(Items.BUCKET), "the bucket comes back");
        r.inventory().setStack(ReactorControllerBlockEntity.COOLANT_IN, new ItemStack(Items.PACKED_ICE, 1));
        run(r, h, 1);
        float coolant = r.coolant();
        h.assertTrue(coolant > 9000, "packed ice gives 9000 mB, have " + coolant);
        run(r, h, 10);
        h.assertTrue(r.coolant() < coolant, "coolant should be used: " + r.coolant());
        h.assertTrue(r.energy().energy() > 0 && r.removedHeat() > 0, "boiling coolant should make FE");
        h.succeed();
    }

    /** No coolant and the control rods out: the core passes the meltdown temperature and melts down. */
    public static void reactorMeltdown(GameTestHelper h) {
        var r = buildReactor(h, new BlockPos(2, 1, 2));
        r.testDestroyBlocks = false; // leave the arena standing
        loadFuel(r, 15);
        r.setInsertion(0);
        r.setTemperature(PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE) - 5);
        run(r, h, 400);
        h.assertTrue(r.meltedDown(), "the reactor should have melted down at " + r.temperature() + " C");
        h.assertTrue(r.inventory().stack(0).isEmpty(), "the fuel is lost in a meltdown");
        if (PowerConfig.get(PowerConfig.MELTDOWN_LEAVES_CORIUM)) {
            h.assertBlockPresent(PowerContent.CORIUM.get(), new BlockPos(3, 2, 3));
        }
        run(r, h, 5);
        h.assertTrue(r.heat() == 0, "a melted reactor makes no more heat");
        h.succeed();
    }

    /** Burnt-up rods come out as Depleted Fuel Rods; with nowhere to put them the reactor SCRAMs itself. */
    public static void reactorMakesWaste(GameTestHelper h) {
        var r = buildReactor(h, new BlockPos(2, 1, 2));
        loadFuel(r, 15);
        r.setInsertion(0);
        int life = PowerConfig.get(PowerConfig.REACTOR_ROD_LIFE_SECONDS) * 20;
        r.setBurn(life - 1);
        run(r, h, 1);
        ItemStack spent = r.inventory().stack(ReactorControllerBlockEntity.FIRST_DEPLETED);
        h.assertTrue(spent.is(PowerContent.DEPLETED_FUEL_ROD.get()), "a spent rod should come out, got " + spent);
        int fuel = 0;
        for (int i = 0; i < ReactorControllerBlockEntity.FUEL_SLOTS; i++) fuel += r.inventory().stack(i).getCount();
        h.assertTrue(fuel == 14, "one rod used up, " + fuel + " left");
        for (int i = 0; i < ReactorControllerBlockEntity.DEPLETED_SLOTS; i++) {
            r.inventory().setStack(ReactorControllerBlockEntity.FIRST_DEPLETED + i, new ItemStack(PowerContent.DEPLETED_FUEL_ROD.get(), 16));
        }
        r.setBurn(life - 1);
        run(r, h, 2);
        h.assertTrue(r.wasteScram() && r.scrammed(), "no room for waste: the reactor SCRAMs itself");
        h.succeed();
    }

    // ---------------------------------------------------------------- radiation

    /** Carrying nuclear waste gives radiation sickness; a full Hazmat Suit stops it. */
    public static void radiationAndHazmat(GameTestHelper h) {
        Player bare = h.makeMockPlayer(GameType.SURVIVAL);
        Player suited = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1, 4.5));
        bare.snapTo(pos.x, pos.y, pos.z);
        suited.snapTo(pos.x, pos.y, pos.z);
        bare.getInventory().add(new ItemStack(PowerContent.NUCLEAR_WASTE.get(), 32));
        suited.getInventory().add(new ItemStack(PowerContent.NUCLEAR_WASTE.get(), 32));
        suited.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PowerContent.HAZMAT_HELMET.get()));
        suited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PowerContent.HAZMAT_CHESTPLATE.get()));
        suited.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PowerContent.HAZMAT_LEGGINGS.get()));
        suited.setItemSlot(EquipmentSlot.FEET, new ItemStack(PowerContent.HAZMAT_BOOTS.get()));
        h.assertTrue(Radiation.shielding(suited) == 1f, "a full suit shields everything");
        for (int i = 0; i < 5; i++) {
            Radiation.tick(bare);
            Radiation.tick(suited);
        }
        h.assertTrue(Radiation.state(bare).dose() >= Radiation.SICKNESS[0], "dose should build up: " + Radiation.state(bare).dose());
        h.assertTrue(bare.hasEffect(PowerContent.RADIATION), "the unprotected player should be radiation-sick");
        h.assertTrue(Radiation.state(suited).exposure() > 0, "the suited player is still near the waste");
        h.assertTrue(Radiation.state(suited).dose() == 0, "the suit should stop the dose: " + Radiation.state(suited).dose());
        h.assertTrue(!suited.hasEffect(PowerContent.RADIATION), "the suited player stays healthy");
        // a Waste Barrel shields what's inside it
        h.setBlock(new BlockPos(4, 1, 6), PowerContent.WASTE_BARREL.get());
        var barrel = h.getBlockEntity(new BlockPos(4, 1, 6), net.juli2kapo.factoryascent.nuclear.WasteBarrelBlockEntity.class);
        barrel.setItem(0, new ItemStack(PowerContent.DEPLETED_FUEL_ROD.get(), 16));
        Player near = h.makeMockPlayer(GameType.SURVIVAL);
        near.snapTo(pos.x, pos.y, pos.z + 1);
        h.assertTrue(Radiation.exposure(near) == 0, "nothing gets out of a Waste Barrel, measured " + Radiation.exposure(near));
        h.succeed();
    }

    // ---------------------------------------------------------------- fusion

    /** Builds the 7x7x3 ring around a core at {@code c} (top layer glass over the channel, a port on top). */
    static TokamakCoreBlockEntity buildTokamak(GameTestHelper h, BlockPos c) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    Block b = switch (TokamakStructure.role(dx, dy, dz)) {
                        case CORE -> PowerContent.TOKAMAK_CORE.get();
                        case MAGNET -> PowerContent.FUSION_MAGNET.get();
                        case CHANNEL -> Blocks.AIR;
                        case CASING -> dy == 1 && Math.max(Math.abs(dx), Math.abs(dz)) == 2 ? PowerContent.REACTOR_GLASS.get()
                                : PowerContent.FUSION_CASING.get();
                    };
                    h.setBlock(p, b);
                }
            }
        }
        h.setBlock(c.offset(3, 1, 0), PowerContent.FUSION_PORT.get());
        var core = h.getBlockEntity(c, TokamakCoreBlockEntity.class);
        core.rescanNow(h.getLevel());
        h.assertTrue(core.structure().valid(), "tokamak ring should be valid: " + core.structure().error() + " at " + core.structure().bad());
        return core;
    }

    private static void tick(TokamakCoreBlockEntity core, GameTestHelper h, int n) {
        for (int i = 0; i < n; i++) core.serverTick(h.getLevel());
    }

    /** No ignition without the start-up charge and fuel; with both it ignites, burns and makes FE. */
    public static void fusionNeedsStartupAndFuel(GameTestHelper h) {
        var core = buildTokamak(h, new BlockPos(4, 2, 4));
        core.toggle();
        tick(core, h, 3);
        h.assertTrue(core.state() == TokamakCoreBlockEntity.State.CHARGING, "uncharged magnets: charging, is " + core.state());
        core.setCharge(TokamakCoreBlockEntity.startupEnergy());
        tick(core, h, 3);
        h.assertTrue(core.state() == TokamakCoreBlockEntity.State.READY, "charged but no fuel: ready, is " + core.state());
        core.inventory().setStack(TokamakCoreBlockEntity.DEUTERIUM, new ItemStack(PowerContent.DEUTERIUM_CELL.get(), 2));
        core.inventory().setStack(TokamakCoreBlockEntity.HELIUM3, new ItemStack(PowerContent.helium3(), 2));
        core.inventory().setStack(TokamakCoreBlockEntity.TRITIUM, new ItemStack(PowerContent.TRITIUM_CELL.get(), 1));
        tick(core, h, 1);
        h.assertTrue(core.state() == TokamakCoreBlockEntity.State.IGNITING, "fuel and charge: ignition, is " + core.state());
        h.assertTrue(core.inventory().stack(TokamakCoreBlockEntity.TRITIUM).isEmpty(), "ignition uses the tritium");
        h.assertTrue(core.charge() == 0, "ignition spends the magnets' charge");
        tick(core, h, TokamakCoreBlockEntity.IGNITION_TICKS + 5);
        h.assertTrue(core.state() == TokamakCoreBlockEntity.State.RUNNING, "burning, is " + core.state());
        h.assertTrue(core.output().energy() > 0 && core.rate() > 0, "fusion should make FE");
        // switching it off is a clean stop
        core.toggle();
        tick(core, h, 1);
        h.assertTrue(core.state() == TokamakCoreBlockEntity.State.COLD, "switched off: cold");
        h.succeed();
    }

    /** Breaking the ring while the plasma burns is a containment failure. */
    public static void fusionContainmentFailure(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 2, 4);
        var core = buildTokamak(h, c);
        core.toggle();
        core.setCharge(TokamakCoreBlockEntity.startupEnergy());
        core.inventory().setStack(TokamakCoreBlockEntity.DEUTERIUM, new ItemStack(PowerContent.DEUTERIUM_CELL.get(), 2));
        core.inventory().setStack(TokamakCoreBlockEntity.HELIUM3, new ItemStack(PowerContent.helium3(), 2));
        core.inventory().setStack(TokamakCoreBlockEntity.TRITIUM, new ItemStack(PowerContent.TRITIUM_CELL.get(), 1));
        tick(core, h, TokamakCoreBlockEntity.IGNITION_TICKS + 5);
        h.assertTrue(core.running(), "should be burning, is " + core.state());
        h.setBlock(c.offset(3, 0, 0), Blocks.AIR); // knock a magnet out of the outer wall
        tick(core, h, 25);
        h.assertTrue(core.state() == TokamakCoreBlockEntity.State.DISRUPTED, "containment failure should disrupt the plasma, is " + core.state());
        h.assertTrue(core.charge() == 0, "a disruption loses the charge");
        h.succeed();
    }
}

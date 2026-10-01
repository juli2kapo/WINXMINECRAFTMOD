package net.juli2kapo.factoryascent.fluid;

import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.fluid.machine.BoilerBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.DieselGeneratorBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineBlock;
import net.juli2kapo.factoryascent.fluid.machine.FuellingPortBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.OilDerrickBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.PumpBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.RefineryBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.SteamTurbineBlockEntity;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipeBlock;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipePayloads;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipeScreens;
import net.juli2kapo.factoryascent.fluid.tank.FluidTankBlockEntity;
import net.juli2kapo.factoryascent.fluid.tank.TankSize;
import net.juli2kapo.factoryascent.fusion.ElectrolyzerBlockEntity;
import net.juli2kapo.factoryascent.fusion.TokamakCoreBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlockEntity;
import net.juli2kapo.factoryascent.nuclear.ReactorPortBlock;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.power.PowerGameTests;
import net.juli2kapo.factoryascent.power.SteamEngineBlockEntity;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** GameTests of the fluids: pipes, tanks, the pump, the oil chain, steam, the reactor and electrolyzer hookups. */
public final class FluidGameTests {
    private FluidGameTests() {}

    private static FluidTankBlockEntity tank(GameTestHelper h, BlockPos pos, TankSize size) {
        h.setBlock(pos, FluidContent.TANKS.get(size).get());
        return h.getBlockEntity(pos, FluidTankBlockEntity.class);
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> T machine(GameTestHelper h, BlockPos pos, FluidMachine m, Direction facing, Class<T> type) {
        h.setBlock(pos, FluidContent.machine(m).get().defaultBlockState().setValue(FluidMachineBlock.FACING, facing));
        return h.getBlockEntity(pos, type);
    }

    private static int insert(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int in = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return in;
        }
    }

    private static ResourceHandler<FluidResource> fluidAt(GameTestHelper h, BlockPos rel, Direction side) {
        return h.getLevel().getCapability(Capabilities.Fluid.BLOCK, h.absolutePos(rel), side);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, BlockPos near) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(near);
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    // ---------------------------------------------------------------- logistics

    /** A wrenched (extracting) pipe face pumps water out of one tank, the network delivers it to the other. */
    private static void basin(GameTestHelper h, int y) {
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) if (x == 2 || x == 6 || z == 2 || z == 6) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
    }

    public static void pipeMovesFluidBetweenTanks(GameTestHelper h) {
        var from = tank(h, new BlockPos(1, 1, 4), TankSize.BRONZE);
        var to = tank(h, new BlockPos(6, 1, 4), TankSize.BRONZE);
        from.tank().setContents(Fluids.WATER, 8000);
        for (int x = 2; x <= 5; x++) h.setBlock(new BlockPos(x, 1, 4), FluidContent.FLUID_PIPES.get(x < 4 ? Tier.LV : Tier.MV).get());
        BlockPos pipe = h.absolutePos(new BlockPos(2, 1, 4));
        BlockState state = h.getLevel().getBlockState(pipe);
        h.assertTrue(state.getValue(FluidPipeBlock.PROPERTIES.get(Direction.WEST)) == PipeConnection.CONNECTED, "pipe should connect to the tank");
        h.assertTrue(((FluidPipeBlock) state.getBlock()).setExtract(h.getLevel(), pipe, state, Direction.WEST, true), "face should switch to extract");
        h.succeedWhen(() -> {
            h.assertTrue(to.tank().holds(Fluids.WATER) && to.tank().amount() >= 1000, "water should arrive, has " + to.tank().amount());
            h.assertTrue(from.tank().amount() + to.tank().amount() == 8000, "no water may be lost or made");
            h.assertTrue(from.tank().amount() <= 8000 - 1000, "the source tank should be drained");
        });
    }

    /** The pipe screen's face switch follows the Wrench's rules (only faces touching a tank or machine). */
    public static void pipeSideConfig(GameTestHelper h) {
        tank(h, new BlockPos(3, 1, 4), TankSize.BRONZE);
        h.setBlock(new BlockPos(4, 1, 4), FluidContent.FLUID_PIPES.get(Tier.LV).get());
        ServerPlayer p = player(h, new BlockPos(4, 1, 3));
        BlockPos pipe = h.absolutePos(new BlockPos(4, 1, 4));
        h.assertTrue(FluidPipeScreens.setFace(p, pipe, Direction.WEST.get3DDataValue(), FluidPipePayloads.EXTRACT), "west faces a tank");
        h.assertBlockProperty(new BlockPos(4, 1, 4), FluidPipeBlock.PROPERTIES.get(Direction.WEST), PipeConnection.EXTRACT);
        h.assertTrue(!FluidPipeScreens.setFace(p, pipe, Direction.EAST.get3DDataValue(), FluidPipePayloads.EXTRACT), "east faces nothing");
        h.assertTrue(FluidPipeScreens.setFace(p, pipe, Direction.WEST.get3DDataValue(), FluidPipePayloads.INSERT), "back to delivering");
        h.assertBlockProperty(new BlockPos(4, 1, 4), FluidPipeBlock.PROPERTIES.get(Direction.WEST), PipeConnection.CONNECTED);
        h.succeed();
    }

    /** A broken tank keeps its fluid on the item, and a tank placed from that item has it again. */
    public static void tankKeepsContents(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        var be = tank(h, pos, TankSize.STEEL);
        be.tank().setContents(Fluids.LAVA, 12_345);
        BlockPos abs = h.absolutePos(pos);
        var drops = Block.getDrops(h.getLevel().getBlockState(abs), h.getLevel(), abs, be);
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(FluidContent.TANKS.get(TankSize.STEEL).get().asItem()), "the tank drops itself: " + drops);
        ItemStack item = drops.getFirst();
        var content = item.get(FluidContent.TANK_CONTENTS.get());
        h.assertTrue(content != null && content.getFluid() == Fluids.LAVA && content.copy().getAmount() == 12_345, "the item keeps the lava: " + content);
        BlockPos other = new BlockPos(2, 1, 2);
        var placed = tank(h, other, TankSize.STEEL);
        placed.applyComponentsFromItemStack(item);
        h.assertTrue(placed.tank().holds(Fluids.LAVA) && placed.tank().amount() == 12_345, "the placed tank holds it again");
        h.succeed();
    }

    /** The pump drains a lava pool from the edges in, one source block (1000 mB) at a time. */
    public static void pumpDrainsPool(GameTestHelper h) {
        basin(h, 1);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
        }
        var pump = machine(h, new BlockPos(4, 2, 4), FluidMachine.PUMP, Direction.NORTH, PumpBlockEntity.class);
        pump.energy().produce(pump.energy().capacity());
        h.succeedWhen(() -> {
            h.assertTrue(pump.tank().holds(Fluids.LAVA) && pump.tank().amount() >= 2000, "the pump should have lava, has " + pump.tank().amount());
            int left = 0;
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) if (h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, 1, z))).isSource()) left++;
            }
            h.assertTrue(left <= 9 - 2, "pumped source blocks should be gone, " + left + " left");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(4, 1, 4))).isSource(), "the middle (nearest) goes last");
        });
    }

    // ---------------------------------------------------------------- oil

    /** A formed derrick over an oil pocket draws crude oil out of it (and leaves stone behind). */
    public static void derrickOnDeposit(GameTestHelper h) {
        basin(h, 1);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                h.setBlock(new BlockPos(x, 1, z), ModFluids.CRUDE_OIL.block().defaultBlockState());
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) if (x != 4 || z != 4) h.setBlock(new BlockPos(x, 3, z), FluidContent.DERRICK_BASE.get());
        }
        var derrick = machine(h, new BlockPos(4, 3, 4), FluidMachine.OIL_DERRICK, Direction.NORTH, OilDerrickBlockEntity.class);
        derrick.energy().produce(derrick.energy().capacity());
        h.succeedWhen(() -> {
            h.assertTrue(derrick.formed(), "the 3x3 platform should form the derrick");
            h.assertTrue(derrick.oil().holds(ModFluids.CRUDE_OIL.source()) && derrick.oil().amount() >= 1000,
                    "crude oil should come up, has " + derrick.oil().amount());
            h.assertTrue(h.getBlockState(new BlockPos(4, 3, 4)).getValue(FluidMachineBlock.FORMED), "the controller shows it is formed");
            var viaBase = fluidAt(h, new BlockPos(3, 3, 4), Direction.WEST);
            h.assertTrue(viaBase != null && viaBase.getAmountAsLong(0) >= 1000, "pipes on a Derrick Base reach the oil");
        });
    }

    /** Without its platform the derrick does nothing. */
    public static void derrickNeedsPlatform(GameTestHelper h) {
        basin(h, 1);
        h.setBlock(new BlockPos(4, 1, 4), ModFluids.CRUDE_OIL.block().defaultBlockState());
        var derrick = machine(h, new BlockPos(4, 2, 4), FluidMachine.OIL_DERRICK, Direction.NORTH, OilDerrickBlockEntity.class);
        derrick.energy().produce(derrick.energy().capacity());
        h.runAfterDelay(60, () -> {
            h.assertTrue(!derrick.formed() && derrick.oil().isEmpty(), "an unformed derrick pumps nothing");
            h.succeed();
        });
    }

    /** One batch: 1000 mB of crude makes 500 diesel, 250 rocket fuel, a plastic and two tar. */
    public static void refineryOutputs(GameTestHelper h) {
        var r = machine(h, new BlockPos(4, 1, 4), FluidMachine.REFINERY, Direction.NORTH, RefineryBlockEntity.class);
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(4, y, 4), FluidContent.REFINERY_TOWER.get());
        h.assertTrue(insert(fluidAt(h, new BlockPos(4, 3, 4), Direction.EAST), ModFluids.CRUDE_OIL.source(), 1500) == 1500,
                "a tower section takes crude oil into the refinery");
        r.energy().produce(r.energy().capacity());
        h.succeedWhen(() -> {
            h.assertTrue(r.formed(), "three towers on the controller form the refinery");
            h.assertTrue(r.diesel().amount() == RefineryBlockEntity.DIESEL, "diesel: " + r.diesel().amount());
            h.assertTrue(r.rocketFuel().amount() == RefineryBlockEntity.ROCKET_FUEL, "rocket fuel: " + r.rocketFuel().amount());
            h.assertTrue(r.crude().amount() == 500, "one batch of crude used: " + r.crude().amount());
            h.assertTrue(r.inventory().stack(RefineryBlockEntity.PLASTIC_SLOT).is(FluidContent.PLASTIC.get()), "plastic");
            h.assertTrue(r.inventory().stack(RefineryBlockEntity.TAR_SLOT).getCount() == 2, "two tar");
        });
    }

    /** The Diesel Generator burns piped diesel into FE. */
    public static void dieselGeneratorBurns(GameTestHelper h) {
        var gen = machine(h, new BlockPos(4, 1, 4), FluidMachine.DIESEL_GENERATOR, Direction.NORTH, DieselGeneratorBlockEntity.class);
        gen.testAirless = false;
        h.assertTrue(insert(fluidAt(h, new BlockPos(4, 1, 4), Direction.UP), ModFluids.DIESEL.source(), 1000) == 1000, "takes diesel");
        h.assertTrue(insert(fluidAt(h, new BlockPos(4, 1, 4), Direction.UP), ModFluids.CRUDE_OIL.source(), 1000) == 0, "refuses crude oil");
        h.runAfterDelay(20, () -> {
            h.assertTrue(gen.energy().energy() > 0, "diesel makes FE");
            h.assertTrue(gen.diesel().amount() < 1000, "diesel is burnt");
            h.succeed();
        });
    }

    /** Liquid rocket fuel in the Fuelling Port tops up a Launch Controller next to it. */
    public static void fuellingPortFuelsLaunch(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 1, 4), OrbitalContent.LAUNCH_CONTROLLER.get());
        var controller = h.getBlockEntity(new BlockPos(4, 1, 4), LaunchControllerBlockEntity.class);
        var port = machine(h, new BlockPos(5, 1, 4), FluidMachine.FUELLING_PORT, Direction.NORTH, FuellingPortBlockEntity.class);
        insert(port.fluidHandler(null), ModFluids.ROCKET_FUEL.source(), 2000);
        h.succeedWhen(() -> {
            h.assertTrue(controller.fuel() >= LaunchControllerBlockEntity.FUEL_PER_LAUNCH, "the controller should be fuelled: " + controller.fuel());
            h.assertTrue(port.fuel().amount() <= 2000 - 250, "rocket fuel is used");
        });
    }

    // ---------------------------------------------------------------- steam, reactor, fusion

    /** A Boiler next to a Steam Engine: the engine runs on its steam without any fuel of its own. */
    public static void boilerDrivesSteamEngine(GameTestHelper h) {
        var boiler = machine(h, new BlockPos(3, 1, 4), FluidMachine.BOILER, Direction.NORTH, BoilerBlockEntity.class);
        boiler.testAirless = false;
        boiler.inventory().setStack(BoilerBlockEntity.FUEL, new ItemStack(Items.COAL, 4));
        boiler.water().setContents(Fluids.WATER, 8000);
        boiler.setTemperature(BoilerBlockEntity.FULL);
        h.setBlock(new BlockPos(4, 1, 4), PowerContent.generator(Generator.STEAM_ENGINE).get());
        var engine = h.getBlockEntity(new BlockPos(4, 1, 4), SteamEngineBlockEntity.class);
        engine.testAirless = false;
        h.succeedWhen(() -> {
            h.assertTrue(engine.energy().energy() > 0, "the engine should make FE from the boiler's steam");
            h.assertTrue(engine.inventory().stack(SteamEngineBlockEntity.FUEL).isEmpty(), "with no fuel of its own");
            h.assertTrue(boiler.water().amount() < 8000, "the boiler boils its water");
        });
    }

    /** Water in through a Coolant Port; while a Steam Turbine takes steam, the reactor makes steam and the turbine FE. */
    public static void reactorSteamDrivesTurbine(GameTestHelper h) {
        var r = PowerGameTests.buildReactor(h, new BlockPos(2, 1, 2));
        BlockPos portPos = new BlockPos(4, 3, 6);
        h.setBlock(portPos, PowerContent.REACTOR_COOLANT_PORT.get().defaultBlockState().setValue(ReactorPortBlock.FACING, Direction.SOUTH));
        r.rescan(h.getLevel());
        h.assertTrue(r.structure().valid(), "the reactor with a coolant port is still valid: " + r.structure().error());
        var turbine = machine(h, new BlockPos(4, 3, 7), FluidMachine.STEAM_TURBINE, Direction.SOUTH, SteamTurbineBlockEntity.class);
        var port = fluidAt(h, portPos, Direction.SOUTH);
        h.assertTrue(port != null, "the coolant port exposes a fluid handler");
        h.assertTrue(insert(port, Fluids.WATER, 16_000) == 16_000, "the port takes water as coolant");
        h.assertTrue(insert(port, ModFluids.COOLANT.source(), 1000) == 1000, "and coolant fluid");
        h.assertTrue(r.coolant() >= 16_000 + 4000 - 1, "coolant counts 4x: " + r.coolant());
        for (int i = 0; i < 6; i++) r.inventory().setStack(i, new ItemStack(PowerContent.FUEL_ROD.get(), i < 1 ? 15 : 0));
        r.setInsertion(30);
        r.setTemperature(300);
        r.markSteamDemand(h.getLevel());
        h.succeedWhen(() -> {
            h.assertTrue(r.removedHeat() > 0, "the coolant boils");
            h.assertTrue(turbine.energy().energy() > 0, "the turbine makes FE from the reactor's steam");
            h.assertTrue(r.steamMode(h.getLevel()), "the reactor feeds the steam loop while the turbine takes steam");
        });
    }

    /** Piped water: the Electrolyzer splits it into deuterium gas and hands it to the tank next to it. */
    public static void electrolyzerMakesDeuterium(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 1, 4), ModBlocks.machine(MachineType.ELECTROLYZER).get());
        var ely = h.getBlockEntity(new BlockPos(4, 1, 4), ElectrolyzerBlockEntity.class);
        ely.energy().produce(ely.energy().capacity());
        var out = tank(h, new BlockPos(5, 1, 4), TankSize.BRONZE);
        h.assertTrue(insert(fluidAt(h, new BlockPos(4, 1, 4), Direction.UP), Fluids.WATER, 4000) == 4000, "takes water by pipe");
        h.succeedWhen(() -> {
            h.assertTrue(out.tank().holds(ModFluids.DEUTERIUM.source()) && out.tank().amount() >= 50,
                    "deuterium gas should reach the tank, has " + out.tank().amount());
            h.assertTrue(ely.waterTank().amount() < 4000, "water is split");
        });
    }

    /** The Tokamak (and its ports) take deuterium, tritium and helium-3 gas; a cell's worth stands in for a cell. */
    public static void tokamakTakesGas(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 1, 4), PowerContent.TOKAMAK_CORE.get());
        var core = h.getBlockEntity(new BlockPos(4, 1, 4), TokamakCoreBlockEntity.class);
        var handler = fluidAt(h, new BlockPos(4, 1, 4), Direction.UP);
        h.assertTrue(insert(handler, ModFluids.DEUTERIUM.source(), 1000) == 1000, "deuterium");
        h.assertTrue(insert(handler, ModFluids.TRITIUM.source(), 1000) == 1000, "tritium");
        h.assertTrue(insert(handler, ModFluids.HELIUM_3.source(), 1000) == 1000, "helium-3");
        h.assertTrue(insert(handler, Fluids.WATER, 1000) == 0, "not water");
        h.assertTrue(core.deuteriumGas() == 1000 && core.tritiumGas() == 1000 && core.helium3Gas() == 1000, "all three stored");
        h.succeed();
    }

    /** Cells are fluid containers: a tank fills an Empty Cell into a Deuterium Cell and empties it again. */
    public static void cellsAreContainers(GameTestHelper h) {
        var t = tank(h, new BlockPos(4, 1, 4), TankSize.BRONZE);
        t.tank().setContents(ModFluids.DEUTERIUM.source(), 3000);
        var fill = FluidContainers.fill(new ItemStack(PowerContent.EMPTY_CELL.get()), t.tank().fluid());
        h.assertTrue(fill != null && fill.result().is(PowerContent.DEUTERIUM_CELL.get()) && fill.amount() == 1000, "empty cell + 1000 mB = deuterium cell");
        var drain = FluidContainers.drain(new ItemStack(OrbitalContent.ROCKET_FUEL.get()));
        h.assertTrue(drain != null && drain.amount() == CellFluidHandler.ROCKET_FUEL_ITEM && drain.rest().isEmpty(), "rocket fuel item = 250 mB");
        h.succeed();
    }
}

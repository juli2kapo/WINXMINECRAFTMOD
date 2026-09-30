package net.juli2kapo.factoryascent.gametest;

import java.util.List;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.miner.MinerBlockEntity;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Automated in-world checks for the core promises of the mod. Run with {@code ./gradlew runGameTestServer}.
 */
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, FactoryAscent.MOD_ID);
    private static final Identifier ARENA = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "test_arena");

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> body) {}

    private static final List<Test> TESTS = List.of(
            new Test("quern_grinds_with_cranks", 200, ModGameTests::quernGrindsWithCranks),
            new Test("burner_crusher_doubles_ore", 300, ModGameTests::burnerCrusherDoublesOre),
            new Test("crusher_doubles_ore", 200, ModGameTests::crusherDoublesOre),
            new Test("ore_washer_triples_ore", 200, ModGameTests::oreWasherTriplesOre),
            new Test("induction_smelter_makes_titanium", 200, ModGameTests::inductionSmelterMakesTitanium),
            new Test("plasma_forge_makes_quantum_alloy", 400, ModGameTests::plasmaForgeMakesQuantumAlloy),
            new Test("press_uses_mold", 200, ModGameTests::pressUsesMold),
            new Test("recipe_needs_better_machine", 100, ModGameTests::recipeNeedsBetterMachine),
            new Test("coke_oven_multiblock", 800, ModGameTests::cokeOvenMultiblock),
            new Test("blast_furnace_makes_steel", 900, ModGameTests::blastFurnaceMakesSteel),
            new Test("generator_cable_furnace_chain", 400, ModGameTests::generatorCableFurnaceChain),
            new Test("pipe_extracts_between_chests", 100, ModGameTests::pipeExtractsBetweenChests),
            new Test("miner_leaves_world_alone", 200, ModGameTests::minerLeavesWorldAlone),
            new Test("auto_farmer_harvests_and_replants", 600, ModGameTests::autoFarmerHarvests),
            new Test("crate_keeps_contents", 40, ModGameTests::crateKeepsContents),
            new Test("energy_cell_charges_drill", 100, ModGameTests::energyCellChargesDrill),
            new Test("speed_upgrade_speeds_up", 20, ModGameTests::speedUpgrade),
            new Test("storage_interface_round_trip", 60, ModGameTests::storageInterfaceRoundTrip),
            new Test("storage_cell_keeps_contents", 60, ModGameTests::storageCellKeepsContents),
            new Test("ender_anchor_holds_a_pearl", 40, ModGameTests::enderAnchorBurnsPearls),
            new Test("recall_charm_teleports_home", 40, ModGameTests::recallCharmTeleportsHome),
            new Test("mob_capsule_round_trip", 20, net.juli2kapo.factoryascent.mobs.MobGameTests::capsuleRoundTrip),
            new Test("mob_capsule_refuses_blacklisted", 20, net.juli2kapo.factoryascent.mobs.MobGameTests::capsuleRefusesBlacklisted),
            new Test("size_rays_scale_mobs", 20, net.juli2kapo.factoryascent.mobs.MobGameTests::sizeRays),
            new Test("drill_area_breaks_3x3", 20, net.juli2kapo.factoryascent.item.drill.DrillGameTests::areaBreaksThreeByThree),
            new Test("drill_area_skips_bedrock", 20, net.juli2kapo.factoryascent.item.drill.DrillGameTests::areaSkipsBedrock),
            new Test("drill_vein_mines_cluster", 20, net.juli2kapo.factoryascent.item.drill.DrillGameTests::veinMinesCluster),
            new Test("size_ray_scope_round_trip", 20, net.juli2kapo.factoryascent.mobs.SizeRayRecipeTests::scopeRoundTrip),
            new Test("team_create_invite_join", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::teamCreateInviteJoin),
            new Test("launch_registers_satellite", 200, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::launchRegistersSatellite),
            new Test("coverage_follows_team", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::coverageFollowsTeam),
            new Test("survey_needs_satellite", 300, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::surveyNeedsSatellite),
            new Test("survey_disk_image_matches_live", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::surveyDiskImageMatchesLive),
            new Test("survey_is_per_team", 200, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::surveyIsPerTeam),
            new Test("sneak_use_returns_payload", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::sneakUseReturnsPayload),
            new Test("breaking_controller_drops_payload", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::breakingControllerDropsPayload),
            new Test("launch_button_launches", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::launchButtonLaunches),
            new Test("deorbit_removes_satellite", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::deorbitRemovesSatellite),
            new Test("asat_destroys_foreign_satellite", 800, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::asatDestroysForeignSatellite),
            new Test("asat_destroys_own_satellite", 300, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::asatDestroysOwnSatellite),
            new Test("guardian_intercepts_asat", 300, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::guardianIntercepts),
            new Test("asat_disabled_blocks_launch", 20, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::asatDisabledBlocksLaunch),
            new Test("hopper_feeds_launch_controller", 200, net.juli2kapo.factoryascent.orbital.OrbitalGameTests::hopperFeedsController),
            new Test("ui_beacon_rename_owner_only", 20, net.juli2kapo.factoryascent.ender.EnderUiGameTests::beaconRename),
            new Test("ui_charm_unlink", 20, net.juli2kapo.factoryascent.ender.EnderUiGameTests::charmUnlink),
            new Test("ui_anchor_slot_and_switch", 40, net.juli2kapo.factoryascent.ender.EnderUiGameTests::anchorMenu),
            new Test("ui_pipe_side_config", 20, net.juli2kapo.factoryascent.ui.UiGameTests::pipeSideConfig),
            new Test("ui_drill_mode_screen", 20, net.juli2kapo.factoryascent.ui.UiGameTests::drillModeScreen),
            new Test("ui_machine_sides_panel", 20, net.juli2kapo.factoryascent.ui.UiGameTests::machineSidesPanel),
            new Test("space_suit_oxygen_drains_only_airless", 20, net.juli2kapo.factoryascent.space.SpaceGameTests::suitOxygenDrainsOnlyWhereAirless),
            new Test("space_compressor_refills_suits", 400, net.juli2kapo.factoryascent.space.SpaceGameTests::compressorRefillsSuits),
            new Test("space_sealer_makes_air", 200, net.juli2kapo.factoryascent.space.SpaceGameTests::sealerMakesAir),
            new Test("space_sealed_room_flood_fill", 20, net.juli2kapo.factoryascent.space.SpaceGameTests::sealedRoomFloodFill),
            new Test("space_crew_capsule_rules", 60, net.juli2kapo.factoryascent.space.SpaceGameTests::crewCapsuleRules),
            new Test("space_crew_strapped_in_after_liftoff", 120, net.juli2kapo.factoryascent.space.SpaceGameTests::crewStrappedInAfterLiftoff),
            new Test("space_jetpack_thrusts", 20, net.juli2kapo.factoryascent.space.SpaceGameTests::jetpackThrusts),
            new Test("space_jet_suit_recipe_keeps_components", 20, net.juli2kapo.factoryascent.space.SpaceGameTests::jetSuitRecipeKeepsComponents),
            new Test("space_starter_deck", 20, net.juli2kapo.factoryascent.space.SpaceGameTests::starterDeck),
            new Test("planet_rules", 20, net.juli2kapo.factoryascent.space.PlanetGameTests::planetRules),
            new Test("station_magnetic_boots", 20, net.juli2kapo.factoryascent.space.PlanetGameTests::magneticBoots),
            new Test("satellite_sky_list", 20, net.juli2kapo.factoryascent.space.PlanetGameTests::satelliteSkyList),
            new Test("ship_navigation_rules", 20, net.juli2kapo.factoryascent.ships.ShipGameTests::navigationRules),
            new Test("sieve_sifts_gravel", 200, AgeContentTests::sieveSiftsGravel),
            new Test("water_wheel_drives_quern", 400, AgeContentTests::waterWheelDrivesQuern),
            new Test("windmill_needs_open_air", 100, AgeContentTests::windmillNeedsOpenAir),
            new Test("drying_rack_makes_leather", 600, AgeContentTests::dryingRackMakesLeather),
            new Test("backpack_keeps_contents", 20, AgeContentTests::backpackKeepsContents),
            new Test("grappling_hook_pulls", 20, AgeContentTests::grapplingHookPulls),
            new Test("charger_charges_items", 400, AgeContentTests::chargerChargesItems),
            new Test("magnet_pulls_items", 20, AgeContentTests::magnetPullsItems),
            new Test("floodlight_lights_area", 60, AgeContentTests::floodlightLightsArea),
            new Test("breaker_and_placer", 200, AgeContentTests::breakerAndPlacer),
            new Test("vacuum_hopper_collects", 200, AgeContentTests::vacuumHopperCollects),
            new Test("tree_farm_fells_and_replants", 500, AgeContentTests::treeFarmFellsAndReplants),
            new Test("industrial_grinder_quadruples", 100, AgeContentTests::industrialGrinderQuadruples),
            new Test("recycler_salvages", 200, AgeContentTests::recyclerSalvages),
            new Test("mob_farm_drops_loot", 400, AgeContentTests::mobFarmDropsLoot),
            new Test("ship_sail_rules", 20, net.juli2kapo.factoryascent.ships.ShipGameTests::sailRules),
            new Test("ship_cog_sails_on_water", 100, net.juli2kapo.factoryascent.ships.ShipGameTests::cogSails),
            new Test("ship_motor_uses_power", 100, net.juli2kapo.factoryascent.ships.ShipGameTests::motorUsesPower),
            new Test("ship_cargo_persists", 20, net.juli2kapo.factoryascent.ships.ShipGameTests::cargoPersists),
            new Test("ship_shuttle_climbs_on_fuel", 100, net.juli2kapo.factoryascent.ships.ShipGameTests::shuttleClimbs),
            new Test("ship_orbit_transfer_thresholds", 20, net.juli2kapo.factoryascent.ships.ShipGameTests::transferThresholds),
            // power ladder (power/PowerGameTests)
            new Test("power_dynamo_makes_fe", 200, net.juli2kapo.factoryascent.power.PowerGameTests::dynamoMakesFe),
            new Test("power_steam_engine_heats_and_generates", 800, net.juli2kapo.factoryascent.power.PowerGameTests::steamEngineHeatsAndGenerates),
            new Test("power_airless_only_rtg_works", 60, net.juli2kapo.factoryascent.power.PowerGameTests::airlessOnlyRtgWorks),
            new Test("power_wind_turbine_needs_mast", 120, net.juli2kapo.factoryascent.power.PowerGameTests::windTurbineNeedsMast),
            new Test("power_biogas_from_crops", 200, net.juli2kapo.factoryascent.power.PowerGameTests::biogasFromCrops),
            new Test("power_magmatic_burns_lava", 100, net.juli2kapo.factoryascent.power.PowerGameTests::magmaticBurnsLava),
            new Test("power_solar_array_follows_sun", 60, net.juli2kapo.factoryascent.power.PowerGameTests::solarArrayFollowsSun),
            new Test("power_centrifuge_enriches", 400, net.juli2kapo.factoryascent.power.PowerGameTests::centrifugeEnriches),
            new Test("power_reactor_heat_and_control_rods", 20, net.juli2kapo.factoryascent.power.PowerGameTests::reactorHeatAndControlRods),
            new Test("power_reactor_scram_on_redstone", 20, net.juli2kapo.factoryascent.power.PowerGameTests::reactorScramOnRedstone),
            new Test("power_reactor_coolant_makes_power", 20, net.juli2kapo.factoryascent.power.PowerGameTests::reactorCoolantMakesPower),
            new Test("power_reactor_meltdown", 20, net.juli2kapo.factoryascent.power.PowerGameTests::reactorMeltdown),
            new Test("power_reactor_makes_waste", 20, net.juli2kapo.factoryascent.power.PowerGameTests::reactorMakesWaste),
            new Test("power_radiation_and_hazmat", 20, net.juli2kapo.factoryascent.power.PowerGameTests::radiationAndHazmat),
            new Test("power_fusion_needs_startup_and_fuel", 20, net.juli2kapo.factoryascent.power.PowerGameTests::fusionNeedsStartupAndFuel),
            new Test("power_fusion_containment_failure", 20, net.juli2kapo.factoryascent.power.PowerGameTests::fusionContainmentFailure),
            new Test("dyson_mass_driver_launches", 200, net.juli2kapo.factoryascent.dyson.DysonGameTests::massDriverLaunches),
            new Test("dyson_mass_driver_needs_rails_and_sky", 200, net.juli2kapo.factoryascent.dyson.DysonGameTests::massDriverNeedsRailsAndSky),
            new Test("dyson_receiver_scales_with_swarm_and_sky", 100, net.juli2kapo.factoryascent.dyson.DysonGameTests::receiverScalesWithSwarmAndSky),
            new Test("dyson_milestones_fire", 20, net.juli2kapo.factoryascent.dyson.DysonGameTests::milestonesFire),
            new Test("dyson_swarm_is_per_team", 60, net.juli2kapo.factoryascent.dyson.DysonGameTests::swarmIsPerTeam),
            new Test("dyson_saves_and_loads", 20, net.juli2kapo.factoryascent.dyson.DysonGameTests::savesAndLoads),
            new Test("dyson_launch_pad_carries_collector", 200, net.juli2kapo.factoryascent.dyson.DysonGameTests::launchPadCarriesCollector)
    );

    private ModGameTests() {}

    public static void register(IEventBus modBus) {
        for (Test t : TESTS) FUNCTIONS.register(t.name(), () -> t.body());
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            Identifier id = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, t.name());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(env, ARENA, t.maxTicks(), 0, true)));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static AbstractMachineBlockEntity place(GameTestHelper h, BlockPos pos, MachineType type) {
        h.setBlock(pos, ModBlocks.machine(type).get());
        return h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
    }

    private static void charge(AbstractMachineBlockEntity be) {
        be.energy().produce(be.energy().capacity());
    }

    private static ItemStack slot(AbstractMachineBlockEntity be, int index) {
        return be.inventory().stack(index);
    }

    private static Item mat(String name) {
        return ModItems.MATERIALS.get(name).get();
    }

    private static void expect(GameTestHelper h, ItemStack stack, Item item, int count, String what) {
        h.assertTrue(stack.is(item) && stack.getCount() >= count,
                what + ": expected " + count + "x " + item + " but found " + stack);
    }

    private static void fuel(AbstractMachineBlockEntity be, ItemStack fuel) {
        be.inventory().setStack(be.inventory().slots().firstFuel(), fuel);
    }

    /** Fills the 3x3x3 cube whose front-bottom-centre is the controller at {@code c} (facing north). */
    private static void cube(GameTestHelper h, BlockPos c, net.minecraft.world.level.block.Block wall, boolean hollow) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = 0; dz <= 2; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (p.equals(c)) continue;
                    boolean center = dx == 0 && dy == 1 && dz == 1;
                    h.setBlock(p, center && hollow ? Blocks.AIR : wall);
                }
            }
        }
    }

    // ---------------------------------------------------------------- tests

    private static void quernGrindsWithCranks(GameTestHelper h) {
        var quern = (ProcessingMachineBlockEntity) place(h, new BlockPos(4, 1, 4), MachineType.QUERN);
        quern.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 4));
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        for (int i = 0; i < 30; i++) h.runAfterDelay(5L * i + 1, () -> quern.crank(player));
        int out = quern.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(quern, out), mat("iron_dust"), 1, "quern output"));
    }

    private static void burnerCrusherDoublesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.BURNER_CRUSHER);
        fuel(be, new ItemStack(Items.COAL, 2));
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("iron_dust"), 2, "burner crusher output"));
    }

    private static void crusherDoublesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.CRUSHER);
        charge(be);
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("iron_dust"), 2, "crusher output"));
    }

    /** The Ore Washer (grade 4) runs the 3-dust recipe, not the Crusher's 2. */
    private static void oreWasherTriplesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.ORE_WASHER);
        charge(be);
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("iron_dust"), 3, "ore washer output"));
    }

    /** Titanium needs grade 5: the Induction Smelter makes it. */
    private static void inductionSmelterMakesTitanium(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.INDUCTION_SMELTER);
        charge(be);
        be.inventory().setStack(0, new ItemStack(mat("titanium_dust")));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("titanium_ingot"), 1, "induction smelter output"));
    }

    /** Quantum alloy needs grade 7: the Plasma Forge makes it. */
    private static void plasmaForgeMakesQuantumAlloy(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.PLASMA_FORGE);
        charge(be);
        be.inventory().setStack(0, new ItemStack(mat("titanium_ingot")));
        be.inventory().setStack(1, new ItemStack(Items.ENDER_PEARL));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("quantum_alloy_ingot"), 1, "plasma forge output"));
    }

    private static void pressUsesMold(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.METAL_PRESS);
        charge(be);
        var s = be.inventory().slots();
        be.inventory().setStack(s.firstInput(), new ItemStack(Items.IRON_INGOT, 2));
        be.inventory().setStack(s.firstMold(), new ItemStack(ModItems.MOLDS.get("gear_mold").get()));
        h.succeedWhen(() -> {
            expect(h, slot(be, s.firstOutput()), mat("iron_gear"), 1, "press output");
            h.assertTrue(!slot(be, s.firstMold()).isEmpty(), "mould must not be consumed");
        });
    }

    private static void recipeNeedsBetterMachine(GameTestHelper h) {
        var furnace = place(h, new BlockPos(4, 1, 4), MachineType.ELECTRIC_FURNACE);
        charge(furnace);
        furnace.inventory().setStack(0, new ItemStack(mat("titanium_dust")));
        int out = furnace.inventory().slots().firstOutput();
        h.runAfterDelay(60, () -> {
            h.assertTrue(slot(furnace, out).isEmpty(), "an Electric Furnace must not smelt titanium");
            h.assertTrue(furnace.status() == AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW, "should report it needs a better machine");
            h.succeed();
        });
    }

    private static void cokeOvenMultiblock(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 2);
        var oven = place(h, c, MachineType.COKE_OVEN);
        oven.inventory().setStack(0, new ItemStack(Items.COAL, 2));
        h.runAfterDelay(45, () -> {
            h.assertTrue(oven.status() == AbstractMachineBlockEntity.STATUS_INCOMPLETE, "no structure yet: must say incomplete");
            cube(h, c, ModBlocks.COKE_OVEN_BRICKS.get(), false);
        });
        int out = oven.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(oven, out), mat("coke"), 1, "coke oven output"));
    }

    private static void blastFurnaceMakesSteel(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 2);
        cube(h, c, ModBlocks.FIRE_BRICKS.get(), true);
        var furnace = place(h, c, MachineType.BLAST_FURNACE);
        fuel(furnace, new ItemStack(mat("coke"), 4));
        furnace.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 2));
        int out = furnace.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(furnace, out), mat("steel_ingot"), 1, "blast furnace output"));
    }

    private static void generatorCableFurnaceChain(GameTestHelper h) {
        var gen = place(h, new BlockPos(1, 1, 4), MachineType.COMBUSTION_GENERATOR);
        for (int x = 2; x <= 5; x++) h.setBlock(new BlockPos(x, 1, 4), ModBlocks.POWER_CABLES.get(Tier.LV).get());
        var furnace = place(h, new BlockPos(6, 1, 4), MachineType.ELECTRIC_FURNACE);
        fuel(gen, new ItemStack(Items.COAL, 4));
        furnace.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 3));
        int out = furnace.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(furnace, out), Items.IRON_INGOT, 3, "furnace powered through cables"));
    }

    private static void pipeExtractsBetweenChests(GameTestHelper h) {
        BlockPos from = new BlockPos(2, 1, 4), pipePos = new BlockPos(3, 1, 4), to = new BlockPos(5, 1, 4);
        h.setBlock(from, Blocks.CHEST);
        h.setBlock(pipePos, ModBlocks.ITEM_PIPES.get(Tier.LV).get());
        h.setBlock(new BlockPos(4, 1, 4), ModBlocks.ITEM_PIPES.get(Tier.LV).get());
        h.setBlock(to, Blocks.CHEST);
        h.getBlockEntity(from, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 10));
        BlockPos abs = h.absolutePos(pipePos);
        var state = h.getLevel().getBlockState(abs);
        ((ItemPipeBlock) state.getBlock()).toggleExtract(h.getLevel(), abs, state, Direction.WEST);
        h.succeedWhen(() -> {
            Container chest = h.getBlockEntity(to, ChestBlockEntity.class);
            int total = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(Items.DIAMOND)) total += chest.getItem(i).getCount();
            }
            h.assertTrue(total == 10, "expected 10 diamonds moved, found " + total);
        });
    }

    /**
     * The miner never digs the world around it. The GameTest server builds its world without data
     * pack dimensions, so The Deep is missing here and the miner must wait harmlessly; digging the
     * claim itself is covered on a dedicated server (see docs/DESIGN.md, "The Deep").
     */
    private static void minerLeavesWorldAlone(GameTestHelper h) {
        BlockPos minerPos = new BlockPos(4, 4, 4);
        BlockPos localOre = new BlockPos(4, 2, 4);
        h.setBlock(localOre, Blocks.IRON_ORE);
        var miner = (MinerBlockEntity) place(h, minerPos, MachineType.MINER);
        charge(miner);
        boolean deepExists = net.juli2kapo.factoryascent.miner.TheDeep.level(h.getLevel().getServer()) != null;
        h.runAfterDelay(100, () -> {
            h.assertBlockPresent(Blocks.IRON_ORE, localOre);
            if (deepExists) {
                h.assertTrue(miner.claim() >= 0, "miner should have claimed a chunk in The Deep");
            } else {
                h.assertTrue(miner.claim() < 0 && miner.mined() == 0, "without The Deep the miner must not dig");
            }
            h.succeed();
        });
    }

    private static void autoFarmerHarvests(GameTestHelper h) {
        // Farmer at z=0 facing south would need a rotation; default north: field is at z-1.. so place it at the back.
        BlockPos farmerPos = new BlockPos(4, 2, 8);
        BlockPos crop = new BlockPos(4, 2, 6);
        h.setBlock(crop.below(), Blocks.FARMLAND);
        h.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        var farmer = place(h, farmerPos, MachineType.AUTO_FARMER);
        charge(farmer);
        h.succeedWhen(() -> {
            boolean wheat = false;
            for (int i = farmer.inventory().slots().firstOutput(); i < farmer.inventory().slots().firstUpgrade(); i++) {
                wheat |= slot(farmer, i).is(Items.WHEAT);
            }
            h.assertTrue(wheat, "farmer should have harvested wheat");
            h.assertBlockPresent(Blocks.WHEAT, crop);
        });
    }

    private static void crateKeepsContents(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, ModBlocks.WOODEN_CRATE.get());
        h.getBlockEntity(pos, net.juli2kapo.factoryascent.storage.CrateBlockEntity.class).setItem(3, new ItemStack(Items.EMERALD, 7));
        h.getLevel().destroyBlock(h.absolutePos(pos), true);
        h.succeedWhen(() -> {
            var entities = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(2));
            boolean ok = entities.stream().anyMatch(e -> e.getItem().is(ModBlocks.WOODEN_CRATE.get().asItem())
                    && e.getItem().get(DataComponents.CONTAINER) != null
                    && e.getItem().get(DataComponents.CONTAINER).nonEmptyItemCopyStream().anyMatch(s -> s.is(Items.EMERALD) && s.getCount() == 7));
            h.assertTrue(ok, "crate item should drop with the emeralds inside");
            h.assertTrue(entities.stream().noneMatch(e -> e.getItem().is(Items.EMERALD)), "emeralds must not spill");
        });
    }

    private static void energyCellChargesDrill(GameTestHelper h) {
        var cell = place(h, new BlockPos(4, 1, 4), MachineType.ENERGY_CELL);
        charge(cell);
        fuel(cell, new ItemStack(ModItems.ELECTRIC_DRILL.get()));
        h.succeedWhen(() -> {
            ItemStack drill = slot(cell, cell.inventory().slots().firstFuel());
            h.assertTrue(drill.getOrDefault(ModComponents.ENERGY.get(), 0) > 0, "drill should be charging");
        });
    }

    private static void speedUpgrade(GameTestHelper h) {
        var crusher = place(h, new BlockPos(2, 1, 4), MachineType.CRUSHER);
        var s = crusher.inventory().slots();
        crusher.inventory().setStack(s.firstUpgrade(), new ItemStack(ModItems.SPEED_UPGRADE.get()));
        h.assertTrue(Math.abs(crusher.speedMultiplier() - 1.5f) < 0.001f, "one speed upgrade = x1.5");
        h.succeed();
    }

    // ---------------------------------------------------------------- storage network

    /** Controller, cable, drive with one 1k cell, interface; returns the drive. */
    private static net.juli2kapo.factoryascent.storagenet.StorageDriveBlockEntity storageNetwork(GameTestHelper h, boolean power) {
        h.setBlock(new BlockPos(2, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageContent.CONTROLLER.get());
        h.setBlock(new BlockPos(3, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageContent.CABLE.get());
        h.setBlock(new BlockPos(4, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageContent.DRIVE.get());
        h.setBlock(new BlockPos(5, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageContent.INTERFACE.get());
        if (power) {
            h.getBlockEntity(new BlockPos(2, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageControllerBlockEntity.class).fill();
        }
        var drive = h.getBlockEntity(new BlockPos(4, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageDriveBlockEntity.class);
        drive.setCell(0, new ItemStack(net.juli2kapo.factoryascent.storagenet.StorageContent.CELL_1K.get()));
        return drive;
    }

    private static net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> interfaceHandler(GameTestHelper h) {
        var handler = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                h.absolutePos(new BlockPos(5, 1, 4)), Direction.EAST);
        h.assertTrue(handler != null, "storage interface must expose an item handler");
        return handler;
    }

    private static void storageInterfaceRoundTrip(GameTestHelper h) {
        var drive = storageNetwork(h, false);
        var controller = h.getBlockEntity(new BlockPos(2, 1, 4), net.juli2kapo.factoryascent.storagenet.StorageControllerBlockEntity.class);
        var diamond = net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIAMOND);
        h.runAfterDelay(3, () -> {
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(interfaceHandler(h).insert(diamond, 10, tx) == 0, "an unpowered network must not accept items");
            }
            controller.fill();
        });
        h.runAfterDelay(8, () -> {
            var handler = interfaceHandler(h);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(handler.insert(diamond, 100, tx) == 100, "should insert 100 diamonds");
                // Aborted: nothing may stick.
            }
            h.assertTrue(net.juli2kapo.factoryascent.storagenet.StorageCellItem.contents(drive.cell(0)).isEmpty(),
                    "an aborted transaction must leave the cell empty");
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(handler.insert(diamond, 100, tx) == 100, "should insert 100 diamonds");
                tx.commit();
            }
            h.assertTrue(net.juli2kapo.factoryascent.storagenet.StorageCellItem.contents(drive.cell(0)).count(diamond) == 100,
                    "the cell should hold 100 diamonds");
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(handler.extract(diamond, 40, tx) == 40, "should extract 40 diamonds");
                tx.commit();
            }
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(handler.extract(diamond, 1000, tx) == 60, "only 60 diamonds should be left");
            }
            h.assertTrue(net.juli2kapo.factoryascent.storagenet.StorageCellItem.contents(drive.cell(0)).count(diamond) == 60,
                    "the cell should hold 60 diamonds after extracting 40");
            var full = net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COBBLESTONE);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(handler.insert(full, 5000, tx) == 1024 - 60, "a 1k cell holds 1024 items in total");
            }
            h.succeed();
        });
    }

    private static void storageCellKeepsContents(GameTestHelper h) {
        var drive = storageNetwork(h, true);
        var emerald = net.neoforged.neoforge.transfer.item.ItemResource.of(Items.EMERALD);
        h.runAfterDelay(5, () -> {
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(interfaceHandler(h).insert(emerald, 37, tx) == 37, "should insert 37 emeralds");
                tx.commit();
            }
            // Take the cell out through the drive's slots, as the GUI does.
            var slots = drive.cellSlots();
            var cellResource = slots.getResource(0);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(slots.extract(0, cellResource, 1, tx) == 1, "the cell should come out of the drive");
                tx.commit();
            }
            ItemStack cell = cellResource.toStack(1);
            h.assertTrue(drive.cell(0).isEmpty(), "the drive slot should be empty");
            h.assertTrue(net.juli2kapo.factoryascent.storagenet.StorageCellItem.contents(cell).count(emerald) == 37,
                    "the removed cell should still hold 37 emeralds");
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(interfaceHandler(h).extract(emerald, 64, tx) == 0, "with the cell gone the network is empty");
            }
            // Put it back and break the drive: the cell drops with its contents and nothing spills.
            drive.setCell(0, cell);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(interfaceHandler(h).extract(emerald, 64, tx) == 37, "the re-inserted cell should be readable again");
            }
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(4, 1, 4)), true);
        });
        h.runAfterDelay(8, () -> {
            var entities = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(4, 1, 4))).inflate(2));
            boolean ok = entities.stream().anyMatch(e -> e.getItem().getItem() instanceof net.juli2kapo.factoryascent.storagenet.StorageCellItem
                    && net.juli2kapo.factoryascent.storagenet.StorageCellItem.contents(e.getItem()).count(emerald) == 37);
            h.assertTrue(ok, "the broken drive should drop the cell with 37 emeralds inside");
            h.assertTrue(entities.stream().noneMatch(e -> e.getItem().is(Items.EMERALD)), "emeralds must not spill");
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- ender tech

    /** One pearl switches the anchor on for good; a second is refused; breaking it frees the pearl slot. */
    private static void enderAnchorBurnsPearls(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        var lower = net.juli2kapo.factoryascent.ender.EnderContent.ENDER_ANCHOR.get().defaultBlockState();
        h.setBlock(pos, lower);
        h.setBlock(pos.above(), lower.setValue(net.juli2kapo.factoryascent.ender.EnderAnchorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var anchor = h.getBlockEntity(pos, net.juli2kapo.factoryascent.ender.EnderAnchorBlockEntity.class);
        h.assertTrue(anchor.insertPearl(), "an empty anchor should take a pearl");
        h.assertFalse(anchor.insertPearl(), "a second pearl should be refused");
        h.runAfterDelay(5, () -> {
            h.assertBlockProperty(pos, net.juli2kapo.factoryascent.ender.EnderAnchorBlock.ACTIVE, true);
            h.assertBlockProperty(pos.above(), net.juli2kapo.factoryascent.ender.EnderAnchorBlock.ACTIVE, true);
            // Breaking the top takes the bottom with it.
            h.setBlock(pos.above(), Blocks.AIR);
            h.runAfterDelay(1, () -> {
                h.assertBlockNotPresent(net.juli2kapo.factoryascent.ender.EnderContent.ENDER_ANCHOR.get(), pos);
                h.succeed();
            });
        });
    }

    /** A linked beacon with a pearl pulls the player on top of itself; the pearl is used up, like a stasis chamber. */
    private static void recallCharmTeleportsHome(GameTestHelper h) {
        BlockPos beaconPos = new BlockPos(6, 1, 6);
        h.setBlock(beaconPos, net.juli2kapo.factoryascent.ender.EnderContent.ENDER_BEACON.get());
        var beacon = h.getBlockEntity(beaconPos, net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity.class);
        h.assertTrue(beacon.insertPearl(), "an empty beacon should take a pearl");
        var player = h.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        BlockPos start = h.absolutePos(new BlockPos(1, 2, 1));
        player.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        ItemStack charm = new ItemStack(net.juli2kapo.factoryascent.ender.EnderContent.RECALL_CHARM.get());
        charm.set(net.juli2kapo.factoryascent.ender.EnderContent.LINKED_BEACON.get(),
                net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(beaconPos)));
        net.juli2kapo.factoryascent.ender.EnderContent.RECALL_CHARM.get().recall(player, charm);
        BlockPos landed = player.blockPosition();
        h.assertTrue(landed.equals(h.absolutePos(beaconPos.above())), "player should stand on the beacon, is at " + landed);
        h.assertFalse(beacon.hasPearl(), "the recall should use up the beacon's pearl");
        player.discard();
        h.succeed();
    }
}

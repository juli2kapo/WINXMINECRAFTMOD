package net.juli2kapo.factoryascent.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Manual's table of contents: chapters (one per age, then topics) and their entries. The text
 * of an entry lives in the lang files ({@code guide.factoryascent.entry.<id>.title / .text},
 * written by tools/features/guide.py); this table says what the entry shows (its items' recipes,
 * a multiblock) and which advancement reveals it (locked entries show as "???").
 *
 * <p>Unlock advancements are the step <i>before</i> the thing the entry teaches (the Blast
 * Furnace's page opens with the coke advancement, not with steel), so the book never hides how to
 * reach the next goal. A GameTest checks that every advancement and item here exists.
 */
public final class GuideEntries {
    private GuideEntries() {}

    public enum Chapter {
        START("book", 0xFF8A6A3A), STONE("quern", 0xFF9A9A9A), BRONZE("bronze_ingot", 0xFFD08A3A),
        ELECTRIC("basic_circuit", 0xFFE0C040), AUTOMATION("advanced_circuit", 0xFF4CB050),
        INDUSTRIAL("titanium_ingot", 0xFF4A78D8), ORBITAL("orbital_targeting_core", 0xFFB060E0),
        QUANTUM("quantum_alloy_ingot", 0xFF2FD5CF), POWER("combustion_generator", 0xFFE0A030),
        FLUIDS("bronze_fluid_pipe", 0xFF3F7FD0), LOGISTICS("bronze_item_pipe", 0xFF8A8A50),
        NUCLEAR("reactor_controller", 0xFF6FCF3F), SPACE("shuttle", 0xFF5060C0), DYSON("dyson_collector", 0xFFF0B020),
        ENDER("ender_anchor", 0xFF8E4FD6), SHIPS("bronze_cog", 0xFF2E8FB0), TRAINS("steam_locomotive", 0xFFB0703A),
        PHONE("factory_phone", 0xFF3FA7B5);

        public final String icon;
        public final int color;

        Chapter(String icon, int color) {
            this.icon = icon;
            this.color = color;
        }

        public String key() {
            return "guide.factoryascent.chapter." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * @param advancement advancement path (factoryascent namespace) that reveals the entry; null = always open
     * @param items       items whose recipes the entry shows (and whose machines' screens link here)
     * @param multiblock  structure id in {@link GuideMultiblocks}, or null
     */
    public record Entry(String id, Chapter chapter, String icon, @Nullable String advancement, List<String> items,
                        @Nullable String multiblock) {
        public String titleKey() {
            return "guide.factoryascent.entry." + id + ".title";
        }

        public String textKey() {
            return "guide.factoryascent.entry." + id + ".text";
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    private static void e(String id, Chapter ch, String icon, @Nullable String adv, String items, @Nullable String mb) {
        Entry entry = new Entry(id, ch, icon, adv, items.isBlank() ? List.of() : List.of(items.split(" ")), mb);
        ENTRIES.add(entry);
        BY_ID.put(id, entry);
    }

    static {
        Chapter S = Chapter.START;
        e("welcome", S, "factory_manual", null, "factory_manual", null);
        e("ages", S, "quern", null, "", null);
        e("multiblocks", S, "coke_oven", null, "", null);
        e("hologram", S, "factory_manual", null, "", null);
        // ---------------------------------------------------------------- Stone
        Chapter ST = Chapter.STONE;
        e("hammer", ST, "forge_hammer", null, "forge_hammer copper_plate", null);
        e("quern", ST, "quern", "stone_hammer", "quern", null);
        e("sieve", ST, "sieve", "stone_hammer", "sieve", null);
        e("drying_rack", ST, "drying_rack", "stone_hammer", "drying_rack", null);
        e("kinetic", ST, "water_wheel", "stone_quern", "water_wheel windmill", null);
        e("tin_kiln", ST, "brick_kiln", "stone_hammer", "brick_kiln fire_clay", null);
        // ---------------------------------------------------------------- Bronze
        Chapter BR = Chapter.BRONZE;
        e("bronze", BR, "bronze_ingot", "stone_kiln", "bronze_ingot bronze_pickaxe", null);
        e("burner_machines", BR, "burner_crusher", "age_bronze", "burner_crusher burner_press plate_mold gear_mold rod_mold wire_mold", null);
        e("coke_oven", BR, "coke_oven", "bronze_press", "coke_oven coke_oven_bricks", "coke_oven");
        e("blast_furnace", BR, "blast_furnace", "bronze_coke", "blast_furnace fire_bricks fire_brick", "blast_furnace");
        e("bronze_gear", BR, "bronze_backpack", "age_bronze", "bronze_backpack grappling_hook bronze_helmet", null);
        // ---------------------------------------------------------------- Electric
        Chapter EL = Chapter.ELECTRIC;
        e("machine_frame", EL, "machine_frame", "age_electric", "machine_frame steel_plate", null);
        e("first_power", EL, "combustion_generator", "electric_frame", "combustion_generator copper_cable energy_cell", null);
        e("circuits", EL, "basic_circuit", "electric_power", "assembler basic_circuit motor", null);
        e("electric_machines", EL, "crusher", "electric_circuit", "electric_furnace crusher metal_press alloy_smelter", null);
        e("electric_tools", EL, "electric_drill", "electric_circuit", "electric_drill charger item_magnet night_vision_goggles", null);
        e("auto_farmer", EL, "auto_farmer", "electric_circuit", "auto_farmer", null);
        e("aluminium_silicon", EL, "silicon", "electric_crusher", "aluminum_ingot silicon silicon_wafer", null);
        e("upgrades", EL, "speed_upgrade", "electric_circuit", "speed_upgrade energy_upgrade", null);
        // ---------------------------------------------------------------- Automation
        Chapter AU = Chapter.AUTOMATION;
        e("advanced_circuit", AU, "advanced_circuit", "electric_silicon", "advanced_circuit", null);
        e("miner", AU, "miner", "age_automation", "miner", null);
        e("ore_washer", AU, "ore_washer", "age_automation", "ore_washer induction_smelter", null);
        e("automation_blocks", AU, "block_breaker", "age_automation", "block_breaker block_placer vacuum_hopper tree_farm", null);
        e("oil_derrick", AU, "oil_derrick", "age_automation", "oil_derrick derrick_base", "oil_derrick");
        e("refinery", AU, "refinery", "automation_oil", "refinery refinery_tower", "refinery");
        e("mobs", AU, "mob_capsule", "age_automation", "mob_capsule mob_farm minimizer_ray maximizer_ray size_chamber mob_releaser", null);
        // ---------------------------------------------------------------- Industrial
        Chapter IN = Chapter.INDUSTRIAL;
        e("titanium", IN, "titanium_ingot", "automation_induction", "titanium_ingot hydraulic_press titanium_plate", null);
        e("industrial_machines", IN, "industrial_grinder", "age_industrial", "industrial_grinder recycler centrifuge industrial_energy_cell", null);
        e("precision", IN, "precision_assembler", "industrial_press", "precision_assembler", null);
        // ---------------------------------------------------------------- Orbital
        Chapter OR = Chapter.ORBITAL;
        e("launch_pad", OR, "launch_controller", "industrial_precision", "launch_controller launch_pad", "launch_pad");
        e("satellites", OR, "survey_satellite", "orbital_pad", "survey_satellite uplink_satellite guardian_satellite ground_station orbital_radar", null);
        e("plasma_forge", OR, "plasma_forge", "age_orbital", "plasma_forge orbital_targeting_core", null);
        // ---------------------------------------------------------------- Quantum
        Chapter QU = Chapter.QUANTUM;
        e("quantum_alloy", QU, "quantum_alloy_ingot", "orbital_forge", "quantum_alloy_ingot quantum_energy_cell", null);
        e("tokamak", QU, "tokamak_core", "age_quantum", "tokamak_core fusion_magnet fusion_casing fusion_port electrolyzer", "tokamak");
        e("fusion_fuel", QU, "deuterium_cell", "age_quantum", "electrolyzer deuterium_cell tritium_cell helium_3_fuel_cell", null);
        // ---------------------------------------------------------------- Power
        Chapter PO = Chapter.POWER;
        e("energy_basics", PO, "copper_cable", null, "copper_cable aluminum_cable titanium_cable superconductor_cable", null);
        e("generators", PO, "combustion_generator", "electric_frame", "combustion_generator solar_panel geothermal_generator magmatic_generator biogas_generator", null);
        e("steam", PO, "steam_engine", "electric_frame", "boiler steam_engine steam_turbine kinetic_dynamo", null);
        e("wind_turbine", PO, "wind_turbine", "electric_power", "wind_turbine turbine_mast", "wind_turbine");
        e("solar_rtg", PO, "solar_array", "age_automation", "solar_array rtg radioisotope_pellet", null);
        e("energy_storage", PO, "energy_cell", "electric_frame", "energy_cell advanced_energy_cell industrial_energy_cell quantum_energy_cell", null);
        // ---------------------------------------------------------------- Fluids
        Chapter FL = Chapter.FLUIDS;
        e("fluid_basics", FL, "bronze_fluid_pipe", "age_bronze", "bronze_fluid_pipe bronze_fluid_tank pump", null);
        e("fluid_tiers", FL, "steel_fluid_tank", "bronze_fluids", "steel_fluid_pipe steel_fluid_tank titanium_fluid_pipe titanium_fluid_tank", null);
        e("oil_products", FL, "diesel_bucket", "automation_oil", "diesel_generator plastic tar asphalt", null);
        // ---------------------------------------------------------------- Logistics
        Chapter LO = Chapter.LOGISTICS;
        e("item_pipes", LO, "bronze_item_pipe", "age_bronze", "bronze_item_pipe steel_item_pipe aluminum_item_pipe titanium_item_pipe wrench", null);
        e("crates", LO, "bronze_crate", "age_bronze", "wooden_crate bronze_crate", null);
        e("storage_network", LO, "storage_terminal", "electric_circuit", "storage_controller storage_drive storage_cell_1k storage_terminal storage_interface storage_cable", null);
        e("wireless", LO, "wireless_terminal", "orbital_pad", "wireless_terminal", null);
        // ---------------------------------------------------------------- Nuclear
        Chapter NU = Chapter.NUCLEAR;
        e("uranium", NU, "raw_uranium", "age_industrial", "uranium_ingot enriched_uranium fuel_rod", null);
        e("fission_reactor", NU, "reactor_controller", "industrial_uranium",
                "reactor_controller reactor_casing reactor_glass reactor_fuel_channel reactor_control_rod reactor_access_port reactor_coolant_port reactor_power_port reactor_redstone_port", "fission_reactor");
        e("reactor_heat", NU, "coolant_cell", "industrial_uranium", "coolant_cell coolant_bucket steam_turbine", null);
        e("radiation", NU, "geiger_counter", "industrial_uranium", "geiger_counter hazmat_helmet hazmat_chestplate waste_barrel", null);
        e("waste", NU, "depleted_fuel_rod", "industrial_fuel_rod", "centrifuge waste_barrel radioisotope_pellet", null);
        // ---------------------------------------------------------------- Space
        Chapter SP = Chapter.SPACE;
        e("rockets", SP, "crew_capsule", "orbital_pad", "crew_capsule rocket_fuel return_pod station_kit", null);
        e("suits_oxygen", SP, "astronaut_helmet", "orbital_pad", "astronaut_helmet astronaut_suit astronaut_leggings astronaut_boots oxygen_compressor oxygen_cell", null);
        e("sealed_rooms", SP, "oxygen_sealer", "space_orbit", "oxygen_sealer air_vent airlock_door", null);
        e("stations", SP, "station_core", "space_orbit", "station_core station_kit cargo_pod docking_port", null);
        e("magnetic_boots", SP, "magnetic_boots", "station_boots", "magnetic_boots", null);
        e("shuttle", SP, "shuttle", "space_orbit", "shuttle fuelling_port ion_drive star_chart", null);
        e("planets", SP, "mars_rock", "orbital_shuttle_orbit", "fuel_synthesizer ascent_module thermal_lining distress_beacon", null);
        e("jetpacks", SP, "electric_jetpack", "age_automation", "electric_jetpack advanced_jetpack jet_suit", null);
        // ---------------------------------------------------------------- Dyson
        Chapter DY = Chapter.DYSON;
        e("dyson_overview", DY, "dyson_collector", "age_quantum", "dyson_collector dyson_monitor", null);
        e("mass_driver", DY, "mass_driver", "age_quantum", "mass_driver mass_driver_rail", "mass_driver");
        e("dyson_receiver", DY, "dyson_receiver", "dyson_first", "dyson_receiver dyson_receiver_array", "dyson_receiver");
        // ---------------------------------------------------------------- Ender / cross-dimension
        Chapter EN = Chapter.ENDER;
        e("ender_anchor", EN, "ender_anchor", "electric_frame", "ender_anchor ender_dust", "ender_anchor");
        e("recall", EN, "recall_charm", "electric_anchor", "ender_beacon recall_charm", null);
        e("links", EN, "ender_link", "electric_anchor", "ender_link quantum_entangler", null);
        // ---------------------------------------------------------------- Ships
        Chapter SH = Chapter.SHIPS;
        e("sailing", SH, "bronze_cog", "age_bronze", "bronze_cog", null);
        e("motor_ship", SH, "motor_ship", "electric_circuit", "motor_ship", null);
        e("orbital_shuttle", SH, "shuttle", "space_orbit", "shuttle docking_port", null);
        // ---------------------------------------------------------------- Trains
        Chapter TR = Chapter.TRAINS;
        e("steam_locomotive", TR, "steam_locomotive", "age_bronze", "steam_locomotive", null);
        e("wagons", TR, "cargo_wagon", "bronze_steam_locomotive", "coupler passenger_car cargo_wagon tank_wagon hopper_wagon", null);
        e("train_station", TR, "train_station", "bronze_coupler", "train_station", null);
        e("diesel_locomotive", TR, "diesel_locomotive", "automation_diesel", "diesel_locomotive", null);
        // ---------------------------------------------------------------- Phone
        Chapter PH = Chapter.PHONE;
        e("phone", PH, "factory_phone", "age_automation", "factory_phone link_card phone_dock", null);
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static @Nullable Entry get(String id) {
        return BY_ID.get(id);
    }

    public static List<Entry> of(Chapter chapter) {
        return ENTRIES.stream().filter(e -> e.chapter() == chapter).toList();
    }

    /** "book" → minecraft:book, "x" → factoryascent:x, "ns:x" as is. */
    public static Identifier itemId(String id) {
        if (id.equals("book")) return Identifier.withDefaultNamespace("book");
        return id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, id);
    }

    public static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(itemId(id)).orElse(Items.AIR);
    }

    /** The first entry that lists this item (machine screens and item links open it), or null. */
    public static @Nullable Entry forItem(Item item) {
        for (Entry e : ENTRIES) {
            for (String id : e.items()) if (item(id) == item) return e;
        }
        for (Entry e : ENTRIES) if (item(e.icon()) == item) return e;
        return null;
    }
}

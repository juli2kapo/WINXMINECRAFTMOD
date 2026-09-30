#!/usr/bin/env python3
"""The power ladder (Java: power/, nuclear/, fusion/): models, blockstates, item models, loot, recipes,
worldgen, tags, sounds, the radiation damage type, the Hazmat Suit's equipment asset, advancements
and lang (English + Spanish).

  Bronze      Kinetic Dynamo           bronze frame, copper field coils; its armature (block/kinetic_dynamo_armature)
                                       is turned by power/client/SpinRenderer when a wheel or windmill drives it
  Electric    Steam Engine             brick firebox, riveted copper boiler, chimney, piston; flywheel model spun
              Wind Turbine + Mast      white nacelle on a slender tower; three-blade rotor (1/3 scale model, x3)
  Automation  Biogas Generator         green digester tank with a dome, gas engine and a flare stack
              Magmatic Generator       dark casing, lava window, heat-sink fins, intake funnel
              Advanced Solar Array     a big tilted panel on a post
  Industrial  Centrifuge (machine)     four tall centrifuge tubes over a control cabinet
              uranium / lead ores, reactor casing, glass, fuel channel, control rod, controller, 4 ports,
              Waste Barrel, corium
  Orbital     RTG                      finned radioisotope generator
              Electrolyzer (machine)   glass cell with electrodes and gas domes
  Quantum     Tokamak Core, Fusion Magnet, Fusion Casing, Fusion Port; plasma ring models (1/4 scale, x4)

Models use the element DSL of satellites.py. Textures: power_textures.py.
Run `python3 tools/features/power.py --preview` to render tools/power_preview.png (models + textures).
"""
import importlib.util
import json
import math
import sys
from pathlib import Path

MOD = "factoryascent"
HERE = Path(__file__).resolve().parent


def _load(name):
    key = f"power_dep_{name}"
    if key in sys.modules:
        return sys.modules[key]
    spec = importlib.util.spec_from_file_location(key, HERE / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[key] = mod
    spec.loader.exec_module(mod)
    return mod


S = _load("satellites")
cube, ring, disc, tube, spin, turn, euler, El = S.cube, S.ring, S.disc, S.tube, S.spin, S.turn, S.euler, S.El

GENERATORS = ["kinetic_dynamo", "steam_engine", "wind_turbine", "biogas_generator", "magmatic_generator", "solar_array", "rtg"]
REACTOR = ["reactor_casing", "reactor_glass", "reactor_fuel_channel", "reactor_control_rod", "reactor_controller",
           "reactor_access_port", "reactor_power_port", "reactor_coolant_port", "reactor_redstone_port", "waste_barrel"]
FUSION = ["fusion_casing", "fusion_magnet", "fusion_port", "tokamak_core"]
ORES = ["lead_ore", "deepslate_lead_ore", "deepslate_uranium_ore"]
PICKAXE_BLOCKS = GENERATORS + ["turbine_mast"] + REACTOR + FUSION + ORES + ["corium"]
MATERIALS = ["raw_lead", "lead_dust", "lead_ingot", "lead_plate", "raw_uranium", "uranium_dust", "uranium_ingot",
             "enriched_uranium", "depleted_uranium", "fuel_rod", "depleted_fuel_rod", "nuclear_waste", "radioisotope_pellet",
             "empty_cell", "coolant_cell", "deuterium_cell", "tritium_cell"]
# Not recipes, for tools/check_progression.py: a fission reactor turns fuel rods into spent ones.
PROGRESSION = [("reactor burn-up", ["depleted_fuel_rod"], ["fuel_rod"],
                ["reactor_controller", "reactor_casing", "reactor_fuel_channel"])]
TOOLS = ["geiger_counter", "hazmat_helmet", "hazmat_chestplate", "hazmat_leggings", "hazmat_boots"]


def t(name):
    return f"{MOD}:block/{name}"


def planets_provide_helium3():
    """The Moon (planets content) is helium-3's home; we only stand in for it when it isn't there."""
    p = HERE / "planets.py"
    return p.exists() and '"helium_3"' in p.read_text()


def emissive(els):
    for e in els:
        e.emissive = True
    return els


def model(els, tex, particle=None, ao=True):
    out = {"parent": "minecraft:block/block", **S.model(els, tex, particle=particle)}
    if not ao:
        out["ambientocclusion"] = False
    return out


def shift(els, dx, dy, dz):
    return [e.moved(dx, dy, dz) for e in els]


def scaled(els, k, center=(8, 8, 8)):
    """Elements scaled by k about center (used to put 1/3- and 1/4-scale rotor parts into item models)."""
    out = []
    for e in els:
        f = [center[i] + (e.frm[i] - center[i]) * k for i in range(3)]
        to = [center[i] + (e.to[i] - center[i]) * k for i in range(3)]
        o = [center[i] + (e.origin[i] - center[i]) * k for i in range(3)]
        out.append(El(f, to, e.faces, e.m, o, e.emissive))
    return out


def z_axis(els, pivot):
    """Turns elements built around the vertical axis so that axis runs north-south (through pivot)."""
    return turn(els, euler(x=90), pivot)


def x_axis(els, pivot):
    return turn(els, euler(z=90), pivot)


# ============================================================ generators

DYN_TEX = {"housing": t("dynamo_housing"), "coil": t("dynamo_coil"), "shaft": t("dynamo_shaft"), "top": t("dynamo_top"),
           "terminal": t("dynamo_terminal"), "arm": t("dynamo_armature")}


def kinetic_dynamo():
    els = [cube([1, 0, 1], [15, 3, 15], {**{d: "housing" for d in S.DIRS}, "up": "top"})]
    for x0 in (2, 11):  # field poles, wound with copper
        els.append(cube([x0, 3, 4], [x0 + 3, 14, 12], "coil"))
        els.append(cube([x0 - 0.5, 13.5, 3.5], [x0 + 3.5, 15, 12.5], "housing"))
    els.append(cube([5, 3, 5], [11, 7.5, 11], {**{d: "housing" for d in S.DIRS}, "north": "terminal"}))
    # shafts out of all four sides, each with a coupling flange (a wheel or windmill can sit on any side)
    els += [cube([0, 4.5, 6.5], [5, 7.5, 9.5], "shaft"), cube([11, 4.5, 6.5], [16, 7.5, 9.5], "shaft"),
            cube([6.5, 4.5, 0], [9.5, 7.5, 5], "shaft"), cube([6.5, 4.5, 11], [9.5, 7.5, 16], "shaft")]
    els += [cube([0, 3.5, 5.5], [1, 8.5, 10.5], "housing"), cube([15, 3.5, 5.5], [16, 8.5, 10.5], "housing"),
            cube([5.5, 3.5, 0], [10.5, 8.5, 1], "housing"), cube([5.5, 3.5, 15], [10.5, 8.5, 16], "housing")]
    return els


def dynamo_armature():
    """Centred at (8, 8, 8); the renderer puts that at block (8, 11, 8) and turns it about y."""
    els = tube(8, 8, 5, 11, 2.6, "arm", n=8)
    els += disc(8, 8, 11.4, 2.7, "shaft")
    els += ring(8, 8, 4, 5, 1.8, 1.8, "coil", th=0.5)
    els.append(cube([7.5, 3, 7.5], [8.5, 13, 8.5], "shaft"))
    return els


STEAM_TEX = {"firebox": t("steam_firebox"), "brick": t("steam_firebox"), "boiler": t("steam_boiler"), "end": t("steam_boiler_end"),
             "brass": t("steam_brass"), "iron": t("steam_iron"), "chimney": t("steam_chimney"), "fly": t("steam_flywheel")}


def steam_engine():
    els = [cube([1, 0, 1], [15, 6, 15], {**{d: "brick" for d in S.DIRS}, "up": "iron", "down": "iron"})]
    # the fire door (north) gets the firebox texture with its glowing door
    els.append(cube([4, 0.5, 0.5], [12, 5.5, 1], {"north": "firebox"}, u=4, v=5))
    # boiler: a riveted copper drum lying north-south
    boiler = tube(7, 8.5, 2.5, 15.5, 4.6, "boiler")
    boiler += disc(7, 8.5, 2.9, 4.4, "end", up=False)
    boiler += disc(7, 8.5, 15.5, 4.4, "iron", down=False)
    els += z_axis(boiler, [7, 10.5, 8.5])
    els += tube(4.5, 12.5, 13, 22, 1.3, "chimney")
    els += ring(4.5, 12.5, 22, 23, 1.9, 1.9, "iron", th=0.5)
    els += tube(8.5, 7, 14.5, 17, 1.6, "brass")  # steam dome
    els += disc(8.5, 7, 17.4, 1.7, "brass")
    # piston: cylinder along the east side, rod out towards the flywheel crank
    els.append(cube([12.5, 7, 3], [15.5, 10.5, 10], "brass"))
    els.append(cube([12, 6.5, 2.5], [16, 11, 3.5], "iron"))
    els.append(cube([13.6, 8.2, 10], [14.4, 9, 13.5], "iron"))
    els.append(cube([15, 4, 7], [16, 12, 11], "iron"))  # flywheel bearing
    return els


def steam_flywheel():
    """Centred at (8, 8, 8), turning about x (the renderer puts it at block (17, 8, 9))."""
    rim = ring(8, 8, 7.2, 8.8, 6.6, 6.6, "fly", n=12, th=1.2)
    for k in range(4):
        rim += spin([cube([7.7, 7.4, 2], [8.3, 8.6, 14], "iron")], k * 45, [8, 8, 8])
    rim.append(cube([6.8, 6.8, 6.8], [9.2, 9.2, 9.2], "brass"))
    els = x_axis(rim, [8, 8, 8])
    els.append(cube([5.8, 11, 7.4], [7.2, 13, 8.6], "iron"))  # crank pin
    return els


WIND_TEX = {"nacelle": t("turbine_nacelle"), "detail": t("turbine_detail"), "blade": t("turbine_blade"),
            "hub": t("turbine_hub"), "mast": t("turbine_mast"), "mast_top": t("turbine_mast_top")}


def wind_turbine():
    els = tube(8, 8, 0, 2.5, 3.2, "detail")
    els += disc(8, 8, 0.4, 3.6, "mast_top", up=False)
    els.append(cube([4.5, 2.5, 2.5], [11.5, 9.5, 15.5], "nacelle"))
    els.append(cube([5, 9.5, 3.5], [11, 10.5, 14.5], "nacelle"))
    els.append(cube([5.5, 3.5, 1.5], [10.5, 8.5, 2.5], "detail"))
    els.append(cube([5, 3.5, 15.5], [11, 8.5, 16], "detail"))
    # anemometer mast and cups on top at the back
    els.append(cube([7.7, 10.5, 12], [8.3, 14, 12.6], "detail"))
    for k in range(3):
        els += spin([cube([7.7, 13.2, 12.3], [8.3, 13.8, 14.5], "detail"), cube([7.3, 12.9, 14.3], [8.7, 14.1, 15.1], "detail")],
                    k * 120, [8, 13.5, 12.3])
    return els


def wind_rotor():
    """1/3 scale, centred at (8, 8, 8) and turning about z; the renderer scales it x3 in front of the nacelle."""
    els = [cube([6.3, 6.3, 6.5], [9.7, 9.7, 9.5], "hub"), cube([6.8, 6.8, 5.5], [9.2, 9.2, 6.5], "hub"),
           cube([7.3, 7.3, 4.8], [8.7, 8.7, 5.5], "hub")]
    blade = [cube([6.9, 9.3, 7.6], [9.1, 14.5, 8.2], "blade", v=4), cube([7.2, 14.5, 7.65], [8.8, 20, 8.15], "blade")]
    blade = spin(blade, 14, [8, 14, 7.9], axis="y")  # blade pitch
    for k in range(3):
        els += spin(blade, k * 120, [8, 8, 8], axis="z")
    return els


def turbine_mast():
    els = tube(8, 8, 0, 16, 3.0, "mast")
    els += ring(8, 8, 0, 1.2, 4.0, 4.0, "mast_top", th=0.8)
    els += disc(8, 8, 16, 3.0, "mast_top")
    return els


BIO_TEX = {"tank": t("biogas_tank"), "dome": t("biogas_dome"), "engine": t("biogas_engine"), "steel": t("turbine_detail"),
           "flame": t("biogas_flame"), "pipe": t("steam_iron")}


def biogas_generator(on):
    els = tube(8, 9, 0, 9, 6.2, "tank")
    els += ring(8, 9, 9, 13, 6.2, 2.4, "dome", th=0.7)
    els += disc(8, 9, 13, 2.4, "dome")
    els.append(cube([2, 0, 0.5], [9, 6, 4], {**{d: "steel" for d in S.DIRS}, "north": "engine"}))
    # gas line from the dome to the flare stack in the corner
    els.append(cube([7.5, 12.5, 7.5], [13, 13.5, 8.5], "pipe"))
    els += tube(13.5, 8, 0, 16, 0.9, "pipe")
    els += ring(13.5, 8, 15, 16.5, 1.2, 1.4, "steel", th=0.4)
    if on:
        els += emissive([cube([12.7, 16.5, 7.2], [14.3, 20, 8.8], {d: "flame" for d in ("north", "south", "east", "west")})])
    return els


MAG_TEX = {"casing": t("magmatic_casing"), "front": t("magmatic_front"), "fins": t("magmatic_fins"), "top": t("magmatic_top")}


def magmatic_generator(on):
    tex = {"front": "front_on" if on else "front", "top": "top_on" if on else "top"}
    els = [cube([1, 0, 1], [15, 16, 15], {**{d: "casing" for d in S.DIRS}, "north": tex["front"], "up": tex["top"]})]
    els += [cube([3, 12, 0], [13, 13, 1], "casing"), cube([3, 3, 0], [13, 4, 1], "casing"),
            cube([3, 4, 0], [4, 12, 1], "casing"), cube([12, 4, 0], [13, 12, 1], "casing")]
    for i in range(5):  # heat-sink fins on both sides
        y = 2 + i * 2.6
        els.append(cube([0, y, 2], [1, y + 1, 14], "fins"))
        els.append(cube([15, y, 2], [16, y + 1, 14], "fins"))
    els += ring(8, 8, 16, 17.5, 4.2, 5.2, "casing", th=0.7)
    return els


SOLAR_TEX = {"cells": t("solar_array_cells"), "back": t("solar_array_back"), "frame": t("solar_array_frame"), "post": t("steam_iron")}


def solar_array():
    els = [cube([4, 0, 4], [12, 1, 12], "frame"), cube([6.5, 1, 6.5], [9.5, 8, 9.5], "post"), cube([5, 7, 7], [11, 9, 9], "frame")]
    panel = [cube([0, 8.6, 0.5], [16, 9.6, 15.5], {"up": "cells", "down": "back", "north": "frame", "south": "frame",
                                                    "east": "frame", "west": "frame"})]
    panel += [cube([-0.2, 9.6, 0.3], [16.2, 10.1, 0.9], "frame"), cube([-0.2, 9.6, 15.1], [16.2, 10.1, 15.7], "frame")]
    els += turn(panel, euler(x=-22.5), [8, 9, 8])
    return els


RTG_TEX = {"body": t("rtg_body"), "fin": t("rtg_fin"), "top": t("rtg_top"), "core": t("rtg_core")}


def rtg():
    els = tube(8, 8, 0.5, 14, 3.4, "body")
    els += disc(8, 8, 15, 3.0, "top")
    els += ring(8, 8, 14, 15, 3.4, 3.0, "body", th=0.6)
    els += ring(8, 8, 0, 1.5, 4.4, 4.4, "fin", th=0.8)
    for k in range(8):
        els += spin([cube([11, 2, 7.6], [16 if k % 2 == 0 else 14.5, 13, 8.4], "fin")], k * 45, [8, 8, 8])
    els += emissive(ring(8, 8, 11.5, 12.5, 3.55, 3.55, "core", th=0.3))
    return els


# ============================================================ machines (MachineType: centrifuge, electrolyzer)

CEN_TEX = {"panel": t("centrifuge_panel"), "tube": t("centrifuge_tube"), "top": t("centrifuge_top"),
           "side": t("titanium_machine_side"), "steel": t("turbine_detail")}


def centrifuge(on):
    els = [cube([0, 0, 0], [16, 6, 16], {**{d: "side" for d in S.DIRS}, "north": "panel_on" if on else "panel", "up": "top"})]
    for cx, cz in ((4.5, 4.5), (11.5, 4.5), (4.5, 11.5), (11.5, 11.5)):
        els += tube(cx, cz, 6, 15, 2.3, "tube_on" if on else "tube")
        els += disc(cx, cz, 15.8, 2.5, "top")
        els += ring(cx, cz, 15, 15.8, 2.5, 2.5, "steel", th=0.5)
    els.append(cube([4, 15.9, 7.5], [12, 16.6, 8.5], "steel"))  # cascade header
    els.append(cube([7.5, 15.9, 4], [8.5, 16.6, 12], "steel"))
    return els


ELY_TEX = {"front": t("electrolyzer_front"), "tank": t("electrolyzer_tank"), "electrode": t("electrolyzer_electrode"),
           "h2": t("electrolyzer_dome_h"), "o2": t("electrolyzer_dome_o"), "side": t("orbital_machine_side"),
           "pipe": t("steam_iron")}


def electrolyzer(on):
    els = [cube([0, 0, 0], [16, 5, 16], {**{d: "side" for d in S.DIRS}, "north": "front_on" if on else "front"})]
    els.append(cube([2, 5, 3], [14, 12.5, 13], "tank"))
    for x in (5, 10):
        els.append(cube([x, 5.5, 7.4], [x + 1, 13.5, 8.6], "electrode_on" if on else "electrode"))
    for cx, gas in ((5.5, "h2"), (10.5, "o2")):
        els += tube(cx, 8, 12.5, 14.5, 1.9, gas)
        els += ring(cx, 8, 14.5, 15.8, 1.9, 0.8, gas, th=0.5)
        els.append(cube([cx - 0.4, 14.5, 8], [cx + 0.4, 15.3, 15.5], "pipe"))
    els.append(cube([4, 13.5, 14.5], [12, 16, 15.5], "pipe"))
    return els


# ============================================================ fission

REA_TEX = {"casing": t("reactor_casing"), "channel": t("reactor_fuel_channel"), "channel_top": t("reactor_fuel_channel_top"),
           "glow": t("reactor_cherenkov"), "rod": t("reactor_control_rod"), "head": t("reactor_control_head"),
           "barrel": t("waste_barrel_side"), "barrel_top": t("waste_barrel_top")}


def fuel_channel():
    els = emissive(tube(8, 8, 0, 16, 3.2, "glow"))
    els += ring(8, 8, 0, 16, 5.6, 5.6, "channel", sides=[0, 2, 4, 6], th=1.2)
    for y0 in (0, 7.5, 15):
        els += ring(8, 8, y0, y0 + 1, 6, 6, "channel", th=1.0)
    els += disc(8, 8, 16, 3.3, "channel_top")
    els += disc(8, 8, 0.4, 3.3, "channel_top", up=False)
    return els


def control_rod():
    els = tube(8, 8, 0, 13, 2.4, "rod")
    els.append(cube([3, 13, 3], [13, 16, 13], "head"))
    els.append(cube([6, 16, 6], [10, 16.8, 10], "head"))
    for k in range(4):  # guide lugs
        els += spin([cube([7.4, 1, 10], [8.6, 12, 11.2], "rod")], k * 90, [8, 8, 8])
    return els


def controller(on):
    front = "front_on" if on else "front"
    els = [cube([0, 0, 0], [16, 16, 16], {**{d: "casing" for d in S.DIRS}, "north": front})]
    els.append(cube([1.5, 14, -1.2], [14.5, 15, 0], "casing"))            # screen hood
    els.append(cube([9.2, 3.2, -1], [12.8, 6.8, 0], "red"))                # SCRAM button
    els.append(cube([2.5, 3.5, -0.6], [6.5, 6.5, 0], "casing"))           # key switch plate
    return els


PORT_DETAIL = {"access": "reactor_port_access_detail", "power": "reactor_port_power_detail",
               "coolant": "reactor_port_coolant_detail", "redstone": "reactor_port_redstone_detail"}


def port(kind):
    els = [cube([0, 0, 0], [16, 16, 16], {**{d: "casing" for d in S.DIRS}, "north": "face"})]
    if kind == "access":
        els += [cube([3.5, 3.5, -0.5], [12.5, 4.5, 0], "detail"), cube([3.5, 11.5, -0.5], [12.5, 12.5, 0], "detail"),
                cube([6, 7, -1.5], [10, 8.5, 0], "detail"), cube([6, 7, -1.5], [7, 8.5, -0.5], "detail")]
    elif kind == "power":
        for x in (4, 7, 10):
            els.append(cube([x, 5, -2], [x + 2, 11, 0], "detail"))
            els.append(cube([x - 0.3, 10.2, -2.3], [x + 2.3, 11.2, -1.5], "detail"))
    elif kind == "coolant":
        flange = ring(8, 8, -3, 0, 4.2, 4.2, "detail", inner="detail", th=1.1)
        flange += ring(8, 8, -3.6, -3, 5.2, 5.2, "detail", th=1.8)
        els += turn(flange, euler(x=90), [8, 8, 8])  # the flange's axis now runs out of the north face
    else:
        els.append(cube([5, 5, -1.2], [11, 11, 0], "detail"))
        els.append(cube([6.5, 6.5, -1.8], [9.5, 9.5, -1.2], "detail"))
    return els


def waste_barrel():
    els = tube(8, 8, 0, 15, 6, "barrel")
    els += disc(8, 8, 15, 6, "barrel_top")
    els += disc(8, 8, 0.4, 6, "barrel_top", up=False)
    for y in (3.5, 11):
        els += ring(8, 8, y, y + 1, 6.35, 6.35, "barrel_top", th=0.5)
    els += ring(8, 8, 15, 15.8, 6.1, 6.1, "barrel_top", th=0.5)
    return els


def corium():
    els = emissive([cube([0, 0, 0], [16, 16, 16], "corium")])
    els += emissive([cube([2.5, 16, 3], [8, 17.3, 8.5], "corium"), cube([9, 16, 8], [13.5, 17, 12.5], "corium"),
                     cube([6, 16, 10], [8.5, 16.6, 13], "corium")])
    return els


# ============================================================ fusion

FUS_TEX = {"casing": t("fusion_casing"), "coil": t("fusion_magnet_coil"), "core": t("fusion_magnet_core"), "port": t("fusion_port"),
           "sol": t("tokamak_core"), "sol_top": t("tokamak_core_top")}


def fusion_magnet():
    els = [cube([1, 0, 1], [15, 16, 15], "core")]
    for y in (1.5, 6.5, 11.5):
        els.append(cube([0, y, 0], [16, y + 3, 16], {**{d: "coil" for d in ("north", "south", "east", "west")},
                                                     "up": "core", "down": "core"}))
    return els


def fusion_port():
    els = [cube([0, 0, 0], [16, 16, 16], {**{d: "casing" for d in S.DIRS}, "north": "port"})]
    coupler = ring(8, 8, -1.5, 0, 3.8, 3.8, "core", n=6, th=1.2, phase=0)
    els += turn(coupler, euler(x=90), [8, 8, 8])
    return els


def tokamak_core(on):
    side, top = ("sol_on", "sol_top_on") if on else ("sol", "sol_top")
    els = tube(8, 8, 0, 16, 6, side)
    els += disc(8, 8, 16, 6, top)
    els += disc(8, 8, 0.4, 6, top, up=False)
    for y in (0, 15):
        els.append(cube([1, y, 1], [15, y + 1, 15], {**{d: "core" for d in S.DIRS}}))
    if on:
        els += emissive(ring(8, 8, 7.5, 8.5, 6.2, 6.2, side, th=0.3))
    return els


def plasma_path(n=48, r=8.0, p=6):
    """Points of a rounded square (superellipse |x|^p + |z|^p = r^p) around (8, 8): the channel two
    blocks from the core at a quarter scale, rounding the corners so the plasma stays in the air."""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        c, s = math.cos(a), math.sin(a)
        rr = r / (abs(c) ** p + abs(s) ** p) ** (1 / p)
        pts.append((8 + rr * c, 8 + rr * s))
    return pts


def plasma_ring(tex, half, lift=0.0):
    els = []
    pts = plasma_path()
    for i, (x0, z0) in enumerate(pts):
        x1, z1 = pts[(i + 1) % len(pts)]
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        length = math.hypot(x1 - x0, z1 - z0) + 0.15
        e = cube([cx - length / 2, 8 - half + lift, cz - half], [cx + length / 2, 8 + half + lift, cz + half],
                 {d: tex for d in ("north", "south", "up", "down")}, u=i % 8)
        ang = -math.degrees(math.atan2(z1 - z0, x1 - x0))
        els += spin([e], ang, [cx, 8, cz])
    return emissive(els)


# ============================================================ writing

SIDES = ("north", "east", "south", "west")
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
FACING6 = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def facing_active_states(ctx, name, on_model=True):
    ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": {
        f"active={str(a).lower()},facing={f}": {"model": f"{MOD}:block/{name}{'_on' if a and on_model else ''}",
                                               **({"y": ROT[f]} if ROT[f] else {})}
        for f in SIDES for a in (False, True)}})


def item_model(ctx, name, parent, extra=None):
    ctx.write(ctx.ASSETS / "models" / "item" / f"{name}.json", {"parent": f"{MOD}:{parent}", **(extra or {})})
    ctx.item_def(name, f"item/{name}")


def blocks(ctx):
    bm = ctx.block_model
    # ---- generators
    dyn_on = {**DYN_TEX, "coil": t("dynamo_coil_on"), "terminal": t("dynamo_terminal_on")}
    bm("kinetic_dynamo", model(kinetic_dynamo(), DYN_TEX, particle=t("dynamo_housing")))
    bm("kinetic_dynamo_on", model(kinetic_dynamo(), dyn_on, particle=t("dynamo_housing")))
    bm("kinetic_dynamo_armature", model(dynamo_armature(), DYN_TEX))
    bm("kinetic_dynamo_item", model(kinetic_dynamo() + shift(dynamo_armature(), 0, 3, 0), DYN_TEX, particle=t("dynamo_housing")))
    steam_on = {**STEAM_TEX, "firebox": t("steam_firebox_on")}
    bm("steam_engine", model(steam_engine(), STEAM_TEX, particle=t("steam_boiler")))
    bm("steam_engine_on", model(steam_engine(), steam_on, particle=t("steam_boiler")))
    bm("steam_engine_flywheel", model(steam_flywheel(), STEAM_TEX))
    bm("steam_engine_item", model(steam_engine() + shift(steam_flywheel(), 9, 0, 1), STEAM_TEX, particle=t("steam_boiler")))
    bm("wind_turbine", model(wind_turbine(), WIND_TEX, particle=t("turbine_nacelle")))
    bm("wind_turbine_rotor", model(wind_rotor(), WIND_TEX, particle=t("turbine_blade")))
    bm("wind_turbine_item", model(wind_turbine() + shift(scaled(wind_rotor(), 1.2), 0, -2, -9.5), WIND_TEX,
                                  particle=t("turbine_nacelle")))
    bm("turbine_mast", model(turbine_mast(), WIND_TEX, particle=t("turbine_mast")))
    bm("biogas_generator", model(biogas_generator(False), BIO_TEX, particle=t("biogas_tank")))
    bm("biogas_generator_on", model(biogas_generator(True), {**BIO_TEX, "engine": t("biogas_engine_on")}, particle=t("biogas_tank")))
    mag_on = {**MAG_TEX, "front_on": t("magmatic_front_on"), "top_on": t("magmatic_top_on")}
    bm("magmatic_generator", model(magmatic_generator(False), MAG_TEX, particle=t("magmatic_casing")))
    bm("magmatic_generator_on", model(magmatic_generator(True), mag_on, particle=t("magmatic_casing")))
    bm("solar_array", model(solar_array(), SOLAR_TEX, particle=t("solar_array_cells")))
    bm("rtg", model(rtg(), RTG_TEX, particle=t("rtg_body")))
    for g in GENERATORS:
        on_model = g in ("kinetic_dynamo", "steam_engine", "biogas_generator", "magmatic_generator")
        facing_active_states(ctx, g, on_model)
        item_model(ctx, g, f"block/{g}_item" if g in ("kinetic_dynamo", "steam_engine", "wind_turbine") else f"block/{g}")
    ctx.write(ctx.ASSETS / "blockstates" / "turbine_mast.json", {"variants": {"": {"model": f"{MOD}:block/turbine_mast"}}})
    item_model(ctx, "turbine_mast", "block/turbine_mast")
    # ---- machines (their blockstates, loot and names come from gen_resources.py's MACHINES table)
    cen = {**CEN_TEX, "panel_on": t("centrifuge_panel_on"), "tube_on": t("centrifuge_tube_on")}
    bm("centrifuge", model(centrifuge(False), cen, particle=t("centrifuge_top")))
    bm("centrifuge_on", model(centrifuge(True), cen, particle=t("centrifuge_top")))
    ely = {**ELY_TEX, "front_on": t("electrolyzer_front_on"), "electrode_on": t("electrolyzer_electrode_on")}
    bm("electrolyzer", model(electrolyzer(False), ely, particle=t("electrolyzer_front")))
    bm("electrolyzer_on", model(electrolyzer(True), ely, particle=t("electrolyzer_front")))
    # ---- ores and simple cubes
    for name in ORES + ["reactor_casing", "fusion_casing"]:
        bm(name, {"parent": "minecraft:block/cube_all", "textures": {"all": t(name)}})
    bm("reactor_glass", {"parent": "minecraft:block/cube_all", "textures": {"all": t("reactor_glass")}})
    for name in ORES + ["reactor_casing", "reactor_glass", "fusion_casing"]:
        ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        item_model(ctx, name, f"block/{name}")
    # ---- reactor parts
    bm("reactor_fuel_channel", model(fuel_channel(), REA_TEX, particle=t("reactor_fuel_channel")))
    bm("reactor_control_rod", model(control_rod(), REA_TEX, particle=t("reactor_control_rod")))
    bm("waste_barrel", model(waste_barrel(), REA_TEX, particle=t("waste_barrel_side")))
    bm("corium", model(corium(), {"corium": t("corium")}, particle=t("corium")))
    for name in ("reactor_fuel_channel", "reactor_control_rod", "waste_barrel", "corium"):
        ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        item_model(ctx, name, f"block/{name}")
    ctl = {"casing": t("reactor_casing"), "front": t("reactor_controller_front"), "front_on": t("reactor_controller_front_on"),
           "red": t("reactor_port_redstone_detail")}
    bm("reactor_controller", model(controller(False), ctl, particle=t("reactor_casing")))
    bm("reactor_controller_on", model(controller(True), ctl, particle=t("reactor_casing")))
    facing_active_states(ctx, "reactor_controller")
    item_model(ctx, "reactor_controller", "block/reactor_controller")
    for kind in ("access", "power", "coolant", "redstone"):
        name = f"reactor_{kind}_port"
        bm(name, model(port(kind), {"casing": t("reactor_casing"), "face": t(f"reactor_port_{kind}"), "detail": t(PORT_DETAIL[kind])},
                       particle=t("reactor_casing")))
        ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": {
            f"facing={f}": {"model": f"{MOD}:block/{name}", **r} for f, r in FACING6.items()}})
        item_model(ctx, name, f"block/{name}")
    # ---- fusion
    bm("fusion_magnet", model(fusion_magnet(), FUS_TEX, particle=t("fusion_magnet_coil")))
    bm("fusion_port", model(fusion_port(), FUS_TEX, particle=t("fusion_casing")))
    fus_on = {**FUS_TEX, "sol_on": t("tokamak_core_on"), "sol_top_on": t("tokamak_core_top_on")}
    bm("tokamak_core", model(tokamak_core(False), FUS_TEX, particle=t("tokamak_core")))
    bm("tokamak_core_on", model(tokamak_core(True), fus_on, particle=t("tokamak_core")))
    ctx.write(ctx.ASSETS / "blockstates" / "fusion_magnet.json", {"variants": {"": {"model": f"{MOD}:block/fusion_magnet"}}})
    ctx.write(ctx.ASSETS / "blockstates" / "fusion_port.json", {"variants": {
        f"facing={f}": {"model": f"{MOD}:block/fusion_port", **r} for f, r in FACING6.items()}})
    ctx.write(ctx.ASSETS / "blockstates" / "tokamak_core.json", {"variants": {
        "active=false": {"model": f"{MOD}:block/tokamak_core"}, "active=true": {"model": f"{MOD}:block/tokamak_core_on"}}})
    for name in FUSION[1:]:
        item_model(ctx, name, f"block/{name}")
    bm("plasma_ring", model(plasma_ring("plasma", 1.2), {"plasma": t("plasma")}, ao=False))
    bm("plasma_filament", model(plasma_ring("filament", 1.55), {"filament": t("plasma_filament")}, ao=False))
    # ---- loot
    for name in PICKAXE_BLOCKS:
        if name == "waste_barrel":
            ctx.loot_self(name, True)
        elif name in ("lead_ore", "deepslate_lead_ore", "deepslate_uranium_ore", "corium"):
            continue
        else:
            ctx.loot_self(name)
    ore_loot(ctx, "lead_ore", "raw_lead")
    ore_loot(ctx, "deepslate_lead_ore", "raw_lead")
    ore_loot(ctx, "deepslate_uranium_ore", "raw_uranium")
    ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / "corium.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:nuclear_waste",
                                                                        "functions": [{"function": "minecraft:set_count",
                                                                                       "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}]}]}],
        "random_sequence": f"{MOD}:blocks/corium"})


def ore_loot(ctx, name, raw):
    ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{MOD}:{name}", "conditions": [{
                "condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": f"{MOD}:{raw}", "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{MOD}:blocks/{name}"})


def items(ctx):
    names = MATERIALS + TOOLS
    if not planets_provide_helium3():
        names.append("helium_3")
    for name in names:
        ctx.flat_item(name, handheld=name == "geiger_counter")
    ctx.write(ctx.ASSETS / "equipment" / "hazmat.json", {"layers": {
        "humanoid": [{"texture": f"{MOD}:hazmat"}], "humanoid_leggings": [{"texture": f"{MOD}:hazmat"}]}})


# ============================================================ data

def worldgen(ctx):
    wg = ctx.DATA / MOD / "worldgen"

    def ore(name, targets, size, count, lo, hi, shape, air_discard=0.0):
        ctx.write(wg / "configured_feature" / f"ore_{name}.json", {"type": "minecraft:ore", "config": {
            "discard_chance_on_air_exposure": air_discard, "size": size, "targets": [
                {"state": {"Name": f"{MOD}:{block}"}, "target": {"predicate_type": "minecraft:tag_match",
                                                                "tag": f"minecraft:{repl}_ore_replaceables"}}
                for block, repl in targets]}})
        ctx.write(wg / "placed_feature" / f"ore_{name}.json", {"feature": f"{MOD}:ore_{name}", "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": f"minecraft:{shape}",
                                                           "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:biome"}]})
    ore("lead", [("lead_ore", "stone"), ("deepslate_lead_ore", "deepslate")], 8, 6, -40, 64, "trapezoid")
    # Rare and deep, like diamonds: small veins that half the time vanish where they touch air.
    ore("uranium", [("deepslate_uranium_ore", "deepslate")], 4, 1, -64, -20, "trapezoid", air_discard=0.5)
    ctx.write(ctx.DATA / MOD / "neoforge" / "biome_modifier" / "power_ores.json", {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": [f"{MOD}:ore_lead", f"{MOD}:ore_uranium"], "step": "underground_ores"})


def tags(ctx):
    T = ctx.add_tag
    T("minecraft", "block", "needs_iron_tool", ["lead_ore", "deepslate_lead_ore", "deepslate_uranium_ore"])
    T("minecraft", "block", "needs_diamond_tool", ["corium"])
    for kind in ("block", "item"):
        T("c", kind, "ores", ["#c:ores/lead", "#c:ores/uranium"])
        T("c", kind, "ores_in_ground/stone", ["lead_ore"])
        T("c", kind, "ores_in_ground/deepslate", ["deepslate_lead_ore", "deepslate_uranium_ore"])
        ctx.write(ctx.DATA / "c" / "tags" / kind / "ores" / "lead.json",
                  {"replace": False, "values": [f"{MOD}:lead_ore", f"{MOD}:deepslate_lead_ore"]})
        ctx.write(ctx.DATA / "c" / "tags" / kind / "ores" / "uranium.json",
                  {"replace": False, "values": [f"{MOD}:deepslate_uranium_ore"]})
    for group, entries in (("raw_materials", {"lead": "raw_lead", "uranium": "raw_uranium"}),
                           ("ingots", {"lead": "lead_ingot", "uranium": "uranium_ingot"}),
                           ("dusts", {"lead": "lead_dust", "uranium": "uranium_dust"}),
                           ("plates", {"lead": "lead_plate"})):
        T("c", "item", group, [f"#c:{group}/{m}" for m in entries])
        for metal, item in entries.items():
            ctx.write(ctx.DATA / "c" / "tags" / "item" / group / f"{metal}.json", {"replace": False, "values": [f"{MOD}:{item}"]})
    T("minecraft", "item", "head_armor", ["hazmat_helmet"])
    T("minecraft", "item", "chest_armor", ["hazmat_chestplate"])
    T("minecraft", "item", "leg_armor", ["hazmat_leggings"])
    T("minecraft", "item", "foot_armor", ["hazmat_boots"])
    for tag in ("bypasses_armor", "bypasses_shield", "no_knockback", "bypasses_cooldown"):
        T("minecraft", "damage_type", tag, [f"{MOD}:radiation"])


def data(ctx):
    ctx.write(ctx.DATA / MOD / "damage_type" / "radiation.json", {"exhaustion": 0.0, "message_id": f"{MOD}.radiation",
                                                                  "scaling": "never", "effects": "hurt"})
    sounds = ctx.ASSETS / "sounds.json"
    have = json.loads(sounds.read_text()) if sounds.exists() else {}
    have.update({
        "geiger_click": {"subtitle": f"subtitles.{MOD}.geiger_click", "sounds": [
            {"name": "minecraft:random/click", "pitch": 2.0, "volume": 0.35},
            {"name": "minecraft:note/hat", "pitch": 1.6, "volume": 0.45}]},
        "reactor_alarm": {"subtitle": f"subtitles.{MOD}.reactor_alarm", "sounds": [
            {"name": "minecraft:note/bit", "pitch": 0.8, "volume": 1.0, "attenuation_distance": 48},
            {"name": "minecraft:note/pling", "pitch": 0.7, "volume": 1.0, "attenuation_distance": 48}]},
        "fusion_hum": {"subtitle": f"subtitles.{MOD}.fusion_hum", "sounds": [
            {"name": "minecraft:block/beacon/ambient", "pitch": 0.6, "volume": 1.0, "attenuation_distance": 24}]},
    })
    ctx.write(sounds, have)


# ============================================================ recipes

def recipes(ctx):
    SH, SL, MC = ctx.shaped, ctx.shapeless, ctx.machine
    BP, SP, AP, TP, LP = "#c:plates/bronze", "#c:plates/steel", "#c:plates/aluminum", "#c:plates/titanium", "#c:plates/lead"
    CW, AC, AM, M = "#c:wires/copper", "advanced_circuit", "advanced_machine_frame", "machine_frame"
    # ---- Bronze: the first FE
    SH("kinetic_dynamo", ["PWP", "GCG", "PWP"], {"P": BP, "W": CW, "G": "#c:gears/iron", "C": "minecraft:copper_block"}, "kinetic_dynamo")
    # ---- Electric
    SH("steam_engine", ["CGC", "PMP", "BFB"], {"C": "#c:plates/copper", "G": "#c:gears/steel", "P": "minecraft:piston",
                                              "M": M, "B": "minecraft:bricks", "F": "minecraft:furnace"}, "steam_engine")
    SH("wind_turbine", ["PSP", "GMG", "PCP"], {"P": SP, "S": "#c:rods/steel", "G": "#c:gears/steel", "M": "motor",
                                              "C": "basic_circuit"}, "wind_turbine")
    SH("turbine_mast", [" P ", "PRP", " P "], {"P": "#c:plates/iron", "R": "#c:rods/steel"}, "turbine_mast", 4)
    # ---- Automation
    SH("biogas_generator", ["PKP", "CMC", "PFP"], {"P": AP, "K": "minecraft:composter", "C": AC, "M": AM,
                                                  "F": "minecraft:furnace"}, "biogas_generator")
    SH("magmatic_generator", ["PMP", "CFC", "PHP"], {"P": AP, "M": "minecraft:magma_block", "C": AC, "F": AM,
                                                    "H": "heating_coil"}, "magmatic_generator")
    SH("solar_array", ["SSS", "PCP", "PWP"], {"S": "solar_panel", "P": AP, "C": AC, "W": "#c:wires/gold"}, "solar_array")
    # ---- lead and uranium
    for metal, raw, ore_tag in (("lead", "raw_lead", "#c:ores/lead"), ("uranium", "raw_uranium", "#c:ores/uranium")):
        ingot, dust = f"{metal}_ingot", f"{metal}_dust"
        smelt(ctx, f"{ingot}_from_raw", raw, ingot)
        smelt(ctx, f"{ingot}_from_dust", dust, ingot, 0.3)
        smelt(ctx, f"{ingot}_from_ore", ore_tag, ingot)
        by = "iron_dust" if metal == "lead" else "lead_dust"
        MC("crushing", f"{dust}_from_raw_quern", [(raw, 1)], dust, 1, time=40, byproduct=(dust, 0.5))
        MC("crushing", f"{dust}_from_raw_burner", [(raw, 1)], dust, 2, time=60, min_grade=2)
        MC("crushing", f"{dust}_from_raw", [(raw, 1)], dust, 2, time=60, min_grade=3, byproduct=(by, 0.10))
        MC("crushing", f"{dust}_from_ore", [(ore_tag, 1)], dust, 3, time=80, min_grade=3, byproduct=(by, 0.10))
        MC("crushing", f"{dust}_from_raw_washed", [(raw, 1)], dust, 3, time=60, min_grade=4, byproduct=(by, 0.25))
        MC("crushing", f"{dust}_from_ingot", [(f"#c:ingots/{metal}", 1)], dust, 1, time=40, min_grade=2)
    SL("lead_plate_by_hammer", ["#c:ingots/lead", "#c:ingots/lead", "forge_hammer"], "lead_plate")
    MC("pressing", "lead_plate", [("#c:ingots/lead", 1)], "lead_plate", time=40, mold="plate_mold")
    # ---- Industrial: the centrifuge and the fuel cycle
    SH("centrifuge", ["TGT", "MFM", "TCT"], {"T": TP, "G": "minecraft:glass", "M": "motor", "F": AM, "C": AC}, "centrifuge")
    centrifuge_recipe(ctx, "enriched_uranium", [("#c:dusts/uranium", 3)], "enriched_uranium", 1, ("depleted_uranium", 2), time=200)
    centrifuge_recipe(ctx, "enriched_uranium_from_ingots", [("#c:ingots/uranium", 3)], "enriched_uranium", 1, ("depleted_uranium", 2), time=240)
    centrifuge_recipe(ctx, "reprocess_spent_fuel", [("depleted_fuel_rod", 1)], "nuclear_waste", 2, ("depleted_uranium", 1), time=300,
               extras=[("enriched_uranium", 1, 0.25)])
    centrifuge_recipe(ctx, "radioisotope_pellet", [("nuclear_waste", 3)], "radioisotope_pellet", 1, None, time=400)
    if not planets_provide_helium3():
        # stand-in until the Moon provides it: tritium decays into helium-3
        centrifuge_recipe(ctx, "helium_3_from_tritium", [("tritium_cell", 1)], "helium_3", 1, ("empty_cell", 1), time=600)
    MC("assembling", "fuel_rod", [("enriched_uranium", 3), ("#c:rods/steel", 2), (LP, 1)], "fuel_rod", time=160, min_grade=3)
    SH("empty_cell", [" P ", "PGP", " P "], {"P": "#c:plates/tin", "G": "minecraft:glass_pane"}, "empty_cell", 4)
    SL("coolant_cell", ["empty_cell", "minecraft:ice", "minecraft:snowball", "minecraft:snowball"], "coolant_cell")
    SH("reactor_casing", ["LTL", "TCT", "LTL"], {"L": LP, "T": TP, "C": "minecraft:gray_concrete"}, "reactor_casing", 8)
    SH("reactor_glass", ["LGL", "G G", "LGL"], {"L": LP, "G": "minecraft:glass"}, "reactor_glass", 4)
    SH("reactor_fuel_channel", ["PGP", "R R", "PGP"], {"P": SP, "G": "minecraft:glass", "R": "#c:rods/steel"}, "reactor_fuel_channel", 2)
    SH("reactor_control_rod", [" M ", "LRL", " R "], {"M": "motor", "L": LP, "R": "#c:rods/steel"}, "reactor_control_rod")
    SH("reactor_controller", ["CAC", "KFK", "CTC"], {"C": "reactor_casing", "A": AC, "K": "minecraft:comparator", "F": AM, "T": TP},
       "reactor_controller")
    SL("reactor_access_port", ["reactor_casing", "minecraft:hopper"], "reactor_access_port")
    SL("reactor_power_port", ["reactor_casing", "aluminum_cable", "aluminum_cable"], "reactor_power_port")
    SL("reactor_coolant_port", ["reactor_casing", "minecraft:bucket", "motor"], "reactor_coolant_port")
    SL("reactor_redstone_port", ["reactor_casing", "minecraft:comparator", "minecraft:redstone"], "reactor_redstone_port")
    SH("waste_barrel", ["LDL", "L L", "LLL"], {"L": LP, "D": "minecraft:yellow_dye"}, "waste_barrel")
    SH("corium", ["WWW", "WMW", "WWW"], {"W": "nuclear_waste", "M": "minecraft:magma_block"}, "corium")
    SH("geiger_counter", ["GW ", "PCP", "PRP"], {"G": "minecraft:glass_pane", "W": CW, "P": "#c:plates/tin", "C": "basic_circuit",
                                                "R": "minecraft:redstone"}, "geiger_counter", category="equipment")
    Y = "minecraft:yellow_wool"
    SH("hazmat_helmet", ["WLW", "WGW"], {"W": Y, "L": LP, "G": "minecraft:glass_pane"}, "hazmat_helmet", category="equipment")
    SH("hazmat_chestplate", ["L L", "WLW", "WWW"], {"W": Y, "L": LP}, "hazmat_chestplate", category="equipment")
    SH("hazmat_leggings", ["WLW", "W W", "L L"], {"W": Y, "L": LP}, "hazmat_leggings", category="equipment")
    SH("hazmat_boots", ["W W", "L L"], {"W": Y, "L": LP}, "hazmat_boots", category="equipment")
    # ---- Orbital: RTG (Precision Assembler) and the Electrolyzer
    MC("assembling", "rtg", [(LP, 4), (TP, 4), (AC, 1), ("heating_coil", 2)], "rtg", time=400, min_grade=6)
    MC("assembling", "electrolyzer", [(TP, 4), (AM, 1), ("#c:wires/gold", 4), ("minecraft:glass", 2)], "electrolyzer",
       time=400, min_grade=6)
    MC("electrolysis", "deuterium_cell", [("minecraft:water_bucket", 1), ("empty_cell", 1)], "deuterium_cell", time=200, min_grade=6)
    electrolysis_recipe(ctx, "tritium_cell", [("deuterium_cell", 4)], "tritium_cell", ("empty_cell", 3), time=400)
    # ---- Quantum: the tokamak
    Q = "#c:ingots/quantum_alloy"
    SH("fusion_casing", ["QTQ", "T T", "QTQ"], {"Q": Q, "T": TP}, "fusion_casing", 8)
    SH("fusion_magnet", ["SSS", "SQS", "SSS"], {"S": "superconductor_cable", "Q": Q}, "fusion_magnet", 2)
    SL("fusion_port", ["fusion_casing", "superconductor_cable", "minecraft:hopper"], "fusion_port")
    SH("tokamak_core", ["QMQ", "OFO", "QMQ"], {"Q": "#c:storage_blocks/quantum_alloy", "M": "fusion_magnet",
                                              "O": "orbital_targeting_core", "F": AM}, "tokamak_core")


def smelt(ctx, name, inp, out, xp=0.7):
    for kind, time in (("smelting", 200), ("blasting", 100)):
        ctx.write(ctx.DATA / MOD / "recipe" / f"{name}{'' if kind == 'smelting' else '_blasting'}.json", {
            "type": f"minecraft:{kind}", "category": "misc", "ingredient": ctx.ing(inp),
            "result": {"id": f"{MOD}:{out}" if ":" not in out else out}, "experience": xp, "cookingtime": time})


def centrifuge_recipe(ctx, name, ins, out, count, by, time, extras=None):
    ctx.machine("centrifuging", name, ins, out, count, time=time, min_grade=5)
    path = ctx.DATA / MOD / "recipe" / "centrifuging" / f"{name}.json"
    obj = json.loads(path.read_text())
    if by:
        obj["byproduct"] = {"item": {"id": f"{MOD}:{by[0]}", "count": by[1]}, "chance": 1.0}
    if extras:
        obj["extras"] = [{"item": {"id": f"{MOD}:{i}", "count": c}, "chance": p} for i, c, p in extras]
    ctx.write(path, obj)


def electrolysis_recipe(ctx, name, ins, out, by, time):
    ctx.machine("electrolysis", name, ins, out, time=time, min_grade=6)
    path = ctx.DATA / MOD / "recipe" / "electrolysis" / f"{name}.json"
    obj = json.loads(path.read_text())
    obj["byproduct"] = {"item": {"id": f"{MOD}:{by[0]}", "count": by[1]}, "chance": 1.0}
    ctx.write(path, obj)


# ============================================================ advancements

def code_advancement(ctx, key, parent, icon, frame, en, es, hidden=False):
    display = {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    if hidden:
        display["hidden"] = True
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}", "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": display, "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def advancements(ctx):
    A = ctx.advancement
    A("bronze_dynamo", "age_bronze", "kinetic_dynamo", ["kinetic_dynamo"], "Spin Doctor",
      "Build a Kinetic Dynamo: a Water Wheel or Windmill next to it makes your first FE",
      "Doctor en giros", "Construye una dinamo cinética: una rueda hidráulica o un molino a su lado dan tu primera FE")
    A("electric_steam", "electric_power", "steam_engine", ["steam_engine"], "Full Steam Ahead",
      "Build a Steam Engine. Give it water and fuel, and wait for the pressure",
      "A todo vapor", "Construye una máquina de vapor. Dale agua y combustible y espera a que suba la presión")
    A("electric_wind", "electric_power", "wind_turbine", ["wind_turbine"], "Blowin' in the Wind",
      "Build a Wind Turbine and put it on a mast of four Turbine Masts or more, high up",
      "Soplando en el viento", "Construye una turbina eólica y ponla sobre un mástil de cuatro tramos o más, bien alto")
    A("automation_biogas", "age_automation", "biogas_generator", ["biogas_generator"], "Compost Heap",
      "Build a Biogas Generator and feed it crops, leaves or rotten flesh",
      "Montón de compost", "Construye un generador de biogás y aliméntalo con cultivos, hojas o carne podrida")
    A("automation_magmatic", "automation_geothermal", "magmatic_generator", ["magmatic_generator"], "Liquid Hot Magma",
      "Build a Magmatic Generator: lava buckets in, empty buckets out",
      "Magma líquido y ardiente", "Construye un generador magmático: entran cubos de lava, salen cubos vacíos")
    A("automation_solar_array", "age_automation", "solar_array", ["solar_array"], "Here Comes the Sun",
      "Build an Advanced Solar Array: six Solar Panels in one",
      "Aquí viene el sol", "Construye un panel solar avanzado: seis paneles solares en uno")
    A("industrial_lead", "age_industrial", "lead_ingot", ["lead_ingot"], "Heavy Metal",
      "Smelt lead: it keeps radiation in", "Metal pesado", "Funde plomo: frena la radiación")
    A("industrial_uranium", "age_industrial", "raw_uranium", ["raw_uranium"], "Yellowcake",
      "Mine uranium, deep in the deepslate. Careful: it's radioactive",
      "Torta amarilla", "Extrae uranio, en lo profundo de la pizarra. Cuidado: es radiactivo")
    A("industrial_centrifuge", "industrial_uranium", "centrifuge", ["centrifuge", "enriched_uranium"], "Round and Round",
      "Enrich uranium in a Centrifuge", "Vueltas y vueltas", "Enriquece uranio en una centrifugadora")
    A("industrial_fuel_rod", "industrial_centrifuge", "fuel_rod", ["fuel_rod"], "Rods of Power",
      "Assemble a Fuel Rod from enriched uranium", "Barras de poder", "Ensambla una barra de combustible con uranio enriquecido")
    A("industrial_reactor", "industrial_fuel_rod", "reactor_controller", ["reactor_controller"], "Critical Mass",
      "Build a fission reactor: casing box, fuel channels, control rods and a controller",
      "Masa crítica", "Construye un reactor de fisión: caja de carcasa, canales de combustible, barras de control y un controlador",
      frame="goal")
    A("industrial_hazmat", "industrial_lead", "hazmat_chestplate", ["hazmat_chestplate"], "Suited and Booted",
      "Make a Hazmat Suit: wear all four pieces and radiation can't touch you",
      "Bien equipado", "Fabrica un traje Hazmat: con las cuatro piezas la radiación no te toca")
    A("industrial_geiger", "industrial_uranium", "geiger_counter", ["geiger_counter"], "Click Click Click",
      "Make a Geiger Counter and listen", "Clic clic clic", "Fabrica un contador Geiger y escucha")
    A("industrial_waste", "industrial_reactor", "waste_barrel", ["waste_barrel", "depleted_fuel_rod"], "Not in My Backyard",
      "Store spent fuel in a lead-lined Waste Barrel", "No en mi patio", "Guarda el combustible gastado en un barril de residuos forrado de plomo")
    code_advancement(ctx, "industrial_radiation", "industrial_uranium", "nuclear_waste", "task",
                     ("Glowing Review", "Get radiation sickness"), ("Brillas con luz propia", "Contrae la enfermedad por radiación"),
                     hidden=True)
    code_advancement(ctx, "industrial_meltdown", "industrial_reactor", "corium", "challenge",
                     ("China Syndrome", "Witness a reactor meltdown (and live to regret it)"),
                     ("Síndrome de China", "Presencia la fusión del núcleo de un reactor (y vive para lamentarlo)"), hidden=True)
    A("orbital_rtg", "age_orbital", "rtg", ["rtg"], "Plutonium Heart",
      "Build an RTG: steady power from a decaying pellet, even in space",
      "Corazón de plutonio", "Construye un RTG: energía constante de una pastilla que decae, incluso en el espacio")
    A("orbital_electrolyzer", "age_orbital", "electrolyzer", ["electrolyzer", "deuterium_cell"], "Heavy Water",
      "Split deuterium out of water in an Electrolyzer", "Agua pesada", "Separa deuterio del agua en un electrolizador")
    A("quantum_tokamak", "age_quantum", "tokamak_core", ["tokamak_core"], "Donut of Destiny",
      "Build a Tokamak Core and the magnet ring around it", "La rosquilla del destino",
      "Construye un núcleo tokamak y el anillo de imanes a su alrededor")
    code_advancement(ctx, "quantum_fusion", "quantum_tokamak", "tokamak_core", "challenge",
                     ("A Star Is Born", "Ignite fusion plasma in a tokamak"),
                     ("Ha nacido una estrella", "Enciende plasma de fusión en un tokamak"))


# ============================================================ lang

GEN_NAMES = {
    "kinetic_dynamo": ("Kinetic Dynamo", "Dinamo cinética"), "steam_engine": ("Steam Engine", "Máquina de vapor"),
    "wind_turbine": ("Wind Turbine", "Turbina eólica"), "biogas_generator": ("Biogas Generator", "Generador de biogás"),
    "magmatic_generator": ("Magmatic Generator", "Generador magmático"), "solar_array": ("Advanced Solar Array", "Panel solar avanzado"),
    "rtg": ("RTG (Radioisotope Generator)", "RTG (generador de radioisótopos)"),
}
GEN_DESC = {
    "kinetic_dynamo": ("Turns the rotation of a Water Wheel or Windmill touching it into FE.",
                       "Convierte en FE el giro de una rueda hidráulica o un molino de viento que la toque."),
    "steam_engine": ("Fuel heats the boiler; from 100 °C the steam turns the flywheel. Needs water: buckets, or water sources touching it. Needs air.",
                     "El combustible calienta la caldera; desde 100 °C el vapor mueve el volante. Necesita agua: cubos, o fuentes de agua que la toquen. Necesita aire."),
    "wind_turbine": ("Put it on 4+ Turbine Masts. More power high up and in storms; its 5×5 rotor disc must be clear.",
                     "Ponla sobre 4+ tramos de mástil. Más energía en altura y con tormenta; el disco de 5×5 del rotor debe estar libre."),
    "biogas_generator": ("Digests crops, leaves, saplings or rotten flesh into biogas and burns it. Needs air.",
                         "Digiere cultivos, hojas, brotes o carne podrida en biogás y lo quema. Necesita aire."),
    "magmatic_generator": ("Burns lava buckets (50 s each) and magma blocks; hands back the empty buckets. Pipes can feed it.",
                           "Quema cubos de lava (50 s cada uno) y bloques de magma; devuelve los cubos vacíos. Se puede alimentar con tuberías."),
    "solar_array": ("A big tilted panel: six Solar Panels' worth in daylight, half in rain, 1.5× in airless space.",
                    "Un gran panel inclinado: como seis paneles solares con luz de día, la mitad con lluvia, 1,5× en el vacío."),
    "rtg": ("A Radioisotope Pellet's decay heat gives steady power for an hour: day, night, in orbit, no air needed.",
            "El calor de una pastilla de radioisótopos da energía constante durante una hora: de día, de noche, en órbita, sin aire."),
}
GEN_POWER = {
    "kinetic_dynamo": ("Up to %s FE/t (about 20 from a Water Wheel in flowing water)", "Hasta %s FE/t (unas 20 con una rueda en agua corriente)"),
    "steam_engine": ("%s FE/t at full pressure", "%s FE/t a plena presión"),
    "wind_turbine": ("Up to %s FE/t in a thunderstorm", "Hasta %s FE/t con tormenta"),
    "biogas_generator": ("%s FE/t", "%s FE/t"), "magmatic_generator": ("%s FE/t", "%s FE/t"),
    "solar_array": ("Up to %s FE/t", "Hasta %s FE/t"), "rtg": ("%s FE/t, always", "%s FE/t, siempre"),
}
BLOCK_NAMES = {
    "turbine_mast": ("Turbine Mast", "Tramo de mástil de turbina"),
    "lead_ore": ("Lead Ore", "Mena de plomo"), "deepslate_lead_ore": ("Deepslate Lead Ore", "Mena de plomo de pizarra profunda"),
    "deepslate_uranium_ore": ("Deepslate Uranium Ore", "Mena de uranio de pizarra profunda"),
    "reactor_casing": ("Reactor Casing", "Carcasa de reactor"), "reactor_glass": ("Reactor Glass", "Vidrio de reactor"),
    "reactor_fuel_channel": ("Reactor Fuel Channel", "Canal de combustible del reactor"),
    "reactor_control_rod": ("Reactor Control Rod", "Barra de control del reactor"),
    "reactor_controller": ("Reactor Controller", "Controlador del reactor"),
    "reactor_access_port": ("Reactor Access Port", "Puerto de acceso del reactor"),
    "reactor_power_port": ("Reactor Power Port", "Puerto de energía del reactor"),
    "reactor_coolant_port": ("Reactor Coolant Port", "Puerto de refrigerante del reactor"),
    "reactor_redstone_port": ("Reactor Redstone Port", "Puerto de redstone del reactor"),
    "waste_barrel": ("Waste Barrel", "Barril de residuos nucleares"), "corium": ("Corium", "Corio"),
    "fusion_casing": ("Fusion Casing", "Carcasa de fusión"), "fusion_magnet": ("Fusion Magnet", "Imán de fusión"),
    "fusion_port": ("Fusion Port", "Puerto de fusión"), "tokamak_core": ("Tokamak Core", "Núcleo tokamak"),
}
ITEM_NAMES = {
    "raw_lead": ("Raw Lead", "Plomo en bruto"), "lead_dust": ("Lead Dust", "Polvo de plomo"), "lead_ingot": ("Lead Ingot", "Lingote de plomo"),
    "lead_plate": ("Lead Plate", "Placa de plomo"), "raw_uranium": ("Raw Uranium", "Uranio en bruto"),
    "uranium_dust": ("Uranium Dust", "Polvo de uranio"), "uranium_ingot": ("Uranium Ingot", "Lingote de uranio"),
    "enriched_uranium": ("Enriched Uranium", "Uranio enriquecido"), "depleted_uranium": ("Depleted Uranium", "Uranio empobrecido"),
    "fuel_rod": ("Fuel Rod", "Barra de combustible"), "depleted_fuel_rod": ("Depleted Fuel Rod", "Barra de combustible gastada"),
    "nuclear_waste": ("Nuclear Waste", "Residuos nucleares"), "radioisotope_pellet": ("Radioisotope Pellet", "Pastilla de radioisótopos"),
    "empty_cell": ("Empty Cell", "Celda vacía"), "coolant_cell": ("Coolant Cell", "Celda de refrigerante"),
    "deuterium_cell": ("Deuterium Cell", "Celda de deuterio"), "tritium_cell": ("Tritium Cell", "Celda de tritio"),
    "geiger_counter": ("Geiger Counter", "Contador Geiger"), "hazmat_helmet": ("Hazmat Helmet", "Casco Hazmat"),
    "hazmat_chestplate": ("Hazmat Suit", "Traje Hazmat"), "hazmat_leggings": ("Hazmat Leggings", "Pantalones Hazmat"),
    "hazmat_boots": ("Hazmat Boots", "Botas Hazmat"), "helium_3": ("Helium-3", "Helio-3"),
}


def lang(ctx):
    L = ctx.lang
    B, I, T, G, M = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"gui.{MOD}", f"message.{MOD}"
    for name, (en, es) in GEN_NAMES.items():
        L(f"{B}.{name}", en, es)
        L(f"desc.{MOD}.{name}", *GEN_DESC[name])
        L(f"{T}.power.{name}", *GEN_POWER[name])
    for name, (en, es) in BLOCK_NAMES.items():
        L(f"{B}.{name}", en, es)
    for name, (en, es) in ITEM_NAMES.items():
        if name == "helium_3" and planets_provide_helium3():
            continue
        L(f"{I}.{name}", en, es)
    L(f"jei.{MOD}.centrifuging", "Centrifuging", "Centrifugado")
    L(f"jei.{MOD}.electrolysis", "Electrolysis", "Electrólisis")
    L(f"effect.{MOD}.radiation", "Radiation Sickness", "Enfermedad por radiación")
    L(f"death.attack.{MOD}.radiation", "%1$s died of radiation poisoning", "%1$s murió por envenenamiento radiactivo")
    L(f"death.attack.{MOD}.radiation.player", "%1$s died of radiation poisoning while fighting %2$s",
      "%1$s murió por envenenamiento radiactivo mientras luchaba contra %2$s")
    for key, en, es in [("geiger_click", "Geiger counter clicks", "Clics del contador Geiger"),
                        ("reactor_alarm", "Reactor alarm", "Alarma del reactor"),
                        ("fusion_hum", "Plasma hums", "Zumbido del plasma")]:
        L(f"subtitles.{MOD}.{key}", en, es)
    # tooltips
    for key, en, es in [
        ("turbine_mast", "Stack %s or more under a Wind Turbine", "Apila %s o más bajo una turbina eólica"),
        ("reactor_casing", "Wall of a fission reactor", "Pared de un reactor de fisión"),
        ("reactor_glass", "Wall of a fission reactor you can see through (or a tokamak's shell)",
         "Pared transparente de un reactor de fisión (o de la carcasa de un tokamak)"),
        ("reactor_fuel_channel", "Goes inside: holds one fuel rod. Channels touching each other run hotter and burn fuel better",
         "Va dentro: aloja una barra de combustible. Los canales que se tocan se calientan más y aprovechan mejor el combustible"),
        ("reactor_control_rod", "Goes inside: absorbs neutrons. One per four channels gives full control",
         "Va dentro: absorbe neutrones. Una cada cuatro canales da el control total"),
        ("reactor_build", "A closed box, 3×3×3 to 7×7×7: casing, glass and ports outside; channels, control rods or air inside",
         "Una caja cerrada, de 3×3×3 a 7×7×7: carcasa, vidrio y puertos fuera; canales, barras de control o aire dentro"),
        ("reactor_controller", "Part of a reactor wall, facing out. Screen: core map, temperature, control rods, SCRAM",
         "Parte de una pared del reactor, mirando afuera. Pantalla: mapa del núcleo, temperatura, barras de control, SCRAM"),
        ("reactor_danger", "Melts down at %s °C without coolant. Redstone signal = SCRAM",
         "Se funde a %s °C sin refrigerante. Señal de redstone = SCRAM"),
        ("reactor_access_port", "Fuel rods and coolant items in, spent rods and empty buckets/cells out (pipes, hoppers)",
         "Entran barras de combustible y refrigerante, salen barras gastadas y cubos/celdas vacíos (tuberías, tolvas)"),
        ("reactor_power_port", "The reactor's FE comes out here", "Por aquí sale la FE del reactor"),
        ("reactor_coolant_port", "Pumps 50 mB/t of coolant from each water source block touching it; takes coolant items",
         "Bombea 50 mB/t de refrigerante por cada fuente de agua que lo toque; acepta objetos refrigerantes"),
        ("reactor_redstone_port", "A redstone signal SCRAMs the reactor; a comparator reads its temperature",
         "Una señal de redstone hace SCRAM; un comparador lee su temperatura"),
        ("reactor_port_wall", "Place it in a reactor wall, facing out", "Colócalo en una pared del reactor, mirando afuera"),
        ("waste_barrel", "27 lead-lined slots for radioactive items: nothing inside irradiates anyone",
         "27 ranuras forradas de plomo para objetos radiactivos: lo que hay dentro no irradia a nadie"),
        ("waste_barrel_keep", "Keeps its contents when broken", "Conserva su contenido al romperse"),
        ("corium", "Molten reactor core. Burning hot and fiercely radioactive", "Núcleo de reactor fundido. Ardiente y ferozmente radiactivo"),
        ("hazmat", "Lead-lined: stops a quarter of the radiation", "Forrado de plomo: detiene un cuarto de la radiación"),
        ("hazmat_set", "Wear all four pieces: no radiation gets through", "Con las cuatro piezas no pasa nada de radiación"),
        ("geiger_counter", "Hold it: it clicks faster near radiation and shows the dose on screen. Use: reading",
         "Llévalo en la mano: hace clic más rápido cerca de radiación y muestra la dosis. Usar: lectura"),
        ("fusion_casing", "Shell of a tokamak (Reactor Glass works too)", "Carcasa de un tokamak (el vidrio de reactor también sirve)"),
        ("fusion_magnet", "Superconducting coil: the tokamak's walls and central solenoid",
         "Bobina superconductora: las paredes y el solenoide central del tokamak"),
        ("fusion_port", "FE in to charge the magnets, FE out while burning, fuel cells in. Right-click: tokamak screen",
         "Entra FE para cargar los imanes, sale FE mientras arde, entran celdas de combustible. Clic derecho: pantalla del tokamak"),
        ("tokamak_core", "The centre of a 7×7×3 fusion ring. Burns deuterium and helium-3; a tritium cell ignites it",
         "El centro de un anillo de fusión de 7×7×3. Quema deuterio y helio-3; una celda de tritio lo enciende"),
        ("tokamak_numbers", "%s FE/t while burning. Start-up: %s FE", "%s FE/t mientras arde. Arranque: %s FE"),
        ("tokamak_build", "Middle layer: core, magnet ring, air ring, magnet ring. Top and bottom: magnet on the core, casing around",
         "Capa central: núcleo, anillo de imanes, anillo de aire, anillo de imanes. Arriba y abajo: imán sobre el núcleo, carcasa alrededor"),
    ]:
        L(f"{T}.{key}", en, es)
    # generator screens
    for key, en, es in [
        ("power.status_rate", "%s · %s FE/t", "%s · %s FE/t"),
        ("power.dynamo_info", "Rotation %s%% · %s rotors", "Giro %s%% · %s rotores"),
        ("power.steam_info", "%s °C · pressure %s%%", "%s °C · presión %s%%"),
        ("power.wind_info", "Mast %s/%s · %s m above sea", "Mástil %s/%s · %s m sobre el mar"),
        ("power.gas_info", "Biogas %s / %s mB", "Biogás %s / %s mB"),
        ("power.lava_info", "Lava %s / %s mB", "Lava %s / %s mB"),
        ("power.rtg_info", "%s min %s s of decay left", "Quedan %s min %s s de desintegración"),
        ("power.rtg_decay", "Pellet: %s%% left", "Pastilla: queda un %s%%"),
        ("power.temperature", "Boiler: %s °C", "Caldera: %s °C"),
        ("power.pressure", "Pressure: %s%%", "Presión: %s%%"),
        ("power.steam_hint", "Steam from 100 °C, full pressure at 180 °C", "Vapor desde 100 °C, presión máxima a 180 °C"),
        ("power.water", "Water: %s / %s mB", "Agua: %s / %s mB"),
        ("power.water_sources", "%s water sources touching it (25 mB/t each)", "%s fuentes de agua lo tocan (25 mB/t cada una)"),
        ("power.gas_hint", "Crops, leaves, saplings, rotten flesh… 2 mB burn every tick", "Cultivos, hojas, brotes, carne podrida… arde 2 mB por tick"),
        ("power.lava_hint", "1 mB per tick: a bucket lasts 50 s", "1 mB por tick: un cubo dura 50 s"),
        ("power.wind_hint", "Needs %s+ masts below and a clear 5×5 disc in front", "Necesita %s+ tramos debajo y un disco libre de 5×5 delante"),
    ]:
        L(f"{G}.{key}", en, es)
    for key, en, es in [("idle", "Idle", "Inactivo"), ("running", "Running", "Generando"), ("no_fuel", "No fuel", "Sin combustible"),
                        ("full", "Buffer full", "Búfer lleno"), ("heating", "Heating up", "Calentando"), ("no_water", "No water", "Sin agua"),
                        ("no_rotation", "Not turning", "Sin giro"), ("no_sky", "No open sky", "Sin cielo abierto"), ("night", "Night", "Noche"),
                        ("no_mast", "Needs a mast", "Necesita un mástil"), ("blocked", "Rotor blocked", "Rotor bloqueado"),
                        ("no_air", "No air: nothing burns", "Sin aire: nada arde"), ("digesting", "Digesting", "Digiriendo"),
                        ("no_pellet", "No pellet", "Sin pastilla")]:
        L(f"status.{MOD}.power.{key}", en, es)
    # reactor screen
    for key, en, es in [
        ("output", "Output", "Salida"), ("temp", "Core", "Núcleo"), ("heat", "Heat", "Calor"), ("rods", "Rods", "Barras"),
        ("bonus", "Bonus · ctrl", "Bonus · ctrl"), ("insertion", "Control rods: %s%% in", "Barras de control: %s%% dentro"),
        ("scram", "SCRAM", "SCRAM"), ("fuel", "Fuel", "Combust."), ("spent", "Spent", "Gastado"), ("coolant", "Coolant", "Refrig."),
        ("melted", "MELTDOWN", "FUSIÓN DEL NÚCLEO"), ("incomplete", "Structure incomplete", "Estructura incompleta"),
        ("alarm", "!! TEMPERATURE !!", "¡¡ TEMPERATURA !!"), ("scram_waste", "SCRAM: no room for waste", "SCRAM: sin sitio para residuos"),
        ("scram_redstone", "SCRAM (redstone)", "SCRAM (redstone)"), ("scrammed", "SCRAM", "SCRAM"),
        ("no_fuel", "No fuel", "Sin combustible"), ("no_coolant", "NO COOLANT", "SIN REFRIGERANTE"), ("online", "Online", "En marcha"),
        ("heating", "Heating up", "Calentando"),
        ("temp_tip", "Core temperature: %s °C", "Temperatura del núcleo: %s °C"),
        ("temp_marks", "Alarm at %s °C, meltdown at %s °C. Coolant boils from 100 °C",
         "Alarma a %s °C, fusión a %s °C. El refrigerante hierve desde 100 °C"),
        ("coolant_tip", "Coolant: %s / %s mB", "Refrigerante: %s / %s mB"),
        ("coolant_use", "Using %s mB/t", "Consumo: %s mB/t"),
        ("coolant_hint", "Water buckets, ice, packed ice, blue ice, Coolant Cells, or water sources at a Coolant Port",
         "Cubos de agua, hielo, hielo compacto, hielo azul, celdas de refrigerante, o fuentes de agua en un puerto de refrigerante"),
        ("map_tip", "The core from above: green = fuel channels, grey = control rods", "El núcleo visto desde arriba: verde = canales, gris = barras de control"),
        ("map_counts", "%s fuel channels, %s control rods", "%s canales de combustible, %s barras de control"),
        ("burn", "Current rods: %s%% burnt", "Barras actuales: %s%% consumido"),
        ("bonus_tip", "Bonus: channels touching each other make more heat per rod", "Bonus: los canales que se tocan dan más calor por barra"),
        ("authority_tip", "Ctrl: how much the control rods can hold back (1 rod per 4 channels = 100%)",
         "Ctrl: cuánto pueden frenar las barras de control (1 barra cada 4 canales = 100%)"),
        ("slider_tip", "Click to set how far the control rods go in", "Haz clic para fijar cuánto entran las barras de control"),
        ("scram_tip", "Drop every control rod in at once", "Mete todas las barras de control de golpe"),
        ("scram_release", "Release the SCRAM", "Soltar el SCRAM"),
        ("scram_redstone_tip", "A redstone signal on the controller or a Redstone Port does the same",
         "Una señal de redstone en el controlador o en un puerto de redstone hace lo mismo"),
        ("error.ok", "", ""), ("error.too_small", "Too small: at least 3×3×3", "Demasiado pequeño: mínimo 3×3×3"),
        ("error.too_big", "Too big: at most 7×7×7", "Demasiado grande: máximo 7×7×7"),
        ("error.bad_wall", "Wall block missing or wrong at %s %s %s", "Falta un bloque de pared o no vale en %s %s %s"),
        ("error.bad_interior", "Only fuel channels, control rods or air inside (%s %s %s)",
         "Dentro solo canales, barras de control o aire (%s %s %s)"),
        ("error.no_channels", "No fuel channels inside", "No hay canales de combustible dentro"),
        ("error.two_controllers", "A second controller at %s %s %s", "Un segundo controlador en %s %s %s"),
    ]:
        L(f"{G}.reactor.{key}", en, es)
    # tokamak screen
    for key, en, es in [
        ("start", "Start", "Arrancar"), ("stop", "Stop", "Parar"), ("incomplete", "Ring incomplete", "Anillo incompleto"),
        ("state.cold", "Cold", "Frío"), ("state.charging", "Charging magnets", "Cargando imanes"),
        ("state.ready", "Charged: needs fuel", "Cargado: falta combustible"), ("state.igniting", "Igniting", "Encendiendo"),
        ("state.running", "Burning", "Ardiendo"), ("state.disrupted", "DISRUPTION", "DISRUPCIÓN"),
        ("output", "Output: %s FE/t", "Salida: %s FE/t"), ("charge", "Magnets: %s / %s FE", "Imanes: %s / %s FE"),
        ("charge_hint", "Feed FE into a Fusion Port while it's switched on", "Mete FE por un puerto de fusión con el tokamak encendido"),
        ("how", "Start, charge the magnets, load deuterium, helium-3 and a tritium cell: it ignites by itself",
         "Arranca, carga los imanes, pon deuterio, helio-3 y una celda de tritio: se enciende solo"),
        ("button_tip", "Switch the tokamak on or off (off cools the plasma safely)", "Enciende o apaga el tokamak (apagarlo enfría el plasma sin peligro)"),
        ("slot.0", "Deuterium Cells", "Celdas de deuterio"), ("slot.1", "Helium-3", "Helio-3"),
        ("slot.2", "Tritium Cell (ignition)", "Celda de tritio (encendido)"),
        ("error.ok", "", ""), ("error.needs_magnet", "Needs a Fusion Magnet at %s %s %s", "Falta un imán de fusión en %s %s %s"),
        ("error.needs_casing", "Needs casing, Reactor Glass or a port at %s %s %s", "Falta carcasa, vidrio de reactor o un puerto en %s %s %s"),
        ("error.channel_blocked", "The plasma channel must be empty (%s %s %s)", "El canal del plasma debe estar vacío (%s %s %s)"),
    ]:
        L(f"{G}.tokamak.{key}", en, es)
    for key, en, es in [
        ("reactor_alarm", "☢ REACTOR ALARM: %s °C (meltdown at %s °C)", "☢ ALARMA DEL REACTOR: %s °C (fusión a %s °C)"),
        ("meltdown", "☢ A reactor has melted down! Stay away from the corium without a Hazmat Suit.",
         "☢ ¡Un reactor se ha fundido! No te acerques al corio sin traje Hazmat."),
        ("fusion_disruption", "⚡ PLASMA DISRUPTION: containment lost", "⚡ DISRUPCIÓN DEL PLASMA: contención perdida"),
        ("geiger", "Geiger: %s rad/s here (%s through your suit), dose %s rad", "Geiger: %s rad/s aquí (%s a través del traje), dosis %s rad"),
    ]:
        L(f"{M}.{key}", en, es)
    for key, en, es in [("rad_rate", "%s rad/s", "%s rad/s"), ("rad_dose", "Dose %s rad", "Dosis %s rad"),
                        ("rad_shield", "Suit stops %s%%", "El traje frena %s%%"), ("rad_geiger", "Geiger counter", "Contador Geiger")]:
        L(f"hud.{MOD}.{key}", en, es)


def generate(ctx):
    blocks(ctx)
    items(ctx)
    worldgen(ctx)
    tags(ctx)
    data(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx)


# ============================================================ preview (tools/power_preview.png)

def preview(path):
    from PIL import Image, ImageDraw
    drill = S._load("drill")
    tex_root = S.ASSETS / "textures" / "block"
    cache = {}

    def textures(tmap):
        out = {}
        for k, v in tmap.items():
            name = v.split("/")[-1]
            if name not in cache:
                im = Image.open(tex_root / f"{name}.png").convert("RGBA")
                cache[name] = im.crop((0, 0, 16, 16)) if im.height > 16 else im
            out[k] = cache[name]
        return out

    FULL = {**DYN_TEX, **STEAM_TEX, **WIND_TEX, **BIO_TEX, **MAG_TEX, **SOLAR_TEX, **RTG_TEX, **CEN_TEX, **ELY_TEX, **REA_TEX, **FUS_TEX}
    entries = [
        ("Kinetic Dynamo", kinetic_dynamo() + shift(dynamo_armature(), 0, 3, 0), {**DYN_TEX, "coil": t("dynamo_coil_on"), "terminal": t("dynamo_terminal_on")}, 16),
        ("Steam Engine", steam_engine() + shift(steam_flywheel(), 9, 0, 1), {**STEAM_TEX, "firebox": t("steam_firebox_on")}, 16),
        ("Wind Turbine", [e.moved(0, 32, 0) for e in wind_turbine()] + [e.moved(0, 32, 0) for e in shift(scaled(wind_rotor(), 3), 0, -2, -9.5)]
         + sum((shift(turbine_mast(), 0, 16 * k, 0) for k in range(2)), []), WIND_TEX, 48),
        ("Biogas Generator", biogas_generator(True), {**BIO_TEX, "engine": t("biogas_engine_on")}, 16),
        ("Magmatic Generator", magmatic_generator(True), {**MAG_TEX, "front_on": t("magmatic_front_on"), "top_on": t("magmatic_top_on")}, 16),
        ("Adv. Solar Array", solar_array(), SOLAR_TEX, 16),
        ("RTG", rtg(), RTG_TEX, 16),
        ("Centrifuge", centrifuge(True), {**CEN_TEX, "panel_on": t("centrifuge_panel_on"), "tube_on": t("centrifuge_tube_on")}, 16),
        ("Electrolyzer", electrolyzer(True), {**ELY_TEX, "front_on": t("electrolyzer_front_on"), "electrode_on": t("electrolyzer_electrode_on")}, 16),
        ("Reactor Controller", controller(True), {"casing": t("reactor_casing"), "front_on": t("reactor_controller_front_on"),
                                                  "front": t("reactor_controller_front"), "red": t("reactor_port_redstone_detail")}, 16),
        ("Fuel Channel", fuel_channel(), REA_TEX, 16),
        ("Control Rod", control_rod(), REA_TEX, 16),
        ("Access Port", port("access"), {"casing": t("reactor_casing"), "face": t("reactor_port_access"), "detail": t("reactor_port_access_detail")}, 16),
        ("Power Port", port("power"), {"casing": t("reactor_casing"), "face": t("reactor_port_power"), "detail": t("reactor_port_power_detail")}, 16),
        ("Coolant Port", port("coolant"), {"casing": t("reactor_casing"), "face": t("reactor_port_coolant"), "detail": t("reactor_port_coolant_detail")}, 16),
        ("Redstone Port", port("redstone"), {"casing": t("reactor_casing"), "face": t("reactor_port_redstone"), "detail": t("reactor_port_redstone_detail")}, 16),
        ("Waste Barrel", waste_barrel(), REA_TEX, 16),
        ("Corium", corium(), {"corium": t("corium")}, 16),
        ("Turbine Mast", turbine_mast(), WIND_TEX, 16),
        ("Fusion Magnet", fusion_magnet(), FUS_TEX, 16),
        ("Fusion Port", fusion_port(), FUS_TEX, 16),
        ("Tokamak Core", tokamak_core(True), {**FUS_TEX, "sol_on": t("tokamak_core_on"), "sol_top_on": t("tokamak_core_top_on")}, 16),
        ("Plasma ring (x4)", scaled(plasma_ring("plasma", 1.2), 4) + scaled(plasma_ring("filament", 1.55), 4),
         {"plasma": t("plasma"), "filament": t("plasma_filament")}, 80),
    ]
    size, bg = 220, (32, 34, 42, 255)
    panels = []
    for label, els, tmap, extent in entries:
        q = S._quads(els, textures(tmap))
        m = S._mul(drill._rot("x", 28), drill._rot("y", 145 if extent < 80 else -20))
        cy = 8 if extent == 16 else 24 if extent == 48 else 8
        scale = 150 / max(16, extent) * (0.62 if extent == 48 else 1.0 if extent == 16 else 1.6)
        img = drill._render_mixed([(q, lambda p, m=m, cy=cy: S._app(m, [(p[0] - 8) / 16, (p[1] - cy) / 16, (p[2] - 8) / 16]))],
                                  lambda v, s=scale: (size / 2 + v[0] * s * 16 / 1.3, size / 2 + 10 - v[1] * s * 16 / 1.3),
                                  (size, size), bg)
        panels.append((label, img))
    cols = 6
    rows = (len(panels) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (size + 8) + 8, rows * (size + 24) + 8 + 330), (22, 22, 28, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(panels):
        x, y = 8 + (i % cols) * (size + 8), 8 + (i // cols) * (size + 24)
        sheet.alpha_composite(img, (x, y + 16))
        d.text((x + 4, y + 2), label, fill=(235, 235, 235, 255))
    tp = HERE.parent / "power_textures_preview.png"
    if tp.exists():
        tim = Image.open(tp).convert("RGBA")
        k = min(1.0, (sheet.width - 16) / tim.width)
        tim = tim.resize((int(tim.width * k), int(tim.height * k)))
        if tim.height > 322:
            tim = tim.resize((int(tim.width * 322 / tim.height), 322))
        sheet.alpha_composite(tim, (8, sheet.height - 330 + 4))
    sheet.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(HERE.parent / "power_preview.png")
    else:
        print(__doc__)

#!/usr/bin/env python3
"""Real fluids (Java: fluid/): fluid blocks and buckets, fluid pipes, tanks, the Pump, the Boiler,
Biogas Digester, Diesel Generator, Steam Turbine, the oil chain (Oil Derrick + Derrick Base,
Refinery + Refinery Tower, Plastic, Tar, Asphalt) and the Fuelling Port: models, blockstates, item
models, loot, recipes, oil worldgen, tags, advancements and lang (English + Spanish).

  Bronze      Fluid Pipes (4 stage colours, glass window), Bronze Fluid Tank
  Electric    Boiler (brick firebox, riveted drum, gauge, safety valve), Pump, Steel Fluid Tank
  Automation  Biogas Digester (concrete tank, green dome), Diesel Generator (yellow enclosure,
              radiator, exhaust stack), Oil Derrick (wellhead on a 3x3 platform; a pumpjack drawn
              by the block entity renderer at x3 scale), Refinery (base + 3-section column with flare)
  Industrial  Steam Turbine (rotor spun by the renderer), Titanium Fluid Tank
  Orbital     Fuelling Port

Models use the element DSL of satellites.py. Textures: fluids_textures.py.
Run `python3 tools/features/fluids.py --preview` to render tools/fluids_models_preview.png.
"""
import importlib.util
import json
import math
import sys
from pathlib import Path

MOD = "factoryascent"
HERE = Path(__file__).resolve().parent


def _load(name):
    key = f"fluids_dep_{name}"
    if key in sys.modules:
        return sys.modules[key]
    spec = importlib.util.spec_from_file_location(key, HERE / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[key] = mod
    spec.loader.exec_module(mod)
    return mod


S = _load("satellites")
cube, ring, disc, tube, spin, turn, euler, El = S.cube, S.ring, S.disc, S.tube, S.spin, S.turn, S.euler, S.El

LIQUIDS = ["crude_oil", "diesel", "rocket_fuel", "coolant"]
GASES = ["steam", "biogas", "deuterium", "tritium", "helium_3"]
PIPES = ["bronze_fluid_pipe", "steel_fluid_pipe", "aluminum_fluid_pipe", "titanium_fluid_pipe"]
TANKS = ["bronze_fluid_tank", "steel_fluid_tank", "titanium_fluid_tank"]
MACHINES = ["boiler", "pump", "biogas_digester", "diesel_generator", "oil_derrick", "refinery", "steam_turbine", "fuelling_port"]
PICKAXE_BLOCKS = PIPES + TANKS + MACHINES + ["derrick_base", "refinery_tower", "asphalt"]
# Not recipes, for tools/check_progression.py
PROGRESSION = [
    ("bucket an oil lake", ["crude_oil_bucket"], ["minecraft:bucket"], []),
    ("refinery", ["diesel_bucket", "rocket_fuel_bucket", "plastic", "tar"], ["crude_oil_bucket", "minecraft:bucket"],
     ["refinery", "refinery_tower"]),
] + [(f"pour out a {f} bucket", [f], [f"{f}_bucket"], []) for f in LIQUIDS]


def t(name):
    return f"{MOD}:block/{name}"


def model(els, tex, particle=None, render_type=None, ao=True):
    out = {"parent": "minecraft:block/block", **S.model(els, tex, particle=particle)}
    if render_type:
        out["render_type"] = render_type
    if not ao:
        out["ambientocclusion"] = False
    return out


def shift(els, dx, dy, dz):
    return [e.moved(dx, dy, dz) for e in els]


def z_axis(els, pivot):
    return turn(els, euler(x=90), pivot)


def x_axis(els, pivot):
    return turn(els, euler(z=90), pivot)


# ============================================================ pipes & tanks

def pipe_core():
    return [cube([4, 4, 4], [12, 12, 12], "t", u=4, v=4)]


def pipe_arm():
    # along -z (north), uv sampling the flanged duct texture
    return [El([4, 4, 0], [12, 12, 4], {
        "north": ("t", [4, 4, 12, 12]), "south": ("t", [4, 4, 12, 12]),
        "up": ("t", [4, 0, 12, 4]), "down": ("t", [4, 0, 12, 4]),
        "east": ("t", [4, 0, 12, 4]), "west": ("t", [4, 0, 12, 4])})]


def pipe_arm_json():
    r = {"from": [4, 4, 0], "to": [12, 12, 4], "faces": {
        "north": {"uv": [4, 4, 12, 12], "texture": "#t"}, "south": {"uv": [4, 4, 12, 12], "texture": "#t"},
        "up": {"uv": [4, 0, 12, 4], "texture": "#t"}, "down": {"uv": [4, 0, 12, 4], "texture": "#t"},
        "east": {"uv": [4, 0, 12, 4], "texture": "#t", "rotation": 90}, "west": {"uv": [4, 0, 12, 4], "texture": "#t", "rotation": 90}}}
    return r


def pipe_core_json():
    return {"from": [4, 4, 4], "to": [12, 12, 12], "faces": {d: {"uv": [4, 4, 12, 12], "texture": "#t"} for d in S.DIRS}}


def tank_elements(size):
    els = [cube([1, 0, 1], [15, 1.5, 15], {**{d: "frame" for d in S.DIRS}, "up": "lid", "down": "lid"}),
           cube([1, 14.5, 1], [15, 16, 15], {**{d: "frame" for d in S.DIRS}, "up": "lid", "down": "lid"})]
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        els.append(cube([x, 1.5, z], [x + 2, 14.5, z + 2], "frame"))
    # glass panes (inner faces too, so the fluid shows from every side)
    els.append(cube([3, 1.5, 1.4], [13, 14.5, 1.6], {"north": "glass", "south": "glass"}, u=3, v=1.5))
    els.append(cube([3, 1.5, 14.4], [13, 14.5, 14.6], {"north": "glass", "south": "glass"}, u=3, v=1.5))
    els.append(cube([1.4, 1.5, 3], [1.6, 14.5, 13], {"east": "glass", "west": "glass"}, u=3, v=1.5))
    els.append(cube([14.4, 1.5, 3], [14.6, 14.5, 13], {"east": "glass", "west": "glass"}, u=3, v=1.5))
    if size in ("steel", "titanium"):          # a reinforcing band round the middle
        for y in ((7, 8.5),) if size == "steel" else ((5, 6), (10, 11)):
            els.append(cube([0.8, y[0], 0.8], [15.2, y[1], 1.4], "frame"))
            els.append(cube([0.8, y[0], 14.6], [15.2, y[1], 15.2], "frame"))
            els.append(cube([0.8, y[0], 1.4], [1.4, y[1], 14.6], "frame"))
            els.append(cube([14.6, y[0], 1.4], [15.2, y[1], 14.6], "frame"))
    if size == "bronze":                       # a filler cap on the lid
        els += tube(8, 8, 16, 17, 1.6, "frame")
        els += disc(8, 8, 17.2, 1.8, "lid")
    else:                                      # inlet valve with a hand wheel
        els += tube(11, 11, 16, 17.5, 1.2, "frame")
        els += ring(11, 11, 17.5, 18, 2.4, 2.4, "lid", th=0.6)
    return els


# ============================================================ machines

def boiler(on):
    fb = "firebox_on" if on else "firebox"
    els = [cube([0, 0, 0], [16, 7, 16], {**{d: "brick" for d in S.DIRS}, "north": fb, "up": "drum", "down": "drum"})]
    drum = tube(8, 8, 1, 15, 4.8, "drum", n=10)
    drum += disc(8, 8, 1.4, 4.6, "end", up=False, n=10)
    drum += disc(8, 8, 15, 4.6, "end", down=False, n=10)
    els += [e.moved(0, 3.5, 0) for e in z_axis(drum, [8, 8, 8])]
    # safety valve and steam dome on top, outlet at the back
    els += tube(5, 9, 15.5, 18, 0.9, "pipe")
    els.append(cube([3, 18, 7], [7, 18.8, 11], {**{d: "pipe" for d in S.DIRS}, "up": "valve"}, u=0))
    els += tube(11, 8, 15.2, 17.2, 2.0, "drum")
    els += disc(11, 8, 17.5, 2.1, "pipe")
    els.append(cube([10, 9, 15], [13, 12, 16.4], "pipe"))
    return els


def pump(on):
    els = [cube([1, 0, 1], [15, 2, 15], {**{d: "pipe" for d in S.DIRS}, "up": "top"})]
    els.append(cube([3, 2, 3], [13, 10, 13], {**{d: "body_on" if on else "body" for d in S.DIRS}, "up": "top", "down": "top"}))
    els += tube(8, 8, 10, 15, 3.6, "motor", n=10)
    els += disc(8, 8, 15.3, 3.4, "top", n=10)
    for dx, dz in ((0, -6), (6, 0), (0, 6), (-6, 0)):   # outlet flanges on the four sides
        x, z = 8 + dx, 8 + dz
        els.append(cube([x - 2, 4, z - 2], [x + 2, 8, z + 2], "pipe"))
    els += tube(8, 8, -0.01, 0.6, 2.4, "pipe")          # suction pipe into the pool
    return els


def biogas_digester(on):
    els = tube(8, 8, 0, 10, 7.4, "tank", n=12)
    els += disc(8, 8, 0.2, 7.2, "tank", up=False, n=12)
    els += ring(8, 8, 10, 13.5, 7.4, 5.2, "dome", n=12)
    els += ring(8, 8, 13.5, 15, 5.2, 2.6, "dome", n=12)
    els += disc(8, 8, 15, 2.6, "dome", n=12)
    els.append(cube([4, 2, 0.2], [12, 9, 1.2], {"north": "hatch_on" if on else "hatch", "east": "pipe", "west": "pipe",
                                                  "up": "pipe", "down": "pipe"}, u=4, v=2))
    els += tube(11.5, 8, 14, 17, 0.9, "pipe")            # gas take-off
    els.append(cube([10.5, 16, 7], [14.5, 17, 9], "pipe"))
    els.append(cube([13.6, 12, 6.5], [15.2, 17, 9.5], "pipe"))
    return els


def diesel_generator(on):
    els = [cube([0, 0, 0], [16, 1.5, 16], "pipe")]
    els.append(cube([1, 1.5, 1], [15, 11, 15], {**{d: "panel" for d in S.DIRS}, "north": "front_on" if on else "front", "up": "top"}))
    els.append(cube([4, 11, 4], [12, 13, 9], {**{d: "pipe" for d in S.DIRS}, "up": "top"}))   # air filter
    els += tube(12, 12, 11, 17, 1.5, "exhaust", n=8)
    els += ring(12, 12, 17, 17.6, 1.9, 1.9, "pipe", th=0.5)
    els.append(cube([15, 3, 4], [16, 6, 12], "pipe"))      # fuel inlet rail
    return els


def steam_turbine():
    els = [cube([0, 0, 2], [16, 16, 16], {**{d: "casing" for d in S.DIRS}, "up": "top", "down": "top"})]
    # front bezel round the rotor opening
    for a, b in (([0, 0, 0], [16, 2, 2]), ([0, 14, 0], [16, 16, 2]), ([0, 2, 0], [2, 14, 2]), ([14, 2, 0], [16, 14, 2])):
        els.append(cube(a, b, {**{d: "casing" for d in S.DIRS}, "north": "front"}, u=a[0], v=16 - b[1]))
    els.append(cube([2, 2, 1.8], [14, 14, 2], {"north": "front"}, u=2, v=2))
    # steam inlet on top, exhaust trunk at the back
    els += tube(8, 9, 16, 17.5, 2.6, "pipe")
    els.append(cube([3, 3, 15.9], [13, 13, 16.6], "pipe"))
    return els


def turbine_rotor():
    """Turns about z through (8, 8): eight twisted blades in front of the casing."""
    els = []
    for k in range(8):
        blade = cube([7.4, 8, 0.6], [8.6, 13.6, 1.4], "blade")
        blade = turn([blade], euler(y=18), [8, 8, 1])
        els += spin(blade, k * 45, [8, 8, 1], axis="z")
    els.append(cube([6, 6, 0.2], [10, 10, 1.6], "hub"))
    return els


def wellhead(on):
    els = [cube([0, 0, 0], [16, 4, 16], {**{d: "base_side" for d in S.DIRS}, "up": "base"})]
    els += tube(8, 8, 4, 11, 3, "steel", n=8)
    els += ring(8, 8, 7, 8.5, 4.2, 4.2, "paint", th=1.2, n=8)
    els += disc(8, 8, 11.4, 3.2, "wellhead_on" if on else "wellhead")
    for x in (3, 13):                                     # side valves with hand wheels
        els.append(cube([min(x, 5), 6.5, 7], [max(x, 11), 8.5, 9], "steel"))
    els += turn(ring(8, 2.4, 6, 6.6, 2, 2, "weight", th=0.5), euler(z=90), [2.4, 6.3, 8])
    els += turn(ring(8, 13.6, 6, 6.6, 2, 2, "weight", th=0.5), euler(z=90), [13.6, 6.3, 8])
    return els


# Pumpjack (1/3 scale over the 3x3 platform). True px relative to the controller, facing north:
# beam pivot (y 60, z 34), crank axle (y 34, z 42), horse head over the wellhead (z ~5).
def tp(x, y, z):
    """True px -> model px of the x3-scaled parts."""
    return [(x + 16) / 3, (y - 16) / 3, (z + 16) / 3]


def tbox(a, b, tex, **kw):
    return cube(tp(*a), tp(*b), tex, **kw)


def pumpjack_frame():
    els = []
    # skid: two rails along z and cross ties
    for x in (-8, 20):
        els.append(tbox([x, 16, -4], [x + 4, 20, 50], "steel"))
    for z in (-2, 20, 46):
        els.append(tbox([-8, 16, z], [24, 19, z + 3], "steel"))
    # samson post: two legs up to the saddle bearing, braced towards the head
    for x in (-2, 14):
        els.append(tbox([x, 19, 32], [x + 4, 58, 36], "paint"))
    els.append(tbox([-2, 56, 31], [18, 60, 37], "paint"))      # saddle
    for x in (-2, 14):   # from the skid (z 16) up to the post (z 32, y 54): 38.5 px, leaning 24.6 degrees
        brace = tbox([x + 0.5, 36.5 - 19.25, 22.5], [x + 3.5, 36.5 + 19.25, 25.5], "paint")
        els += turn([brace], euler(x=24.6), tp(x + 2, 36.5, 24))
    # gearbox and motor next to the crank
    els.append(tbox([2, 19, 37], [14, 36, 48], "paint"))
    els.append(tbox([4, 19, 48], [12, 28, 56], "steel"))
    els.append(tbox([6, 28, 49], [10, 31, 55], "weight"))
    return els


def pumpjack_beam():
    els = [tbox([5, 57, 8], [11, 63, 46], "paint")]           # walking beam
    els.append(tbox([6, 63, 32], [10, 65, 36], "steel"))      # bearing cap
    # horse head: a curved plate over the wellhead
    for k in range(7):
        z = 1 + k
        bulge = abs(3 - k)
        els.append(tbox([4, 40 + bulge * 1.4, z], [12, 68 - bulge * 1.2, z + 1.2], "paint"))
    els.append(tbox([4.5, 39, 0.6], [11.5, 68, 1.4], "steel"))   # bridle face
    # equalizer at the tail and the pitman arms down to the crank pins
    els.append(tbox([-4, 55, 42], [20, 59, 46], "steel"))
    for x in (-4, 18):
        els.append(tbox([x, 36, 43], [x + 2, 57, 45], "steel"))
    return els


def pumpjack_crank():
    els = [tbox([-6, 32, 40], [22, 36, 44], "steel")]          # crank shaft (axle at y 34, z 42)
    for x in (-6, 18):
        els.append(tbox([x, 25, 39], [x + 4, 36, 45], "paint"))   # crank arm (down)
        els.append(tbox([x - 1, 20.5, 35], [x + 5, 26.5, 49], "weight"))  # counterweight
    return els


def pumpjack_rod():
    els = [tbox([7, 11, 4], [9, 41, 6], "steel")]               # polished rod into the wellhead
    els.append(tbox([5.5, 40, 2.5], [10.5, 41.5, 7.5], "weight"))  # carrier bar
    return els


def refinery(on):
    els = [cube([0, 0, 0], [16, 16, 16], {**{d: "side" for d in S.DIRS}, "north": "base_on" if on else "base", "up": "top"})]
    els.append(cube([2, 15.9, 2], [14, 16.4, 14], {**{d: "steel" for d in S.DIRS}, "up": "top"}))
    return els


def refinery_tower(top):
    els = tube(8, 8, 0, 16, 5.6, "tower", n=12)
    for y in (0, 8):
        els += ring(8, 8, y, y + 1.2, 6.4, 6.4, "steel", n=12, th=0.8)
    els.append(cube([13, 5, 7], [16, 7, 9], "steel"))         # side draw-off pipe
    els.append(cube([0, 11, 7], [3, 13, 9], "steel"))
    if top:
        els += ring(8, 8, 15.99, 17.5, 5.6, 3, "tower", n=12)
        els += disc(8, 8, 17.5, 3, "tower_top", n=12)
        els += tube(10.5, 9, 17, 22, 0.8, "steel")
        flame = [cube([9.5, 22, 8.9], [11.5, 26, 9.1], {"north": "flare", "south": "flare"}, u=4, v=0),
                 cube([10.4, 22, 8], [10.6, 26, 10], {"east": "flare", "west": "flare"}, u=4, v=0)]
        for e in flame:
            e.emissive = True
        els += flame
    return els


def fuelling_port():
    els = [cube([2, 0, 2], [14, 13, 14], {**{d: "body" for d in S.DIRS}, "up": "top", "down": "top"})]
    els.append(cube([1, 0, 1], [15, 1.5, 15], "nozzle"))
    # hose coiled on the side and the nozzle in its holster
    els += turn(ring(8, 14.4, 4, 5, 3.5, 3.5, "hose", th=1, n=10), euler(x=90), [8, 8, 14.4])
    els.append(cube([6.5, 6, 14], [9.5, 10, 16], "nozzle"))
    els.append(cube([7.3, 9.5, 15.2], [8.7, 13, 16.2], "hose"))
    els += tube(8, 8, 13, 15, 2, "nozzle")
    return els


# ============================================================ writing

SIDES = ("north", "east", "south", "west")
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
FACING6 = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}

BOILER_TEX = {"brick": t("boiler_brick"), "firebox": t("boiler_firebox"), "firebox_on": t("boiler_firebox_on"),
              "drum": t("boiler_drum"), "end": t("boiler_end"), "valve": t("boiler_valve"), "pipe": t("fluid_steel_pipe")}
PUMP_TEX = {"body": t("pump_body"), "body_on": t("pump_body_on"), "motor": t("pump_motor"), "top": t("pump_top"), "pipe": t("fluid_steel_pipe")}
DIGEST_TEX = {"tank": t("digester_tank"), "dome": t("digester_dome"), "hatch": t("digester_hatch"), "hatch_on": t("digester_hatch_on"),
              "pipe": t("fluid_steel_pipe")}
DIESEL_TEX = {"panel": t("diesel_panel"), "front": t("diesel_front"), "front_on": t("diesel_front_on"), "top": t("diesel_top"),
              "exhaust": t("diesel_exhaust"), "pipe": t("fluid_steel_pipe")}
TURBINE_TEX = {"casing": t("turbine_casing"), "front": t("turbine_front"), "top": t("turbine_top"), "pipe": t("fluid_steel_pipe"),
               "blade": t("turbine_rotor_blade"), "hub": t("turbine_rotor_hub")}
DERRICK_TEX = {"base": t("derrick_base"), "base_side": t("derrick_base_side"), "steel": t("pumpjack_steel"), "paint": t("pumpjack_paint"),
               "weight": t("pumpjack_weight"), "wellhead": t("wellhead"), "wellhead_on": t("wellhead_on")}
REFINERY_TEX = {"base": t("refinery_base"), "base_on": t("refinery_base_on"), "side": t("refinery_side"), "top": t("refinery_tower_top"),
                "steel": t("pumpjack_steel"), "tower": t("refinery_tower"), "tower_top": t("refinery_tower_top"), "flare": t("refinery_flare")}
FUEL_TEX = {"body": t("fuelling_port_body"), "top": t("fuelling_port_top"), "hose": t("fuelling_hose"), "nozzle": t("fuelling_nozzle")}

MACHINE_MODELS = {
    "boiler": (boiler, BOILER_TEX, "boiler_drum", True),
    "pump": (pump, PUMP_TEX, "pump_body", True),
    "biogas_digester": (biogas_digester, DIGEST_TEX, "digester_tank", True),
    "diesel_generator": (diesel_generator, DIESEL_TEX, "diesel_panel", True),
    "oil_derrick": (wellhead, DERRICK_TEX, "derrick_base_side", True),
    "refinery": (refinery, REFINERY_TEX, "refinery_side", True),
    "steam_turbine": (lambda on: steam_turbine(), TURBINE_TEX, "turbine_casing", False),
    "fuelling_port": (lambda on: fuelling_port(), FUEL_TEX, "fuelling_port_body", False),
}


def item_model(ctx, name, parent, extra=None):
    ctx.write(ctx.ASSETS / "models" / "item" / f"{name}.json", {"parent": f"{MOD}:{parent}", **(extra or {})})
    ctx.item_def(name, f"item/{name}")


def blocks(ctx):
    bm = ctx.block_model
    # ---- fluid blocks (rendered by the fluid renderer; the model only gives the particle)
    for fid in LIQUIDS:
        bm(fid, {"textures": {"particle": t(f"fluid/{fid}_still")}})
        ctx.write(ctx.ASSETS / "blockstates" / f"{fid}.json", {"variants": {"": {"model": f"{MOD}:block/{fid}"}}})
    # ---- pipes
    ext = t("fluid_pipe_extract")
    bm("fluid_pipe_extract", {"parent": "minecraft:block/block", "textures": {"t": ext, "particle": ext}, "elements": [
        {"from": [2, 2, 0], "to": [14, 14, 2], "faces": {
            "north": {"uv": [2, 2, 14, 14], "texture": "#t"}, "south": {"uv": [2, 2, 14, 14], "texture": "#t"},
            "up": {"uv": [2, 0, 14, 2], "texture": "#t"}, "down": {"uv": [2, 0, 14, 2], "texture": "#t"},
            "east": {"uv": [0, 2, 2, 14], "texture": "#t"}, "west": {"uv": [0, 2, 2, 14], "texture": "#t"}}}]})
    for name in PIPES:
        tx = t(name)
        bm(f"{name}_core", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                            "textures": {"t": tx, "particle": tx}, "elements": [pipe_core_json()]})
        bm(f"{name}_arm", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                           "textures": {"t": tx, "particle": tx}, "elements": [pipe_arm_json()]})
        arm2 = pipe_arm_json()
        arm2["from"], arm2["to"] = [4, 4, 12], [12, 12, 16]
        ctx.write(ctx.ASSETS / "models" / "item" / f"{name}.json", {
            "parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": {"t": tx, "particle": tx},
            "elements": [pipe_core_json(), pipe_arm_json(), arm2],
            "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
        ctx.item_def(name, f"item/{name}")
        parts = [{"apply": {"model": f"{MOD}:block/{name}_core"}}]
        for d in SIDES + ("up", "down"):
            parts.append({"when": {d: "connected|extract"}, "apply": {"model": f"{MOD}:block/{name}_arm", **FACING6[d]}})
            parts.append({"when": {d: "extract"}, "apply": {"model": f"{MOD}:block/fluid_pipe_extract", **FACING6[d]}})
        ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"multipart": parts})
    # ---- tanks
    for name in TANKS:
        size = name.split("_")[0]
        tex = {"frame": t(f"{size}_fluid_tank_frame"), "glass": t(f"{size}_fluid_tank_glass"), "lid": t(f"{size}_fluid_tank_lid")}
        bm(name, model(tank_elements(size), tex, particle=tex["frame"], render_type="minecraft:translucent"))
        ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        item_model(ctx, name, f"block/{name}")
    # ---- machines
    for name, (fn, tex, particle, has_on) in MACHINE_MODELS.items():
        bm(name, model(fn(False), tex, particle=t(particle)))
        if has_on:
            bm(f"{name}_on", model(fn(True), tex, particle=t(particle)))
        ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": {
            f"active={str(a).lower()},facing={f},formed={str(fo).lower()}": {
                "model": f"{MOD}:block/{name}{'_on' if a and has_on else ''}", **({"y": ROT[f]} if ROT[f] else {})}
            for f in SIDES for a in (False, True) for fo in (False, True)}})
        item_model(ctx, name, f"block/{name}")
    # moving parts (standalone models drawn by MachinePartRenderer)
    bm("steam_turbine_rotor", model(turbine_rotor(), TURBINE_TEX, particle=t("turbine_rotor_blade")))
    bm("oil_derrick_frame", model(pumpjack_frame(), DERRICK_TEX, particle=t("pumpjack_paint")))
    bm("oil_derrick_beam", model(pumpjack_beam(), DERRICK_TEX, particle=t("pumpjack_paint")))
    bm("oil_derrick_crank", model(pumpjack_crank(), DERRICK_TEX, particle=t("pumpjack_paint")))
    bm("oil_derrick_rod", model(pumpjack_rod(), DERRICK_TEX, particle=t("pumpjack_steel")))
    # ---- multiblock parts and the road
    bm("derrick_base", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": t("derrick_base"), "bottom": t("derrick_base_side"), "side": t("derrick_base_side")}})
    ctx.write(ctx.ASSETS / "blockstates" / "derrick_base.json", {"variants": {"": {"model": f"{MOD}:block/derrick_base"}}})
    item_model(ctx, "derrick_base", "block/derrick_base")
    bm("refinery_tower", model(refinery_tower(False), REFINERY_TEX, particle=t("refinery_tower")))
    bm("refinery_tower_top", model(refinery_tower(True), REFINERY_TEX, particle=t("refinery_tower")))
    ctx.write(ctx.ASSETS / "blockstates" / "refinery_tower.json", {"variants": {
        "top=false": {"model": f"{MOD}:block/refinery_tower"}, "top=true": {"model": f"{MOD}:block/refinery_tower_top"}}})
    item_model(ctx, "refinery_tower", "block/refinery_tower_top")
    bm("asphalt", {"parent": "minecraft:block/cube_all", "textures": {"all": t("asphalt")}})
    ctx.write(ctx.ASSETS / "blockstates" / "asphalt.json", {"variants": {"": {"model": f"{MOD}:block/asphalt"}}})
    item_model(ctx, "asphalt", "block/asphalt")
    # ---- loot
    for name in PIPES + MACHINES + ["derrick_base", "refinery_tower", "asphalt"]:
        ctx.loot_self(name)
    for name in TANKS:   # tanks keep what they hold
        ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}", "functions": [
                {"function": "minecraft:copy_components", "source": "block_entity",
                 "include": [f"{MOD}:tank_contents", "minecraft:custom_name"]}]}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{MOD}:blocks/{name}"})


def items(ctx):
    for fid in LIQUIDS:
        ctx.flat_item(f"{fid}_bucket")
    ctx.flat_item("plastic")
    ctx.flat_item("tar")


# ============================================================ data

def worldgen(ctx):
    wg = ctx.DATA / MOD / "worldgen"
    oil = {"type": "minecraft:simple_state_provider", "state": {"Name": f"{MOD}:crude_oil", "Properties": {"level": "0"}}}

    def lake(name, barrier):
        ctx.write(wg / "configured_feature" / f"{name}.json", {"type": "minecraft:lake", "config": {
            "barrier": {"type": "minecraft:simple_state_provider", "state": {"Name": barrier}},
            "can_place_feature": {"type": "minecraft:true"},
            "can_replace_with_air_or_fluid": {"type": "minecraft:not", "predicate": {
                "type": "minecraft:matching_block_tag", "tag": "minecraft:features_cannot_replace"}},
            "can_replace_with_barrier": {"type": "minecraft:not", "predicate": {
                "type": "minecraft:matching_block_tag", "tag": "minecraft:lava_pool_stone_cannot_replace"}},
            "fluid": oil}})
    lake("oil_lake", "minecraft:coarse_dirt")
    lake("oil_pocket", "minecraft:stone")
    # tar pits on the surface of dry biomes
    ctx.write(wg / "placed_feature" / "oil_lake_surface.json", {"feature": f"{MOD}:oil_lake", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 24}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    # oil pockets deep underground, everywhere in the Overworld
    ctx.write(wg / "placed_feature" / "oil_pocket_underground.json", {"feature": f"{MOD}:oil_pocket", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 5}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -52},
                                                      "max_inclusive": {"absolute": 24}}},
        {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 32, "target_condition": {
            "type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
                {"type": "minecraft:inside_world_bounds", "offset": [0, -5, 0]}]}},
        {"type": "minecraft:surface_relative_threshold_filter", "heightmap": "OCEAN_FLOOR_WG", "max_inclusive": -12},
        {"type": "minecraft:biome"}]})
    BM = ctx.DATA / MOD / "neoforge" / "biome_modifier"
    ctx.write(BM / "oil_lakes.json", {"type": "neoforge:add_features", "biomes": [
        "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
        "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:swamp"],
        "features": f"{MOD}:oil_lake_surface", "step": "lakes"})
    ctx.write(BM / "oil_pockets.json", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
                                        "features": f"{MOD}:oil_pocket_underground", "step": "lakes"})


def tags(ctx):
    T = ctx.add_tag
    T("minecraft", "block", "needs_stone_tool", ["asphalt"])
    for fid in LIQUIDS:
        pass
    # fluid tags: our liquids behave like their own fluids (not water/lava); buckets in c:buckets
    ctx.write(ctx.DATA / MOD / "tags" / "fluid" / "crude_oil.json", {"values": [f"{MOD}:crude_oil", f"{MOD}:flowing_crude_oil"]})
    for fid in LIQUIDS:
        ctx.write(ctx.DATA / "c" / "tags" / "item" / "buckets" / f"{fid}.json", {"replace": False, "values": [f"{MOD}:{fid}_bucket"]})
    ctx.write(ctx.DATA / "c" / "tags" / "item" / "plastics.json", {"replace": False, "values": [f"{MOD}:plastic"]})
    T("c", "item", "buckets", [f"#c:buckets/{fid}" for fid in LIQUIDS])


# ============================================================ recipes

def recipes(ctx):
    SH, SL = ctx.shaped, ctx.shapeless
    BP, SP, AP, TP = "#c:plates/bronze", "#c:plates/steel", "#c:plates/aluminum", "#c:plates/titanium"
    AC, AM, M, CB = "advanced_circuit", "advanced_machine_frame", "machine_frame", "basic_circuit"
    # ---- logistics: pipes in the four stages (8 each), tanks
    SH("bronze_fluid_pipe", ["PCP"], {"P": BP, "C": "minecraft:clay_ball"}, "bronze_fluid_pipe", 8)
    up = {"steel_fluid_pipe": ("bronze_fluid_pipe", "#c:ingots/steel"),
          "aluminum_fluid_pipe": ("steel_fluid_pipe", "#c:ingots/aluminum"),
          "titanium_fluid_pipe": ("aluminum_fluid_pipe", "#c:ingots/titanium")}
    for cur, (prev, x) in up.items():
        SH(cur, ["CCC", "CXC", "CCC"], {"C": prev, "X": x}, cur, 8)
    SH("bronze_fluid_tank", ["PGP", "G G", "PGP"], {"P": BP, "G": "#c:glass_blocks/colorless"}, "bronze_fluid_tank")
    SH("steel_fluid_tank", ["PGP", "GTG", "PGP"], {"P": SP, "G": "#c:glass_blocks/colorless", "T": "bronze_fluid_tank"}, "steel_fluid_tank")
    SH("titanium_fluid_tank", ["PGP", "GTG", "PGP"], {"P": TP, "G": "#c:glass_blocks/colorless", "T": "steel_fluid_tank"}, "titanium_fluid_tank")
    SH("pump", ["PMP", "CBC", "PIP"], {"P": SP, "M": "motor", "C": CB, "B": "minecraft:bucket", "I": "bronze_fluid_pipe"}, "pump")
    # ---- power
    SH("boiler", ["CCC", "CFC", "BMB"], {"C": "#c:plates/copper", "F": "minecraft:furnace", "B": "minecraft:bricks", "M": M}, "boiler")
    SH("biogas_digester", ["SBS", "SMS", "SPS"], {"S": "minecraft:smooth_stone", "B": "minecraft:composter", "M": M,
                                                  "P": "bronze_fluid_pipe"}, "biogas_digester")
    SH("diesel_generator", ["PXP", "MFM", "PCP"], {"P": AP, "X": "minecraft:piston", "M": "motor", "F": AM, "C": AC}, "diesel_generator")
    SH("steam_turbine", ["TPT", "RAR", "TCT"], {"T": TP, "P": "steel_fluid_pipe", "R": "motor", "A": AM, "C": AC}, "steam_turbine")
    # ---- oil
    SH("oil_derrick", ["SRS", "PMP", "SCS"], {"S": SP, "R": "#c:rods/steel", "P": "steel_fluid_pipe", "M": "motor", "C": AC}, "oil_derrick")
    SH("derrick_base", ["SRS", "R R", "SRS"], {"S": SP, "R": "#c:rods/steel"}, "derrick_base", 4)
    SH("refinery", ["PFP", "HMH", "PCP"], {"P": AP, "F": "minecraft:blast_furnace", "H": "heating_coil", "M": AM, "C": AC}, "refinery")
    SH("refinery_tower", ["PGP", "P P", "PGP"], {"P": SP, "G": "steel_fluid_pipe"}, "refinery_tower")
    SL("asphalt", ["tar", "minecraft:gravel", "minecraft:gravel", "#c:sands"], "asphalt", 4)
    SL("coolant_bucket", ["minecraft:bucket", "minecraft:packed_ice", "minecraft:packed_ice", "minecraft:lapis_lazuli"], "coolant_bucket")
    SL("rocket_fuel_from_bucket", ["rocket_fuel_bucket"], "rocket_fuel", 4)
    SH("fuelling_port", ["TRT", "HAH", "TCT"], {"T": TP, "R": "rocket_fuel", "H": "aluminum_fluid_pipe", "A": AM, "C": AC}, "fuelling_port")


# ============================================================ advancements

def code_advancement(ctx, key, parent, icon, frame, en, es):
    display = {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}", "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": display, "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def advancements(ctx):
    A = ctx.advancement
    A("bronze_fluids", "bronze_logistics", "bronze_fluid_pipe", ["bronze_fluid_pipe", "bronze_fluid_tank"], "Plumber's Apprentice",
      "Make Bronze Fluid Pipes or a Bronze Fluid Tank. Wrench a pipe face to pump out of a tank",
      "Aprendiz de fontanero", "Haz tuberías de fluidos de bronce o un tanque. Usa la llave en una cara para bombear de un tanque")
    A("electric_boiler", "electric_steam", "boiler", ["boiler"], "Let Off Some Steam",
      "Build a Boiler and pipe its steam into Steam Engines", "Soltar vapor",
      "Construye una caldera y lleva su vapor por tubería a motores de vapor")
    A("electric_pump", "electric_power", "pump", ["pump"], "Drain the Lake",
      "Build a Pump and set it over a pool", "Vaciar el lago", "Construye una bomba y ponla sobre un estanque")
    A("automation_digester", "automation_biogas", "biogas_digester", ["biogas_digester"], "Gut Feeling",
      "Build a Biogas Digester and pipe its gas to Biogas Generators", "Corazonada",
      "Construye un digestor de biogás y lleva su gas por tubería a generadores de biogás")
    A("automation_oil", "age_automation", "crude_oil_bucket", ["crude_oil_bucket"], "Black Gold",
      "Bucket some crude oil from a tar pit in a dry biome (or an oil pocket deep down)", "Oro negro",
      "Llena un cubo de crudo en un pozo de alquitrán de un bioma seco (o en una bolsa de petróleo profunda)")
    code_advancement(ctx, "automation_derrick", "automation_oil", "oil_derrick", "goal",
                     ("Nodding Donkey", "Form an Oil Derrick (controller in a 3×3 of Derrick Bases) over an oil pocket"),
                     ("Burro cabeceador", "Forma una torre petrolera (controlador en un 3×3 de bases) sobre una bolsa de petróleo"))
    code_advancement(ctx, "automation_refinery", "automation_derrick", "refinery", "goal",
                     ("Cracking Up", "Form a Refinery: the controller with three Refinery Tower sections on top"),
                     ("Craqueo", "Forma una refinería: el controlador con tres secciones de torre encima"))
    A("automation_diesel", "automation_refinery", "diesel_generator", ["diesel_generator"], "Diesel Power",
      "Build a Diesel Generator and feed it diesel", "Potencia diésel", "Construye un generador diésel y aliméntalo con diésel")
    A("automation_plastic", "automation_refinery", "plastic", ["plastic"], "Fantastic Plastic",
      "Refine crude oil into plastic", "Plástico fantástico", "Refina crudo en plástico")
    A("automation_asphalt", "automation_refinery", "asphalt", ["asphalt"], "Highway to Hell",
      "Lay asphalt: tar, gravel and sand make a fast road", "Autopista al infierno",
      "Pon asfalto: alquitrán, grava y arena hacen una carretera rápida")
    A("industrial_turbine", "industrial_reactor", "steam_turbine", ["steam_turbine"], "Spin Cycle",
      "Build a Steam Turbine and drive it with a reactor's Coolant Port", "Centrifugado",
      "Construye una turbina de vapor y muévela con el puerto de refrigerante de un reactor")
    A("orbital_fuelling", "age_orbital", "fuelling_port", ["fuelling_port"], "Fill 'Er Up",
      "Build a Fuelling Port next to a Launch Controller or a shuttle pad", "Lleno, por favor",
      "Construye un puerto de repostaje junto a un controlador de lanzamiento o una plataforma de lanzadera")


# ============================================================ lang

FLUID_NAMES = {
    "crude_oil": ("Crude Oil", "Petróleo crudo"), "diesel": ("Diesel", "Diésel"), "rocket_fuel": ("Rocket Fuel", "Combustible de cohete"),
    "coolant": ("Coolant", "Refrigerante"), "steam": ("Steam", "Vapor"), "biogas": ("Biogas", "Biogás"),
    "deuterium": ("Deuterium", "Deuterio"), "tritium": ("Tritium", "Tritio"), "helium_3": ("Helium-3", "Helio-3"),
}
BUCKET_ES = {"crude_oil": "Cubo de petróleo crudo", "diesel": "Cubo de diésel", "rocket_fuel": "Cubo de combustible de cohete",
             "coolant": "Cubo de refrigerante"}
BLOCK_NAMES = {
    "bronze_fluid_pipe": ("Bronze Fluid Pipe", "Tubería de fluidos de bronce"),
    "steel_fluid_pipe": ("Steel Fluid Pipe", "Tubería de fluidos de acero"),
    "aluminum_fluid_pipe": ("Aluminium Fluid Pipe", "Tubería de fluidos de aluminio"),
    "titanium_fluid_pipe": ("Titanium Fluid Pipe", "Tubería de fluidos de titanio"),
    "bronze_fluid_tank": ("Bronze Fluid Tank", "Tanque de fluidos de bronce"),
    "steel_fluid_tank": ("Steel Fluid Tank", "Tanque de fluidos de acero"),
    "titanium_fluid_tank": ("Titanium Fluid Tank", "Tanque de fluidos de titanio"),
    "boiler": ("Boiler", "Caldera"), "pump": ("Pump", "Bomba"), "biogas_digester": ("Biogas Digester", "Digestor de biogás"),
    "diesel_generator": ("Diesel Generator", "Generador diésel"), "oil_derrick": ("Oil Derrick", "Torre petrolera"),
    "derrick_base": ("Derrick Base", "Base de torre petrolera"), "refinery": ("Refinery", "Refinería"),
    "refinery_tower": ("Refinery Tower", "Torre de refinería"), "steam_turbine": ("Steam Turbine", "Turbina de vapor"),
    "fuelling_port": ("Fuelling Port", "Puerto de repostaje"), "asphalt": ("Asphalt", "Asfalto"),
}
DESC = {
    "boiler": ("Burns furnace fuel and boils water (pipes, buckets or water touching it) into steam for Steam Engines and Turbines.",
               "Quema combustible de horno y hierve agua (tuberías, cubos o agua que la toque) en vapor para motores y turbinas de vapor."),
    "pump": ("Set it over a pool: pumps the farthest source blocks of the fluid under it first. Water sources stay.",
             "Ponla sobre un estanque: bombea primero las fuentes más lejanas del fluido de abajo. Las fuentes de agua quedan."),
    "biogas_digester": ("Rots organic items into biogas, piped out to Biogas Generators. Needs no power.",
                        "Pudre objetos orgánicos en biogás, que sale por tubería a los generadores de biogás. No usa energía."),
    "diesel_generator": ("Burns diesel from pipes or buckets: steady power. Needs air.",
                         "Quema diésel de tuberías o cubos: energía constante. Necesita aire."),
    "oil_derrick": ("Pumpjack. Build it in the middle of a 3×3 of Derrick Bases: it drills straight down to the oil pocket under it. Pipes on the bases take the oil.",
                    "Bombeo de varilla. Constrúyelo en medio de un 3×3 de bases: perfora hacia abajo hasta la bolsa de petróleo. Las tuberías en las bases sacan el crudo."),
    "refinery": ("Stack three Refinery Towers on it: splits crude oil into diesel, rocket fuel, plastic and tar.",
                 "Apila tres torres de refinería encima: separa el crudo en diésel, combustible de cohete, plástico y alquitrán."),
    "steam_turbine": ("Turns a reactor's (or many boilers') steam into a lot of FE once its rotor is spun up.",
                      "Convierte el vapor de un reactor (o de muchas calderas) en mucha FE cuando el rotor está lanzado."),
    "fuelling_port": ("Liquid rocket fuel in; fuels a Launch Controller next to it, and Shuttles docked at a pad it touches.",
                      "Entra combustible líquido; reposta un controlador de lanzamiento contiguo y las lanzaderas atracadas en una plataforma que toque."),
    "fluid_pipe": ("Carries fluids and gases. Wrench (or the pipe screen) sets a face to pump out of the tank there.",
                   "Lleva fluidos y gases. La llave (o la pantalla de la tubería) hace que una cara bombee del tanque de al lado."),
    "fluid_tank": ("Holds one fluid; keeps it when broken. Liquids settle into a tank below, gases rise into one above.",
                   "Guarda un fluido; lo conserva al romperse. Los líquidos bajan a un tanque inferior, los gases suben a uno superior."),
    "refinery_tower": ("A section of the Refinery's column: stack three on the Refinery. Pipes on it reach the Refinery's tanks.",
                       "Sección de la columna de la refinería: apila tres sobre ella. Las tuberías conectadas llegan a sus tanques."),
}
TOOLTIP = {
    "boiler": ("Up to 20 mB/t of steam", "Hasta 20 mB/t de vapor"),
    "pump": ("1000 mB per source block, 200 FE each", "1000 mB por bloque fuente, 200 FE cada uno"),
    "biogas_digester": ("50–500 mB of biogas per item", "50–500 mB de biogás por objeto"),
    "diesel_generator": ("300 FE/t for 1 mB/t of diesel", "300 FE/t por 1 mB/t de diésel"),
    "oil_derrick": ("1000 mB of crude every 2 s, 60 FE/t", "1000 mB de crudo cada 2 s, 60 FE/t"),
    "refinery": ("1000 mB crude → 500 diesel + 250 rocket fuel + plastic + 2 tar", "1000 mB de crudo → 500 diésel + 250 combustible + plástico + 2 alquitrán"),
    "steam_turbine": ("Up to 4000 FE/t from 400 mB/t of steam", "Hasta 4000 FE/t con 400 mB/t de vapor"),
    "fuelling_port": ("250 mB of rocket fuel = 1 Rocket Fuel", "250 mB de combustible = 1 combustible de cohete"),
}
ITEM_NAMES = {"plastic": ("Plastic", "Plástico"), "tar": ("Tar", "Alquitrán")}
STATUS = {
    "idle": ("Ready", "Listo"), "running": ("Running", "Funcionando"), "no_power": ("No power", "Sin energía"),
    "no_input": ("Nothing to process", "Nada que procesar"), "full": ("Output full", "Salida llena"), "heating": ("Heating up", "Calentando"),
    "no_water": ("No water", "Sin agua"), "incomplete": ("Structure incomplete", "Estructura incompleta"),
    "no_deposit": ("No oil under the platform", "No hay petróleo bajo la plataforma"), "no_fuel": ("No fuel", "Sin combustible"),
    "no_air": ("No air: nothing burns", "Sin aire: nada arde"), "no_source": ("No fluid source below", "No hay fuente de fluido abajo"),
    "spinning_up": ("Spinning up", "Acelerando"), "no_target": ("Nothing to fuel", "Nada que repostar"), "digesting": ("Digesting", "Digiriendo"),
}
TANK_TIPS = {
    "boiler": [("Water: pipes, buckets, or water source blocks touching it", "Agua: tuberías, cubos o fuentes de agua que la toquen"),
               ("Steam: goes out to pipes and steam machines next to it", "Vapor: sale a tuberías y máquinas de vapor contiguas")],
    "pump": [("Pumped fluid: pipes take it from any side but the bottom", "Fluido bombeado: las tuberías lo sacan por cualquier lado salvo abajo")],
    "biogas_digester": [("Biogas: goes out to pipes and Biogas Generators next to it", "Biogás: sale a tuberías y generadores contiguos")],
    "diesel_generator": [("Diesel: by pipe or bucket", "Diésel: por tubería o cubo")],
    "oil_derrick": [("Crude oil: goes out to pipes and tanks next to it", "Crudo: sale a tuberías y tanques contiguos")],
    "refinery": [("Crude oil in", "Entra crudo"), ("Diesel out", "Sale diésel"), ("Rocket fuel out", "Sale combustible de cohete")],
    "steam_turbine": [("Steam: from a reactor Coolant Port or Boilers", "Vapor: de un puerto de refrigerante de reactor o de calderas")],
    "fuelling_port": [("Rocket fuel: by pipe or bucket", "Combustible de cohete: por tubería o cubo")],
}


def lang(ctx):
    L = ctx.lang
    B, I, T, G = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"gui.{MOD}"
    for fid, (en, es) in FLUID_NAMES.items():
        L(f"fluid_type.{MOD}.{fid}", en, es)
        if fid in LIQUIDS:
            L(f"{B}.{fid}", en, es)
            L(f"{I}.{fid}_bucket", f"{en} Bucket", BUCKET_ES[fid])
    for name, (en, es) in BLOCK_NAMES.items():
        L(f"{B}.{name}", en, es)
    for name, (en, es) in ITEM_NAMES.items():
        L(f"{I}.{name}", en, es)
    for name, (en, es) in DESC.items():
        L(f"desc.{MOD}.{name}", en, es)
    for name, (en, es) in TOOLTIP.items():
        L(f"{T}.fluid.{name}", en, es)
    L(f"{T}.multiblock.oil_derrick", "Multiblock: the controller in the middle of a 3×3 of Derrick Bases",
      "Multibloque: el controlador en el centro de un 3×3 de bases")
    L(f"{T}.multiblock.refinery", "Multiblock: three Refinery Towers stacked on the controller",
      "Multibloque: tres torres de refinería apiladas sobre el controlador")
    L(f"{T}.fluid_pipe_rate", "Up to %s mB/s per extracting face", "Hasta %s mB/s por cara que extrae")
    L(f"{T}.tank_capacity", "Holds %s buckets", "Capacidad: %s cubos")
    L(f"{T}.tank_holds", "%1$s: %2$s mB", "%1$s: %2$s mB")
    for key, (en, es) in STATUS.items():
        L(f"status.{MOD}.fluid.{key}", en, es)
    for machine, tips in TANK_TIPS.items():
        for i, (en, es) in enumerate(tips):
            L(f"{G}.fluid.tank.{machine}.{i}", en, es)
    for key, en, es in [
        ("fluid.status_making", "%s · %s FE/t", "%s · %s FE/t"),
        ("fluid.status_using", "%s · %s FE/t", "%s · %s FE/t"),
        ("fluid.status_steam", "%s · %s mB/t", "%s · %s mB/t"),
        ("fluid.tank_empty", "Empty (%s mB)", "Vacío (%s mB)"),
        ("fluid.tank_amount", "%s / %s mB", "%s / %s mB"),
        ("fluid.pocket", "Oil pocket: %s", "Bolsa: %s"),
        ("fluid.no_pocket", "No oil found", "Sin petróleo"),
        ("fluid.depth", "%s blocks down", "%s bloques abajo"),
        ("fluid.rotor", "Rotor speed: %s%%", "Velocidad del rotor: %s%%"),
        ("fluid.pump_found", "%s source blocks in reach (radius %s)", "%s bloques fuente al alcance (radio %s)"),
        ("fluid.fuel_items", "Worth %s Rocket Fuel · %s delivered", "Equivale a %s combustibles · %s entregados"),
        ("fluid_pipe.rate", "%s mB/s per face", "%s mB/s por cara"),
        ("power.steam_chest", "Steam from a Boiler: %s / %s mB", "Vapor de una caldera: %s / %s mB"),
        ("fluid_pipe.network", "%s pipes · %s outlets · %s mB/s max", "%s tuberías · %s salidas · %s mB/s máx."),
        ("fluid_pipe.idle", "Nothing flowing · %s pumping faces", "Nada fluye · %s caras bombeando"),
        ("fluid_pipe.flowing", "%s flowing · %s pumping faces", "Fluye %s · %s caras bombeando"),
        ("fluid_pipe.extract_tip", "This face pumps fluid out of the block there into the network",
         "Esta cara bombea fluido del bloque de al lado hacia la red"),
        ("fluid_pipe.insert_tip", "This face delivers fluid from the network into the block there",
         "Esta cara entrega fluido de la red al bloque de al lado"),
        ("fluid_pipe.state_extract", "Pumping out of it", "Bombeando desde aquí"),
        ("fluid_pipe.state_insert", "Delivering into it", "Entregando aquí"),
    ]:
        L(f"{G}.{key}", en, es)
    for key, en, es in [
        ("fluid_pipe_extract", "Pipe face: pumping out", "Cara de tubería: bombeando"),
        ("fluid_pipe_insert", "Pipe face: delivering", "Cara de tubería: entregando"),
        ("tank_empty", "Empty tank (%s buckets)", "Tanque vacío (%s cubos)"),
        ("tank_contents", "%s: %s / %s mB", "%s: %s / %s mB"),
    ]:
        L(f"message.{MOD}.{key}", en, es)
    L(f"jei.{MOD}.fluid_processes", "Fluid Machines", "Máquinas de fluidos")
    L(f"jei.{MOD}.fluid_note.boiler", "Burns any furnace fuel", "Quema cualquier combustible de horno")
    L(f"jei.{MOD}.fluid_note.per_tick", "per tick", "por tick")
    L(f"jei.{MOD}.fluid_note.per_item", "per item", "por objeto")


def generate(ctx):
    blocks(ctx)
    items(ctx)
    worldgen(ctx)
    tags(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx)


# ============================================================ preview (tools/fluids_models_preview.png)

def preview(path):
    from PIL import Image, ImageDraw
    drill = S._load("drill")
    tex_root = S.ASSETS / "textures" / "block"
    cache = {}

    def textures(tmap):
        out = {}
        for k, v in tmap.items():
            name = v.split("block/")[-1]
            if name not in cache:
                im = Image.open(tex_root / f"{name}.png").convert("RGBA")
                cache[name] = im.crop((0, 0, im.width, im.width)) if im.height > im.width else im
            out[k] = cache[name]
        return out

    def scaled3(els):
        def tr(v):
            return [v[0] * 3 - 16, v[1] * 3 + 16, v[2] * 3 - 16]
        return [El(tr(e.frm), tr(e.to), e.faces, e.m, tr(e.origin), e.emissive) for e in els]

    pipe_tex = {"t": t("steel_fluid_pipe")}
    base = []
    for dx in (-16, 0, 16):
        for dz in (-16, 0, 16):
            if dx or dz:
                base.append(cube([dx, 0, dz], [dx + 16, 16, dz + 16], {**{d: "base_side" for d in S.DIRS}, "up": "base"}))
    derrick = base + wellhead(True) + scaled3(pumpjack_frame() + pumpjack_beam() + pumpjack_crank() + pumpjack_rod())
    tower = refinery(True) + shift(refinery_tower(False), 0, 16, 0) + shift(refinery_tower(False), 0, 32, 0) + shift(refinery_tower(True), 0, 48, 0)
    entries = [
        ("Steel Fluid Pipe", [cube([4, 4, 4], [12, 12, 12], "t", u=4, v=4), cube([4, 4, 0], [12, 12, 4], "t", u=4, v=0),
                              cube([12, 4, 4], [16, 12, 12], "t", u=4, v=0)], pipe_tex, 16),
        ("Bronze Tank", tank_elements("bronze"), {"frame": t("bronze_fluid_tank_frame"), "glass": t("bronze_fluid_tank_glass"),
                                                  "lid": t("bronze_fluid_tank_lid")}, 16),
        ("Titanium Tank", tank_elements("titanium"), {"frame": t("titanium_fluid_tank_frame"), "glass": t("titanium_fluid_tank_glass"),
                                                      "lid": t("titanium_fluid_tank_lid")}, 16),
        ("Boiler", boiler(True), BOILER_TEX, 16),
        ("Pump", pump(True), PUMP_TEX, 16),
        ("Biogas Digester", biogas_digester(True), DIGEST_TEX, 16),
        ("Diesel Generator", diesel_generator(True), DIESEL_TEX, 16),
        ("Steam Turbine", steam_turbine() + turbine_rotor(), TURBINE_TEX, 16),
        ("Fuelling Port", fuelling_port(), FUEL_TEX, 16),
        ("Oil Derrick (formed)", derrick, DERRICK_TEX, 48),
        ("Refinery (formed)", tower, REFINERY_TEX, 64),
    ]
    size, bg = 220, (32, 34, 42, 255)
    panels = []
    for label, els, tmap, extent in entries:
        q = S._quads(els, textures(tmap))
        m = S._mul(drill._rot("x", 28), drill._rot("y", 145))
        cy = 8 if extent == 16 else 26 if extent == 48 else 34
        scale = 150 / max(16, extent) * (1.0 if extent == 16 else 1.25)
        img = drill._render_mixed([(q, lambda p, m=m, cy=cy: S._app(m, [(p[0] - 8) / 16, (p[1] - cy) / 16, (p[2] - 8) / 16]))],
                                  lambda v, s=scale: (size / 2 + v[0] * s * 16 / 1.3, size / 2 + 10 - v[1] * s * 16 / 1.3),
                                  (size, size), bg)
        panels.append((label, img))
    cols = 6
    rows = (len(panels) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (size + 8) + 8, rows * (size + 24) + 8), (22, 22, 28, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(panels):
        x, y = 8 + (i % cols) * (size + 8), 8 + (i // cols) * (size + 24)
        sheet.alpha_composite(img, (x, y + 16))
        d.text((x + 4, y + 2), label, fill=(235, 235, 235, 255))
    sheet.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(HERE.parent / "fluids_models_preview.png")
    else:
        print(__doc__)

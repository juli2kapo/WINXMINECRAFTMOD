#!/usr/bin/env python3
"""Early and mid-game additions: models, recipes, advancements, lang, equipment and loot.

Stone age:   Sieve (hand-cranked sifting), Drying Rack, Water Wheel and Windmill (mechanical rotation
             that cranks the Quern or Sieve next to them; their rotors are separate models turned by
             kinetic/client/KineticRenderer.java).
Bronze age:  bronze armour, Bronze Backpack, Grappling Hook (tin nuggets come from the Sieve).
Electric:    Charger, Floodlight, Item Magnet, Night-Vision Goggles.
Automation:  Block Breaker, Block Placer, Vacuum Hopper, Tree Farm.
Industrial:  Industrial Grinder, Recycler (+ scrap, scrap box), Mob Farm Controller.

The machines are rows of the MACHINES table in tools/gen_resources.py (blockstates, loot, names and
descriptions come from there); this module overwrites their placeholder block models with the real
ones, built with the model DSL of tools/models/gen_models.py (vanilla textures are allowed for the
wooden stone-age builds). Textures come from tools/features/early_textures.py.

Run `python3 tools/features/early.py --preview` to render every new model to tools/early_preview.png.
"""
import importlib.util
import math
import sys
from pathlib import Path

MOD = "factoryascent"
TOOLS = Path(__file__).resolve().parents[1]
ROOT = TOOLS.parent
AXE_BLOCKS = ["sieve", "drying_rack", "water_wheel", "windmill"]


def _load(name, path):
    if name in sys.modules:
        return sys.modules[name]
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


ET = _load("early_textures_dep", Path(__file__).with_name("early_textures.py"))
gm = _load("gen_models", TOOLS / "models" / "gen_models.py")
ET.gt()  # merges this module's carved windows into gen_textures.LAYOUT


# ============================================================ model DSL on top of gen_models

class B(gm.Box):
    """gen_models.Box without the 0..16 bounds check (rotors and sails reach past the block),
    plus an optional glow (light_emission 15 on the element)."""

    def __init__(self, fr, to, tex, uv=None, skip=(), rot=None, shade=True, glow=False):
        self.fr = [float(v) for v in fr]
        self.to = [float(v) for v in to]
        for a, b in zip(self.fr, self.to):
            assert a < b, (fr, to)
        self.tex, self.uv, self.skip, self.rot, self.shade, self.glow = tex, uv or {}, set(skip), rot, shade, glow
        if any(v < 0 or v > 16 for v in self.fr + self.to):
            # Outside the block the projected UVs would leave the texture: map each face from its corner instead.
            dx, dy, dz = (self.to[i] - self.fr[i] for i in range(3))
            dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
            for face, (du, dv) in dims.items():
                self.uv.setdefault(face, [0, 0, min(du, 16), min(dv, 16)])


class M(gm.Model):
    """Model whose texture map may name vanilla textures ("minecraft:block/oak_planks")."""

    def __init__(self, mid, textures, boxes, on=None, particle=None, desc="", boxes_on=None, display=None):
        super().__init__(mid, textures, boxes, on=on, particle=particle, desc=desc, boxes_on=boxes_on)
        self.display = display

    @staticmethod
    def ref(name):
        return name if ":" in name else f"{MOD}:block/{name}"

    def to_json(self, on):
        tm = self.tex_map(on)
        boxes = self.boxes_on if on else self.boxes
        elements = []
        for b in boxes:
            e = gm.element_json(b, boxes)
            if getattr(b, "glow", False):
                e["light_emission"] = 15
            elements.append(e)
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        assert not used - set(tm), (self.id, used - set(tm))
        tex = {"particle": self.ref(tm.get(self.particle, self.particle))}
        for k in sorted(tm):
            if k in used:
                tex[k] = self.ref(tm[k])
        out = {"parent": "minecraft:block/block", "textures": tex, "elements": elements}
        if self.display:
            out["display"] = self.display
        return out


PLANKS, LOG, STRIPPED = "minecraft:block/oak_planks", "minecraft:block/oak_log", "minecraft:block/stripped_oak_log"
SPRUCE = "minecraft:block/spruce_planks"


def rot90(box, n, cx, cy):
    """Box turned n quarter turns about the z axis through (cx, cy) (for the windmill's sails)."""
    x0, y0, z0 = box.fr
    x1, y1, z1 = box.to
    pts = [(x0, y0), (x1, y1)]
    for _ in range(n % 4):
        pts = [(cx + (y - cy), cy - (x - cx)) for x, y in pts]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return B((min(xs), min(ys), z0), (max(xs), max(ys), z1), box.tex, skip=box.skip, glow=box.glow)


# ---------------------------------------------------------------- stone age

def m_sieve():
    tx = {"leg": STRIPPED, "plank": PLANKS, "mesh": "sieve_mesh", "handle": "quern_handle", "gravel": "minecraft:block/gravel",
          "sand": "minecraft:block/sand"}
    b = []
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        b.append(B((x, 0, z), (x + 2, 9, z + 2), "leg"))
    b += [B((0, 8, 0), (16, 12, 2), "plank"), B((0, 8, 14), (16, 12, 16), "plank"),
          B((0, 8, 2), (2, 12, 14), "plank"), B((14, 8, 2), (16, 12, 14), "plank"),
          B((2, 9, 2), (14, 9.5, 14), "mesh", skip=("north", "south", "east", "west")),
          # bearing block for the crank on the east rail (the crank itself is m_sieve_crank)
          B((16, 8.5, 6.5), (16.5, 11.5, 9.5), "leg")]
    on = b + [B((4, 9.5, 4), (12, 11, 12), "gravel"), B((5, 11, 5), (11, 11.75, 11), "gravel"),
              B((5, 0, 5), (11, 0.75, 11), "sand")]
    return M("sieve", tx, b, boxes_on=on, particle="plank", desc="oak tray on legs, string mesh, crank; gravel on it when working")


def m_sieve_crank():
    """The sieve's crank, turned about the x axis through (y 10, z 8) by client/SieveRenderer.java."""
    tx = {"handle": "quern_handle", "arm": "minecraft:block/stripped_dark_oak_log"}
    b = [B((15.5, 9.25, 7.25), (17.75, 10.75, 8.75), "handle"),     # axle out of the east rail
         B((17.75, 8.75, 6.75), (19, 15.5, 9.25), "arm"),           # crank arm
         B((19, 13.5, 7.25), (21.5, 15, 8.75), "handle")]           # grip
    return M("sieve_crank", tx, b, particle="arm", desc="sieve crank: axle, arm and grip")


def m_drying_rack():
    tx = {"post": STRIPPED, "pole": LOG, "foot": PLANKS, "hides": "drying_rack_hides"}
    b = []
    for x in (1, 13):
        b += [B((x, 0, 7), (x + 2, 15, 9), "post"), B((x - 1, 0, 4), (x + 3, 1, 12), "foot")]
    b += [B((3, 13, 7.25), (13, 14.5, 8.75), "pole"), B((3, 7, 7.5), (13, 8, 8.5), "pole")]
    hides = [B((3.5, 1.5, 7.9), (12.5, 13.5, 8.1), "hides", skip=("east", "west", "up", "down"))]
    return M("drying_rack", tx, b, boxes_on=b + hides, particle="post",
             desc="A-frame of stripped oak with two poles; hides and kelp hang when drying")


def m_water_wheel():
    tx = {"post": STRIPPED, "sill": PLANKS, "bearing": "minecraft:block/iron_block"}
    b = [B((6, 0, 0), (10, 9, 2), "post"), B((6, 0, 14), (10, 9, 16), "post"),
         B((6.5, 6.5, 0.5), (9.5, 9.5, 2.5), "bearing"), B((6.5, 6.5, 13.5), (9.5, 9.5, 15.5), "bearing"),
         B((4, 0, 0), (12, 1, 16), "sill")]
    return M("water_wheel", tx, b, particle="post", desc="two oak posts with iron bearings (the wheel turns: rotor)")


def m_water_wheel_rotor():
    tx = {"axle": LOG, "arm": PLANKS, "paddle": "water_wheel_paddle", "hub": "minecraft:block/oak_log_top"}
    b = [B((7, 7, 0.5), (9, 9, 15.5), {"all": "axle", "north": "hub", "south": "hub"}),
         B((6, 6, 3), (10, 10, 13), {"all": "arm", "north": "hub", "south": "hub"})]
    for ang in (0, 45, 90, 135):
        arm = B((1, 7.25, 5), (15, 8.75, 11), "arm", rot=("z", ang, (8, 8, 8)) if ang else None)
        b.append(arm)
        for x0, x1 in ((0.25, 1.75), (14.25, 15.75)):
            b.append(B((x0, 4.5, 3.5), (x1, 11.5, 12.5), "paddle", rot=("z", ang, (8, 8, 8)) if ang else None))
    return M("water_wheel_rotor", tx, b, particle="paddle", desc="eight iron-strapped paddles on an oak hub")


def m_windmill():
    tx = {"side": "windmill_side", "roof": "windmill_roof", "hub": SPRUCE, "floor": "minecraft:block/stone_bricks"}
    b = [B((1, 0, 1), (15, 12, 15), {"side": "side", "up": "roof", "down": "floor"}),
         B((0, 0, 0), (16, 2, 16), "floor"),
         B((0, 12, 0), (16, 14, 16), "roof"), B((2, 14, 2), (14, 15.5, 14), "roof"), B((5, 15.5, 5), (11, 16, 11), "roof"),
         B((6, 9, 0), (10, 13, 1), "hub")]
    return M("windmill", tx, b, particle="side", desc="whitewashed timber-framed tower, shingle roof (sails: rotor)")


WINDMILL_PIVOT = (8, 11)


def m_windmill_rotor():
    tx = {"spar": PLANKS, "sail": "windmill_sail", "hub": SPRUCE}
    cx, cy = WINDMILL_PIVOT
    b = [B((6.5, 9.5, -2.5), (9.5, 12.5, 0.5), "hub")]
    spar = B((7.5, 12.5, -2), (8.5, 30, -1), "spar")
    sail = B((8.5, 15, -1.75), (13, 29.5, -1.25), "sail")
    for n in range(4):
        b += [rot90(spar, n, cx, cy), rot90(sail, n, cx, cy)]
    return M("windmill_rotor", tx, b, particle="sail", desc="four canvas sails on oak spars")


# ---------------------------------------------------------------- electric age

def m_charger():
    tx = {"front": "charger_front", "side": "steel_machine_side", "top": "charger_top", "bottom": "steel_machine_bottom",
          "prong": "minecraft:block/copper_block"}
    b = [B((1, 0, 1), (15, 8, 15), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
         B((4, 8, 7), (5, 10, 9), "prong"), B((11, 8, 7), (12, 10, 9), "prong")]
    return M("charger", tx, b, on={"front": "charger_front_on", "top": "charger_top_on"}, particle="side",
             desc="low steel pad: copper contacts, glowing charge ring, charge gauge")


def m_floodlight():
    tx = {"base": "steel_machine_side", "base_top": "steel_machine_bottom", "housing": "floodlight_housing",
          "lens": "floodlight_lens", "yoke": "steel_machine_bottom"}
    tilt = ("x", -22.5, (8, 8.5, 8))
    b = [B((2, 0, 2), (14, 2, 14), {"side": "base", "up": "base_top", "down": "base_top"}),
         B((7, 2, 7), (9, 4, 9), "yoke"),
         B((2.5, 4, 7), (3.5, 11, 9), "yoke"), B((12.5, 4, 7), (13.5, 11, 9), "yoke"),
         B((3.5, 3.5, 7), (12.5, 4.5, 9), "yoke"),
         B((4, 5, 3), (12, 12, 13), {"all": "housing", "north": "lens"}, rot=tilt)]
    on = b[:-1] + [B((4, 5, 3), (12, 12, 13), {"all": "housing", "north": "lens"}, rot=tilt, glow=True)]
    return M("floodlight", tx, b, on={"lens": "floodlight_lens_on"}, boxes_on=on, particle="housing",
             desc="tilted lamp in a steel yoke: finned housing, big lens")


# ---------------------------------------------------------------- automation age

ALU = {"side": "alu_casing_side", "top": "alu_casing_top", "bottom": "alu_casing_bottom", "inner": "alu_casing_inner"}
STD = gm.faces_of("front", "side", "top", "bottom")


def m_block_breaker():
    tx = dict(ALU, front="block_breaker_front", pick="block_breaker_pick")
    b = gm.carved("block_breaker", STD)

    def head(angle):
        r = ("z", angle, (8, 8, 1.5)) if angle else None
        return [B((7, 7, 0.5), (9, 9, 3), "pick"),
                B((4, 7.5, 1), (12, 8.5, 2), "pick", rot=r), B((7.5, 4, 1), (8.5, 12, 2), "pick", rot=r),
                B((7.25, 7.25, 0), (8.75, 8.75, 0.5), "pick")]
    return M("block_breaker", tx, b + head(0), on={"front": "block_breaker_front_on"}, boxes_on=b + head(45),
             particle="alu_casing_side", desc="aluminium: four-pronged pick head in a hazard-framed mouth")


def m_block_placer():
    tx = dict(ALU, front="block_placer_front", plate="block_placer_plate", rod="block_breaker_pick")
    b = gm.carved("block_placer", STD)
    idle = [B((5, 5, 1.5), (11, 11, 2), "plate")]
    on = [B((5, 5, 0.25), (11, 11, 0.75), "plate"), B((7.5, 7.5, 0.75), (8.5, 8.5, 2), "rod")]
    return M("block_placer", tx, b + idle, on={"front": "block_placer_front_on"}, boxes_on=b + on,
             particle="alu_casing_side", desc="aluminium: pusher plate in a square port (out when placing)")


def m_vacuum_hopper():
    tx = dict(ALU, side="vacuum_hopper_side", top="vacuum_hopper_top")
    faces = gm.faces_of("side", "side", "top", "bottom")
    b = gm.carved("vacuum_hopper_box", faces, bounds=((0, 4, 0), (16, 16, 16)))
    b += [B((4, 1, 4), (12, 4, 12), "side"), B((6, 0, 6), (10, 1, 10), "bottom")]
    return M("vacuum_hopper", tx, b, on={"side": "vacuum_hopper_side_on", "top": "vacuum_hopper_top_on"},
             particle="alu_casing_side", desc="aluminium hopper: intake fan under a grille, suction slots, spout")


def m_tree_farm():
    tx = dict(ALU, front="tree_farm_front", top="tree_farm_top", saw="tree_farm_saw", rim="block_breaker_pick",
              sapling="minecraft:block/oak_sapling")
    b = gm.carved("tree_farm", STD)
    b += gm.octo_z(8, 8, 3.5, 1, 2, {"north": "saw", "all": "rim"}, c=1)
    b += [B((7.5, 7.5, 2), (8.5, 8.5, 3), "rim")]
    full = {f: [0, 0, 16, 16] for f in ("north", "south", "east", "west")}
    sap = [B((4.5, 15.5, 7.95), (11.5, 22.5, 8.05), "sapling", uv=dict(full), skip=("east", "west", "up", "down")),
           B((7.95, 15.5, 4.5), (8.05, 22.5, 11.5), "sapling", uv=dict(full), skip=("north", "south", "up", "down"))]
    return M("tree_farm", tx, b + sap, on={"front": "tree_farm_front_on", "saw": "tree_farm_saw_on"},
             particle="alu_casing_side", desc="aluminium: circular saw in the front, soil tray + sapling on top")


# ---------------------------------------------------------------- industrial age

TI = {"side": "titanium_machine_side", "top": "ti_casing_top", "bottom": "titanium_machine_bottom",
      "inner": "titanium_machine_inner"}


def m_industrial_grinder():
    tx = dict(TI, front="industrial_grinder_front", top="industrial_grinder_top", drum="industrial_grinder_drum",
              end="industrial_grinder_drum_end")
    b = gm.carved("industrial_grinder", STD)
    b += gm.octo_z(8, 9.5, 4, 0.5, 3.75, {"north": "end", "all": "drum"}, c=1.5)
    return M("industrial_grinder", tx, b, on={"front": "industrial_grinder_front_on", "drum": "industrial_grinder_drum_on",
                                              "end": "industrial_grinder_drum_end_on"},
             particle="titanium_machine_side", desc="titanium: one big toothed grinding drum, ore hopper on top")


def m_recycler():
    tx = dict(TI, front="recycler_front", top="recycler_top", shaft="industrial_grinder_drum",
              shaft_end="industrial_grinder_drum_end")
    b = gm.carved("recycler", STD)
    for cx in (6, 10):
        b += gm.octo_z(cx, 10, 1.6, 1, 3, {"north": "shaft_end", "all": "shaft"}, c=0.6)
    return M("recycler", tx, b, on={"front": "recycler_front_on", "shaft": "industrial_grinder_drum_on"},
             particle="titanium_machine_side", desc="titanium: twin shredder shafts over a conveyor, recycling mark")


def m_mob_farm():
    tx = dict(TI, front="mob_farm_front", top="mob_farm_top", glass="minecraft:block/glass", tube="block_breaker_pick")
    b = gm.carved("mob_farm", STD)
    b += [B((3, 3, 0.5), (13, 14, 0.75), "glass", skip=("east", "west", "up", "down")),
          B((1, 14, 6), (2, 16, 10), "tube"), B((14, 14, 6), (15, 16, 10), "tube")]
    return M("mob_farm", tx, b, on={"front": "mob_farm_front_on"}, particle="titanium_machine_side",
             desc="titanium: caged glass chamber with a ghostly mob, capsule socket on top")


MODELS = [m_sieve, m_drying_rack, m_water_wheel, m_windmill, m_charger, m_floodlight, m_block_breaker,
          m_block_placer, m_vacuum_hopper, m_tree_farm, m_industrial_grinder, m_recycler, m_mob_farm]
ROTORS = {"water_wheel": m_water_wheel_rotor, "windmill": m_windmill_rotor, "sieve": m_sieve_crank}


def write_models(ctx):
    for f in MODELS:
        mdl = f()
        for on in (False, True):
            ctx.block_model(mdl.id + ("_on" if on else ""), mdl.to_json(on))
    for mid, f in ROTORS.items():
        rotor = f()
        ctx.block_model(rotor.id, rotor.to_json(False))
        # the inventory icon shows the frame and the rotor together
        base = [f_() for f_ in MODELS if f_.__name__ == f"m_{mid}"][0]
        both = M(mid + "_item", {**base.textures, **rotor.textures}, base.boxes + rotor.boxes, particle=base.particle)
        js = both.to_json(False)
        if mid == "windmill":
            js["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.36, 0.36, 0.36]},
                             "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
                             "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
                             "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
                             "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.2, 0.2, 0.2]}}
        ctx.write(ctx.ASSETS / "models" / "item" / f"{mid}.json", js)
        ctx.item_def(mid, f"item/{mid}")


# ============================================================ items, equipment, loot

FLAT = ["bronze_helmet", "bronze_chestplate", "bronze_leggings", "bronze_boots", "bronze_backpack", "item_magnet",
        "night_vision_goggles", "tin_nugget", "scrap", "scrap_box"]


def items(ctx):
    for name in FLAT:
        ctx.flat_item(name)
    ctx.flat_item("grappling_hook", handheld=True)
    E = ctx.ASSETS / "equipment"
    ctx.write(E / "bronze.json", {"layers": {
        "humanoid": [{"texture": f"{MOD}:bronze"}], "humanoid_leggings": [{"texture": f"{MOD}:bronze"}],
        "humanoid_baby": [{"texture": f"{MOD}:bronze"}]}})
    ctx.write(E / "night_vision_goggles.json", {"layers": {"humanoid": [{"texture": f"{MOD}:night_vision_goggles"}]}})


SCRAP_BOX_LOOT = [  # item, weight, min, max
    ("minecraft:iron_ingot", 20, 1, 2), ("minecraft:copper_ingot", 20, 1, 3), (f"{MOD}:tin_ingot", 15, 1, 2),
    ("minecraft:coal", 15, 2, 4), ("minecraft:redstone", 12, 2, 5), (f"{MOD}:bronze_ingot", 10, 1, 2),
    (f"{MOD}:steel_ingot", 6, 1, 1), (f"{MOD}:aluminum_ingot", 6, 1, 2), ("minecraft:lapis_lazuli", 6, 2, 4),
    ("minecraft:gold_ingot", 5, 1, 1), (f"{MOD}:basic_circuit", 3, 1, 1), ("minecraft:emerald", 2, 1, 1),
    ("minecraft:diamond", 2, 1, 1), (f"{MOD}:titanium_ingot", 1, 1, 1), ("minecraft:ender_pearl", 1, 1, 1),
]


def loot(ctx):
    entries = []
    for item, weight, lo, hi in SCRAP_BOX_LOOT:
        e = {"type": "minecraft:item", "name": item, "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        entries.append(e)
    ctx.write(ctx.DATA / MOD / "loot_table" / "gameplay" / "scrap_box.json",
              {"type": "minecraft:gift", "pools": [{"rolls": 1, "entries": entries}],
               "random_sequence": f"{MOD}:gameplay/scrap_box"})


def tags(ctx):
    def tag(ns, kind, path, values):
        ctx.write(ctx.DATA / ns / "tags" / kind / f"{path}.json",
                  {"replace": False, "values": [v if v.startswith("#") or ":" in v else f"{MOD}:{v}" for v in values]})
    tag("minecraft", "item", "head_armor", ["bronze_helmet"])
    tag("minecraft", "item", "chest_armor", ["bronze_chestplate"])
    tag("minecraft", "item", "leg_armor", ["bronze_leggings"])
    tag("minecraft", "item", "foot_armor", ["bronze_boots"])
    tag("minecraft", "item", "trimmable_armor", ["bronze_helmet", "bronze_chestplate", "bronze_leggings", "bronze_boots"])
    tag("c", "item", "nuggets/tin", ["tin_nugget"])
    tag("c", "item", "nuggets", ["#c:nuggets/tin"])


# ============================================================ recipes

def recipes(ctx):
    S, P, T = "minecraft:stick", "#minecraft:planks", "minecraft:string"
    G = "#c:gears/iron"
    # ---- Stone age
    ctx.shaped("sieve", ["PTP", "PTP", "S S"], {"P": P, "T": T, "S": S}, "sieve")
    ctx.shaped("drying_rack", ["SSS", "T T", "S S"], {"S": S, "T": T}, "drying_rack")
    ctx.shaped("water_wheel", ["PSP", "SGS", "PSP"], {"P": P, "S": S, "G": G}, "water_wheel")
    ctx.shaped("windmill", ["WSW", "SGS", "LBL"], {"W": "#minecraft:wool", "S": S, "G": G, "L": "#minecraft:logs",
                                                   "B": "minecraft:bricks"}, "windmill")
    sift = [  # input -> result, byproduct, extras
        ("gravel", "minecraft:gravel", "minecraft:flint", ("minecraft:iron_nugget", 0.3),
         [("minecraft:copper_nugget", 0.25), ("tin_nugget", 0.25)]),
        ("sand", "minecraft:sand", "minecraft:clay_ball", ("minecraft:gold_nugget", 0.1),
         [("minecraft:cactus", 0.05), ("minecraft:sugar_cane", 0.08)]),
        ("red_sand", "minecraft:red_sand", "minecraft:clay_ball", ("minecraft:gold_nugget", 0.2),
         [("minecraft:redstone", 0.08), ("minecraft:dead_bush", 0.05)]),
        ("dirt", "minecraft:dirt", "minecraft:wheat_seeds", ("minecraft:beetroot_seeds", 0.25),
         [("minecraft:pumpkin_seeds", 0.1), ("minecraft:melon_seeds", 0.1)]),
        ("soul_sand", "minecraft:soul_sand", "minecraft:quartz", ("minecraft:gold_nugget", 0.2),
         [("minecraft:nether_wart", 0.06), ("minecraft:ghast_tear", 0.02)]),
    ]
    for name, inp, out, by, extras in sift:
        ctx.machine("sifting", name, [(inp, 1)], out, time=60, byproduct=by)
        path = ctx.DATA / MOD / "recipe" / "sifting" / f"{name}.json"
        import json
        obj = json.loads(path.read_text())
        obj["extras"] = [{"item": {"id": ctx.ing(i)}, "chance": c} for i, c in extras]
        ctx.write(path, obj)
    ctx.shaped("tin_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": "#c:nuggets/tin"}, "tin_ingot")
    ctx.shapeless("tin_nugget", ["#c:ingots/tin"], "tin_nugget", 9)
    for name, inp, out, time in (("leather", "minecraft:rotten_flesh", "minecraft:leather", 400),
                                 ("dried_kelp", "minecraft:kelp", "minecraft:dried_kelp", 200),
                                 ("sponge", "minecraft:wet_sponge", "minecraft:sponge", 600)):
        ctx.machine("drying", name, [(inp, 1)], out, time=time)

    # ---- Bronze age
    Bz = "#c:ingots/bronze"
    for name, pattern in (("bronze_helmet", ["BBB", "B B"]), ("bronze_chestplate", ["B B", "BBB", "BBB"]),
                          ("bronze_leggings", ["BBB", "B B", "B B"]), ("bronze_boots", ["B B", "B B"])):
        ctx.shaped(name, pattern, {"B": Bz}, name, category="equipment")
    ctx.shaped("bronze_backpack", ["LTL", "PCP", "LLL"], {"L": "minecraft:leather", "T": T, "P": "#c:plates/bronze",
                                                         "C": "minecraft:chest"}, "bronze_backpack", category="equipment")
    ctx.shaped("grappling_hook", [" PP", " TP", "T  "], {"P": "#c:plates/bronze", "T": T}, "grappling_hook",
               category="equipment")

    # ---- Electric age
    SP, CW, CB = "#c:plates/steel", "#c:wires/copper", "basic_circuit"
    ctx.shaped("charger", ["PCP", "WMW", "PRP"], {"P": SP, "C": CB, "W": CW, "M": "machine_frame",
                                                 "R": "minecraft:redstone_block"}, "charger")
    ctx.shaped("floodlight", ["PPP", "GLG", "PWP"], {"P": SP, "G": "#c:glass_blocks/colorless", "L": "minecraft:glowstone",
                                                    "W": CW}, "floodlight")
    ctx.shaped("item_magnet", ["P P", "R R", "ICI"], {"P": SP, "R": "minecraft:redstone", "I": "#c:ingots/iron", "C": CB},
               "item_magnet", category="equipment")
    ctx.shaped("night_vision_goggles", ["LCL", "GTG"], {"L": "minecraft:leather", "C": CB, "G": "minecraft:glass_pane",
                                                       "T": "minecraft:golden_carrot"}, "night_vision_goggles",
               category="equipment")

    # ---- Automation age
    AP, AC = "#c:plates/aluminum", "advanced_circuit"
    ctx.shaped("block_breaker", ["PAP", "CMC", "PWP"], {"P": AP, "A": "minecraft:iron_pickaxe", "C": AC, "M": "machine_frame",
                                                       "W": CW}, "block_breaker")
    ctx.shaped("block_placer", ["PDP", "CMC", "PWP"], {"P": AP, "D": "minecraft:dispenser", "C": AC, "M": "machine_frame",
                                                      "W": CW}, "block_placer")
    ctx.shaped("vacuum_hopper", ["PEP", "CHC", "PMP"], {"P": AP, "E": "ender_dust", "C": AC, "H": "minecraft:hopper",
                                                       "M": "motor"}, "vacuum_hopper")
    ctx.shaped("tree_farm", ["PAP", "CFC", "PSP"], {"P": AP, "A": "minecraft:iron_axe", "C": AC, "F": "advanced_machine_frame",
                                                   "S": "#minecraft:saplings"}, "tree_farm")

    # ---- Industrial age
    TP, F = "#c:plates/titanium", "advanced_machine_frame"
    ctx.shaped("industrial_grinder", ["TCT", "GXG", "TFT"], {"T": TP, "C": AC, "G": "#c:gears/titanium", "X": "crusher",
                                                            "F": F}, "industrial_grinder")
    ctx.shaped("recycler", ["TCT", "HFH", "TGT"], {"T": TP, "C": AC, "H": "minecraft:hopper", "F": F,
                                                  "G": "#c:gears/steel"}, "recycler")
    ctx.shaped("mob_farm", ["TWT", "CFC", "TMT"], {"T": TP, "W": "minecraft:diamond_sword", "C": AC, "F": F,
                                                  "M": "mob_capsule"}, "mob_farm")
    grind = {"iron": ("#c:raw_materials/iron", "#c:ores/iron", "tin_dust", "gold_dust"),
             "copper": ("#c:raw_materials/copper", "#c:ores/copper", "gold_dust", "iron_dust"),
             "gold": ("#c:raw_materials/gold", "#c:ores/gold", "copper_dust", "quartz_dust"),
             "tin": ("#c:raw_materials/tin", "#c:ores/tin", "iron_dust", "copper_dust"),
             "bauxite": ("#c:raw_materials/aluminum", "#c:ores/aluminum", "iron_dust", "titanium_dust"),
             "titanium": ("#c:raw_materials/titanium", "#c:ores/titanium", "iron_dust", "bauxite_dust")}
    import json
    for metal, (raw, ore, by, second) in grind.items():
        dust = f"{metal}_dust"
        for kind, inp, count, extra in (("raw", raw, 4, 0.10), ("ore", ore, 5, 0.15)):
            name = f"{dust}_from_{kind}_ground"
            ctx.machine("crushing", name, [(inp, 1)], dust, count, time=60 if kind == "raw" else 80, min_grade=5,
                        byproduct=(by, 0.35))
            path = ctx.DATA / MOD / "recipe" / "crushing" / f"{name}.json"
            obj = json.loads(path.read_text())
            obj["extras"] = [{"item": {"id": ctx.ing(second)}, "chance": extra}]
            ctx.write(path, obj)
    # Recycler: junk into scrap (nine scrap make a Scrap Box) ...
    ctx.shapeless("scrap_box", ["scrap"] * 9, "scrap_box")
    for name, inp, count in (("cobblestone", "#c:cobblestones", 16), ("dirt", "minecraft:dirt", 16),
                             ("netherrack", "minecraft:netherrack", 16), ("gravel", "minecraft:gravel", 16),
                             ("rotten_flesh", "minecraft:rotten_flesh", 8), ("poisonous_potato", "minecraft:poisonous_potato", 4),
                             ("spider_eye", "minecraft:spider_eye", 4), ("saplings", "#minecraft:saplings", 8),
                             ("sticks", "minecraft:stick", 32)):
        ctx.machine("recycling", f"scrap_from_{name}", [(inp, count)], "scrap", time=40, min_grade=5)
    # ... and worn-out gear back into what it was made of (one unit less than the recipe took).
    gear = {"helmet": 4, "chestplate": 7, "leggings": 6, "boots": 3, "sword": 1, "pickaxe": 2, "axe": 2, "shovel": 1, "hoe": 1}
    for prefix, out, pieces in (("minecraft:iron_", "iron_dust", gear), ("minecraft:golden_", "gold_dust", gear),
                                ("minecraft:diamond_", "minecraft:diamond", gear), ("bronze_", "bronze_ingot", gear)):
        for piece, count in pieces.items():
            item = prefix + piece
            ctx.machine("recycling", f"{item.split(':')[-1]}_salvage", [(item, 1)], out, count, time=80, min_grade=5)


# ============================================================ advancements

def advancements(ctx):
    A = ctx.advancement
    A("stone_sieve", "stone_quern", "sieve", ["sieve"], "Panning for Gold",
      "Build a Sieve and sift gravel, sand or dirt for flint, clay, seeds and nuggets", "Buscando oro",
      "Construye una criba y criba grava, arena o tierra en busca de pedernal, arcilla, semillas y pepitas")
    A("stone_drying_rack", "stone_hammer", "drying_rack", ["drying_rack"], "Hang It Out to Dry",
      "Build a Drying Rack: rotten flesh dries into leather", "Tendido al sol",
      "Construye un secadero: la carne podrida se seca en cuero")
    A("stone_water_wheel", "stone_quern", "water_wheel", ["water_wheel"], "Go With the Flow",
      "Build a Water Wheel in running water next to a Quern or Sieve: no more cranking", "Dejarse llevar",
      "Construye una rueda hidráulica en agua corriente junto a un molino o una criba: se acabó girar la manivela")
    A("stone_windmill", "stone_water_wheel", "windmill", ["windmill"], "Tilting at Windmills",
      "Build a Windmill up high with open air in front of its sails", "Contra los molinos de viento",
      "Construye un molino de viento en lo alto con aire libre delante de sus aspas")
    A("bronze_armor", "bronze_tools", "bronze_chestplate",
      ["bronze_helmet", "bronze_chestplate", "bronze_leggings", "bronze_boots"], "Suit Up",
      "Forge a piece of bronze armour", "A vestirse", "Forja una pieza de armadura de bronce")
    A("bronze_backpack", "age_bronze", "bronze_backpack", ["bronze_backpack"], "Pack Mule",
      "Sew a Bronze Backpack: 27 slots you carry around", "Mula de carga",
      "Cose una mochila de bronce: 27 ranuras que llevas contigo")
    A("bronze_grappling_hook", "age_bronze", "grappling_hook", ["grappling_hook"], "Get Over Here!",
      "Craft a Grappling Hook and pull yourself up a cliff", "¡Ven aquí!",
      "Fabrica un gancho de escalada y súbete a un acantilado")
    A("electric_charger", "electric_power", "charger", ["charger"], "Plugged In",
      "Build a Charger: it tops up any FE tool and hands it back full", "Enchufado",
      "Construye un cargador: recarga cualquier herramienta con FE y la devuelve llena")
    A("electric_magnet", "electric_charger", "item_magnet", ["item_magnet"], "Attractive",
      "Craft an Item Magnet, switch it on and never chase drops again", "Atractivo",
      "Fabrica un imán de objetos, enciéndelo y no vuelvas a perseguir lo que cae")
    A("electric_goggles", "electric_charger", "night_vision_goggles", ["night_vision_goggles"], "Night Owl",
      "Craft Night-Vision Goggles and wear them charged", "Ave nocturna",
      "Fabrica unas gafas de visión nocturna y póntelas cargadas")
    A("electric_floodlight", "electric_power", "floodlight", ["floodlight"], "Stadium Lights",
      "Build a Floodlight and light up a field at night", "Luces de estadio",
      "Construye un reflector e ilumina un campo de noche")
    A("automation_breaker", "age_automation", "block_breaker", ["block_breaker"], "Demolition Crew",
      "Build a Block Breaker", "Cuadrilla de demolición", "Construye un rompedor de bloques")
    A("automation_placer", "automation_breaker", "block_placer", ["block_placer"], "Brick by Brick",
      "Build a Block Placer (point a Breaker at what it places for an endless block farm)", "Ladrillo a ladrillo",
      "Construye un colocador de bloques (apunta un rompedor a lo que coloca para una granja de bloques infinita)")
    A("automation_vacuum", "age_automation", "vacuum_hopper", ["vacuum_hopper"], "Hoovering Up",
      "Build a Vacuum Hopper to collect every drop around it", "Aspiradora",
      "Construye una tolva aspiradora para recoger todo lo que cae alrededor")
    A("automation_tree_farm", "age_automation", "tree_farm", ["tree_farm"], "Lumberjack",
      "Build a Tree Farm: it plants and fells a whole grove", "Leñador",
      "Construye una granja de árboles: planta y tala una arboleda entera", frame="goal")
    A("industrial_grinder", "age_industrial", "industrial_grinder", ["industrial_grinder"], "Grind Harder",
      "Build an Industrial Grinder: 4 dust from every raw ore", "Molienda industrial",
      "Construye una moledora industrial: 4 polvos por mineral en bruto")
    A("industrial_recycler", "age_industrial", "recycler", ["recycler", "scrap_box"], "Waste Not",
      "Build a Recycler and open a Scrap Box", "Nada se desperdicia",
      "Construye una recicladora y abre una caja de chatarra")
    A("industrial_mob_farm", "age_industrial", "mob_farm", ["mob_farm"], "Factory Farming",
      "Build a Mob Farm Controller and feed it a filled Mob Capsule", "Granja industrial",
      "Construye un controlador de granja de criaturas y dale una cápsula llena", frame="goal")


# ============================================================ lang

LANG = [
    ("item.factoryascent.bronze_helmet", "Bronze Helmet", "Casco de bronce"),
    ("item.factoryascent.bronze_chestplate", "Bronze Chestplate", "Peto de bronce"),
    ("item.factoryascent.bronze_leggings", "Bronze Leggings", "Grebas de bronce"),
    ("item.factoryascent.bronze_boots", "Bronze Boots", "Botas de bronce"),
    ("item.factoryascent.bronze_backpack", "Bronze Backpack", "Mochila de bronce"),
    ("item.factoryascent.grappling_hook", "Grappling Hook", "Gancho de escalada"),
    ("item.factoryascent.item_magnet", "Item Magnet", "Imán de objetos"),
    ("item.factoryascent.night_vision_goggles", "Night-Vision Goggles", "Gafas de visión nocturna"),
    ("item.factoryascent.tin_nugget", "Tin Nugget", "Pepita de estaño"),
    ("item.factoryascent.scrap", "Scrap", "Chatarra"),
    ("item.factoryascent.scrap_box", "Scrap Box", "Caja de chatarra"),
    ("jei.factoryascent.sifting", "Sifting", "Cribado"),
    ("jei.factoryascent.drying", "Drying", "Secado"),
    ("jei.factoryascent.recycling", "Recycling", "Reciclaje"),
    ("tooltip.factoryascent.kinetic.water_wheel",
     "Turns the handle of hand-cranked machines touching it. Flowing water turns it fastest",
     "Gira la manivela de las máquinas manuales que toca. El agua corriente la mueve más rápido"),
    ("tooltip.factoryascent.kinetic.windmill",
     "Turns the handle of hand-cranked machines touching it. Faster up high and in storms; needs a 3×3 of air in front",
     "Gira la manivela de las máquinas manuales que toca. Más rápido en lo alto y con tormenta; necesita 3×3 de aire delante"),
    ("tooltip.factoryascent.magnet_on", "On: pulling items", "Encendido: atrae objetos"),
    ("tooltip.factoryascent.magnet_off", "Off (use to switch on)", "Apagado (úsalo para encenderlo)"),
    ("tooltip.factoryascent.item_magnet", "Pulls dropped items within %s blocks to you. Charge it in a Charger",
     "Atrae hacia ti los objetos tirados a %s bloques. Cárgalo en un cargador"),
    ("tooltip.factoryascent.night_vision_goggles", "Worn and charged: night vision (%s FE/s)",
     "Puestas y cargadas: visión nocturna (%s FE/s)"),
    ("tooltip.factoryascent.grappling_hook", "Aim at a block up to %s blocks away and use to be pulled to it",
     "Apunta a un bloque a hasta %s bloques y úsalo para lanzarte hacia él"),
    ("tooltip.factoryascent.backpack", "%s / %s slots used. Use to open", "%s / %s ranuras usadas. Úsala para abrirla"),
    ("tooltip.factoryascent.scrap_box", "Use to open it for a random material (sneak: the whole stack)",
     "Úsala para abrirla y obtener un material al azar (agachado: toda la pila)"),
    ("message.factoryascent.magnet_on", "Item Magnet on", "Imán de objetos encendido"),
    ("message.factoryascent.magnet_off", "Item Magnet off", "Imán de objetos apagado"),
    ("gui.factoryascent.kinetic_info", "Turning at %s%% · drives %s", "Gira al %s%% · mueve %s"),
    ("gui.factoryascent.kinetic_blocked", "Sails blocked: needs open air", "Aspas trabadas: sin aire libre"),
    ("gui.factoryascent.kinetic_no_water", "No water touching it", "Sin agua que la toque"),
    ("gui.factoryascent.kinetic_nothing", "Turning %s%% · nothing to drive", "Gira al %s%% · no mueve nada"),
    ("gui.factoryascent.floodlight_info", "Lighting %s spots", "Iluminando %s puntos"),
    ("gui.factoryascent.mob_farm_info", "Farming: %s", "Produce: %s"),
    ("gui.factoryascent.mob_farm_empty", "Insert a filled capsule", "Pon una cápsula llena"),
    ("gui.factoryascent.crank_hint", "Click (or sneak + right-click the machine) to turn it",
     "Haz clic (o agáchate + clic derecho en la máquina) para girarla"),
]


def lang(ctx):
    for key, en, es in LANG:
        ctx.lang(key, en, es)


def generate(ctx):
    write_models(ctx)
    items(ctx)
    loot(ctx)
    tags(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx)


# ============================================================ preview (python3 tools/features/early.py --preview)

_jar_cache = {}


def _load_tex(name):
    """gen_models.load_tex, also reading vanilla textures ("minecraft:block/x") out of the client jar."""
    if ":" not in name:
        return _orig_load_tex(name)
    if name not in _jar_cache:
        import io
        import zipfile
        import numpy as np
        from PIL import Image
        ns, path = name.split(":", 1)
        with zipfile.ZipFile(ET.CLIENT_JAR) as z:
            img = Image.open(io.BytesIO(z.read(f"assets/{ns}/textures/{path}.png"))).convert("RGBA")
        _jar_cache[name] = np.asarray(img.crop((0, 0, 16, 16))).astype(np.float32)
    return _jar_cache[name]


_orig_load_tex = gm.load_tex


def preview(path):
    from PIL import Image, ImageDraw, ImageFont
    gm.load_tex = _load_tex
    models = [f() for f in MODELS]
    rotors = {k: f() for k, f in ROTORS.items()}
    views = [((1.0, 0.85, -1.25), False, "idle"), ((-1.0, 0.85, -1.25), True, "working"), ((-1.1, 0.9, 1.2), False, "back")]
    cell, pad, cols = 170, 6, 2
    w = cols * (len(views) * cell + pad * 3) + pad
    rows = (len(models) + cols - 1) // cols
    img = Image.new("RGBA", (w, rows * (cell + 30) + pad + 24), (52, 55, 62, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    d.text((pad, 4), "Factory Ascent: early and mid-game additions (idle / working / back). Rotors drawn at rest.",
           fill=(240, 240, 240), font=font)
    for k, mdl in enumerate(models):
        ox = pad + (k % cols) * (len(views) * cell + pad * 3)
        oy = 24 + pad + (k // cols) * (cell + 30)
        d.rectangle([ox - 2, oy - 2, ox + len(views) * cell + 2, oy + cell + 26], fill=(66, 70, 78, 255))
        rotor = rotors.get(mdl.id)
        shown = mdl
        if rotor:
            shown = M(mdl.id, {**mdl.textures, **rotor.textures}, mdl.boxes + rotor.boxes, particle=mdl.particle, desc=mdl.desc)
        scale = 2.6 if mdl.id == "windmill" else 5.6
        for j, (cam, on, _) in enumerate(views):
            r = gm.render(shown, on, cam, cell, scale=scale)
            img.alpha_composite(r, (ox + j * cell, oy))
        d.text((ox + 2, oy + cell + 2), mdl.id, fill=(240, 240, 240), font=font)
        d.text((ox + 2, oy + cell + 13), mdl.desc[:92], fill=(170, 176, 186), font=font)
    img.save(path)
    print("preview:", path)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(ROOT / "tools" / "early_preview.png")

"""Planetary outposts (Java: outpost/): what the planets' materials are for, and getting home.

  Moon     Lunar Glass (smelted Moon Regolith): station windows, solar arrays, the Dyson Receiver Array;
           Helium-3 goes into Stellar Alloy (dyson.py)
  Mars     Martian Steel (hematite + steel in the Alloy Smelter, or hematite alone in the Blast Furnace):
           Docking Port (stations.py), Mass Driver Rail (dyson.py), Fusion Casing (power.py), the Fuel
           Synthesizer; Martian ice: Oxygen Cells (Electrolyzer) and Hydrolox Cells (Fuel Synthesizer)
  Io       Sulfur (crushed Io rock): gunpowder, Sulfuric Acid Cells; acid + Ionite: Quantum Circuits for the
           Dyson Collector, Mass Driver and Dyson Receiver (dyson.py)
  stone    polished / bricks / tiles / brick slab, stairs and wall of Moon, Mars and Io rock (crafting and
           stonecutter), airtight for stations; the bare rocks count as cobblestone (furnace, stone tools)
  home     Ascent Module (climb from a planet to Earth orbit), Distress Beacon (a teammate's shuttle can
           fly to it), Fuel Synthesizer (machine: MachineType.FUEL_SYNTHESIZER, recipe kind synthesis)
Models, blockstates, loot, recipes, tags, advancements and lang (English + Spanish).
"""

MOD = "factoryascent"
ROCKS = {"moon_rock": ("Moon Rock", "roca lunar", "de roca lunar"), "mars_rock": ("Martian Rock", "roca marciana", "de roca marciana"),
         "io_rock": ("Io Rock", "roca de Ío", "de roca de Ío")}
SHAPES = ("polished", "bricks", "tiles", "slab", "stairs", "wall")


def fam(rock):
    """Block ids of one rock's family."""
    return {"polished": f"polished_{rock}", "bricks": f"{rock}_bricks", "tiles": f"{rock}_tiles",
            "slab": f"{rock}_brick_slab", "stairs": f"{rock}_brick_stairs", "wall": f"{rock}_brick_wall"}


DECOR = [b for r in ROCKS for b in fam(r).values()]
FULL_DECOR = [fam(r)[s] for r in ROCKS for s in ("polished", "bricks", "tiles")]
PICKAXE_BLOCKS = DECOR + ["lunar_glass", "ascent_module", "distress_beacon"]
ITEMS = ["sulfur", "martian_steel_ingot", "sulfuric_acid_cell", "quantum_circuit", "hydrolox_fuel_cell", "oxygen_cell"]

# vanilla stairs blockstate pattern: (facing, half, shape) -> (model suffix, x, y)
STAIRS = {}
for _f, _base in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
    for _half in ("bottom", "top"):
        for _shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
            _sfx = "" if _shape == "straight" else ("_inner" if _shape.startswith("inner") else "_outer")
            _y = _base
            if _half == "bottom" and _shape.endswith("left"):
                _y -= 90
            if _half == "top" and _shape.endswith("right"):
                _y += 90
            STAIRS[(_f, _half, _shape)] = (_sfx, 180 if _half == "top" else 0, _y % 360)


def ns(x):
    return x if ":" in x or x.startswith("#") else f"{MOD}:{x}"


def t(name):
    return f"{MOD}:block/{name}"


# ============================================================ models

def box(f, to, faces, rot=None):
    """An element with automatic UVs from its coordinates; faces: {face: texture key}."""
    x0, y0, z0 = f
    x1, y1, z1 = to
    uv = {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
          "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
          "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}
    el = {"from": list(f), "to": list(to),
          "faces": {k: {"uv": [max(0, min(16, v)) for v in uv[k]], "texture": "#" + tex} for k, tex in faces.items()}}
    if rot:
        el["rotation"] = rot
    return el


def all_faces(tex, top=None, bottom=None):
    return {"north": tex, "south": tex, "east": tex, "west": tex, "up": top or tex, "down": bottom or top or tex}


def synthesizer_model(on):
    front = "front_on" if on else "front"
    els = [box((0, 0, 0), (16, 12, 16), {"north": front, "south": "side", "east": "side", "west": "side", "up": "top", "down": "top"}),
           box((2, 12, 4), (7, 16, 12), all_faces("tank_h", "top")),
           box((9, 12, 4), (14, 16, 12), all_faces("tank_o", "top")),
           box((7, 13, 7), (9, 15, 9), all_faces("top"))]
    tex = {"front": t("fuel_synthesizer_front"), "front_on": t("fuel_synthesizer_front_on"), "side": t("fuel_synthesizer_side"),
           "top": t("fuel_synthesizer_top"), "tank_h": t("fuel_synthesizer_tank_h"), "tank_o": t("fuel_synthesizer_tank_o"),
           "particle": t("fuel_synthesizer_side")}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def ascent_model():
    els = [box((4, 1, 4), (12, 5, 12), all_faces("booster", "booster", "nozzle"))]
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):  # landing legs with foot pads
        els.append(box((x + 0.5, 1, z + 0.5), (x + 1.5, 6, z + 1.5), all_faces("booster")))
        els.append(box((x, 0, z), (x + 2, 1, z + 2), all_faces("booster")))
    els += [box((3, 5, 3), (13, 11, 13), all_faces("hull")),
            box((4, 11, 4), (12, 13, 12), all_faces("hull")),
            box((5.5, 13, 5.5), (10.5, 15, 10.5), all_faces("hull")),
            box((7.5, 15, 7.5), (8.5, 16, 8.5), all_faces("booster")),
            box((5.5, 7, 2.5), (10.5, 10, 3), {"north": "window", "east": "booster", "west": "booster", "up": "booster", "down": "booster"})]
    tex = {"hull": t("ascent_hull"), "booster": t("ascent_booster"), "nozzle": t("ascent_nozzle"), "window": t("ascent_window"),
           "particle": t("ascent_hull")}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def beacon_model():
    els = []
    for x, z in ((3, 3), (11, 3), (3, 11), (11, 11)):  # tripod-ish legs
        els.append(box((x, 0, z), (x + 2, 5, z + 2), all_faces("leg")))
    els += [box((4, 4, 4), (12, 11, 12), all_faces("body")),
            box((5, 11, 5), (11, 14, 11), all_faces("lens")),
            box((7.5, 14, 7.5), (8.5, 15, 8.5), all_faces("leg"))]
    tex = {"leg": t("distress_beacon_leg"), "body": t("distress_beacon_body"), "lens": t("distress_beacon_lens"),
           "particle": t("distress_beacon_body")}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def blocks(ctx):
    A, D = ctx.ASSETS, ctx.DATA
    # Lunar Glass: translucent like the station window
    ctx.block_model("lunar_glass", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                    "textures": {"all": t("lunar_glass")}})
    ctx.write(A / "blockstates" / "lunar_glass.json", {"variants": {"": {"model": f"{MOD}:block/lunar_glass"}}})
    ctx.item_def("lunar_glass", "block/lunar_glass")
    ctx.loot_self("lunar_glass")
    # decorative families
    for rock in ROCKS:
        b = fam(rock)
        for s in ("polished", "bricks", "tiles"):
            ctx.block_model(b[s], {"parent": "minecraft:block/cube_all", "textures": {"all": t(b[s])}})
            ctx.write(A / "blockstates" / f"{b[s]}.json", {"variants": {"": {"model": f"{MOD}:block/{b[s]}"}}})
        brick = t(b["bricks"])
        tex = {"bottom": brick, "top": brick, "side": brick}
        ctx.block_model(b["slab"], {"parent": "minecraft:block/slab", "textures": tex})
        ctx.block_model(f"{b['slab']}_top", {"parent": "minecraft:block/slab_top", "textures": tex})
        ctx.write(A / "blockstates" / f"{b['slab']}.json", {"variants": {
            "type=bottom": {"model": f"{MOD}:block/{b['slab']}"}, "type=top": {"model": f"{MOD}:block/{b['slab']}_top"},
            "type=double": {"model": f"{MOD}:block/{b['bricks']}"}}})
        for sfx, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            ctx.block_model(f"{b['stairs']}{sfx}", {"parent": f"minecraft:block/{parent}", "textures": tex})
        variants = {}
        for (f, half, shape), (sfx, x, y) in sorted(STAIRS.items()):
            v = {"model": f"{MOD}:block/{b['stairs']}{sfx}"}
            if x:
                v["x"] = x
            if y:
                v["y"] = y
            if x or y:
                v["uvlock"] = True
            variants[f"facing={f},half={half},shape={shape}"] = v
        ctx.write(A / "blockstates" / f"{b['stairs']}.json", {"variants": variants})
        w = b["wall"]
        ctx.block_model(f"{w}_post", {"parent": "minecraft:block/template_wall_post", "textures": {"wall": brick}})
        ctx.block_model(f"{w}_side", {"parent": "minecraft:block/template_wall_side", "textures": {"wall": brick}})
        ctx.block_model(f"{w}_side_tall", {"parent": "minecraft:block/template_wall_side_tall", "textures": {"wall": brick}})
        ctx.block_model(f"{w}_inventory", {"parent": "minecraft:block/wall_inventory", "textures": {"wall": brick}})
        parts = [{"apply": {"model": f"{MOD}:block/{w}_post"}, "when": {"up": "true"}}]
        for kind, sfx in (("low", "_side"), ("tall", "_side_tall")):
            for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                apply = {"model": f"{MOD}:block/{w}{sfx}", "uvlock": True}
                if y:
                    apply["y"] = y
                parts.append({"apply": apply, "when": {side: kind}})
        ctx.write(A / "blockstates" / f"{w}.json", {"multipart": parts})
        for name in b.values():
            ctx.item_def(name, f"block/{w}_inventory" if name == w else f"block/{name}")
            if name != b["slab"]:
                ctx.loot_self(name)
        ctx.write(D / MOD / "loot_table" / "blocks" / f"{b['slab']}.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b['slab']}",
                "functions": [{"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [
                    {"condition": "minecraft:block_state_property", "block": f"{MOD}:{b['slab']}", "properties": {"type": "double"}}]},
                    {"function": "minecraft:explosion_decay"}]}]}],
            "random_sequence": f"{MOD}:blocks/{b['slab']}"})
    # the machine (MachineType.FUEL_SYNTHESIZER): the core writes its blockstate; these replace its placeholder models
    ctx.block_model("fuel_synthesizer", synthesizer_model(False))
    ctx.block_model("fuel_synthesizer_on", synthesizer_model(True))
    # Ascent Module (faces the player who placed it) and Distress Beacon
    ctx.block_model("ascent_module", ascent_model())
    ctx.write(A / "blockstates" / "ascent_module.json", {"variants": {
        f"facing={f}": {"model": f"{MOD}:block/ascent_module", **({"y": r} if r else {})}
        for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    ctx.item_def("ascent_module", "block/ascent_module")
    ctx.loot_self("ascent_module")
    ctx.block_model("distress_beacon", beacon_model())
    ctx.write(A / "blockstates" / "distress_beacon.json", {"variants": {"": {"model": f"{MOD}:block/distress_beacon"}}})
    ctx.item_def("distress_beacon", "block/distress_beacon")
    ctx.loot_self("distress_beacon")
    for item in ITEMS:
        ctx.flat_item(item)


# ============================================================ tags

def tags(ctx):
    rocks = [f"{MOD}:{r}" for r in ROCKS]
    ctx.add_tag(MOD, "item", "planet_rock", rocks)
    # a stranded astronaut can build a furnace and stone tools out of the local rock
    ctx.add_tag("minecraft", "item", "stone_crafting_materials", rocks)
    ctx.add_tag("minecraft", "item", "stone_tool_materials", rocks)
    ctx.add_tag("minecraft", "item", "cobblestone_tool_materials", rocks)
    for kind in ("block", "item"):
        ctx.add_tag("minecraft", kind, "walls", [f"{MOD}:{fam(r)['wall']}" for r in ROCKS])
        ctx.add_tag("minecraft", kind, "slabs", [f"{MOD}:{fam(r)['slab']}" for r in ROCKS])
        ctx.add_tag("minecraft", kind, "stairs", [f"{MOD}:{fam(r)['stairs']}" for r in ROCKS])
    ctx.add_tag("c", "item", "glass_blocks", [f"{MOD}:lunar_glass"])
    ctx.add_tag("c", "block", "glass_blocks", [f"{MOD}:lunar_glass"])
    ctx.add_tag("c", "item", "ingots", ["#c:ingots/martian_steel"])
    ctx.add_tag("c", "item", "ingots/martian_steel", [f"{MOD}:martian_steel_ingot"])
    ctx.add_tag("c", "item", "dusts", ["#c:dusts/sulfur"])
    ctx.add_tag("c", "item", "dusts/sulfur", [f"{MOD}:sulfur"])
    # station builders: the full stone blocks and Lunar Glass hold air like hull plating
    ctx.add_tag(MOD, "block", "airtight", [f"{MOD}:{b}" for b in FULL_DECOR] + [f"{MOD}:lunar_glass"])


# ============================================================ recipes

def write_recipe(ctx, name, obj):
    ctx.write(ctx.DATA / MOD / "recipe" / f"{name}.json", obj)


def cook(ctx, name, inp, out, xp=0.4, time=200):
    for kind, mult, sfx in (("smelting", 1, ""), ("blasting", 0.5, "_blasting")):
        write_recipe(ctx, name + sfx, {"type": f"minecraft:{kind}", "category": "blocks", "ingredient": ns(inp),
                                       "result": {"id": ns(out)}, "experience": xp, "cookingtime": int(time * mult)})


def cut(ctx, name, inp, out, count=1):
    obj = {"type": "minecraft:stonecutting", "ingredient": ns(inp), "result": {"id": ns(out)}}
    if count != 1:
        obj["result"]["count"] = count
    write_recipe(ctx, f"{name}_stonecutting", obj)


def recipes(ctx):
    TP = "#c:plates/titanium"
    # ---- Moon: Lunar Glass and what it's for
    cook(ctx, "lunar_glass", "moon_regolith", "lunar_glass")
    ctx.shaped("station_window_from_lunar_glass", [" G ", "GSG", " G "], {"G": "lunar_glass", "S": "#c:plates/steel"},
               "station_window", count=8, category="building")
    ctx.shaped("solar_array_from_lunar_glass", ["GGG", "PCP", "SWS"], {"G": "lunar_glass", "P": "#c:plates/aluminum",
               "C": "advanced_circuit", "S": "solar_panel", "W": "#c:wires/gold"}, "solar_array", count=2)
    # ---- Mars: Martian Steel, Oxygen Cells, Hydrolox
    ctx.machine("alloying", "martian_steel_ingot", [("raw_hematite", 2), ("#c:ingots/steel", 1)], "martian_steel_ingot",
                count=2, time=160, min_grade=3)
    ctx.machine("blasting", "martian_steel_from_hematite", [("raw_hematite", 2)], "martian_steel_ingot", time=600)
    ctx.machine("electrolysis", "oxygen_cell", [("martian_ice", 1), ("empty_cell", 1)], "oxygen_cell", time=160, min_grade=6)
    ctx.machine("synthesis", "hydrolox_fuel_cell", [("martian_ice", 2), ("empty_cell", 1)], "hydrolox_fuel_cell", time=200, min_grade=6)
    ctx.machine("synthesis", "helium_3_fuel_cell", [("helium_3", 3), ("empty_cell", 1)], "helium_3_fuel_cell", time=300, min_grade=6)
    ctx.machine("synthesis", "rocket_fuel_from_sulfur", [("sulfur", 4), ("empty_cell", 1)], "rocket_fuel", count=2, time=200, min_grade=6)
    # ---- Io: sulfur, acid, quantum circuits
    ctx.machine("crushing", "sulfur_from_io_rock", [("io_rock", 1)], "sulfur", time=60, byproduct=("sulfur", 0.5))
    ctx.shapeless("gunpowder_from_sulfur", ["sulfur", "sulfur", "#c:dusts/coal", "minecraft:bone_meal"], "minecraft:gunpowder", count=3)
    write_recipe(ctx, "sulfuric_acid_cell", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [
        ns("empty_cell"), ns("sulfur"), ns("sulfur"), ["minecraft:water_bucket", f"{MOD}:martian_ice"]],
        "result": {"id": ns("sulfuric_acid_cell")}})
    ctx.machine("assembling", "quantum_circuit", [("advanced_circuit", 1), ("ionite", 2), ("sulfuric_acid_cell", 1),
                                                  ("#c:wires/gold", 2)], "quantum_circuit", count=2, time=300, min_grade=6,
                byproduct=("empty_cell", 1.0))
    # ---- decorative stone (crafting and stonecutter)
    for rock in ROCKS:
        b = fam(rock)
        ctx.shaped(b["polished"], ["RR", "RR"], {"R": rock}, b["polished"], count=4, category="building")
        ctx.shaped(b["bricks"], ["PP", "PP"], {"P": b["polished"]}, b["bricks"], count=4, category="building")
        ctx.shaped(b["tiles"], ["BB", "BB"], {"B": b["bricks"]}, b["tiles"], count=4, category="building")
        ctx.shaped(b["slab"], ["BBB"], {"B": b["bricks"]}, b["slab"], count=6, category="building")
        ctx.shaped(b["stairs"], ["B  ", "BB ", "BBB"], {"B": b["bricks"]}, b["stairs"], count=4, category="building")
        ctx.shaped(b["wall"], ["BBB", "BBB"], {"B": b["bricks"]}, b["wall"], count=6, category="building")
        for shape, count in (("polished", 1), ("bricks", 1), ("tiles", 1), ("slab", 2), ("stairs", 1), ("wall", 1)):
            cut(ctx, f"{b[shape]}_from_{rock}", rock, b[shape], count)
        for shape, count in (("tiles", 1), ("slab", 2), ("stairs", 1), ("wall", 1)):
            cut(ctx, f"{b[shape]}_from_{b['bricks']}", b["bricks"], b[shape], count)
        cut(ctx, f"{b['bricks']}_from_{b['polished']}", b["polished"], b["bricks"])
    # ---- getting home
    ctx.shaped("ascent_module", ["IGI", "ICI", "RFR"], {"I": "#c:ingots/iron", "G": "#c:glass_blocks", "C": "advanced_circuit",
                                                       "R": f"#{MOD}:planet_rock", "F": "minecraft:furnace"}, "ascent_module")
    ctx.shaped("distress_beacon", ["GLG", "RCR", "III"], {"G": "#c:glass_blocks", "L": "minecraft:redstone_lamp",
                                                         "R": "minecraft:redstone", "C": "basic_circuit", "I": "#c:ingots/iron"},
               "distress_beacon")
    ctx.shaped("fuel_synthesizer", ["MCM", "EFE", "MHM"], {"M": "martian_steel_ingot", "C": "advanced_circuit", "E": "empty_cell",
                                                          "F": "machine_frame", "H": "heating_coil"}, "fuel_synthesizer")


# ============================================================ advancements

def code_advancement(ctx, key, parent, icon, frame, en, es):
    display = {"icon": {"id": ns(icon)}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}", "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": display, "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def advancements(ctx):
    A = ctx.advancement
    A("outpost_lunar_glass", "planet_moon", "lunar_glass", ["lunar_glass"], "Moonlight Through Glass",
      "Smelt Moon Regolith into Lunar Glass", "Luz de luna a través del cristal", "Funde regolito lunar en cristal lunar")
    A("outpost_architect", "outpost_lunar_glass", "moon_rock_bricks", FULL_DECOR, "Planetary Architect",
      "Cut planet rock into polished stone, bricks or tiles", "Arquitecto planetario",
      "Talla roca planetaria en piedra pulida, ladrillos o baldosas")
    A("outpost_martian_steel", "planet_mars", "martian_steel_ingot", ["martian_steel_ingot"], "Red Steel",
      "Alloy Martian hematite into Martian Steel", "Acero rojo", "Alea hematita marciana en acero marciano")
    A("outpost_fuel_synthesizer", "outpost_martian_steel", "fuel_synthesizer", ["hydrolox_fuel_cell"], "Living off the Land",
      "Synthesize a Hydrolox Cell from Martian ice", "Vivir de la tierra", "Sintetiza una celda de hidrolox con hielo marciano")
    A("outpost_sulfur", "planet_io", "sulfur", ["sulfur"], "Brimstone", "Crush Io's rock for sulfur",
      "Azufre", "Tritura la roca de Ío para obtener azufre")
    A("outpost_quantum_circuit", "outpost_sulfur", "quantum_circuit", ["quantum_circuit"], "Etched in Ionite",
      "Etch Ionite with sulfuric acid into a Quantum Circuit", "Grabado en ionita",
      "Graba ionita con ácido sulfúrico en un circuito cuántico", frame="goal")
    code_advancement(ctx, "outpost_ascent", "planet_moon", "ascent_module", "goal",
                     ("Ascent Stage", "Climb from a planet back to Earth orbit in an Ascent Module"),
                     ("Etapa de ascenso", "Vuelve de un planeta a la órbita terrestre en un módulo de ascenso"))
    code_advancement(ctx, "outpost_beacon", "planet_moon", "distress_beacon", "task",
                     ("Mayday", "Light a Distress Beacon on a planet"),
                     ("Mayday", "Enciende una baliza de socorro en un planeta"))


# ============================================================ lang

def lang(L):
    B, I, T, M, G = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"gui.{MOD}"
    L(f"{B}.lunar_glass", "Lunar Glass", "Cristal lunar")
    for rock, (en, es, de) in ROCKS.items():
        b = fam(rock)
        L(f"{B}.{b['polished']}", f"Polished {en}", f"{es[0].upper() + es[1:]} pulida")
        L(f"{B}.{b['bricks']}", f"{en} Bricks", f"Ladrillos {de}")
        L(f"{B}.{b['tiles']}", f"{en} Tiles", f"Baldosas {de}")
        L(f"{B}.{b['slab']}", f"{en} Brick Slab", f"Losa de ladrillos {de}")
        L(f"{B}.{b['stairs']}", f"{en} Brick Stairs", f"Escaleras de ladrillos {de}")
        L(f"{B}.{b['wall']}", f"{en} Brick Wall", f"Muro de ladrillos {de}")
    L(f"{B}.ascent_module", "Ascent Module", "Módulo de ascenso")
    L(f"{B}.distress_beacon", "Distress Beacon", "Baliza de socorro")
    for key, en, es in [
        ("sulfur", "Sulfur", "Azufre"), ("martian_steel_ingot", "Martian Steel Ingot", "Lingote de acero marciano"),
        ("sulfuric_acid_cell", "Sulfuric Acid Cell", "Celda de ácido sulfúrico"), ("quantum_circuit", "Quantum Circuit", "Circuito cuántico"),
        ("hydrolox_fuel_cell", "Hydrolox Cell", "Celda de hidrolox"), ("oxygen_cell", "Oxygen Cell", "Celda de oxígeno"),
    ]:
        L(f"{I}.{key}", en, es)
    L(f"jei.{MOD}.synthesis", "Fuel Synthesis", "Síntesis de combustible")
    for key, en, es in [
        ("sulfur", "Crushed out of Io's rock. Gunpowder, acid and rocket propellant",
         "Triturado de la roca de Ío. Pólvora, ácido y propelente de cohete"),
        ("martian_steel_ingot", "Hematite alloyed with steel: for planetary-grade parts",
         "Hematita aleada con acero: para piezas de grado planetario"),
        ("sulfuric_acid_cell", "Etches Ionite into Quantum Circuits", "Graba ionita en circuitos cuánticos"),
        ("quantum_circuit", "Ionite circuitry: the Dyson Cube's machines think with it",
         "Circuitería de ionita: las máquinas del cubo de Dyson piensan con ella"),
        ("hydrolox_fuel_cell", "Shuttle fuel made from Martian ice: worth 2 Rocket Fuel",
         "Combustible de transbordador hecho con hielo marciano: vale 2 combustibles de cohete"),
        ("oxygen_cell", "Use it while wearing a suit to top up its air, anywhere",
         "Úsala con un traje puesto para recargar su aire, en cualquier lugar"),
        ("oxygen_cell_air", "Holds %s s of air", "Contiene %s s de aire"),
        ("ascent_module", "A one-seat capsule on a booster: use it on a planet to climb back to Earth orbit",
         "Una cápsula monoplaza sobre un propulsor: úsala en un planeta para volver a la órbita terrestre"),
        ("ascent_cost", "From %s: %s fuel", "Desde %s: %s de combustible"),
        ("ascent_module_how", "Carry Rocket Fuel, Hydrolox or Helium-3 Fuel Cells. Spent on the way up",
         "Lleva combustible de cohete, celdas de hidrolox o de helio-3. Se gasta al subir"),
        ("distress_beacon", "Lit on a planet, it calls your team: their shuttles can fly straight to it",
         "Encendida en un planeta, llama a tu equipo: sus transbordadores pueden volar directo a ella"),
        ("distress_beacon_how", "Pick it in a shuttle's Navigation panel (SOS button)",
         "Elígela en el panel de navegación del transbordador (botón SOS)"),
    ]:
        L(f"{T}.{key}", en, es)
    for key, en, es in [
        ("ascent_not_planet", "The Ascent Module only flies from a planet's surface",
         "El módulo de ascenso solo despega desde la superficie de un planeta"),
        ("ascent_no_fuel", "Not enough fuel to reach orbit: %s needed, you carry %s",
         "No hay combustible para llegar a la órbita: hacen falta %s, llevas %s"),
        ("ascent_liftoff", "Ignition! Climbing to Earth orbit...", "¡Ignición! Subiendo a la órbita terrestre..."),
        ("return_pod_planet", "A Return Pod can't climb out of a planet's gravity: build an Ascent Module",
         "Una cápsula de retorno no puede salir de la gravedad de un planeta: construye un módulo de ascenso"),
        ("beacon_not_planet", "A Distress Beacon only calls for help from a planet",
         "Una baliza de socorro solo pide ayuda desde un planeta"),
        ("beacon_lit", "MAYDAY: %s lit a distress beacon on %s at %s, %s",
         "MAYDAY: %s encendió una baliza de socorro en %s en %s, %s"),
        ("beacon_calling", "Calling %s for help", "Pidiendo ayuda a %s"),
        ("oxygen_cell_full", "Your suit's tank is already full", "El tanque de tu traje ya está lleno"),
        ("oxygen_cell_used", "Suit topped up: +%s s of air", "Traje recargado: +%s s de aire"),
        ("nav.no_beacons", "No distress beacons from your team", "Tu equipo no tiene balizas de socorro"),
        ("nav.homing", "Homing on the distress beacon at %s, %s", "Rumbo a la baliza de socorro en %s, %s"),
    ]:
        L(f"{M}.{key}", en, es)
    for key, en, es in [
        ("nav.beacons", "SOS: %s waiting", "SOS: %s esperando"),
        ("nav.beacons_none", "SOS: none", "SOS: ninguna"),
        ("nav.beacon", "SOS %s %s,%s", "SOS %s %s,%s"),
        ("nav.beacon_tip", "Distress beacon %s of %s: click for the next", "Baliza de socorro %s de %s: clic para la siguiente"),
    ]:
        L(f"{G}.{key}", en, es)


def generate(ctx):
    blocks(ctx)
    tags(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx.lang)

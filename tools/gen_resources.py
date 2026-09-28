#!/usr/bin/env python3
"""Generates every JSON resource of Factory Ascent: blockstates, block/item models, item model
definitions, lang files, loot tables, tags, recipes, worldgen, data maps and advancements.

Run from anywhere:  python3 tools/gen_resources.py

Hand-made machine models live in tools/models/block/<id>.json (and <id>_on.json) and are copied
in; a plain placeholder is used for any model that doesn't exist yet. Textures come from
tools/gen_textures.py (and tools/gen_storage_textures.py). The storage network adds its own
resources through tools/storage_resources.py. This script owns (and rewrites) only the folders
it generates.
"""
import importlib.util
import json
import shutil
from pathlib import Path

MOD = "factoryascent"
TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / MOD
DATA = ROOT / "data"
MODEL_SRC = TOOLS / "models" / "block"
TEXTURES = ASSETS / "textures"

AGES = ["stone", "bronze", "electric", "automation", "industrial", "orbital", "quantum"]

# id -> (age, has facing, has working model, description en, description es)
MACHINES = {
    "quern": ("stone", False, False,
              "Hand-cranked millstone: grinds raw ore into dust (1 + 50% chance of a second). Sneak + right-click or use the Crank button.",
              "Molino de mano: muele mineral en bruto en polvo (1 + 50% de otro). Agáchate + clic derecho o usa el botón Girar."),
    "brick_kiln": ("stone", True, True,
                   "Fuel-fired kiln that alloys copper and tin into bronze.",
                   "Horno de ladrillo a combustible que alea cobre y estaño en bronce."),
    "burner_crusher": ("bronze", True, True,
                       "Fuel-powered crusher: 2 dust per raw ore.",
                       "Trituradora a combustible: 2 polvos por mineral en bruto."),
    "burner_press": ("bronze", True, True,
                     "Fuel-powered press: plates, gears, rods and wires depending on the mould.",
                     "Prensa a combustible: placas, engranajes, varillas y cables según el molde."),
    "coke_oven": ("bronze", True, True,
                  "Multiblock controller: bakes coal into coke (and logs into charcoal). Needs no fuel.",
                  "Controlador multibloque: convierte carbón en coque (y troncos en carbón vegetal). No usa combustible."),
    "blast_furnace": ("bronze", True, True,
                      "Multiblock controller: turns iron into steel, burning coke.",
                      "Controlador multibloque: convierte hierro en acero quemando coque."),
    "electric_furnace": ("electric", True, True,
                         "Smelts anything a furnace can, five times faster, plus aluminium.",
                         "Funde todo lo que funde un horno, cinco veces más rápido, y además aluminio."),
    "crusher": ("electric", True, True,
                "2 dust per raw ore (3 per ore block) plus a chance of a byproduct.",
                "2 polvos por mineral en bruto (3 por bloque de mena) y probabilidad de subproducto."),
    "metal_press": ("electric", True, True,
                    "Fast electric press. The mould decides what it makes.",
                    "Prensa eléctrica rápida. El molde decide qué fabrica."),
    "alloy_smelter": ("electric", True, True,
                      "Alloys bronze, steel and silicon quickly.",
                      "Alea bronce, acero y silicio rápidamente."),
    "assembler": ("electric", True, True,
                  "Builds circuits, motors and machine frames from parts.",
                  "Fabrica circuitos, motores y chasis a partir de piezas."),
    "combustion_generator": ("electric", True, True,
                             "Burns furnace fuel for 40 FE/t.",
                             "Quema combustible de horno para 40 FE/t."),
    "solar_panel": ("electric", False, False,
                    "8 FE/t in daylight under open sky (half in rain).",
                    "8 FE/t con luz del día y cielo abierto (la mitad con lluvia)."),
    "auto_farmer": ("electric", True, True,
                    "Harvests ripe crops in a 9×9 field in front of it and replants them.",
                    "Cosecha los cultivos maduros de un campo de 9×9 frente a él y los replanta."),
    "energy_cell": ("electric", True, False,
                    "Stores energy. Charges items in its slot.",
                    "Almacena energía. Carga objetos en su ranura."),
    "miner": ("automation", False, True,
              "Claims its own chunk in The Deep, a sealed mining dimension, and digs out its ore.",
              "Reclama su propio chunk en Las Profundidades, una dimensión minera sellada, y extrae sus menas."),
    "geothermal_generator": ("automation", True, True,
                             "24 FE/t for every lava source touching it. The lava is never used up.",
                             "24 FE/t por cada fuente de lava que lo toque. La lava nunca se gasta."),
    "advanced_energy_cell": ("automation", True, False, "Stores 4× more energy.", "Almacena 4 veces más energía."),
    "ore_washer": ("automation", True, True,
                   "Washes raw ore into 3 dust (4 from ore blocks), with a better byproduct chance.",
                   "Lava el mineral en bruto en 3 polvos (4 de bloques de mena), con más probabilidad de subproducto."),
    "induction_smelter": ("industrial", True, True,
                          "Induction heating: the only smelter hot enough for titanium. Also smelts anything a furnace can, very fast.",
                          "Calentamiento por inducción: el único horno capaz de fundir titanio. También funde todo lo de un horno, muy rápido."),
    "hydraulic_press": ("industrial", True, True,
                        "Presses titanium into plates and gears. The mould decides what it makes.",
                        "Prensa titanio en placas y engranajes. El molde decide qué fabrica."),
    "industrial_energy_cell": ("industrial", True, False, "Stores 16× more energy.", "Almacena 16 veces más energía."),
    "precision_assembler": ("orbital", True, True,
                            "Clean-room assembler for spacecraft parts such as the Orbital Targeting Core.",
                            "Ensambladora de sala limpia para piezas espaciales como el núcleo de puntería orbital."),
    "plasma_forge": ("orbital", True, True,
                     "Forges quantum alloy in a plasma arc: the way into the Quantum age.",
                     "Forja aleación cuántica en un arco de plasma: la entrada a la Era Cuántica."),
    "quantum_energy_cell": ("quantum", True, False, "Stores 64× more energy.", "Almacena 64 veces más energía."),
}
MACHINE_EN = {
    "quern": "Quern", "brick_kiln": "Brick Kiln", "burner_crusher": "Burner Crusher", "burner_press": "Burner Press",
    "coke_oven": "Coke Oven", "blast_furnace": "Blast Furnace", "electric_furnace": "Electric Furnace",
    "crusher": "Crusher", "metal_press": "Metal Press", "alloy_smelter": "Alloy Smelter", "assembler": "Assembler",
    "combustion_generator": "Combustion Generator", "solar_panel": "Solar Panel", "auto_farmer": "Auto-Farmer",
    "energy_cell": "Energy Cell", "miner": "Ore Miner", "geothermal_generator": "Geothermal Generator",
    "advanced_energy_cell": "Advanced Energy Cell", "industrial_energy_cell": "Industrial Energy Cell",
    "ore_washer": "Ore Washer", "induction_smelter": "Induction Smelter", "hydraulic_press": "Hydraulic Press",
    "precision_assembler": "Precision Assembler", "plasma_forge": "Plasma Forge",
    "quantum_energy_cell": "Quantum Energy Cell",
}
MACHINE_ES = {
    "quern": "Molino de mano", "brick_kiln": "Horno de ladrillo", "burner_crusher": "Trituradora a combustión",
    "burner_press": "Prensa a combustión", "coke_oven": "Horno de coque", "blast_furnace": "Alto horno",
    "electric_furnace": "Horno eléctrico", "crusher": "Trituradora", "metal_press": "Prensa de metal",
    "alloy_smelter": "Fundidora de aleaciones", "assembler": "Ensambladora",
    "combustion_generator": "Generador de combustión", "solar_panel": "Panel solar", "auto_farmer": "Granjero automático",
    "energy_cell": "Celda de energía", "miner": "Minero de menas", "geothermal_generator": "Generador geotérmico",
    "advanced_energy_cell": "Celda de energía avanzada", "industrial_energy_cell": "Celda de energía industrial",
    "ore_washer": "Lavadora de mineral", "induction_smelter": "Fundidora de inducción", "hydraulic_press": "Prensa hidráulica",
    "precision_assembler": "Ensambladora de precisión", "plasma_forge": "Forja de plasma",
    "quantum_energy_cell": "Celda de energía cuántica",
}

CABLES = {"lv": "copper_cable", "mv": "aluminum_cable", "hv": "titanium_cable", "ev": "superconductor_cable"}
PIPES = {"lv": "bronze_item_pipe", "mv": "steel_item_pipe", "hv": "aluminum_item_pipe", "ev": "titanium_item_pipe"}
CABLE_EN = {"copper_cable": "Copper Cable", "aluminum_cable": "Aluminium Cable", "titanium_cable": "Titanium Cable",
            "superconductor_cable": "Superconducting Cable", "bronze_item_pipe": "Bronze Item Pipe",
            "steel_item_pipe": "Steel Item Pipe", "aluminum_item_pipe": "Aluminium Item Pipe",
            "titanium_item_pipe": "Titanium Item Pipe"}
CABLE_ES = {"copper_cable": "Cable de cobre", "aluminum_cable": "Cable de aluminio", "titanium_cable": "Cable de titanio",
            "superconductor_cable": "Cable superconductor", "bronze_item_pipe": "Tubería de bronce",
            "steel_item_pipe": "Tubería de acero", "aluminum_item_pipe": "Tubería de aluminio",
            "titanium_item_pipe": "Tubería de titanio"}

ORES = {  # block -> (raw drop, needs tool, stone/deepslate, metal tag)
    "tin_ore": ("raw_tin", "stone", "stone", "tin"),
    "deepslate_tin_ore": ("raw_tin", "stone", "deepslate", "tin"),
    "bauxite_ore": ("raw_bauxite", "stone", "stone", "aluminum"),
    "deepslate_titanium_ore": ("raw_titanium", "iron", "deepslate", "titanium"),
}
STORAGE_METALS = ["tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"]
DUSTS = ["iron", "copper", "gold", "tin", "coal", "quartz", "bauxite", "titanium"]
INGOTS = ["tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"]
PLATES = ["iron", "copper", "tin", "bronze", "steel", "aluminum", "titanium"]
GEARS = ["iron", "bronze", "steel", "titanium"]
RODS = ["iron", "steel"]
WIRES = ["copper", "gold"]
MOLDS = ["plate", "gear", "rod", "wire"]
MATERIALS = (["raw_tin", "raw_bauxite", "raw_titanium"] + [f"{d}_dust" for d in DUSTS]
             + [f"{i}_ingot" for i in INGOTS] + ["coke", "fire_clay", "fire_brick", "silicon", "silicon_wafer"]
             + [f"{p}_plate" for p in PLATES] + [f"{g}_gear" for g in GEARS] + [f"{r}_rod" for r in RODS]
             + [f"{w}_wire" for w in WIRES]
             + ["motor", "heating_coil", "basic_circuit", "advanced_circuit", "machine_frame", "advanced_machine_frame",
                "orbital_targeting_core"])
HANDHELD = ["forge_hammer", "wrench", "bronze_pickaxe", "bronze_axe", "bronze_shovel", "bronze_hoe", "bronze_sword",
            "electric_drill"]
FLAT_ITEMS = MATERIALS + [f"{m}_mold" for m in MOLDS] + ["speed_upgrade", "energy_upgrade"]
SIMPLE_BLOCKS = ["coke_oven_bricks", "fire_bricks"] + list(ORES) + [f"{m}_block" for m in STORAGE_METALS]
CRATES = {"wooden_crate": 27, "bronze_crate": 54}


def ns(path):
    return path if ":" in path else f"{MOD}:{path}"


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def has_texture(name):
    return (TEXTURES / "block" / f"{name}.png").exists()


def tex(name, fallback):
    return f"{MOD}:block/{name}" if has_texture(name) else fallback


def clean():
    for sub in ["blockstates", "models", "items", "lang"]:
        shutil.rmtree(ASSETS / sub, ignore_errors=True)
    for sub in ["recipe", "loot_table", "tags", "worldgen", "neoforge", "advancement", "data_maps",
                "dimension", "dimension_type"]:
        shutil.rmtree(DATA / MOD / sub, ignore_errors=True)
    for ns_dir in ["c", "minecraft", "neoforge", "orbital_railgun"]:
        shutil.rmtree(DATA / ns_dir, ignore_errors=True)


class FeatureContext:
    """
    What a feature module (tools/features/<name>.py) gets: the generator's helpers. A module defines
    generate(ctx), called after the core resources are written. It may also define
    PICKAXE_BLOCKS / AXE_BLOCKS (lists of block ids) for the mineable tags.
    """
    MOD = MOD

    def __init__(self):
        self.write = write
        self.ing = ing
        self.shaped = shaped
        self.shapeless = shapeless
        self.machine = machine
        self.advancement = advancement
        self.item_def = item_def
        self.block_model = block_model
        self.loot_self = loot_self
        self.ASSETS = ASSETS
        self.DATA = DATA
        self.en = {}
        self.es = {}

    def lang(self, key, en, es):
        """Adds a translation (key without namespace prefix handling: pass the full key)."""
        self.en[key] = en
        self.es[key] = es

    def flat_item(self, name, handheld=False):
        write(ASSETS / "models" / "item" / f"{name}.json", {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": f"{MOD}:item/{name}"}})
        item_def(name, f"item/{name}")


def load_feature_modules():
    modules = []
    for path in sorted((TOOLS / "features").glob("*.py")):
        spec = importlib.util.spec_from_file_location(f"feature_{path.stem}", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        modules.append(module)
    return modules


def load_storage_module():
    path = TOOLS / "storage_resources.py"
    if not path.exists():
        return None
    spec = importlib.util.spec_from_file_location("storage_resources", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# ============================================================ models

def block_model(name, obj):
    write(ASSETS / "models" / "block" / f"{name}.json", obj)


def item_def(name, model):
    write(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": ns(model)}})


def machine_model(mid, variant):
    """Copy the hand-made model if it exists, else a placeholder cube (keeps the game loadable)."""
    src = MODEL_SRC / f"{variant}.json"
    if src.exists():
        block_model(variant, json.loads(src.read_text()))
        return
    front = tex(f"{mid}_front", "minecraft:block/furnace_front")
    side = tex(f"{mid}_side", "minecraft:block/iron_block")
    block_model(variant, {"parent": "minecraft:block/orientable", "textures": {"front": front, "side": side, "top": side}})


FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ["north", "east", "south", "west", "up", "down"]
ARM_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def machines():
    for mid, (age, facing, has_on, _, _) in MACHINES.items():
        machine_model(mid, mid)
        machine_model(mid, f"{mid}_on")
        variants = {}
        for f, rot in FACINGS.items():
            for active in ("false", "true"):
                model = f"{MOD}:block/{mid}" + ("_on" if active == "true" else "")
                v = {"model": model}
                if rot and facing:
                    v["y"] = rot
                variants[f"active={active},facing={f}"] = v
        write(ASSETS / "blockstates" / f"{mid}.json", {"variants": variants})
        item_def(mid, f"block/{mid}")
    # Loose parts drawn by block entity renderers (e.g. the quern's turning runner stone).
    for part in ("quern_runner",):
        if (MODEL_SRC / f"{part}.json").exists():
            block_model(part, json.loads((MODEL_SRC / f"{part}.json").read_text()))


def conduit_models(name):
    t = tex(name, "minecraft:block/iron_block")
    core = {"from": [5, 5, 5], "to": [11, 11, 11], "faces": {d: {"uv": [5, 5, 11, 11], "texture": "#t"} for d in DIRS}}

    def arm(z0, z1):
        return {"from": [5, 5, z0], "to": [11, 11, z1], "faces": {
            "north": {"uv": [5, 5, 11, 11], "texture": "#t"}, "south": {"uv": [5, 5, 11, 11], "texture": "#t"},
            "up": {"uv": [5, z0, 11, z1], "texture": "#t"}, "down": {"uv": [5, z0, 11, z1], "texture": "#t"},
            "east": {"uv": [5, z0, 11, z1], "texture": "#t", "rotation": 90},
            "west": {"uv": [5, z0, 11, z1], "texture": "#t", "rotation": 90}}}
    block_model(f"{name}_core", {"parent": "minecraft:block/block", "textures": {"t": t, "particle": t}, "elements": [core]})
    block_model(f"{name}_arm", {"parent": "minecraft:block/block", "textures": {"t": t, "particle": t}, "elements": [arm(0, 5)]})
    write(ASSETS / "models" / "item" / f"{name}.json", {
        "parent": "minecraft:block/block", "textures": {"t": t, "particle": t},
        "elements": [core, arm(0, 5), arm(11, 16)],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def(name, f"item/{name}")


def cables_and_pipes():
    ext = tex("item_pipe_extract", "minecraft:block/iron_block")
    block_model("item_pipe_extract", {
        "parent": "minecraft:block/block", "textures": {"t": ext, "particle": ext},
        "elements": [{"from": [3, 3, 0], "to": [13, 13, 2], "faces": {
            "north": {"uv": [3, 3, 13, 13], "texture": "#t"}, "south": {"uv": [3, 3, 13, 13], "texture": "#t"},
            "up": {"uv": [3, 0, 13, 2], "texture": "#t"}, "down": {"uv": [3, 0, 13, 2], "texture": "#t"},
            "east": {"uv": [0, 3, 2, 13], "texture": "#t"}, "west": {"uv": [0, 3, 2, 13], "texture": "#t"}}}]})
    for name in CABLES.values():
        conduit_models(name)
        parts = [{"apply": {"model": f"{MOD}:block/{name}_core"}}]
        for d in DIRS:
            parts.append({"when": {d: "true"}, "apply": {"model": f"{MOD}:block/{name}_arm", **ARM_ROT[d]}})
        write(ASSETS / "blockstates" / f"{name}.json", {"multipart": parts})
    for name in PIPES.values():
        conduit_models(name)
        parts = [{"apply": {"model": f"{MOD}:block/{name}_core"}}]
        for d in DIRS:
            parts.append({"when": {d: "connected|extract"}, "apply": {"model": f"{MOD}:block/{name}_arm", **ARM_ROT[d]}})
            parts.append({"when": {d: "extract"}, "apply": {"model": f"{MOD}:block/item_pipe_extract", **ARM_ROT[d]}})
        write(ASSETS / "blockstates" / f"{name}.json", {"multipart": parts})


def simple_blocks():
    for name in SIMPLE_BLOCKS:
        block_model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name, "minecraft:block/stone")}})
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        item_def(name, f"block/{name}")
    for name in CRATES:
        block_model(name, {"parent": "minecraft:block/cube_column", "textures": {
            "side": tex(f"{name}_side", "minecraft:block/barrel_side"),
            "end": tex(f"{name}_top", "minecraft:block/barrel_top")}})
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        item_def(name, f"block/{name}")


def items():
    for name in FLAT_ITEMS + HANDHELD:
        parent = "minecraft:item/handheld" if name in HANDHELD else "minecraft:item/generated"
        write(ASSETS / "models" / "item" / f"{name}.json", {"parent": parent, "textures": {"layer0": f"{MOD}:item/{name}"}})
        item_def(name, f"item/{name}")


# ============================================================ lang

EN_METAL = {"iron": "Iron", "copper": "Copper", "gold": "Gold", "tin": "Tin", "coal": "Coal", "quartz": "Quartz",
            "bauxite": "Bauxite", "titanium": "Titanium", "bronze": "Bronze", "steel": "Steel",
            "aluminum": "Aluminium", "quantum_alloy": "Quantum Alloy"}
ES_METAL = {"iron": "hierro", "copper": "cobre", "gold": "oro", "tin": "estaño", "coal": "carbón", "quartz": "cuarzo",
            "bauxite": "bauxita", "titanium": "titanio", "bronze": "bronce", "steel": "acero",
            "aluminum": "aluminio", "quantum_alloy": "aleación cuántica"}
AGE_EN = {"stone": "Stone Age", "bronze": "Bronze Age", "electric": "Electric Age", "automation": "Automation Age",
          "industrial": "Industrial Age", "orbital": "Orbital Age", "quantum": "Quantum Age"}
AGE_ES = {"stone": "Edad de Piedra", "bronze": "Edad del Bronce", "electric": "Era Eléctrica",
          "automation": "Era de la Automatización", "industrial": "Era Industrial", "orbital": "Era Orbital",
          "quantum": "Era Cuántica"}


def lang(extra_en, extra_es, advancement_text):
    en, es = {}, {}

    def add(key, e, s):
        en[key] = e
        es[key] = s
    for key, e, s_ in [("processing", "Factory Ascent: Processing", "Factory Ascent: Procesado"),
                       ("power", "Factory Ascent: Power", "Factory Ascent: Energía"),
                       ("logistics", "Factory Ascent: Logistics & Storage", "Factory Ascent: Logística y almacenamiento"),
                       ("tools", "Factory Ascent: Tools", "Factory Ascent: Herramientas"),
                       ("materials", "Factory Ascent: Materials", "Factory Ascent: Materiales"),
                       ("world", "Factory Ascent: World", "Factory Ascent: Mundo"),
                       ("orbital", "Factory Ascent: Orbital", "Factory Ascent: Orbital")]:
        add(f"itemGroup.{MOD}.{key}", e, s_)
    for key, e, s_ in [("smelting", "Electric Furnace", "Horno eléctrico"),
                       ("crushing", "Crushing", "Triturado"),
                       ("pressing", "Pressing", "Prensado"),
                       ("alloying", "Alloying", "Aleación"),
                       ("assembling", "Assembling", "Ensamblado"),
                       ("coking", "Coking", "Coquización"),
                       ("blasting", "Steelmaking", "Fabricación de acero")]:
        add(f"jei.{MOD}.{key}", e, s_)
    for a in AGES:
        add(f"age.{MOD}.{a}", AGE_EN[a], AGE_ES[a])
    for t, n in (("lv", "LV"), ("mv", "MV"), ("hv", "HV"), ("ev", "EV")):
        add(f"tier.{MOD}.{t}", n, n)
    for mid, (_, _, _, den, des) in MACHINES.items():
        add(f"block.{MOD}.{mid}", MACHINE_EN[mid], MACHINE_ES[mid])
        add(f"desc.{MOD}.{mid}", den, des)
    for name in list(CABLES.values()) + list(PIPES.values()):
        add(f"block.{MOD}.{name}", CABLE_EN[name], CABLE_ES[name])
    add(f"block.{MOD}.wooden_crate", "Wooden Crate", "Cajón de madera")
    add(f"block.{MOD}.bronze_crate", "Bronze Crate", "Cajón de bronce")
    add(f"block.{MOD}.coke_oven_bricks", "Coke Oven Bricks", "Ladrillos de horno de coque")
    add(f"block.{MOD}.fire_bricks", "Fire Bricks", "Ladrillos refractarios")
    ore_en = {"tin_ore": "Tin Ore", "deepslate_tin_ore": "Deepslate Tin Ore", "bauxite_ore": "Bauxite Ore",
              "deepslate_titanium_ore": "Deepslate Titanium Ore"}
    ore_es = {"tin_ore": "Mena de estaño", "deepslate_tin_ore": "Mena de estaño de pizarra profunda",
              "bauxite_ore": "Mena de bauxita", "deepslate_titanium_ore": "Mena de titanio de pizarra profunda"}
    for k in ORES:
        add(f"block.{MOD}.{k}", ore_en[k], ore_es[k])
    for m in STORAGE_METALS:
        add(f"block.{MOD}.{m}_block", f"Block of {EN_METAL[m]}", f"Bloque de {ES_METAL[m]}")
    for r in ["tin", "bauxite", "titanium"]:
        add(f"item.{MOD}.raw_{r}", f"Raw {EN_METAL[r]}", f"{ES_METAL[r].capitalize()} en bruto")
    for d in DUSTS:
        add(f"item.{MOD}.{d}_dust", f"{EN_METAL[d]} Dust", f"Polvo de {ES_METAL[d]}")
    for i in INGOTS:
        add(f"item.{MOD}.{i}_ingot", f"{EN_METAL[i]} Ingot", f"Lingote de {ES_METAL[i]}")
    for key, e, s in [("coke", "Coke", "Coque"), ("fire_clay", "Fire Clay", "Arcilla refractaria"),
                      ("fire_brick", "Fire Brick", "Ladrillo refractario"), ("silicon", "Silicon", "Silicio"),
                      ("silicon_wafer", "Silicon Wafer", "Oblea de silicio"), ("motor", "Motor", "Motor"),
                      ("heating_coil", "Heating Coil", "Resistencia calefactora"),
                      ("basic_circuit", "Basic Circuit", "Circuito básico"),
                      ("advanced_circuit", "Advanced Circuit", "Circuito avanzado"),
                      ("orbital_targeting_core", "Orbital Targeting Core", "Núcleo de puntería orbital"),
                      ("machine_frame", "Machine Frame", "Chasis de máquina"),
                      ("advanced_machine_frame", "Advanced Machine Frame", "Chasis de máquina avanzado"),
                      ("speed_upgrade", "Speed Upgrade", "Mejora de velocidad"),
                      ("energy_upgrade", "Energy Upgrade", "Mejora de energía"),
                      ("forge_hammer", "Forge Hammer", "Martillo de forja"), ("wrench", "Wrench", "Llave inglesa"),
                      ("bronze_pickaxe", "Bronze Pickaxe", "Pico de bronce"), ("bronze_axe", "Bronze Axe", "Hacha de bronce"),
                      ("bronze_shovel", "Bronze Shovel", "Pala de bronce"), ("bronze_hoe", "Bronze Hoe", "Azada de bronce"),
                      ("bronze_sword", "Bronze Sword", "Espada de bronce"),
                      ("electric_drill", "Electric Drill", "Taladro eléctrico")]:
        add(f"item.{MOD}.{key}", e, s)
    for p in PLATES:
        add(f"item.{MOD}.{p}_plate", f"{EN_METAL[p]} Plate", f"Placa de {ES_METAL[p]}")
    for g in GEARS:
        add(f"item.{MOD}.{g}_gear", f"{EN_METAL[g]} Gear", f"Engranaje de {ES_METAL[g]}")
    for r in RODS:
        add(f"item.{MOD}.{r}_rod", f"{EN_METAL[r]} Rod", f"Varilla de {ES_METAL[r]}")
    for w in WIRES:
        add(f"item.{MOD}.{w}_wire", f"{EN_METAL[w]} Wire", f"Cable de {ES_METAL[w]}")
    mold_es = {"plate": "placas", "gear": "engranajes", "rod": "varillas", "wire": "cables"}
    for m in MOLDS:
        add(f"item.{MOD}.{m}_mold", f"{m.capitalize()} Mould", f"Molde de {mold_es[m]}")

    T = f"tooltip.{MOD}"
    for key, e, s in [
        ("age", "%s", "%s"),
        ("power.manual", "Powered by hand", "Funciona a mano"),
        ("power.fuel", "Burns furnace fuel", "Quema combustible de horno"),
        ("power.electric", "Needs FE (Forge Energy)", "Necesita FE (energía)"),
        ("power.none", "Needs no power", "No necesita energía"),
        ("energy_use", "Uses %s FE/t while working", "Consume %s FE/t mientras trabaja"),
        ("upgrade_slot_count", "Upgrade slots: %s", "Ranuras de mejora: %s"),
        ("miner_radius", "Claim: %s×%s chunk column in The Deep", "Reclamo: columna de %s×%s chunks en Las Profundidades"),
        ("generation", "Generates %s FE/t", "Genera %s FE/t"),
        ("generation_per_lava", "Generates %s FE/t per touching lava source", "Genera %s FE/t por fuente de lava adyacente"),
        ("capacity", "Capacity: %s FE", "Capacidad: %s FE"),
        ("transfer", "Transfer: %s FE/t", "Transferencia: %s FE/t"),
        ("cell_front", "Power comes OUT of the lightning-bolt face and goes IN through every other face. Right-click a side with a Wrench (or use the Sides tab of its screen) to make it the output. The slot charges tools",
         "La energía SALE por la cara del rayo y ENTRA por las demás. Clic derecho en un lado con la llave (o usa la pestaña Lados de su pantalla) para que sea la salida. La ranura carga herramientas"),
        ("cable_rate", "Network throughput: %s FE/t", "Capacidad de red: %s FE/t"),
        ("cable_bottleneck", "A network runs at the rate of its slowest cable", "Una red funciona al ritmo de su cable más lento"),
        ("pipe_rate", "Pulls up to %s items/s per extracting face", "Extrae hasta %s objetos/s por cara de extracción"),
        ("pipe_hint", "Wrench a face touching an inventory to pull from it, or right-click the pipe with an empty hand",
         "Usa la llave en una cara junto a un inventario para extraer, o haz clic derecho en la tubería con la mano vacía"),
        ("crate", "Holds %s stacks and keeps them when broken", "Guarda %s pilas y las conserva al romperse"),
        ("multiblock.coke_oven", "Multiblock: controller at the bottom centre of the front of a 3×3×3 cube of Coke Oven Bricks",
         "Multibloque: el controlador va abajo al centro del frente de un cubo 3×3×3 de ladrillos de horno de coque"),
        ("multiblock.blast_furnace", "Multiblock: controller at the bottom centre of the front of a 3×3×3 cube of Fire Bricks with a hollow centre",
         "Multibloque: el controlador va abajo al centro del frente de un cubo 3×3×3 de ladrillos refractarios, hueco en el centro"),
        ("speed_upgrade", "+50% machine speed (uses more energy)", "+50% de velocidad (consume más energía)"),
        ("energy_upgrade", "-20% energy per operation", "-20% de energía por operación"),
        ("upgrade_slots", "Fits in the upgrade slots of electric machines", "Va en las ranuras de mejora de las máquinas eléctricas"),
        ("mold", "Press mould: decides what a press makes. Never used up.", "Molde de prensa: decide qué fabrica. No se gasta."),
        ("forge_hammer", "Craft with 2 ingots to make a plate", "Combínalo con 2 lingotes para hacer una placa"),
        ("wrench", "Rotates machines; sets an Energy Cell's output face; toggles item pipe faces between insert and extract",
         "Gira máquinas; elige la cara de salida de una celda de energía; alterna las caras de las tuberías entre insertar y extraer"),
        ("stored_energy", "Energy: %s / %s FE", "Energía: %s / %s FE"),
        ("electric_drill", "Mines faster than netherite while charged. Charge it in an Energy Cell.", "Pica más rápido que la netherita mientras tenga carga. Cárgalo en una celda de energía."),
    ]:
        add(f"{T}.{key}", e, s)
    M = f"message.{MOD}"
    add(f"{M}.pipe_extract", "Pipe face: extracting", "Cara de tubería: extrayendo")
    add(f"{M}.cell_output", "Energy Cell output: %s face", "Salida de la celda de energía: cara %s")
    for d, e, es_ in [("north", "north", "norte"), ("south", "south", "sur"), ("east", "east", "este"),
                      ("west", "west", "oeste"), ("up", "top", "superior"), ("down", "bottom", "inferior")]:
        add(f"direction.{MOD}.{d}", e, es_)
    add(f"{M}.pipe_insert", "Pipe face: inserting", "Cara de tubería: insertando")
    S = f"status.{MOD}"
    for key, e, s in [("working", "Working", "Trabajando"), ("idle", "Idle", "En espera"),
                      ("no_power", "Not enough power", "Energía insuficiente"), ("output_full", "Output full", "Salida llena"),
                      ("tier_too_low", "Needs a better machine", "Requiere una máquina mejor"),
                      ("no_fuel", "No fuel", "Sin combustible"), ("full", "Energy buffer full", "Batería interna llena"),
                      ("finished", "Area finished", "Área terminada"),
                      ("incomplete", "Structure incomplete (%s blocks wrong)", "Estructura incompleta (%s bloques mal)"),
                      ("needs_crank", "Turn the crank!", "¡Gira la manivela!"),
                      ("loading", "Reaching The Deep…", "Llegando a Las Profundidades…")]:
        add(f"{S}.{key}", e, s)
    G = f"gui.{MOD}"
    for key, e, s in [
        ("energy", "%s / %s FE", "%s / %s FE"), ("using", "Using %s FE/t", "Consumiendo %s FE/t"),
        ("generating", "Generating %s FE/t", "Generando %s FE/t"), ("cell_in", "In: %s FE/t", "Entrada: %s FE/t"),
        ("cell_out", "Out: %s FE/t", "Salida: %s FE/t"), ("progress", "Progress: %s%%", "Progreso: %s%%"),
        ("rate", "Output: %s items/min", "Producción: %s objetos/min"), ("speed", "Speed: %s%%", "Velocidad: %s%%"),
        ("eject_on", "Auto-eject: ON (click to toggle)", "Expulsión automática: SÍ (clic para cambiar)"),
        ("eject_off", "Auto-eject: OFF (click to toggle)", "Expulsión automática: NO (clic para cambiar)"),
        ("sunlight", "Sunlight: %s%%", "Luz solar: %s%%"), ("lava", "Lava sources: %s / 5", "Fuentes de lava: %s / 5"),
        ("miner_info", "Deep claim #%s · Y %s", "Reclamo #%s en las Profundidades · Y %s"), ("farm_info", "Field: %s wide, in front", "Campo: %s de ancho, delante"),
        ("miner_progress", "Claim dug out: %s%%", "Reclamo excavado: %s%%"), ("miner_rate", "Up to %s ores/min", "Hasta %s menas/min"),
        ("crank", "Crank", "Girar"), ("crank_hint", "Click (or sneak + right-click the Quern) to turn it",
                                     "Haz clic (o agáchate + clic derecho en el molino) para girarlo"),
        ("show_accepts", "Show what this machine accepts", "Mostrar qué acepta esta máquina"),
        ("hide_accepts", "Hide the list of accepted items", "Ocultar la lista de objetos aceptados"),
        ("accepts", "Accepts (%s)", "Acepta (%s)"), ("no_recipes", "Nothing", "Nada"),
        ("needs_better_machine", "Better machine", "Máquina mejor"),
        ("with", "  with %s", "  con %s"), ("with_mold", "  using a %s", "  usando un %s"),
        ("see_panel", "See the list on the left for what it accepts", "Mira la lista de la izquierda para ver qué acepta"),
        ("jei_time", "%s s", "%s s"), ("jei_needs", "Needs: %s+", "Requiere: %s+"),
        ("jei_needs_long", "Needs a %s or a better machine of the same kind",
         "Requiere %s o una máquina mejor del mismo tipo"),
        ("jei_needs_later", "Needs a later-age machine", "Requiere máquina posterior"),
        ("jei_needs_later_long", "Needs a grade %s machine, unlocked in a later age",
         "Requiere una máquina de grado %s, que llega en una era posterior"),
        ("jei_chance", "%s%% chance", "%s%% de probabilidad"),
        ("jei_mold", "Mould: not consumed", "Molde: no se consume"),
        ("not_accepted", "This machine has no recipe for this item", "Esta máquina no tiene receta para este objeto"),
        ("see_recipes", "Click ? (top right) to see what it accepts", "Pulsa ? (arriba a la derecha) para ver qué acepta"),
    ]:
        add(f"{G}.{key}", e, s)
    for key, (te, de, ts, ds) in advancement_text.items():
        add(f"advancements.{MOD}.{key}.title", te, ts)
        add(f"advancements.{MOD}.{key}.description", de, ds)
    en.update(extra_en)
    es.update(extra_es)
    write(ASSETS / "lang" / "en_us.json", en)
    for loc in ["es_es", "es_ar", "es_mx", "es_cl", "es_uy", "es_ve", "es_ec"]:
        write(ASSETS / "lang" / f"{loc}.json", es)


# ============================================================ loot tables

def loot_self(name, keep_container=False):
    entry = {"type": "minecraft:item", "name": ns(name)}
    if keep_container:
        entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                               "include": ["minecraft:container", "minecraft:custom_name"]}]
    write(DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{MOD}:blocks/{name}"})


def loot_ore(name, raw):
    write(DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": ns(name), "conditions": [{
                "condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": ns(raw), "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{MOD}:blocks/{name}"})


def loot():
    for mid in MACHINES:
        loot_self(mid)
    for name in list(CABLES.values()) + list(PIPES.values()) + ["coke_oven_bricks", "fire_bricks"]:
        loot_self(name)
    for name in CRATES:
        loot_self(name, keep_container=True)
    for name, (raw, _, _, _) in ORES.items():
        loot_ore(name, raw)
    for m in STORAGE_METALS:
        loot_self(f"{m}_block")


# ============================================================ tags & data maps

def tag(namespace, kind, path, values):
    write(DATA / namespace / "tags" / kind / f"{path}.json",
          {"replace": False, "values": [v if v.startswith("#") else ns(v) for v in values]})


def tags(extra_pickaxe):
    pickaxe = (list(MACHINES) + list(CABLES.values()) + list(PIPES.values()) + ["coke_oven_bricks", "fire_bricks", "bronze_crate"]
               + list(ORES) + [f"{m}_block" for m in STORAGE_METALS] + extra_pickaxe)
    tag("minecraft", "block", "mineable/pickaxe", pickaxe)
    tag("minecraft", "block", "mineable/axe", ["wooden_crate"])
    tag("minecraft", "block", "needs_stone_tool", [o for o, v in ORES.items() if v[1] == "stone"] + [f"{m}_block" for m in STORAGE_METALS])
    tag("minecraft", "block", "needs_iron_tool", [o for o, v in ORES.items() if v[1] == "iron"])
    by_metal = {}
    for ore, (_, _, _, metal) in ORES.items():
        by_metal.setdefault(metal, []).append(ore)
    for kind in ("block", "item"):
        tag("c", kind, "ores", [f"#c:ores/{m}" for m in by_metal])
        for metal, ores in by_metal.items():
            tag("c", kind, f"ores/{metal}", ores)
        tag("c", kind, "ores_in_ground/stone", [o for o, v in ORES.items() if v[2] == "stone"])
        tag("c", kind, "ores_in_ground/deepslate", [o for o, v in ORES.items() if v[2] == "deepslate"])
        tag("c", kind, "storage_blocks", [f"#c:storage_blocks/{m}" for m in STORAGE_METALS])
        for m in STORAGE_METALS:
            tag("c", kind, f"storage_blocks/{m}", [f"{m}_block"])
    tag("c", "item", "raw_materials", ["#c:raw_materials/tin", "#c:raw_materials/aluminum", "#c:raw_materials/titanium"])
    tag("c", "item", "raw_materials/tin", ["raw_tin"])
    tag("c", "item", "raw_materials/aluminum", ["raw_bauxite"])
    tag("c", "item", "raw_materials/titanium", ["raw_titanium"])
    tag("c", "item", "ingots", [f"#c:ingots/{i}" for i in INGOTS])
    for i in INGOTS:
        tag("c", "item", f"ingots/{i}", [f"{i}_ingot"])
    tag("c", "item", "dusts", [f"#c:dusts/{d}" for d in DUSTS])
    for d in DUSTS:
        tag("c", "item", f"dusts/{d}", [f"{d}_dust"])
    for group, names in (("plates", PLATES), ("gears", GEARS), ("rods", RODS), ("wires", WIRES)):
        tag("c", "item", group, [f"#c:{group}/{n}" for n in names])
        for n in names:
            tag("c", "item", f"{group}/{n}", [f"{n}_{group[:-1]}"])
    tag("c", "item", "coal_coke", ["coke"])
    tag("c", "item", "silicon", ["silicon"])
    tag("c", "item", "tools/wrench", ["wrench"])
    tag("minecraft", "item", "pickaxes", ["bronze_pickaxe"])
    tag("minecraft", "item", "axes", ["bronze_axe"])
    tag("minecraft", "item", "shovels", ["bronze_shovel"])
    tag("minecraft", "item", "hoes", ["bronze_hoe"])
    tag("minecraft", "item", "swords", ["bronze_sword"])
    write(DATA / "neoforge" / "data_maps" / "item" / "furnace_fuels.json",
          {"values": {f"{MOD}:coke": {"burn_time": 3200}}})


# ============================================================ worldgen

def worldgen():
    wg = DATA / MOD / "worldgen"

    def ore(name, targets, size, count, lo, hi, shape):
        write(wg / "configured_feature" / f"ore_{name}.json", {"type": "minecraft:ore", "config": {
            "discard_chance_on_air_exposure": 0.0, "size": size, "targets": [
                {"state": {"Name": ns(block)}, "target": {"predicate_type": "minecraft:tag_match",
                                                          "tag": f"minecraft:{repl}_ore_replaceables"}}
                for block, repl in targets]}})
        write(wg / "placed_feature" / f"ore_{name}.json", {"feature": f"{MOD}:ore_{name}", "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": f"minecraft:{shape}",
                                                           "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:biome"}]})
    ore("tin", [("tin_ore", "stone"), ("deepslate_tin_ore", "deepslate")], 9, 10, -16, 96, "trapezoid")
    ore("bauxite", [("bauxite_ore", "stone")], 8, 6, 32, 128, "uniform")
    # Rare and deep: a few blocks per chunk, like diamonds.
    ore("titanium", [("deepslate_titanium_ore", "deepslate")], 4, 1, -64, -16, "trapezoid")
    the_deep(wg)
    write(DATA / MOD / "neoforge" / "biome_modifier" / "ores.json", {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": [f"{MOD}:ore_tin", f"{MOD}:ore_bauxite", f"{MOD}:ore_titanium"], "step": "underground_ores"})


# The Deep: a sealed mining dimension. Miners claim a chunk column here and dig out real ore
# blocks, instead of creating items from nothing. Deepslate below y 80, stone above, bedrock
# floor and ceiling, ores denser than the overworld (the whole 254-block column is ore-bearing).
DEEP_ORES = [
    # (name, feature, count, lo, hi)
    ("coal", "minecraft:ore_coal", 18, 80, 254),
    ("iron", "minecraft:ore_iron", 20, 40, 254),
    ("copper", "minecraft:ore_copper_small", 10, 80, 254),
    ("tin", f"{MOD}:ore_tin", 16, 50, 254),
    ("bauxite", f"{MOD}:ore_bauxite", 10, 80, 254),
    ("emerald", "minecraft:ore_emerald", 4, 120, 254),
    ("gold", "minecraft:ore_gold", 8, 1, 110),
    ("redstone", "minecraft:ore_redstone", 10, 1, 70),
    ("lapis", "minecraft:ore_lapis", 5, 1, 90),
    ("diamond", "minecraft:ore_diamond_small", 6, 1, 40),
    ("titanium", f"{MOD}:ore_titanium", 4, 1, 70),
]


def the_deep(wg):
    placed = []
    for name, feature, count, lo, hi in DEEP_ORES:
        write(wg / "placed_feature" / f"deep_ore_{name}.json", {"feature": feature, "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                           "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:biome"}]})
        placed.append(f"{MOD}:deep_ore_{name}")
    features = [[] for _ in range(11)]
    features[6] = placed  # underground_ores
    write(wg / "biome" / "the_deep.json", {
        "attributes": {"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#0e0c14"},
        "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"},
        "features": features, "has_precipitation": False, "spawn_costs": {},
        "spawners": {k: [] for k in ["ambient", "axolotls", "creature", "misc", "monster",
                                     "underground_water_creature", "water_ambient", "water_creature"]},
        "temperature": 0.5})
    write(DATA / MOD / "dimension_type" / "the_deep.json", {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "explodes": False},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/sky_light_level": 0.0,
            "minecraft:visual/fog_color": "#0e0c14",
            "minecraft:visual/sky_light_factor": 0.0,
        },
        "cardinal_light": "default", "coordinate_scale": 1.0, "has_ceiling": True,
        "has_ender_dragon_fight": False, "has_fixed_time": True, "has_skylight": False,
        "height": 256, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 256, "min_y": 0,
        "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0, "skybox": "none",
        "timelines": "#minecraft:in_nether"})
    write(DATA / MOD / "dimension" / "the_deep.json", {
        "type": f"{MOD}:the_deep",
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": f"{MOD}:the_deep", "features": True, "lakes": False, "structure_overrides": [],
            "layers": [{"block": "minecraft:bedrock", "height": 1},
                       {"block": "minecraft:deepslate", "height": 79},
                       {"block": "minecraft:stone", "height": 175},
                       {"block": "minecraft:bedrock", "height": 1}]}}})


# ============================================================ recipes

def railgun_recipe():
    """
    Overrides the Orbital Railgun's own recipe (same path, and this mod loads after it: see the
    optional dependency in neoforge.mods.toml) so the railgun belongs to the Orbital age.
    """
    path = DATA / "orbital_railgun" / "recipe" / "orbital_railgun.json"
    write(path, {
        "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "orbital_railgun"}],
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["TST", "CBC", "TOT"],
        "key": {"T": ing("#c:plates/titanium"), "S": ing("minecraft:spyglass"), "C": ing("advanced_circuit"),
                "B": ing("minecraft:beacon"), "O": ing("orbital_targeting_core")},
        "result": {"id": "orbital_railgun:orbital_railgun"}})


RECIPES = DATA / MOD / "recipe"


def ing(x):
    return x if x.startswith("#") or ":" in x else f"{MOD}:{x}"


def result(item, count=1):
    r = {"id": ing(item)}
    if count != 1:
        r["count"] = count
    return r


def shaped(name, pattern, key, out, count=1, category="misc"):
    used = set("".join(pattern)) - {" "}
    write(RECIPES / f"{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                     "key": {k: ing(v) for k, v in key.items() if k in used},
                                     "result": result(out, count)})


def shapeless(name, ingredients, out, count=1, category="misc"):
    write(RECIPES / f"{name}.json", {"type": "minecraft:crafting_shapeless", "category": category,
                                     "ingredients": [ing(i) for i in ingredients], "result": result(out, count)})


def smelt(name, inp, out, xp=0.7, time=200, blasting=True):
    write(RECIPES / f"{name}.json", {"type": "minecraft:smelting", "category": "misc", "ingredient": ing(inp),
                                     "result": result(out), "experience": xp, "cookingtime": time})
    if blasting:
        write(RECIPES / f"{name}_blasting.json", {"type": "minecraft:blasting", "category": "misc",
                                                  "ingredient": ing(inp), "result": result(out),
                                                  "experience": xp, "cookingtime": time // 2})


def machine(kind, name, ingredients, out, count=1, time=40, min_grade=1, byproduct=None, mold=None):
    obj = {"type": f"{MOD}:{kind}",
           "ingredients": [{"ingredient": ing(i), "count": c} for i, c in ingredients],
           "result": result(out, count), "time": time}
    if min_grade > 1:
        obj["min_grade"] = min_grade
    if byproduct:
        item, chance = byproduct
        obj["byproduct"] = {"item": result(item), "chance": chance}
    if mold:
        obj["mold"] = ing(mold)
    write(RECIPES / kind / f"{name}.json", obj)


def recipes():
    I, C, ST = "#c:ingots/iron", "#c:ingots/copper", "#c:rods/wooden"
    # ---------------------------------------------------------------- Stone age
    shaped("forge_hammer", [" I ", "ISI", " S "], {"I": I, "S": ST}, "forge_hammer", category="equipment")
    shaped("wrench", ["I I", " C ", " I "], {"I": I, "C": C}, "wrench", category="equipment")
    for metal, src in [("iron", I), ("copper", C), ("tin", "#c:ingots/tin"), ("bronze", "#c:ingots/bronze")]:
        shapeless(f"{metal}_plate_by_hammer", [src, src, "forge_hammer"], f"{metal}_plate")
    shaped("iron_gear_by_hand", [" I ", "I I", " I "], {"I": I}, "iron_gear")
    shaped("quern", [" W ", "SFS", "SSS"], {"W": ST, "S": "minecraft:smooth_stone", "F": "minecraft:flint"}, "quern")
    shaped("brick_kiln", ["BBB", "BFB", "BBB"], {"B": "minecraft:bricks", "F": "minecraft:furnace"}, "brick_kiln")
    shaped("wooden_crate", ["LPL", "P P", "LPL"], {"L": "#minecraft:logs", "P": "#minecraft:planks"}, "wooden_crate")
    B = "#c:ingots/bronze"
    tools = {"bronze_pickaxe": ["BBB", " S ", " S "], "bronze_axe": ["BB", "BS", " S"], "bronze_shovel": ["B", "S", "S"],
             "bronze_hoe": ["BB", " S", " S"], "bronze_sword": ["B", "B", "S"]}
    for name, pattern in tools.items():
        shaped(name, pattern, {"B": B, "S": ST}, name, category="equipment")

    # ---------------------------------------------------------------- Bronze age
    BP = "#c:plates/bronze"
    shaped("burner_crusher", ["PHP", "GFG", "PBP"], {"P": BP, "H": "minecraft:hopper", "G": "#c:gears/iron",
                                                    "F": "minecraft:furnace", "B": "minecraft:bricks"}, "burner_crusher")
    shaped("burner_press", ["PNP", "GFG", "PBP"], {"P": BP, "N": "minecraft:piston", "G": "#c:gears/iron",
                                                  "F": "minecraft:furnace", "B": "minecraft:bricks"}, "burner_press")
    shaped("coke_oven_bricks", ["CBC", "BSB", "CBC"], {"C": "minecraft:clay_ball", "B": "minecraft:brick",
                                                      "S": "minecraft:sandstone"}, "coke_oven_bricks", 3, "building")
    shaped("coke_oven", ["BIB", "BFB", "BBB"], {"B": "coke_oven_bricks", "I": "#c:plates/iron",
                                               "F": "minecraft:furnace"}, "coke_oven")
    shapeless("fire_clay", ["minecraft:clay_ball", "minecraft:clay_ball", "#minecraft:sand", "minecraft:gravel"], "fire_clay", 4)
    smelt("fire_brick", "fire_clay", "fire_brick", 0.2, 200, blasting=False)
    shaped("fire_bricks", ["FF", "FF"], {"F": "fire_brick"}, "fire_bricks", 1, "building")
    shaped("blast_furnace_controller", ["BIB", "BFB", "BBB"], {"B": "fire_bricks", "I": "#c:storage_blocks/iron",
                                                              "F": "minecraft:blast_furnace"}, "blast_furnace")
    shaped("bronze_crate", ["PPP", "PCP", "PPP"], {"P": BP, "C": "wooden_crate"}, "bronze_crate")
    shaped("bronze_item_pipe", ["PGP"], {"P": BP, "G": "#c:glass_blocks/colorless"}, "bronze_item_pipe", 8)
    shapeless("plate_mold", [BP] * 4 + ["forge_hammer"], "plate_mold")
    shapeless("gear_mold", [BP] * 4 + ["#c:gears/iron"], "gear_mold")
    shapeless("rod_mold", [BP] * 4 + ["minecraft:stick"], "rod_mold")
    shapeless("wire_mold", [BP] * 4 + ["minecraft:string"], "wire_mold")

    # ---------------------------------------------------------------- Electric age
    SP, CW = "#c:plates/steel", "#c:wires/copper"
    shaped("machine_frame", ["PRP", "R R", "PRP"], {"P": SP, "R": "#c:rods/iron"}, "machine_frame")
    M = "machine_frame"
    shaped("combustion_generator", ["PWP", "FMF", "PWP"], {"P": SP, "W": CW, "F": "minecraft:furnace", "M": M}, "combustion_generator")
    shaped("energy_cell", ["PRP", "RMR", "PWP"], {"P": SP, "R": "minecraft:redstone_block", "W": CW, "M": M}, "energy_cell")
    shaped("copper_cable", ["LLL", "WWW", "LLL"], {"L": "#minecraft:wool", "W": CW}, "copper_cable", 8)
    shaped("assembler", ["PTP", "GMG", "PWP"], {"P": SP, "T": "minecraft:crafting_table", "G": "#c:gears/bronze",
                                               "W": CW, "M": M}, "assembler")
    CB = "basic_circuit"
    shaped("electric_furnace", ["PCP", "FMF", "PWP"], {"P": SP, "C": CB, "F": "minecraft:furnace", "W": CW, "M": M}, "electric_furnace")
    shaped("crusher", ["PHP", "GMG", "PCP"], {"P": SP, "H": "minecraft:hopper", "G": "#c:gears/steel", "C": CB, "M": M}, "crusher")
    shaped("metal_press", ["PNP", "CMC", "PWP"], {"P": SP, "N": "minecraft:piston", "C": CB, "W": CW, "M": M}, "metal_press")
    shaped("alloy_smelter", ["PBP", "CMC", "PWP"], {"P": SP, "B": "minecraft:blast_furnace", "C": CB, "W": CW, "M": M}, "alloy_smelter")
    shaped("solar_panel", ["DDD", "PCP", "PWP"], {"D": "minecraft:daylight_detector", "C": CB, "P": SP, "W": CW}, "solar_panel")
    shaped("auto_farmer", ["PHP", "CMC", "PWP"], {"P": SP, "H": "minecraft:iron_hoe", "C": CB, "W": CW, "M": M}, "auto_farmer")
    shaped("electric_drill", [" PS", "PMP", "CP "], {"P": SP, "S": "#c:rods/steel", "M": "motor", "C": CB}, "electric_drill",
           category="equipment")
    shaped("speed_upgrade", ["RWR", "WCW", "RWR"], {"R": "minecraft:redstone", "W": "#c:wires/gold", "C": CB}, "speed_upgrade", 2)
    shaped("energy_upgrade", ["LWL", "WCW", "LWL"], {"L": "#c:gems/lapis", "W": "#c:wires/gold", "C": CB}, "energy_upgrade", 2)

    # ---------------------------------------------------------------- Automation age (and later infrastructure)
    AC, AM = "advanced_circuit", "advanced_machine_frame"
    shaped("miner", ["PDP", "CMC", "PHP"], {"P": "#c:plates/aluminum", "D": "minecraft:diamond_pickaxe", "C": AC, "M": AM,
                                           "H": "minecraft:hopper"}, "miner")
    shaped("geothermal_generator", ["PLP", "CMC", "PWP"], {"P": "#c:plates/aluminum", "L": "minecraft:magma_block", "C": AC,
                                                          "M": AM, "W": CW}, "geothermal_generator")
    shaped("advanced_energy_cell", [" C ", "AEA", " C "], {"C": AC, "A": "#c:plates/aluminum", "E": "energy_cell"}, "advanced_energy_cell")
    shaped("industrial_energy_cell", [" T ", "TET", " T "], {"T": "#c:plates/titanium", "E": "advanced_energy_cell"}, "industrial_energy_cell")
    # ---------------------------------------------------------------- the late-age machines: each is built from the
    # previous age's materials, and each unlocks the next breakthrough.
    AP, AC2 = "#c:plates/aluminum", "advanced_circuit"
    shaped("ore_washer", ["GWG", "CMC", "PFP"], {"G": "minecraft:glass", "W": "minecraft:water_bucket", "C": AC2,
                                               "M": "motor", "P": AP, "F": "advanced_machine_frame"}, "ore_washer")
    shaped("induction_smelter", ["PHP", "CFC", "PHP"], {"P": AP, "H": "heating_coil", "C": AC2,
                                                      "F": "advanced_machine_frame"}, "induction_smelter")
    shaped("hydraulic_press", ["PMP", "CFC", "PBP"], {"P": AP, "M": "motor", "C": AC2, "F": "advanced_machine_frame",
                                                    "B": "#c:storage_blocks/steel"}, "hydraulic_press")
    shaped("precision_assembler", ["TCT", "AFA", "TCT"], {"T": "#c:plates/titanium", "C": AC2, "A": "assembler",
                                                        "F": "advanced_machine_frame"}, "precision_assembler")
    shaped("plasma_forge", ["TOT", "HFH", "TBT"], {"T": "#c:plates/titanium", "O": "orbital_targeting_core",
                                                 "H": "heating_coil", "F": "advanced_machine_frame",
                                                 "B": "#c:storage_blocks/titanium"}, "plasma_forge")
    shaped("quantum_energy_cell", [" Q ", "QEQ", " Q "], {"Q": "#c:ingots/quantum_alloy", "E": "industrial_energy_cell"}, "quantum_energy_cell")
    order = ["lv", "mv", "hv", "ev"]
    up = {"mv": "#c:ingots/aluminum", "hv": "#c:ingots/titanium", "ev": "#c:ingots/quantum_alloy"}
    pipe_up = {"mv": "#c:ingots/steel", "hv": "#c:ingots/aluminum", "ev": "#c:ingots/titanium"}
    for prev, cur in zip(order, order[1:]):
        shaped(CABLES[cur], ["CCC", "CXC", "CCC"], {"C": CABLES[prev], "X": up[cur]}, CABLES[cur], 8)
        shaped(PIPES[cur], ["CCC", "CXC", "CCC"], {"C": PIPES[prev], "X": pipe_up[cur]}, PIPES[cur], 8)

    # ---------------------------------------------------------------- storage blocks
    for m in STORAGE_METALS:
        shaped(f"{m}_block", ["III", "III", "III"], {"I": f"#c:ingots/{m}"}, f"{m}_block", category="building")
        shapeless(f"{m}_ingot_from_block", [f"{m}_block"], f"{m}_ingot", 9)

    # ---------------------------------------------------------------- vanilla furnace
    for d in ["iron", "copper", "gold"]:
        smelt(f"{d}_ingot_from_dust", f"{d}_dust", f"minecraft:{d}_ingot", 0.3, 200)
    smelt("tin_ingot_from_dust", "tin_dust", "tin_ingot", 0.3, 200)
    smelt("tin_ingot_from_raw", "raw_tin", "tin_ingot", 0.7, 200)
    smelt("tin_ingot_from_ore", "#c:ores/tin", "tin_ingot", 0.7, 200)

    # ---------------------------------------------------------------- Electric Furnace only (grade gates)
    machine("smelting", "aluminum_from_dust", [("bauxite_dust", 1)], "aluminum_ingot", time=40, min_grade=3)
    machine("smelting", "aluminum_from_raw", [("raw_bauxite", 1)], "aluminum_ingot", time=60, min_grade=3)
    machine("smelting", "aluminum_from_ore", [("#c:ores/aluminum", 1)], "aluminum_ingot", time=60, min_grade=3)
    machine("smelting", "titanium_from_dust", [("titanium_dust", 1)], "titanium_ingot", time=100, min_grade=5)
    machine("smelting", "titanium_from_raw", [("raw_titanium", 1)], "titanium_ingot", time=160, min_grade=5)

    # ---------------------------------------------------------------- crushing: Quern 1(+50%) -> Burner 2 -> Crusher 2 + byproduct
    crush = {"iron": ("#c:raw_materials/iron", "#c:ores/iron", "tin_dust"),
             "copper": ("#c:raw_materials/copper", "#c:ores/copper", "gold_dust"),
             "gold": ("#c:raw_materials/gold", "#c:ores/gold", "copper_dust"),
             "tin": ("#c:raw_materials/tin", "#c:ores/tin", "iron_dust"),
             "bauxite": ("#c:raw_materials/aluminum", "#c:ores/aluminum", "iron_dust"),
             "titanium": ("#c:raw_materials/titanium", "#c:ores/titanium", "iron_dust")}
    for metal, (raw, ore_tag, by) in crush.items():
        dust = f"{metal}_dust"
        machine("crushing", f"{dust}_from_raw_quern", [(raw, 1)], dust, 1, time=40, min_grade=1, byproduct=(dust, 0.5))
        machine("crushing", f"{dust}_from_raw_burner", [(raw, 1)], dust, 2, time=60, min_grade=2)
        machine("crushing", f"{dust}_from_raw", [(raw, 1)], dust, 2, time=60, min_grade=3, byproduct=(by, 0.10))
        machine("crushing", f"{dust}_from_ore_burner", [(ore_tag, 1)], dust, 2, time=80, min_grade=2)
        machine("crushing", f"{dust}_from_ore", [(ore_tag, 1)], dust, 3, time=80, min_grade=3, byproduct=(by, 0.10))
        # Ore Washer (Automation): 3 per raw ore, 4 per ore block, better byproduct
        machine("crushing", f"{dust}_from_raw_washed", [(raw, 1)], dust, 3, time=60, min_grade=4, byproduct=(by, 0.25))
        machine("crushing", f"{dust}_from_ore_washed", [(ore_tag, 1)], dust, 4, time=80, min_grade=4, byproduct=(by, 0.25))
    machine("crushing", "coal_dust", [("minecraft:coal", 1)], "coal_dust", time=30)
    machine("crushing", "coal_dust_from_coke", [("coke", 1)], "coal_dust", 2, time=30, min_grade=2)
    machine("crushing", "quartz_dust", [("minecraft:quartz", 1)], "quartz_dust", time=30)
    machine("crushing", "gravel", [("#c:cobblestones/normal", 1)], "minecraft:gravel", time=30)
    machine("crushing", "sand", [("minecraft:gravel", 1)], "minecraft:sand", time=30, byproduct=("minecraft:flint", 0.15))
    machine("crushing", "bone_meal", [("minecraft:bone", 1)], "minecraft:bone_meal", 6, time=20)
    machine("crushing", "blaze_powder", [("minecraft:blaze_rod", 1)], "minecraft:blaze_powder", 4, time=20, min_grade=2)
    machine("crushing", "string", [("#minecraft:wool", 1)], "minecraft:string", 4, time=20)

    # ---------------------------------------------------------------- pressing (mould selects the shape)
    for p in PLATES:
        machine("pressing", f"{p}_plate", [(f"#c:ingots/{p}", 1)], f"{p}_plate", time=40,
                min_grade=5 if p == "titanium" else 3 if p == "aluminum" else 1, mold="plate_mold")
    for g in GEARS:
        machine("pressing", f"{g}_gear", [(f"#c:ingots/{g}", 2)], f"{g}_gear", time=60,
                min_grade=5 if g == "titanium" else 1, mold="gear_mold")
    for r in RODS:
        machine("pressing", f"{r}_rod", [(f"#c:ingots/{r}", 1)], f"{r}_rod", 2, time=40, mold="rod_mold")
    for w in WIRES:
        machine("pressing", f"{w}_wire", [(f"#c:ingots/{w}", 1)], f"{w}_wire", 3, time=40, mold="wire_mold")
    machine("pressing", "silicon_wafer", [("silicon", 1)], "silicon_wafer", 2, time=60, min_grade=3, mold="plate_mold")

    # ---------------------------------------------------------------- alloying: Brick Kiln (1) and Alloy Smelter (3)
    machine("alloying", "bronze_kiln", [(C, 3), ("#c:ingots/tin", 1)], "bronze_ingot", 4, time=200)
    machine("alloying", "bronze", [(C, 3), ("#c:ingots/tin", 1)], "bronze_ingot", 4, time=80, min_grade=3)
    machine("alloying", "bronze_from_dust", [("#c:dusts/copper", 3), ("#c:dusts/tin", 1)], "bronze_ingot", 4, time=60, min_grade=3)
    machine("alloying", "steel", [(I, 1), ("#c:coal_coke", 1)], "steel_ingot", time=100, min_grade=3)
    machine("alloying", "steel_from_dust", [("#c:dusts/iron", 1), ("#c:dusts/coal", 2)], "steel_ingot", time=80, min_grade=3)
    machine("alloying", "silicon", [("#c:dusts/quartz", 2), ("#c:dusts/coal", 1)], "silicon", time=80, min_grade=3)
    machine("alloying", "quantum_alloy", [("#c:ingots/titanium", 1), ("minecraft:ender_pearl", 1)],
            "quantum_alloy_ingot", time=200, min_grade=7)

    # ---------------------------------------------------------------- coke oven & blast furnace
    machine("coking", "coke", [("minecraft:coal", 1)], "coke", time=600)
    machine("coking", "charcoal", [("#minecraft:logs_that_burn", 1)], "minecraft:charcoal", time=300)
    machine("blasting", "steel", [(I, 1)], "steel_ingot", time=400)
    machine("blasting", "steel_from_block", [("#c:storage_blocks/iron", 1)], "steel_block", time=3200)

    # ---------------------------------------------------------------- assembler
    machine("assembling", "basic_circuit", [(CW, 3), ("minecraft:redstone", 2), (SP, 1)], "basic_circuit", time=80, min_grade=3)
    machine("assembling", "motor", [("#c:rods/iron", 2), (CW, 4), ("#c:plates/iron", 2)], "motor", time=80, min_grade=3)
    machine("assembling", "heating_coil", [(CW, 8), ("#c:rods/iron", 1)], "heating_coil", time=60, min_grade=3)
    machine("assembling", "advanced_circuit", [("basic_circuit", 1), ("silicon_wafer", 2), ("#c:wires/gold", 4),
                                                ("#c:gems/quartz", 2)], "advanced_circuit", time=160, min_grade=3)
    machine("assembling", "advanced_machine_frame", [("machine_frame", 1), ("#c:plates/aluminum", 4),
                                                      ("#c:gears/steel", 2), ("motor", 1)], "advanced_machine_frame",
            time=160, min_grade=3)
    # Orbital age: the targeting core, and through it the Orbital Railgun (optional mod).
    machine("assembling", "orbital_targeting_core", [("#c:plates/titanium", 4), ("advanced_circuit", 2),
                                                      ("minecraft:end_crystal", 1), ("minecraft:echo_shard", 2)],
            "orbital_targeting_core", time=400, min_grade=6)
    railgun_recipe()


# ============================================================ advancements

ADV = {}  # key -> (title en, desc en, title es, desc es)


def advancement(key, parent, icon, items, title_en, desc_en, title_es, desc_es, frame="task", root=False):
    ADV[key] = (title_en, desc_en, title_es, desc_es)
    display = {"icon": {"id": ns(icon)}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    if root:
        display.update({"background": "minecraft:gui/advancements/backgrounds/stone", "show_toast": False,
                        "announce_to_chat": False})
    item_filter = ns(items[0]) if len(items) == 1 else [ns(i) for i in items]
    obj = {"criteria": {"got": {"trigger": "minecraft:inventory_changed",
                                "conditions": {"items": [{"items": item_filter}]}}},
           "display": display, "requirements": [["got"]]}
    if parent:
        obj["parent"] = f"{MOD}:{parent}"
    write(DATA / MOD / "advancement" / f"{key}.json", obj)


def advancements(storage_terminal_id):
    A = advancement
    A("root", None, "quern", ["minecraft:crafting_table"], "Factory Ascent",
      "Seven ages from a hand mill to the stars.",
      "Factory Ascent", "Siete eras, del molino de mano a las estrellas.", root=True)
    A("stone_hammer", "root", "forge_hammer", ["forge_hammer"], "Hammer Time",
      "Craft a Forge Hammer and hammer 2 ingots into a plate", "¡A martillar!",
      "Fabrica un martillo de forja y convierte 2 lingotes en una placa")
    A("stone_quern", "stone_hammer", "quern", ["quern"], "Grind It Out",
      "Build a Quern. Put raw ore in and turn the crank to get dust", "Muele que muele",
      "Construye un molino de mano. Pon mineral en bruto y gira la manivela")
    A("stone_dust", "stone_quern", "iron_dust", ["iron_dust", "copper_dust", "tin_dust", "gold_dust"], "Dusty",
      "Grind raw ore into dust and smelt it. Dust can give bonus ingots", "Polvoriento",
      "Muele mineral en polvo y fúndelo. El polvo puede dar lingotes de más")
    A("stone_tin", "stone_hammer", "raw_tin", ["raw_tin"], "Tin Can",
      "Find tin ore (pale specks, common underground)", "Lata de estaño",
      "Encuentra mena de estaño (manchas claras, común bajo tierra)")
    A("stone_kiln", "stone_tin", "brick_kiln", ["brick_kiln"], "Firing Up",
      "Build a Brick Kiln to alloy 3 copper + 1 tin", "Encendiendo",
      "Construye un horno de ladrillo para alear 3 cobre + 1 estaño")
    A("age_bronze", "stone_kiln", "bronze_ingot", ["bronze_ingot"], "The Bronze Age",
      "Breakthrough! Make your first bronze ingot", "La Edad del Bronce", "¡Avance! Haz tu primer lingote de bronce",
      frame="challenge")
    A("bronze_tools", "age_bronze", "bronze_pickaxe", ["bronze_pickaxe"], "Better Than Iron?",
      "Craft a bronze pickaxe", "¿Mejor que el hierro?", "Fabrica un pico de bronce")
    A("bronze_crusher", "age_bronze", "burner_crusher", ["burner_crusher"], "Double Down",
      "Build a Burner Crusher: 2 dust from every raw ore", "Doble o nada",
      "Construye una trituradora a combustión: 2 polvos por mineral")
    A("bronze_press", "age_bronze", "burner_press", ["burner_press"], "Under Pressure",
      "Build a Burner Press and a mould to make plates, gears, rods and wires", "Bajo presión",
      "Construye una prensa a combustión y un molde para placas, engranajes, varillas y cables")
    A("bronze_coke", "bronze_press", "coke", ["coke"], "Cooking Coal",
      "Build a Coke Oven (3×3×3 of Coke Oven Bricks) and bake coal into coke", "Cocinando carbón",
      "Construye un horno de coque (3×3×3 de ladrillos) y convierte carbón en coque")
    A("bronze_logistics", "bronze_crusher", "bronze_item_pipe", ["bronze_item_pipe", "bronze_crate"], "Moving Stuff",
      "Make Bronze Item Pipes (or a Bronze Crate). Wrench a pipe face to pull from a chest", "Mudanzas",
      "Haz tuberías de bronce (o un cajón). Usa la llave en una cara para extraer de un cofre")
    A("age_electric", "bronze_coke", "steel_ingot", ["steel_ingot"], "Forged in Fire",
      "Breakthrough! Build the Blast Furnace (3×3×3 Fire Bricks, hollow) and make steel", "Forjado en fuego",
      "¡Avance! Construye el alto horno (3×3×3 de ladrillos refractarios, hueco) y haz acero", frame="challenge")
    A("electric_frame", "age_electric", "machine_frame", ["machine_frame"], "Framework",
      "Craft a Machine Frame from steel plates", "Estructura", "Fabrica un chasis de máquina con placas de acero")
    A("electric_power", "electric_frame", "combustion_generator", ["combustion_generator"], "It's Alive!",
      "Build a Combustion Generator and connect machines with Copper Cable", "¡Está vivo!",
      "Construye un generador de combustión y conecta máquinas con cable de cobre")
    A("electric_assembler", "electric_power", "assembler", ["assembler"], "Some Assembly Required",
      "Build an Assembler", "Requiere montaje", "Construye una ensambladora")
    A("electric_circuit", "electric_assembler", "basic_circuit", ["basic_circuit"], "Integrated",
      "Breakthrough! Assemble your first Basic Circuit: every electric machine needs one", "Integrado",
      "¡Avance! Ensambla tu primer circuito básico: toda máquina eléctrica lo necesita", frame="challenge")
    A("electric_crusher", "electric_circuit", "crusher", ["crusher", "electric_furnace"], "Full Throttle",
      "Build an electric Crusher or Electric Furnace", "A toda máquina", "Construye una trituradora o un horno eléctricos")
    A("electric_drill", "electric_circuit", "electric_drill", ["electric_drill"], "Power Tool",
      "Craft an Electric Drill and charge it in an Energy Cell", "Herramienta eléctrica",
      "Fabrica un taladro eléctrico y cárgalo en una celda de energía", frame="goal")
    A("electric_farmer", "electric_circuit", "auto_farmer", ["auto_farmer"], "Retired Farmer",
      "Build an Auto-Farmer: it harvests and replants a 9×9 field", "Granjero jubilado",
      "Construye un granjero automático: cosecha y replanta un campo de 9×9", frame="goal")
    if storage_terminal_id:
        A("electric_storage", "electric_circuit", storage_terminal_id, [storage_terminal_id], "Everything in Its Place",
          "Build a Storage Network: controller, drive with cells, and a terminal", "Todo en su lugar",
          "Construye una red de almacenamiento: controlador, unidad con celdas y terminal", frame="goal")
    A("electric_aluminum", "electric_crusher", "aluminum_ingot", ["aluminum_ingot"], "Light Metal",
      "Smelt bauxite into aluminium in the Electric Furnace", "Metal ligero",
      "Funde bauxita en aluminio con el horno eléctrico")
    A("electric_silicon", "electric_aluminum", "silicon", ["silicon"], "Silicon Valley",
      "Alloy quartz dust and coal dust into silicon, then press it into wafers", "Valle del silicio",
      "Alea polvo de cuarzo y de carbón en silicio y prénsalo en obleas")
    A("age_automation", "electric_silicon", "advanced_circuit", ["advanced_circuit"], "The Automation Age",
      "Breakthrough! Assemble an Advanced Circuit", "La Era de la Automatización",
      "¡Avance! Ensambla un circuito avanzado", frame="challenge")
    A("automation_miner", "age_automation", "miner", ["miner"], "Strip Mine",
      "Build an Ore Miner: it digs ore out of its own claim in The Deep", "Mina a cielo abierto",
      "Construye un minero de menas: extrae menas de su propio reclamo en Las Profundidades", frame="goal")
    A("automation_geothermal", "age_automation", "geothermal_generator", ["geothermal_generator"], "Hot Stuff",
      "Build a Geothermal Generator next to lava", "Cosa caliente", "Construye un generador geotérmico junto a lava")
    A("automation_washer", "age_automation", "ore_washer", ["ore_washer"], "Squeaky Clean",
      "Build an Ore Washer: 3 dust from every raw ore", "Limpio y reluciente",
      "Construye una lavadora de mineral: 3 polvos por cada mineral en bruto")
    A("automation_induction", "automation_washer", "induction_smelter", ["induction_smelter"], "Hotter Than Fire",
      "Build an Induction Smelter, the only furnace hot enough for titanium", "Más caliente que el fuego",
      "Construye una fundidora de inducción, el único horno capaz de fundir titanio")
    # ---------------------------------------------------------------- Industrial
    A("age_industrial", "automation_induction", "titanium_ingot", ["titanium_ingot"], "The Industrial Age",
      "Breakthrough! Smelt titanium in an Induction Smelter", "La Era Industrial",
      "¡Avance! Funde titanio en una fundidora de inducción", frame="challenge")
    A("industrial_press", "age_industrial", "hydraulic_press", ["hydraulic_press"], "Under Pressure",
      "Build a Hydraulic Press and press titanium plates", "Bajo presión",
      "Construye una prensa hidráulica y prensa placas de titanio")
    A("industrial_cell", "industrial_press", "industrial_energy_cell", ["industrial_energy_cell"], "Big Battery",
      "Build an Industrial Energy Cell", "Batería grande", "Construye una celda de energía industrial")
    A("industrial_precision", "industrial_press", "precision_assembler", ["precision_assembler"], "Clean Room",
      "Build a Precision Assembler for spacecraft parts", "Sala limpia",
      "Construye una ensambladora de precisión para piezas espaciales")
    # ---------------------------------------------------------------- Orbital
    A("age_orbital", "industrial_precision", "orbital_targeting_core", ["orbital_targeting_core"], "The Orbital Age",
      "Breakthrough! Assemble an Orbital Targeting Core", "La Era Orbital",
      "¡Avance! Ensambla un núcleo de puntería orbital", frame="challenge")
    A("orbital_forge", "age_orbital", "plasma_forge", ["plasma_forge"], "Star in a Box",
      "Build a Plasma Forge", "Una estrella en una caja", "Construye una forja de plasma")
    # ---------------------------------------------------------------- Quantum
    A("age_quantum", "orbital_forge", "quantum_alloy_ingot", ["quantum_alloy_ingot"], "The Quantum Age",
      "Breakthrough! Forge quantum alloy in the Plasma Forge", "La Era Cuántica",
      "¡Avance! Forja aleación cuántica en la forja de plasma", frame="challenge")
    A("quantum_cell", "age_quantum", "quantum_energy_cell", ["quantum_energy_cell"], "Bottled Lightning",
      "Build a Quantum Energy Cell", "Rayos embotellados", "Construye una celda de energía cuántica")


def main():
    clean()
    storage = load_storage_module()
    machines()
    cables_and_pipes()
    simple_blocks()
    items()
    extra_en, extra_es, extra_pickaxe = {}, {}, []
    if storage:
        storage.generate(write, ASSETS, DATA, MOD)
        extra_en, extra_es = storage.lang()
        extra_pickaxe = list(getattr(storage, "PICKAXE_BLOCKS", []))
    advancements("storage_terminal" if storage else None)
    recipes()
    features = load_feature_modules()
    ctx = FeatureContext()
    for feature in features:
        feature.generate(ctx)
        extra_pickaxe += list(getattr(feature, "PICKAXE_BLOCKS", []))
    extra_en.update(ctx.en)
    extra_es.update(ctx.es)
    lang(extra_en, extra_es, ADV)
    loot()
    tags(extra_pickaxe)
    worldgen()
    count = sum(1 for _ in ROOT.rglob("*.json"))
    print(f"wrote resources, {count} json files under {ROOT} (storage module: {'yes' if storage else 'no'}, "
          f"features: {', '.join(f.__name__.removeprefix('feature_') for f in features) or 'none'})")


if __name__ == "__main__":
    main()

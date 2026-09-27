#!/usr/bin/env python3
"""Generates every JSON resource of Factory Ascent: blockstates, block/item models, item model
definitions, lang files, loot tables, tags, recipes and worldgen.

Run from anywhere:  python3 tools/gen_resources.py
Textures are made separately by tools/gen_textures.py. This script owns (and rewrites) only the
folders it generates, so hand-written files elsewhere under resources are left alone.
"""
import json
import shutil
from pathlib import Path

MOD = "factoryascent"
ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / MOD
DATA = ROOT / "data"

TIERS = ["basic", "reinforced", "advanced", "elite", "ultimate"]
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ["north", "east", "south", "west", "up", "down"]

PROCESSORS = ["electric_furnace", "crusher", "metal_press", "alloy_smelter", "assembler"]
FRONTED = PROCESSORS + ["combustion_generator", "geothermal_generator"]
MACHINES = PROCESSORS + ["miner", "combustion_generator", "solar_panel", "geothermal_generator", "energy_cell"]

ORES = {  # block -> (raw drop, needs tool, stone/deepslate)
    "tin_ore": ("raw_tin", "stone", "stone"),
    "deepslate_tin_ore": ("raw_tin", "stone", "deepslate"),
    "bauxite_ore": ("raw_bauxite", "stone", "stone"),
    "deepslate_bauxite_ore": ("raw_bauxite", "stone", "deepslate"),
    "deepslate_titanium_ore": ("raw_titanium", "iron", "deepslate"),
}
ORE_METAL = {"tin_ore": "tin", "deepslate_tin_ore": "tin", "bauxite_ore": "aluminum",
             "deepslate_bauxite_ore": "aluminum", "deepslate_titanium_ore": "titanium"}
STORAGE_METALS = ["tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"]

DUSTS = ["iron", "copper", "gold", "tin", "coal", "quartz", "bauxite", "titanium"]
INGOTS = ["tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"]
PLATES = ["iron", "copper", "tin", "bronze", "steel", "aluminum", "titanium"]
GEARS = ["iron", "bronze", "steel", "titanium"]
RODS = ["iron", "steel"]
WIRES = ["copper", "gold"]
MOLDS = ["plate", "gear", "rod", "wire"]
MATERIALS = (["raw_tin", "raw_bauxite", "raw_titanium"] + [f"{d}_dust" for d in DUSTS]
             + [f"{i}_ingot" for i in INGOTS] + ["silicon", "silicon_wafer"]
             + [f"{p}_plate" for p in PLATES] + [f"{g}_gear" for g in GEARS] + [f"{r}_rod" for r in RODS]
             + [f"{w}_wire" for w in WIRES] + ["motor", "heating_coil"])
TOOLS = ["forge_hammer", "wrench"]
SIMPLE_ITEMS = (MATERIALS + [f"{m}_mold" for m in MOLDS] + [f"{t}_circuit" for t in TIERS]
                + [f"{t}_machine_frame" for t in TIERS] + ["speed_upgrade", "energy_upgrade"]
                + [f"{t}_upgrade_kit" for t in TIERS[1:]])


def ns(path):
    return path if ":" in path else f"{MOD}:{path}"


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def clean():
    for sub in ["blockstates", "models", "items", "lang"]:
        shutil.rmtree(ASSETS / sub, ignore_errors=True)
    for sub in ["recipe", "loot_table", "tags", "worldgen", "neoforge"]:
        shutil.rmtree(DATA / MOD / sub, ignore_errors=True)
    shutil.rmtree(DATA / "c", ignore_errors=True)
    shutil.rmtree(DATA / "minecraft", ignore_errors=True)


# ============================================================ blockstates & models

def block_model(name, obj):
    write(ASSETS / "models" / "block" / f"{name}.json", obj)


def item_def(name, model):
    write(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": ns(model)}})


def machines():
    for tier in TIERS:
        casing = lambda part: f"{MOD}:block/casing_{tier}_{part}"
        for m in MACHINES:
            name = f"{tier}_{m}"
            if m in FRONTED:
                for on in (False, True):
                    block_model(name + ("_on" if on else ""), {
                        "parent": "minecraft:block/orientable_with_bottom",
                        "textures": {
                            "front": f"{MOD}:block/{name}_front" + ("_on" if on else ""),
                            "side": casing("side"), "top": casing("top"), "bottom": casing("bottom"),
                        }})
            elif m == "energy_cell":
                block_model(name, {"parent": "minecraft:block/orientable_with_bottom", "textures": {
                    "front": f"{MOD}:block/{name}_front", "side": f"{MOD}:block/{name}_side",
                    "top": casing("top"), "bottom": casing("bottom")}})
            elif m == "miner":
                block_model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {
                    "side": f"{MOD}:block/{name}_side", "top": f"{MOD}:block/{name}_top",
                    "bottom": f"{MOD}:block/{name}_bottom"}})
            elif m == "solar_panel":
                side, top, bottom = casing("side"), f"{MOD}:block/{name}_top", casing("bottom")
                block_model(name, {
                    "parent": "minecraft:block/block",
                    "textures": {"particle": top, "top": top, "side": side, "bottom": bottom},
                    "elements": [{
                        "from": [0, 0, 0], "to": [16, 6, 16],
                        "faces": {
                            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
                            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                            "north": {"uv": [0, 10, 16, 16], "texture": "#side", "cullface": "north"},
                            "south": {"uv": [0, 10, 16, 16], "texture": "#side", "cullface": "south"},
                            "west": {"uv": [0, 10, 16, 16], "texture": "#side", "cullface": "west"},
                            "east": {"uv": [0, 10, 16, 16], "texture": "#side", "cullface": "east"},
                        }}]})
            variants = {}
            for facing, rot in FACINGS.items():
                for active in ("false", "true"):
                    model = f"{MOD}:block/{name}" + ("_on" if m in FRONTED and active == "true" else "")
                    v = {"model": model}
                    if rot and m not in ("miner", "solar_panel"):
                        v["y"] = rot
                    variants[f"active={active},facing={facing}"] = v
            write(ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
            item_def(name, f"block/{name}")


def conduit_models(kind, tier):
    """Core + north arm models for cables/pipes, plus an inventory model."""
    name = f"{tier}_{kind}"
    tex = f"{MOD}:block/{name}"
    core = {"from": [5, 5, 5], "to": [11, 11, 11], "faces": {
        d: {"uv": [5, 5, 11, 11], "texture": "#t"} for d in DIRS}}
    def arm(z0, z1):
        return {"from": [5, 5, z0], "to": [11, 11, z1], "faces": {
            "north": {"uv": [5, 5, 11, 11], "texture": "#t"},
            "south": {"uv": [5, 5, 11, 11], "texture": "#t"},
            "up": {"uv": [5, z0, 11, z1], "texture": "#t"},
            "down": {"uv": [5, z0, 11, z1], "texture": "#t"},
            "east": {"uv": [5, z0, 11, z1], "texture": "#t", "rotation": 90},
            "west": {"uv": [5, z0, 11, z1], "texture": "#t", "rotation": 90},
        }}
    block_model(f"{name}_core", {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                                 "elements": [core]})
    block_model(f"{name}_arm", {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                                "elements": [arm(0, 5)]})
    write(ASSETS / "models" / "item" / f"{name}.json", {
        "parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [core, arm(0, 5), arm(11, 16)],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def(name, f"item/{name}")


ARM_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
           "up": {"x": 270}, "down": {"x": 90}}


def cables_and_pipes():
    block_model("item_pipe_extract", {
        "parent": "minecraft:block/block",
        "textures": {"t": f"{MOD}:block/item_pipe_extract", "particle": f"{MOD}:block/item_pipe_extract"},
        "elements": [{"from": [3, 3, 0], "to": [13, 13, 2], "faces": {
            "north": {"uv": [3, 3, 13, 13], "texture": "#t"}, "south": {"uv": [3, 3, 13, 13], "texture": "#t"},
            "up": {"uv": [3, 0, 13, 2], "texture": "#t"}, "down": {"uv": [3, 0, 13, 2], "texture": "#t"},
            "east": {"uv": [0, 3, 2, 13], "texture": "#t"}, "west": {"uv": [0, 3, 2, 13], "texture": "#t"}}}]})
    for tier in TIERS:
        conduit_models("power_cable", tier)
        parts = [{"apply": {"model": f"{MOD}:block/{tier}_power_cable_core"}}]
        for d in DIRS:
            parts.append({"when": {d: "true"}, "apply": {"model": f"{MOD}:block/{tier}_power_cable_arm", **ARM_ROT[d]}})
        write(ASSETS / "blockstates" / f"{tier}_power_cable.json", {"multipart": parts})

        conduit_models("item_pipe", tier)
        parts = [{"apply": {"model": f"{MOD}:block/{tier}_item_pipe_core"}}]
        for d in DIRS:
            parts.append({"when": {d: "connected|extract"},
                          "apply": {"model": f"{MOD}:block/{tier}_item_pipe_arm", **ARM_ROT[d]}})
            parts.append({"when": {d: "extract"}, "apply": {"model": f"{MOD}:block/item_pipe_extract", **ARM_ROT[d]}})
        write(ASSETS / "blockstates" / f"{tier}_item_pipe.json", {"multipart": parts})


def simple_blocks():
    for name in list(ORES) + [f"{m}_block" for m in STORAGE_METALS]:
        block_model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{name}"}})
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        item_def(name, f"block/{name}")


def items():
    for name in SIMPLE_ITEMS + TOOLS:
        parent = "minecraft:item/handheld" if name in TOOLS else "minecraft:item/generated"
        write(ASSETS / "models" / "item" / f"{name}.json", {"parent": parent, "textures": {"layer0": f"{MOD}:item/{name}"}})
        item_def(name, f"item/{name}")


# ============================================================ lang

EN_TIER = {"basic": "Basic", "reinforced": "Reinforced", "advanced": "Advanced", "elite": "Elite", "ultimate": "Ultimate"}
ES_TIER = {"basic": "básico", "reinforced": "reforzado", "advanced": "avanzado", "elite": "de élite", "ultimate": "definitivo"}
ES_TIER_F = {"basic": "básica", "reinforced": "reforzada", "advanced": "avanzada", "elite": "de élite", "ultimate": "definitiva"}

EN_MACHINE = {"electric_furnace": "Electric Furnace", "crusher": "Crusher", "metal_press": "Metal Press",
              "alloy_smelter": "Alloy Smelter", "assembler": "Assembler", "miner": "Miner",
              "combustion_generator": "Combustion Generator", "solar_panel": "Solar Panel",
              "geothermal_generator": "Geothermal Generator", "energy_cell": "Energy Cell",
              "power_cable": "Power Cable", "item_pipe": "Item Pipe"}
# (Spanish name, feminine?)
ES_MACHINE = {"electric_furnace": ("Horno eléctrico", False), "crusher": ("Trituradora", True),
              "metal_press": ("Prensa de metal", True), "alloy_smelter": ("Fundidora de aleaciones", True),
              "assembler": ("Ensambladora", True), "miner": ("Minero", False),
              "combustion_generator": ("Generador de combustión", False), "solar_panel": ("Panel solar", False),
              "geothermal_generator": ("Generador geotérmico", False), "energy_cell": ("Celda de energía", True),
              "power_cable": ("Cable de energía", False), "item_pipe": ("Tubería de objetos", True)}

EN_METAL = {"iron": "Iron", "copper": "Copper", "gold": "Gold", "tin": "Tin", "coal": "Coal", "quartz": "Quartz",
            "bauxite": "Bauxite", "titanium": "Titanium", "bronze": "Bronze", "steel": "Steel",
            "aluminum": "Aluminium", "quantum_alloy": "Quantum Alloy"}
ES_METAL = {"iron": "hierro", "copper": "cobre", "gold": "oro", "tin": "estaño", "coal": "carbón", "quartz": "cuarzo",
            "bauxite": "bauxita", "titanium": "titanio", "bronze": "bronce", "steel": "acero",
            "aluminum": "aluminio", "quantum_alloy": "aleación cuántica"}


def lang():
    en, es = {}, {}
    def add(key, e, s):
        en[key] = e
        es[key] = s
    add(f"itemGroup.{MOD}", "Factory Ascent", "Factory Ascent")
    for t in TIERS:
        add(f"tier.{MOD}.{t}", EN_TIER[t], ES_TIER[t].capitalize())
    for t in TIERS:
        for m in MACHINES + ["power_cable", "item_pipe"]:
            name_es, fem = ES_MACHINE[m]
            add(f"block.{MOD}.{t}_{m}", f"{EN_TIER[t]} {EN_MACHINE[m]}", f"{name_es} {(ES_TIER_F if fem else ES_TIER)[t]}")
    ore_en = {"tin_ore": "Tin Ore", "deepslate_tin_ore": "Deepslate Tin Ore", "bauxite_ore": "Bauxite Ore",
              "deepslate_bauxite_ore": "Deepslate Bauxite Ore", "deepslate_titanium_ore": "Deepslate Titanium Ore"}
    ore_es = {"tin_ore": "Mena de estaño", "deepslate_tin_ore": "Mena de estaño de pizarra profunda",
              "bauxite_ore": "Mena de bauxita", "deepslate_bauxite_ore": "Mena de bauxita de pizarra profunda",
              "deepslate_titanium_ore": "Mena de titanio de pizarra profunda"}
    for k in ORES:
        add(f"block.{MOD}.{k}", ore_en[k], ore_es[k])
    for m in STORAGE_METALS:
        add(f"block.{MOD}.{m}_block", f"Block of {EN_METAL[m]}", f"Bloque de {ES_METAL[m]}")
    for r in ["tin", "bauxite", "titanium"]:
        add(f"item.{MOD}.raw_{r}", f"Raw {EN_METAL[r]}", f"{ES_METAL[r].capitalize()} en bruto")
    for d in DUSTS:
        add(f"item.{MOD}.{d}_dust", f"{EN_METAL[d]} Dust", f"Polvo de {ES_METAL[d]}")
    for i in INGOTS:
        e = "Quantum Alloy Ingot" if i == "quantum_alloy" else f"{EN_METAL[i]} Ingot"
        add(f"item.{MOD}.{i}_ingot", e, f"Lingote de {ES_METAL[i]}")
    add(f"item.{MOD}.silicon", "Silicon", "Silicio")
    add(f"item.{MOD}.silicon_wafer", "Silicon Wafer", "Oblea de silicio")
    for p in PLATES:
        add(f"item.{MOD}.{p}_plate", f"{EN_METAL[p]} Plate", f"Placa de {ES_METAL[p]}")
    for g in GEARS:
        add(f"item.{MOD}.{g}_gear", f"{EN_METAL[g]} Gear", f"Engranaje de {ES_METAL[g]}")
    for r in RODS:
        add(f"item.{MOD}.{r}_rod", f"{EN_METAL[r]} Rod", f"Varilla de {ES_METAL[r]}")
    for w in WIRES:
        add(f"item.{MOD}.{w}_wire", f"{EN_METAL[w]} Wire", f"Cable de {ES_METAL[w]}")
    add(f"item.{MOD}.motor", "Motor", "Motor")
    add(f"item.{MOD}.heating_coil", "Heating Coil", "Resistencia calefactora")
    mold_es = {"plate": "placas", "gear": "engranajes", "rod": "varillas", "wire": "cables"}
    for m in MOLDS:
        add(f"item.{MOD}.{m}_mold", f"{m.capitalize()} Mould", f"Molde de {mold_es[m]}")
    for t in TIERS:
        add(f"item.{MOD}.{t}_circuit", f"{EN_TIER[t]} Circuit", f"Circuito {ES_TIER[t]}")
        add(f"item.{MOD}.{t}_machine_frame", f"{EN_TIER[t]} Machine Frame", f"Chasis de máquina {ES_TIER[t]}")
    for t in TIERS[1:]:
        add(f"item.{MOD}.{t}_upgrade_kit", f"{EN_TIER[t]} Upgrade Kit", f"Kit de mejora {ES_TIER[t]}")
    add(f"item.{MOD}.speed_upgrade", "Speed Upgrade", "Mejora de velocidad")
    add(f"item.{MOD}.energy_upgrade", "Energy Upgrade", "Mejora de energía")
    add(f"item.{MOD}.forge_hammer", "Forge Hammer", "Martillo de forja")
    add(f"item.{MOD}.wrench", "Wrench", "Llave inglesa")

    T = f"tooltip.{MOD}"
    add(f"{T}.speed", "Speed: ×%s", "Velocidad: ×%s")
    add(f"{T}.energy_use", "Uses %s FE/t at full speed", "Consume %s FE/t a máxima velocidad")
    add(f"{T}.upgrade_slot_count", "Upgrade slots: %s", "Ranuras de mejora: %s")
    add(f"{T}.miner_radius", "Radius: %s blocks", "Radio: %s bloques")
    add(f"{T}.miner_rate", "Digs up to %s ores/min", "Extrae hasta %s menas/min")
    add(f"{T}.generation", "Generates %s FE/t", "Genera %s FE/t")
    add(f"{T}.generation_per_lava", "Generates %s FE/t per touching lava source", "Genera %s FE/t por cada fuente de lava adyacente")
    add(f"{T}.fuel_efficiency", "Fuel efficiency: %s%%", "Eficiencia de combustible: %s%%")
    add(f"{T}.capacity", "Capacity: %s FE", "Capacidad: %s FE")
    add(f"{T}.transfer", "Transfer: %s FE/t", "Transferencia: %s FE/t")
    add(f"{T}.cell_front", "Outputs only from its front face", "Solo emite energía por su cara frontal")
    add(f"{T}.upgradable", "Upgrade in place with a %s Upgrade Kit", "Mejorable en el sitio con un kit de mejora %s")
    add(f"{T}.cable_rate", "Network throughput: %s FE/t", "Capacidad de red: %s FE/t")
    add(f"{T}.cable_bottleneck", "A network runs at the rate of its slowest cable",
        "Una red funciona al ritmo de su cable más lento")
    add(f"{T}.pipe_rate", "Pulls up to %s items/s per extracting face", "Extrae hasta %s objetos/s por cara de extracción")
    add(f"{T}.pipe_hint", "Wrench a face touching an inventory to pull from it",
        "Usa la llave en una cara junto a un inventario para extraer de él")
    add(f"{T}.speed_upgrade", "+50% machine speed (uses more energy)", "+50% de velocidad (consume más energía)")
    add(f"{T}.energy_upgrade", "-20% energy per operation", "-20% de energía por operación")
    add(f"{T}.upgrade_slots", "Basic machines have 1 slot, Reinforced 2, Advanced and up 3",
        "Las máquinas básicas tienen 1 ranura, las reforzadas 2 y desde avanzadas 3")
    add(f"{T}.mold", "Metal Press mould: decides what the press makes. Never used up.",
        "Molde para la prensa: decide qué fabrica. No se gasta.")
    add(f"{T}.upgrade_kit", "Right-click a %s machine to make it %s", "Clic derecho en una máquina %s para hacerla %s")
    add(f"{T}.upgrade_kit.keeps", "Keeps its items, energy and settings", "Conserva sus objetos, energía y ajustes")
    add(f"{T}.forge_hammer", "Craft with 2 ingots to make a plate", "Combínalo con 2 lingotes para hacer una placa")
    add(f"{T}.wrench", "Rotates machines; toggles item pipe faces between insert and extract",
        "Gira máquinas; alterna las caras de las tuberías entre insertar y extraer")

    M = f"message.{MOD}"
    add(f"{M}.max_tier", "This machine is already Ultimate", "Esta máquina ya es definitiva")
    add(f"{M}.wrong_kit", "This machine needs a %s kit (you have %s)", "Esta máquina necesita un kit %s (tienes %s)")
    add(f"{M}.upgraded", "Upgraded to %s", "Mejorada a %s")
    add(f"{M}.pipe_extract", "Pipe face: extracting", "Cara de tubería: extrayendo")
    add(f"{M}.pipe_insert", "Pipe face: inserting", "Cara de tubería: insertando")

    S = f"status.{MOD}"
    for key, e, s in [("working", "Working", "Trabajando"), ("idle", "Idle", "En espera"),
                      ("no_power", "Not enough power", "Energía insuficiente"), ("output_full", "Output full", "Salida llena"),
                      ("tier_too_low", "Needs a higher tier machine", "Requiere una máquina de mayor nivel"),
                      ("no_fuel", "No fuel", "Sin combustible"), ("full", "Energy buffer full", "Batería interna llena"),
                      ("finished", "Area finished", "Área terminada")]:
        add(f"{S}.{key}", e, s)

    G = f"gui.{MOD}"
    add(f"{G}.energy", "%s / %s FE", "%s / %s FE")
    add(f"{G}.using", "Using %s FE/t", "Consumiendo %s FE/t")
    add(f"{G}.generating", "Generating %s FE/t", "Generando %s FE/t")
    add(f"{G}.cell_in", "In: %s FE/t", "Entrada: %s FE/t")
    add(f"{G}.cell_out", "Out: %s FE/t", "Salida: %s FE/t")
    add(f"{G}.progress", "Progress: %s%%", "Progreso: %s%%")
    add(f"{G}.rate", "Output: %s items/min", "Producción: %s objetos/min")
    add(f"{G}.speed", "Speed: %s%%", "Velocidad: %s%%")
    add(f"{G}.eject_on", "Auto-eject: ON (click to toggle)", "Expulsión automática: SÍ (clic para cambiar)")
    add(f"{G}.eject_off", "Auto-eject: OFF (click to toggle)", "Expulsión automática: NO (clic para cambiar)")
    add(f"{G}.slot_locked", "Unlocks at %s tier", "Se desbloquea en nivel %s")
    add(f"{G}.sunlight", "Sunlight: %s%%", "Luz solar: %s%%")
    add(f"{G}.lava", "Lava sources: %s / 5", "Fuentes de lava: %s / 5")
    add(f"{G}.miner_info", "R %s  Y %s", "R %s  Y %s")
    add(f"{G}.miner_progress", "Area swept: %s%%", "Área recorrida: %s%%")
    add(f"{G}.miner_rate", "Up to %s ores/min", "Hasta %s menas/min")

    write(ASSETS / "lang" / "en_us.json", en)
    for loc in ["es_es", "es_ar", "es_mx", "es_cl", "es_uy", "es_ve", "es_ec"]:
        write(ASSETS / "lang" / f"{loc}.json", es)


# ============================================================ loot tables

def loot_self(name):
    write(DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": ns(name)}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
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
    for t in TIERS:
        for m in MACHINES + ["power_cable", "item_pipe"]:
            loot_self(f"{t}_{m}")
    for name, (raw, _, _) in ORES.items():
        loot_ore(name, raw)
    for m in STORAGE_METALS:
        loot_self(f"{m}_block")


# ============================================================ tags

def tag(namespace, kind, path, values):
    write(DATA / namespace / "tags" / kind / f"{path}.json", {"replace": False, "values": [ns(v) if not v.startswith("#") else v for v in values]})


def tags():
    machine_blocks = [f"{t}_{m}" for t in TIERS for m in MACHINES]
    conduits = [f"{t}_{k}" for t in TIERS for k in ("power_cable", "item_pipe")]
    storage = [f"{m}_block" for m in STORAGE_METALS]
    tag("minecraft", "block", "mineable/pickaxe", machine_blocks + conduits + list(ORES) + storage)
    tag("minecraft", "block", "needs_stone_tool", [o for o, v in ORES.items() if v[1] == "stone"] + storage)
    tag("minecraft", "block", "needs_iron_tool", [o for o, v in ORES.items() if v[1] == "iron"])

    by_metal = {}
    for ore, metal in ORE_METAL.items():
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
    tag("c", "item", "silicon", ["silicon"])
    tag("c", "item", "tools/wrench", ["wrench"])


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
    ore("bauxite", [("bauxite_ore", "stone"), ("deepslate_bauxite_ore", "deepslate")], 8, 6, 32, 128, "uniform")
    ore("titanium", [("deepslate_titanium_ore", "deepslate")], 5, 4, -64, 0, "trapezoid")
    write(DATA / MOD / "neoforge" / "biome_modifier" / "ores.json", {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": [f"{MOD}:ore_tin", f"{MOD}:ore_bauxite", f"{MOD}:ore_titanium"],
        "step": "underground_ores"})


# ============================================================ recipes

RECIPES = DATA / MOD / "recipe"


def ing(x):
    """'minecraft:iron_ingot', 'plate' (mod item) or '#c:ingots/iron' -> ingredient JSON."""
    if x.startswith("#"):
        return x
    return x if ":" in x else f"{MOD}:{x}"


def result(item, count=1):
    r = {"id": ing(item)}
    if count != 1:
        r["count"] = count
    return r


def shaped(name, pattern, key, out, count=1, category="misc"):
    write(RECIPES / f"{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                     "key": {k: ing(v) for k, v in key.items()}, "result": result(out, count)})


def shapeless(name, ingredients, out, count=1, category="misc"):
    write(RECIPES / f"{name}.json", {"type": "minecraft:crafting_shapeless", "category": category,
                                     "ingredients": [ing(i) for i in ingredients], "result": result(out, count)})


def smelt(name, inp, out, xp=0.7, time=200, blasting=True):
    write(RECIPES / f"{name}.json", {"type": "minecraft:smelting", "category": "misc", "ingredient": ing(inp),
                                     "result": result(out), "experience": xp, "cookingtime": time})
    if blasting:
        write(RECIPES / f"{name}_blasting.json", {"type": "minecraft:blasting", "category": "misc", "ingredient": ing(inp),
                                                  "result": result(out), "experience": xp, "cookingtime": time // 2})


def machine(kind, name, ingredients, out, count=1, time=40, min_tier=1, byproduct=None, mold=None):
    obj = {"type": f"{MOD}:{kind}",
           "ingredients": [{"ingredient": ing(i), "count": c} for i, c in ingredients],
           "result": result(out, count), "time": time}
    if min_tier > 1:
        obj["min_tier"] = min_tier
    if byproduct:
        obj["byproduct"] = {"item": result(byproduct[0], byproduct[1] if len(byproduct) > 2 else 1), "chance": byproduct[-1]}
    if mold:
        obj["mold"] = ing(mold)
    write(RECIPES / kind / f"{name}.json", obj)


def recipes():
    # ---------------------------------------------------------------- bootstrap (crafting table)
    shaped("forge_hammer", [" I ", "ISI", " S "], {"I": "#c:ingots/iron", "S": "#c:rods/wooden"}, "forge_hammer", category="equipment")
    shaped("wrench", ["I I", " C ", " I "], {"I": "#c:ingots/iron", "C": "#c:ingots/copper"}, "wrench", category="equipment")
    for metal, src in [("iron", "#c:ingots/iron"), ("copper", "#c:ingots/copper"), ("tin", "#c:ingots/tin"), ("bronze", "#c:ingots/bronze")]:
        shapeless(f"{metal}_plate_by_hammer", [src, src, "forge_hammer"], f"{metal}_plate")
    shaped("iron_gear_by_hand", [" I ", "I I", " I "], {"I": "#c:ingots/iron"}, "iron_gear")
    shapeless("plate_mold", ["#c:plates/iron"] * 4 + ["forge_hammer"], "plate_mold")
    shapeless("gear_mold", ["#c:plates/iron"] * 4 + ["#c:gears/iron"], "gear_mold")
    shapeless("rod_mold", ["#c:plates/iron"] * 4 + ["minecraft:stick"], "rod_mold")
    shapeless("wire_mold", ["#c:plates/iron"] * 4 + ["minecraft:string"], "wire_mold")
    shaped("basic_machine_frame", ["PCP", "C C", "PCP"], {"P": "#c:plates/iron", "C": "#c:ingots/copper"}, "basic_machine_frame")

    # ---------------------------------------------------------------- basic machines
    P, M, R = "#c:plates/iron", "basic_machine_frame", "minecraft:redstone"
    basic = {
        "electric_furnace": (["PFP", "CMC", "PRP"], {"F": "minecraft:furnace", "C": "#c:ingots/copper"}),
        "crusher": (["PFP", "GMG", "PRP"], {"F": "minecraft:flint", "G": "#c:gears/iron"}),
        "metal_press": (["PNP", "GMG", "PRP"], {"N": "minecraft:piston", "G": "#c:gears/iron"}),
        "alloy_smelter": (["PBP", "CMC", "PRP"], {"B": "minecraft:blast_furnace", "C": "#c:ingots/copper"}),
        "assembler": (["PTP", "GMG", "PRP"], {"T": "minecraft:crafting_table", "G": "#c:gears/iron"}),
        "miner": (["PDP", "GMG", "PHP"], {"D": "minecraft:iron_pickaxe", "G": "#c:gears/iron", "H": "minecraft:hopper"}),
        "combustion_generator": (["PCP", "FMF", "PRP"], {"F": "minecraft:furnace", "C": "#c:ingots/copper"}),
        "solar_panel": (["DDD", "CMC", "PRP"], {"D": "minecraft:daylight_detector", "C": "#c:ingots/copper"}),
        "geothermal_generator": (["PLP", "CMC", "PRP"], {"L": "minecraft:magma_block", "C": "#c:ingots/copper"}),
        "energy_cell": (["PBP", "BMB", "PCP"], {"B": "minecraft:redstone_block", "C": "minecraft:copper_block"}),
    }
    for m, (pattern, key) in basic.items():
        full = {"P": P, "M": M, "R": R, **key}
        used = set("".join(pattern))
        shaped(f"basic_{m}", pattern, {k: v for k, v in full.items() if k in used}, f"basic_{m}")

    # ---------------------------------------------------------------- tier kits and crafted upgrades
    kit_plate = {"reinforced": "#c:plates/steel", "advanced": "#c:plates/aluminum",
                 "elite": "#c:plates/titanium", "ultimate": "#c:ingots/quantum_alloy"}
    for i, t in enumerate(TIERS[1:], start=1):
        shaped(f"{t}_upgrade_kit", [" C ", "PFP", " C "],
               {"C": f"{t}_circuit", "P": kit_plate[t], "F": f"{t}_machine_frame"}, f"{t}_upgrade_kit")
        prev = TIERS[i - 1]
        for m in MACHINES:
            shapeless(f"{t}_{m}_from_{prev}", [f"{prev}_{m}", f"{t}_upgrade_kit"], f"{t}_{m}")

    # ---------------------------------------------------------------- cables & pipes
    shaped("basic_power_cable", ["WWW", "CCC", "WWW"], {"W": "#minecraft:wool", "C": "#c:ingots/copper"}, "basic_power_cable", 12)
    shaped("basic_item_pipe", ["PGP"], {"P": "#c:plates/iron", "G": "#c:glass_blocks/colorless"}, "basic_item_pipe", 6)
    conduit_upgrade = {"reinforced": "#c:ingots/steel", "advanced": "#c:ingots/aluminum",
                       "elite": "#c:ingots/titanium", "ultimate": "#c:ingots/quantum_alloy"}
    for i, t in enumerate(TIERS[1:], start=1):
        prev = TIERS[i - 1]
        for kind in ("power_cable", "item_pipe"):
            shaped(f"{t}_{kind}", ["CCC", "CXC", "CCC"], {"C": f"{prev}_{kind}", "X": conduit_upgrade[t]}, f"{t}_{kind}", 8)

    # ---------------------------------------------------------------- upgrade cards
    shaped("speed_upgrade", ["ARA", "RCR", "ARA"],
           {"A": "#c:plates/aluminum", "R": "minecraft:redstone", "C": "reinforced_circuit"}, "speed_upgrade", 2)
    shaped("energy_upgrade", ["ALA", "LCL", "ALA"],
           {"A": "#c:plates/aluminum", "L": "#c:gems/lapis", "C": "reinforced_circuit"}, "energy_upgrade", 2)

    # ---------------------------------------------------------------- storage blocks
    for m in STORAGE_METALS:
        shaped(f"{m}_block", ["III", "III", "III"], {"I": f"#c:ingots/{m}"}, f"{m}_block", category="building")
        shapeless(f"{m}_ingot_from_block", [f"{m}_block"], f"{m}_ingot", 9)

    # ---------------------------------------------------------------- furnace (vanilla and our Electric Furnace)
    for d in ["iron", "copper", "gold"]:
        smelt(f"{d}_ingot_from_dust", f"{d}_dust", f"minecraft:{d}_ingot", 0.3, 200)
    smelt("tin_ingot_from_dust", "tin_dust", "tin_ingot", 0.3, 200)
    smelt("tin_ingot_from_raw", "raw_tin", "tin_ingot", 0.7, 200)
    smelt("tin_ingot_from_ore", "#c:ores/tin", "tin_ingot", 0.7, 200)

    # Electric-Furnace-only smelting (min tier gates the late metals)
    machine("smelting", "aluminum_from_dust", [("bauxite_dust", 1)], "aluminum_ingot", time=40)
    machine("smelting", "aluminum_from_raw", [("raw_bauxite", 1)], "aluminum_ingot", time=60)
    machine("smelting", "aluminum_from_ore", [("#c:ores/aluminum", 1)], "aluminum_ingot", time=60)
    machine("smelting", "titanium_from_dust", [("titanium_dust", 1)], "titanium_ingot", time=100, min_tier=3)
    machine("smelting", "titanium_from_raw", [("raw_titanium", 1)], "titanium_ingot", time=160, min_tier=3)
    machine("smelting", "titanium_from_ore", [("#c:ores/titanium", 1)], "titanium_ingot", time=160, min_tier=3)

    # ---------------------------------------------------------------- crusher: 2x / 3x / 4x ore yields by tier
    crush = {"iron": ("#c:raw_materials/iron", "#c:ores/iron", ("tin_dust", 0.10)),
             "copper": ("#c:raw_materials/copper", "#c:ores/copper", ("gold_dust", 0.10)),
             "gold": ("#c:raw_materials/gold", "#c:ores/gold", None),
             "tin": ("#c:raw_materials/tin", "#c:ores/tin", ("iron_dust", 0.10)),
             "bauxite": ("#c:raw_materials/aluminum", "#c:ores/aluminum", None),
             "titanium": ("#c:raw_materials/titanium", "#c:ores/titanium", ("iron_dust", 0.15))}
    for metal, (raw, ore_tag, by) in crush.items():
        for count, tier in ((2, 1), (3, 3), (4, 5)):
            suffix = {1: "", 3: "_advanced", 5: "_ultimate"}[tier]
            machine("crushing", f"{metal}_dust_from_raw{suffix}", [(raw, 1)], f"{metal}_dust", count,
                    time=60, min_tier=tier, byproduct=by)
            machine("crushing", f"{metal}_dust_from_ore{suffix}", [(ore_tag, 1)], f"{metal}_dust", count + 1,
                    time=80, min_tier=tier, byproduct=by)
    machine("crushing", "coal_dust", [("minecraft:coal", 1)], "coal_dust", time=30)
    machine("crushing", "coal_dust_from_charcoal", [("minecraft:charcoal", 1)], "coal_dust", time=30)
    machine("crushing", "quartz_dust", [("minecraft:quartz", 1)], "quartz_dust", time=30)
    machine("crushing", "iron_dust_from_ingot", [("#c:ingots/iron", 1)], "iron_dust", time=40)
    machine("crushing", "gravel", [("#c:cobblestones/normal", 1)], "minecraft:gravel", time=30)
    machine("crushing", "sand", [("minecraft:gravel", 1)], "minecraft:sand", time=30, byproduct=("minecraft:flint", 0.15))
    machine("crushing", "bone_meal", [("minecraft:bone", 1)], "minecraft:bone_meal", 6, time=20)
    machine("crushing", "blaze_powder", [("minecraft:blaze_rod", 1)], "minecraft:blaze_powder", 4, time=20)
    machine("crushing", "string", [("#minecraft:wool", 1)], "minecraft:string", 4, time=20)

    # ---------------------------------------------------------------- metal press (mould selects the shape)
    for p in PLATES:
        machine("pressing", f"{p}_plate", [(f"#c:ingots/{p}", 1)], f"{p}_plate", time=40,
                min_tier=3 if p == "titanium" else 1, mold="plate_mold")
    for g in GEARS:
        machine("pressing", f"{g}_gear", [(f"#c:ingots/{g}", 2)], f"{g}_gear", time=60,
                min_tier=3 if g == "titanium" else 1, mold="gear_mold")
    for r in RODS:
        machine("pressing", f"{r}_rod", [(f"#c:ingots/{r}", 1)], f"{r}_rod", 2, time=40, mold="rod_mold")
    for w in WIRES:
        machine("pressing", f"{w}_wire", [(f"#c:ingots/{w}", 1)], f"{w}_wire", 3, time=40, mold="wire_mold")
    machine("pressing", "silicon_wafer", [("silicon", 1)], "silicon_wafer", 2, time=60, mold="plate_mold")

    # ---------------------------------------------------------------- alloy smelter
    machine("alloying", "bronze", [("#c:ingots/copper", 3), ("#c:ingots/tin", 1)], "bronze_ingot", 4, time=80)
    machine("alloying", "bronze_from_dust", [("#c:dusts/copper", 3), ("#c:dusts/tin", 1)], "bronze_ingot", 4, time=60)
    machine("alloying", "steel", [("#c:ingots/iron", 1), ("#c:dusts/coal", 2)], "steel_ingot", time=100)
    machine("alloying", "steel_from_dust", [("#c:dusts/iron", 1), ("#c:dusts/coal", 1)], "steel_ingot", time=60)
    machine("alloying", "silicon", [("#c:dusts/quartz", 2), ("#c:dusts/coal", 1)], "silicon", time=80, min_tier=2)
    machine("alloying", "quantum_alloy", [("#c:ingots/titanium", 1), ("minecraft:ender_pearl", 1)],
            "quantum_alloy_ingot", time=200, min_tier=4)
    machine("alloying", "netherite", [("minecraft:netherite_scrap", 4), ("#c:ingots/gold", 4)],
            "minecraft:netherite_ingot", time=200, min_tier=4)

    # ---------------------------------------------------------------- assembler (tier-gated components)
    machine("assembling", "basic_circuit", [("#c:wires/copper", 3), ("minecraft:redstone", 2), ("#c:plates/iron", 1)],
            "basic_circuit", time=80)
    machine("assembling", "reinforced_circuit", [("basic_circuit", 1), ("#c:plates/steel", 1), ("#c:wires/gold", 2),
                                                  ("minecraft:redstone", 4)], "reinforced_circuit", time=120)
    machine("assembling", "advanced_circuit", [("reinforced_circuit", 1), ("silicon_wafer", 2), ("#c:wires/gold", 4),
                                                ("#c:gems/quartz", 2)], "advanced_circuit", time=160, min_tier=2)
    machine("assembling", "elite_circuit", [("advanced_circuit", 2), ("#c:plates/titanium", 1), ("#c:gems/diamond", 1),
                                             ("silicon_wafer", 4)], "elite_circuit", time=200, min_tier=3)
    machine("assembling", "ultimate_circuit", [("elite_circuit", 2), ("#c:ingots/quantum_alloy", 2),
                                                ("#c:gems/diamond", 2), ("minecraft:ender_eye", 1)],
            "ultimate_circuit", time=300, min_tier=4)
    machine("assembling", "motor", [("#c:rods/iron", 2), ("#c:wires/copper", 4), ("#c:plates/iron", 2)], "motor", time=80)
    machine("assembling", "heating_coil", [("#c:wires/copper", 8), ("#c:rods/iron", 1)], "heating_coil", time=60)
    machine("assembling", "reinforced_machine_frame", [("basic_machine_frame", 1), ("#c:plates/steel", 4),
                                                        ("#c:gears/bronze", 2)], "reinforced_machine_frame", time=120)
    machine("assembling", "advanced_machine_frame", [("reinforced_machine_frame", 1), ("#c:plates/aluminum", 4),
                                                      ("#c:gears/steel", 2), ("motor", 1)],
            "advanced_machine_frame", time=160, min_tier=2)
    machine("assembling", "elite_machine_frame", [("advanced_machine_frame", 1), ("#c:plates/titanium", 4),
                                                   ("#c:gears/titanium", 2), ("heating_coil", 2)],
            "elite_machine_frame", time=200, min_tier=3)
    machine("assembling", "ultimate_machine_frame", [("elite_machine_frame", 1), ("#c:ingots/quantum_alloy", 4),
                                                      ("minecraft:netherite_ingot", 1), ("#c:gears/titanium", 4)],
            "ultimate_machine_frame", time=300, min_tier=4)


def main():
    clean()
    machines()
    cables_and_pipes()
    simple_blocks()
    items()
    lang()
    loot()
    tags()
    worldgen()
    recipes()
    count = sum(1 for _ in ROOT.rglob("*.json"))
    print(f"wrote resources, {count} json files under {ROOT}")


if __name__ == "__main__":
    main()

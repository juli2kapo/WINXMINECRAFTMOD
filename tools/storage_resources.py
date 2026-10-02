#!/usr/bin/env python3
"""Resources of the Storage Network (controller, cable, drive, cells, terminal, interface).

Loaded by tools/gen_resources.py, which calls generate(write, ASSETS, DATA, MOD), lang() and reads
PICKAXE_BLOCKS. Run this file directly to (re)draw the storage textures:

    python3 tools/storage_resources.py --textures
"""
import random
import sys
from pathlib import Path

MOD = "factoryascent"

CABLE = "storage_cable"
# id -> (front texture base, has an "on" front)
DEVICES = {
    "storage_controller": True,
    "storage_drive": True,
    "storage_terminal": True,
    "storage_interface": True,
}
CELLS = {"storage_cell_1k": 1024, "storage_cell_4k": 4096}
BLOCKS = [CABLE] + list(DEVICES)
PICKAXE_BLOCKS = list(BLOCKS)

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ["north", "east", "south", "west", "up", "down"]
ARM_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def ns(path):
    return path if ":" in path else f"{MOD}:{path}"


# ============================================================ generate

def generate(write, ASSETS, DATA, MOD_ID):
    assert MOD_ID == MOD
    models(write, ASSETS)
    loot(write, DATA)
    recipes(write, DATA)
    advancements(write, DATA)


def models(write, ASSETS):
    def block_model(name, obj):
        write(ASSETS / "models" / "block" / f"{name}.json", obj)

    def item_def(name, model):
        write(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": ns(model)}})

    device_models(write, ASSETS, block_model, item_def)

    # Cable: a thin core plus one arm per connected side (same shape as the power cables).
    t = f"{MOD}:block/{CABLE}"
    lo, hi = 6, 10
    core = {"from": [lo, lo, lo], "to": [hi, hi, hi], "faces": {d: {"uv": [lo, lo, hi, hi], "texture": "#t"} for d in DIRS}}

    def arm(z0, z1):
        return {"from": [lo, lo, z0], "to": [hi, hi, z1], "faces": {
            "north": {"uv": [lo, lo, hi, hi], "texture": "#t"}, "south": {"uv": [lo, lo, hi, hi], "texture": "#t"},
            "up": {"uv": [lo, z0, hi, z1], "texture": "#t"}, "down": {"uv": [lo, z0, hi, z1], "texture": "#t"},
            "east": {"uv": [lo, z0, hi, z1], "texture": "#t", "rotation": 90},
            "west": {"uv": [lo, z0, hi, z1], "texture": "#t", "rotation": 90}}}
    block_model(f"{CABLE}_core", {"parent": "minecraft:block/block", "textures": {"t": t, "particle": t}, "elements": [core]})
    block_model(f"{CABLE}_arm", {"parent": "minecraft:block/block", "textures": {"t": t, "particle": t}, "elements": [arm(0, lo)]})
    write(ASSETS / "models" / "item" / f"{CABLE}.json", {
        "parent": "minecraft:block/block", "textures": {"t": t, "particle": t},
        "elements": [core, arm(0, lo), arm(hi, 16)],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    parts = [{"apply": {"model": f"{MOD}:block/{CABLE}_core"}}]
    for d in DIRS:
        parts.append({"when": {d: "true"}, "apply": {"model": f"{MOD}:block/{CABLE}_arm", **ARM_ROT[d]}})
    write(ASSETS / "blockstates" / f"{CABLE}.json", {"multipart": parts})
    item_def(CABLE, f"item/{CABLE}")

    for cell in CELLS:
        write(ASSETS / "models" / "item" / f"{cell}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{cell}"}})
        item_def(cell, f"item/{cell}")


# ------------------------------------------------------------ device models
# Every device has its own shape (all face north in the model; the blockstate turns them).

FACE_DIRS = ("north", "south", "west", "east", "up", "down")


def _el(frm, to, faces, rotation=None):
    """An element; faces maps direction -> texture key or (key, uv). Boundary faces get a cullface."""
    out = {}
    for d, spec in faces.items():
        key, uv = (spec, None) if isinstance(spec, str) else spec
        f = {"texture": "#" + key}
        if uv:
            f["uv"] = uv
        cull = {"north": frm[2] == 0, "south": to[2] == 16, "west": frm[0] == 0, "east": to[0] == 16,
                "down": frm[1] == 0, "up": to[1] == 16}[d]
        if cull and rotation is None:
            f["cullface"] = d
        out[d] = f
    e = {"from": list(frm), "to": list(to), "faces": out}
    if rotation:
        e["rotation"] = rotation
    return e


def _all(key, **over):
    return {d: over.get(d, key) for d in FACE_DIRS}


def _tex(**names):
    t = {k: f"{MOD}:block/{v}" for k, v in names.items()}
    return t


def _controller_model(on):
    s = "_on" if on else ""
    tex = _tex(rack=f"storage_controller_rack{s}", side="storage_controller_side", top="storage_controller_top",
               post="storage_controller_post", core=f"storage_controller_core{s}", core_side="storage_controller_core_side",
               base="storage_controller_base", particle="storage_controller_side")
    els = [
        _el((0, 0, 0), (16, 1, 16), _all("base")),                                        # plinth
        _el((1, 1, 2), (15, 15, 15), {"north": "rack", "south": "side", "west": "side", "east": "side"}),
        _el((0, 15, 0), (16, 16, 16), _all("top", north="base", south="base", west="base", east="base", down="base")),
    ]
    for x0 in (0, 14):                                                                   # rack rails
        for z0 in (0, 14):
            els.append(_el((x0, 1, z0), (x0 + 2, 15, z0 + 2), _all("post")))
    els.append(_el((5, 5, 0.5), (11, 11, 2), {"north": "core", "west": "core_side", "east": "core_side",
                                               "up": "core_side", "down": "core_side"}))  # glowing core housing
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


BAY_Y = (11, 8, 5, 2)   # bottom of bay 0..3 (top bay first)


def _drive_model(on):
    s = "_on" if on else ""
    tex = _tex(bay=f"storage_drive_bays{s}", bezel="storage_drive_bezel", side="storage_drive_side",
               top="storage_drive_top", particle="storage_drive_side")
    els = [
        _el((0, 0, 3), (16, 16, 16), {"north": "bay", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}),
        _el((0, 0, 0), (2, 16, 3), {"north": "bezel", "west": "bezel", "east": "bezel", "up": "bezel", "down": "bezel"}),
        _el((14, 0, 0), (16, 16, 3), {"north": "bezel", "west": "bezel", "east": "bezel", "up": "bezel", "down": "bezel"}),
        _el((2, 14, 0), (14, 16, 3), {"north": "bezel", "up": "bezel", "down": "bezel"}),
        _el((2, 0, 0), (14, 2, 3), {"north": "bezel", "up": "bezel", "down": "bezel"}),
    ]
    for y in (5, 8, 11):                                                                 # shelves between bays
        els.append(_el((2, y - 0.5, 1), (14, y + 0.5, 3), {"north": "bezel", "up": "bezel", "down": "bezel"}))
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def _drive_cell_model(slot, kind):
    """A cell cartridge sitting in bay `slot`, sticking out of the drive front."""
    y0 = BAY_Y[slot] + 0.5
    t = f"{MOD}:block/storage_drive_cart_{'4k' if kind == 2 else '1k'}"
    face = {"north": ("c", [0, 0, 10, 2]), "up": ("c", [0, 2, 10, 4]), "down": ("c", [0, 2, 10, 4]),
            "west": ("c", [10, 0, 12, 2]), "east": ("c", [10, 0, 12, 2])}
    return {"parent": "minecraft:block/block", "textures": {"c": t, "particle": t},
            "elements": [_el((3, y0, 0.5), (13, y0 + 2, 3), face)]}


def _terminal_model(on):
    s = "_on" if on else ""
    tex = _tex(cab="storage_terminal_cabinet", side="storage_terminal_side", keys="storage_terminal_keys",
               screen=f"storage_terminal_screen{s}", shell="storage_terminal_shell", particle="storage_terminal_side")
    els = [
        _el((0, 0, 0), (16, 7, 16), {"north": "cab", "south": "side", "west": "side", "east": "side", "up": "shell", "down": "shell"}),
        # slanted keyboard deck
        _el((1, 6, 1), (15, 8, 8), {"up": "keys", "north": "shell", "west": "shell", "east": "shell", "south": "shell"},
            rotation={"angle": -22.5, "axis": "x", "origin": [8, 7, 8]}),
        # monitor
        _el((1, 7, 9), (15, 16, 15), {"north": "screen", "south": "shell", "west": "shell", "east": "shell", "up": "shell"}),
        _el((5, 9, 15), (11, 14, 16), _all("shell")),                                  # monitor back hump
    ]
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def _interface_model(on):
    s = "_on" if on else ""
    tex = _tex(mouth=f"storage_interface_mouth{s}", side="storage_interface_side", top="storage_interface_top",
               collar="storage_interface_collar", belt="storage_interface_belt", particle="storage_interface_side")
    els = [
        _el((0, 0, 3), (16, 16, 16), {"north": "mouth", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}),
        _el((2, 2, 0), (14, 4, 3), {"north": "collar", "up": "belt", "down": "collar", "west": "collar", "east": "collar"}),
        _el((2, 12, 0), (14, 14, 3), {"north": "collar", "up": "collar", "down": "collar", "west": "collar", "east": "collar"}),
        _el((2, 4, 0), (4, 12, 3), {"north": "collar", "west": "collar", "east": "collar"}),
        _el((12, 4, 0), (14, 12, 3), {"north": "collar", "west": "collar", "east": "collar"}),
    ]
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


DEVICE_MODELS = {"storage_controller": _controller_model, "storage_drive": _drive_model,
                 "storage_terminal": _terminal_model, "storage_interface": _interface_model}


def device_models(write, ASSETS, block_model, item_def):
    for dev, fn in DEVICE_MODELS.items():
        for on in (False, True):
            block_model(dev + ("_on" if on else ""), fn(on))
        base = {}
        for facing, rot in FACINGS.items():
            for online in ("false", "true"):
                v = {"model": f"{MOD}:block/{dev}" + ("_on" if online == "true" else "")}
                if rot:
                    v["y"] = rot
                base[f"facing={facing},online={online}"] = v
        if dev != "storage_drive":
            write(ASSETS / "blockstates" / f"{dev}.json", {"variants": base})
        else:
            parts = []
            for key, v in base.items():
                facing, online = (kv.split("=")[1] for kv in key.split(","))
                parts.append({"when": {"facing": facing, "online": online}, "apply": v})
            for slot in range(4):
                for kind in (1, 2):
                    name = f"storage_drive_cell{slot}_{kind}"
                    block_model(name, _drive_cell_model(slot, kind))
                    for facing, rot in FACINGS.items():
                        apply = {"model": f"{MOD}:block/{name}"}
                        if rot:
                            apply["y"] = rot
                        parts.append({"when": {"facing": facing, f"cell{slot}": str(kind)}, "apply": apply})
            write(ASSETS / "blockstates" / f"{dev}.json", {"multipart": parts})
        item_def(dev, f"block/{dev}")


def loot(write, DATA):
    for name in BLOCKS:
        write(DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": ns(name)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{MOD}:blocks/{name}"})


def recipes(write, DATA):
    out_dir = DATA / MOD / "recipe"

    def ing(x):
        return x if x.startswith("#") or ":" in x else f"{MOD}:{x}"

    def shaped(name, pattern, key, out, count=1):
        used = set("".join(pattern)) - {" "}
        result = {"id": ing(out)}
        if count != 1:
            result["count"] = count
        write(out_dir / f"{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                         "key": {k: ing(v) for k, v in key.items() if k in used}, "result": result})

    S, I, W = "#c:plates/steel", "#c:plates/iron", "#c:wires/copper"
    C, F = "basic_circuit", "machine_frame"
    shaped(CABLE, ["III", "WRW", "III"], {"I": I, "W": W, "R": "minecraft:redstone"}, CABLE, count=8)
    shaped("storage_controller", ["SCS", "WFW", "SCS"], {"S": S, "C": C, "W": W, "F": F}, "storage_controller")
    shaped("storage_drive", ["SWS", "HFH", "SCS"], {"S": S, "W": W, "H": "minecraft:chest", "F": F, "C": C}, "storage_drive")
    shaped("storage_terminal", ["SGS", "CFC", "SWS"], {"S": S, "G": "minecraft:glass_pane", "C": C, "F": F, "W": W},
           "storage_terminal")
    shaped("storage_interface", ["SHS", "CFC", "SWS"], {"S": S, "H": "minecraft:hopper", "C": C, "F": F, "W": W},
           "storage_interface")
    # Cells are never an ingredient (a full cell would lose its items in the crafting grid).
    shaped("storage_cell_1k", ["GRG", "RCR", "SSS"], {"G": "minecraft:glass", "R": "minecraft:redstone", "C": C, "S": S},
           "storage_cell_1k")
    shaped("storage_cell_4k", ["GRG", "CDC", "SSS"], {"G": "minecraft:glass", "R": "minecraft:redstone_block", "C": C,
                                                    "D": "minecraft:diamond", "S": S}, "storage_cell_4k")


ADV_TEXT = {
    "storage_network": ("Storage Network", "Craft a Storage Controller: the heart of a centralized storage network",
                        "Red de almacenamiento",
                        "Fabrica un controlador de almacenamiento: el corazón de un almacén centralizado"),
    "storage_cells": ("Bigger on the Inside", "Craft a 4k Storage Cell", "Más grande por dentro",
                      "Fabrica una celda de almacenamiento de 4k"),
}


def advancements(write, DATA):
    def adv(key, parent, icon, items, frame="task"):
        te, de, _, _ = ADV_TEXT[key]
        write(DATA / MOD / "advancement" / f"{key}.json", {
            "parent": f"{MOD}:{parent}",
            "criteria": {"got": {"trigger": "minecraft:inventory_changed",
                                 "conditions": {"items": [{"items": ns(items[0]) if len(items) == 1 else [ns(i) for i in items]}]}}},
            "display": {"icon": {"id": ns(icon)}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
                        "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame},
            "requirements": [["got"]]})
    adv("storage_network", "age_electric", "storage_controller", ["storage_controller"])
    adv("storage_cells", "storage_network", "storage_cell_4k", ["storage_cell_4k"], frame="goal")


# ============================================================ lang

def lang():
    en, es = {}, {}

    def add(key, e, s):
        en[key] = e
        es[key] = s

    names = {
        CABLE: ("Storage Cable", "Cable de almacenamiento",
                "Joins storage devices into one network", "Une los dispositivos de almacenamiento en una red"),
        "storage_controller": ("Storage Controller", "Controlador de almacenamiento",
                               "Powers a storage network. Exactly one per network",
                               "Alimenta una red de almacenamiento. Exactamente uno por red"),
        "storage_drive": ("Storage Drive", "Unidad de almacenamiento",
                          "Holds 4 storage cells", "Contiene 4 celdas de almacenamiento"),
        "storage_terminal": ("Storage Terminal", "Terminal de almacenamiento",
                             "Search, take and store everything in the network",
                             "Busca, saca y guarda todo lo de la red"),
        "storage_interface": ("Storage Interface", "Interfaz de almacenamiento",
                              "Lets pipes and hoppers insert into and extract from the network",
                              "Permite que tuberías y tolvas metan y saquen objetos de la red"),
    }
    for block, (e, s, de, ds) in names.items():
        add(f"block.{MOD}.{block}", e, s)
        add(f"desc.{MOD}.{block}", de, ds)
    add(f"item.{MOD}.storage_cell_1k", "1k Storage Cell", "Celda de almacenamiento de 1k")
    add(f"item.{MOD}.storage_cell_4k", "4k Storage Cell", "Celda de almacenamiento de 4k")

    T = f"tooltip.{MOD}"
    add(f"{T}.storage_cell.used", "Items: %s / %s", "Objetos: %s / %s")
    add(f"{T}.storage_cell.types", "Types: %s / %s", "Tipos: %s / %s")
    add(f"{T}.storage_cell.more", "  …and %s more", "  …y %s más")
    add(f"{T}.storage_controller.power", "Uses %s FE/t + %s FE/t per device", "Consume %s FE/t + %s FE/t por dispositivo")

    G = f"gui.{MOD}.storage"
    add(f"{G}.status.online", "Online", "En línea")
    add(f"{G}.status.no_controller", "No controller", "Sin controlador")
    add(f"{G}.status.multiple_controllers", "Too many controllers", "Demasiados controladores")
    add(f"{G}.status.no_power", "No power", "Sin energía")
    add(f"{G}.controller_info", "%s / %s FE, %s FE/t, %s devices, %s drives",
        "%s / %s FE, %s FE/t, %s dispositivos, %s unidades")
    add(f"{G}.search", "Search…", "Buscar…")
    add(f"{G}.usage", "%s / %s items · %s / %s types", "%s / %s objetos · %s / %s tipos")
    add(f"{G}.stored", "Stored: %s", "Almacenado: %s")
    add(f"{G}.empty", "Empty", "Vacío")

    for key, (te, de, ts, ds) in ADV_TEXT.items():
        add(f"advancements.{MOD}.{key}.title", te, ts)
        add(f"advancements.{MOD}.{key}.description", de, ds)
    return en, es


# ============================================================ textures

TEX = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / MOD / "textures"

OUTLINE = (38, 42, 48)
CASE = (92, 100, 112)
CASE_LIGHT = (128, 138, 150)
CASE_DARK = (66, 72, 82)
TEAL = (63, 167, 181)
TEAL_LIGHT = (130, 220, 228)
TEAL_DARK = (30, 92, 104)
SCREEN_OFF = (22, 30, 36)
LED_OFF = (60, 40, 40)
LED_ON = (90, 240, 120)


def _img():
    from PIL import Image
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def _rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def _noise(im, name, amount=6):
    rnd = random.Random(name)
    for x in range(16):
        for y in range(16):
            r, g, b, a = im.getpixel((x, y))
            if a:
                d = rnd.randint(-amount, amount)
                im.putpixel((x, y), (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a))


GUN = (52, 56, 66)
GUN_L = (78, 84, 98)
GUN_D = (30, 32, 38)
AMBER = (240, 168, 48)
AMBER_DARK = (110, 70, 20)
GREEN = (90, 220, 110)
GREEN_DARK = (40, 80, 50)


def _put(im, x, y, c):
    im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def _plate(name, fill, light, dark, noise=5):
    im = _img()
    _rect(im, 0, 0, 15, 15, fill)
    _rect(im, 0, 0, 15, 0, light)
    _rect(im, 0, 0, 0, 15, light)
    _rect(im, 0, 15, 15, 15, dark)
    _rect(im, 15, 0, 15, 15, dark)
    _noise(im, name, noise)
    return im


# ---- controller: dark server rack on rails with a glowing core

def _ctl_rack(on):
    """Front of the rack: five server blades, each with a status LED and an activity bar."""
    im = _img()
    _rect(im, 0, 0, 15, 15, GUN_D)
    rnd = random.Random("ctl_rack")
    for i, y in enumerate((1, 4, 7, 10, 13)):
        _rect(im, 1, y, 14, y + 1, GUN)
        _rect(im, 1, y, 14, y, GUN_L)
        _put(im, 2, y + 1, GREEN if on else LED_OFF)
        _put(im, 3, y + 1, (AMBER if (on and i % 2) else GREEN_DARK if on else LED_OFF))
        for x in range(10, 14):  # activity bar
            lit = on and rnd.random() < 0.6
            _put(im, x, y + 1, TEAL_LIGHT if lit else (24, 28, 34))
    _noise(im, "ctl_rack", 3)
    return im


def _ctl_side():
    im = _plate("ctl_side", GUN, GUN_L, GUN_D)
    for x in range(3, 13, 3):          # vertical vent slots
        _rect(im, x, 2, x, 13, GUN_D)
        _rect(im, x + 1, 2, x + 1, 13, GUN_L)
    return im


def _ctl_top():
    im = _plate("ctl_top", GUN, GUN_L, GUN_D)
    for x in range(16):                 # round fan grille
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 2 <= d <= 5.6:
                _put(im, x, y, GUN_D if (x + y) % 2 else (40, 44, 52))
            elif d < 2:
                _put(im, x, y, CASE_LIGHT)
    return im


def _ctl_post():
    im = _plate("ctl_post", CASE, CASE_LIGHT, CASE_DARK, 4)
    for y in range(1, 16, 3):           # rack-mount holes
        for x in (4, 11):
            _put(im, x, y, OUTLINE)
    return im


def _ctl_base():
    im = _plate("ctl_base", CASE_DARK, CASE, OUTLINE, 4)
    for x in range(0, 16, 4):
        _put(im, x, 8, (240, 200, 60))
    return im


def _ctl_core(on):
    """The core lens: steel bezel, dark ring and a teal crystal that glows while the network is online."""
    im = _img()
    _rect(im, 0, 0, 15, 15, CASE)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.6:
                _put(im, x, y, OUTLINE)
            if d < 5.2:
                _put(im, x, y, TEAL if on else TEAL_DARK)
            if d < 3.4:
                _put(im, x, y, TEAL_LIGHT if on else (52, 120, 130))
            if d < 1.6:
                _put(im, x, y, (235, 255, 255) if on else (80, 150, 158))
    _put(im, 5, 5, (255, 255, 255) if on else (120, 170, 176))   # glint
    _rect(im, 0, 0, 15, 0, CASE_LIGHT)
    _rect(im, 0, 15, 15, 15, CASE_DARK)
    return im


def _ctl_core_side():
    im = _plate("ctl_core_side", CASE, CASE_LIGHT, CASE_DARK, 3)
    _rect(im, 0, 7, 15, 8, TEAL_DARK)
    return im


# ---- drive: light chassis with four bays; the cartridges are separate model parts

def _drv_bays(on):
    im = _img()
    _rect(im, 0, 0, 15, 15, CASE)
    for top in (2, 5, 8, 11):            # texture rows of each bay opening (model y 11..14 -> v 2..5)
        _rect(im, 2, top, 13, top + 2, (20, 22, 26))
        _rect(im, 4, top + 1, 11, top + 1, (40, 44, 52))   # connector
        for x in range(5, 11, 2):
            _put(im, x, top + 1, (200, 170, 70))           # gold pins
        _put(im, 13, top + 1, LED_ON if on else LED_OFF)
    _noise(im, "drv_bays", 3)
    return im


def _drv_bezel():
    im = _plate("drv_bezel", CASE_LIGHT, (170, 180, 192), CASE, 4)
    return im


def _drv_side():
    im = _plate("drv_side", CASE_LIGHT, (170, 180, 192), CASE)
    for y in (3, 6, 9, 12):             # drive rails seen through the side
        _rect(im, 2, y, 13, y, CASE_DARK)
        _rect(im, 2, y + 1, 13, y + 1, (170, 180, 192))
    _rect(im, 1, 14, 14, 14, TEAL_DARK)
    return im


def _drv_top():
    im = _plate("drv_top", CASE_LIGHT, (170, 180, 192), CASE)
    for x in range(2, 14, 2):
        _rect(im, x, 3, x, 12, CASE)
    return im


def _drv_cart(big):
    """Cartridge: row 0-1 = its 10x2 front (label, grip, LED), rows 2-3 = top/bottom, cols 10-11 = ends."""
    im = _img()
    body = (150, 158, 170)
    label = (216, 160, 40) if big else TEAL
    _rect(im, 0, 0, 11, 3, body)
    _rect(im, 0, 0, 9, 0, (196, 204, 214))
    _rect(im, 1, 0, 4, 1, label)
    _rect(im, 6, 1, 8, 1, (60, 66, 76))     # grip
    _put(im, 9, 0, LED_ON)
    _rect(im, 0, 2, 9, 3, (120, 128, 140))
    _rect(im, 0, 2, 3, 2, label)
    _rect(im, 10, 0, 11, 1, (92, 100, 112))
    return im


# ---- terminal: cabinet, slanted keyboard and a monitor

def _term_cabinet():
    im = _plate("term_cab", CASE, CASE_LIGHT, CASE_DARK)
    _rect(im, 2, 10, 13, 13, CASE_DARK)    # drawer (model y 0..7 -> rows 9..15)
    _rect(im, 6, 11, 9, 11, CASE_LIGHT)
    _rect(im, 0, 9, 15, 9, TEAL_DARK)
    return im


def _term_side():
    im = _plate("term_side", CASE, CASE_LIGHT, CASE_DARK)
    _rect(im, 0, 9, 15, 9, TEAL_DARK)
    return im


def _term_shell():
    return _plate("term_shell", GUN, GUN_L, GUN_D, 3)


def _term_keys():
    im = _img()
    _rect(im, 0, 0, 15, 15, GUN_D)
    for y in range(1, 15, 2):               # key rows (rows 8..15 used by the deck top)
        for x in range(1, 15, 2):
            _put(im, x, y, (186, 192, 200))
            _put(im, x + 1, y, (120, 126, 136))
    _rect(im, 4, 13, 11, 13, (186, 192, 200))   # space bar
    _rect(im, 12, 9, 13, 11, TEAL)              # enter key
    return im


def _term_screen(on):
    """Monitor face: bezel (rows 0-8, cols 1-14 are the front) with a search bar and an item grid."""
    im = _img()
    _rect(im, 0, 0, 15, 15, GUN)
    _rect(im, 2, 1, 13, 7, SCREEN_OFF)
    if on:
        _rect(im, 3, 2, 10, 2, (200, 230, 236))          # search bar
        _put(im, 12, 2, TEAL_LIGHT)
        cols = [(220, 80, 70), (90, 200, 100), (230, 200, 70), (90, 140, 230), (200, 200, 210), (170, 110, 220)]
        rnd = random.Random("terminal_grid")
        for y in (4, 6):
            for x in range(3, 13, 2):
                _put(im, x, y, cols[rnd.randrange(len(cols))])
    else:
        _put(im, 7, 4, (40, 52, 60))
    _rect(im, 1, 8, 14, 8, GUN_D)
    _put(im, 13, 8, LED_ON if on else LED_OFF)
    return im


# ---- interface: a port with an amber collar, conveyor floor and in/out arrows

def _ifc_mouth(on):
    """Back of the port: an import arrow (green, pointing in) over an export arrow (amber, pointing out)."""
    im = _img()
    _rect(im, 0, 0, 15, 15, CASE)
    _rect(im, 2, 2, 13, 13, (20, 22, 26))
    g = GREEN if on else GREEN_DARK
    a = AMBER if on else AMBER_DARK
    # top: green arrow pointing right
    _rect(im, 4, 6, 9, 6, g)
    for i in range(3):
        _rect(im, 9 + i, 4 + i, 9 + i, 8 - i, g)
    # bottom: amber arrow pointing left
    _rect(im, 6, 10, 11, 10, a)
    for i in range(3):
        _rect(im, 6 - i, 8 + i, 6 - i, 12 - i, a)
    _rect(im, 0, 0, 15, 0, CASE_LIGHT)
    _rect(im, 0, 15, 15, 15, CASE_DARK)
    return im


def _ifc_collar():
    """Amber port collar with dark chevron stripes and a bright lip."""
    im = _plate("ifc_collar", (204, 146, 44), (244, 196, 96), (132, 90, 26), 3)
    for x in range(16):
        for y in range(16):
            if (x + y) % 8 in (0, 1):
                _put(im, x, y, (150, 102, 30))
    return im


def _ifc_belt():
    im = _img()
    _rect(im, 0, 0, 15, 15, (36, 38, 44))
    for y in range(0, 16, 3):                # rubber belt ridges
        _rect(im, 0, y, 15, y, (70, 74, 82))
    _rect(im, 0, 0, 0, 15, (120, 126, 136))
    _rect(im, 15, 0, 15, 15, (120, 126, 136))
    return im


def _ifc_side():
    im = _plate("ifc_side", CASE, CASE_LIGHT, CASE_DARK)
    _rect(im, 2, 5, 13, 10, CASE_DARK)
    for i in range(3):                       # double arrow: green in, amber out
        _put(im, 3 + i, 7 - i, GREEN); _put(im, 3 + i, 8 + i, GREEN)
        _put(im, 12 - i, 7 - i, AMBER); _put(im, 12 - i, 8 + i, AMBER)
    _rect(im, 3, 7, 7, 8, GREEN)
    _rect(im, 8, 7, 12, 8, AMBER)
    return im


def _ifc_top():
    im = _plate("ifc_top", CASE, CASE_LIGHT, CASE_DARK)
    _rect(im, 3, 3, 12, 12, CASE_DARK)
    _rect(im, 4, 4, 11, 11, (20, 22, 26))
    for x in range(4, 12, 2):
        _rect(im, x, 4, x, 11, (60, 64, 72))
    return im


def _cable():
    im = _img()
    _rect(im, 0, 0, 15, 15, (70, 76, 86))
    for i in range(16):
        c = TEAL if (i // 2) % 2 == 0 else TEAL_DARK
        _rect(im, i, 7, i, 8, c)
        im.putpixel((i, 6), (96, 104, 116, 255))
        im.putpixel((i, 9), (50, 54, 62, 255))
    _noise(im, "storage_cable", 4)
    return im


def _cell(big):
    im = _img()
    body, light, dark = ((150, 158, 170), (196, 204, 214), (92, 100, 112))
    _rect(im, 3, 1, 12, 14, OUTLINE)
    _rect(im, 4, 2, 11, 13, body)
    _rect(im, 4, 2, 11, 2, light)
    _rect(im, 4, 2, 4, 13, light)
    _rect(im, 11, 3, 11, 13, dark)
    _rect(im, 5, 13, 11, 13, dark)
    label = (216, 160, 40) if big else TEAL
    _rect(im, 5, 4, 10, 7, label)
    _rect(im, 5, 4, 10, 4, tuple(min(255, v + 50) for v in label))
    _rect(im, 6, 9, 9, 11, (30, 34, 40))
    im.putpixel((7, 10), (LED_ON if big else TEAL_LIGHT) + (255,))
    im.putpixel((8, 10), (LED_ON if big else TEAL_LIGHT) + (255,))
    for x in range(5, 11, 2):
        im.putpixel((x, 14), (216, 180, 60, 255))
    return im


def textures():
    block, item = TEX / "block", TEX / "item"
    block.mkdir(parents=True, exist_ok=True)
    item.mkdir(parents=True, exist_ok=True)
    out = {
        block / f"{CABLE}.png": _cable(),
        item / "storage_cell_1k.png": _cell(False),
        item / "storage_cell_4k.png": _cell(True),
        block / "storage_controller_side.png": _ctl_side(),
        block / "storage_controller_top.png": _ctl_top(),
        block / "storage_controller_post.png": _ctl_post(),
        block / "storage_controller_base.png": _ctl_base(),
        block / "storage_controller_core_side.png": _ctl_core_side(),
        block / "storage_drive_bezel.png": _drv_bezel(),
        block / "storage_drive_side.png": _drv_side(),
        block / "storage_drive_top.png": _drv_top(),
        block / "storage_drive_cart_1k.png": _drv_cart(False),
        block / "storage_drive_cart_4k.png": _drv_cart(True),
        block / "storage_terminal_cabinet.png": _term_cabinet(),
        block / "storage_terminal_side.png": _term_side(),
        block / "storage_terminal_shell.png": _term_shell(),
        block / "storage_terminal_keys.png": _term_keys(),
        block / "storage_interface_collar.png": _ifc_collar(),
        block / "storage_interface_belt.png": _ifc_belt(),
        block / "storage_interface_side.png": _ifc_side(),
        block / "storage_interface_top.png": _ifc_top(),
    }
    for on in (False, True):
        s = "_on" if on else ""
        out[block / f"storage_controller_rack{s}.png"] = _ctl_rack(on)
        out[block / f"storage_controller_core{s}.png"] = _ctl_core(on)
        out[block / f"storage_drive_bays{s}.png"] = _drv_bays(on)
        out[block / f"storage_terminal_screen{s}.png"] = _term_screen(on)
        out[block / f"storage_interface_mouth{s}.png"] = _ifc_mouth(on)
    stale = [block / f"storage_casing_{k}.png" for k in ("side", "top")] + [
        block / f"{d}_front{s}.png" for d in DEVICES for s in ("", "_on")]
    for path in stale:
        if path.exists():
            path.unlink()
    for path, im in out.items():
        im.save(path)
    print(f"wrote {len(out)} storage textures")


if __name__ == "__main__":
    if "--textures" in sys.argv:
        textures()
    else:
        print(__doc__)

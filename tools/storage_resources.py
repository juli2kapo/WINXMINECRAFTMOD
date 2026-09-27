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

    side, top = f"{MOD}:block/storage_casing_side", f"{MOD}:block/storage_casing_top"
    for dev in DEVICES:
        for suffix in ("", "_on"):
            block_model(dev + suffix, {"parent": "minecraft:block/orientable", "textures": {
                "front": f"{MOD}:block/{dev}_front{suffix}", "side": side, "top": top, "particle": side}})
        variants = {}
        for facing, rot in FACINGS.items():
            for online in ("false", "true"):
                v = {"model": f"{MOD}:block/{dev}" + ("_on" if online == "true" else "")}
                if rot:
                    v["y"] = rot
                variants[f"facing={facing},online={online}"] = v
        write(ASSETS / "blockstates" / f"{dev}.json", {"variants": variants})
        item_def(dev, f"block/{dev}")

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


def _casing(name):
    """Bevelled steel plate with a teal trim line: the shared side of every storage device."""
    im = _img()
    _rect(im, 0, 0, 15, 15, CASE)
    _rect(im, 0, 0, 15, 0, CASE_LIGHT)
    _rect(im, 0, 0, 0, 15, CASE_LIGHT)
    _rect(im, 0, 15, 15, 15, CASE_DARK)
    _rect(im, 15, 0, 15, 15, CASE_DARK)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel((x, y), CASE_LIGHT + (255,))
    _noise(im, name)
    return im


def _side():
    im = _casing("storage_casing_side")
    _rect(im, 1, 7, 14, 8, TEAL_DARK)
    _rect(im, 1, 7, 14, 7, TEAL)
    return im


def _top():
    im = _casing("storage_casing_top")
    _rect(im, 4, 4, 11, 11, CASE_DARK)
    _rect(im, 5, 5, 10, 10, TEAL_DARK)
    _rect(im, 6, 6, 9, 9, TEAL)
    return im


def _frame(name):
    im = _casing(name)
    _rect(im, 2, 2, 13, 13, OUTLINE)
    return im


def _controller(on):
    im = _frame("storage_controller_front")
    _rect(im, 3, 3, 12, 12, TEAL_DARK if not on else TEAL)
    _rect(im, 5, 5, 10, 10, SCREEN_OFF if not on else TEAL_LIGHT)
    _rect(im, 7, 3, 8, 12, TEAL_DARK)
    _rect(im, 3, 7, 12, 8, TEAL_DARK)
    _rect(im, 6, 6, 9, 9, (200, 250, 250) if on else (40, 60, 66))
    return im


def _drive(on):
    im = _frame("storage_drive_front")
    for i in range(4):
        y = 3 + i * 3
        _rect(im, 3, y, 12, y + 1, CASE_DARK)
        _rect(im, 4, y, 10, y, (24, 26, 30))
        im.putpixel((12, y), (LED_ON if on else LED_OFF) + (255,))
    return im


def _terminal(on):
    im = _frame("storage_terminal_front")
    _rect(im, 3, 3, 12, 10, SCREEN_OFF)
    if on:
        rnd = random.Random("terminal")
        for y in (4, 6, 8):
            for x in range(4, 12, 2):
                if rnd.random() < 0.8:
                    im.putpixel((x, y), TEAL_LIGHT + (255,))
                    im.putpixel((x, y + 1), TEAL + (255,))
    _rect(im, 3, 12, 12, 12, CASE_DARK)
    for x in (4, 6, 8, 10):
        im.putpixel((x, 12), CASE_LIGHT + (255,))
    return im


def _interface(on):
    im = _frame("storage_interface_front")
    _rect(im, 3, 3, 12, 12, CASE_DARK)
    ring = TEAL_LIGHT if on else TEAL_DARK
    for x in range(4, 12):
        for y in range(4, 12):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if 2.6 <= d <= 4.0:
                im.putpixel((x, y), ring + (255,))
            elif d < 2.6:
                im.putpixel((x, y), (24, 26, 30, 255))
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        im.putpixel((x, y), TEAL + (255,))
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
        block / "storage_casing_side.png": _side(),
        block / "storage_casing_top.png": _top(),
        block / f"{CABLE}.png": _cable(),
        item / "storage_cell_1k.png": _cell(False),
        item / "storage_cell_4k.png": _cell(True),
    }
    for on in (False, True):
        s = "_on" if on else ""
        out[block / f"storage_controller_front{s}.png"] = _controller(on)
        out[block / f"storage_drive_front{s}.png"] = _drive(on)
        out[block / f"storage_terminal_front{s}.png"] = _terminal(on)
        out[block / f"storage_interface_front{s}.png"] = _interface(on)
    for path, im in out.items():
        im.save(path)
    print(f"wrote {len(out)} storage textures")


if __name__ == "__main__":
    if "--textures" in sys.argv:
        textures()
    else:
        print(__doc__)

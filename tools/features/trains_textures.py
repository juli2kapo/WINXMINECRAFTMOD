#!/usr/bin/env python3
"""Textures of the trains (tools/features/trains.py): 16x16 block textures for the rolling stock's
part models and the Train Station (textures/block/train_*.png), and the item icons
(textures/item/<id>.png for the locomotives, wagons and the Coupler).

Run:  python3 tools/features/trains_textures.py          (redraw all)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily). Every texture gets its own RNG seeded
from its name, so reruns are identical.
"""
import importlib.util
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/factoryascent/textures"


def _load(name):
    spec = importlib.util.spec_from_file_location(f"trains_tex_dep_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_S = _load("ships_textures")
Canvas, rgb, mix, shade, outline = _S.Canvas, _S.rgb, _S.mix, _S.shade, _S.outline

# ============================================================ palette

GREEN, GREEN_HI, GREEN_LO = rgb("#2f5d3a"), rgb("#467a52"), rgb("#1d3a24")
BLACK, BLACK_HI = rgb("#222326"), rgb("#3a3c42")
BRASS, BRASS_HI, BRASS_LO = rgb("#c99a45"), rgb("#f0cd7a"), rgb("#7f5a22")
RED, RED_LO = rgb("#a8322a"), rgb("#6e1d18")
IRON, IRON_HI, IRON_LO = rgb("#4a4c52"), rgb("#6a6d75"), rgb("#2c2d31")
STEEL, STEEL_HI = rgb("#9aa0a8"), rgb("#d4d8de")
YELLOW, YELLOW_LO = rgb("#e8b22a"), rgb("#b07d12")
MAROON, MAROON_LO = rgb("#6e2230"), rgb("#4a1520")
CREAM = rgb("#e9dcb8")
BROWN, BROWN_LO = rgb("#7a4a2a"), rgb("#4e2e18")
GLASS = rgb("#8cc4e8", 130)


def fill_grad(c, top, bottom):
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(top, bottom, y / 15))


def rivets(c, color_hi, color_lo, rows=(1, 14), cols=range(1, 16, 4)):
    for y in rows:
        for x in cols:
            c.put(x, y, color_hi)
            c.put(x, min(15, y + 1), color_lo)


# ============================================================ steam locomotive

def green_paint():
    c = Canvas("train_green", GREEN)
    fill_grad(c, GREEN_HI, GREEN_LO)
    c.rect(0, 0, 15, 0, shade(GREEN_HI, 1.15))
    rivets(c, GREEN_HI, GREEN_LO, rows=(2, 13))
    c.noise(0.035)
    return c


def boiler():
    """Boiler cladding: green with a lining stripe (runs around the barrel)."""
    c = Canvas("train_boiler", GREEN)
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(GREEN_HI, GREEN_LO, abs(x - 7.5) / 9))
    for x in range(16):
        c.put(x, 0, BRASS_LO)
        c.put(x, 15, BRASS_LO)
    c.noise(0.03)
    return c


def black_paint():
    c = Canvas("train_black", BLACK)
    fill_grad(c, BLACK_HI, BLACK)
    rivets(c, BLACK_HI, rgb("#141416"))
    c.noise(0.06)
    return c


def smokebox():
    c = Canvas("train_smokebox", rgb("#1b1b1d"))
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(rgb("#2c2c30"), rgb("#121214"), ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 11))
    for a in range(0, 360, 45):  # door dogs
        x = 7.5 + 6.2 * math.cos(math.radians(a))
        y = 7.5 + 6.2 * math.sin(math.radians(a))
        c.put(round(x), round(y), rgb("#55555c"))
    c.put(7, 7, rgb("#8a8a90"))
    c.put(8, 8, rgb("#5a5a60"))
    c.noise(0.05)
    return c


def brass():
    c = Canvas("train_brass", BRASS)
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(BRASS_HI, BRASS_LO, (x + y) / 30))
    c.rect(0, 0, 15, 0, BRASS_HI)
    c.noise(0.04)
    return c


def red_beam():
    c = Canvas("train_red", RED)
    fill_grad(c, shade(RED, 1.15), RED_LO)
    c.rect(0, 0, 15, 0, rgb("#d24a3a"))
    rivets(c, rgb("#c8483c"), RED_LO, rows=(3, 12), cols=range(2, 16, 5))
    c.noise(0.04)
    return c


def cab_window():
    c = Canvas("train_cab_window", GREEN)
    fill_grad(c, GREEN_HI, GREEN_LO)
    for y in range(2, 9):
        for x in range(3, 13):
            c.put(x, y, mix(rgb("#bfe0f4"), rgb("#4e7a96"), (y - 2) / 7))
    c.rect(2, 1, 13, 1, BRASS)
    c.rect(2, 9, 13, 9, BRASS)
    c.rect(2, 1, 2, 9, BRASS)
    c.rect(13, 1, 13, 9, BRASS)
    c.put(4, 3, rgb("#ffffff"))
    c.put(5, 3, rgb("#e4f4ff"))
    c.noise(0.03)
    return c


def roof():
    c = Canvas("train_roof", rgb("#3c3e44"))
    for y in range(16):
        tone = 1.0 if y % 4 else 0.82
        for x in range(16):
            c.put(x, y, shade(rgb("#45474e"), tone))
    c.noise(0.05)
    return c


def coal():
    c = Canvas("train_coal", rgb("#1a1a1c"))
    rng = c.rng
    for _ in range(26):
        x, y = rng.randrange(16), rng.randrange(16)
        r = rng.choice((1, 1, 2))
        col = rng.choice((rgb("#2a2a2e"), rgb("#333338"), rgb("#121214")))
        for dy in range(r):
            for dx in range(r):
                c.put(x + dx, y + dy, col)
        c.put(x, y, rgb("#55565e"))
    return c


def wheel(name, spokes, tire, rim, hub, crank):
    """A round wheel seen side-on (transparent corners): tyre, rim, spokes or disc, hub, crank boss."""
    c = Canvas(name)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 7.9:
                continue
            if d > 6.6:
                c.put(x, y, tire)
            elif d > 5.6:
                c.put(x, y, rim)
            elif d < 1.8:
                c.put(x, y, hub)
            elif spokes:
                a = (math.degrees(math.atan2(y - 7.5, x - 7.5)) + 360) % 360
                step = 360 / spokes
                off = min(a % step, step - a % step)  # degrees from the nearest spoke
                c.put(x, y, rim if off * d * 0.0175 < 0.6 else rgb("#141416"))
            else:
                c.put(x, y, shade(rim, 0.85 if (x + y) % 3 else 0.75))
    if crank:
        c.rect(9, 9, 11, 11, crank)
        c.put(10, 10, shade(crank, 1.3))
    return c


def wheel_big():
    return wheel("train_wheel_big", 8, rgb("#b9bdc4"), GREEN, BRASS, rgb("#30302f"))


def wheel_small():
    return wheel("train_wheel_small", 0, rgb("#a8acb2"), rgb("#2c2d31"), rgb("#6a6d75"), None)


def rod_steel():
    c = Canvas("train_rod", STEEL)
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(STEEL_HI, rgb("#6e737a"), y / 15))
    c.noise(0.03)
    return c


def lamp():
    c = Canvas("train_lamp", rgb("#fff3c4"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, mix(rgb("#ffffff"), rgb("#ffcf4a"), min(1, d / 8)))
    return c


def lamp_off():
    c = Canvas("train_lamp_off", rgb("#b8b39a"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, mix(rgb("#d8d6c8"), rgb("#7c7866"), min(1, d / 8)))
    return c


def beam():
    c = Canvas("train_beam", rgb("#fff2b0", 70))
    for y in range(16):
        for x in range(16):
            c.put(x, y, rgb("#fff4c0", max(18, 80 - y * 4)))
    return c


def glass():
    c = Canvas("train_glass", GLASS)
    for i in range(3, 9):
        c.put(i, 12 - i, rgb("#e8f6ff", 170))
    return c


# ============================================================ diesel locomotive

def diesel_paint():
    c = Canvas("train_diesel", YELLOW)
    fill_grad(c, shade(YELLOW, 1.08), YELLOW_LO)
    c.rect(0, 0, 15, 0, rgb("#f6d060"))
    rivets(c, rgb("#f4cf5a"), YELLOW_LO, rows=(2, 13), cols=range(2, 16, 6))
    c.noise(0.03)
    return c


def diesel_stripe():
    c = Canvas("train_diesel_stripe", YELLOW)
    fill_grad(c, shade(YELLOW, 1.08), YELLOW_LO)
    c.rect(0, 9, 15, 13, rgb("#c43a2a"))
    c.rect(0, 14, 15, 15, rgb("#262626"))
    for x in range(16):  # thin white pinstripe
        c.put(x, 8, rgb("#f4f0e0"))
    c.noise(0.03)
    return c


def grille():
    c = Canvas("train_grille", rgb("#3a3b40"))
    for y in range(16):
        for x in range(16):
            c.put(x, y, rgb("#55575e") if y % 3 == 0 else rgb("#24252a") if y % 3 == 2 else rgb("#3a3b40"))
    c.rect(0, 0, 0, 15, YELLOW_LO)
    c.rect(15, 0, 15, 15, YELLOW_LO)
    return c


def fan():
    c = Canvas("train_fan")
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 7.9:
                continue
            a = (math.degrees(math.atan2(y - 7.5, x - 7.5)) + 360) % 360
            blade = (a + d * 9) % 90 < 34
            c.put(x, y, rgb("#8a8e96") if blade else rgb("#1c1d20"))
            if d > 7:
                c.put(x, y, rgb("#55575e"))
            if d < 1.6:
                c.put(x, y, rgb("#c4c8ce"))
    return c


def cab_front():
    """Diesel cab front / side: yellow with a dark windscreen band."""
    c = Canvas("train_diesel_window", YELLOW)
    fill_grad(c, shade(YELLOW, 1.08), YELLOW_LO)
    for y in range(2, 9):
        for x in range(1, 15):
            c.put(x, y, mix(rgb("#a8d4ee"), rgb("#2e4a5e"), (y - 2) / 7))
    c.rect(7, 2, 8, 8, rgb("#262626"))
    c.put(3, 3, rgb("#ffffff"))
    return c


# ============================================================ coach, box car, tank, hopper

def coach():
    c = Canvas("train_coach", MAROON)
    fill_grad(c, shade(MAROON, 1.12), MAROON_LO)
    c.rect(0, 2, 15, 2, BRASS)
    c.rect(0, 13, 15, 13, BRASS)
    c.noise(0.03)
    return c


def coach_window():
    c = Canvas("train_coach_window", CREAM)
    fill_grad(c, CREAM, shade(CREAM, 0.85))
    c.rect(0, 0, 15, 0, MAROON)
    c.rect(0, 15, 15, 15, MAROON)
    c.rect(0, 0, 1, 15, MAROON)
    c.rect(14, 0, 15, 15, MAROON)
    return c


def wood():
    return _S.planks("train_wood", rgb("#9a6a3c"), rgb("#5a3c20"), rows=4)


def seat():
    c = Canvas("train_seat", rgb("#8c2a2a"))
    for y in range(16):
        for x in range(16):
            c.put(x, y, rgb("#a63636") if (x // 4 + y // 4) % 2 == 0 else rgb("#8a2a2a"))
    c.noise(0.04)
    return c


def boxcar():
    return _S.planks("train_boxcar", BROWN, BROWN_LO, horizontal=False, rows=4)


def boxcar_door():
    c = _S.planks("train_boxcar_door", shade(BROWN, 0.85), BROWN_LO, horizontal=True, rows=4)
    c.name = "train_boxcar_door"
    for i in range(16):  # Z brace
        c.put(i, 15 - i, IRON)
        c.put(i, min(15, 16 - i), IRON_LO)
    c.rect(0, 0, 15, 0, IRON)
    c.rect(0, 15, 15, 15, IRON)
    c.rect(12, 7, 13, 8, STEEL_HI)
    return c


def tank():
    c = Canvas("train_tank", rgb("#2a2c30"))
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(rgb("#4a4e56"), rgb("#1c1d21"), abs(x - 6) / 10))
    for y in (0, 8):
        for x in range(16):
            c.put(x, y, rgb("#5c616a"))
    rivets(c, rgb("#6c717a"), rgb("#16171a"), rows=(1, 9), cols=range(1, 16, 3))
    c.noise(0.03)
    return c


def hopper():
    c = Canvas("train_hopper", rgb("#6e4434"))
    rng = c.rng
    for y in range(16):
        for x in range(16):
            base = mix(rgb("#7e4e3a"), rgb("#55332a"), y / 15)
            c.put(x, y, shade(base, 1 + rng.uniform(-0.12, 0.12)))
    for x in (0, 7, 8, 15):  # ribs
        for y in range(16):
            c.put(x, y, rgb("#4a2c22") if x in (0, 8) else rgb("#9a6650"))
    for _ in range(10):  # rust streaks
        x = rng.randrange(16)
        for y in range(rng.randrange(4, 10)):
            c.put(x, y + 3, rgb("#9a5a2e"))
    return c


def ore():
    c = Canvas("train_ore", rgb("#6c6c6c"))
    rng = c.rng
    for y in range(16):
        for x in range(16):
            c.put(x, y, shade(rgb("#727272"), 1 + rng.uniform(-0.2, 0.2)))
    for _ in range(9):
        x, y = rng.randrange(15), rng.randrange(15)
        col = rng.choice((rgb("#d8af93"), rgb("#e6c35c"), rgb("#3a3a3a"), rgb("#a5662e")))
        c.put(x, y, col)
        c.put(x + 1, y, shade(col, 0.8))
        c.put(x, y + 1, shade(col, 0.7))
    return c


def iron():
    c = Canvas("train_iron", IRON)
    fill_grad(c, IRON_HI, IRON_LO)
    c.noise(0.06)
    return c


def bogie():
    c = Canvas("train_bogie", rgb("#2e2f33"))
    fill_grad(c, rgb("#45474d"), rgb("#1e1f22"))
    for x in (3, 12):  # spring coils
        for y in range(5, 12):
            c.put(x, y, rgb("#8a8e96") if y % 2 else rgb("#55575e"))
            c.put(x + 1, y, rgb("#55575e") if y % 2 else rgb("#8a8e96"))
    c.rect(6, 6, 9, 9, rgb("#55575e"))  # axle box
    c.put(7, 7, rgb("#9aa0a8"))
    return c


def steel():
    c = Canvas("train_steel", STEEL)
    fill_grad(c, STEEL_HI, rgb("#7e848c"))
    c.noise(0.03)
    return c


# ============================================================ station

def station_top():
    c = Canvas("train_station_top", rgb("#9a9a96"))
    rng = c.rng
    for y in range(16):
        for x in range(16):
            c.put(x, y, shade(rgb("#a4a49e"), 1 + rng.uniform(-0.06, 0.06)))
    for i in range(16):  # paving joints
        c.put(i, 7, rgb("#7c7c78"))
        c.put(i, 15, rgb("#7c7c78"))
        c.put(7 if i < 8 else 3, i, rgb("#7c7c78"))
    c.rect(0, 0, 15, 2, rgb("#e8c22a"))  # the yellow line along the platform edge (north = track side)
    c.rect(0, 3, 15, 3, rgb("#c8c8c2"))
    return c


def station_side():
    c = Canvas("train_station_side", rgb("#8a8780"))
    rng = c.rng
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            joint = y % 4 == 3 or (x + off) % 8 == 7
            c.put(x, y, rgb("#6c6a64") if joint else shade(rgb("#9c9890"), 1 + rng.uniform(-0.07, 0.07)))
    c.rect(0, 0, 15, 1, rgb("#c8c8c2"))  # coping stone
    return c


def station_post():
    c = Canvas("train_station_post", rgb("#2c3a2c"))
    fill_grad(c, rgb("#3e5440"), rgb("#1e2a20"))
    return c


def signal(name, color, lit):
    c = Canvas(name, rgb("#1a1a1a"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.5:
                c.put(x, y, mix(rgb("#ffffff"), rgb(color), min(1, d / 5)) if lit else shade(rgb(color), 0.35))
    return c


# ============================================================ items

def icon_steam():
    c = Canvas("steam_locomotive")
    g, gl, b, br, r = GREEN_HI, GREEN, rgb("#1b1b1d"), BRASS, RED
    c.rect(1, 3, 5, 9, gl)          # cab
    c.rect(1, 3, 5, 3, rgb("#3c3e44"))  # roof
    c.rect(2, 5, 4, 6, rgb("#bfe0f4"))  # window
    c.rect(6, 6, 12, 9, g)          # boiler
    c.rect(12, 6, 14, 9, b)         # smokebox
    c.rect(12, 3, 13, 5, b)         # chimney
    c.rect(9, 5, 10, 5, br)         # dome
    c.rect(0, 10, 15, 10, r)        # frame / buffer beam
    c.put(15, 9, rgb("#ffe080"))    # lamp
    for x in (3, 7, 11):            # wheels
        c.rect(x - 1, 11, x + 1, 13, rgb("#b9bdc4"))
        c.put(x, 12, br)
    c.rect(2, 12, 12, 12, rgb("#d4d8de"))  # coupling rod
    c.put(4, 1, rgb("#c8c8c8"))
    c.put(13, 1, rgb("#bbbbbb"))
    c.put(12, 2, rgb("#a0a0a0"))
    outline(c)
    return c


def icon_diesel():
    c = Canvas("diesel_locomotive")
    y_, yl = YELLOW, YELLOW_LO
    c.rect(1, 5, 11, 10, y_)        # long hood
    c.rect(11, 3, 14, 10, y_)       # cab
    c.rect(12, 4, 14, 5, rgb("#4e7a96"))  # windscreen
    c.rect(1, 9, 14, 9, rgb("#c43a2a"))   # stripe
    c.rect(3, 6, 8, 7, rgb("#3a3b40"))    # grille
    c.rect(0, 11, 15, 11, rgb("#2e2f33"))  # frame
    for x in (2, 5, 10, 13):
        c.rect(x, 12, x + 1, 13, rgb("#a8acb2"))
    c.put(15, 7, rgb("#fff3c4"))    # headlight
    c.put(4, 4, rgb("#55575e"))     # exhaust
    outline(c)
    return c


def icon_coach():
    c = Canvas("passenger_car")
    c.rect(0, 4, 15, 10, MAROON)
    c.rect(0, 3, 15, 3, rgb("#45474e"))
    for x in (1, 5, 9, 13):
        c.rect(x, 5, x + 1, 7, rgb("#bfe0f4"))
    c.rect(0, 8, 15, 8, BRASS)
    c.rect(0, 11, 15, 11, rgb("#2e2f33"))
    for x in (2, 4, 11, 13):
        c.rect(x, 12, x, 13, rgb("#a8acb2"))
    outline(c)
    return c


def icon_boxcar():
    c = Canvas("cargo_wagon")
    c.rect(0, 2, 15, 10, BROWN)
    for x in (0, 4, 11, 15):
        c.rect(x, 2, x, 10, BROWN_LO)
    c.rect(6, 3, 9, 10, shade(BROWN, 0.8))  # door
    c.rect(6, 3, 9, 3, IRON)
    c.rect(0, 1, 15, 1, rgb("#45474e"))
    c.rect(0, 11, 15, 11, rgb("#2e2f33"))
    for x in (2, 4, 11, 13):
        c.rect(x, 12, x, 13, rgb("#a8acb2"))
    outline(c)
    return c


def icon_tank():
    c = Canvas("tank_wagon")
    c.rect(1, 4, 14, 9, rgb("#2e3036"))
    c.rect(2, 3, 13, 3, rgb("#4a4e56"))
    c.rect(2, 10, 13, 10, rgb("#1c1d21"))
    c.rect(1, 6, 14, 6, rgb("#5c616a"))
    c.rect(7, 1, 8, 2, rgb("#4a4e56"))  # dome
    c.rect(0, 11, 15, 11, rgb("#2e2f33"))
    for x in (2, 4, 11, 13):
        c.rect(x, 12, x, 13, rgb("#a8acb2"))
    outline(c)
    return c


def icon_hopper():
    c = Canvas("hopper_wagon")
    for y in range(3, 11):
        inset = max(0, y - 7)
        c.rect(1 + inset, y, 14 - inset, y, rgb("#7e4e3a"))
    for x in (4, 8, 11):
        c.rect(x, 3, x, 8, rgb("#55332a"))
    c.rect(2, 2, 13, 2, rgb("#8a8a8a"))  # ore heap
    c.rect(4, 1, 10, 1, rgb("#a5662e"))
    c.rect(0, 11, 15, 11, rgb("#2e2f33"))
    for x in (2, 4, 11, 13):
        c.rect(x, 12, x, 13, rgb("#a8acb2"))
    outline(c)
    return c


def icon_coupler():
    c = Canvas("coupler")
    steel_c, dark = rgb("#b0b6be"), rgb("#55575e")
    for i in range(2, 10):  # drawbar (diagonal)
        c.put(i, 13 - i, steel_c)
        c.put(i + 1, 13 - i, dark)
    c.rect(9, 1, 14, 6, dark)   # knuckle head
    c.rect(10, 2, 13, 5, steel_c)
    c.rect(11, 3, 12, 4, dark)
    c.rect(1, 12, 3, 14, BRASS)  # handle
    outline(c)
    return c


BLOCK = [green_paint, boiler, black_paint, smokebox, brass, red_beam, cab_window, roof, coal, wheel_big, wheel_small,
         rod_steel, lamp, lamp_off, beam, glass, diesel_paint, diesel_stripe, grille, fan, cab_front, coach, coach_window,
         wood, seat, boxcar, boxcar_door, tank, hopper, ore, iron, bogie, steel, station_top, station_side, station_post]
SIGNALS = [("train_signal_green", "#3cff5a", True), ("train_signal_red", "#ff3a2a", True),
           ("train_signal_green_off", "#3cff5a", False), ("train_signal_red_off", "#ff3a2a", False)]
ITEMS = [icon_steam, icon_diesel, icon_coach, icon_boxcar, icon_tank, icon_hopper, icon_coupler]


def all_textures():
    out = []
    for fn in BLOCK:
        c = fn()
        out.append((TEX / "block" / f"{c.name}.png", c))
    for name, color, lit in SIGNALS:
        out.append((TEX / "block" / f"{name}.png", signal(name, color, lit)))
    for fn in ITEMS:
        c = fn()
        out.append((TEX / "item" / f"{c.name}.png", c))
    return out


def draw(only_missing):
    textures = all_textures()
    n = 0
    for path, c in textures:
        if only_missing and path.exists():
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        c.image().save(path)
        n += 1
    return n


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    print(f"drew {draw(only_missing=False)} train textures")

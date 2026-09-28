#!/usr/bin/env python3
"""Textures for the 3D satellites and the launch vehicle on the Launch Pad (16x16, block atlas, so
both the item models and the pad's renderer can use them), plus the satellites' flat GUI icons.

Satellites: gold / silver multi-layer insulation foil, armour plate, white radiator, solar cells,
steel, black instrument housing, a camera lens, the dish's white face, red paint.
Launch vehicle: white hull (plain and with the flag stripe), black carbon interstage, engine bell
(outside, and its throat), grid fin (cut-out lattice), fairing (outside, and the quilted acoustic
blanket inside), payload adapter, the gunmetal anti-satellite livery, the kill vehicle's seeker
and the exhaust plume (translucent).

Run:  python3 tools/features/satellite_textures.py      (redraws all of them)
gen_resources.py only draws the missing ones (see orbital_textures.py for the same pattern).
"""
import importlib.util
import math
import random
from pathlib import Path


def _load(name):
    spec = importlib.util.spec_from_file_location(f"sattex_dep_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


O = _load("orbital_textures")
Canvas, rgb, mix, shade = O.Canvas, O.rgb, O.mix, O.shade
STEEL, DARK, GOLD, RED, WHITE, CYAN, BLUE_PV, FLAME = O.STEEL, O.DARK, O.GOLD, O.RED, O.WHITE, O.CYAN, O.BLUE_PV, O.FLAME
HAZ_Y, HAZ_K = O.HAZ_Y, O.HAZ_K
TEX = O.TEX

SILVER = [rgb(h) for h in ("#FAFBFD", "#D6DAE0", "#AEB4BE", "#7C838F")]
GOLDF = [rgb(h) for h in ("#FFF0A0", "#EBC350", "#C9982E", "#8E6418")]
GUN = [rgb(h) for h in ("#7A808C", "#5A606C", "#434854", "#2E323A", "#1C1F25")]
CELL = [rgb(h) for h in ("#5C8FE0", "#2F5BB0", "#1F3F86", "#142A5C")]


def foil(pal, seed):
    """Crinkled multi-layer insulation: a base tone crossed by soft creases and bright glints."""
    rng = random.Random(seed)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = math.sin((x * 0.9 + y * 0.35) + rng.random() * 0.6) + math.sin(x * 0.3 - y * 1.1)
            c.put(x, y, pal[1] if v > 0.6 else pal[2] if v > -0.9 else pal[3] if v < -1.5 else pal[2])
    for _ in range(7):  # glints
        x, y = rng.randrange(16), rng.randrange(16)
        c.put(x, y, pal[0])
        c.put(x + 1, y, pal[1])
    for _ in range(3):  # creases
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randrange(3, 6)):
            c.put(x + i, y + i // 2, pal[3])
    return c


def armor():
    """Gunmetal plates with bevels and rivets, and a red stripe across the middle."""
    rng = random.Random(41)
    c = Canvas()
    O.noisy(c, GUN, rng, base=1, p=0.1)
    for x0, y0 in ((0, 0), (8, 0), (0, 8), (8, 8)):
        c.bevel(x0, y0, x0 + 7, y0 + 7, GUN[0], GUN[3])
    for x in range(16):
        c.put(x, 6, RED[1])
        c.put(x, 7, RED[2])
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.put(x, y, GUN[0])
        c.put(x + 1, y + 1, GUN[4])
    return c


def radiator():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, WHITE[1] if y % 3 else WHITE[2])
    c.bevel(0, 0, 15, 15, WHITE[0], WHITE[3])
    return c


def cells():
    """Solar cells: 3x3 px blue cells with a diagonal sheen, silver bus lines between them."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                c.put(x, y, SILVER[2] if (x % 4 == 0 and y % 4 == 0) else CELL[3])
            else:
                lx, ly = x % 4, y % 4
                c.put(x, y, CELL[0] if (lx, ly) == (1, 1) else CELL[1] if lx + ly <= 3 else CELL[2])
    return c


def panel_back():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, SILVER[2] if (x + y) % 8 == 0 or (x - y) % 8 == 0 else SILVER[1])
    return c


def steel():
    rng = random.Random(42)
    c = Canvas()
    O.noisy(c, STEEL, rng, base=1, p=0.2)
    return c


def dark():
    rng = random.Random(43)
    c = Canvas()
    O.noisy(c, GUN, rng, base=3, p=0.15)
    for x in range(16):
        c.put(x, 0, GUN[2])
        c.put(x, 15, GUN[4])
    return c


def lens():
    """A camera lens seen head on: black barrel ring, deep blue glass, a cyan glint."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = math.hypot(dx, dy)
            if d > 7:
                col = GUN[4]
            elif d > 5.6:
                col = GUN[2] if dx + dy < 0 else GUN[3]
            elif d > 5:
                col = GOLDF[2]
            else:
                col = mix(rgb("#10204A"), rgb("#050A1A"), min(1, d / 5))
                if math.hypot(dx + 2, dy + 2) < 1.4:
                    col = CYAN[0]
                elif math.hypot(dx + 2, dy + 2) < 2.4:
                    col = CYAN[3]
                elif math.hypot(dx - 2, dy - 2) < 0.8:
                    col = rgb("#6C3AA8")
            c.put(x, y, col)
    return c


def dish():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, WHITE[1] if x % 8 else WHITE[2])
    for x in range(16):
        c.put(x, 15, WHITE[3])
    return c


def red():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, RED[1] if y < 12 else RED[2])
    for x in range(16):
        c.put(x, 0, RED[0])
    return c


# ============================================================ launch vehicle

def hull(pal=WHITE, seam=WHITE[2], seed=51):
    rng = random.Random(seed)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, pal[1] if rng.random() > 0.08 else pal[0])
    for x in range(16):
        c.put(x, 15, seam)
    for x in (3, 11):
        c.put(x, 7, seam)
    return c


def hull_flag():
    """The hull with the flag stripe (red, white, blue) down one side."""
    c = hull()
    for y in range(16):
        for x, col in ((5, RED[1]), (6, RED[2]), (9, BLUE_PV[1]), (10, BLUE_PV[2])):
            c.put(x, y, col)
    return c


def hull_asat():
    c = hull(GUN, GUN[3], 52)
    for y in range(16):
        c.put(7, y, RED[2])
        c.put(8, y, RED[3])
    return c


def carbon():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, rgb("#26282E") if (x // 2 + y // 2) % 2 else rgb("#1A1C21"))
    for x in range(16):
        c.put(x, 0, WHITE[2])
        c.put(x, 15, rgb("#101114"))
    return c


def bell():
    """Engine bell outside: dark steel warming to bronze toward the lip, with cooling-tube lines."""
    c = Canvas()
    for y in range(16):
        base = mix(GUN[2], rgb("#8A5A2E"), y / 15)
        for x in range(16):
            c.put(x, y, shade(base, -0.25) if x % 3 == 0 else base)
    for x in range(16):
        c.put(x, 15, rgb("#C08040"))
    return c


def bell_inner():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            c.put(x, y, FLAME[1] if d < 2 else FLAME[3] if d < 3.5 else rgb("#3A1A0C") if d < 6 else GUN[4])
    return c


def gridfin():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0 or x == 15 or y == 15:
                c.put(x, y, GUN[1] if (x + y) % 2 else GUN[2])
    return c


def fin():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, GUN[3] if x < 2 else WHITE[1] if x < 12 else WHITE[2])
    return c


def fairing():
    c = hull(seed=53)
    for y in range(16):
        c.put(0, y, WHITE[3])
    return c


def fairing_inner():
    """The acoustic blanket inside the fairing: quilted silver pads."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            lx, ly = x % 4, y % 4
            c.put(x, y, SILVER[3] if lx == 0 or ly == 0 else SILVER[1] if lx + ly < 4 else SILVER[2])
    return c


def adapter():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, STEEL[2] if (x + y) % 4 else STEEL[3])
    for x in range(16):
        c.put(x, 0, STEEL[0])
    return c


def seeker():
    """The kill vehicle's nose: red with a black seeker window."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, RED[1] if x < 8 else RED[2])
    for y in range(0, 5):
        for x in range(6, 10):
            c.put(x, y, rgb("#101218"))
    c.put(6, 1, CYAN[2])
    return c


def kv_body():
    c = hull(GUN, GUN[3], 54)
    for x in range(16):
        c.put(x, 3, HAZ_Y if (x // 2) % 2 == 0 else HAZ_K)
        c.put(x, 4, HAZ_K if (x // 2) % 2 == 0 else HAZ_Y)
    return c


def plume():
    """Exhaust plume: white-hot at the nozzle, yellow then orange, fading out (translucent)."""
    from PIL import Image
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        t = y / 15
        col = mix(mix(FLAME[0], FLAME[1], min(1, t * 2)), FLAME[2], max(0, t * 2 - 1))
        for x in range(16):
            flick = 0.85 + 0.15 * math.sin(x * 1.7 + y * 0.9)
            a = round(235 * (1 - t) ** 0.8 * flick + 20)
            img.putpixel((x, y), col[:3] + (a,))
    return img


# ============================================================ flat GUI icons

def icon(kind):
    """The satellite seen from three-quarters above: wings, bus and the type's instrument."""
    c = Canvas()
    bus_pal = {"survey": GOLDF, "uplink": SILVER, "guardian": GUN}[kind]
    # wings (slanted parallelograms)
    for x0, x1 in ((0, 4), (11, 15)):
        for y in range(6, 11):
            for x in range(x0, x1 + 1):
                edge = y in (6, 10) or x in (x0, x1)
                c.put(x, y, CELL[3] if edge else CELL[0] if (x + y) % 3 == 0 else CELL[1])
        c.put(x0 + 1, 7, CELL[0])
    for x in (5, 10):  # booms
        c.put(x, 8, STEEL[2])
    # bus
    for y in range(5, 12):
        for x in range(6, 10):
            c.put(x, y, bus_pal[1] if x < 8 else bus_pal[2])
    for x in range(6, 10):
        c.put(x, 5, bus_pal[0])
        c.put(x, 11, bus_pal[3])
    if kind == "survey":
        c.put(6, 7, GOLDF[0])
        for y in (12, 13):  # telescope below
            c.put(7, y, GUN[3])
            c.put(8, y, GUN[3])
        c.put(6, 13, GUN[4])
        c.put(9, 13, GUN[4])
        c.put(7, 14, CYAN[1])
        c.put(8, 14, rgb("#10204A"))
        c.put(8, 3, STEEL[1])
        c.put(8, 4, STEEL[2])
        c.put(8, 2, RED[1])
    elif kind == "uplink":
        for x in range(4, 12):  # dish on top
            c.put(x, 2, WHITE[0] if 5 <= x <= 10 else WHITE[2])
        for x in range(5, 11):
            c.put(x, 3, WHITE[2])
        c.put(4, 1, WHITE[1])
        c.put(11, 1, WHITE[1])
        for x in range(6, 10):
            c.put(x, 4, STEEL[2])
        c.put(8, 1, STEEL[1])
        c.put(8, 0, CYAN[1])
        c.put(7, 7, WHITE[0])
        for y in (12, 13):  # omni antenna below
            c.put(8, y, STEEL[1])
    else:
        for x in range(6, 10):  # red stripe on the armour
            c.put(x, 8, RED[1])
        for x in (6, 7, 8, 9):  # turret
            c.put(x, 4, GUN[2])
        c.put(7, 3, GUN[1])
        c.put(8, 3, GUN[2])
        for i in range(3):  # barrels up and right
            c.put(9 + i, 2 - i, GUN[0])
            c.put(9 + i, 3 - i, GUN[3])
        c.put(12, 0, RED[1])
        for x, y in ((6, 12), (7, 12), (8, 12), (9, 12)):  # interceptor pod
            c.put(x, y, GUN[3])
        c.put(6, 13, RED[1])
        c.put(8, 13, RED[1])
        c.put(7, 13, GUN[4])
        c.put(9, 13, GUN[4])
    c.outline()
    return c


BLOCKS = {
    "sat_foil_gold": lambda: foil(GOLDF, 61), "sat_foil_silver": lambda: foil(SILVER, 62), "sat_armor": armor,
    "sat_radiator": radiator, "sat_cells": cells, "sat_panel_back": panel_back, "sat_steel": steel, "sat_dark": dark,
    "sat_lens": lens, "sat_dish": dish, "sat_red": red,
    "lv_hull": hull, "lv_hull_flag": hull_flag, "lv_hull_asat": hull_asat, "lv_carbon": carbon, "lv_bell": bell,
    "lv_bell_inner": bell_inner, "lv_gridfin": gridfin, "lv_fin": fin, "lv_fairing": fairing,
    "lv_fairing_inner": fairing_inner, "lv_adapter": adapter, "lv_seeker": seeker, "lv_kv_body": kv_body,
    "lv_plume": plume,
}
ITEMS = {f"{k}_satellite_icon": (lambda k=k: icon(k)) for k in ("survey", "uplink", "guardian")}


def paths():
    for name, fn in BLOCKS.items():
        yield TEX / "block" / f"{name}.png", fn
    for name, fn in ITEMS.items():
        yield TEX / "item" / f"{name}.png", fn


def draw(only_missing):
    for path, fn in paths():
        if only_missing and path.exists():
            continue
        img = fn()
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    draw(only_missing=False)
    print(f"wrote {len(BLOCKS) + len(ITEMS)} satellite and launch vehicle textures")

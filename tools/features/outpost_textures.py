#!/usr/bin/env python3
"""Textures of planetary outposts (Java: outpost/, recipes in tools/features/outpost.py):

  block/  lunar_glass; per planet rock (moon_rock, mars_rock, io_rock): polished_<rock>, <rock>_bricks,
          <rock>_tiles (the slabs, stairs and walls reuse the bricks); fuel_synthesizer_* (front, front_on,
          side, top, tank_h, tank_o); ascent_* (hull, booster, window, nozzle); distress_beacon_* (body,
          lens, leg)
  item/   sulfur, martian_steel_ingot, sulfuric_acid_cell, quantum_circuit, hydrolox_fuel_cell, oxygen_cell

Run:  python3 tools/features/outpost_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily).
"""
import importlib.util
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def _load(name):
    spec = importlib.util.spec_from_file_location(f"{name}_for_outpost", Path(__file__).with_name(f"{name}.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


P = _load("planets_textures")
O = P.O
rgb, mix, shade, Canvas, noisy, rivet = O.rgb, O.mix, O.shade, O.Canvas, O.noisy, O.rivet
STEEL, DARK, OUTLINE = O.STEEL, O.DARK, O.OUTLINE

ROCKS = {"moon_rock": P.MOONROCK, "mars_rock": P.MARSROCK, "io_rock": P.BASALT}
# a planet's accent: Helium-3 cyan on the Moon, hematite on Mars, sulfur on Io
ACCENT = {"moon_rock": P.HE3, "mars_rock": P.HEMATITE, "io_rock": P.SULFUR}
MSTEEL = [rgb(h) for h in ("#F0C8B8", "#C8846C", "#9A5A48", "#6C3A30", "#42221C")]
SULF = P.SULFUR
H2 = [rgb(h) for h in ("#D8ECFF", "#8CC0F8", "#4A88D8", "#2A5AA0")]
OX = [rgb(h) for h in ("#FFFFFF", "#E4F4FF", "#B0D8F0", "#7AA8C8")]
ACID = [rgb(h) for h in ("#F8FFB0", "#D8F050", "#A8C828", "#6A8418")]
ORANGE = [rgb(h) for h in ("#FFD8A0", "#F0A040", "#C06A18", "#7A400C")]
REDL = [rgb(h) for h in ("#FFE0D8", "#FF6A50", "#D02818", "#7A1008")]


def seed(name):
    return random.Random(sum(ord(ch) * (i + 7) for i, ch in enumerate(name)))


# ============================================================ decorative stone

def polished(rock):
    """A smooth slab of the rock with a bevelled rim and a few flecks of the planet's accent."""
    pal, acc, rng = ROCKS[rock], ACCENT[rock], seed("polished" + rock)
    c = Canvas()
    noisy(c, pal, rng, base=1, p=0.12)
    c.bevel(0, 0, 15, 15, pal[0], pal[3])
    c.bevel(1, 1, 14, 14, shade(pal[1], 0.15), pal[2])
    for _ in range(4):
        c.put(rng.randrange(3, 13), rng.randrange(3, 13), acc[1])
    return c


def bricks(rock):
    """Four courses of cut blocks with dark mortar, offset every other course."""
    pal, rng = ROCKS[rock], seed("bricks" + rock)
    c = Canvas()
    noisy(c, pal, rng, base=2, p=0.25)
    mortar = pal[4]
    for row in range(4):
        y0 = row * 4
        for x in range(16):
            c.put(x, y0 + 3, mortar)
        off = 0 if row % 2 == 0 else 4
        for x in (off, off + 8):
            for y in range(y0, y0 + 3):
                c.put(x % 16, y, mortar)
        for x in range(16):
            c.put(x, y0, pal[1] if c.get(x, y0) != mortar else mortar)
    return c


def tiles(rock):
    """A 2x2 grid of large square tiles with light rims and an accent inlay at the centre."""
    pal, acc, rng = ROCKS[rock], ACCENT[rock], seed("tiles" + rock)
    c = Canvas()
    noisy(c, pal, rng, base=1, p=0.18)
    for t0 in (0, 8):
        for u0 in (0, 8):
            c.bevel(t0, u0, t0 + 7, u0 + 7, pal[0], pal[3])
    for x in range(16):
        c.put(x, 7, pal[4])
        c.put(x, 8, pal[4])
        c.put(7, x, pal[4])
        c.put(8, x, pal[4])
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.put(x, y, acc[1])
    return c


def lunar_glass():
    """Clear glass with a faint silver tint, a slim frame and a few bright inclusions."""
    c = Canvas()
    frame = [rgb("#DCE4EC"), rgb("#AEB8C6"), rgb("#7E8898")]
    for x in range(16):
        for y in range(16):
            if x in (0, 15) or y in (0, 15):
                c.put(x, y, frame[1] if (x + y) % 2 else frame[0])
    for i in range(3, 9):
        c.put(i, 12 - i, rgb("#FFFFFF", 150))
        c.put(i + 1, 12 - i, rgb("#E8F4FF", 90))
    for x, y in ((11, 4), (4, 11), (12, 12)):
        c.put(x, y, rgb("#C8E8FF", 120))
    for y in range(1, 15):
        for x in range(1, 15):
            if c.get(x, y) is None and (x * 7 + y * 3) % 23 == 0:
                c.put(x, y, rgb("#E0E8F4", 46))
    return c


# ============================================================ fuel synthesizer

def synth_panel(rng):
    c = Canvas()
    noisy(c, DARK, rng, base=1, p=0.12)
    c.bevel(0, 0, 15, 15, DARK[0], DARK[4])
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(c, x, y, STEEL)
    return c


def synth_front(on):
    """Two sight-glasses (hydrogen blue, oxygen white) over a status strip and a cell hatch."""
    rng = seed("synth_front")
    c = synth_panel(rng)
    for x0, pal in ((3, H2), (9, OX)):
        c.rect(x0, 3, x0 + 3, 8, OUTLINE)
        level = 6 if on else 4
        for y in range(4, 8):
            for x in range(x0 + 1, x0 + 3):
                c.put(x, y, pal[1] if y >= 8 - level // 2 else shade(pal[3], -0.4))
        c.put(x0 + 1, 4, pal[0] if on else pal[3])
    c.rect(3, 10, 12, 10, OUTLINE)
    for x in range(4, 12):
        c.put(x, 10, O.GREEN[1] if on and x % 2 else (O.GREEN[3] if on else DARK[3]))
    c.rect(5, 12, 10, 14, STEEL[2])
    c.bevel(5, 12, 10, 14, STEEL[0], STEEL[4])
    c.put(7, 13, O.HAZ_Y)
    c.put(8, 13, O.HAZ_K)
    return c


def synth_side():
    rng = seed("synth_side")
    c = synth_panel(rng)
    for y in (4, 7, 10):
        for x in range(3, 13):
            c.put(x, y, DARK[3])
            c.put(x, y + 1, DARK[0])
    c.rect(6, 12, 9, 13, ORANGE[1])
    return c


def synth_top():
    rng = seed("synth_top")
    c = Canvas()
    noisy(c, STEEL, rng, base=2, p=0.12)
    c.bevel(0, 0, 15, 15, STEEL[1], STEEL[4])
    for x in range(2, 14):
        c.put(x, 7, O.HAZ_Y if (x // 2) % 2 else O.HAZ_K)
    return c


def tank(pal):
    """A pressure tank skin: vertical bands of the gas colour between steel hoops."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            band = pal[0] if x < 4 else pal[1] if x < 9 else pal[2] if x < 13 else pal[3]
            c.put(x, y, band)
    for y in (2, 13):
        for x in range(16):
            c.put(x, y, STEEL[2])
            c.put(x, y + 1, STEEL[4])
    c.put(5, 6, (255, 255, 255, 255))
    return c


# ============================================================ ascent module

def ascent_hull():
    """White capsule skin with panel seams, an orange stripe and rivets."""
    rng = seed("ascent_hull")
    c = Canvas()
    noisy(c, O.WHITE, rng, base=1, p=0.1)
    for x in range(16):
        c.put(x, 5, O.WHITE[3])
        c.put(x, 11, ORANGE[1])
        c.put(x, 12, ORANGE[2])
    for y in range(16):
        c.put(7, y, O.WHITE[2] if y not in (11, 12) else ORANGE[3])
    for x in (2, 12):
        rivet(c, x, 2, STEEL)
    return c


def ascent_booster():
    """Booster stage: dark scorched steel with hazard chevrons."""
    rng = seed("ascent_booster")
    c = Canvas()
    noisy(c, DARK, rng, base=2, p=0.25)
    for x in range(16):
        c.put(x, 0, STEEL[1])
        c.put(x, 15, DARK[4])
        if (x // 2) % 2 == 0:
            c.put(x, 3, O.HAZ_Y)
            c.put(x, 4, O.HAZ_Y)
        else:
            c.put(x, 3, O.HAZ_K)
            c.put(x, 4, O.HAZ_K)
    for _ in range(14):
        c.put(rng.randrange(16), rng.randrange(9, 15), shade(DARK[4], -0.4))
    return c


def ascent_window():
    c = Canvas()
    c.rect(0, 0, 15, 15, STEEL[3])
    c.rect(2, 2, 13, 13, rgb("#1B3550"))
    for i in range(4, 10):
        c.put(i, 13 - i, rgb("#8CC8F0"))
    c.put(4, 4, rgb("#FFFFFF"))
    c.bevel(1, 1, 14, 14, STEEL[1], STEEL[4])
    return c


def ascent_nozzle():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, OUTLINE if r < 3 else DARK[4] if r < 5 else DARK[2] if r < 7 else DARK[1])
    c.put(7, 7, ORANGE[2])
    c.put(8, 8, ORANGE[3])
    return c


# ============================================================ distress beacon

def beacon_body():
    """A red-and-white emergency box with a hazard band and an SOS light strip."""
    rng = seed("beacon_body")
    c = Canvas()
    noisy(c, REDL[1:], rng, base=1, p=0.1)
    for x in range(16):
        c.put(x, 0, O.WHITE[1])
        c.put(x, 1, O.WHITE[2])
        c.put(x, 14, O.WHITE[2])
        c.put(x, 15, O.WHITE[3])
    for x, on in zip(range(3, 13), (1, 0, 1, 0, 1, 1, 1, 0, 1, 0)):  # S O S in blinks
        c.put(x, 7, rgb("#FFF6C0") if on else REDL[3])
    c.bevel(0, 0, 15, 15, O.WHITE[0], REDL[3])
    return c


def beacon_lens():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            r = ((x - 6) ** 2 + (y - 6) ** 2) ** 0.5
            c.put(x, y, REDL[0] if r < 2.5 else REDL[1] if r < 6 else REDL[2] if r < 9 else REDL[3])
    return c


def beacon_leg():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, STEEL[1] if x < 5 else STEEL[2] if x < 11 else STEEL[3])
    for y in (4, 11):
        for x in range(16):
            c.put(x, y, O.HAZ_Y)
    return c


# ============================================================ items

def sulfur():
    """A heap of bright yellow sulfur crystals."""
    c = Canvas()
    rng = seed("sulfur")
    for y in range(6, 14):
        half = (y - 5) + 1
        for x in range(8 - half, 8 + half):
            if 1 <= x <= 14:
                c.put(x, y, SULF[1] if rng.random() < 0.6 else SULF[2])
    for x, y in ((6, 7), (9, 9), (5, 11), (10, 12), (8, 6)):
        c.put(x, y, SULF[0])
    for x in range(2, 14):
        c.put(x, 13, SULF[3])
    c.outline()
    return c


def martian_steel_ingot():
    """An ingot of rust-red Martian steel with a darker temper line."""
    c = Canvas()
    for y in range(5, 12):
        inset = max(0, 7 - y) if y < 7 else 0
        for x in range(2 + inset, 14 - inset):
            k = 0 if y == 5 else 1 if y < 8 else 2 if y < 10 else 3
            c.put(x, y, MSTEEL[k])
    for x in range(3, 13):
        c.put(x, 9, MSTEEL[4])
    c.put(4, 6, (255, 255, 255, 255))
    c.outline()
    return c


def cell(fill, glow, cap=STEEL):
    """An Empty Cell (power ladder style) holding something."""
    c = Canvas()
    for y in range(2, 15):
        for x in range(4, 12):
            if y in (2, 14) and x in (4, 11):
                continue
            edge = x in (4, 11) or y in (2, 3, 14)
            c.put(x, y, cap[3] if edge else fill)
    c.rect(6, 0, 9, 1, cap[2])
    for y in range(4, 14):
        c.put(5, y, (255, 255, 255, 110))
    c.rect(7, 6, 8, 11, glow)
    return c


def sulfuric_acid_cell():
    c = cell(ACID[2], ACID[0])
    c.put(9, 12, ACID[3])
    c.put(8, 4, O.HAZ_Y)
    return c


def hydrolox_fuel_cell():
    """Half hydrogen blue, half oxygen white: the two halves of hydrolox."""
    c = cell(H2[2], H2[0])
    for y in range(9, 14):
        for x in range(6, 11):
            c.put(x, y, OX[2])
    c.rect(7, 10, 8, 12, OX[0])
    c.rect(6, 0, 9, 1, ORANGE[1])
    return c


def oxygen_cell():
    c = cell(OX[2], OX[0])
    c.put(7, 4, rgb("#3A8AD8"))
    c.put(8, 4, rgb("#3A8AD8"))
    return c


def quantum_circuit():
    """A dark board with violet Ionite traces and a glowing core."""
    c = Canvas()
    for y in range(2, 14):
        for x in range(2, 14):
            c.put(x, y, rgb("#1A1430") if (x + y) % 5 else rgb("#221A3E"))
    c.bevel(2, 2, 13, 13, rgb("#3A2E60"), rgb("#0E0A1C"))
    for x in range(3, 13):
        c.put(x, 5, P.VIOLET[2])
        c.put(x, 10, P.VIOLET[2])
    for y in range(5, 11):
        c.put(5, y, P.VIOLET[3])
        c.put(10, y, P.VIOLET[3])
    c.rect(6, 6, 9, 9, P.VIOLET[1])
    c.rect(7, 7, 8, 8, P.VIOLET[0])
    for x in (4, 7, 10):
        c.put(x, 1, O.GOLD[1])
        c.put(x, 14, O.GOLD[1])
    c.outline()
    return c


BLOCKS = {"lunar_glass": lunar_glass,
          "fuel_synthesizer_front": lambda: synth_front(False), "fuel_synthesizer_front_on": lambda: synth_front(True),
          "fuel_synthesizer_side": synth_side, "fuel_synthesizer_top": synth_top,
          "fuel_synthesizer_tank_h": lambda: tank(H2), "fuel_synthesizer_tank_o": lambda: tank(OX),
          "ascent_hull": ascent_hull, "ascent_booster": ascent_booster, "ascent_window": ascent_window,
          "ascent_nozzle": ascent_nozzle,
          "distress_beacon_body": beacon_body, "distress_beacon_lens": beacon_lens, "distress_beacon_leg": beacon_leg}
for _rock in ROCKS:
    BLOCKS[f"polished_{_rock}"] = (lambda r=_rock: polished(r))
    BLOCKS[f"{_rock}_bricks"] = (lambda r=_rock: bricks(r))
    BLOCKS[f"{_rock}_tiles"] = (lambda r=_rock: tiles(r))
ITEMS = {"sulfur": sulfur, "martian_steel_ingot": martian_steel_ingot, "sulfuric_acid_cell": sulfuric_acid_cell,
         "quantum_circuit": quantum_circuit, "hydrolox_fuel_cell": hydrolox_fuel_cell, "oxygen_cell": oxygen_cell}


def paths():
    for name, fn in BLOCKS.items():
        yield TEX / "block" / f"{name}.png", fn
    for name, fn in ITEMS.items():
        yield TEX / "item" / f"{name}.png", fn


def draw(only_missing):
    for path, fn in paths():
        if only_missing and path.exists():
            continue
        fn().save(path)


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    draw(only_missing=False)
    print(f"wrote {len(BLOCKS) + len(ITEMS)} outpost textures")

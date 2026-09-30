#!/usr/bin/env python3
"""Textures of the power ladder (16x16, drawn with tools/gen_textures.py's canvas and palettes):

  generators   Kinetic Dynamo, Steam Engine (+ flywheel), Wind Turbine (+ mast, blades), Biogas
               Generator, Magmatic Generator, Advanced Solar Array, RTG
  machines     Centrifuge, Electrolyzer
  ores         lead ore (stone + deepslate), deepslate uranium ore
  fission      reactor casing / glass / fuel channel / control rod / controller / four ports,
               Waste Barrel, corium (animated)
  fusion       Fusion Casing, Fusion Magnet, Fusion Port, Tokamak Core, the plasma ring
  items        lead and uranium materials, fuel rods, waste, pellets, cells, helium-3, Geiger counter,
               Hazmat Suit (+ its armour layers in entity/equipment)

Run:  python3 tools/features/power_textures.py      (redraws all of them, writes tools/power_textures_preview.png)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily).
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOLS = HERE.parent
TEX = TOOLS.parent / "src/main/resources/assets/factoryascent/textures"


def _gt():
    if str(TOOLS) not in sys.path:
        sys.path.insert(0, str(TOOLS))
    import gen_textures
    return gen_textures


# ============================================================ palettes & helpers (bound lazily)

G = None


def _init():
    global G, C, mix, lighten, darken, ramp, rng_for, Tex, BRONZE, COPPER, IRON, STEEL, DARK, WHITE, YELLOW, LEAD, URAN
    global GREEN_PAINT, CYAN, VIOLET, LAVA, WATER, RED, BLUE_PV, TITAN, GOLD
    if G is not None:
        return
    G = _gt()
    C, mix, lighten, darken, ramp, rng_for, Tex = G.C, G.mix, G.lighten, G.darken, G.ramp, G.rng_for, G.Tex
    BRONZE = G.MAT["bronze"]
    COPPER = G.MAT["copper"]
    IRON = G.MAT["iron"]
    GOLD = G.MAT["gold"]
    STEEL = ramp("#373B42", "#5A606A", "#868E9A", "#B4BAC4", "#E2E6EC")
    DARK = ramp("#1A1D22", "#2A2E36", "#3C424C", "#555C68", "#737B88")
    WHITE = ramp("#7E8692", "#AEB5BF", "#D5DAE0", "#EEF1F4", "#FFFFFF")
    YELLOW = ramp("#5A3E06", "#A87C10", "#E0B820", "#F6D850", "#FFF4B0")
    LEAD = ramp("#23262E", "#3C414C", "#5C6474", "#848EA0", "#B4BECE")
    URAN = ramp("#16300C", "#2A6016", "#48A026", "#84E04A", "#D4FF9C")
    GREEN_PAINT = ramp("#1C3016", "#2E4E22", "#44702E", "#62923E", "#8CB85A")
    CYAN = ramp("#0E3A44", "#1A6B6E", "#2FB5B0", "#6FF0E8", "#C8FFFB")
    VIOLET = ramp("#2A1648", "#4A2E70", "#8A56D0", "#C08CFF", "#EAD6FF")
    LAVA = ramp("#5A1004", "#A02A08", "#E05A10", "#FF9A20", "#FFE070")
    WATER = ramp("#0E2448", "#1A4E9A", "#2F7CC8", "#5AB4E8", "#BDEBFF")
    RED = ramp("#4A0A08", "#8A1810", "#C82A1E", "#F05040", "#FFA090")
    BLUE_PV = ramp("#0A1430", "#152A58", "#23468A", "#3A6FC0", "#6FA4E8")
    TITAN = G.MAT["titanium"]


def tex(fill=None):
    return Tex(fill=fill) if fill is not None else Tex()


def noise(t, rng, pal, x0=0, y0=0, x1=15, y1=15, base=2, p=0.16):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r = rng.random()
            t.set(x, y, pal[base - 1] if r < p / 2 else pal[base + 1] if r < p else pal[base])


def bevel(t, x0, y0, x1, y1, light, dark):
    t.hline(x0, x1, y0, light)
    t.vline(x0, y0, y1, light)
    t.hline(x0, x1, y1, dark)
    t.vline(x1, y0, y1, dark)


def bolt(t, x, y, pal):
    t.set(x, y, pal[4])
    t.set(x + 1, y, pal[2])
    t.set(x, y + 1, pal[2])
    t.set(x + 1, y + 1, pal[0])


def plate_face(rng, pal, bolts=True, seam=None):
    """A bevelled metal plate with corner bolts."""
    t = tex()
    noise(t, rng, pal, base=2, p=0.14)
    bevel(t, 0, 0, 15, 15, pal[3], pal[0])
    bevel(t, 1, 1, 14, 14, pal[4], pal[1])
    if bolts:
        for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
            bolt(t, x, y, pal)
    if seam == "h":
        t.hline(2, 13, 7, pal[1])
        t.hline(2, 13, 8, pal[3])
    return t


def disc(t, cx, cy, r, col, r0=-1.0):
    for y in range(t.h):
        for x in range(t.w):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 < d <= r:
                t.set(x, y, col)


def trefoil(t, cx, cy, r, col, back=None):
    """The radiation trefoil: three 60-degree blades around a dot."""
    for y in range(t.h):
        for x in range(t.w):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if back is not None and d <= r + 0.8:
                t.set(x, y, back)
            a = (math.degrees(math.atan2(dy, dx)) + 90) % 120
            if d <= r * 0.28 or r * 0.42 <= d <= r and (a < 30 or a > 90):
                t.set(x, y, col)


def stripes(t, x0, y0, x1, y1, a, b, width=2, phase=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t.set(x, y, a if ((x + y + phase) // width) % 2 == 0 else b)


def glow_line(t, x0, x1, y, pal):
    for x in range(x0, x1 + 1):
        t.set(x, y, pal[3] if (x % 3) else pal[4])


# ============================================================ generators

def dynamo_housing(rng):
    return plate_face(rng, BRONZE, seam="h")


def dynamo_coil(on):
    t = tex()
    for y in range(16):
        for x in range(16):
            c = COPPER[3] if y % 2 == 0 else COPPER[1]
            if x % 5 == 0:
                c = COPPER[2]
            t.set(x, y, c)
    bevel(t, 0, 0, 15, 15, COPPER[4], COPPER[0])
    if on:
        for y in range(1, 15, 2):
            for x in range(1, 15):
                if (x + y) % 4 == 0:
                    t.set(x, y, C("#FFD090"))
    return t


def dynamo_armature():
    t = tex()
    for y in range(16):
        for x in range(16):
            seg = (x // 2) % 2
            t.set(x, y, COPPER[3] if seg else IRON[2])
    t.hline(0, 15, 0, IRON[4])
    t.hline(0, 15, 15, IRON[0])
    for x in range(0, 16, 4):
        t.vline(x, 0, 15, darken(COPPER[1], 0.2))
    return t


def dynamo_top(rng):
    t = plate_face(rng, BRONZE, bolts=False)
    disc(t, 8, 8, 6.5, BRONZE[0])
    disc(t, 8, 8, 5.5, IRON[1])
    for i in range(6):
        a = i * math.pi / 3
        for r in range(2, 6):
            t.set(int(8 + math.cos(a) * r), int(8 + math.sin(a) * r), IRON[3])
    disc(t, 8, 8, 1.6, IRON[4])
    return t


def iron_part(rng):
    t = tex()
    noise(t, rng, IRON, base=2, p=0.2)
    t.hline(0, 15, 0, IRON[4])
    t.hline(0, 15, 15, IRON[0])
    return t


def dynamo_terminal(on):
    t = tex(DARK[1])
    bevel(t, 0, 0, 15, 15, DARK[3], DARK[0])
    for x in (3, 7, 11):
        t.rect(x, 4, x + 2, 11, COPPER[2])
        t.rect(x, 4, x + 2, 5, COPPER[4])
    t.rect(2, 13, 13, 14, (C("#3CE05A") if on else C("#6A1616")))
    return t


def steam_firebox(rng, on):
    t = G.brick_wall(rng, G.ramp("#3A1A10", "#6A2E1C", "#8E4028", "#B05A38", "#D07A50"), C("#2A2420"), [[8, 8], [4, 8, 4], [8, 8], [4, 8, 4]])
    t.rect(4, 5, 11, 14, IRON[0])
    t.rect(5, 6, 10, 13, IRON[2] if not on else C("#FF8A20"))
    if on:
        for y in range(7, 13):
            for x in range(6, 10):
                t.set(x, y, C("#FFE070") if (x + y) % 3 == 0 else C("#FFB030"))
    else:
        t.rect(6, 8, 9, 8, IRON[1])
        t.rect(6, 11, 9, 11, IRON[1])
    t.rect(9, 9, 10, 10, IRON[4])
    return t


def steam_boiler(rng):
    t = tex()
    noise(t, rng, COPPER, base=2, p=0.18)
    for x in (0, 8):
        t.vline(x, 0, 15, COPPER[0])
        for y in range(1, 16, 3):
            t.set(x + 1, y, COPPER[4])
    t.hline(0, 15, 0, COPPER[4])
    t.hline(0, 15, 15, COPPER[0])
    return t


def steam_boiler_end(rng):
    t = plate_face(rng, IRON, bolts=False)
    for i in range(12):
        a = i * math.pi / 6
        t.set(int(8 + math.cos(a) * 6.3), int(8 + math.sin(a) * 6.3), IRON[4])
    # pressure gauge
    disc(t, 8, 7, 3.6, C("#E8E0C8"))
    disc(t, 8, 7, 3.6, IRON[0], 3.0)
    t.set(8, 7, C("#202020"))
    t.set(9, 6, C("#D02010"))
    t.set(10, 5, C("#D02010"))
    t.rect(7, 12, 9, 13, BRONZE[3])
    return t


def brass(rng):
    t = tex()
    noise(t, rng, GOLD, base=2, p=0.2)
    t.hline(0, 15, 0, GOLD[4])
    t.hline(0, 15, 15, GOLD[0])
    return t


def flywheel():
    t = tex(IRON[1])
    for y in range(16):
        for x in range(16):
            if (x + y) % 5 == 0:
                t.set(x, y, IRON[2])
    bevel(t, 0, 0, 15, 15, IRON[3], IRON[0])
    return t


def chimney(rng):
    t = tex()
    noise(t, rng, DARK, base=1, p=0.25)
    for y in (3, 11):
        t.hline(0, 15, y, DARK[3])
        t.hline(0, 15, y + 1, DARK[0])
    return t


def turbine_nacelle(rng):
    t = tex()
    noise(t, rng, WHITE, base=3, p=0.08)
    t.hline(0, 15, 5, WHITE[1])
    t.hline(0, 15, 6, WHITE[4])
    for x in (4, 11):
        t.set(x, 10, WHITE[0])
        t.set(x, 11, WHITE[1])
    t.hline(0, 15, 15, WHITE[1])
    return t


def turbine_detail(rng):
    t = plate_face(rng, STEEL)
    t.rect(4, 6, 11, 9, DARK[1])
    for x in range(5, 11, 2):
        t.vline(x, 6, 9, DARK[3])
    return t


def turbine_blade():
    t = tex(WHITE[3])
    for y in range(16):
        t.set(0, y, WHITE[1])
        t.set(15, y, WHITE[4])
    for y in range(0, 4):
        t.hline(0, 15, y, RED[2] if y != 1 else RED[3])
    t.hline(0, 15, 4, WHITE[1])
    return t


def turbine_hub():
    t = tex(WHITE[2])
    disc(t, 8, 8, 7.5, WHITE[3])
    disc(t, 8, 8, 4, WHITE[4])
    disc(t, 8, 8, 1.5, STEEL[2])
    bevel(t, 0, 0, 15, 15, WHITE[4], WHITE[0])
    return t


def turbine_mast(rng):
    t = tex()
    noise(t, rng, WHITE, base=2, p=0.1)
    t.hline(0, 15, 0, WHITE[0])
    t.hline(0, 15, 1, WHITE[4])
    for x in (2, 13):
        t.set(x, 0, STEEL[1])
    # ladder rungs up the side
    t.vline(6, 0, 15, STEEL[1])
    t.vline(9, 0, 15, STEEL[1])
    for y in range(3, 16, 4):
        t.hline(6, 9, y, STEEL[2])
    return t


def turbine_mast_top():
    t = tex(WHITE[2])
    disc(t, 8, 8, 7.8, WHITE[1])
    disc(t, 8, 8, 6.6, WHITE[3])
    for i in range(8):
        a = i * math.pi / 4
        bolt(t, int(7.5 + math.cos(a) * 5), int(7.5 + math.sin(a) * 5), STEEL)
    disc(t, 8, 8, 2.5, STEEL[1])
    return t


def biogas_tank(rng):
    t = tex()
    noise(t, rng, GREEN_PAINT, base=2, p=0.14)
    for y in (0, 7, 15):
        t.hline(0, 15, y, GREEN_PAINT[0])
    for y in (1, 8):
        t.hline(0, 15, y, GREEN_PAINT[4])
    for x in range(0, 16, 4):
        t.set(x + 1, 3, GREEN_PAINT[4])
        t.set(x + 1, 11, GREEN_PAINT[4])
    # rust streaks
    for x in (3, 10, 13):
        for y in range(9, 9 + rng.randint(3, 6)):
            t.set(x, y, C("#6A4A22"))
    return t


def biogas_dome(rng):
    t = tex()
    noise(t, rng, GREEN_PAINT, base=1, p=0.2)
    for y in range(0, 16, 5):
        t.hline(0, 15, y, GREEN_PAINT[3])
    return t


def biogas_engine(rng, on):
    t = plate_face(rng, STEEL, bolts=False)
    t.rect(2, 3, 13, 8, DARK[1])
    for x in range(3, 13, 2):
        t.vline(x, 3, 8, DARK[3])
    t.rect(3, 11, 6, 13, DARK[0])
    t.rect(4, 12, 5, 12, C("#3CE05A") if on else C("#6A1616"))
    t.rect(9, 11, 12, 13, YELLOW[2])
    t.set(10, 12, YELLOW[0])
    return t


def flame():
    t = tex()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) / (1.5 + y * 0.4)
            if d < 1:
                t.set(x, y, C("#FFF4A0") if d < 0.35 else C("#FFB030") if d < 0.7 else C("#FF6010"))
    return t


def magmatic_casing(rng):
    t = plate_face(rng, DARK)
    t.hline(2, 13, 7, DARK[0])
    t.hline(2, 13, 8, DARK[3])
    return t


def magmatic_front(rng, on, phase=0):
    t = plate_face(rng, DARK, bolts=False)
    t.rect(3, 3, 12, 12, DARK[0])
    for y in range(4, 12):
        for x in range(4, 12):
            if on:
                v = G.lava_value(x * 2 + phase * 3, y * 2 - phase * 2)
                t.set(x, y, LAVA[4] if v < 0.25 else LAVA[3] if v < 0.5 else LAVA[2])
            else:
                t.set(x, y, C("#2A1410") if (x + y) % 3 else C("#3A1C14"))
    for x in (5, 8, 11):  # grille bars
        t.vline(x, 4, 11, DARK[2])
    bolt(t, 1, 1, DARK)
    bolt(t, 13, 1, DARK)
    bolt(t, 1, 13, DARK)
    bolt(t, 13, 13, DARK)
    return t


def magmatic_fins():
    t = tex(DARK[1])
    for x in range(16):
        if x % 3 == 0:
            t.vline(x, 0, 15, DARK[4])
        elif x % 3 == 1:
            t.vline(x, 0, 15, DARK[2])
    return t


def magmatic_top(rng, on):
    t = plate_face(rng, DARK, bolts=False)
    disc(t, 8, 8, 5.5, DARK[0])
    disc(t, 8, 8, 4.5, LAVA[3] if on else C("#2A1410"))
    if on:
        disc(t, 8, 8, 2, LAVA[4])
    return t


def solar_cells():
    t = tex(BLUE_PV[1])
    for y in range(16):
        for x in range(16):
            c = BLUE_PV[2] if (x // 2 + y // 2) % 2 else BLUE_PV[1]
            if x % 8 in (0, 7) or y % 8 in (0, 7):
                c = STEEL[3]
            t.set(x, y, c)
    for i in range(1, 7):  # sky reflection
        t.set(i + 1, i, BLUE_PV[4])
        t.set(i + 9, i + 8, BLUE_PV[3])
    return t


def solar_back(rng):
    t = plate_face(rng, STEEL, bolts=False)
    t.rect(6, 6, 9, 9, DARK[1])
    return t


def solar_frame(rng):
    t = tex()
    noise(t, rng, STEEL, base=3, p=0.1)
    t.hline(0, 15, 0, STEEL[4])
    t.hline(0, 15, 15, STEEL[1])
    return t


def rtg_body(rng):
    t = tex()
    noise(t, rng, DARK, base=2, p=0.14)
    stripes(t, 0, 6, 15, 9, YELLOW[2], DARK[0], width=2)
    t.hline(0, 15, 5, DARK[4])
    t.hline(0, 15, 10, DARK[0])
    return t


def rtg_fin(rng):
    t = tex()
    noise(t, rng, G.MAT["aluminum"], base=2, p=0.12)
    for y in range(1, 16, 3):
        t.hline(0, 15, y, G.MAT["aluminum"][4])
    t.vline(15, 0, 15, G.MAT["aluminum"][0])
    return t


def rtg_top(rng):
    t = tex(DARK[2])
    disc(t, 8, 8, 7.6, DARK[1])
    disc(t, 8, 8, 6.6, YELLOW[2])
    trefoil(t, 8, 8, 5.6, DARK[0])
    return t


def rtg_core():
    t = tex(C("#FF5020"))
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 7 == 0:
                t.set(x, y, C("#FFB060"))
    return t


# ============================================================ centrifuge & electrolyzer

def centrifuge_panel(rng, on):
    t = plate_face(rng, TITAN)
    t.rect(3, 3, 12, 8, DARK[0])
    for i, x in enumerate(range(4, 12, 2)):
        h = (i * 3 + (2 if on else 0)) % 5 + 1
        t.rect(x, 8 - h, x, 7, C("#3CE05A") if on else DARK[3])
    for x, col in ((4, "#E0C040"), (7, "#40A0E0"), (10, "#E04040")):
        t.rect(x, 10, x + 1, 11, C(col) if on else DARK[2])
    t.hline(3, 12, 13, TITAN[1])
    return t


def centrifuge_tube(rng, on):
    t = tex()
    for y in range(16):
        for x in range(16):
            shade = abs(x - 7.5) / 8
            t.set(x, y, mix(TITAN[4], TITAN[1], shade))
    for y in (2, 13):
        t.hline(0, 15, y, TITAN[0])
    if on:
        for y in range(4, 12):
            t.set(7, y, C("#9CFF6A"))
            t.set(8, y, C("#4FD02A"))
    else:
        t.vline(7, 4, 11, TITAN[3])
    return t


def centrifuge_top(rng):
    t = plate_face(rng, TITAN, bolts=False)
    for cx, cy in ((4.5, 4.5), (11.5, 4.5), (4.5, 11.5), (11.5, 11.5)):
        disc(t, cx, cy, 3, TITAN[0])
        disc(t, cx, cy, 2, TITAN[3])
    return t


def electrolyzer_tank(rng):
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, C("#9ED8F0", 140) if y > 3 else C("#DCF2FF", 110))
    for y in range(5, 16):
        for x in range(16):
            if (x * 7 + y * 3) % 11 == 0:
                t.set(x, y, C("#FFFFFF", 200))
    t.hline(0, 15, 4, C("#E8F8FF", 220))
    bevel(t, 0, 0, 15, 15, C("#C8E4F0", 230), C("#6E8EA0", 230))
    return t


def electrolyzer_front(rng, on):
    t = plate_face(rng, WHITE)
    t.rect(3, 3, 12, 7, DARK[0])
    for i, x in enumerate((4, 6, 8, 10)):
        t.rect(x, 4, x, 6, C("#40A0E0") if on and i % 2 == 0 else C("#E0E0E0") if on else DARK[3])
    t.rect(3, 10, 6, 12, C("#2F7CC8"))
    t.rect(9, 10, 12, 12, C("#E04040"))
    t.set(4, 11, C("#FFFFFF"))
    t.set(10, 11, C("#FFFFFF"))
    return t


def electrode(on):
    t = tex(DARK[2])
    for y in range(16):
        t.set(7, y, DARK[4])
        t.set(8, y, DARK[3])
    if on:
        for y in range(2, 16, 3):
            t.set(5 + (y % 2) * 5, y, C("#FFFFFF"))
    return t


def dome(rng, col):
    t = tex()
    noise(t, rng, col, base=2, p=0.1)
    bevel(t, 0, 0, 15, 15, col[4], col[0])
    return t


# ============================================================ ores

def ores():
    G.ORES.setdefault("lead", (ramp("#2A2E3A", "#4A5264", "#707A8E", "#9AA4B8", "#C8D0E0"), "lump", 5, (6, 9)))
    G.ORES.setdefault("uranium", (ramp("#1E4A10", "#3A8A1C", "#62C432", "#A0F060", "#E8FFB8"), "gem", 5, None))
    return {"lead_ore": G.ore_block("lead", False), "deepslate_lead_ore": G.ore_block("lead", True),
            "deepslate_uranium_ore": G.ore_block("uranium", True)}


# ============================================================ fission

def reactor_casing(rng):
    t = plate_face(rng, LEAD, seam=None)
    stripes(t, 2, 2, 13, 3, YELLOW[2], DARK[0], width=2)
    stripes(t, 2, 12, 13, 13, YELLOW[2], DARK[0], width=2)
    t.hline(2, 13, 7, LEAD[1])
    t.hline(2, 13, 8, LEAD[3])
    return t


def reactor_glass():
    """Thick leaded glass: almost clear with a faint green tint, a thin frame and two glints."""
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, C("#C8F0E0", 38))
    for i in range(3, 7):
        t.set(i + 1, i, C("#FFFFFF", 120))
        t.set(i + 7, i + 6, C("#FFFFFF", 90))
    bevel(t, 0, 0, 15, 15, LEAD[3], LEAD[0])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        t.set(x, y, LEAD[1])
    return t


def fuel_channel(rng):
    t = tex()
    noise(t, rng, STEEL, base=2, p=0.14)
    for x in (3, 7, 11):
        t.rect(x, 2, x + 1, 13, C("#40C8FF"))
        t.set(x, 2, C("#C8F4FF"))
    t.hline(0, 15, 0, STEEL[4])
    t.hline(0, 15, 15, STEEL[0])
    t.hline(0, 15, 1, STEEL[1])
    return t


def fuel_channel_top(rng):
    t = tex(STEEL[1])
    disc(t, 8, 8, 7.5, STEEL[2])
    for cx, cy in ((5, 5), (11, 5), (5, 11), (11, 11)):
        disc(t, cx, cy, 2.2, URAN[3])
        disc(t, cx, cy, 1.0, URAN[4])
    disc(t, 8, 8, 1.2, STEEL[4])
    return t


def cherenkov():
    t = tex(C("#2A8CFF"))
    for y in range(16):
        for x in range(16):
            v = (math.sin(x * 0.9) + math.cos(y * 0.7 + x * 0.3)) * 0.5
            t.set(x, y, C("#7CD8FF") if v > 0.4 else C("#3AA0FF") if v > -0.3 else C("#1E6AE0"))
    return t


def control_rod(rng):
    t = tex()
    noise(t, rng, DARK, base=1, p=0.16)
    for y in range(0, 16, 4):
        t.hline(0, 15, y, DARK[3])
        t.set(2, y + 2, YELLOW[2])
    return t


def control_head(rng):
    t = plate_face(rng, YELLOW, bolts=False)
    t.rect(5, 5, 10, 10, DARK[1])
    t.rect(6, 6, 9, 9, DARK[3])
    return t


def reactor_controller(rng, on):
    t = plate_face(rng, LEAD, bolts=True)
    t.rect(2, 2, 13, 8, DARK[0])
    t.rect(3, 3, 12, 7, C("#0A2A14") if on else C("#101418"))
    if on:
        for x in range(3, 13):  # temperature trace
            y = 6 - int(1.5 + math.sin(x * 0.9) * 1.5)
            t.set(x, y, C("#3CE05A"))
        t.hline(3, 12, 7, C("#1E8A2E"))
    trefoil(t, 4.5, 11.5, 2.2, YELLOW[2], back=DARK[0])
    disc(t, 11, 11.5, 2.4, RED[2] if on else RED[1])
    t.set(10, 10, RED[4])
    t.hline(7, 8, 10, C("#3CE05A") if on else DARK[2])
    t.hline(7, 8, 12, YELLOW[2] if on else DARK[2])
    return t


def port_face(rng, kind):
    t = plate_face(rng, LEAD, bolts=True)
    if kind == "access":
        t.rect(3, 3, 12, 12, LEAD[0])
        t.rect(4, 4, 11, 11, LEAD[2])
        bevel(t, 4, 4, 11, 11, LEAD[3], LEAD[1])
        t.rect(6, 7, 9, 8, STEEL[4])
        for x in range(5, 11):
            t.set(x, 5, YELLOW[2] if x % 2 else DARK[0])
    elif kind == "power":
        t.rect(3, 3, 12, 12, DARK[1])
        for x in (4, 7, 10):
            t.rect(x, 5, x + 1, 10, COPPER[3])
            t.rect(x, 5, x + 1, 5, COPPER[4])
        t.set(12, 3, YELLOW[3])
    elif kind == "coolant":
        disc(t, 8, 8, 6, STEEL[1])
        disc(t, 8, 8, 5, WATER[1])
        disc(t, 8, 8, 3.2, WATER[3])
        t.hline(3, 12, 8, STEEL[3])
        t.vline(8, 3, 12, STEEL[3])
    else:  # redstone
        t.rect(3, 3, 12, 12, DARK[0])
        disc(t, 8, 8, 4, RED[2])
        disc(t, 8, 8, 2, RED[4])
        for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
            t.set(x, y, RED[1])
    return t


def port_detail(kind):
    pal = {"access": STEEL, "power": COPPER, "coolant": WATER, "redstone": RED}[kind]
    t = tex(pal[2])
    bevel(t, 0, 0, 15, 15, pal[4], pal[0])
    for x in range(2, 14, 3):
        t.vline(x, 2, 13, pal[1])
    return t


def waste_barrel_side(rng):
    t = tex()
    noise(t, rng, YELLOW, base=2, p=0.12)
    for y in (1, 14):
        t.hline(0, 15, y, YELLOW[0])
        t.hline(0, 15, y + 1 if y == 1 else y - 1, YELLOW[4])
    trefoil(t, 8, 8, 5.8, DARK[0], back=None)
    # scuffs
    for x, y in ((2, 5), (13, 10), (3, 11)):
        t.set(x, y, YELLOW[1])
    return t


def waste_barrel_top(rng):
    t = tex(YELLOW[1])
    disc(t, 8, 8, 7.6, YELLOW[2])
    disc(t, 8, 8, 6.6, YELLOW[1], 6.0)
    disc(t, 11, 5, 1.8, DARK[1])
    disc(t, 11, 5, 0.9, DARK[3])
    stripes(t, 3, 9, 8, 11, YELLOW[3], DARK[0], width=2)
    return t


def corium_frames():
    frames = []
    for phase in range(6):
        t = tex()
        for y in range(16):
            for x in range(16):
                v = G.lava_value(x * 2 + phase * 2, y * 2 + phase)
                if v < 0.18:
                    c = C("#FFE080")
                elif v < 0.34:
                    c = C("#FF8A20")
                elif v < 0.5:
                    c = C("#8A2A10")
                else:
                    c = C("#2A2420") if (x + y + phase) % 5 else C("#4A3A2A")
                t.set(x, y, c)
        for x, y in ((3, 4), (12, 9), (6, 13)):
            t.set(x, y, C("#9CFF6A"))
        frames.append(t)
    return frames


# ============================================================ fusion

def fusion_casing(rng):
    t = plate_face(rng, ramp("#1A1E2A", "#2A3242", "#3C4658", "#566278", "#7A869C"))
    for x in range(2, 14):
        t.set(x, 7, CYAN[1])
        t.set(x, 8, CYAN[2] if x % 3 else CYAN[3])
    return t


def magnet_coil():
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, COPPER[3] if x % 2 == 0 else COPPER[1])
            if y in (0, 15):
                t.set(x, y, darken(COPPER[0], 0.3))
    for y in range(1, 15, 5):
        t.hline(0, 15, y, C("#8090A8"))
    return t


def magnet_core(rng):
    t = tex()
    noise(t, rng, ramp("#141A24", "#1E2634", "#2A3444", "#3A485C", "#56667E"), base=2, p=0.16)
    for i in range(3, 13, 3):  # frost of the cryostat
        t.set(i, 2, C("#D8F0FF"))
        t.set(i + 1, 13, C("#B0D8F0"))
    bevel(t, 0, 0, 15, 15, CYAN[2], CYAN[0])
    return t


def fusion_port(rng):
    t = fusion_casing(rng)
    t.rect(3, 3, 12, 12, C("#141A24"))
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            if max(dx, dy * 1.15 + dx * 0.5) < 4.6 and max(dx, dy * 1.15 + dx * 0.5) > 3.4:
                t.set(x, y, CYAN[3])
    disc(t, 8, 8, 1.8, CYAN[4])
    return t


def tokamak_core(rng, on):
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, COPPER[3] if (y // 2) % 2 else COPPER[1])
    for x in (0, 5, 10, 15):
        t.vline(x, 0, 15, C("#2A3444"))
    if on:
        for x in (2, 7, 12):
            t.vline(x, 0, 15, VIOLET[3])
            t.vline(x + 1, 0, 15, VIOLET[4])
    return t


def tokamak_top(rng, on):
    t = tex(C("#2A3444"))
    disc(t, 8, 8, 7, COPPER[2])
    disc(t, 8, 8, 5, C("#1A2230"))
    disc(t, 8, 8, 3, VIOLET[3] if on else VIOLET[0])
    return t


def plasma():
    t = tex()
    for y in range(16):
        for x in range(16):
            v = abs(y - 7.5) / 8
            a = int(235 - 150 * v)
            col = mix(C("#FFE8FF"), C("#B050FF"), min(1, v * 1.6))
            if (x + y * 2) % 7 == 0:
                col = C("#FFFFFF")
            t.set(x, y, (col[0], col[1], col[2], a))
    return t


def plasma_filament():
    t = tex()
    for y in range(16):
        for x in range(16):
            if (x + y) % 6 in (0, 1):
                t.set(x, y, (200, 250, 255, 190 if (x + y) % 6 == 0 else 110))
    return t


# ============================================================ items

def item():
    return tex()


def fuel_rod(depleted):
    """A zircaloy fuel rod standing on end: end caps, and the pellet stack glowing through its window."""
    t = item()
    body = DARK if depleted else STEEL
    for y in range(2, 14):
        for x in range(5, 11):
            t.set(x, y, body[3] if x == 5 else body[4] if x == 6 else body[1] if x == 10 else body[2])
    for y in range(4, 12):
        for x in (7, 8):
            if depleted:
                t.set(x, y, C("#6A7A30") if y % 2 else C("#44521E"))
            else:
                t.set(x, y, URAN[4] if y % 2 else URAN[3])
    cap = YELLOW if not depleted else ramp("#2A2418", "#4A3E28", "#6A5A40", "#8A7A58", "#A89878")
    for y in (0, 1, 14, 15):
        for x in range(4, 12):
            t.set(x, y, cap[3] if x < 7 else cap[2] if x < 10 else cap[1])
    t.hline(4, 11, 0, cap[4])
    t.hline(4, 11, 15, cap[0])
    if depleted:
        for x, y in ((12, 5), (13, 3), (3, 9), (12, 11)):
            t.set(x, y, C("#B8FF60", 200))
    return t


def nuclear_waste():
    t = item()
    for y in range(4, 15):
        for x in range(3, 13):
            d = math.hypot((x - 7.5) / 5, (y - 9.5) / 5.5)
            if d < 1:
                t.set(x, y, C("#3A4A20") if (x * 3 + y) % 4 else C("#5A6A2A"))
    for x, y in ((6, 8), (7, 8), (9, 11), (5, 12), (10, 7)):
        t.set(x, y, C("#B8FF60"))
    for x, y in ((8, 3), (9, 2), (6, 2)):
        t.set(x, y, C("#9CFF6A", 180))
    return t


def pellet():
    t = item()
    for y in range(3, 14):
        for x in range(4, 12):
            d = abs(x - 7.5) / 4
            t.set(x, y, mix(C("#FF9040"), C("#A02A08"), d))
    t.hline(4, 11, 3, C("#FFD090"))
    t.hline(4, 11, 13, C("#6A1A04"))
    trefoil(t, 8, 8.5, 2.6, C("#2A0A04"))
    return t


def cell(fill, glow=None):
    t = item()
    for y in range(2, 15):
        for x in range(4, 12):
            if y in (2, 14) and x in (4, 11):
                continue
            edge = x in (4, 11) or y in (2, 3, 14)
            t.set(x, y, STEEL[3] if edge else (fill if fill else C("#3A3F48")))
    t.rect(6, 0, 9, 1, STEEL[2])
    t.vline(5, 4, 13, C("#FFFFFF", 110))
    if glow:
        t.rect(7, 6, 8, 11, glow)
    return t


def helium3():
    t = item()
    disc(t, 8, 8, 6.5, C("#FFE8B0", 120))
    disc(t, 8, 8, 4.5, C("#FFD070", 190))
    disc(t, 8, 8, 2.2, C("#FFFFFF"))
    for x, y in ((3, 3), (12, 4), (4, 12), (12, 12)):
        t.set(x, y, C("#FFF4D0"))
    return t


def geiger():
    t = item()
    t.rect(2, 5, 11, 14, YELLOW[2])
    bevel(t, 2, 5, 11, 14, YELLOW[4], YELLOW[0])
    t.rect(4, 7, 9, 10, C("#E8E0C8"))
    t.set(5, 9, C("#202020"))
    t.set(6, 8, C("#D02010"))
    t.set(7, 8, C("#D02010"))
    t.rect(4, 12, 5, 13, DARK[1])
    t.rect(8, 12, 9, 13, RED[2])
    # probe on a cable
    t.rect(12, 1, 14, 6, STEEL[3])
    t.vline(13, 7, 9, DARK[0])
    t.hline(11, 13, 9, DARK[0])
    t.set(13, 1, STEEL[4])
    return t


def hazmat_item(kind):
    t = item()
    Y, K = YELLOW, DARK
    if kind == "helmet":
        for y in range(2, 12):
            for x in range(3, 13):
                if math.hypot((x - 7.5) / 5, (y - 7) / 5) < 1:
                    t.set(x, y, Y[2])
        t.rect(5, 5, 10, 8, C("#6ED0E8"))
        t.set(6, 6, C("#FFFFFF"))
        t.rect(6, 11, 9, 13, K[1])
        disc(t, 7, 12, 1.2, K[3])
    elif kind == "chestplate":
        t.rect(3, 3, 12, 14, Y[2])
        t.rect(0, 3, 2, 10, Y[1])
        t.rect(13, 3, 15, 10, Y[1])
        t.rect(6, 2, 9, 3, K[1])
        t.vline(7, 4, 13, Y[0])
        trefoil(t, 10.5, 10.5, 2.0, K[0])
    elif kind == "leggings":
        t.rect(3, 2, 12, 5, Y[2])
        t.rect(3, 6, 6, 14, Y[2])
        t.rect(9, 6, 12, 14, Y[2])
        t.hline(3, 12, 3, K[1])
    else:
        t.rect(2, 8, 6, 13, Y[2])
        t.rect(9, 8, 13, 13, Y[2])
        t.rect(1, 12, 6, 14, K[1])
        t.rect(9, 12, 14, 14, K[1])
    for y in range(16):
        for x in range(16):
            c = t.get(x, y)
            if c[3] > 0 and all(t.get(x + dx, y + dy)[3] == 0 for dx, dy in ((0, -1), (-1, 0))):
                t.set(x, y, lighten(c, 0.25))
    return t


def effect_icon():
    """18x18 status effect icon: a green trefoil on a dark disc."""
    t = Tex(18, 18)
    disc(t, 9, 9, 8.6, C("#1A2A10"))
    disc(t, 9, 9, 8.6, C("#5FD13B"), 7.6)
    trefoil(t, 9, 9, 6.6, C("#8CF05A"))
    return t


def hazmat_armor():
    """64x32 humanoid layer (helmet with visor, suit) and the leggings layer."""
    from PIL import Image
    rng = rng_for("hazmat_armor")
    Y = YELLOW
    main = Tex(64, 32)
    for y in range(32):
        for x in range(64):
            r = rng.random()
            main.set(x, y, Y[1] if r < 0.06 else Y[3] if r < 0.1 else Y[2])
    # visor on the helmet front (8..15, 8..15)
    main.rect(9, 10, 14, 13, C("#5AC8E0"))
    main.rect(10, 11, 11, 11, C("#FFFFFF"))
    # respirator
    main.rect(10, 14, 13, 15, DARK[1])
    # seams and black straps
    for x in range(20, 28):
        main.set(x, 20, DARK[1])  # belt on body front
    for x in range(16, 40):
        main.set(x, 25, Y[0])
    trefoil(main, 24, 25.5, 3.2, DARK[0])  # on the chest
    # gloves (arm ends) dark
    for x in range(40, 56):
        for y in (30, 31):
            main.set(x, y, DARK[1])
    legs = Tex(64, 32)
    for y in range(32):
        for x in range(64):
            r = rng.random()
            legs.set(x, y, Y[1] if r < 0.06 else Y[3] if r < 0.1 else Y[2])
    # boots region of the main layer (legs 0..15, 16..31) darker soles
    for x in range(0, 16):
        for y in (30, 31):
            main.set(x, y, DARK[1])
    return main, legs


# ============================================================ registry

def provides_helium3():
    """Helium-3 belongs to the Moon: the planets content draws it when it exists."""
    planets = HERE / "planets_textures.py"
    return planets.exists() and "def helium_3(" in planets.read_text()


def build():
    _init()
    out = {}
    anim = {}

    def blk(name, t):
        out["block/%s.png" % name] = t

    def itm(name, t):
        out["item/%s.png" % name] = t

    def ani(name, frames, frametime):
        out["block/%s.png" % name] = G.anim(frames)
        anim["block/%s.png" % name] = frametime

    R = rng_for
    # generators
    blk("dynamo_housing", dynamo_housing(R("dynamo_housing")))
    blk("dynamo_coil", dynamo_coil(False))
    blk("dynamo_coil_on", dynamo_coil(True))
    blk("dynamo_armature", dynamo_armature())
    blk("dynamo_top", dynamo_top(R("dynamo_top")))
    blk("dynamo_shaft", iron_part(R("dynamo_shaft")))
    blk("dynamo_terminal", dynamo_terminal(False))
    blk("dynamo_terminal_on", dynamo_terminal(True))
    blk("steam_firebox", steam_firebox(R("steam_firebox"), False))
    blk("steam_firebox_on", steam_firebox(R("steam_firebox"), True))
    blk("steam_boiler", steam_boiler(R("steam_boiler")))
    blk("steam_boiler_end", steam_boiler_end(R("steam_boiler_end")))
    blk("steam_brass", brass(R("steam_brass")))
    blk("steam_iron", iron_part(R("steam_iron")))
    blk("steam_flywheel", flywheel())
    blk("steam_chimney", chimney(R("steam_chimney")))
    blk("turbine_nacelle", turbine_nacelle(R("turbine_nacelle")))
    blk("turbine_detail", turbine_detail(R("turbine_detail")))
    blk("turbine_blade", turbine_blade())
    blk("turbine_hub", turbine_hub())
    blk("turbine_mast", turbine_mast(R("turbine_mast")))
    blk("turbine_mast_top", turbine_mast_top())
    blk("biogas_tank", biogas_tank(R("biogas_tank")))
    blk("biogas_dome", biogas_dome(R("biogas_dome")))
    blk("biogas_engine", biogas_engine(R("biogas_engine"), False))
    blk("biogas_engine_on", biogas_engine(R("biogas_engine"), True))
    blk("biogas_flame", flame())
    blk("magmatic_casing", magmatic_casing(R("magmatic_casing")))
    blk("magmatic_front", magmatic_front(R("magmatic_front"), False))
    ani("magmatic_front_on", [magmatic_front(R("magmatic_front"), True, p) for p in range(6)], 3)
    blk("magmatic_fins", magmatic_fins())
    blk("magmatic_top", magmatic_top(R("magmatic_top"), False))
    blk("magmatic_top_on", magmatic_top(R("magmatic_top"), True))
    blk("solar_array_cells", solar_cells())
    blk("solar_array_back", solar_back(R("solar_array_back")))
    blk("solar_array_frame", solar_frame(R("solar_array_frame")))
    blk("rtg_body", rtg_body(R("rtg_body")))
    blk("rtg_fin", rtg_fin(R("rtg_fin")))
    blk("rtg_top", rtg_top(R("rtg_top")))
    blk("rtg_core", rtg_core())
    # machines
    blk("centrifuge_panel", centrifuge_panel(R("centrifuge_panel"), False))
    blk("centrifuge_panel_on", centrifuge_panel(R("centrifuge_panel"), True))
    blk("centrifuge_tube", centrifuge_tube(R("centrifuge_tube"), False))
    blk("centrifuge_tube_on", centrifuge_tube(R("centrifuge_tube"), True))
    blk("centrifuge_top", centrifuge_top(R("centrifuge_top")))
    blk("electrolyzer_tank", electrolyzer_tank(R("electrolyzer_tank")))
    blk("electrolyzer_front", electrolyzer_front(R("electrolyzer_front"), False))
    blk("electrolyzer_front_on", electrolyzer_front(R("electrolyzer_front"), True))
    blk("electrolyzer_electrode", electrode(False))
    blk("electrolyzer_electrode_on", electrode(True))
    blk("electrolyzer_dome_h", dome(R("electrolyzer_dome_h"), ramp("#1A3A6A", "#2A5A9A", "#3F7CC8", "#6AA8E8", "#B0D8FF")))
    blk("electrolyzer_dome_o", dome(R("electrolyzer_dome_o"), ramp("#5A1A18", "#8A2A20", "#C04030", "#E07060", "#FFB0A0")))
    # ores
    for name, t in ores().items():
        blk(name, t)
    # fission
    blk("reactor_casing", reactor_casing(R("reactor_casing")))
    blk("reactor_glass", reactor_glass())
    blk("reactor_fuel_channel", fuel_channel(R("reactor_fuel_channel")))
    blk("reactor_fuel_channel_top", fuel_channel_top(R("reactor_fuel_channel_top")))
    blk("reactor_cherenkov", cherenkov())
    blk("reactor_control_rod", control_rod(R("reactor_control_rod")))
    blk("reactor_control_head", control_head(R("reactor_control_head")))
    blk("reactor_controller_front", reactor_controller(R("reactor_controller_front"), False))
    blk("reactor_controller_front_on", reactor_controller(R("reactor_controller_front"), True))
    for kind in ("access", "power", "coolant", "redstone"):
        blk("reactor_port_" + kind, port_face(R("reactor_port_" + kind), kind))
        blk("reactor_port_" + kind + "_detail", port_detail(kind))
    blk("waste_barrel_side", waste_barrel_side(R("waste_barrel_side")))
    blk("waste_barrel_top", waste_barrel_top(R("waste_barrel_top")))
    ani("corium", corium_frames(), 6)
    # fusion
    blk("fusion_casing", fusion_casing(R("fusion_casing")))
    blk("fusion_magnet_coil", magnet_coil())
    blk("fusion_magnet_core", magnet_core(R("fusion_magnet_core")))
    blk("fusion_port", fusion_port(R("fusion_port")))
    blk("tokamak_core", tokamak_core(R("tokamak_core"), False))
    blk("tokamak_core_on", tokamak_core(R("tokamak_core"), True))
    blk("tokamak_core_top", tokamak_top(R("tokamak_core_top"), False))
    blk("tokamak_core_top_on", tokamak_top(R("tokamak_core_top"), True))
    blk("plasma", plasma())
    blk("plasma_filament", plasma_filament())
    # items
    lead_pal = ramp("#2A2E3A", "#4A5264", "#707A8E", "#9AA4B8", "#C8D0E0")
    uran_pal = ramp("#1E4A10", "#3A8A1C", "#62C432", "#A0F060", "#E8FFB8")
    itm("raw_lead", G.raw_lump(lead_pal, R("raw_lead")))
    itm("lead_dust", G.dust(lead_pal, R("lead_dust")))
    itm("lead_ingot", G.ingot(lead_pal))
    itm("lead_plate", G.plate(lead_pal))
    itm("raw_uranium", G.raw_lump(uran_pal, R("raw_uranium")))
    itm("uranium_dust", G.dust(uran_pal, R("uranium_dust")))
    itm("uranium_ingot", G.ingot(uran_pal))
    itm("enriched_uranium", G.ingot(ramp("#2A6A10", "#4AB020", "#7CF040", "#C0FF80", "#F4FFE0"), G.QUANTUM_VEINS))
    itm("depleted_uranium", G.ingot(ramp("#1E221E", "#343C34", "#4A564A", "#687868", "#90A090")))
    itm("fuel_rod", fuel_rod(False))
    itm("depleted_fuel_rod", fuel_rod(True))
    itm("nuclear_waste", nuclear_waste())
    itm("radioisotope_pellet", pellet())
    itm("empty_cell", cell(None))
    itm("coolant_cell", cell(C("#6ED0F0"), C("#C8F4FF")))
    itm("deuterium_cell", cell(C("#3A6AD0"), C("#9CC8FF")))
    itm("tritium_cell", cell(C("#2AA05A"), C("#B8FF90")))
    if not provides_helium3():
        itm("helium_3", helium3())
    itm("geiger_counter", geiger())
    for kind in ("helmet", "chestplate", "leggings", "boots"):
        itm("hazmat_" + kind, hazmat_item(kind))
    return out, anim


def write(only_missing):
    out, anim = build()
    for rel, t in out.items():
        path = TEX / rel
        if only_missing and path.exists():
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        t.image().save(path)
        meta = Path(str(path) + ".mcmeta")
        if rel in anim:
            meta.write_text('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n' % anim[rel])
    icon = effect_icon()
    path = TEX / "mob_effect" / "radiation.png"
    if not (only_missing and path.exists()):
        path.parent.mkdir(parents=True, exist_ok=True)
        icon.image().save(path)
    main, legs = hazmat_armor()
    for sub, t in (("humanoid", main), ("humanoid_leggings", legs)):
        path = TEX / "entity" / "equipment" / sub / "hazmat.png"
        if only_missing and path.exists():
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        t.image().save(path)
    return out


def generate(ctx):
    try:
        write(only_missing=True)
    except ImportError:
        pass


def preview(path):
    from PIL import Image, ImageDraw
    out, _ = build()
    items = list(out.items())
    cols, s = 12, 6
    cell_w, cell_h = 16 * s + 10, 16 * s + 22
    img = Image.new("RGBA", (cols * cell_w + 10, ((len(items) + cols - 1) // cols) * cell_h + 10), (40, 42, 48, 255))
    d = ImageDraw.Draw(img)
    for i, (name, t) in enumerate(items):
        im = t.image()
        if im.height > 16:
            im = im.crop((0, 0, 16, 16))
        x, y = 10 + (i % cols) * cell_w, 10 + (i // cols) * cell_h
        img.alpha_composite(im.resize((16 * s, 16 * s), Image.NEAREST), (x, y))
        d.text((x, y + 16 * s + 3), name.split("/")[-1][:-4][:17], fill=(225, 225, 225, 255))
    img.save(path)


if __name__ == "__main__":
    write(only_missing=False)
    preview(TOOLS / "power_textures_preview.png")
    print("wrote power textures and tools/power_textures_preview.png")

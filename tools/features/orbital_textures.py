#!/usr/bin/env python3
"""Textures of the Orbital age (16x16): launch pad, launch controller, ground station dish, the
rocket drawn on the pad (and its dark anti-satellite variant), the orbital radar, satellites,
the anti-satellite missile, rocket fuel and the wireless terminal.

Run:  python3 tools/features/orbital_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily, so resources still generate
without it once the PNGs exist).
"""
import math
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def shade(c, f):
    """f > 0 lightens towards white, f < 0 darkens towards black."""
    return mix(c, (255, 255, 255, c[3]), f) if f > 0 else mix(c, (0, 0, 0, c[3]), -f)


# Palettes in the house style (see tools/gen_textures.py): cool steel, dark steel, hazard stripes.
STEEL = [rgb(h) for h in ("#E2E6EC", "#B4BAC4", "#868E9A", "#5A606A", "#373B42")]
DARK = [rgb(h) for h in ("#8C94A2", "#687080", "#4C5260", "#353A44", "#22252C")]
TITAN = [rgb(h) for h in ("#EEEAF6", "#C9C4D8", "#A09AB4", "#726C88", "#4A4660")]
HAZ_Y, HAZ_K = rgb("#E8B818"), rgb("#1E1E22")
OUTLINE = rgb("#1B1922")
CYAN = [rgb(h) for h in ("#C8FFFB", "#6FF0E8", "#2FB5B0", "#1A6B6E")]
GREEN = [rgb(h) for h in ("#D8FFC8", "#7BE05A", "#3C9E2E", "#1E5A1A")]
BLUE_PV = [rgb(h) for h in ("#6FA4E8", "#3A6FC0", "#23468A", "#152A58")]
GOLD = [rgb(h) for h in ("#FFF2A8", "#F0C850", "#C08A20", "#7A5010")]
RED = [rgb(h) for h in ("#FF9A8A", "#E04A3A", "#A02620", "#5E1412")]
WHITE = [rgb(h) for h in ("#FFFFFF", "#E8ECF0", "#C4CAD2", "#8E96A2")]
FLAME = [rgb(h) for h in ("#FFF6C0", "#FFC840", "#FF8A20", "#C04010")]


class Canvas:
    def __init__(self, fill=None):
        self.px = [[fill] * 16 for _ in range(16)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16 and c is not None:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < 16 and 0 <= y < 16 else None

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def bevel(self, x0, y0, x1, y1, light, dark):
        for x in range(x0, x1 + 1):
            self.put(x, y0, light)
            self.put(x, y1, dark)
        for y in range(y0, y1 + 1):
            self.put(x0, y, light)
            self.put(x1, y, dark)

    def outline(self, color=OUTLINE):
        edge = [(x, y) for y in range(16) for x in range(16) if self.px[y][x] is None and any(
            self.get(x + dx, y + dy) not in (None, color) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for x, y in edge:
            self.px[y][x] = color

    def save(self, path):
        from PIL import Image
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)


def noisy(c, pal, rng, x0=0, y0=0, x1=15, y1=15, base=1, p=0.14):
    """Fill with the palette's base tone and a sprinkle of its neighbours (brushed metal)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r = rng.random()
            c.put(x, y, pal[base - 1] if r < p / 2 else pal[base + 1] if r < p else pal[base])


def rivet(c, x, y, pal):
    c.put(x, y, pal[0])
    c.put(x + 1, y + 1, pal[3])
    c.put(x + 1, y, pal[2])
    c.put(x, y + 1, pal[2])


# ============================================================ blocks

def launch_pad_top():
    """A steel deck plate: diamond tread, a hazard stripe along every edge so the 3x3 reads as one pad
    with a striped border and seams."""
    rng = random.Random(11)
    c = Canvas()
    noisy(c, DARK, rng, base=1)
    for y in range(2, 14):
        for x in range(2, 14):
            if (x + y) % 4 == 0 and (x - y) % 4 == 0:
                c.put(x, y, DARK[0])
                c.put(x + 1, y, DARK[2])
    for i in range(16):  # hazard edge, 1 px wide, stripes on the diagonal
        col = HAZ_Y if (i // 2) % 2 == 0 else HAZ_K
        c.put(i, 0, col)
        c.put(15, i, col)
        c.put(15 - i, 15, col)
        c.put(0, 15 - i, col)
    c.bevel(1, 1, 14, 14, DARK[0], DARK[3])
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(c, x, y, STEEL)
    return c


def launch_pad_side():
    rng = random.Random(12)
    c = Canvas()
    noisy(c, DARK, rng, base=2)
    for x in range(16):  # the visible 4 px (rows 12..15): hazard band with a dark lip
        c.put(x, 12, DARK[0])
        for y in (13, 14):
            c.put(x, y, HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K)
        c.put(x, 15, DARK[4])
    return c


def launch_controller_top(on):
    """The centre of the pad: a round blast deflector (a grate over the flame trench) inside a clamp
    ring, with status lights in the corners."""
    rng = random.Random(13)
    c = Canvas()
    noisy(c, STEEL, rng, base=2)
    c.bevel(0, 0, 15, 15, STEEL[1], STEEL[4])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 3.2:
                c.put(x, y, HAZ_K if (x + y) % 2 else DARK[3])  # grate
            elif d < 4.6:
                c.put(x, y, TITAN[1] if (x + 0.5 - 8) + (y + 0.5 - 8) < 0 else TITAN[3])
            elif d < 5.8:
                a = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                c.put(x, y, HAZ_Y if int((a + math.pi) / (math.pi / 4)) % 2 == 0 else HAZ_K)
            elif d < 6.4:
                c.put(x, y, STEEL[4])
    light = GREEN[1] if on else RED[2]
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.put(x, y, light)
    if on:  # warm glow in the trench
        for x, y in ((7, 7), (8, 8), (7, 8), (8, 7)):
            c.put(x, y, FLAME[2])
    return c


def launch_controller_side(on):
    c = launch_pad_side()
    for x in (6, 7, 8, 9):
        c.put(x, 12, DARK[4])
    c.put(7, 12, GREEN[1] if on else RED[2])
    c.put(8, 12, GREEN[0] if on else RED[3])
    return c


def station_base():
    rng = random.Random(21)
    c = Canvas()
    noisy(c, DARK, rng, base=2)
    c.bevel(0, 0, 15, 15, DARK[0], DARK[4])
    for y in (4, 8, 12):
        for x in range(2, 14):
            c.put(x, y, DARK[3])
            c.put(x, y + 1, DARK[1])
    c.put(3, 2, CYAN[1])
    c.put(5, 2, GREEN[1])
    return c


def station_dish():
    """Front of the dish: white panels with seams in rings and spokes, shaded towards the rim."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = math.hypot(dx, dy)
            tone = WHITE[1] if d < 4 else WHITE[2] if d < 7 else WHITE[3]
            if abs(d - 4.5) < 0.5 or abs(d - 7.2) < 0.45:
                tone = shade(tone, -0.18)
            if abs(dx) < 0.6 or abs(dy) < 0.6:
                tone = shade(tone, -0.1)
            if dx + dy < -6:
                tone = shade(tone, 0.25)
            c.put(x, y, tone)
    return c


def station_dish_back():
    rng = random.Random(22)
    c = Canvas()
    noisy(c, STEEL, rng, base=2)
    for i in range(16):
        c.put(i, i, STEEL[3])
        c.put(15 - i, i, STEEL[3])
    c.bevel(0, 0, 15, 15, STEEL[1], STEEL[4])
    return c


def station_feed():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, DARK[1] if (x // 2) % 2 == 0 else DARK[2])
    for x in range(16):
        c.put(x, 0, CYAN[1])
        c.put(x, 1, CYAN[2])
    return c


def rocket_body(pal=None, band=None):
    """White hull with a black roll pattern, a porthole and a flag-red stripe; tiles vertically.
    The anti-satellite missile uses a gunmetal hull (pal) with a hazard band."""
    pal = pal or WHITE
    c = Canvas()
    for y in range(16):
        for x in range(16):
            t = pal[1] if 3 <= x <= 10 else pal[2] if x > 10 else pal[0]
            c.put(x, y, t)
    for y in range(0, 4):  # roll pattern checker
        for x in range(0, 8):
            c.put(x, y, HAZ_K if (x < 4) == (y < 2) else c.get(x, y))
    for x in range(16):
        if band == "hazard":
            c.put(x, 9, HAZ_Y if (x // 2) % 2 == 0 else HAZ_K)
            c.put(x, 10, HAZ_K if (x // 2) % 2 == 0 else HAZ_Y)
        else:
            c.put(x, 9, RED[1])
            c.put(x, 10, RED[2])
    if band == "hazard":  # a warhead stencil instead of the porthole
        for y, x in ((5, 7), (5, 8), (6, 7), (6, 8), (7, 6), (7, 9)):
            c.put(x, y, RED[1])
    else:
        for y, x in ((5, 7), (5, 8), (6, 6), (6, 9), (7, 7), (7, 8)):
            c.put(x, y, DARK[3])
        c.put(7, 6, CYAN[1])
        c.put(8, 6, CYAN[2])
    for y in range(16):
        c.put(15, y, pal[3])
    return c


def rocket_nose(pal):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, pal[0] if x < 4 else pal[1] if x < 11 else pal[2])
    for x in range(16):
        c.put(x, 15, pal[3])
    return c


def rocket_fin(pal=None):
    pal = pal or RED
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, pal[1] if x < 8 else pal[2])
    for x in range(16):
        c.put(x, 0, pal[0])
    return c


def radar_base():
    """The radar's plinth: dark steel with a red warning band and a lit status strip."""
    rng = random.Random(31)
    c = Canvas()
    noisy(c, DARK, rng, base=2)
    c.bevel(0, 0, 15, 15, DARK[0], DARK[4])
    for x in range(1, 15):
        c.put(x, 12, RED[2] if (x // 2) % 2 == 0 else HAZ_K)
        c.put(x, 13, RED[3] if (x // 2) % 2 == 0 else HAZ_K)
    for x in (3, 5, 7):
        c.put(x, 3, RED[1])
    c.put(10, 3, GREEN[1])
    for x, y in ((2, 7), (12, 7)):
        rivet(c, x, y, STEEL)
    return c


def radar_array():
    """Front of the phased-array antenna: a grid of emitter cells between dark ribs."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                c.put(x, y, DARK[3])
            else:
                c.put(x, y, RED[1] if (x % 4, y % 4) == (2, 2) else STEEL[1] if (x + y) % 2 else STEEL[2])
    for x in range(16):
        c.put(x, 0, DARK[4])
        c.put(x, 15, DARK[4])
    return c


def radar_back():
    rng = random.Random(32)
    c = Canvas()
    noisy(c, DARK, rng, base=1)
    for y in (5, 10):
        for x in range(16):
            c.put(x, y, DARK[3])
    c.bevel(0, 0, 15, 15, DARK[0], DARK[4])
    return c


def rocket_engine():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            c.put(x, y, FLAME[2] if d < 3 else DARK[4] if d < 5 else DARK[2] if (x + y) % 3 else DARK[3])
    return c


# ============================================================ items

def satellite_item(kind):
    """A satellite: gold-foil bus, blue solar wings on a boom; a camera lens (survey) or a dish (uplink)."""
    c = Canvas()
    # wings
    for x0 in (0, 11):
        for y in range(4, 12):
            for x in range(x0, x0 + 5):
                cell = BLUE_PV[3] if y in (4, 11) or x in (x0, x0 + 4) else BLUE_PV[1] if (x + y) % 2 == 0 else BLUE_PV[2]
                c.put(x, y, cell)
        for x in range(x0 + 1, x0 + 4):
            c.put(x, 5, BLUE_PV[0])
    for x in range(5, 11):
        c.put(x, 7, STEEL[2])
        c.put(x, 8, STEEL[3])
    # bus
    for y in range(4, 13):
        for x in range(5, 11):
            c.put(x, y, GOLD[1] if (x + y * 3) % 5 else GOLD[0])
    for x in range(5, 11):
        c.put(x, 12, GOLD[2])
        c.put(x, 4, GOLD[0])
    for y in range(4, 13):
        c.put(10, y, GOLD[2])
    c.put(6, 6, GOLD[0])
    if kind == "guardian":
        # a small gold-rimmed red shield on top
        for y, row in enumerate(("..GGGG..", ".GRRRRG.", ".GRWRRG.", "..GRRG..", "...GG...")):
            for i, ch in enumerate(row):
                if ch != ".":
                    c.put(4 + i, y, {"G": GOLD[2], "R": RED[1], "W": WHITE[0]}[ch])
        for x in range(5, 11):  # red trim on the bus
            c.put(x, 12, RED[2])
    elif kind == "survey":
        for y, x, col in ((12, 7, DARK[3]), (12, 8, DARK[3]), (13, 7, GREEN[2]), (13, 8, GREEN[1]), (14, 7, DARK[3]), (14, 8, DARK[3])):
            c.put(x, y, col)
        c.put(8, 13, GREEN[0])
        for y in (2, 3, 4):  # thin antenna
            c.put(8, y, STEEL[1])
        c.put(8, 1, RED[1])
    else:
        # parabolic dish on top
        for x, y in ((4, 3), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 3)):
            c.put(x, y, WHITE[2])
        for x in range(5, 11):
            c.put(x, 3, WHITE[0])
        c.put(4, 2, WHITE[1])
        c.put(11, 2, WHITE[1])
        c.put(7, 1, STEEL[2])
        c.put(8, 1, STEEL[2])
        c.put(8, 0, CYAN[1])
        c.put(7, 2, STEEL[3])
        c.put(8, 2, STEEL[3])
    c.outline()
    return c


def asat_missile():
    """A slim gunmetal missile flying up and to the right: red warhead, hazard band, dark fins, flame."""
    c = Canvas()
    GUN = DARK
    for i in range(3, 13):  # body along the diagonal, 3 px wide
        x, y = i, 15 - i
        c.put(x, y, GUN[1])
        c.put(x - 1, y, GUN[2])
        c.put(x, y + 1, GUN[3])
        c.put(x - 1, y - 1, GUN[0])
    for i in (7, 8):  # hazard band
        c.put(i, 15 - i, HAZ_Y)
        c.put(i - 1, 15 - i, HAZ_K)
        c.put(i, 16 - i, HAZ_K)
    for i in range(12, 15):  # warhead
        c.put(i, 15 - i, RED[1])
        c.put(i - 1, 15 - i, RED[2])
        c.put(i, 16 - i, RED[3])
    c.put(14, 1, RED[0])
    c.put(15, 0, RED[1])
    for x, y in ((2, 11), (1, 11), (4, 14), (4, 15), (3, 10), (5, 13)):  # fins
        c.put(x, y, GUN[3])
    c.put(1, 14, FLAME[1])
    c.put(2, 13, FLAME[2])
    c.put(0, 15, FLAME[0])
    c.put(1, 15, FLAME[2])
    c.put(0, 14, FLAME[3])
    c.outline()
    return c


def rocket_fuel():
    """A red fuel canister with a flame decal and a steel cap."""
    c = Canvas()
    for y in range(4, 15):
        for x in range(4, 12):
            c.put(x, y, RED[1] if x < 9 else RED[2])
    for x in range(4, 12):
        c.put(x, 4, RED[0])
    c.put(4, 5, RED[0])
    for x in range(6, 10):  # cap
        c.put(x, 2, STEEL[1])
        c.put(x, 3, STEEL[2])
    c.put(10, 3, DARK[3])
    c.put(11, 2, DARK[2])
    for x, y, col in ((7, 7, FLAME[1]), (6, 8, FLAME[2]), (7, 8, FLAME[0]), (8, 8, FLAME[1]), (6, 9, FLAME[2]),
                      (7, 9, FLAME[1]), (8, 9, FLAME[2]), (7, 10, FLAME[3]), (8, 6, FLAME[1])):
        c.put(x, y, col)
    for x in range(4, 12):
        c.put(x, 12, HAZ_K if x % 2 else HAZ_Y)
    c.outline()
    return c


def wireless_terminal():
    """A handheld storage terminal: dark steel case, cyan item-grid screen, a stub antenna with a lit tip."""
    c = Canvas()
    for y in range(3, 15):
        for x in range(3, 12):
            c.put(x, y, DARK[2] if x < 11 else DARK[3])
    for x in range(3, 12):
        c.put(x, 3, DARK[0])
    for y in range(5, 10):
        for x in range(4, 11):
            c.put(x, y, CYAN[3] if (x + y) % 2 else rgb("#0F2C30"))
    for x, y in ((5, 6), (7, 6), (9, 6), (5, 8), (7, 8)):
        c.put(x, y, CYAN[1])
    c.put(9, 8, CYAN[0])
    for x in range(5, 10):  # keypad
        c.put(x, 11, DARK[0] if x % 2 else DARK[3])
        c.put(x, 13, DARK[0] if x % 2 == 0 else DARK[3])
    for y in range(0, 3):  # antenna
        c.put(10, y, STEEL[1])
    c.put(10, 0, CYAN[0])
    c.outline()
    return c


BLOCKS = {
    "launch_pad_top": launch_pad_top, "launch_pad_side": launch_pad_side,
    "launch_controller_top": lambda: launch_controller_top(False),
    "launch_controller_top_on": lambda: launch_controller_top(True),
    "launch_controller_side": lambda: launch_controller_side(False),
    "launch_controller_side_on": lambda: launch_controller_side(True),
    "ground_station_base": station_base, "ground_station_dish": station_dish,
    "ground_station_dish_back": station_dish_back, "ground_station_feed": station_feed,
    "rocket_body": rocket_body, "rocket_nose_survey": lambda: rocket_nose(GREEN),
    "rocket_nose_uplink": lambda: rocket_nose(CYAN), "rocket_fin": rocket_fin, "rocket_engine": rocket_engine,
    "rocket_nose_guardian": lambda: rocket_nose(GOLD),
    "rocket_body_asat": lambda: rocket_body(DARK, "hazard"), "rocket_nose_asat": lambda: rocket_nose(RED),
    "rocket_fin_asat": lambda: rocket_fin(DARK),
    "orbital_radar_base": radar_base, "orbital_radar_array": radar_array, "orbital_radar_back": radar_back,
}
ITEMS = {
    "survey_satellite": lambda: satellite_item("survey"), "uplink_satellite": lambda: satellite_item("uplink"),
    "rocket_fuel": rocket_fuel, "wireless_terminal": wireless_terminal,
    "guardian_satellite": lambda: satellite_item("guardian"), "asat_missile": asat_missile,
}


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
    print(f"wrote {len(BLOCKS) + len(ITEMS)} orbital textures")

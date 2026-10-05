#!/usr/bin/env python3
"""Textures of the planets (Moon, Mars, Io):

  block/  moon_regolith, moon_rock, helium_3_regolith, mars_sand, mars_rock, hematite_ore,
          martian_ice, io_rock, ionite_ore
  item/   helium_3, raw_hematite, ionite, helium_3_fuel_cell, thermal_lining, ion_drive, star_chart
  environment/  moon_disc (Earth orbit's Moon), phobos, deimos (Mars' moons), jupiter and europa
                (Io's sky), satellite_dot (a satellite seen from the ground at night)

Run:  python3 tools/features/planets_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily).
"""
import importlib.util
import math
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def _orbital():
    spec = importlib.util.spec_from_file_location("orbital_textures_for_planets", Path(__file__).with_name("orbital_textures.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


O = _orbital()
rgb, mix, shade, Canvas, noisy = O.rgb, O.mix, O.shade, O.Canvas, O.noisy
OUTLINE = O.OUTLINE

REGOLITH = [rgb(h) for h in ("#ABABB0", "#96969B", "#88888D", "#75757A", "#5E5E63")]
MOONROCK = [rgb(h) for h in ("#9C9CA2", "#7C7C83", "#65656C", "#505057", "#3A3A40")]
MARS = [rgb(h) for h in ("#E89A62", "#CC7444", "#B05E34", "#8E4828", "#6A341E")]
MARSROCK = [rgb(h) for h in ("#B0644A", "#944E38", "#7C402E", "#643224", "#48241A")]
ICE = [rgb(h) for h in ("#F4FAFF", "#DCEBF6", "#BCD4E8", "#98B8D4", "#7A9CBC")]
BASALT = [rgb(h) for h in ("#5A4E46", "#453C36", "#342D29", "#26211E", "#191614")]
SULFUR = [rgb(h) for h in ("#FFF7A0", "#F2DE50", "#D8BC2C", "#A88A1C")]
HE3 = [rgb(h) for h in ("#E8FFFF", "#A8F4FF", "#5CD8F0", "#2A9CC0", "#1A607A")]
VIOLET = [rgb(h) for h in ("#F6E0FF", "#D8A0FF", "#A860F0", "#7030C0", "#401878")]
HEMATITE = [rgb(h) for h in ("#C8B8B8", "#8C7070", "#5E4444", "#3E2828", "#261616")]
GOLD = [rgb(h) for h in ("#FFF4B8", "#F4D060", "#D0A030", "#9A7018", "#5E420C")]
STEEL = O.STEEL
DARK = O.DARK


def speckle(c, pal, rng, n, colors):
    for _ in range(n):
        c.put(rng.randrange(16), rng.randrange(16), colors[rng.randrange(len(colors))])


def pit(c, x, y, pal):
    """A tiny crater: dark bowl, light rim on the lower right."""
    c.put(x, y, pal[4])
    c.put(x + 1, y, pal[3])
    c.put(x, y + 1, pal[3])
    c.put(x + 1, y + 1, pal[1])
    c.put(x + 2, y + 1, pal[0])
    c.put(x + 1, y + 2, pal[0])


# ============================================================ blocks

def moon_regolith():
    rng = random.Random(3101)
    c = Canvas()
    noisy(c, REGOLITH, rng, base=1, p=0.22)
    speckle(c, REGOLITH, rng, 8, [REGOLITH[0], REGOLITH[3]])
    for x, y in ((3, 4), (10, 11), (12, 2)):
        pit(c, x, y, REGOLITH)
    return c


def moon_rock():
    rng = random.Random(3102)
    c = Canvas()
    noisy(c, MOONROCK, rng, base=2, p=0.3)
    # cracks
    for sx, sy, n in ((1, 5, 7), (9, 12, 6), (6, 1, 5)):
        x, y = sx, sy
        for _ in range(n):
            c.put(x, y, MOONROCK[4])
            c.put(x, y - 1, MOONROCK[1])
            x += 1
            y += rng.choice((-1, 0, 0, 1))
    speckle(c, MOONROCK, rng, 10, [MOONROCK[0]])
    return c


def helium_3_regolith():
    """Moon regolith shot through with glowing He-3 crystal clusters: dark-rimmed so they read from afar."""
    c = moon_regolith()
    rim = rgb("#3E4A56")
    # each cluster: a diamond of crystal with a bright core, outlined against the grey dust
    for cx, cy, big in ((4, 4, True), (11, 3, False), (12, 10, True), (5, 11, False), (8, 7, False)):
        r = 2 if big else 1
        for dy in range(-r - 1, r + 2):
            for dx in range(-r - 1, r + 2):
                d = abs(dx) + abs(dy)
                if d == r + 1:
                    c.put(cx + dx, cy + dy, rim)
                elif d <= r:
                    k = 0 if d == 0 else (1 if (dx < 0 or dy < 0) else 2)
                    if big and d == r:
                        k = 3 if (dx > 0 or dy > 0) else 2
                    c.put(cx + dx, cy + dy, HE3[k])
    for x, y in ((1, 8), (14, 14), (9, 13), (14, 6)):  # loose glints
        c.put(x, y, HE3[1])
    return c


def mars_sand():
    rng = random.Random(3201)
    c = Canvas()
    noisy(c, MARS, rng, base=2, p=0.3)
    for y in (3, 8, 13):  # wind ripples
        for x in range(16):
            yy = y + (1 if (x // 4) % 2 else 0)
            c.put(x, yy, MARS[1])
            c.put(x, yy + 1, MARS[3])
    speckle(c, MARS, rng, 8, [MARS[4], MARS[0]])
    return c


def mars_rock():
    rng = random.Random(3202)
    c = Canvas()
    noisy(c, MARSROCK, rng, base=2, p=0.3)
    for y in (2, 6, 11, 14):  # strata
        for x in range(16):
            if rng.random() < 0.85:
                c.put(x, y, MARSROCK[3 if y % 2 else 1])
    speckle(c, MARSROCK, rng, 10, [MARSROCK[4], MARSROCK[0]])
    return c


HEMATITE_METAL = [rgb(h) for h in ("#F2EEF4", "#B4ACB8", "#76707E", "#463E4C", "#221C26")]
HEMATITE_STREAK = rgb("#C42A1C")


def hematite_ore():
    """Mars rock with irregular metallic steel-grey hematite lumps, each in a dark rust-stained halo."""
    rng = random.Random(3203)
    c = mars_rock()
    stain = [rgb("#5C2418"), rgb("#43180F")]
    lumps = (((2, 2), (3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (5, 3), (3, 4), (4, 4)),
             ((11, 1), (12, 1), (11, 2), (12, 2), (13, 2), (12, 3)),
             ((7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (7, 9), (8, 9), (9, 9), (8, 10)),
             ((2, 11), (3, 11), (1, 12), (2, 12), (3, 12), (2, 13)),
             ((12, 11), (13, 11), (11, 12), (12, 12), (13, 12), (14, 12), (12, 13), (13, 13)))
    for lump in lumps:
        cs = set(lump)
        for x, y in lump:
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1), (x + 1, y + 1), (x - 1, y - 1)):
                if (nx, ny) not in cs:
                    c.put(nx, ny, stain[0] if rng.random() < 0.6 else stain[1])
        xs, ys = [x for x, _ in lump], [y for _, y in lump]
        cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
        for x, y in lump:
            lit = (cx - x) + (cy - y)
            k = 1 if lit > 0.8 else 2 if lit > -0.6 else 3
            c.put(x, y, HEMATITE_METAL[k])
        hx, hy = min(lump, key=lambda q: q[0] + q[1])
        c.put(hx, hy, HEMATITE_METAL[0])
        lx, ly = max(lump, key=lambda q: q[0] + q[1])
        c.put(lx, ly, HEMATITE_METAL[4])
    return c


def martian_ice():
    rng = random.Random(3204)
    c = Canvas()
    noisy(c, ICE, rng, base=1, p=0.25)
    for sx, sy in ((1, 2), (6, 7), (10, 12)):  # rusty dust streaks and cracks
        for i in range(6):
            c.put(sx + i, sy + i // 2, MARS[1] if i % 3 else MARS[2])
    for i in range(10):
        c.put(3 + i, 14 - i, ICE[0])
    speckle(c, ICE, rng, 6, [ICE[3]])
    return c


def io_rock():
    rng = random.Random(3301)
    c = Canvas()
    noisy(c, BASALT, rng, base=2, p=0.35)
    for _ in range(7):  # vesicles
        x, y = rng.randrange(15), rng.randrange(15)
        c.put(x, y, BASALT[4])
        c.put(x + 1, y + 1, BASALT[0])
    speckle(c, BASALT, rng, 6, [SULFUR[1], SULFUR[2]])
    return c


def ionite_ore():
    rng = random.Random(3302)
    c = io_rock()
    for x, y in ((3, 2), (10, 4), (6, 9), (12, 11), (2, 12)):
        c.put(x, y, VIOLET[0])
        c.put(x, y + 1, VIOLET[1])
        c.put(x + 1, y + 1, VIOLET[2])
        c.put(x, y + 2, VIOLET[3])
        c.put(x - 1, y + 1, VIOLET[2])
        if rng.random() < 0.5:
            c.put(x + 1, y, VIOLET[1])
    return c


# ============================================================ items

def helium_3():
    """A sealed glass ampoule of glowing Helium-3."""
    c = Canvas()
    for y in range(4, 14):
        for x in range(5, 11):
            edge = x in (5, 10) or y == 13
            c.put(x, y, HE3[3] if edge else HE3[1 if (x + y) % 3 else 0])
    for x in range(6, 10):
        c.put(x, 3, STEEL[1])
        c.put(x, 2, STEEL[2])
    c.put(7, 1, STEEL[3])
    c.put(8, 1, STEEL[3])
    c.put(6, 6, (255, 255, 255, 255))
    c.put(6, 7, HE3[0])
    c.outline()
    return c


def raw_hematite():
    c = Canvas()
    pts = [(2, 6), (4, 3), (8, 2), (12, 3), (14, 7), (13, 12), (9, 14), (4, 13), (2, 10)]
    for y in range(16):
        for x in range(16):
            inside = sum(1 for i in range(len(pts)) if _cross(pts[i], pts[(i + 1) % len(pts)], (x + 0.5, y + 0.5)) > 0)
            if inside == len(pts):
                k = 1 + ((x * 7 + y * 3) % 5 == 0) + (y > 8) + (x > 9)
                c.put(x, y, HEMATITE[min(4, k)])
    c.put(6, 5, HEMATITE[0])
    c.put(7, 5, rgb("#D07060"))
    c.put(9, 8, rgb("#A04038"))
    c.outline()
    return c


def _cross(a, b, p):
    return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])


def ionite():
    """A cluster of violet Ionite crystals."""
    c = Canvas()
    for (x0, h, w) in ((4, 8, 2), (7, 11, 3), (11, 7, 2)):
        for y in range(14 - h, 14):
            for x in range(x0, x0 + w):
                c.put(x, y, VIOLET[1 if x == x0 else 2])
        c.put(x0, 13 - h, VIOLET[0])
        for x in range(x0, x0 + w):
            c.put(x, 13, VIOLET[3])
    c.put(8, 5, (255, 255, 255, 255))
    c.outline()
    return c


def helium_3_fuel_cell():
    c = Canvas()
    for y in range(3, 14):
        for x in range(4, 12):
            c.put(x, y, STEEL[1] if x < 7 else STEEL[2] if x < 10 else STEEL[3])
    for y in (6, 7, 8, 9):
        for x in range(4, 12):
            c.put(x, y, HE3[1] if y in (7, 8) else HE3[3])
    for x in range(5, 11):
        c.put(x, 2, DARK[1])
        c.put(x, 14, DARK[2])
    c.put(7, 1, O.HAZ_Y)
    c.put(8, 1, O.HAZ_K)
    c.put(6, 7, (255, 255, 255, 255))
    c.outline()
    return c


def thermal_lining():
    """A folded, quilted gold-foil blanket."""
    c = Canvas()
    for y in range(4, 13):
        for x in range(2, 14):
            q = (x - 2) % 4 == 0 or (y - 4) % 3 == 0
            c.put(x, y, GOLD[3] if q else GOLD[1 if (x + y) % 2 else 2])
    for x in range(2, 14):
        c.put(x, 4, GOLD[0])
        c.put(x, 12, GOLD[4])
    for y in range(5, 12):  # the fold
        c.put(13, y, GOLD[4])
    c.outline()
    return c


def ion_drive():
    c = Canvas()
    for y in range(2, 9):
        for x in range(4, 12):
            c.put(x, y, DARK[1] if x < 8 else DARK[2])
    for x in range(5, 11):
        c.put(x, 2, STEEL[0])
    for y in range(9, 14):  # the bell
        w = y - 7
        for x in range(8 - w, 8 + w):
            c.put(x, y, STEEL[2] if abs(x - 7.5) < w - 1 else STEEL[3])
    for x in range(6, 10):
        c.put(x, 13, rgb("#6FB8FF"))
        c.put(x, 14, rgb("#B8E0FF"))
    c.put(7, 5, VIOLET[1])
    c.put(8, 5, VIOLET[2])
    c.outline()
    return c


def star_chart():
    c = Canvas()
    navy = [rgb(h) for h in ("#3A4A88", "#26346A", "#18224C")]
    for y in range(2, 14):
        for x in range(1, 15):
            c.put(x, y, navy[1] if (x + y) % 5 else navy[2])
    for y in range(2, 14):  # rolled edges
        c.put(1, y, rgb("#D8C8A0"))
        c.put(14, y, rgb("#B8A880"))
    c.put(7, 7, GOLD[1])  # the sun
    c.put(8, 7, GOLD[0])
    c.put(7, 8, GOLD[2])
    c.put(8, 8, GOLD[1])
    for a in range(0, 360, 30):  # an orbit
        x = 7.5 + math.cos(math.radians(a)) * 4.5
        y = 7.5 + math.sin(math.radians(a)) * 3.5
        c.put(int(round(x)), int(round(y)), navy[0])
    c.put(12, 6, rgb("#3E8EE8"))
    c.put(4, 10, MARS[1])
    for x, y in ((3, 3), (12, 12), (10, 3)):
        c.put(x, y, (255, 255, 255, 255))
    c.outline()
    return c


# ============================================================ sky textures (bigger, PIL)

def _disc(size, colour_at, seed):
    """A lit square (sky bodies are square, like vanilla's sun and moon): colour_at(u, v, rng) gives the
    surface colour at (u, v) in -1..1; darkened towards the edges."""
    from PIL import Image
    rng = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    r = size / 2 - 0.5
    for y in range(size):
        for x in range(size):
            u, v = (x - size / 2 + 0.5) / r, (y - size / 2 + 0.5) / r
            d = max(abs(u), abs(v))
            if d > 1:
                continue
            col = colour_at(u, v, rng)
            limb = 0.7 + 0.3 * (1 - d ** 6)
            a = 255
            img.putpixel((x, y), (int(col[0] * limb), int(col[1] * limb), int(col[2] * limb), max(0, a)))
    return img


def _blobs(seed, n, spread=1.0):
    rng = random.Random(seed)
    return [(rng.uniform(-spread, spread), rng.uniform(-spread, spread), rng.uniform(0.08, 0.35)) for _ in range(n)]


def moon_disc():
    maria = _blobs(41, 9, 0.7)
    craters = _blobs(42, 14, 0.9)

    def at(u, v, rng):
        base = 190
        for bx, by, br in maria:
            if (u - bx) ** 2 + (v - by) ** 2 < br * br:
                base = 120
        for bx, by, br in craters:
            d = math.sqrt((u - bx) ** 2 + (v - by) ** 2)
            if abs(d - br * 0.4) < 0.03:
                base += 35
        base += rng.randint(-8, 8)
        return (base, base, base + 4)
    return _disc(64, at, 43)


def lumpy(size, seed, pal):
    """An irregular little moon (Phobos, Deimos): a square with blocky bites out of its edges, craters and a grooved face."""
    from PIL import Image
    rng = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bumps = [rng.uniform(0.78, 1.0) for _ in range(12)]
    craters = _blobs(seed + 1, 6, 0.6)
    for y in range(size):
        for x in range(size):
            u, v = (x - size / 2 + 0.5) / (size / 2), (y - size / 2 + 0.5) / (size / 2)
            # a square with blocky bites out of its edges (an irregular little moon, Minecraft-style)
            i = int((math.atan2(v, u) / (2 * math.pi) * 12) % 12)
            edge = 1.0 if bumps[i] > 0.86 else 0.82
            d = max(abs(u), abs(v))
            if d > edge * 0.95:
                continue
            k = 1
            for bx, by, br in craters:
                cd = math.sqrt((u - bx) ** 2 + (v - by) ** 2)
                if cd < br * 0.6:
                    k = 3
                elif cd < br * 0.75:
                    k = 0
            col = pal[min(len(pal) - 1, k + (1 if u + v > 0.6 else 0))]
            shade_f = 0.6 + 0.4 * max(0.0, 1 - d / edge)
            img.putpixel((x, y), (int(col[0] * shade_f), int(col[1] * shade_f), int(col[2] * shade_f), 255))
    return img


def jupiter():
    rng = random.Random(51)
    bands = []
    y = -1.0
    tones = [(222, 200, 170), (190, 140, 100), (236, 222, 196), (170, 116, 80), (214, 184, 150), (150, 100, 72)]
    while y < 1.0:
        h = rng.uniform(0.04, 0.11)
        bands.append((y, y + h, tones[len(bands) % len(tones)]))
        y += h

    def at(u, v, r):
        vv = v + 0.012 * math.sin(u * 9 + v * 30) + 0.006 * math.sin(u * 23)
        col = bands[-1][2]
        for b0, b1, c in bands:
            if b0 <= vv < b1:
                t = (vv - b0) / (b1 - b0)
                col = tuple(int(ch * (0.9 + 0.1 * math.sin(t * math.pi))) for ch in c)
                break
        # the Great Red Spot
        gx, gy = 0.28, 0.34
        e = ((u - gx) / 0.22) ** 2 + ((v - gy) / 0.11) ** 2
        if e < 1:
            col = mix((*col, 255), (190, 70, 44, 255), min(1.0, 1.6 * (1 - e)))[:3]
        elif e < 1.25:
            col = mix((*col, 255), (240, 226, 200, 255), 0.6)[:3]
        n = r.randint(-6, 6)
        return (max(0, min(255, col[0] + n)), max(0, min(255, col[1] + n)), max(0, min(255, col[2] + n)))
    return _disc(256, at, 52)


def europa():
    lines = [(random.Random(61 + i).uniform(-1, 1), random.Random(71 + i).uniform(0.3, 3)) for i in range(8)]

    def at(u, v, r):
        base = [236, 230, 220]
        for c0, slope in lines:
            if abs(v - (c0 + slope * (u - 0.1) * 0.3)) < 0.03:
                base = [170, 120, 90]
        n = r.randint(-5, 5)
        return (base[0] + n, base[1] + n, base[2] + n)
    return _disc(32, at, 62)


def satellite_dot():
    from PIL import Image
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            d = max(abs(x - 3.5), abs(y - 3.5))
            a = max(0.0, 1 - d / 3.8)
            img.putpixel((x, y), (255, 255, 255, int(255 * min(1.0, a * 1.6))))
    return img


class _Img:
    def __init__(self, fn):
        self.fn = fn

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.fn().save(path)


BLOCKS = {"moon_regolith": moon_regolith, "moon_rock": moon_rock, "helium_3_regolith": helium_3_regolith,
          "mars_sand": mars_sand, "mars_rock": mars_rock, "hematite_ore": hematite_ore, "martian_ice": martian_ice,
          "io_rock": io_rock, "ionite_ore": ionite_ore}
ITEMS = {"helium_3": helium_3, "raw_hematite": raw_hematite, "ionite": ionite, "helium_3_fuel_cell": helium_3_fuel_cell,
         "thermal_lining": thermal_lining, "ion_drive": ion_drive, "star_chart": star_chart}
ENVIRONMENT = {"moon_disc": moon_disc, "phobos": lambda: lumpy(32, 81, [(170, 150, 130), (140, 120, 104), (110, 94, 82), (80, 68, 60)]),
               "deimos": lambda: lumpy(24, 91, [(190, 172, 150), (160, 144, 126), (128, 114, 100), (96, 86, 76)]),
               "jupiter": jupiter, "europa": europa, "satellite_dot": satellite_dot}


def paths():
    for name, fn in BLOCKS.items():
        yield TEX / "block" / f"{name}.png", fn
    for name, fn in ITEMS.items():
        yield TEX / "item" / f"{name}.png", fn
    for name, fn in ENVIRONMENT.items():
        yield TEX / "environment" / f"{name}.png", (lambda f=fn: _Img(f))


def draw(only_missing):
    for path, fn in paths():
        if only_missing and path.exists():
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        fn().save(path)


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    draw(only_missing=False)
    print(f"wrote {len(BLOCKS) + len(ITEMS) + len(ENVIRONMENT)} planet textures")

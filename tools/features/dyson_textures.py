#!/usr/bin/env python3
"""Textures of the Dyson Sphere (16x16): the Mass Driver breech and rails, the Dyson Receiver, its
rectenna arrays and sun-tracking dish, the Dyson Monitor, Stellar Alloy and the Solar Collector.

Run:  python3 tools/features/dyson_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily, so resources still generate without it
once the PNGs exist).
"""
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


# Quantum-age palette: dark iridescent hull, stellar gold, cyan coils.
HULL = [rgb(h) for h in ("#9AA4BC", "#6C7690", "#4C5470", "#343A52", "#20243A")]
GOLD = [rgb(h) for h in ("#FFF4B8", "#FFD760", "#E8A830", "#B06E18", "#6E3E0C")]
CYAN = [rgb(h) for h in ("#E0FFFF", "#7EF6FF", "#28C8E8", "#1478A0", "#0A3C58")]
PV = [rgb(h) for h in ("#5A6CC8", "#3848A0", "#242E78", "#161C4C", "#0C1030")]
COPPER = [rgb(h) for h in ("#FFC8A0", "#E88A50", "#B45A2A", "#7A3416")]
OUT = rgb("#0E0C16")
SCREEN = [rgb(h) for h in ("#B8FFF6", "#40E0D8", "#138C98", "#0A3A48", "#041820")]


class Canvas:
    def __init__(self, fill=None):
        self.px = [[fill] * 16 for _ in range(16)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16 and c is not None:
            self.px[y][x] = c

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

    def save(self, path):
        from PIL import Image
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)


def noisy(c, pal, rng, x0=0, y0=0, x1=15, y1=15, base=2, p=0.16):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r = rng.random()
            c.put(x, y, pal[base - 1] if r < p / 2 else pal[base + 1] if r < p else pal[base])


def plate(seed, pal=HULL):
    """A dark hull plate with a bevel and corner bolts."""
    rng = random.Random(seed)
    c = Canvas()
    noisy(c, pal, rng)
    c.bevel(0, 0, 15, 15, pal[1], pal[4])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.put(x, y, pal[0])
        c.put(x + 1, y + 1, pal[4])
    return c


# ============================================================ blocks

def mass_driver_side():
    """The breech: a hull block with a glowing cyan capacitor window and hazard trim."""
    c = plate(1)
    c.rect(4, 4, 11, 11, OUT)
    for y in range(5, 11):
        for x in range(5, 11):
            t = (y - 5) / 5
            c.put(x, y, mix(CYAN[1], CYAN[3], t))
    for x in range(5, 11):
        c.put(x, 7, CYAN[0])
    for x in range(1, 15):
        c.put(x, 14, GOLD[2] if (x // 2) % 2 == 0 else OUT)
    return c


def mass_driver_top():
    """The top of the breech: the bore the collectors leave through, ringed by coils."""
    c = plate(2)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 3.2:
                c.put(x, y, mix(CYAN[2], OUT, min(1, (3.2 - d) / 2)))
            elif d < 4.6:
                c.put(x, y, COPPER[1] if (x + y) % 2 == 0 else COPPER[2])
            elif d < 5.2:
                c.put(x, y, OUT)
    return c


def mass_driver_bottom():
    return plate(3)


def mass_driver_rail():
    """Guide posts and coil housings (dark hull)."""
    c = plate(4)
    for y in range(16):
        c.put(7, y, HULL[1])
        c.put(8, y, HULL[3])
    return c


def mass_driver_coil():
    """The coil windings: copper wire with cyan insulation stripes."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            band = y % 4
            c.put(x, y, COPPER[0] if band == 0 else COPPER[1] if band == 1 else COPPER[2] if band == 2 else CYAN[3])
    for x in range(0, 16, 5):
        for y in range(16):
            c.put(x, y, OUT)
    return c


def receiver_side():
    c = plate(5)
    for x in range(2, 14):
        c.put(x, 5, GOLD[2])
        c.put(x, 6, GOLD[3])
    for x in (4, 8, 12):
        c.rect(x - 1, 9, x, 11, CYAN[2])
        c.put(x - 1, 9, CYAN[0])
    return c


def receiver_top():
    """The pedestal top: a gold emitter plate with a glowing core."""
    c = plate(6)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 2:
                c.put(x, y, GOLD[0])
            elif d < 4:
                c.put(x, y, GOLD[1] if (x + y) % 2 == 0 else GOLD[2])
            elif d < 5:
                c.put(x, y, OUT)
    return c


def array_top():
    """Rectenna: a grid of dipoles over a deep blue ground plane."""
    rng = random.Random(7)
    c = Canvas()
    noisy(c, PV, rng, base=2, p=0.1)
    for i in range(0, 16, 4):
        for j in range(16):
            c.put(i, j, HULL[2])
            c.put(j, i, HULL[2])
    for gx in range(2, 16, 4):
        for gy in range(2, 16, 4):
            c.put(gx - 1, gy, GOLD[1])
            c.put(gx, gy, GOLD[0])
            c.put(gx + 1, gy, GOLD[2])
    c.bevel(0, 0, 15, 15, HULL[1], HULL[4])
    return c


def array_side():
    c = plate(8)
    for x in range(1, 15):
        c.put(x, 10, GOLD[2])
    return c


def dish():
    """The dish's mirror face: gold facets."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            fx, fy = x // 4, y // 4
            t = ((fx * 3 + fy * 5) % 4) / 4
            c.put(x, y, mix(GOLD[0], GOLD[2], t))
            if x % 4 == 0 or y % 4 == 0:
                c.put(x, y, GOLD[3])
    return c


def dish_back():
    return plate(9)


def dish_feed():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, CYAN[1] if (x + y) % 3 else CYAN[0])
    c.bevel(0, 0, 15, 15, CYAN[0], CYAN[3])
    return c


def monitor_front():
    """A console screen showing a tiny sphere, with a keyboard strip below."""
    c = plate(10)
    c.rect(2, 2, 13, 9, OUT)
    c.rect(3, 3, 12, 8, SCREEN[4])
    for y in range(3, 9):
        for x in range(3, 13):
            d = ((x - 7.5) ** 2 + ((y - 5.5) * 1.3) ** 2) ** 0.5
            if d < 1.3:
                c.put(x, y, GOLD[0])
            elif 2.2 < d < 3.2:
                c.put(x, y, SCREEN[1] if (x + y) % 2 else SCREEN[2])
    c.put(3, 8, SCREEN[1])
    c.put(4, 8, SCREEN[1])
    c.put(5, 8, SCREEN[2])
    for x in range(3, 13, 2):
        c.put(x, 11, HULL[0])
        c.put(x, 12, HULL[1])
    c.put(12, 12, rgb("#60E070"))
    return c


def monitor_side():
    c = plate(11)
    for y in range(3, 13, 3):
        for x in range(4, 12):
            c.put(x, y, HULL[4])
    return c


def monitor_top():
    """The projector lens on top."""
    c = plate(12)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2:
                c.put(x, y, CYAN[0])
            elif d < 3.5:
                c.put(x, y, CYAN[2])
            elif d < 4.3:
                c.put(x, y, OUT)
    return c


# ============================================================ items

def stellar_alloy_ingot():
    """An ingot of white-gold stellar alloy with a sunny sheen."""
    c = Canvas()
    shape = [(3, 11, 12, 11), (2, 10, 13, 10), (2, 9, 13, 9), (3, 8, 13, 8), (4, 7, 12, 7), (5, 6, 11, 6), (6, 5, 10, 5)]
    for x0, y, x1, _ in shape:
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            c.put(x, y, mix(GOLD[0], GOLD[2], t * 0.8 + (y - 5) / 20))
    for x in range(6, 11):
        c.put(x, 5, GOLD[0])
    c.put(7, 6, rgb("#FFFFFF"))
    c.put(8, 6, rgb("#FFFFFF"))
    c.put(12, 8, CYAN[1])
    # outline
    edge = []
    for y in range(16):
        for x in range(16):
            if c.px[y][x] is None and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and c.px[y + dy][x + dx] not in (None, OUT)
                                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge:
        c.put(x, y, GOLD[4])
    return c


def dyson_collector():
    """A Solar Collector: a hexagonal photovoltaic sail on a gold frame with a glinting core."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            inside = dy <= 6.6 and dx <= 7.2 - dy * 0.5
            rim = inside and not (dy <= 5.4 and dx <= 6.0 - dy * 0.5)
            if rim:
                c.put(x, y, GOLD[1] if y < 8 else GOLD[2])
            elif inside:
                cell = ((x // 2) + (y // 2)) % 2
                c.put(x, y, PV[1] if cell else PV[2])
                if x % 4 == 0 or y % 4 == 0:
                    c.put(x, y, PV[3])
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.put(x, y, GOLD[0])
    c.put(5, 4, rgb("#C8E0FF"))
    c.put(4, 5, rgb("#C8E0FF"))
    return c


BLOCKS = {
    "mass_driver_side": mass_driver_side, "mass_driver_top": mass_driver_top, "mass_driver_bottom": mass_driver_bottom,
    "mass_driver_rail": mass_driver_rail, "mass_driver_coil": mass_driver_coil,
    "dyson_receiver_side": receiver_side, "dyson_receiver_top": receiver_top,
    "dyson_receiver_array_top": array_top, "dyson_receiver_array_side": array_side,
    "dyson_dish": dish, "dyson_dish_back": dish_back, "dyson_dish_feed": dish_feed,
    "dyson_monitor_front": monitor_front, "dyson_monitor_side": monitor_side, "dyson_monitor_top": monitor_top,
}
ITEMS = {"stellar_alloy_ingot": stellar_alloy_ingot, "dyson_collector": dyson_collector}


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
    print(f"wrote {len(BLOCKS) + len(ITEMS)} dyson textures")

#!/usr/bin/env python3
"""Textures of the ships (tools/features/ships.py): the Bronze Cog, the Motor Ship and the Orbital
Shuttle. 16x16 block textures (textures/block/ship_*.png, used by the entity part models) and the
three item icons (textures/item/{bronze_cog,motor_ship,shuttle}.png).

Run:  python3 tools/features/ships_textures.py          (redraw all)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily).
Every texture gets its own RNG seeded from its name, so reruns are identical.
"""
import random
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/factoryascent/textures"


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (c[3],)


class Canvas:
    def __init__(self, name, fill=None):
        self.name = name
        self.rng = random.Random(zlib.crc32(name.encode()))
        self.px = [[fill] * 16 for _ in range(16)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16 and c is not None:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y % 16][x % 16]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def noise(self, amount=0.08, x0=0, y0=0, x1=15, y1=15):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                c = self.px[y][x]
                if c is not None and c[3] > 0:
                    self.px[y][x] = shade(c, 1 + self.rng.uniform(-amount, amount))

    def image(self):
        from PIL import Image
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        return img


# ============================================================ wood (Bronze Cog)

OAK = rgb("#8a6238")
OAK_DARK = rgb("#5a3d22")
TAR = rgb("#3a2716")
BRONZE = rgb("#c08a3e")
BRONZE_HI = rgb("#e8b868")
BRONZE_LO = rgb("#7a5424")


def planks(name, base, seam, horizontal=True, nails=None, rows=4):
    c = Canvas(name, base)
    step = 16 // rows
    for r in range(rows):
        tone = 1 + c.rng.uniform(-0.09, 0.09)
        for i in range(16):
            for j in range(step):
                x, y = (i, r * step + j) if horizontal else (r * step + j, i)
                c.put(x, y, shade(base, tone))
        # grain streaks
        for _ in range(3):
            i0 = c.rng.randrange(16)
            j = c.rng.randrange(1, step)
            for i in range(i0, i0 + c.rng.randrange(3, 7)):
                x, y = (i % 16, r * step + j) if horizontal else (r * step + j, i % 16)
                c.put(x, y, shade(base, tone * 0.86))
        # seam line
        for i in range(16):
            x, y = (i, r * step) if horizontal else (r * step, i)
            c.put(x, y, seam)
        # butt joint
        b = (c.rng.randrange(16) + r * 5) % 16
        for j in range(step):
            x, y = (b, r * step + j) if horizontal else (r * step + j, b)
            c.put(x, y, seam)
        if nails:
            for k in (2, 10):
                x, y = ((k + r * 3) % 16, r * step + step // 2) if horizontal else (r * step + step // 2, (k + r * 3) % 16)
                c.put(x, y, nails)
    c.noise(0.04)
    return c


def cog_planks():
    return planks("ship_cog_planks", OAK, TAR, nails=BRONZE, rows=4)


def cog_deck():
    return planks("ship_cog_deck", rgb("#b08850"), rgb("#6e5030"), horizontal=False, rows=4)


def cog_dark():
    return planks("ship_cog_dark", OAK_DARK, TAR, rows=2)


def bronze():
    c = Canvas("ship_bronze", BRONZE)
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(BRONZE_HI, BRONZE_LO, (x + y) / 30))
    c.rect(0, 0, 15, 0, BRONZE_HI)
    c.rect(0, 15, 15, 15, BRONZE_LO)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12), (7, 7)):
        c.put(x, y, BRONZE_HI)
        c.put(x + 1, y + 1, BRONZE_LO)
    c.noise(0.05)
    return c


def sail():
    cream = rgb("#efe4c6")
    red = rgb("#b8352c")
    c = Canvas("ship_sail", cream)
    for x in range(16):
        stripe = (x // 4) % 2 == 1
        for y in range(16):
            col = red if stripe else cream
            c.put(x, y, col)
        if x % 4 == 0:
            for y in range(16):
                c.put(x, y, shade(c.get(x, y), 0.88))  # sewn seam
    for y in (5, 11):  # reef points
        for x in range(1, 16, 3):
            c.put(x, y, rgb("#8c7a58"))
    c.noise(0.035)
    return c


def rope():
    c = Canvas("ship_rope", rgb("#b89968"))
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                c.put(x, y, rgb("#7d6440"))
            elif (x + y) % 4 == 1:
                c.put(x, y, rgb("#d8bf8e"))
    return c


def flag():
    c = Canvas("ship_flag", rgb("#b8352c"))
    c.rect(0, 6, 15, 9, rgb("#e8b868"))
    c.rect(6, 0, 9, 15, rgb("#e8b868"))
    c.noise(0.04)
    return c


def lantern():
    c = Canvas("ship_lantern", rgb("#ffd66a"))
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            c.put(x, y, mix(rgb("#fff4c0"), rgb("#f0a030"), min(1, d / 12)))
    return c


def iron_dark():
    c = Canvas("ship_iron", rgb("#3b3b40"))
    c.rect(0, 0, 15, 0, rgb("#5a5a62"))
    c.rect(0, 15, 15, 15, rgb("#26262a"))
    c.noise(0.07)
    return c


# ============================================================ steel (Motor Ship)

NAVY = rgb("#253a5c")
NAVY_HI = rgb("#35507a")


def steel_plates(name, base, rivet_hi, rivet_lo, seam):
    c = Canvas(name, base)
    for y in range(16):
        for x in range(16):
            c.put(x, y, shade(base, 1 + 0.05 * ((x // 8 + y // 8) % 2)))
    for i in range(16):
        c.put(i, 0, seam)
        c.put(i, 8, seam)
        c.put(0, i, seam)
    c.put(8, 4, seam)
    for x in range(2, 16, 4):
        c.put(x, 2, rivet_hi)
        c.put(x, 10, rivet_hi)
        c.put(x, 3, rivet_lo)
        c.put(x, 11, rivet_lo)
    c.noise(0.04)
    return c


def hull_steel():
    return steel_plates("ship_hull_steel", NAVY, NAVY_HI, rgb("#18263c"), rgb("#1a2a44"))


def hull_red():
    c = steel_plates("ship_hull_red", rgb("#9c2f28"), rgb("#bd463c"), rgb("#6e1f1a"), rgb("#7a221d"))
    for y in range(12, 16):  # water stain
        for x in range(16):
            c.put(x, y, mix(c.get(x, y), rgb("#4a5a40"), 0.25 * (y - 11) / 4))
    return c


def boot_top():
    """White boot-top band over the waterline, black edge."""
    c = Canvas("ship_boot_top", rgb("#e6e8ea"))
    c.rect(0, 0, 15, 1, rgb("#202226"))
    c.rect(0, 14, 15, 15, rgb("#202226"))
    c.noise(0.03)
    return c


def steel_deck():
    c = Canvas("ship_steel_deck", rgb("#7c8288"))
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 5 == 0 and (x // 4 + y // 4) % 2 == 0:
                c.put(x, y, rgb("#9aa0a6"))
            elif (2 * x - y) % 5 == 0 and (x // 4 + y // 4) % 2 == 1:
                c.put(x, y, rgb("#9aa0a6"))
    c.rect(0, 0, 15, 0, rgb("#5c6268"))
    c.noise(0.04)
    return c


def white_paint():
    c = Canvas("ship_white", rgb("#e8eaec"))
    c.rect(0, 15, 15, 15, rgb("#b8bcc0"))
    c.rect(0, 0, 15, 0, rgb("#f8f9fa"))
    for x in range(0, 16, 8):
        for y in range(16):
            c.put(x, y, rgb("#d2d6da"))
    c.noise(0.02)
    return c


def windows():
    c = white_paint()
    c.name = "ship_windows"
    for x0 in (1, 9):
        c.rect(x0, 4, x0 + 5, 10, rgb("#2a3440"))
        c.rect(x0 + 1, 5, x0 + 4, 9, rgb("#6fa8d8"))
        c.put(x0 + 1, 5, rgb("#c8e4ff"))
        c.put(x0 + 2, 5, rgb("#c8e4ff"))
    return c


def funnel():
    c = Canvas("ship_funnel", rgb("#c8a032"))
    for y in range(16):
        for x in range(16):
            col = rgb("#1c1c1e") if y < 4 else rgb("#b8352c") if 6 <= y < 9 else rgb("#e0b840")
            c.put(x, y, shade(col, 1 - 0.12 * abs(x - 7.5) / 7.5))
    c.noise(0.03)
    return c


def propeller():
    c = Canvas("ship_propeller", rgb("#d9a650"))
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(rgb("#ffd890"), rgb("#9a6a28"), (x * 0.7 + y) / 26))
    c.noise(0.05)
    return c


def lamp():
    c = Canvas("ship_lamp", rgb("#fffbe8"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, mix(rgb("#ffffff"), rgb("#ffe89a"), min(1, d / 11)))
    return c


def lamp_off():
    c = Canvas("ship_lamp_off", rgb("#9a9a90"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, mix(rgb("#c8c8bc"), rgb("#6a6a60"), min(1, d / 11)))
    return c


def beam():
    c = Canvas("ship_beam")
    for y in range(16):
        for x in range(16):
            c.put(x, y, rgb("#fff3b0", 70 - y * 3))
    return c


def glass():
    c = Canvas("ship_glass", rgb("#9fd0f0", 110))
    for i in range(16):
        c.put(i, 15 - i, rgb("#e8f6ff", 170))
        if i < 15:
            c.put(i + 1, 15 - i, rgb("#e8f6ff", 140))
    c.rect(0, 0, 15, 0, rgb("#3a4a58", 230))
    c.rect(0, 15, 15, 15, rgb("#3a4a58", 230))
    c.rect(0, 0, 0, 15, rgb("#3a4a58", 230))
    c.rect(15, 0, 15, 15, rgb("#3a4a58", 230))
    return c


def rubber():
    c = Canvas("ship_rubber", rgb("#2a2a2c"))
    c.noise(0.08)
    return c


# ============================================================ Orbital Shuttle

def tiles(name, base, grout, hi):
    c = Canvas(name, base)
    for y in range(16):
        for x in range(16):
            off = 2 if (y // 4) % 2 else 0
            if y % 4 == 0 or (x + off) % 4 == 0:
                c.put(x, y, grout)
            elif (x + off) % 4 == 1 and y % 4 == 1:
                c.put(x, y, hi)
    c.noise(0.03)
    return c


def tile_white():
    return tiles("ship_tile_white", rgb("#eceef0"), rgb("#c4c8cc"), rgb("#ffffff"))


def tile_black():
    c = tiles("ship_tile_black", rgb("#26282c"), rgb("#141516"), rgb("#3a3d42"))
    for _ in range(10):
        x, y = c.rng.randrange(16), c.rng.randrange(16)
        if c.get(x, y) != rgb("#141516"):
            c.put(x, y, rgb("#30333a"))
    return c


def gold_foil():
    c = Canvas("ship_gold_foil", rgb("#d8a830"))
    for y in range(16):
        for x in range(16):
            v = (x * 3 + y * 5 + c.rng.randrange(4)) % 7
            c.put(x, y, [rgb("#f4d070"), rgb("#d8a830"), rgb("#b8841c"), rgb("#e8c050"), rgb("#c89428"),
                         rgb("#f8e090"), rgb("#a87418")][v])
    return c


def engine():
    c = Canvas("ship_engine", rgb("#4a4c52"))
    for y in range(16):
        for x in range(16):
            c.put(x, y, shade(rgb("#5a5c62"), 0.75 + 0.3 * ((y % 4) / 4)))
    c.noise(0.04)
    return c


def engine_inner():
    c = Canvas("ship_engine_inner", rgb("#ff9a30"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, mix(rgb("#fff0b0"), rgb("#e05a10"), min(1, d / 10)))
    return c


def flame():
    c = Canvas("ship_flame")
    for y in range(16):
        for x in range(16):
            t = y / 15
            col = mix(rgb("#fff8d8"), rgb("#ff7a18"), min(1, t * 1.4))
            col = mix(col, rgb("#c83a10"), max(0, t - 0.6) * 2)
            flick = 1 if (x + y * 3) % 5 else 0.85
            c.put(x, y, (col[0], col[1], col[2], round((210 - 150 * t) * flick)))
    return c


def blue_flame():
    c = Canvas("ship_flame_blue")
    for y in range(16):
        for x in range(16):
            t = y / 15
            col = mix(rgb("#e8f4ff"), rgb("#3a78ff"), min(1, t * 1.5))
            c.put(x, y, (col[0], col[1], col[2], round(200 - 150 * t)))
    return c


def light(name, color):
    c = Canvas(name, rgb(color))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, mix(rgb("#ffffff"), rgb(color), min(1, d / 6)))
    return c


def stripe():
    c = Canvas("ship_stripe", rgb("#e86a20"))
    c.rect(0, 0, 15, 1, rgb("#b04810"))
    c.rect(0, 14, 15, 15, rgb("#b04810"))
    c.noise(0.03)
    return c


def hatch():
    c = tile_white()
    c.name = "ship_hatch"
    c.rect(1, 1, 14, 14, rgb("#d4d8dc"))
    c.rect(1, 1, 14, 1, rgb("#8a9098"))
    c.rect(1, 14, 14, 14, rgb("#8a9098"))
    c.rect(1, 1, 1, 14, rgb("#8a9098"))
    c.rect(14, 1, 14, 14, rgb("#8a9098"))
    c.rect(6, 6, 9, 9, rgb("#e86a20"))
    return c


def cockpit_inside():
    c = Canvas("ship_cockpit", rgb("#3a3f48"))
    c.rect(1, 2, 6, 6, rgb("#1a2a1a"))
    c.rect(2, 3, 5, 5, rgb("#40d060"))
    c.rect(9, 2, 14, 6, rgb("#1a1a2a"))
    c.rect(10, 3, 13, 5, rgb("#50a0ff"))
    for x in range(2, 14, 3):
        c.put(x, 10, rgb("#ff5040"))
        c.put(x + 1, 10, rgb("#ffd040"))
    c.rect(0, 13, 15, 15, rgb("#2a2e34"))
    return c


# ============================================================ item icons

OUT = rgb("#1b1922")


def outline(c, color=OUT):
    edge = []
    for y in range(16):
        for x in range(16):
            if c.px[y][x] is None and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and c.px[y + dy][x + dx] not in (None, color)
                                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge:
        c.px[y][x] = color


def icon_cog():
    c = Canvas("bronze_cog")
    # hull
    for x in range(1, 15):
        top = 10 if 3 <= x <= 12 else 9
        for y in range(top, 14 - (1 if x in (1, 14) else 0)):
            c.put(x, y, OAK if y < 12 else OAK_DARK)
    c.rect(2, 10, 13, 10, BRONZE)
    c.rect(0, 8, 2, 9, OAK_DARK)  # stern castle
    c.put(14, 9, OAK_DARK)
    # mast and sail
    c.rect(7, 1, 7, 9, OAK_DARK)
    cream, red = rgb("#efe4c6"), rgb("#b8352c")
    for y in range(2, 9):
        for x in range(3 + (1 if y in (2, 8) else 0), 12 - (1 if y in (2, 8) else 0)):
            if x != 7:
                c.put(x + (1 if 4 <= y <= 6 else 0), y, red if (x // 2) % 2 else cream)
    c.rect(3, 2, 11, 2, OAK_DARK)
    c.put(7, 0, red)
    c.put(8, 0, red)
    c.put(1, 7, rgb("#ffd66a"))
    outline(c)
    return c


def icon_motor():
    c = Canvas("motor_ship")
    for x in range(0, 16):
        for y in range(9, 14):
            if (x == 0 and y > 10) or (x == 15 and y < 10):
                continue
            if y >= 12 and (x < 2 or x > 14):
                continue
            c.put(x, y, NAVY if y < 12 else rgb("#9c2f28"))
    c.rect(1, 11, 14, 11, rgb("#e6e8ea"))
    c.rect(3, 6, 8, 8, rgb("#e8eaec"))  # bridge
    c.rect(4, 6, 7, 6, rgb("#6fa8d8"))
    c.rect(4, 4, 7, 5, rgb("#e8eaec"))
    c.rect(4, 4, 7, 4, rgb("#6fa8d8"))
    c.rect(9, 3, 10, 8, rgb("#e0b840"))  # funnel
    c.rect(9, 3, 10, 3, rgb("#1c1c1e"))
    c.rect(9, 5, 10, 5, rgb("#b8352c"))
    c.put(10, 1, rgb("#b0b4b8"))
    c.put(9, 2, rgb("#b0b4b8"))
    c.rect(11, 7, 14, 8, rgb("#c07020"))  # cargo
    c.rect(5, 1, 5, 3, rgb("#5a5a62"))  # mast
    c.put(5, 0, rgb("#fff4a0"))
    outline(c)
    return c


def icon_shuttle():
    """Side view, nose to the right: white body, black belly, glass canopy, tail fin, delta wing, flame."""
    c = Canvas("shuttle")
    white, grey, black, glass_c = rgb("#eceef0"), rgb("#b8bcc2"), rgb("#2a2c30"), rgb("#6fa8d8")
    rows = {6: (5, 12), 7: (3, 14), 8: (2, 15), 9: (2, 15), 10: (2, 14), 11: (3, 12)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            c.put(x, y, black if y >= 10 else white)
    c.rect(10, 6, 12, 7, glass_c)
    c.put(13, 7, glass_c)
    c.put(10, 6, rgb("#c8e4ff"))
    for y in range(2, 7):  # tail fin
        for x in range(2, 2 + (7 - y)):
            c.put(x + 1, y, white if x > 2 else grey)
    for x in range(4, 11):  # delta wing (seen edge-on, a little below the body line)
        c.put(x, 10, grey if x < 10 else black)
    c.rect(4, 12, 9, 12, grey)
    c.put(5, 13, rgb("#5a5c62"))
    c.put(8, 13, rgb("#5a5c62"))
    c.rect(4, 8, 9, 8, rgb("#e86a20"))
    c.put(1, 8, rgb("#5a5c62"))
    c.put(1, 9, rgb("#5a5c62"))
    c.put(0, 8, rgb("#ffb040"))
    c.put(0, 9, rgb("#ff7a18"))
    c.put(15, 9, grey)
    outline(c)
    return c


BLOCK = {f.__name__: f for f in (cog_planks, cog_deck, cog_dark, bronze, sail, rope, flag, lantern, iron_dark,
                                 hull_steel, hull_red, boot_top, steel_deck, white_paint, windows, funnel, propeller,
                                 lamp, lamp_off, beam, glass, rubber, tile_white, tile_black, gold_foil, engine,
                                 engine_inner, flame, blue_flame, stripe, hatch, cockpit_inside)}
LIGHTS = {"ship_light_red": "#ff3020", "ship_light_green": "#30ff50", "ship_light_white": "#f0f4ff"}
ITEMS = {"bronze_cog": icon_cog, "motor_ship": icon_motor, "shuttle": icon_shuttle}


def all_textures():
    """(path, canvas) for every texture this module draws."""
    out = []
    for fn in BLOCK.values():
        c = fn()
        out.append((TEX / "block" / f"{c.name}.png", c))
    for name, color in LIGHTS.items():
        out.append((TEX / "block" / f"{name}.png", light(name, color)))
    for name, fn in ITEMS.items():
        out.append((TEX / "item" / f"{name}.png", fn()))
    return out


def draw(only_missing):
    textures = all_textures()
    if only_missing and all(p.exists() for p, _ in textures):
        return 0
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
    print(f"drew {draw(only_missing=False)} ship textures")

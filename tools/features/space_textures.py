#!/usr/bin/env python3
"""Textures of space travel and personal gear:

  entity/equipment/humanoid/astronaut.png           the Astronaut Suit (helmet with gold visor, suit)
  entity/equipment/humanoid_leggings/astronaut.png  its leggings
  environment/earth.png                             the planet under the orbit dimension (256x256)
  block/  capsule hull and heat shield, jetpack parts, Return Pod, Oxygen Compressor and Sealer
  item/   suit pieces, Jet Suit, jetpacks, Crew Capsule

Run:  python3 tools/features/space_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily).
"""
import importlib.util
import math
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def _orbital():
    spec = importlib.util.spec_from_file_location("orbital_textures_for_space", Path(__file__).with_name("orbital_textures.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


O = _orbital()
rgb, mix, shade, Canvas, noisy, rivet = O.rgb, O.mix, O.shade, O.Canvas, O.noisy, O.rivet
STEEL, DARK, TITAN, WHITE, CYAN, GOLD, RED, FLAME, OUTLINE = O.STEEL, O.DARK, O.TITAN, O.WHITE, O.CYAN, O.GOLD, O.RED, O.FLAME, O.OUTLINE
SUIT = [rgb(h) for h in ("#FFFFFF", "#EEF1F4", "#D5DAE0", "#AEB5BF", "#7E8692")]
ORANGE = [rgb(h) for h in ("#FFC890", "#F08A30", "#C0601C", "#7A3A10")]
BLUE = [rgb(h) for h in ("#8EC8FF", "#3E8EE8", "#2356A8", "#122E60")]
VISOR = [rgb(h) for h in ("#FFF4B0", "#F2C84A", "#C8902A", "#8A5A18", "#4A3010")]
GREEN_J = [rgb(h) for h in ("#B8D8A0", "#7EA868", "#557A46", "#344E2C")]


# ============================================================ big canvases (armour, planet)

class Big:
    def __init__(self, w, h):
        from PIL import Image
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def put(self, x, y, c):
        if 0 <= x < self.img.width and 0 <= y < self.img.height and c is not None:
            self.img.putpixel((x, y), c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def cloth(self, x0, y0, x1, y1, pal, rng, base=1):
        """Suit fabric: base tone with faint weave noise."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                r = rng.random()
                self.put(x, y, pal[base + 1] if r < 0.08 else pal[max(0, base - 1)] if r < 0.14 else pal[base])

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)


def armor_main():
    """64x32 humanoid layer: helmet (visor on the front face), suit body and arms, boots (legs region)."""
    rng = random.Random(501)
    c = Big(64, 32)
    # ---- helmet: head box faces at (0,0): top 8..15/0..7, bottom 16..23/0..7, right 0..7/8..15,
    # front 8..15/8..15, left 16..23/8..15, back 24..31/8..15
    c.cloth(0, 0, 31, 15, SUIT, rng, base=0)
    for x in range(8, 16):  # crown seam on top
        c.put(x, 3, SUIT[3])
    c.rect(11, 0, 12, 7, SUIT[2])
    # visor: gold, with a sky reflection band and a highlight
    for y in range(9, 15):
        for x in range(9, 15):
            t = (y - 9) / 5
            col = mix(VISOR[1], VISOR[3], t)
            if y == 10 and 10 <= x <= 12:
                col = VISOR[0]
            if y == 13:
                col = mix(col, BLUE[2], 0.35)
            c.put(x, y, col)
    for x in range(8, 16):
        c.put(x, 8, SUIT[3])
        c.put(x, 15, SUIT[4])
    for y in range(8, 16):
        c.put(8, y, SUIT[3])
        c.put(15, y, SUIT[3])
    # side lights and the helmet's neck ring (bottom rows of every side face)
    c.put(3, 11, rgb("#50FF70"))
    c.put(4, 11, SUIT[4])
    c.put(20, 11, rgb("#FF5050"))
    c.put(19, 11, SUIT[4])
    for x in range(0, 32):
        c.put(x, 15, DARK[2])
    for x in range(24, 32):  # back: a small backpack port
        c.put(x, 12, SUIT[3])
    c.rect(26, 10, 29, 11, DARK[1])
    # ---- suit body at (16,16): top 20..27/16..19, bottom 28..35/16..19, right 16..19/20..31,
    # front 20..27/20..31, left 28..31/20..31, back 32..39/20..31
    c.cloth(16, 16, 39, 31, SUIT, rng, base=1)
    # chest control box
    c.rect(21, 22, 26, 26, STEEL[2])
    c.rect(21, 22, 26, 22, STEEL[1])
    c.put(22, 24, rgb("#FF4040"))
    c.put(23, 24, rgb("#40FF60"))
    c.put(25, 24, rgb("#40A0FF"))
    c.put(22, 25, STEEL[4])
    c.put(24, 25, STEEL[4])
    # hoses from the box to the sides, a mission patch, the belt
    c.put(20, 23, DARK[2])
    c.put(27, 23, DARK[2])
    c.put(26, 28, BLUE[1])
    c.put(27, 28, RED[1])
    for x in range(16, 40):
        c.put(x, 30, SUIT[3])
        c.put(x, 31, DARK[2])
    # back: life-support plate
    c.rect(33, 21, 38, 28, SUIT[2])
    c.rect(34, 22, 37, 27, SUIT[3])
    c.put(35, 24, ORANGE[1])
    c.put(36, 24, ORANGE[1])
    # ---- arm at (40,16): top 44..47/16..19, bottom 48..51/16..19, outer 40..43, front 44..47,
    # inner 48..51, back 52..55 (rows 20..31)
    c.cloth(40, 16, 55, 31, SUIT, rng, base=1)
    for x in range(40, 56):
        c.put(x, 23, SUIT[3])  # elbow joint ring
        c.put(x, 28, SUIT[3])  # wrist ring
        for y in (29, 30, 31):
            c.put(x, y, SUIT[3] if y < 31 else SUIT[4])  # gloves
    c.rect(40, 20, 43, 21, ORANGE[1])  # shoulder stripe
    c.rect(40, 22, 43, 22, BLUE[1])
    # ---- boots in the leg region at (0,16): outer 0..3, front 4..7, inner 8..11, back 12..15 (rows 20..31)
    c.cloth(0, 16, 15, 31, SUIT, rng, base=2)
    for x in range(0, 16):
        c.put(x, 26, DARK[1])
        for y in range(27, 32):
            c.put(x, y, DARK[2] if y < 31 else DARK[4])
    c.rect(4, 16, 11, 19, DARK[2])  # soles (bottom face)
    return c


def armor_legs():
    """64x32 humanoid_leggings layer: waist (body region, lower rows) and the legs."""
    rng = random.Random(502)
    c = Big(64, 32)
    c.cloth(16, 16, 39, 31, SUIT, rng, base=1)
    for x in range(16, 40):
        c.put(x, 26, DARK[2])  # belt
        c.put(x, 27, SUIT[3])
    c.put(23, 26, STEEL[1])
    c.put(24, 26, STEEL[1])
    c.cloth(0, 16, 15, 31, SUIT, rng, base=1)
    for x in range(0, 16):
        c.put(x, 25, SUIT[3])  # knee ring
    for y in range(20, 31):  # pocket and stripe on the outer side
        c.put(1, y, ORANGE[1] if y < 24 else SUIT[1])
    c.rect(1, 27, 3, 29, SUIT[3])
    return c


def earth():
    """The planet seen from orbit: oceans, continents with deserts and ice caps, swirling clouds,
    lit from one side, with a thin blue atmosphere at the rim. Transparent outside."""
    size, r = 256, 112
    rng = random.Random(7)

    # value noise
    grid = {}

    def lattice(ix, iy, octave):
        key = (ix, iy, octave)
        if key not in grid:
            grid[key] = rng.random()
        return grid[key]

    def noise(x, y, octave):
        ix, iy = math.floor(x), math.floor(y)
        fx, fy = x - ix, y - iy
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a, b = lattice(ix, iy, octave), lattice(ix + 1, iy, octave)
        c_, d = lattice(ix, iy + 1, octave), lattice(ix + 1, iy + 1, octave)
        return (a + (b - a) * sx) + ((c_ + (d - c_) * sx) - (a + (b - a) * sx)) * sy

    def fbm(x, y, octaves, base):
        total, amp, freq, norm = 0, 1, 1, 0
        for o in range(octaves):
            total += amp * noise(x * freq, y * freq, base + o)
            norm += amp
            amp *= 0.5
            freq *= 2.03
        return total / norm

    c = Big(size, size)
    light = (-0.55, -0.45, 0.70)
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)
    cx = cy = size / 2
    for py in range(size):
        for px in range(size):
            dx, dy = (px + 0.5 - cx) / r, (py + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1.0:
                dist = math.sqrt(d2)
                if dist < 1.10:  # atmosphere glow
                    a = (1 - (dist - 1) / 0.10) ** 2
                    lit = max(0.25, 0.5 - 0.5 * (dx * light[0] + dy * light[1]) / dist)
                    c.put(px, py, (90, 170, 255, int(200 * a * lit)))
                continue
            dz = math.sqrt(1 - d2)
            # sphere coordinates -> map the noise on the sphere so continents curve with it
            u, v = math.atan2(dx, dz) * 2.2 + 4.0, math.asin(dy) * 2.6 + 3.0
            h = fbm(u * 1.4, v * 1.4, 5, 0)
            lat = abs(dy)
            if lat > 0.86 - 0.05 * fbm(u * 3, v * 3, 2, 30):
                col = (236, 242, 250)
            elif h > 0.54:
                dry = fbm(u * 2.3, v * 2.3, 3, 10)
                g = (58, 128, 52) if dry < 0.52 else (170, 150, 92) if dry > 0.62 else (112, 140, 70)
                if h > 0.66:
                    g = mix(g + (255,), (130, 118, 96, 255), 0.5)[:3]
                col = g
            else:
                deep = max(0.0, min(1.0, (0.54 - h) / 0.2))
                col = mix((52, 118, 190, 255), (16, 44, 110, 255), deep)[:3]
            cl = fbm(u * 2.6 + 11, v * 3.4 + 5, 5, 20)
            cloud = max(0.0, min(1.0, (cl - 0.52) * 4.0))
            col = tuple(round(a + (245 - a) * cloud * 0.9) for a in col)
            # lighting: lambert with a dim night side, rim towards the atmosphere colour
            nd = dx * light[0] + dy * light[1] + dz * light[2]
            lum = 0.18 + 0.95 * max(0.0, nd)
            rim = (1 - dz) ** 3
            out = []
            for i, ch in enumerate(col):
                val = ch * lum
                val = val + (120, 190, 255)[i] * rim * 0.55
                out.append(max(0, min(255, round(val))))
            c.put(px, py, tuple(out) + (255,))
    return c


# ============================================================ 16x16 block textures

def capsule_hull():
    """White capsule panels with dark seams and a row of rivets."""
    rng = random.Random(601)
    c = Canvas()
    noisy(c, SUIT, rng, base=1, p=0.10)
    for x in range(16):
        c.put(x, 0, SUIT[3])
        c.put(x, 8, SUIT[3])
    for y in range(16):
        c.put(0, y, SUIT[3])
        c.put(8, y, SUIT[2])
    for x in (3, 12):
        c.put(x, 4, SUIT[4])
        c.put(x, 12, SUIT[4])
    return c


def capsule_mark():
    """Hull panel with an orange stripe and a small flag (the capsule's decal side)."""
    c = capsule_hull()
    for x in range(16):
        c.put(x, 10, ORANGE[1])
        c.put(x, 11, ORANGE[2])
    c.rect(3, 3, 8, 6, BLUE[2])
    for x in range(3, 9):
        c.put(x, 4, WHITE[0])
    c.put(4, 5, RED[1])
    c.put(6, 5, RED[1])
    return c


def heat_shield():
    """Charred ablative tiles: brown-black with hot orange cracks."""
    rng = random.Random(602)
    c = Canvas()
    base = [rgb(h) for h in ("#6A4A34", "#4A3222", "#33221A", "#22160F", "#140D08")]
    noisy(c, base, rng, base=2, p=0.3)
    for y in range(0, 16, 4):
        for x in range(16):
            c.put(x, y, base[4])
    for x in range(0, 16, 4):
        for y in range(16):
            c.put((x + (y // 4) * 2) % 16, y, base[4])
    for _ in range(6):
        c.put(rng.randrange(16), rng.randrange(16), ORANGE[2])
    return c


def jet_tank(pal):
    """A jetpack fuel/battery tank, shaded as a cylinder, with bands."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            t = abs(x - 7.5) / 8
            col = pal[0] if t < 0.15 else pal[1] if t < 0.45 else pal[2] if t < 0.8 else pal[3]
            c.put(x, y, col)
    for y in (2, 13):
        for x in range(16):
            c.put(x, y, DARK[3])
    for x in range(5, 11):
        c.put(x, 7, GOLD[1] if x % 2 else GOLD[2])
    return c


def jet_frame():
    rng = random.Random(611)
    c = Canvas()
    noisy(c, DARK, rng, base=2)
    c.bevel(0, 0, 15, 15, DARK[1], DARK[4])
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(c, x, y, STEEL)
    c.put(7, 7, CYAN[1])
    c.put(8, 7, CYAN[0])
    return c


def jet_nozzle():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            c.put(x, y, DARK[4] if d < 3 else FLAME[3] if d < 4 else STEEL[2] if d < 6 else STEEL[3])
    return c


def jet_strap():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, rgb("#26282E") if (x + y) % 3 else rgb("#34363E"))
    for x in range(16):
        c.put(x, 0, rgb("#50535C"))
    return c


def jet_red():
    rng = random.Random(612)
    c = Canvas()
    noisy(c, RED, rng, base=1)
    c.bevel(0, 0, 15, 15, RED[0], RED[3])
    return c


def return_pod_side():
    """The pod's white cone with a porthole in the middle and a red hatch outline."""
    c = capsule_hull()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 7)
            if d < 2.6:
                c.put(x, y, BLUE[1] if (x + y) % 5 else BLUE[0])
            elif d < 3.5:
                c.put(x, y, STEEL[2])
    for x in range(4, 12):
        c.put(x, 12, RED[1])
    return c


def return_pod_top():
    c = Canvas()
    rng = random.Random(621)
    noisy(c, SUIT, rng, base=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 3:
                c.put(x, y, ORANGE[1] if (x // 2 + y // 2) % 2 else WHITE[1])  # packed parachute
    return c


def machine_side(accent):
    rng = random.Random(631)
    c = Canvas()
    noisy(c, STEEL, rng, base=2)
    c.bevel(0, 0, 15, 15, STEEL[1], STEEL[4])
    for y in (4, 11):
        for x in range(2, 14):
            c.put(x, y, STEEL[3])
    for x in range(2, 14):
        c.put(x, 7, accent[1])
        c.put(x, 8, accent[2])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.put(x, y, STEEL[0])
    return c


def machine_top():
    rng = random.Random(632)
    c = Canvas()
    noisy(c, DARK, rng, base=1)
    c.bevel(0, 0, 15, 15, DARK[0], DARK[4])
    for y in range(3, 13, 2):
        for x in range(3, 13):
            c.put(x, y, DARK[4])
    return c


def compressor_front(on):
    """A round air tank gauge and a pump grille; the gauge glows blue while it pumps."""
    c = machine_side(CYAN)
    for y in range(2, 10):
        for x in range(4, 12):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 6)
            if d < 3.3:
                c.put(x, y, CYAN[1] if on else DARK[3])
            elif d < 4.2:
                c.put(x, y, STEEL[1])
    c.put(8, 4, WHITE[0] if on else DARK[1])
    c.put(8, 5, WHITE[0] if on else DARK[1])
    for y in (11, 13):
        for x in range(4, 12):
            c.put(x, y, DARK[4])
    return c


def sealer_top(on):
    """A round vent with fan blades; lit cyan while the bubble is up."""
    c = machine_top()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = math.hypot(dx, dy)
            if d < 6:
                a = math.atan2(dy, dx)
                blade = int((a + math.pi) / (math.pi / 3)) % 2 == 0
                if d < 1.5:
                    c.put(x, y, STEEL[1])
                else:
                    c.put(x, y, (CYAN[1] if on else STEEL[3]) if blade else (CYAN[3] if on else DARK[4]))
            elif d < 7:
                c.put(x, y, STEEL[2])
    return c


def sealer_side(on):
    c = machine_side(CYAN if on else [STEEL[2], STEEL[3], STEEL[3], STEEL[4]])
    for y in range(2, 6):
        for x in range(5, 11):
            c.put(x, y, CYAN[2] if on and (x + y) % 2 else DARK[3])
    return c


# ============================================================ items

def helmet_icon():
    c = Canvas()
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot((x + 0.5 - 8) / 6, (y + 0.5 - 8) / 6)
            if d < 1:
                c.put(x, y, SUIT[1] if x < 9 else SUIT[2])
    for y in range(5, 11):
        for x in range(4, 12):
            d = math.hypot((x + 0.5 - 8) / 4, (y + 0.5 - 8) / 3)
            if d < 1:
                c.put(x, y, VISOR[1] if y < 7 else VISOR[2] if y < 9 else VISOR[3])
    c.put(6, 6, VISOR[0])
    c.put(7, 6, VISOR[0])
    for x in range(4, 12):
        c.put(x, 13, DARK[2])
        c.put(x, 14, DARK[3])
    c.put(3, 9, rgb("#50FF70"))
    c.outline()
    return c


def suit_icon(jet=False):
    c = Canvas()
    body = [(x, y) for y in range(3, 15) for x in range(3, 13)]
    for x, y in body:
        c.put(x, y, SUIT[1] if x < 9 else SUIT[2])
    for y in range(3, 10):  # arms
        c.put(1, y, SUIT[2])
        c.put(2, y, SUIT[1])
        c.put(13, y, SUIT[2])
        c.put(14, y, SUIT[3])
    for x in range(5, 11):
        c.put(x, 2, DARK[2])  # neck ring
    c.rect(5, 6, 10, 9, STEEL[2])
    c.put(6, 7, rgb("#FF4040"))
    c.put(7, 7, rgb("#40FF60"))
    c.put(9, 7, rgb("#40A0FF"))
    c.put(4, 4, ORANGE[1])
    c.put(11, 4, BLUE[1])
    for x in range(3, 13):
        c.put(x, 13, SUIT[3])
    if jet:  # red tanks and nozzles showing on both sides
        for y in range(4, 13):
            c.put(0, y, RED[1])
            c.put(15, y, RED[2])
        c.put(0, 13, FLAME[1])
        c.put(15, 13, FLAME[1])
        c.put(0, 14, FLAME[2])
        c.put(15, 14, FLAME[2])
    c.outline()
    return c


def leggings_icon():
    c = Canvas()
    for y in range(2, 5):
        for x in range(3, 13):
            c.put(x, y, DARK[2] if y == 3 else SUIT[2])
    for y in range(5, 15):
        for x in list(range(3, 7)) + list(range(9, 13)):
            c.put(x, y, SUIT[1] if x in (3, 4, 9, 10) else SUIT[2])
    for x in list(range(3, 7)) + list(range(9, 13)):
        c.put(x, 9, SUIT[3])
    c.put(3, 6, ORANGE[1])
    c.outline()
    return c


def boots_icon():
    c = Canvas()
    for side in (2, 9):
        for y in range(6, 14):
            for x in range(side, side + 4):
                c.put(x, y, SUIT[1] if y < 10 else DARK[1])
        for x in range(side, side + 6):
            c.put(x, 13, DARK[3])
            c.put(x, 12, DARK[2])
        c.put(side, 9, SUIT[3])
    c.outline()
    return c


def jetpack_icon(advanced):
    c = Canvas()
    tank = [RED[0], RED[1], RED[2], RED[3]] if advanced else GREEN_J
    for tx in (2, 9):
        for y in range(2, 12):
            for x in range(tx, tx + 5):
                t = abs(x - (tx + 2)) / 2.5
                c.put(x, y, tank[0] if t < 0.3 else tank[1] if t < 0.7 else tank[2])
        for x in range(tx, tx + 5):
            c.put(x, 2, STEEL[1])
            c.put(x, 11, DARK[2])
        c.rect(tx + 1, 12, tx + 3, 13, STEEL[3])
        c.put(tx + 2, 14, FLAME[1])
        c.put(tx + 1, 15, FLAME[2] if advanced else None)
        c.put(tx + 3, 15, FLAME[2] if advanced else None)
    c.rect(7, 4, 8, 10, DARK[2])
    c.put(7, 6, CYAN[1])
    if advanced:
        c.put(1, 5, STEEL[2])
        c.put(14, 5, STEEL[2])
        c.put(0, 6, STEEL[3])
        c.put(15, 6, STEEL[3])
    c.outline()
    return c


def capsule_icon():
    """A cone capsule with a window and an escape tower."""
    c = Canvas()
    for y in range(5, 14):
        half = 2 + (y - 5) * 0.62
        for x in range(16):
            if abs(x + 0.5 - 8) <= half:
                c.put(x, y, SUIT[1] if x < 8 else SUIT[2])
    for x in range(2, 14):
        c.put(x, 14, rgb("#4A3222"))
    for x in range(3, 13):
        c.put(x, 11, ORANGE[1])
    c.rect(7, 7, 8, 9, BLUE[1])
    c.put(7, 7, BLUE[0])
    for y in range(0, 5):
        c.put(8, y, RED[1] if y < 2 else STEEL[2])
    c.put(7, 4, STEEL[3])
    c.put(9, 4, STEEL[3])
    c.outline()
    return c


BLOCKS = {
    "capsule_hull": capsule_hull, "capsule_mark": capsule_mark, "capsule_heat_shield": heat_shield,
    "jetpack_tank_electric": lambda: jet_tank(GREEN_J), "jetpack_tank_advanced": lambda: jet_tank(RED),
    "jetpack_frame": jet_frame, "jetpack_nozzle": jet_nozzle, "jetpack_strap": jet_strap, "jetpack_red": jet_red,
    "return_pod_side": return_pod_side, "return_pod_top": return_pod_top,
    "oxygen_compressor_side": lambda: machine_side(CYAN), "oxygen_compressor_top": machine_top,
    "oxygen_compressor_front": lambda: compressor_front(False), "oxygen_compressor_front_on": lambda: compressor_front(True),
    "oxygen_sealer_side": lambda: sealer_side(False), "oxygen_sealer_side_on": lambda: sealer_side(True),
    "oxygen_sealer_top": lambda: sealer_top(False), "oxygen_sealer_top_on": lambda: sealer_top(True),
}
ITEMS = {
    "astronaut_helmet": helmet_icon, "astronaut_suit": suit_icon, "astronaut_leggings": leggings_icon,
    "astronaut_boots": boots_icon, "jet_suit": lambda: suit_icon(True),
    "electric_jetpack": lambda: jetpack_icon(False), "advanced_jetpack": lambda: jetpack_icon(True),
    "crew_capsule": capsule_icon,
}
BIG = {
    TEX / "entity/equipment/humanoid/astronaut.png": armor_main,
    TEX / "entity/equipment/humanoid_leggings/astronaut.png": armor_legs,
    TEX / "environment/earth.png": earth,
}


def paths():
    for name, fn in BLOCKS.items():
        yield TEX / "block" / f"{name}.png", fn
    for name, fn in ITEMS.items():
        yield TEX / "item" / f"{name}.png", fn
    yield from BIG.items()


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
    print(f"wrote {len(BLOCKS) + len(ITEMS) + len(BIG)} space textures")

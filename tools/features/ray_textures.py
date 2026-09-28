#!/usr/bin/env python3
"""Textures of the 3D Minimizer and Maximizer Rays (tools/features/ray.py builds the models from them).

Run:  python3 tools/features/ray_textures.py

Writes into src/main/resources/assets/factoryascent/textures/item/:
  size_ray_parts.png               32x32 atlas shared by both rays: white shell, gunmetal frame, grip, barrel, scope
  {ray}_energy.png / _hot.png      16x16 coil and cell glow (cyan for the Minimizer, orange for the Maximizer);
                                   _hot is the brighter one shown while charging
  {ray}_crystal.png / _hot.png     16x16 emitter crystal at the muzzle
  {ray}_icon.png                   16x16 flat inventory sprite (side view, muzzle to the right)
  {ray}_scoped_icon.png            the same with a scope on top

These all belong to this script, so it always redraws them. The old flat minimizer_ray.png /
maximizer_ray.png from mobs_textures.py are left alone. gen_resources.py loads every tools/features/*.py
and calls generate(ctx); Pillow is imported lazily.
"""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures/item"
RAYS = ("minimizer_ray", "maximizer_ray")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


W_HI, W, W_MID, W_LO = rgb("ffffff"), rgb("e4e9ef"), rgb("bfc7d1"), rgb("8c96a3")
G_HI, G, G_MID, G_LO, G_DK = rgb("6d737c"), rgb("4b5058"), rgb("3a3e45"), rgb("2a2d33"), rgb("1b1d21")
S_HI, S, S_MID, S_LO = rgb("f2f5f8"), rgb("c9cfd6"), rgb("9ea6af"), rgb("6f7780")
R_HI, R, R_LO = rgb("3a3c42"), rgb("26272b"), rgb("17181a")
LENS_HI, LENS, LENS_LO = rgb("d8f4ff"), rgb("5e9fc4"), rgb("274a66")
OUTLINE = rgb("16171b")

# (highlight, main, deep) per ray; the "hot" variants brighten these.
PALETTES = {
    "minimizer_ray": (rgb("e6ffff"), rgb("33d6ff"), rgb("1467c8")),
    "maximizer_ray": (rgb("fff0c8"), rgb("ff5a1f"), rgb("a8200e")),
}


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)

    def bevel(self, x0, y0, x1, y1, hi, mid, lo):
        self.rect(x0, y0, x1, y1, mid)
        for x in range(x0, x1):
            self.put(x, y0, hi)
            self.put(x, y1 - 1, lo)
        for y in range(y0, y1):
            self.put(x0, y, hi if y < y1 - 1 else lo)
            self.put(x1 - 1, y, lo)

    def outline(self, color=OUTLINE):
        edge = []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x][3] == 0 and any(self.get(x + dx, y + dy)[3] > 0 and self.get(x + dx, y + dy) != color
                                                 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    edge.append((x, y))
        for x, y in edge:
            self.px[y][x] = color

    def image(self):
        from PIL import Image
        im = Image.new("RGBA", (self.w, self.h))
        im.putdata([c for row in self.px for c in row])
        return im


# ---------------------------------------------------------------- shared atlas (32x32, 1 texel per model pixel)
REGIONS = {
    "shell_side": (0, 0, 8, 3),     # body east/west: 8 long x 3 tall, white shell with a dark seam
    "shell_top": (8, 0, 11, 8),     # body top/bottom: 3 wide x 8 long
    "shell_end": (11, 0, 14, 3),    # body front/back
    "frame": (14, 0, 16, 2),        # gunmetal: rear cap, mounts, trigger guard
    "grip": (0, 4, 3, 10),          # rubber grip sides: 2.5 long x 6 tall
    "grip_front": (3, 4, 5, 10),    # grip front/back
    "barrel_side": (0, 11, 6, 13),  # barrel: 6 long x 1.5
    "barrel_end": (6, 11, 8, 13),
    "scope_side": (0, 14, 9, 16),   # scope tube: 9 long x 2
    "scope_end": (10, 14, 12, 16),  # scope back
    "lens": (12, 14, 14, 16),       # scope front lens
    "trigger": (16, 0, 17, 2),
}


def atlas():
    c = Canvas(32, 32)
    r = REGIONS
    x0, y0, x1, y1 = r["shell_side"]
    c.bevel(x0, y0, x1, y1, W_HI, W, W_LO)
    for x in range(x0 + 1, x1 - 1):
        c.put(x, y0 + 1, W_MID if x % 3 else W)
    c.put(x0 + 1, y0 + 1, G_MID)
    x0, y0, x1, y1 = r["shell_top"]
    c.bevel(x0, y0, x1, y1, W_HI, W, W_LO)
    for y in range(y0 + 1, y1 - 1):
        c.put(x0 + 1, y, W_MID)
    x0, y0, x1, y1 = r["shell_end"]
    c.bevel(x0, y0, x1, y1, W_HI, W_MID, W_LO)
    c.put(x0 + 1, y0 + 1, G_LO)
    x0, y0, x1, y1 = r["frame"]
    c.rect(x0, y0, x1, y1, G)
    c.put(x0, y0, G_HI)
    c.put(x1 - 1, y1 - 1, G_LO)
    x0, y0, x1, y1 = r["trigger"]
    c.rect(x0, y0, x1, y1, G_LO)
    for key in ("grip", "grip_front"):
        x0, y0, x1, y1 = r[key]
        c.rect(x0, y0, x1, y1, R)
        for y in range(y0 + 1, y1, 2):
            c.put(x0, y, R_HI)
        for x in range(x0, x1):
            c.put(x, y0, G)
    x0, y0, x1, y1 = r["barrel_side"]
    c.rect(x0, y0, x1, y1, S_MID)
    for x in range(x0, x1):
        c.put(x, y0, S_HI)
        c.put(x, y1 - 1, S_LO)
    x0, y0, x1, y1 = r["barrel_end"]
    c.rect(x0, y0, x1, y1, G_DK)
    x0, y0, x1, y1 = r["scope_side"]
    c.rect(x0, y0, x1, y1, G)
    for x in range(x0, x1):
        c.put(x, y0, G_HI)
        c.put(x, y1 - 1, G_LO)
    c.put(x0 + 1, y0, S_MID)
    c.put(x1 - 2, y0, S_MID)
    x0, y0, x1, y1 = r["scope_end"]
    c.bevel(x0, y0, x1, y1, G_HI, G_LO, G_DK)
    x0, y0, x1, y1 = r["lens"]
    c.rect(x0, y0, x1, y1, LENS)
    c.put(x0, y0, LENS_HI)
    c.put(x1 - 1, y1 - 1, LENS_LO)
    return c


def energy(ray, hot):
    """Coil / cell glow: even colour with a bright core line every 4 rows."""
    hi, mid, lo = PALETTES[ray]
    if hot:
        hi, mid, lo = mix(hi, (255, 255, 255, 255), 0.5), mix(mid, hi, 0.45), mid
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.put(x, y, hi if y % 4 == 1 else lo if (x * 5 + y * 3) % 13 == 0 else mid)
    return c


def crystal(ray, hot):
    """Faceted crystal: diagonal facets from highlight to deep colour."""
    hi, mid, lo = PALETTES[ray]
    if hot:
        hi, mid, lo = (255, 255, 255, 255), mix(hi, mid, 0.3), mid
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = (x + y) % 6
            c.put(x, y, hi if k == 0 else mid if k < 4 else lo)
    return c


def icon(ray, scoped):
    """Side view, muzzle to the right: white body, coloured cell and coils, crystal emitter, grip below."""
    hi, mid, lo = PALETTES[ray]
    c = Canvas(16, 16)
    # Body (x 1..8, rows 6..9)
    c.rect(1, 6, 9, 10, W)
    for x in range(1, 9):
        c.put(x, 6, W_HI)
        c.put(x, 9, W_LO)
    c.put(1, 7, G)
    c.put(1, 8, G)
    # Energy cell window on the body
    for x in range(3, 7):
        c.put(x, 7, hi if x == 3 else mid)
        c.put(x, 8, lo if x == 6 else mid)
    # Barrel (x 9..12, rows 7..8) with two coils
    for x in range(9, 13):
        c.put(x, 7, S_HI)
        c.put(x, 8, S_LO)
    for x in (9, 11):
        for y in range(6, 10):
            c.put(x, y, hi if y == 6 else mid if y < 9 else lo)
    # Crystal emitter (diamond at x 13..15)
    for x, y, col in ((13, 7, mid), (13, 8, lo), (14, 6, hi), (14, 7, hi), (14, 8, mid), (14, 9, lo),
                      (15, 7, mid), (15, 8, lo)):
        c.put(x, y, col)
    # Grip (raked back), trigger
    for i, y in enumerate(range(10, 14)):
        off = -(i // 2)
        c.put(3 + off, y, R_HI)
        c.put(4 + off, y, R)
        c.put(5 + off, y, R_LO)
    c.put(7, 10, G)
    c.put(6, 11, G_LO)
    if scoped:
        for x in range(3, 9):
            c.put(x, 3, G_HI if x < 8 else G)
            c.put(x, 4, G_LO)
        c.put(8, 3, LENS_HI)
        c.put(8, 4, LENS)
        c.put(4, 5, G)
        c.put(7, 5, G)
    c.outline()
    return c


def draw_all():
    OUT.mkdir(parents=True, exist_ok=True)
    atlas().image().save(OUT / "size_ray_parts.png")
    for ray in RAYS:
        energy(ray, False).image().save(OUT / f"{ray}_energy.png")
        energy(ray, True).image().save(OUT / f"{ray}_energy_hot.png")
        crystal(ray, False).image().save(OUT / f"{ray}_crystal.png")
        crystal(ray, True).image().save(OUT / f"{ray}_crystal_hot.png")
        icon(ray, False).image().save(OUT / f"{ray}_icon.png")
        icon(ray, True).image().save(OUT / f"{ray}_scoped_icon.png")


def generate(ctx):
    try:
        draw_all()
    except ImportError:
        if not (OUT / "size_ray_parts.png").exists():
            print("ray_textures: Pillow missing, cannot draw the size ray textures")


if __name__ == "__main__":
    draw_all()
    print(f"wrote size ray textures to {OUT}")

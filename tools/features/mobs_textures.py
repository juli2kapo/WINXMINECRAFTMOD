#!/usr/bin/env python3
"""Textures of the mob tools: mob_capsule, mob_capsule_full, minimizer_ray, maximizer_ray (16x16).

Run:  python3 tools/features/mobs_textures.py

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily, so resources still generate
without it once the PNGs exist).
"""
import math
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures/item"
NAMES = ["mob_capsule", "mob_capsule_full", "minimizer_ray", "maximizer_ray"]
OUTLINE = (27, 25, 34, 255)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


class Canvas:
    def __init__(self):
        self.px = [[None] * 16 for _ in range(16)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < 16 and 0 <= y < 16 else None

    def outline(self, color=OUTLINE):
        """Dark 1-px outline around the drawn shape (4-neighbourhood)."""
        edge = []
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is None and any(self.get(x + dx, y + dy) not in (None, color)
                                                 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    edge.append((x, y))
        for x, y in edge:
            self.px[y][x] = color

    def image(self):
        from PIL import Image
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        return img


def seg_dist(px, py, ax, ay, bx, by):
    """Distance from a point to a segment, and the signed side (positive = upper-left)."""
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
    cx, cy = ax + vx * t, ay + vy * t
    side = (px - ax) * vy - (py - ay) * vx
    return math.hypot(px - cx, py - cy), side, t


# ---------------------------------------------------------------- capsule

def capsule(full):
    c = Canvas()
    cx, cy, r = 7.5, 7.5, 6.6
    glass_hi, glass, glass_lo = rgb("#e8fbff"), rgb("#9fd8e8"), rgb("#5e9fb8")
    if full:
        glass_hi, glass, glass_lo = rgb("#f6e8ff"), rgb("#b890e0"), rgb("#6c4a9c")
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = math.hypot(dx, dy)
            if d > r:
                continue
            # light from the upper left
            shade = (dx * 0.6 + dy * 0.8) / r
            col = mix(glass, glass_lo, max(0.0, shade)) if shade > 0 else mix(glass, glass_hi, min(1.0, -shade * 0.8))
            if d > r - 1.1:
                col = mix(col, glass_lo, 0.55)  # rim
            c.put(x, y, col)
    if full:
        # a small glowing creature (a four-legged silhouette with a bright eye) above the band
        body, glow = rgb("#2b1840"), rgb("#e98cff")
        shape = ["......#.",
                 ".....###",
                 "########",
                 "#######.",
                 ".#.#..#."]
        ox, oy = 4, 3  # rows 3..7, above the band
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch == "#":
                    c.put(ox + i, oy + j, body)
        c.put(ox + 6, oy + 1, glow)  # eye
        for y in range(16):  # soft glow around the silhouette
            for x in range(16):
                if c.get(x, y) not in (None, body, glow) and any(c.get(x + dx, y + dy) == body
                                                                 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    c.put(x, y, mix(c.get(x, y), glow, 0.6))
    # steel band across the equator, with rivets
    steel_hi, steel, steel_lo = rgb("#d6dbe2"), rgb("#8d949e"), rgb("#555b66")
    band = 9
    for x in range(16):
        if c.get(x, band) is None and c.get(x, band + 1) is None:
            continue
        c.put(x, band, steel_hi if 3 < x < 12 else steel)
        c.put(x, band + 1, steel_lo if x > 3 else steel)
    for x in (4, 11):
        c.put(x, band, rgb("#3c4049"))
    c.put(7, band, rgb("#ffd34d") if not full else rgb("#7dff8a"))  # status light
    c.put(8, band, rgb("#ffb000") if not full else rgb("#35d24a"))
    # highlights on the glass
    for x, y in ((4, 2), (3, 3), (2, 4)):
        c.put(x, y, rgb("#ffffff"))
    c.outline()
    return c


# ---------------------------------------------------------------- rays

def ray(core, mid, deep):
    c = Canvas()
    body_hi, body, body_lo = rgb("#e3e7ec"), rgb("#a9b0ba"), rgb("#6a717d")
    # grip (bottom left)
    for y in range(16):
        for x in range(16):
            d, side, _ = seg_dist(x + 0.5, y + 0.5, 5.2, 10.8, 2.3, 14.2)
            if d <= 1.15:
                c.put(x, y, rgb("#3a3d45") if side > 0 else rgb("#24262c"))
    # barrel
    for y in range(16):
        for x in range(16):
            d, side, t = seg_dist(x + 0.5, y + 0.5, 3.6, 10.4, 10.6, 4.4)
            if d <= 1.55:
                col = body_hi if side < -2.5 else body if side < 3 else body_lo
                c.put(x, y, col)
    # coils along the barrel
    for (x, y) in ((5, 8), (6, 9), (4, 9), (7, 6), (8, 7), (6, 7)):
        c.put(x, y, mid)
    c.put(5, 9, deep)
    c.put(7, 7, deep)
    # trigger guard
    c.put(6, 11, rgb("#24262c"))
    c.put(7, 11, rgb("#24262c"))
    # emitter dish (top right)
    ex, ey = 12.0, 4.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - ex, y + 0.5 - ey)
            if d <= 2.9:
                c.put(x, y, deep if d > 2.1 else mid if d > 1.1 else core)
    c.put(12, 3, rgb("#ffffff"))
    c.outline()
    # sparkle outside the outline
    c.put(15, 0, core)
    return c


def draw_all():
    OUT.mkdir(parents=True, exist_ok=True)
    capsule(False).image().save(OUT / "mob_capsule.png")
    capsule(True).image().save(OUT / "mob_capsule_full.png")
    ray(rgb("#e6ffff"), rgb("#33d6ff"), rgb("#1a6fd6")).image().save(OUT / "minimizer_ray.png")
    ray(rgb("#fff3c4"), rgb("#ff7a1f"), rgb("#c8261a")).image().save(OUT / "maximizer_ray.png")


def generate(ctx):
    if any(not (OUT / f"{n}.png").exists() for n in NAMES):
        try:
            draw_all()
        except ImportError:
            print("mobs_textures: Pillow missing, run tools/features/mobs_textures.py to draw the textures")


if __name__ == "__main__":
    draw_all()
    print("wrote", ", ".join(NAMES))

#!/usr/bin/env python3
"""Texture of the Factory Ascent Manual (16x16): a leather-bound book with brass corners and a
gear stamped on the cover, a ribbon bookmark and cream page edges.

Run:  python3 tools/features/guide_textures.py

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws the texture when it is missing (Pillow is imported lazily).
"""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures/item"
NAMES = ["factory_manual"]

OUTLINE = (34, 20, 10, 255)
LEATHER = (122, 70, 34, 255)
LEATHER_HI = (150, 92, 46, 255)
LEATHER_LO = (92, 50, 22, 255)
PAGE = (240, 228, 196, 255)
PAGE_LO = (206, 190, 150, 255)
BRASS = (222, 180, 70, 255)
BRASS_LO = (170, 125, 40, 255)
RIBBON = (190, 40, 40, 255)


def draw():
    from PIL import Image
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    # book block: cover from x=2..12, y=1..13; pages show on the right edge and the bottom
    for y in range(1, 15):
        for x in range(2, 14):
            put(x, y, OUTLINE)
    for y in range(2, 13):
        for x in range(3, 12):
            shade = LEATHER_HI if x <= 4 else LEATHER_LO if x >= 10 or y >= 11 else LEATHER
            put(x, y, shade)
    # page edges (right side and bottom), with a darker line every other row
    for y in range(3, 14):
        put(12, y, PAGE if y % 2 else PAGE_LO)
    for x in range(4, 13):
        put(x, 13, PAGE if x % 2 else PAGE_LO)
    # spine highlight
    for y in range(2, 13):
        put(3, y, (170, 110, 60, 255))
    # brass corners
    for (x, y) in [(10, 2), (11, 2), (11, 3), (10, 12), (11, 12), (11, 11)]:
        put(x, y, BRASS)
    # a gear on the cover
    gear = ["..#.#..", ".#####.", "##...##", ".#.#.#.", "##...##", ".#####.", "..#.#.."]
    for gy, row in enumerate(gear):
        for gx, ch in enumerate(row):
            if ch == "#":
                put(4 + gx, 4 + gy, BRASS if (gx + gy) % 3 else BRASS_LO)
    put(7, 7, LEATHER_LO)
    # ribbon hanging out at the bottom
    put(8, 14, RIBBON)
    put(8, 15, RIBBON)
    put(9, 15, (150, 25, 25, 255))
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "factory_manual.png")


def generate(ctx):
    if any(not (OUT / f"{n}.png").exists() for n in NAMES):
        try:
            draw()
        except ImportError:
            print("guide_textures: Pillow missing, run tools/features/guide_textures.py to draw the texture")


if __name__ == "__main__":
    draw()
    print("wrote", ", ".join(NAMES))

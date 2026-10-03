#!/usr/bin/env python3
"""Item textures of the station payloads: station_kit (a folded module crate) and cargo_pod (a freight canister), 16x16.

Run:  python3 tools/features/stationkit_textures.py

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily).
"""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures/item"
NAMES = ["station_kit", "cargo_pod"]
OUTLINE = (24, 26, 34, 255)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def canvas():
    return [[None] * 16 for _ in range(16)]


def outline(px):
    edge = []
    for y in range(16):
        for x in range(16):
            if px[y][x] is None and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[y + dy][x + dx] not in (None, OUTLINE)
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge:
        px[y][x] = OUTLINE


def save(px, name):
    from PIL import Image
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if px[y][x] is not None:
                img.putpixel((x, y), px[y][x])
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png")


def station_kit():
    """A folded module seen at three quarters: white hull panels, a round window, hazard-striped hinge, a cyan core light."""
    px = canvas()
    white, shade, dark = rgb("e8ecef"), rgb("b9c1c8"), rgb("7d8892")
    for y in range(3, 14):
        for x in range(2, 14):
            px[y][x] = white if x < 10 else shade
    # top face (lighter, a lid seam)
    for x in range(3, 15):
        px[2][x] = rgb("f7f9fa")
    for y in range(3, 14):
        px[y][14] = dark
    for x in range(2, 14):
        px[8][x] = dark  # the fold seam
    # hazard stripes along the hinge
    for x in range(2, 14):
        px[9][x] = rgb("f2a33a") if (x // 2) % 2 == 0 else rgb("2a2a2a")
    # round window
    for y, row in enumerate(["  ##  ", " #oo# ", " #oo# ", "  ##  "]):
        for x, c in enumerate(row):
            if c == "#":
                px[3 + y][3 + x] = rgb("5e6a75")
            elif c == "o":
                px[3 + y][3 + x] = rgb("7fc8ff") if x < 3 else rgb("4a8fd0")
    # airlock outline on the lower half
    for y in range(10, 14):
        px[y][10] = rgb("5e6a75")
        px[y][12] = rgb("5e6a75")
    px[10][11] = rgb("5e6a75")
    px[12][11] = rgb("ffd84a")  # handle light
    # station core light
    px[5][11] = rgb("4af0ff")
    px[5][12] = rgb("1bb7d6")
    px[4][11] = rgb("a8fbff")
    outline(px)
    return px


def cargo_pod():
    """A squat freight canister: titanium shell with orange bands, a hatch with a latch and a heat-shield base."""
    px = canvas()
    for y in range(2, 13):
        for x in range(4, 12):
            t = (x - 4) / 7
            c = rgb("d5d9de") if t < 0.35 else rgb("aab2bb") if t < 0.75 else rgb("7f8995")
            px[y][x] = c
    for x in range(5, 11):
        px[1][x] = rgb("c3c9cf")  # rounded top
    for y in (4, 10):
        for x in range(4, 12):
            px[y][x] = rgb("f08a24") if x < 9 else rgb("c06a14")
    # hatch with latch
    for y in range(6, 9):
        for x in range(6, 10):
            px[y][x] = rgb("8c96a1")
    px[7][9] = rgb("ffd84a")
    # heat shield base
    for x in range(3, 13):
        px[13][x] = rgb("5b3a26")
        px[14][x] = rgb("3c261a") if 4 <= x <= 11 else None
    outline(px)
    return px


def draw_all():
    save(station_kit(), "station_kit")
    save(cargo_pod(), "cargo_pod")


def generate(ctx):
    if any(not (OUT / f"{n}.png").exists() for n in NAMES):
        try:
            draw_all()
        except ImportError:
            print("stationkit_textures: Pillow missing, run tools/features/stationkit_textures.py to draw the textures")


if __name__ == "__main__":
    draw_all()
    print("wrote", ", ".join(NAMES))

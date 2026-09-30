#!/usr/bin/env python3
"""Textures of the space station blocks:

  block/  station_hull_{white,gray,dark,orange}, station_window, airlock_door_{top,bottom},
          docking_port_{top,side}, station_core_{top,side}, air_vent_{top,top_on,side}
  item/   airlock_door, magnetic_boots

Run:  python3 tools/features/stations_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that
only draws textures that are missing (Pillow is imported lazily).
"""
import importlib.util
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def _orbital():
    spec = importlib.util.spec_from_file_location("orbital_textures_for_stations", Path(__file__).with_name("orbital_textures.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


O = _orbital()
rgb, mix, shade, Canvas, noisy, rivet = O.rgb, O.mix, O.shade, O.Canvas, O.noisy, O.rivet
STEEL, DARK, HAZ_Y, HAZ_K, CYAN = O.STEEL, O.DARK, O.HAZ_Y, O.HAZ_K, O.CYAN

HULL = {
    "white": [rgb(h) for h in ("#FFFFFF", "#E8ECF0", "#CDD3DA", "#A4ACB6", "#7A838E")],
    "gray": [rgb(h) for h in ("#CED4DC", "#A8B0BA", "#8C949E", "#6C747E", "#4E555E")],
    "dark": [rgb(h) for h in ("#7C8490", "#5C636E", "#474D57", "#353A42", "#23272D")],
    "orange": [rgb(h) for h in ("#FFC890", "#F0923E", "#D8762A", "#AE5A1C", "#7C3E12")],
}
GLASS = [rgb("#D8F0FF", 150), rgb("#A8D4F4", 110), rgb("#7CB4E0", 100)]


def hull(color):
    """A hull panel: seams on the edges, a bevel, four rivets and a small access hatch outline."""
    pal = HULL[color]
    rng = random.Random(sum(map(ord, color)) * 7)
    c = Canvas()
    noisy(c, pal, rng, base=1, p=0.08)
    for i in range(16):
        c.put(i, 0, pal[3])
        c.put(0, i, pal[3])
        c.put(i, 15, pal[4])
        c.put(15, i, pal[4])
    c.bevel(1, 1, 14, 14, pal[0], pal[2])
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(c, x, y, [pal[0], pal[1], pal[3], pal[4]])
    for x in range(5, 11):  # access hatch
        c.put(x, 6, pal[3])
        c.put(x, 10, pal[0])
    for y in range(6, 11):
        c.put(5, y, pal[3])
        c.put(10, y, pal[0])
    if color in ("white", "gray"):
        for x in range(1, 15):  # a thin orange stripe near the bottom, station livery
            c.put(x, 13, rgb("#E07A28"))
    return c


def station_window():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            frame = x < 2 or y < 2 or x > 13 or y > 13
            c.put(x, y, (STEEL[2] if (x < 1 or y < 1) else STEEL[1]) if frame else GLASS[1])
    for i in range(2, 8):  # glints
        c.put(i + 2, 11 - i, GLASS[0])
        c.put(i + 3, 11 - i, GLASS[0])
    c.put(11, 4, GLASS[0])
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.put(x, y, STEEL[3])
    return c


def airlock(top):
    c = Canvas()
    noisy(c, STEEL, random.Random(71 if top else 72), base=1, p=0.08)
    for y in range(16):
        c.put(0, y, DARK[2])
        c.put(15, y, DARK[2])
    if top:
        for x in range(16):
            c.put(x, 0, DARK[2])
        for y in range(4, 11):  # round porthole
            for x in range(4, 12):
                if (x - 7.5) ** 2 + (y - 7) ** 2 <= 12:
                    c.put(x, y, GLASS[1] if (x - 7.5) ** 2 + (y - 7) ** 2 <= 7 else DARK[1])
        c.put(6, 5, GLASS[0])
    else:
        for x in range(16):
            c.put(x, 15, DARK[3])
        for y in range(10, 14):  # hazard band
            for x in range(1, 15):
                c.put(x, y, HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K)
        for y in range(3, 7):  # wheel handle
            c.put(11, y, DARK[2])
        c.put(10, 4, DARK[1])
        c.put(12, 5, DARK[1])
        c.put(2, 5, rgb("#40E060"))  # "sealed" light
    return c


def airlock_item():
    c = Canvas()
    for y in range(1, 15):
        for x in range(4, 12):
            c.put(x, y, STEEL[1] if x < 8 else STEEL[2])
    for y in range(3, 7):
        for x in range(6, 10):
            c.put(x, y, O.CYAN[2] if (x, y) != (6, 3) else O.CYAN[0])
    for y in range(11, 14):
        for x in range(4, 12):
            c.put(x, y, HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K)
    c.outline()
    return c


def docking_port_top():
    c = Canvas()
    noisy(c, DARK, random.Random(81), base=2, p=0.1)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.2 <= d <= 7.3:
                ang = (x - 7.5) * 1.3 + (y - 7.5)
                c.put(x, y, HAZ_Y if int(ang + 20) % 4 < 2 else HAZ_K)
            elif d < 3.2:
                c.put(x, y, STEEL[1] if d < 2.2 else STEEL[3])
    for x, y in ((7, 1), (1, 7), (14, 7), (7, 14)):  # clamps
        c.put(x, y, STEEL[0])
        c.put(x + 1 if x < 14 else x, y, STEEL[2])
    c.put(7, 7, rgb("#40E060"))
    c.put(8, 8, rgb("#40E060"))
    return c


def docking_port_side():
    c = Canvas()
    noisy(c, DARK, random.Random(82), base=1, p=0.1)
    for x in range(16):
        c.put(x, 0, STEEL[1])
        c.put(x, 15, DARK[4])
        for y in (2, 3):
            c.put(x, y, HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K)
    for x in (3, 7, 11):
        c.put(x, 8, rgb("#FFD040"))
        c.put(x + 1, 8, rgb("#FFE890"))
    return c


def station_core(top):
    c = Canvas()
    noisy(c, STEEL, random.Random(91 if top else 92), base=2, p=0.08)
    c.bevel(0, 0, 15, 15, STEEL[0], STEEL[4])
    if top:
        for y in range(3, 13):
            for x in range(3, 13):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d < 4.8:
                    c.put(x, y, CYAN[0] if d < 1.6 else CYAN[1] if d < 3 else CYAN[2])
                elif d < 5.6:
                    c.put(x, y, DARK[3])
        for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
            rivet(c, x, y, STEEL)
    else:
        for y in range(2, 14):
            for x in (3, 12):
                c.put(x, y, CYAN[1] if y % 3 else CYAN[0])
        for y in range(4, 12):
            for x in range(5, 11):
                c.put(x, y, DARK[3] if (y - 4) % 3 else DARK[1])
        c.put(7, 13, rgb("#40E060"))
        c.put(8, 13, rgb("#40E060"))
    return c


def air_vent_top(on):
    c = Canvas()
    noisy(c, STEEL, random.Random(101), base=1, p=0.08)
    c.bevel(0, 0, 15, 15, STEEL[0], STEEL[3])
    for y in range(3, 13, 2):
        for x in range(3, 13):
            c.put(x, y, DARK[3])
            c.put(x, y + 1, (CYAN[1] if (x + y) % 5 else CYAN[0]) if on else DARK[1])
    return c


def air_vent_side():
    c = Canvas()
    noisy(c, STEEL, random.Random(102), base=2, p=0.08)
    c.bevel(0, 0, 15, 15, STEEL[0], STEEL[4])
    for x in range(2, 14):
        c.put(x, 12, rgb("#3E8EE8"))
    for x, y in ((2, 2), (12, 2)):
        rivet(c, x, y, STEEL)
    return c


def magnetic_boots():
    c = Canvas()
    white = [rgb(h) for h in ("#FFFFFF", "#E4E8EE", "#B8C0CA", "#88909C")]
    for (x0, x1) in ((2, 7), (9, 14)):
        for y in range(3, 11):
            for x in range(x0 + 1, x1 - 1):
                c.put(x, y, white[1 if x < (x0 + x1) // 2 else 2])
        for y in range(10, 13):
            for x in range(x0, x1):
                c.put(x, y, white[2])
        for x in range(x0, x1):  # magnet sole: red and blue halves
            c.put(x, 13, rgb("#D83838") if x < (x0 + x1) // 2 else rgb("#3860D8"))
            c.put(x, 14, rgb("#8A1C1C") if x < (x0 + x1) // 2 else rgb("#1C3A8A"))
        c.put(x0 + 2, 5, rgb("#E07A28"))
    c.outline()
    return c


BLOCKS = {f"station_hull_{col}": (lambda col=col: hull(col)) for col in HULL}
BLOCKS.update({
    "station_window": station_window, "airlock_door_top": lambda: airlock(True), "airlock_door_bottom": lambda: airlock(False),
    "docking_port_top": docking_port_top, "docking_port_side": docking_port_side,
    "station_core_top": lambda: station_core(True), "station_core_side": lambda: station_core(False),
    "air_vent_top": lambda: air_vent_top(False), "air_vent_top_on": lambda: air_vent_top(True), "air_vent_side": air_vent_side,
})
ITEMS = {"airlock_door": airlock_item, "magnetic_boots": magnetic_boots}


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
    print(f"wrote {len(BLOCKS) + len(ITEMS)} station textures")

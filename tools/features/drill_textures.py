#!/usr/bin/env python3
"""Textures of the 3D Electric Drill (tools/features/drill.py builds the model from them).

Run:  python3 tools/features/drill_textures.py

Writes into src/main/resources/assets/factoryascent/textures/item/:
  electric_drill_parts.png      32x32 atlas: casing, motor cap, nose, chuck, grip, trigger, battery
  electric_drill_bit.png        16x16 steel spiral of the bit
  electric_drill_bit_spin.png   16x64 animated strip (4 frames, the spiral sliding = spinning) + .mcmeta
  electric_drill_glow.png       16x16 charged indicator band (lit)
  electric_drill_glow_off.png   16x16 the same band, dark (empty drill)
  electric_drill_icon.png       16x16 flat inventory sprite (side view of the same drill)

All of these belong to this script, so it always redraws them. gen_resources.py loads every
tools/features/*.py and calls generate(ctx); Pillow is imported lazily so resources still generate
without it once the PNGs exist.
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures/item"
NAMES = ["electric_drill_parts", "electric_drill_bit", "electric_drill_bit_spin", "electric_drill_glow",
         "electric_drill_glow_off", "electric_drill_icon"]


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# Palette: industrial yellow casing (matches the old flat drill), gunmetal, steel, rubber, cyan glow.
Y_HI, Y, Y_MID, Y_LO, Y_DK = rgb("fff08a"), rgb("f2c21b"), rgb("dba112"), rgb("a8740c"), rgb("6e4a08")
G_HI, G, G_MID, G_LO, G_DK = rgb("6d737c"), rgb("4b5058"), rgb("3a3e45"), rgb("2a2d33"), rgb("1b1d21")
S_HI, S, S_MID, S_LO, S_DK = rgb("f2f5f8"), rgb("c9cfd6"), rgb("9ea6af"), rgb("6f7780"), rgb("454b52")
R_HI, R, R_LO = rgb("3a3c42"), rgb("26272b"), rgb("17181a")
RED_HI, RED, RED_LO = rgb("ff6a55"), rgb("d8362a"), rgb("8e1f18")
GREEN, GREEN_DK = rgb("5cf07a"), rgb("1f6b33")
CYAN_HI, CYAN, CYAN_LO = rgb("e6ffff"), rgb("5ff3ff"), rgb("1fb6d6")
OFF_HI, OFF, OFF_LO = rgb("47636b"), rgb("30464d"), rgb("223237")
OUTLINE = rgb("16171b")


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
        """Fills [x0, x1) x [y0, y1)."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)

    def bevel(self, x0, y0, x1, y1, hi, mid, lo):
        """A panel lit from the top-left: light top/left edge, dark bottom/right edge."""
        self.rect(x0, y0, x1, y1, mid)
        for x in range(x0, x1):
            self.put(x, y0, hi)
            self.put(x, y1 - 1, lo)
        for y in range(y0, y1):
            self.put(x0, y, hi if y < y1 - 1 else lo)
            self.put(x1 - 1, y, lo)

    def outline(self, color=OUTLINE):
        """Dark 1-px outline around the drawn shape (4-neighbourhood)."""
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


# ---------------------------------------------------------------- atlas (32x32, 1 texel per model pixel)
# Regions (x0, y0, x1, y1) in atlas pixels; drill.py maps model faces onto them (uv = pixels / 2).
REGIONS = {
    "casing_side": (0, 0, 8, 5),      # housing east/west, 8 long (z) x 5 tall
    "casing_top": (8, 0, 13, 8),      # housing top, 5 wide x 8 long
    "casing_bottom": (13, 0, 18, 8),  # housing underside
    "casing_front": (18, 0, 23, 5),   # housing face behind the nose
    "casing_back": (23, 0, 28, 5),    # housing back (mostly under the motor cap)
    "cap_back": (0, 8, 4, 12),        # motor cap vent grille
    "cap_side": (28, 0, 29, 4),       # motor cap rim
    "nose_side": (4, 8, 7, 11),       # gearbox sides/top/bottom (3 x 3; sides use 2.5 of it)
    "nose_front": (7, 8, 10, 11),     # gearbox front
    "chuck_side": (10, 8, 12, 10),    # knurled chuck
    "chuck_front": (12, 8, 14, 10),   # chuck jaws
    "grip_side": (0, 12, 4, 18),      # rubber grip sides, 3.5 long x 6 tall
    "grip_front": (4, 12, 7, 18),     # grip front/back, 3 wide x 6 tall
    "trigger": (7, 12, 9, 14),        # red trigger
    "battery_side": (0, 18, 7, 21),   # battery pack sides, 7 long x 3 tall (charge LEDs)
    "battery_top": (8, 14, 14, 21),   # battery top/bottom, 6 wide x 7 long
    "battery_end": (14, 14, 20, 17),  # battery front/back, 6 wide x 3 tall
    "bumper": (20, 14, 22, 16),       # rubber bumper on the housing top
}


def atlas():
    c = Canvas(32, 32)
    r = REGIONS

    # Housing sides: yellow casing, dark trim along the bottom, vent slits at the back, a black hazard stripe
    # where the glow band sits, and a small cyan brand dot.
    x0, y0, x1, y1 = r["casing_side"]
    c.bevel(x0, y0, x1, y1, Y_HI, Y, Y_LO)
    for x in range(x0, x1):
        c.put(x, y1 - 1, Y_DK)
    for x in range(x0 + 1, x1 - 1):
        c.put(x, y0 + 1, Y if x % 3 else Y_HI)
    for vy in (y0 + 1, y0 + 3):  # vents near the motor (back = high z = right in this region)
        for vx in (x0 + 5, x0 + 6):
            c.put(vx, vy, G_DK)
    c.put(x0 + 1, y0 + 2, Y_MID)
    c.put(x0 + 2, y0 + 2, rgb("2f8fa3"))

    # Housing top: yellow with a dark centre rib and screws.
    x0, y0, x1, y1 = r["casing_top"]
    c.bevel(x0, y0, x1, y1, Y_HI, Y, Y_LO)
    for y in range(y0 + 1, y1 - 1):
        c.put(x0 + 2, y, Y_MID if y % 2 else Y_LO)
    c.put(x0 + 1, y0 + 1, S_MID)
    c.put(x1 - 2, y1 - 2, S_MID)

    # Underside: gunmetal.
    x0, y0, x1, y1 = r["casing_bottom"]
    c.bevel(x0, y0, x1, y1, G_HI, G_MID, G_DK)

    # Front of the housing: dark face ring around the nose.
    x0, y0, x1, y1 = r["casing_front"]
    c.bevel(x0, y0, x1, y1, Y, Y_MID, Y_LO)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, G_LO)

    # Housing back.
    x0, y0, x1, y1 = r["casing_back"]
    c.bevel(x0, y0, x1, y1, Y, Y_MID, Y_DK)

    # Motor cap: gunmetal grille.
    x0, y0, x1, y1 = r["cap_back"]
    c.bevel(x0, y0, x1, y1, G_HI, G, G_DK)
    for y in (y0 + 1, y0 + 2):
        for x in (x0 + 1, x0 + 2):
            c.put(x, y, G_DK if (x + y) % 2 else R)
    x0, y0, x1, y1 = r["cap_side"]
    for y in range(y0, y1):
        c.put(x0, y, G_HI if y == y0 else G)

    # Gearbox nose: gunmetal with a bolt in each corner.
    x0, y0, x1, y1 = r["nose_side"]
    c.bevel(x0, y0, x1, y1, G_HI, G, G_LO)
    c.put(x0 + 1, y0 + 1, S_MID)
    x0, y0, x1, y1 = r["nose_front"]
    c.bevel(x0, y0, x1, y1, G_HI, G_MID, G_LO)
    c.put(x0 + 1, y0 + 1, G_DK)

    # Chuck: knurled steel, dark jaws on the front.
    x0, y0, x1, y1 = r["chuck_side"]
    c.rect(x0, y0, x1, y1, S)
    c.put(x0, y0, S_HI)
    c.put(x0 + 1, y0 + 1, S_LO)
    x0, y0, x1, y1 = r["chuck_front"]
    c.rect(x0, y0, x1, y1, S_MID)
    c.put(x0, y0, S_DK)
    c.put(x0 + 1, y0 + 1, S_DK)

    # Grip: black rubber with ridges, a yellow finger guard at the top.
    x0, y0, x1, y1 = r["grip_side"]
    c.rect(x0, y0, x1, y1, R)
    for y in range(y0 + 1, y1, 2):
        for x in range(x0, x1):
            c.put(x, y, R_HI if x < x1 - 1 else R)
    for x in range(x0, x1):
        c.put(x, y0, Y_LO)
    x0, y0, x1, y1 = r["grip_front"]
    c.rect(x0, y0, x1, y1, R)
    for y in range(y0 + 1, y1, 2):
        c.put(x0 + 1, y, R_HI)
    for x in range(x0, x1):
        c.put(x, y0, Y_LO)

    # Trigger.
    x0, y0, x1, y1 = r["trigger"]
    c.rect(x0, y0, x1, y1, RED)
    c.put(x0, y0, RED_HI)
    c.put(x1 - 1, y1 - 1, RED_LO)

    # Battery sides: gunmetal pack, yellow stripe, four green charge LEDs.
    x0, y0, x1, y1 = r["battery_side"]
    c.bevel(x0, y0, x1, y1, G_HI, G, G_DK)
    for x in range(x0, x1):
        c.put(x, y0, Y_MID)
    for i in range(4):
        c.put(x0 + 1 + i, y0 + 1, GREEN if i < 3 else GREEN_DK)
    x0, y0, x1, y1 = r["battery_top"]
    c.bevel(x0, y0, x1, y1, G_HI, G_MID, G_DK)
    for y in range(y0 + 1, y1 - 1, 2):
        c.put(x0 + 2, y, G_LO)
        c.put(x0 + 3, y, G_LO)
    x0, y0, x1, y1 = r["battery_end"]
    c.bevel(x0, y0, x1, y1, G_HI, G, G_DK)
    c.rect(x0 + 2, y0 + 1, x0 + 4, y0 + 2, Y_MID)  # release latch

    x0, y0, x1, y1 = r["bumper"]
    c.rect(x0, y0, x1, y1, R)
    c.put(x0, y0, R_HI)
    return c


# ---------------------------------------------------------------- bit
def bit_frame(phase):
    """Steel spiral: bright diagonal flute edges on darker steel, shifted by `phase` px (0..3)."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = (x + y + phase) % 4
            c.put(x, y, (S_HI, S, S_MID, S_LO)[k])
    return c


def glow(lit):
    c = Canvas(16, 16)
    hi, mid, lo = (CYAN_HI, CYAN, CYAN_LO) if lit else (OFF_HI, OFF, OFF_LO)
    for y in range(16):
        for x in range(16):
            c.put(x, y, hi if y % 4 == 0 else (mid if (x + y) % 5 else lo))
    return c


# ---------------------------------------------------------------- inventory sprite
def icon():
    """Side view, bit to the right: motor housing, glow band, pistol grip + trigger, battery pack."""
    c = Canvas(16, 16)
    # Motor housing (x 2..9, rows 3..7)
    c.rect(2, 3, 10, 8, Y)
    for x in range(2, 10):
        c.put(x, 3, Y_HI)
        c.put(x, 7, Y_LO)
    c.put(2, 4, Y_HI)
    c.put(3, 5, G_DK)
    c.put(4, 5, G_DK)
    # Motor cap at the back
    c.rect(1, 4, 2, 7, G)
    c.put(1, 4, G_HI)
    # Glow band
    for y in range(3, 8):
        c.put(6, y, CYAN if y not in (3, 7) else CYAN_LO)
    c.put(6, 4, CYAN_HI)
    # Nose + chuck
    c.rect(10, 4, 11, 7, G)
    c.put(10, 4, G_HI)
    c.rect(11, 5, 12, 7, S)
    c.put(11, 5, S_HI)
    # Spiral bit
    for x in range(12, 16):
        c.put(x, 5, S_HI if x % 2 == 0 else S_MID)
        c.put(x, 6, S_LO if x % 2 == 0 else S)
    c.put(15, 6, (0, 0, 0, 0))
    # Pistol grip, slanted back (x 4..6, rows 8..12)
    for i, y in enumerate(range(8, 12)):
        off = -1 if i >= 2 else 0
        c.put(5 + off, y, R_HI)
        c.put(6 + off, y, R)
        c.put(7 + off, y, R_LO if i < 2 else R)
    # Trigger
    c.put(8, 8, RED)
    c.put(8, 9, RED_LO)
    # Battery pack (x 2..9, rows 12..13)
    c.rect(2, 12, 10, 14, G)
    for x in range(2, 10):
        c.put(x, 12, Y_MID)
    c.put(3, 13, GREEN)
    c.put(4, 13, GREEN)
    c.put(5, 13, GREEN_DK)
    c.outline()
    return c


def draw_all():
    OUT.mkdir(parents=True, exist_ok=True)
    atlas().image().save(OUT / "electric_drill_parts.png")
    bit_frame(0).image().save(OUT / "electric_drill_bit.png")
    from PIL import Image
    strip = Image.new("RGBA", (16, 64))
    for i in range(4):
        strip.paste(bit_frame(i).image(), (0, 16 * i))
    strip.save(OUT / "electric_drill_bit_spin.png")
    (OUT / "electric_drill_bit_spin.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 1}}, indent=2) + "\n")
    glow(True).image().save(OUT / "electric_drill_glow.png")
    glow(False).image().save(OUT / "electric_drill_glow_off.png")
    icon().image().save(OUT / "electric_drill_icon.png")


def generate(ctx):
    try:
        draw_all()
    except ImportError:
        missing = [n for n in NAMES if not (OUT / f"{n}.png").exists()]
        if missing:
            print(f"drill_textures: Pillow missing, cannot draw {', '.join(missing)}")


if __name__ == "__main__":
    draw_all()
    print(f"wrote {len(NAMES)} drill textures to {OUT}")

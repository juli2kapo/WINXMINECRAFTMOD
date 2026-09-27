#!/usr/bin/env python3
"""Textures of the 3D Electric Drill (tools/features/drill.py builds the model from them).

Run:  python3 tools/features/drill_textures.py

Writes into src/main/resources/assets/factoryascent/textures/item/:
  electric_drill_parts.png      32x32 atlas: motor casing, collar, handles, grip, power pack
  electric_drill_bit.png        16x16 steel flutes of the conical drill head
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
    "casing_side": (0, 0, 8, 6),      # motor body east/west: 8 long (z) x 6 tall
    "casing_top": (8, 0, 15, 8),      # motor body top/bottom: 7 wide x 8 long
    "casing_end": (15, 0, 22, 6),     # motor body front/back: 7 wide x 6 tall
    "collar_front": (0, 8, 8, 15),    # steel collar holding the cone: 8 x 7
    "collar_edge": (8, 8, 9, 15),     # collar rim (1 px deep)
    "handle": (10, 8, 12, 10),        # gunmetal handle posts and bars
    "grip": (12, 8, 14, 14),          # rubber-wrapped rear grip
    "battery_side": (0, 16, 3, 22),   # power pack east/west: 3 long x 6 tall, charge LEDs
    "battery_top": (4, 16, 10, 19),   # power pack top/bottom: 6 wide x 3 long
    "battery_back": (10, 16, 16, 22), # power pack back: 6 x 6, LED row and hazard stripe
}


def atlas():
    c = Canvas(32, 32)
    r = REGIONS

    # Motor body sides: yellow casing, gunmetal skirt, vent slits in front of the band, rivets.
    x0, y0, x1, y1 = r["casing_side"]
    c.bevel(x0, y0, x1, y1, Y_HI, Y, Y_LO)
    for x in range(x0, x1):
        c.put(x, y1 - 1, G_LO)
        c.put(x, y1 - 2, Y_DK if x % 2 else Y_LO)
    for vx in (x0 + 1, x0 + 3):  # vents at the front half (west face: left = front)
        for vy in (y0 + 1, y0 + 2, y0 + 3):
            c.put(vx, vy, G_DK)
    c.put(x1 - 2, y0 + 1, S_MID)
    c.put(x1 - 2, y1 - 3, S_MID)

    # Top / bottom: yellow with a raised steel spine.
    x0, y0, x1, y1 = r["casing_top"]
    c.bevel(x0, y0, x1, y1, Y_HI, Y, Y_LO)
    for y in range(y0, y1):
        c.put(x0 + 3, y, S_MID if y % 2 else S_LO)
    for y in (y0 + 1, y0 + 3, y0 + 5):
        c.put(x0 + 1, y, G_DK)
        c.put(x1 - 2, y, G_DK)

    # Front/back plates.
    x0, y0, x1, y1 = r["casing_end"]
    c.bevel(x0, y0, x1, y1, Y, Y_MID, Y_DK)
    c.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, G_LO)

    # Steel collar: bright ring with bolts.
    x0, y0, x1, y1 = r["collar_front"]
    c.bevel(x0, y0, x1, y1, S_HI, S, S_LO)
    c.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, S_MID)
    for bx, by in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
        c.put(bx, by, S_DK)
    x0, y0, x1, y1 = r["collar_edge"]
    for y in range(y0, y1):
        c.put(x0, y, S if y % 2 else S_MID)

    # Handles.
    x0, y0, x1, y1 = r["handle"]
    c.rect(x0, y0, x1, y1, G)
    c.put(x0, y0, G_HI)
    c.put(x1 - 1, y1 - 1, G_LO)
    x0, y0, x1, y1 = r["grip"]
    c.rect(x0, y0, x1, y1, R)
    for y in range(y0, y1, 2):
        c.put(x0, y, R_HI)
        c.put(x0 + 1, y, R_HI)

    # Power pack: gunmetal, yellow hazard stripe, green charge LEDs.
    x0, y0, x1, y1 = r["battery_side"]
    c.bevel(x0, y0, x1, y1, G_HI, G, G_DK)
    for y in range(y0 + 1, y0 + 5):
        c.put(x0 + 1, y, GREEN if y > y0 + 1 else GREEN_DK)
    x0, y0, x1, y1 = r["battery_top"]
    c.bevel(x0, y0, x1, y1, G_HI, G_MID, G_DK)
    x0, y0, x1, y1 = r["battery_back"]
    c.bevel(x0, y0, x1, y1, G_HI, G, G_DK)
    for x in range(x0, x1):
        c.put(x, y1 - 2, Y if (x // 1) % 2 else G_DK)
    for i in range(4):
        c.put(x0 + 1 + i, y0 + 1, GREEN if i < 3 else GREEN_DK)
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
    """Charge band: an even glow with a few brighter sparkles (any sub-rectangle reads the same)."""
    c = Canvas(16, 16)
    hi, mid, lo = (CYAN_HI, CYAN, CYAN_LO) if lit else (OFF_HI, OFF, OFF_LO)
    for y in range(16):
        for x in range(16):
            k = (x * 7 + y * 3) % 11
            c.put(x, y, hi if k == 0 else lo if k == 5 else mid)
    return c


# ---------------------------------------------------------------- inventory sprite
def icon():
    """Side view: big fluted cone on the right, steel collar, yellow motor body with the cyan band,
    carry handle on top, power pack and grip at the back."""
    c = Canvas(16, 16)
    # Motor body (x 2..7, rows 5..10), cyan band at x 5.
    c.rect(2, 5, 8, 11, Y)
    for x in range(2, 8):
        c.put(x, 5, Y_HI)
        c.put(x, 10, Y_LO)
    c.put(3, 7, G_DK)
    c.put(3, 8, G_DK)
    for y in range(5, 11):
        c.put(5, y, CYAN if 5 < y < 10 else CYAN_LO)
    c.put(5, 6, CYAN_HI)
    # Carry handle over the body.
    for x in range(3, 8):
        c.put(x, 3, G_HI if x < 5 else G)
    c.put(3, 4, G)
    c.put(7, 4, G_LO)
    # Power pack + rear grip.
    c.rect(1, 6, 2, 10, G)
    c.put(1, 6, G_HI)
    c.put(1, 8, GREEN)
    c.put(0, 5, R_HI)
    for y in range(6, 11):
        c.put(0, y, R)
    # Steel collar (x 8, rows 4..11).
    for y in range(4, 12):
        c.put(8, y, S_HI if y < 6 else S if y < 10 else S_LO)
    # Cone: base 8 px tall at x 9, tip at x 15, with diagonal flutes.
    for x in range(9, 16):
        half = max(0.5, 4 * (15.5 - x) / 6.5)
        for y in range(16):
            if abs(y + 0.5 - 8) <= half:
                k = (x + y) % 3
                col = (S_HI, S, S_LO)[k]
                if y + 0.5 > 8 + half - 1:
                    col = S_DK if k else S_LO
                c.put(x, y, col)
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

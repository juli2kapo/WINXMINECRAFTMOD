#!/usr/bin/env python3
"""Factory Ascent texture generator.

Deterministically draws every block and item texture of the mod as 16x16
vanilla-style pixel art and writes them to
src/main/resources/assets/factoryascent/textures/{block,item}/, then runs
tools/models/gen_models.py, which writes the hand-built machine block models
(tools/models/block/*.json, copied in by gen_resources.py) and renders
tools/model_preview.png.

Run:  python3 tools/gen_textures.py            (textures + models + previews)
      python3 tools/gen_textures.py --prune    (also delete textures this script
                                                no longer draws; storage_* is kept)
      python3 tools/gen_textures.py --no-preview --no-models
      python3 tools/gen_textures.py "--sheet=<regex>:<out.png>"   (debug sheet)

Layout of this file
  1. colour helpers + palettes (tiers, materials, rocks)
  2. Tex canvas + drawing primitives (rects, masks, blobs, bars, sprites)
  3. blocks: casing primitives; 3b. the v2 machines by age (LAYOUT = the
     windows carved into each machine model, shared with gen_models.py),
     bricks, crates, energy cells; cable/pipe; ores; storage blocks
  4. items (raw, dusts, ingots, plates, gears, rods, wires, molds,
     circuits, frames, components, upgrades, tools)
  5. main + contact sheet

Ages set the machine palette: stone age = granite, clay brick and wood;
bronze age = riveted bronze (coke oven / blast furnace = their own bricks);
electric age = cool grey-blue steel; automation age = light aluminium.
Energy cells and conduits keep the v1 tier colours per stage: basic (iron
grey), reinforced (steel blue-grey), advanced (copper), elite (violet),
ultimate (cyan on near-black).

Every texture gets its own RNG seeded from its file name, so reruns are
identical and editing one texture never changes another.
"""
import math
import os
import random
import re
import sys
import zlib

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "factoryascent", "textures")
PREVIEW = os.path.join(ROOT, "tools", "texture_preview.png")

# =============================================================================
# 1. Colours & palettes
# =============================================================================

CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    """'#RRGGBB' -> RGBA tuple (tuples pass through)."""
    if isinstance(h, tuple):
        return h if len(h) == 4 else (h[0], h[1], h[2], a)
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    a, b = C(a), C(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def lighten(c, t):
    return mix(c, "#FFFFFF", t)


def darken(c, t):
    return mix(c, "#000000", t)


def ramp(*hexes):
    return [C(h) for h in hexes]


class Tier:
    """Casing palette of one machine tier.

    frame = [hi, light, mid, dark, deep]  (bevelled outer frame, rivets)
    panel = [hi, light, mid, dark, deep]  (flat panel surface inside the frame)
    accent = colour used for small tier-coloured details on dark backgrounds
    """

    def __init__(self, n, name, frame, panel, accent, glow=False):
        self.n, self.name = n, name
        self.frame = ramp(*frame)
        self.panel = ramp(*panel)
        self.accent = C(accent)
        self.glow = glow


TIERS = {
    1: Tier(1, "basic",
            ["#F4F4F4", "#D8D8D8", "#A8A8A8", "#6E6E6E", "#484848"],
            ["#E2E2E2", "#CACACA", "#B9B9B9", "#999999", "#7A7A7A"],
            "#D8D8D8"),
    2: Tier(2, "reinforced",
            ["#A4AEBE", "#7A8494", "#545C6A", "#353B45", "#22262D"],
            ["#8A94A4", "#737D8C", "#646D7C", "#4C5461", "#393F49"],
            "#9AA6B8"),
    3: Tier(3, "advanced",
            ["#F6BC8A", "#E0955A", "#B06A38", "#6E3E1E", "#472510"],
            ["#D89A68", "#C4824E", "#B27243", "#8E5630", "#6A3E22"],
            "#F0A868"),
    4: Tier(4, "elite",
            ["#CDAEF0", "#A77BDA", "#7A4FB0", "#4A2E70", "#2E1B48"],
            ["#9C74CC", "#8961BC", "#7952AA", "#5E3E8A", "#45306A"],
            "#C49CF0"),
    5: Tier(5, "ultimate",
            ["#9AF8F2", "#4FD8D0", "#27A09C", "#17605F", "#0C3536"],
            ["#3A444A", "#2C343A", "#22292E", "#181D21", "#0F1215"],
            "#6FF0E8", glow=True),
}

# neutral steel used for mechanical parts (rollers, rams, pipes)
STEEL = ramp("#E2E6EC", "#B4BAC4", "#868E9A", "#5A606A", "#373B42")
DARK_STEEL = ramp("#8C94A2", "#687080", "#4C5260", "#353A44", "#22252C")
VOID = C("#141418")      # machine interiors
VOID2 = C("#0C0C0F")

LED_OFF = ramp("#3A0E0E", "#6A1616", "#8E2424")      # dark red, lit edge
LED_ON = ramp("#1E8A2E", "#3CE05A", "#C8FFD0")       # green, bright core

HAZARD_Y = ramp("#8A6A08", "#E8B818", "#FFD84A")
HAZARD_K = ramp("#141416", "#26262A")

# ore / material ramps [deep, dark, mid, light, hi]
MAT = {
    "iron": ramp("#5E5E62", "#8C8C92", "#B8B8BE", "#DCDCE0", "#FAFAFC"),
    "copper": ramp("#62300F", "#9C5024", "#CC7438", "#E8985A", "#F8C290"),
    "gold": ramp("#6A4406", "#AC780E", "#E0B024", "#F8D84A", "#FFF4A6"),
    "coal": ramp("#060608", "#0E0E11", "#18181C", "#2A2A30", "#9A9AA8"),
    "quartz": ramp("#8E8074", "#BEB2A4", "#DED4C8", "#F2ECE4", "#FFFFFF"),
    "bauxite": ramp("#4A1A0C", "#7C3016", "#A84820", "#CC6A32", "#E8925A"),
    "titanium": ramp("#48505E", "#7A8696", "#A6B2C2", "#CCD8E6", "#F2F8FF"),
    "diamond": ramp("#0C4448", "#1A8288", "#36C4C0", "#7CECE4", "#D8FFFA"),
    "steel": ramp("#262C38", "#3C4656", "#586476", "#7A879A", "#A4B0C2"),
    "tin": ramp("#56606C", "#86909C", "#B0BAC4", "#D2DAE2", "#F0F4F8"),
    "bronze": ramp("#4E3010", "#84561E", "#B07A30", "#D09E4E", "#EAC47C"),
    "aluminum": ramp("#707A86", "#9EA8B4", "#C4CCD6", "#DEE4EC", "#F8FBFF"),
    "titanium_metal": ramp("#4E4A60", "#7E7C94", "#AEAEC2", "#D6D6E4", "#F8F6FF"),
    "quantum_alloy": ramp("#160C26", "#28163E", "#3E2260", "#58348A", "#7A50B4"),
    "silicon": ramp("#1C2230", "#2C3446", "#404C62", "#5C6A84", "#8C9AB6"),
    "rubber": ramp("#141417", "#1E1E22", "#2A2A2F", "#38383E", "#4C4C54"),
}
CYAN_GLOW = ramp("#1A6B6E", "#2FB5B0", "#6FF0E8", "#C8FFFB")
VIOLET_GLOW = ramp("#4A2E70", "#8A56D0", "#C08CFF", "#EAD6FF")

# titanium ore/dust/raw use the blue-white mineral ramp; the refined metal
# (ingot, plate, gear, block) uses the violet-sheen ramp
MAT["titanium_mineral"] = MAT["titanium"]
MAT["titanium"] = MAT["titanium_metal"]

DUST = {
    "iron": ramp("#6A625A", "#9A928A", "#C0B8AE", "#DCD6CC", "#F2EEE8"),
    "copper": MAT["copper"],
    "gold": MAT["gold"],
    "tin": ramp("#5E6670", "#8C96A2", "#B6C0CA", "#D6DEE6", "#F4F8FC"),
    "coal": ramp("#0E0E10", "#1E1E22", "#2E2E34", "#44444C", "#62626C"),
    "quartz": MAT["quartz"],
    "bauxite": MAT["bauxite"],
    "titanium": MAT["titanium_mineral"],
}
QUANTUM_VEINS = [(4, 7), (5, 6), (6, 6), (7, 5), (8, 5), (9, 6), (3, 8), (3, 9), (6, 9), (7, 9),
                 (7, 10), (11, 7)]

# neutral steel "tier" for untiered steel blocks (pipe connectors)
STEEL_TIER = Tier(0, "steel", ["#D0D6DE", "#A8B0BA", "#7E8792", "#555C66", "#363B42"],
                  ["#9AA2AE", "#8A929E", "#7C8490", "#626A76", "#4A505A"], "#FFD84A")


def rng_for(name):
    return random.Random(zlib.crc32(name.encode("utf-8")))


# =============================================================================
# 2. Canvas & primitives
# =============================================================================


class Tex:
    def __init__(self, w=16, h=16, fill=CLEAR):
        self.w, self.h = w, h
        f = C(fill)
        self.p = [[f] * w for _ in range(h)]

    # -- basic access ---------------------------------------------------------
    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, c):
        if c is not None and self.inside(x, y):
            self.p[y][x] = C(c)

    def get(self, x, y):
        return self.p[y][x] if self.inside(x, y) else CLEAR

    def opaque(self, x, y):
        return self.inside(x, y) and self.p[y][x][3] > 0

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def copy(self):
        t = Tex(self.w, self.h)
        t.p = [row[:] for row in self.p]
        return t

    def blit(self, other, ox=0, oy=0):
        for y in range(other.h):
            for x in range(other.w):
                c = other.p[y][x]
                if c[3] > 0:
                    self.set(ox + x, oy + y, c)

    def image(self):
        img = Image.new("RGBA", (self.w, self.h))
        img.putdata([c for row in self.p for c in row])
        return img

    # -- sprites --------------------------------------------------------------
    def sprite(self, rows, pal, ox=0, oy=0):
        """Draw ASCII art. '.' / ' ' = keep, other chars looked up in pal."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch not in pal:
                    raise KeyError("sprite char %r not in palette" % ch)
                self.set(ox + x, oy + y, pal[ch])

    # -- post-processing ------------------------------------------------------
    def outline(self, factor=0.55, diag=False):
        """Vanilla-item outline: every empty pixel next to the sprite becomes
        a darkened copy of its neighbour."""
        adds = []
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diag:
            nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
        for y in range(self.h):
            for x in range(self.w):
                if self.opaque(x, y):
                    continue
                cols = [self.p[y + dy][x + dx] for dx, dy in nb if self.opaque(x + dx, y + dy)]
                if cols:
                    avg = tuple(sum(c[i] for c in cols) // len(cols) for i in range(3)) + (255,)
                    adds.append((x, y, darken(avg, factor)))
        for x, y, c in adds:
            self.set(x, y, c)
        return self

    def edge_shade(self, mask, light, dark):
        """Given a set of pixels, recolour its top/left border light and its
        bottom/right border dark (simple bevel for flat shapes)."""
        for (x, y) in mask:
            if (x, y - 1) not in mask or (x - 1, y) not in mask:
                self.set(x, y, light)
        for (x, y) in mask:
            if (x, y + 1) not in mask or (x + 1, y) not in mask:
                self.set(x, y, dark)


def dither(t, rng, x0, y0, x1, y1, base, lo, hi, p=0.12):
    """Sparse two-tone speckle over a flat area (metal grain)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if t.get(x, y)[:3] != C(base)[:3]:
                continue
            r = rng.random()
            if r < p / 2:
                t.set(x, y, lo)
            elif r < p:
                t.set(x, y, hi)


LIGHT = (-0.55, -0.75, 1.0)
_ln = math.sqrt(sum(v * v for v in LIGHT))
LIGHT = tuple(v / _ln for v in LIGHT)


def blob_field(blobs, w=16, h=16):
    """Union of ellipsoids -> {(x,y): (intensity, blob_index)}.
    blob = (cx, cy, rx, ry[, height])."""
    out = {}
    for y in range(h):
        for x in range(w):
            best = None
            for i, b in enumerate(blobs):
                cx, cy, rx, ry = b[:4]
                hgt = b[4] if len(b) > 4 else 0.0
                u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d2 = u * u + v * v
                if d2 > 1.0:
                    continue
                z = math.sqrt(1.0 - d2)
                zz = z * min(rx, ry) + hgt
                if best is None or zz > best[0]:
                    n = (u, v, z * 1.1)
                    nl = math.sqrt(sum(k * k for k in n)) or 1
                    inten = sum(n[k] / nl * LIGHT[k] for k in range(3))
                    best = (zz, inten, i)
            if best:
                out[(x, y)] = (best[1], best[2])
    return out


def tone(inten, pal, bias=0.0):
    """Map a lighting intensity (-1..1) to a palette index (dark -> light)."""
    n = len(pal)
    t = max(0.0, min(0.999, (inten + 0.25 + bias) / 1.25))
    return pal[int(t * n)]


def render_blobs(t, blobs, pal, bias=0.0, rng=None, speckle=None, speck_p=0.0):
    field = blob_field(blobs, t.w, t.h)
    for (x, y), (inten, _) in field.items():
        c = tone(inten, pal, bias)
        t.set(x, y, c)
    if rng and speckle:
        for (x, y) in sorted(field):
            if rng.random() < speck_p:
                t.set(x, y, rng.choice(speckle))
    return field


def seg_dist(px, py, ax, ay, bx, by):
    """(distance to segment, signed perpendicular, param along 0..1)."""
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    s = ((px - ax) * dx + (py - ay) * dy) / L2
    sc = max(0.0, min(1.0, s))
    qx, qy = ax + sc * dx, ay + sc * dy
    L = math.sqrt(L2)
    perp = ((px - ax) * (-dy) + (py - ay) * dx) / L   # >0 = left of a->b
    return math.hypot(px - qx, py - qy), perp, s


def render_rod(t, a, b, radius, pal, cap=None):
    """Cylinder from a to b (pixel coords), shaded across its width.
    pal = [dark .. light]. cap(t, x, y, s) may override end pixels."""
    ax, ay = a
    bx, by = b
    for y in range(t.h):
        for x in range(t.w):
            d, perp, s = seg_dist(x + 0.5, y + 0.5, ax, ay, bx, by)
            if s < 0 or s > 1 or abs(perp) > radius:
                continue
            # a->b goes up-right; left side (perp>0) faces the top-left light
            k = perp / radius  # -1..1, +1 = lit side
            idx = int((k + 1) / 2 * (len(pal) - 0.001))
            t.set(x, y, pal[idx])
            if cap:
                cap(t, x, y, s, k)


def ring_mask(cx, cy, r0, r1, w=16, h=16):
    m = set()
    for y in range(h):
        for x in range(w):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                m.add((x, y))
    return m


def disc_mask(cx, cy, r, w=16, h=16):
    return ring_mask(cx, cy, -1, r, w, h)


# =============================================================================
# 3. Blocks
# =============================================================================

# ---- casing: frame, rivets, recess ---------------------------------------------


def frame(t, T, x0=0, y0=0, x1=15, y1=15, width=2, pal=None):
    """Bevelled 2-px frame: outer pixel = bevel edge, inner pixel = body."""
    F = pal or T.frame
    for i in range(width):
        a0, b0, a1, b1 = x0 + i, y0 + i, x1 - i, y1 - i
        tl, br = (F[1], F[3]) if i == 0 else (F[2], F[3])
        t.hline(a0, a1, b0, tl)
        t.vline(a0, b0, b1, tl)
        t.hline(a0, a1, b1, br)
        t.vline(a1, b0, b1, br)
        t.set(a1, b0, F[2])
        t.set(a0, b1, F[2])
    t.set(x0, y0, F[0])  # top-left corner glint


def rivet(t, x, y, T, pal=None):
    F = pal or T.frame
    t.set(x, y, F[0])
    t.set(x + 1, y, F[1])
    t.set(x, y + 1, F[1])
    t.set(x + 1, y + 1, F[3])




def recess(t, x0, y0, x1, y1, T, fill=None):
    """Sunken rectangle: shadow on top/left, catch-light on bottom/right."""
    P = T.panel
    t.rect(x0, y0, x1, y1, fill if fill is not None else mix(P[2], P[3], 0.35))
    t.hline(x0, x1, y0, P[4])
    t.vline(x0, y0, y1, P[4])
    t.hline(x0 + 1, x1, y1, P[1])
    t.vline(x1, y0 + 1, y1, P[1])


def panel_base(T, rng, rivets=True):
    """Tier-coloured frame around a flat panel: the base of every machine face."""
    P = T.panel
    t = Tex(fill=P[2])
    dither(t, rng, 2, 2, 13, 13, P[2], mix(P[2], P[3], 0.45), mix(P[2], P[1], 0.5), 0.14)
    frame(t, T)
    # soft shadow the frame casts onto the panel
    t.hline(2, 13, 2, mix(P[2], P[3], 0.5))
    t.vline(2, 2, 13, mix(P[2], P[3], 0.5))
    if rivets:
        for (x, y) in [(2, 2), (12, 2), (2, 12), (12, 12)]:
            rivet(t, x, y, T)
    return t


def casing_top(T, rng):
    t = panel_base(T, rng)
    P, F = T.panel, T.frame
    recess(t, 4, 4, 11, 11, T, fill=VOID)   # vent grille
    for y in range(5, 11):
        if y % 2 == 1:
            t.hline(5, 10, y, F[2])
            t.set(5, y, F[1])
        else:
            t.hline(5, 10, y, CYAN_GLOW[0] if T.glow else VOID2)
    t.vline(4, 4, 11, P[4])
    return t


def casing_bottom(T, rng):
    P = [darken(c, 0.25) for c in T.panel]
    F = [darken(c, 0.2) for c in T.frame]
    t = Tex(fill=P[2])
    dither(t, rng, 2, 2, 13, 13, P[2], mix(P[2], P[3], 0.5), mix(P[2], P[1], 0.4), 0.1)
    frame(t, T, pal=F)
    t.hline(2, 13, 7, P[3]); t.hline(2, 13, 8, P[1])   # cross seam
    t.vline(7, 2, 13, P[3]); t.vline(8, 2, 13, P[1])
    t.set(7, 7, P[4]); t.set(8, 8, P[2]); t.set(7, 8, P[3]); t.set(8, 7, P[3])
    return t



# =============================================================================
# 3b. v2 machines: one look per age, one front per machine
# =============================================================================
# Every full-block machine model (tools/models/gen_models.py) is a solid cube
# with rectangular windows carved into its faces; LAYOUT is the single source of
# those windows, shared by the textures and the models. Faces use projected
# UVs, so `<id>_front.png` is the literal front view: pixels inside a window are
# what you see on the recess's back wall, pixels outside it are the flush face.
#
#   LAYOUT[id][face] = [(u0, v0, u1, v1, depth), ...]   texture pixels, inclusive
#
# Windows on different faces never share volume and never touch a block edge,
# so the block still hides its neighbours' faces without see-through gaps.

LAYOUT = {
    "brick_kiln": {"north": [(4, 6, 11, 13, 4)], "up": [(6, 6, 9, 9, 3)]},
    "burner_crusher": {"north": [(3, 3, 12, 8, 3), (4, 11, 11, 13, 2)], "up": [(4, 5, 11, 11, 3)]},
    "burner_press": {"north": [(3, 2, 12, 9, 4), (4, 12, 11, 14, 2)], "up": [(10, 10, 12, 12, 3)]},
    "coke_oven": {"north": [(4, 5, 11, 13, 1)]},
    "blast_furnace": {"north": [(4, 2, 11, 7, 1), (6, 10, 9, 13, 2)]},
    "electric_furnace": {"north": [(3, 3, 12, 11, 3)]},
    "crusher": {"north": [(3, 2, 12, 9, 3)], "up": [(4, 4, 11, 11, 3)]},
    "metal_press": {"north": [(3, 1, 12, 10, 4)]},
    "alloy_smelter": {"north": [(2, 4, 13, 11, 4)], "up": [(3, 7, 6, 10, 2), (9, 7, 12, 10, 2)]},
    "assembler": {"north": [(2, 2, 13, 10, 4)]},
    "combustion_generator": {"north": [(3, 2, 12, 5, 2), (4, 8, 11, 12, 2)], "up": [(10, 10, 12, 12, 3)]},
    "auto_farmer": {"north": [(3, 3, 12, 8, 3)], "up": [(2, 4, 13, 13, 2)]},
    "miner": {f: [(4, 4, 11, 10, 2)] for f in ("north", "south", "west", "east")},
    "geothermal_generator": {"north": [(3, 3, 12, 12, 2)],
                             "west": [(3, 1, 4, 14, 1), (11, 1, 12, 14, 1)],
                             "east": [(3, 1, 4, 14, 1), (11, 1, 12, 14, 1)]},
    "solar_panel": {"up": [(1, 1, 14, 14, 1)]},
    # automation: washer drum window + drain grate, water basin on top
    "ore_washer": {"north": [(3, 2, 12, 10, 2), (6, 12, 9, 13, 1)], "up": [(4, 4, 11, 11, 2)]},
    # industrial: coil bay around a crucible, cooling-fin well on top
    "induction_smelter": {"north": [(3, 3, 12, 10, 4)], "up": [(3, 3, 12, 12, 2)]},
    "hydraulic_press": {"north": [(2, 1, 13, 10, 4)]},
    # orbital: domed clean-room bay (arch = stacked windows), octagonal plasma chamber
    "precision_assembler": {"north": [(6, 2, 9, 2, 4), (4, 3, 11, 3, 4), (3, 4, 12, 10, 4)]},
    "plasma_forge": {"north": [(5, 2, 10, 2, 3), (4, 3, 11, 3, 3), (3, 4, 12, 10, 3),
                               (4, 11, 11, 11, 3), (5, 12, 10, 12, 3)]},
}
ENERGY_CELLS = {"energy_cell": 1, "advanced_energy_cell": 3, "industrial_energy_cell": 4, "quantum_energy_cell": 5}
for _cell in ENERGY_CELLS:
    LAYOUT[_cell] = {"north": [(4, 3, 11, 12, 1)], "west": [(6, 2, 9, 13, 1)], "east": [(6, 2, 9, 13, 1)]}

# ---- age palettes (Tier ramps are hi .. deep) -------------------------------------------

BRONZE_T = Tier(0, "bronze",
                ["#F6D38E", "#DAA656", "#AA742E", "#6E4617", "#44280B"],
                ["#CB9147", "#BC843E", "#AC7636", "#8C5C27", "#6C441A"],
                "#FFB84A")
STEEL_T = Tier(0, "steel",
               ["#CAD5E2", "#98A7BB", "#6C7C94", "#46536B", "#2C3446"],
               ["#9EACBF", "#8C9AAE", "#7E8CA1", "#667488", "#4E586A"],
               "#7FC4FF")
ALU_T = Tier(0, "aluminum",
             ["#FFFFFF", "#E8EEF4", "#BEC8D2", "#8C96A2", "#646C78"],
             ["#F2F6FA", "#E2E8EE", "#D6DEE6", "#BAC4CE", "#9AA4B0"],
             "#62D0FF")
# industrial age: titanium, a darker blue-violet steel with a bold amber trim
TITAN_T = Tier(0, "titanium",
               ["#C6C2EE", "#8E8AC8", "#605C98", "#3A366A", "#211E42"],
               ["#7A76A4", "#6A6694", "#5C5886", "#4A4670", "#363258"],
               "#FFB02E")
# orbital age: white spacecraft panels in a gold frame
ORBITAL_T = Tier(0, "orbital",
                 ["#FFFFFF", "#F2F3F5", "#C6CAD0", "#8E949C", "#5E646C"],
                 ["#FFFFFF", "#F4F5F6", "#E6E8EA", "#CCD0D4", "#A8ACB2"],
                 "#3A7BD5")
IRON = ramp("#A2A6AE", "#767A82", "#55585F", "#393B40", "#232428")      # hi .. deep
FAMILY = {"bronze": BRONZE_T, "steel": STEEL_T, "aluminum": ALU_T, "titanium": TITAN_T, "orbital": ORBITAL_T}
SOOT = ramp("#0E0A09", "#17110E", "#211813", "#2C2019")                 # deep .. light


def win_list(name, face="north"):
    return LAYOUT[name].get(face, [])


def in_window(name, face, x, y):
    return any(u0 <= x <= u1 and v0 <= y <= v1 for (u0, v0, u1, v1, _) in win_list(name, face))


def win_shadow(t, u0, v0, u1, v1, amt=0.4):
    """Back wall of a recess: its rim shades the top row and left column."""
    for u in range(u0, u1 + 1):
        t.set(u, v0, darken(t.get(u, v0), amt))
    for v in range(v0 + 1, v1 + 1):
        t.set(u0, v, darken(t.get(u0, v), amt * 0.6))


def win_lip(t, u0, v0, u1, v1, dark, light):
    """Rim drawn on the flush face around a window: dark above/left, lit below/right."""
    t.hline(u0 - 1, u1 + 1, v0 - 1, dark)
    t.vline(u0 - 1, v0 - 1, v1 + 1, dark)
    t.hline(u0, u1 + 1, v1 + 1, light)
    t.vline(u1 + 1, v0, v1 + 1, light)


def inner_tex(base, rng, stripe=None):
    """Side walls of every recess: plain dark metal / soot."""
    base = C(base)
    t = Tex(fill=base)
    dither(t, rng, 0, 0, 15, 15, base, darken(base, 0.25), lighten(base, 0.1), 0.25)
    if stripe:
        for i in range(16):
            t.set(i, 0, stripe); t.set(0, i, stripe); t.set(i, 15, stripe); t.set(15, i, stripe)
    return t


def casing_face(T, rng, name=None, face="north", rivets=((2, 2), (12, 2), (2, 12), (12, 12))):
    """Age-coloured frame + panel with window rims and rivets clear of the windows."""
    t = panel_base(T, rng, rivets=False)
    for (x, y) in rivets:
        if name is None or not any(in_window(name, face, x + dx, y + dy) or
                                   in_window(name, face, x + dx + 1, y + dy + 1)
                                   for dx in (-1, 0, 1, 2) for dy in (-1, 0, 1, 2)):
            rivet(t, x, y, T)
    if name:
        for (u0, v0, u1, v1, _) in win_list(name, face):
            win_lip(t, u0, v0, u1, v1, T.frame[4], T.frame[1])
    return t


def void_fill(t, u0, v0, u1, v1, top=VOID2, bottom=VOID):
    for v in range(v0, v1 + 1):
        t.hline(u0, u1, v, mix(top, bottom, (v - v0) / max(1, v1 - v0)))


# ---- shared painters: fire, lava, rift, grit ----------------------------------------------

FIRE_IDLE = {"A": "#0C0A0A", "B": "#100C0B", "C": "#150E0C", "D": "#1C100C", "E": "#28120C", "F": "#3A160C"}
FIRE_ON = {"A": "#5A1C08", "B": "#96360C", "C": "#D25E14", "D": "#F29426", "E": "#FFCC56", "F": "#FFF2BA"}


def paint_fire(t, rng, u0, v0, u1, v1, on, grate=True):
    """Flames rising from a bed; embers only when idle."""
    H = v1 - v0 + 1
    for u in range(u0, u1 + 1):
        h = rng.uniform(0.5, 1.1)
        for v in range(v0, v1 + 1):
            f = (v1 - v + 0.5) / H
            k = h - f
            if on:
                key = "F" if k > 0.6 else "E" if k > 0.42 else "D" if k > 0.24 else "C" if k > 0.08 else \
                    "B" if k > -0.12 else "A"
                t.set(u, v, FIRE_ON[key])
            else:
                t.set(u, v, FIRE_IDLE["C" if f < 0.3 else "B" if f < 0.65 else "A"])
    if not on:
        for _ in range(max(2, (u1 - u0) // 2)):
            t.set(rng.randint(u0, u1), v1 - (1 if grate else 0), rng.choice(["#6A2208", "#3E1408", "#8A3010"]))
    if grate:
        for u in range(u0, u1 + 1):
            t.set(u, v1, STEEL[3] if (u - u0) % 2 == 0 else "#1A1A1E")


def paint_coals(t, rng, u0, v0, u1, v1, on):
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            r = rng.random()
            if on:
                c = "#FFD060" if r < 0.2 else "#FF8A1C" if r < 0.55 else "#C8420C" if r < 0.85 else "#5A1A08"
            else:
                c = "#3A3438" if r < 0.25 else "#26222A" if r < 0.7 else "#161418"
                if r > 0.96:
                    c = "#6A2208"
            t.set(u, v, c)


LAVA_NOISE = None


def lava_value(u, v):
    global LAVA_NOISE
    if LAVA_NOISE is None:
        r = rng_for("lava_noise")
        a, b = smooth_noise(r, 4, 4), smooth_noise(r, 2, 2)
        LAVA_NOISE = [[a[y][x] * 0.7 + b[y][x] * 0.3 for x in range(16)] for y in range(16)]
    return LAVA_NOISE[v % 16][u % 16]


def paint_lava(t, u0, v0, u1, v1, on, phase=0, oy=0):
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            n = lava_value(u + phase // 3, v + phase + oy)
            if on:
                c = "#FFF0A0" if n < 0.3 else "#FFC040" if n < 0.42 else "#FF8A20" if n < 0.56 else \
                    "#E2521A" if n < 0.7 else "#A8300E"
            else:
                c = "#8A260C" if n < 0.3 else "#4A140A" if n < 0.42 else "#2E0E0A" if n < 0.6 else "#1E0B0A"
            t.set(u, v, c)


def paint_rift(t, u0, v0, u1, v1, on, phase=0.0):
    """Window into The Deep: a dark violet vortex with specks of stone."""
    cx, cy = (u0 + u1 + 1) / 2.0, (v0 + v1 + 1) / 2.0
    rx, ry = (u1 - u0 + 1) / 2.0, (v1 - v0 + 1) / 2.0
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            dx, dy = (u + 0.5 - cx) / rx, (v + 0.5 - cy) / ry
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            s = (a / (2 * math.pi) * 2 + d * 1.3 - phase) % 1.0
            arm = s < 0.4
            if on:
                c = "#F4E4FF" if d < 0.22 else ("#C08CFF" if arm else "#8A56D0") if d < 0.55 else \
                    ("#7A40C0" if arm else "#3E1E6A") if d < 0.95 else ("#4A2480" if arm else "#1E0E36")
            else:
                c = "#8A60C8" if d < 0.2 else ("#5A3490" if arm else "#361C5C") if d < 0.55 else \
                    ("#2E1650" if arm else "#1A0C30") if d < 0.95 else ("#20103A" if arm else "#120822")
            t.set(u, v, c)
    for (du, dv) in ((1, 1), (u1 - u0 - 1, 2), (2, v1 - v0 - 1)):
        t.set(u0 + du, v0 + dv, "#E0D0FF" if on else "#6A5A8A")


def grit(t, rng, u0, v0, u1, v1, n, cols=("#B8AE9C", "#8E8676", "#D8D0C0")):
    for _ in range(n):
        t.set(rng.randint(u0, u1), rng.randint(v0, v1), rng.choice(cols))


def hazard_strip(t, u0, v0, u1, v1):
    for y in range(v0, v1 + 1):
        for x in range(u0, u1 + 1):
            t.set(x, y, HAZARD_Y[1] if (x + y) % 4 < 2 else HAZARD_K[0])


def gauge(t, x, y, T, needle="#D02010"):
    """3x3 round pressure gauge."""
    t.set(x + 1, y, IRON[1]); t.set(x, y + 1, IRON[1]); t.set(x + 2, y + 1, IRON[3]); t.set(x + 1, y + 2, IRON[3])
    t.set(x, y, T.frame[3]); t.set(x + 2, y, T.frame[3]); t.set(x, y + 2, T.frame[3]); t.set(x + 2, y + 2, T.frame[4])
    t.set(x + 1, y + 1, "#ECEAE0")
    t.set(x + 2, y + 1, needle)


def status_led(t, x, y, on):
    L = LED_ON if on else LED_OFF
    t.set(x, y, L[2]); t.set(x + 1, y, L[1])


# ---- bricks -------------------------------------------------------------------------------


def brick_wall(rng, pal, mortar, rows, row_h=4, speck=None, speck_p=0.0, rows_used=16):
    """Running-bond bricks. pal = [deep, dark, mid, light, hi]; rows = widths per course."""
    t = Tex(fill=mortar)
    for i, widths in enumerate(rows):
        y0 = i * row_h
        if y0 >= rows_used:
            break
        x = 0
        for w in widths:
            base = pal[2] if rng.random() < 0.6 else rng.choice([pal[1], pal[3]])
            for yy in range(y0, min(y0 + row_h - 1, rows_used)):
                for xx in range(x + 1, x + w):
                    c = base
                    if yy == y0:
                        c = mix(base, pal[4], 0.45)
                    elif yy == y0 + row_h - 2:
                        c = mix(base, pal[0], 0.45)
                    if xx == x + 1 and yy != y0:
                        c = mix(c, pal[3], 0.25)
                    if speck and rng.random() < speck_p:
                        c = rng.choice(speck)
                    t.set(xx % 16, yy, c)
            x += w
    return t


KILN = ramp("#5E2C16", "#8C4628", "#B26034", "#CB7C46", "#DE9C62")
KILN_MORTAR = "#C4B290"
COKE_BRICK = ramp("#1A100B", "#2A1A12", "#3B261A", "#4F3424", "#654632")
COKE_MORTAR = "#120D0B"
FIRE_BRICK = ramp("#8A6A38", "#B08E52", "#CDAC6C", "#E0C686", "#F0DCA4")
FIRE_MORTAR = "#F2EAD4"
COBBLE = ramp("#4A4A4E", "#626266", "#7A7A7E", "#929296", "#AAAAAE")


def kiln_bricks(rng, course=True):
    t = brick_wall(rng, KILN, KILN_MORTAR, [[5, 6, 5], [3, 5, 5, 3], [6, 5, 5], [2, 6, 5, 3]], 4,
                   speck=[KILN[1], KILN[3]], speck_p=0.06, rows_used=13 if course else 16)
    if course:   # rough stone footing course
        for y in range(13, 16):
            for x in range(16):
                c = COBBLE[2] if rng.random() < 0.5 else rng.choice([COBBLE[1], COBBLE[3]])
                if y == 13:
                    c = COBBLE[4] if x % 5 else COBBLE[1]
                if y == 15:
                    c = COBBLE[0]
                if x % 5 == 4 and y > 13:
                    c = COBBLE[0]
                t.set(x, y, c)
    return t


def coke_oven_bricks(rng):
    """Dark soot-brown bricks, near-black mortar, occasional tar glaze."""
    t = brick_wall(rng, COKE_BRICK, COKE_MORTAR, [[8, 8], [4, 8, 4], [8, 8], [4, 8, 4]], 4,
                   speck=["#2A1A12", "#6E5038"], speck_p=0.05)
    for (x, y) in [(3, 1), (11, 5), (6, 9), (13, 13), (2, 13)]:
        t.set(x, y, "#806048")
    return t


def fire_bricks(rng):
    """Big sandy-yellow refractory blocks, cream mortar, iron specks."""
    t = brick_wall(rng, FIRE_BRICK, FIRE_MORTAR, [[8, 8], [4, 8, 4]], 8,
                   speck=["#8A5A2A", "#A0703A"], speck_p=0.05)
    return t


# ---- stone age: quern & brick kiln -------------------------------------------------------

GRANITE = ramp("#4E4C4A", "#686664", "#83807C", "#9C9994", "#B8B4AE")


def granite(rng, pal=GRANITE, w=16, h=16):
    t = Tex(w, h)
    n = smooth_noise(rng, 4, 4)
    for y in range(h):
        for x in range(w):
            v = n[y % 16][x % 16] * 0.7 + rng.random() * 0.3
            t.set(x, y, pal[1 + min(3, int(v * 3.6))])
            if rng.random() < 0.06:
                t.set(x, y, pal[0])
    return t


def quern_side(rng):
    """Both stones seen from the side: runner (rows 5-9) above the bed stone (rows 10-15)."""
    t = granite(rng)
    G = GRANITE
    t.hline(0, 15, 5, G[4])                  # runner top edge
    t.hline(0, 15, 7, G[1])                  # chisel groove round the runner
    t.hline(0, 15, 9, G[0])                  # joint between the stones (shadow)
    t.hline(0, 15, 10, G[4])                 # bed stone top edge
    for x in range(0, 16, 3):
        t.set(x, 12, G[1]); t.set(x + 1, 13, G[1])
    t.hline(0, 15, 15, G[0])
    return t


def quern_top(rng, runner, on):
    """Top of a millstone: radial furrows; the runner has the grain eye, the bed a flour ring."""
    t = granite(rng)
    G = GRANITE
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 8
            if 1.8 < d < 7 and (a % 1.0) < 0.18:
                t.set(x, y, G[1])
            if runner and d < 1.6:
                t.set(x, y, "#1A1614")
            if runner and 1.6 <= d < 2.3:
                t.set(x, y, G[4])
            if not runner and d > 4.6 and on and zlib.crc32(bytes([x, y])) % 5 < 3:
                t.set(x, y, "#EDE6D6" if zlib.crc32(bytes([y, x])) % 2 else "#D8CFBC")
    if runner:
        t.set(7, 7, "#C8A860"); t.set(8, 8, "#A88A48")   # grain in the eye
    return t


def quern_handle(rng):
    t = Tex(fill=WOOD[2])
    for y in range(16):
        for x in range(16):
            t.set(x, y, WOOD[3] if x % 4 == 0 else WOOD[2] if x % 4 in (1, 2) else WOOD[1])
    return t


def kiln_front(rng, on):
    t = kiln_bricks(rng_for("brick_kiln/bricks"))
    u0, v0, u1, v1, _ = win_list("brick_kiln")[0]
    # arch of wedge bricks over the mouth
    for u in range(u0 - 1, u1 + 2):
        t.set(u, v0 - 1, KILN[1] if u % 2 else KILN[0])
        t.set(u, v0 - 2, KILN[3] if u % 2 else KILN[2])
    t.set(u0 - 1, v0 - 2, KILN_MORTAR); t.set(u1 + 1, v0 - 2, KILN_MORTAR)
    rr = rng_for("brick_kiln/fire")
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            t.set(u, v, mix(SOOT[0], SOOT[2], (v - v0) / (v1 - v0)))
    paint_fire(t, rr, u0, v0 + 2, u1, v1 - 1, on, grate=False)
    paint_coals(t, rr, u0, v1, u1, v1, on)
    win_shadow(t, u0, v0, u1, v1, 0.5)
    # arch shoulders (model: small bricks in the top corners of the mouth)
    t.set(u0, v0, KILN[2]); t.set(u1, v0, KILN[2])
    t.hline(u0 - 1, u1 + 1, v1 + 1, COBBLE[4])   # hearth sill
    return t


def kiln_top(rng, on):
    t = kiln_bricks(rng_for("brick_kiln/top"), course=False)
    u0, v0, u1, v1, _ = win_list("brick_kiln", "up")[0]
    for (x, y) in ring_mask(8, 8, 2.2, 3.4):
        t.set(x, y, mix(t.get(x, y), SOOT[1], 0.55))
    rr = rng_for("brick_kiln/flue")
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            t.set(u, v, rr.choice(["#FFB43C", "#F07A1E", "#C8420C"]) if on else rr.choice([SOOT[0], SOOT[1], "#3A1408"]))
    win_shadow(t, u0, v0, u1, v1, 0.5)
    return t


def logs(rng, on):
    t = Tex(fill="#3A2412")
    for y in range(16):
        for x in range(16):
            c = "#4A2E16" if (y % 4) in (0, 1) else "#2A1A0C"
            if on and rng.random() < 0.3:
                c = rng.choice(["#FF8A1C", "#C8420C", "#FFD060"])
            elif not on and rng.random() < 0.15:
                c = "#1A1210"
            t.set(x, y, c)
    return t


# ---- bronze age ---------------------------------------------------------------------------


def family_side(T, rng, kind):
    P, F = T.panel, T.frame
    t = casing_face(T, rng)
    if kind == "bronze":        # two riveted plates with a lap seam and a copper pipe
        t.hline(2, 13, 7, F[3]); t.hline(2, 13, 8, F[1])
        for x in (3, 6, 9, 12):
            t.set(x, 6, F[0]); t.set(x, 9, F[1]); t.set(x + 1, 9, F[3])
        gauge(t, 10, 2, T)
    elif kind == "steel":       # louvred vent block
        recess(t, 4, 4, 11, 11, T, fill=VOID)
        for y in range(5, 11):
            if y % 2 == 1:
                t.hline(5, 10, y, F[1]); t.set(10, y, F[3])
        t.hline(4, 11, 13, T.accent)
    elif kind == "titanium":    # corrugated armour sheet under heavy corner brackets, amber trim
        ti_brackets(t, None, "west")
        for y in range(4, 11):
            for x in range(3, 13):
                t.set(x, y, [P[0], P[1], P[3], P[4]][(x - 3) % 4] if y not in (4, 10) else
                      (F[3] if y == 4 else F[1]))
        t.hline(3, 12, 12, T.accent); t.hline(3, 12, 13, darken(T.accent, 0.45))
        for x in (4, 7, 10):
            t.set(x, 12, darken(T.accent, 0.6))
    elif kind == "orbital":     # white heat-shield tiles, blue mission stripe, gold trim
        gold_trim(t)
        t.rect(2, 2, 13, 13, P[1])
        for y in range(2, 14):
            for x in range(2, 14):
                if (x - 2) % 4 == 3 or (y - 2) % 4 == 3:
                    t.set(x, y, P[3])
                elif rng.random() < 0.12:
                    t.set(x, y, P[2])
        for (x, y) in [(2, 2), (12, 2), (2, 12), (12, 12)]:
            rivet(t, x, y, T)
        t.rect(10, 6, 12, 8, T.accent); t.hline(10, 12, 6, lighten(T.accent, 0.35))
        t.hline(10, 12, 8, darken(T.accent, 0.35))
    else:                       # aluminium: brushed sheet with a blue accent stripe
        t.rect(2, 2, 13, 13, P[2])
        for y in range(2, 14):
            for x in range(2, 14):
                if rng.random() < 0.35:
                    t.set(x, y, P[1] if (y % 3) else P[0])
        for (x, y) in [(2, 2), (12, 2), (2, 12), (12, 12)]:
            rivet(t, x, y, T)
        t.hline(2, 13, 10, T.accent); t.hline(2, 13, 11, darken(T.accent, 0.35))
    return t


def family_bottom(T, rng):
    return casing_bottom(T, rng)


def bronze_front_base(name, rng):
    return casing_face(BRONZE_T, rng, name)


def burner_crusher_front(rng, on):
    t = bronze_front_base("burner_crusher", rng)
    (a0, b0, a1, b1, _), (f0, g0, f1, g1, _) = win_list("burner_crusher")
    void_fill(t, a0, b0, a1, b1)
    if on:
        grit(t, rng_for("bc/grit"), a0, b0 + 2, a1, b1, 10)
    win_shadow(t, a0, b0, a1, b1)
    paint_fire(t, rng_for("bc/fire"), f0, g0, f1, g1, on)
    win_shadow(t, f0, g0, f1, g1)
    # firebox door hinges & a band of rivets between the two openings
    F = BRONZE_T.frame
    for u in range(3, 13, 3):
        t.set(u, 10, F[0]); t.set(u + 1, 10, F[3])
    gauge(t, 12, 12, BRONZE_T, "#D02010" if on else "#606060")
    return t


def roller_end(rng, on):
    """Front view of the two toothed rollers (model: x 3.3-7.7 and 8.3-12.7, y 8.3-12.7)."""
    t = Tex(fill=STEEL[2])
    for (cx, cy) in ((10.5, 5.5), (5.5, 5.5)):          # texture coords (u = 16 - x)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                if d > 2.6:
                    continue
                a = (math.atan2(dy, dx) / (2 * math.pi) * 8 + (0.5 if on else 0)) % 1.0
                c = STEEL[1] if a < 0.5 else STEEL[3]
                if d < 1.1:
                    c = BRONZE_T.frame[1]
                elif d < 1.6:
                    c = STEEL[4]
                t.set(x, y, c)
    return t


def knurl(rng, pal=STEEL):
    t = Tex(fill=pal[2])
    for y in range(16):
        for x in range(16):
            t.set(x, y, pal[1] if (x + y) % 3 == 0 else pal[3] if (x - y) % 3 == 0 else pal[2])
    return t


def hopper_top(T, rng, name, contents):
    t = casing_face(T, rng, name, "up")
    u0, v0, u1, v1, _ = win_list(name, "up")[0]
    rr = rng_for(name + "/hopper")
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            t.set(u, v, rr.choice(contents))
    win_shadow(t, u0, v0, u1, v1, 0.5)
    return t


ORE_BITS = ["#2A2624", "#3A3430", "#C87438", "#E8985A", "#B8B8BE", "#8C8C92", "#4A4440", "#86909C"]


def burner_press_front(rng, on):
    t = bronze_front_base("burner_press", rng)
    (a0, b0, a1, b1, _), (f0, g0, f1, g1, _) = win_list("burner_press")
    void_fill(t, a0, b0, a1, b1)
    # guide rails either side of the ram
    for v in range(b0, b1 + 1):
        t.set(a0 + 1, v, IRON[2]); t.set(a1 - 1, v, IRON[3])
    t.hline(a0, a1, b1, IRON[3])
    win_shadow(t, a0, b0, a1, b1)
    paint_fire(t, rng_for("bp/fire"), f0, g0, f1, g1, on)
    win_shadow(t, f0, g0, f1, g1)
    F = BRONZE_T.frame
    for u in range(3, 13, 3):
        t.set(u, 11, F[0]); t.set(u + 1, 11, F[3])
    gauge(t, 13, 0, BRONZE_T, "#D02010" if on else "#606060")
    return t


def press_head(pal, stripes=False):
    """Rows alternate lit / shadow so a 2-px-tall head reads at any height."""
    t = Tex()
    for y in range(16):
        for x in range(16):
            c = pal[1] if y % 2 == 0 else pal[3]
            if stripes and y % 2 == 1:
                c = HAZARD_Y[1] if (x // 2) % 2 == 0 else HAZARD_K[0]
            t.set(x, y, c)
    return t


def rod_tex(pal):
    """Vertical polished rod: lit column stripes (pal hi .. deep)."""
    t = Tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, [pal[1], pal[0], pal[2], pal[3]][x % 4])
    return t


def hatch(t, rng, u0, v0, u1, v1, glow_on=None):
    """Riveted iron door filling a window."""
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            t.set(u, v, IRON[2] if rng.random() > 0.12 else IRON[3])
    t.hline(u0, u1, v0, IRON[1]); t.vline(u0, v0, v1, IRON[1])
    t.hline(u0, u1, v1, IRON[4]); t.vline(u1, v0, v1, IRON[4])
    for (u, v) in [(u0 + 1, v0 + 1), (u1 - 1, v0 + 1), (u0 + 1, v1 - 1), (u1 - 1, v1 - 1)]:
        t.set(u, v, IRON[0])
    my = (v0 + v1) // 2
    t.hline(u0 + 1, u1 - 1, my, IRON[3])       # cross strap
    t.hline(u0 + 1, u1 - 1, my + 1, IRON[1])
    return t


def coke_oven_front(rng, on):
    t = coke_oven_bricks(rng_for("block/coke_oven_bricks"))
    u0, v0, u1, v1, _ = win_list("coke_oven")[0]
    win_lip(t, u0, v0, u1, v1, "#0A0706", IRON[2])
    hatch(t, rng_for("coke_oven/hatch"), u0, v0, u1, v1)
    # peephole with the coal glowing inside, and a latch handle
    ph = ["#FFD060", "#FF8A1C"] if on else ["#2A1208", "#1A0C08"]
    t.set(7, 7, ph[0]); t.set(8, 7, ph[1]); t.set(7, 8, ph[1]); t.set(8, 8, ph[1])
    t.rect(6, 6, 9, 6, IRON[1]); t.rect(6, 9, 9, 9, IRON[3])
    t.hline(6, 9, 11, IRON[0]); t.set(9, 12, IRON[4])
    return t


def blast_furnace_front(rng, on):
    t = fire_bricks(rng_for("block/fire_bricks"))
    (u0, v0, u1, v1, _), (a0, b0, a1, b1, _) = win_list("blast_furnace")
    win_lip(t, u0, v0, u1, v1, "#6A4A20", "#FFF6E0")
    win_lip(t, a0, b0, a1, b1, "#6A4A20", "#FFF6E0")
    hatch(t, rng_for("blast_furnace/hatch"), u0, v0, u1, v1)
    # sight slit
    for u in range(6, 10):
        t.set(u, 4, ("#FFE27A" if u % 2 else "#FF9A2A") if on else "#1A0C08")
    # tap hole with molten iron (on) or a dark clay plug (idle)
    for v in range(b0, b1 + 1):
        for u in range(a0, a1 + 1):
            if on:
                t.set(u, v, "#FFF2B0" if (u + v) % 3 == 0 else "#FFB43C" if v < b1 else "#F07A1E")
            else:
                t.set(u, v, "#3A2A20" if (u + v) % 2 else "#2A1E18")
    win_shadow(t, a0, b0, a1, b1, 0.3)
    # iron trough under the tap
    t.hline(a0 - 1, a1 + 1, 15, IRON[2])
    t.hline(a0, a1, 14, ("#FF9A2A" if on else IRON[3]))
    return t


# ---- electric age -------------------------------------------------------------------------


def steel_front(name, rng):
    return casing_face(STEEL_T, rng, name)


def control_strip(t, v, on, T=STEEL_T, dial=True):
    """Row of controls under a window: dial, LED, switch."""
    if dial:
        t.set(3, v, IRON[1]); t.set(4, v, "#ECEAE0"); t.set(5, v, IRON[3])
    status_led(t, 11, v, on)
    t.set(8, v, T.frame[4]); t.set(9, v, T.accent)


def electric_furnace_front(rng, on):
    t = steel_front("electric_furnace", rng)
    u0, v0, u1, v1, _ = win_list("electric_furnace")[0]
    # ceramic chamber lining, glowing when on
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            if on:
                c = mix("#FF8A2A", "#FFD27A", ((u + v) % 4) / 4.0) if (v - v0) % 3 == 1 else "#7A2A0C"
            else:
                c = "#3A3632" if (v - v0) % 3 == 1 else "#26221F"
            t.set(u, v, c)
    win_shadow(t, u0, v0, u1, v1)
    # vent slits above, controls below
    for u in range(4, 12, 2):
        t.set(u, 1, VOID)
    control_strip(t, 13, on)
    return t


def furnace_top(rng, on):
    t = casing_face(STEEL_T, rng)
    for y in (4, 7, 10):
        recess(t, 4, y, 11, y + 1, STEEL_T, fill=VOID)
        t.hline(5, 10, y + 1, ("#FF9A2A" if on else "#3A2A22"))
    return t


def press_top(rng):
    """Cap of the hydraulic cylinder with two yellow hose ports."""
    t = casing_face(STEEL_T, rng)
    for (x, y) in sorted(disc_mask(8, 8, 3.8)):
        dx, dy = x + 0.5 - 8, y + 0.5 - 8
        t.set(x, y, "#F2F6FA" if dx + dy < -2 else "#C8D0DA" if dx + dy < 1.5 else "#8A94A2")
    for (x, y) in [(8, 5), (5, 8), (10, 8), (8, 10)]:
        t.set(x, y, "#5A6270")
    t.rect(12, 3, 13, 4, "#F4C430"); t.rect(2, 11, 3, 12, "#F4C430")
    t.set(13, 4, "#B88A10"); t.set(3, 12, "#B88A10")
    return t


def funnel_top(rng):
    t = casing_face(STEEL_T, rng, "alloy_smelter", "up", rivets=((2, 2), (12, 2), (2, 12), (12, 12)))
    rr = rng_for("alloy_smelter/funnels")
    cols = ([MAT["copper"][2], MAT["copper"][3], "#3A3430"], [MAT["tin"][2], MAT["tin"][3], "#3A3430"])
    for k, (u0, v0, u1, v1, _) in enumerate(win_list("alloy_smelter", "up")):
        for v in range(v0, v1 + 1):
            for u in range(u0, u1 + 1):
                t.set(u, v, rr.choice(cols[k]))
        win_shadow(t, u0, v0, u1, v1, 0.5)
    t.hline(4, 11, 3, STEEL_T.accent)
    return t


def glass_top(rng, on):
    """Inspection window over the assembler's work cell."""
    t = casing_face(STEEL_T, rng)
    recess(t, 3, 3, 12, 12, STEEL_T, fill="#1A2E50" if on else "#142238")
    for i in range(4):
        t.set(5 + i, 8 - i, "#6A8AC0"); t.set(6 + i, 8 - i, "#4A6AA0")
    t.rect(9, 9, 10, 10, "#E8861C")   # the arm, seen from above
    t.set(9, 9, "#FFB040")
    return t


def coil_tex(on):
    """Heating element rod: copper helix, orange-hot when on."""
    t = Tex()
    Cu = MAT["copper"]
    pal = ["#FFF2BA", "#FFCC56", "#F29426", "#D25E14"] if on else [Cu[4], Cu[3], Cu[1], Cu[0]]
    for y in range(16):
        for x in range(16):
            t.set(x, y, pal[(x + y) % 4])
    return t


def crusher_front(rng, on):
    t = steel_front("crusher", rng)
    u0, v0, u1, v1, _ = win_list("crusher")[0]
    void_fill(t, u0, v0, u1, v1)
    rr = rng_for("crusher/grit")
    # rubble at the bottom of the chamber, more of it falling when on
    for u in range(u0, u1 + 1):
        t.set(u, v1, rr.choice(["#6E6860", "#8E8676", "#4A4640"]))
    if on:
        grit(t, rr, u0 + 3, v0 + 1, u1 - 3, v1 - 1, 9)
    win_shadow(t, u0, v0, u1, v1)
    hazard_strip(t, 3, 12, 12, 13)
    t.hline(3, 12, 11, STEEL_T.frame[4])
    status_led(t, 11, 14, on)
    return t


def jaw_tex(rng):
    """Serrated manganese-steel jaw plate."""
    t = Tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, STEEL[0] if y % 2 == 0 else STEEL[3])
            if x % 4 == 0:
                t.set(x, y, STEEL[2])
    return t


def metal_press_front(rng, on):
    t = steel_front("metal_press", rng)
    u0, v0, u1, v1, _ = win_list("metal_press")[0]
    void_fill(t, u0, v0, u1, v1)
    # yellow hydraulic hoses running down the back wall
    Y = ["#F4C430", "#B88A10"]
    for v in range(v0, v1 + 1):
        t.set(u0 + 1, v, Y[0]); t.set(u1 - 1, v, Y[1])
    t.hline(u0 + 1, u1 - 1, v0, Y[1])
    win_shadow(t, u0, v0, u1, v1)
    hazard_strip(t, 3, 12, 12, 13)
    t.hline(3, 12, 14, STEEL_T.frame[3])
    status_led(t, 13, 14, on)
    return t


def alloy_smelter_front(rng, on):
    t = steel_front("alloy_smelter", rng)
    u0, v0, u1, v1, _ = win_list("alloy_smelter")[0]
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            f = (v - v0) / (v1 - v0)
            t.set(u, v, mix("#2A1208", "#C8420C", f * 0.9) if on else mix(VOID2, "#2A2420", f))
    # heating coil zig-zag on the back wall
    for u in range(u0, u1 + 1):
        t.set(u, v1 - 1 - (u % 2), "#FFB43C" if on else "#4A3A30")
    win_shadow(t, u0, v0, u1, v1)
    # two funnel mouths above the window (inputs) and the output tray slot
    for u0_ in (3, 10):
        t.hline(u0_, u0_ + 2, 1, VOID); t.hline(u0_ + 1, u0_ + 1, 2, VOID)
    t.hline(5, 10, 13, VOID); t.hline(5, 10, 14, STEEL_T.frame[1])
    status_led(t, 12, 13, on)
    return t


def crucible_side(rng):
    t = Tex()
    G = ramp("#2A2A2E", "#3C3C42", "#505058", "#6A6A72")
    for y in range(16):
        for x in range(16):
            t.set(x, y, G[3] if y % 5 == 0 else G[1 + (x % 3 == 0)])
    return t


def crucible_top(on, metal):
    t = Tex()
    for y in range(16):
        for x in range(16):
            if on:
                t.set(x, y, "#FFF2B0" if (x * 5 + y * 3) % 7 == 0 else metal[3] if (x + y) % 3 else metal[4])
            else:
                t.set(x, y, metal[1] if (x + y) % 3 else metal[0])
    return t


def assembler_front(rng, on):
    t = steel_front("assembler", rng)
    u0, v0, u1, v1, _ = win_list("assembler")[0]
    # blue cutting-mat grid on the back wall
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            grid = (u - u0) % 3 == 0 or (v - v0) % 3 == 0
            t.set(u, v, ("#3A6AA8" if on else "#2A4A78") if grid else ("#1A2E50" if on else "#142238"))
    win_shadow(t, u0, v0, u1, v1)
    if on:
        t.set(9, 8, "#FFFFFF"); t.set(10, 8, "#FFE070"); t.set(9, 7, "#FFE070")   # weld spark
    # status screen below
    t.rect(3, 12, 10, 13, "#0E1A12")
    for u in range(4, 10):
        if on:
            t.set(u, 12 + (u % 2), LED_ON[1])
    status_led(t, 12, 12, on)
    return t


def flat(c, rng=None, var=None):
    t = Tex(fill=c)
    if rng and var:
        dither(t, rng, 0, 0, 15, 15, c, var[0], var[1], 0.2)
    return t


def combustion_front(rng, on):
    t = steel_front("combustion_generator", rng)
    (g0, h0, g1, h1, _), (f0, k0, f1, k1, _) = win_list("combustion_generator")
    # fan behind the grille
    void_fill(t, g0, h0, g1, h1)
    for u in range(g0 + 2, g1 - 1, 3):
        t.set(u, h0 + 1, STEEL[3]); t.set(u + 1, h0 + 2, STEEL[3])
    win_shadow(t, g0, h0, g1, h1)
    paint_fire(t, rng_for("cg/fire"), f0, k0, f1, k1, on)
    win_shadow(t, f0, k0, f1, k1)
    gauge(t, 1, 7, STEEL_T, "#D02010" if on else "#606060")
    status_led(t, 13, 13, on)
    return t


def auto_farmer_front(rng, on):
    t = steel_front("auto_farmer", rng)
    u0, v0, u1, v1, _ = win_list("auto_farmer")[0]
    # grow light over a strip of soil with seedlings
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            t.set(u, v, mix("#3A1E4A", "#140C1C", (v - v0) / (v1 - v0)) if on else mix(VOID2, VOID, 0.5))
    for u in range(u0, u1 + 1):
        t.set(u, v1, "#5A3A20" if u % 2 else "#4A2E18")
        t.set(u, v1 - 1, "#6A4426")
    for u in (u0 + 1, u0 + 4, u0 + 7):
        t.set(u, v1 - 2, "#4CB040" if on else "#2E6A2A")
        t.set(u + 1, v1 - 3, "#7AD860" if on else "#3A8030")
    win_shadow(t, u0, v0, u1, v1)
    # green accent band and a seed slot
    G = ["#2E8C3C", "#4CB05A", "#1F6A2C"]
    t.hline(2, 13, 11, G[1]); t.hline(2, 13, 12, G[2])
    t.set(7, 13, VOID); t.set(8, 13, VOID); t.set(7, 14, "#C8A860")
    status_led(t, 12, 13, on)
    return t


def auto_farmer_top(rng, on):
    t = casing_face(STEEL_T, rng, "auto_farmer", "up")
    u0, v0, u1, v1, _ = win_list("auto_farmer", "up")[0]
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            row = (u - u0) % 3
            t.set(u, v, "#4A2E18" if row == 0 else "#6A4426" if row == 1 else "#5A3A20")
            if row == 1 and (v - v0) % 3 == 1:
                t.set(u, v, ("#8ADC5A" if (v % 2) else "#C8B040") if on else "#3E8A30")
    win_shadow(t, u0, v0, u1, v1, 0.3)
    return t


def auto_farmer_side(rng):
    t = family_side(STEEL_T, rng, "steel")
    G = ["#2E8C3C", "#4CB05A", "#1F6A2C"]
    t.hline(2, 13, 12, G[1]); t.hline(2, 13, 13, G[2])
    # leaf badge
    t.set(3, 3, G[1]); t.set(4, 3, G[1]); t.set(4, 4, G[0]); t.set(3, 4, G[2])
    return t


def solar_top(rng):
    """14x14 cell field (3x3 cells) inside a 1-px aluminium rim."""
    cells = ramp("#14285A", "#1E3A78", "#2A4C92")
    t = Tex(fill=ALU_T.frame[2])
    t.hline(0, 15, 0, ALU_T.frame[1]); t.vline(0, 0, 15, ALU_T.frame[1])
    for gy in range(3):
        for gx in range(3):
            x0, y0 = 1 + gx * 5, 1 + gy * 5
            t.rect(x0, y0, x0 + 3, y0 + 3, cells[1])
            t.set(x0, y0, cells[2]); t.set(x0 + 3, y0 + 3, cells[0])
            t.hline(x0, x0 + 3, y0 + 1, mix(cells[1], "#8CA8DC", 0.3))
    for i in range(14):   # silver bus lines between the cells
        for k in (5, 10):
            t.set(k, 1 + i, "#C8D4E8"); t.set(1 + i, k, "#C8D4E8")
    for i in range(7):    # sky reflection
        x, y = 3 + i, 10 - i
        t.set(x, y, mix(t.get(x, y), "#FFFFFF", 0.22))
    return t


def solar_side(rng):
    t = Tex(fill=ALU_T.panel[2])
    for x in range(16):
        t.set(x, 10, ALU_T.frame[1])
        t.set(x, 11, "#2A4C92" if x % 3 else ALU_T.frame[3])   # cell edge peeking over the rim
        t.set(x, 12, ALU_T.panel[2]); t.set(x, 13, ALU_T.panel[3])
        t.set(x, 14, ALU_T.panel[3] if x % 4 else ALU_T.frame[3]); t.set(x, 15, ALU_T.frame[4])
    for y in range(10):
        t.hline(0, 15, y, ALU_T.panel[2 + (y % 2)])
    return t


# ---- energy cells -------------------------------------------------------------------------


def cell_colours(T):
    if T.glow:
        return [CYAN_GLOW[0], CYAN_GLOW[1], CYAN_GLOW[2], CYAN_GLOW[3]]
    return ["#2A8A3A", "#4AD85E", "#8CF09A", "#D8FFE0"]


def cell_front(T, rng, on, name):
    t = casing_face(T, rng, name)
    u0, v0, u1, v1, _ = win_list(name)[0]
    t.rect(u0, v0, u1, v1, VOID)
    Y = ("#FFF6B0", "#FFD83A", "#C88A10") if on else ("#E0B830", "#B08A18", "#6A5010")
    t.sprite(BOLT, {"Y": Y[1], "o": Y[2]}, 5, 3)
    t.set(9, 3, Y[0])
    G = cell_colours(T)
    for u in range(u0, u1 + 1):   # charge bar along the bottom of the window
        lit = u <= u0 + (6 if on else 3)
        t.set(u, v1, G[2] if lit else "#1A1E1C")
    t.set(u0, v1, G[1])
    win_shadow(t, u0, v0, u1, v1, 0.2)
    return t


def cell_side(T, rng, on, name):
    t = casing_face(T, rng, name, "west", rivets=((2, 2), (12, 2), (2, 12), (12, 12)))
    u0, v0, u1, v1, _ = win_list(name, "west")[0]
    t.rect(u0, v0, u1, v1, VOID)
    G = cell_colours(T)
    fill = v0 + (3 if on else 6)
    for v in range(v0 + 1, v1):
        for u in range(u0 + 1, u1):
            if v >= fill:
                t.set(u, v, G[2] if (v % 2 == 0) else G[1])
            else:
                t.set(u, v, "#141816")
    for v in range(v0 + 1, v1, 2):   # scale ticks
        t.set(u0 - 2, v, T.frame[3]); t.set(u1 + 2, v, T.frame[3])
    return t


def cell_top(T, rng):
    t = casing_face(T, rng)
    # + and - terminals, tier stripe between them
    for (x, c) in ((4, "#C82828"), (10, "#202020")):
        t.rect(x, 6, x + 1, 9, IRON[1]); t.set(x, 6, IRON[0]); t.set(x + 1, 9, IRON[3])
        t.rect(x, 7, x + 1, 8, c)
    t.hline(6, 9, 7, T.accent); t.hline(6, 9, 8, darken(T.accent, 0.35))
    return t


# ---- automation age: miner & geothermal ---------------------------------------------------


def miner_side_tex(rng, on, phase=0.0):
    t = casing_face(ALU_T, rng, "miner", "west")
    u0, v0, u1, v1, _ = win_list("miner", "west")[0]
    paint_rift(t, u0, v0, u1, v1, on, phase)
    win_shadow(t, u0, v0, u1, v1, 0.3)
    hazard_band(t, 2, 12, 13, 13)
    return t


def geo_front(rng, on, phase=0):
    t = casing_face(ALU_T, rng, "geothermal_generator")
    u0, v0, u1, v1, _ = win_list("geothermal_generator")[0]
    paint_lava(t, u0, v0, u1, v1, on, phase)
    win_shadow(t, u0, v0, u1, v1, 0.3)
    status_led(t, 12, 14, on)
    return t


def geo_side(rng, on, phase=0):
    t = casing_face(ALU_T, rng, "geothermal_generator", "west", rivets=((6, 2), (6, 12)))
    for (u0, v0, u1, v1, _) in win_list("geothermal_generator", "west"):
        paint_lava(t, u0, v0, u1, v1, on, phase, oy=5)
    # heat-exchanger fins between the channels
    for y in range(4, 12, 2):
        t.hline(6, 9, y, ALU_T.frame[3]); t.hline(6, 9, y + 1, ALU_T.frame[1])
    return t


def basalt(rng):
    t = Tex()
    B = ramp("#1E1E22", "#2A2A30", "#36363C", "#44444A", "#56565C")
    for y in range(16):
        for x in range(16):
            t.set(x, y, B[1 + (rng.random() < 0.5) + (x % 4 == 0)] if y % 5 else B[0])
    return t


def anim(frames):
    """Stack frames into a vertical animation strip."""
    t = Tex(16, 16 * len(frames))
    for i, f in enumerate(frames):
        t.blit(f, 0, 16 * i)
    return t


# ---- automation / industrial / orbital processing machines -------------------------------
# ore washer (aluminium), induction smelter + hydraulic press (titanium),
# precision assembler + plasma forge (orbital white/gold)

WATER = ramp("#0E2448", "#1A4E9A", "#2F7CC8", "#5AB4E8", "#BDEBFF")      # deep .. foam
WATER_PIPE = ramp("#9AD4FF", "#4A9AE0", "#2A6AB0", "#1A4478")              # hi .. deep
GOLD_FOIL = ramp("#6A4A08", "#A87C14", "#DDB030", "#F6D860", "#FFF6C0")    # deep .. hi
WET_ORE = [("#C87438", "#7A3A14", "#FFD0A0"), ("#B8B8BE", "#5E5E66", "#FFFFFF"),
           ("#E0B024", "#7A5408", "#FFF4A6")]                                # body, shadow, glint


def win_pixels(name, face="north"):
    return {(u, v) for (u0, v0, u1, v1, _) in win_list(name, face)
            for u in range(u0, u1 + 1) for v in range(v0, v1 + 1)}


def win_ring(name, face="north"):
    """Flush-face pixels touching a (possibly stepped) window, 8-neighbourhood."""
    W = win_pixels(name, face)
    return {(u + du, v + dv) for (u, v) in W for du in (-1, 0, 1) for dv in (-1, 0, 1)} - W


def ti_brackets(t, name, face="north"):
    """Heavy L-shaped corner plates: the titanium casing's bold trim."""
    F = TITAN_T.frame
    for (cx, cy, sx, sy) in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        pix = [(cx + sx * i, cy) for i in range(4)] + [(cx, cy + sy * i) for i in range(1, 4)]
        if name and any(in_window(name, face, x + dx, y + dy) for (x, y) in pix
                        for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
            continue
        for (x, y) in pix:
            t.set(x, y, F[0] if (sx > 0 and y == cy) or (sy > 0 and x == cx) else F[1])
        for i in range(1, 4):   # cast shadow inside the L
            t.set(cx + sx * i, cy + sy, mix(t.get(cx + sx * i, cy + sy), F[4], 0.5))
            t.set(cx + sx, cy + sy * i, mix(t.get(cx + sx, cy + sy * i), F[4], 0.5))
        t.set(cx + sx, cy + sy, F[3])   # bolt head
        t.set(cx, cy, TITAN_T.accent)


def gold_trim(t):
    """Orbital casings: a gold inner ring on the white frame."""
    for i in range(1, 15):
        t.set(i, 1, GOLD_FOIL[3]); t.set(1, i, GOLD_FOIL[3])
        t.set(i, 14, GOLD_FOIL[1]); t.set(14, i, GOLD_FOIL[1])
    t.set(1, 14, GOLD_FOIL[2]); t.set(14, 1, GOLD_FOIL[2])


def orb_face(rng, name=None, face="north", rivets=((2, 2), (12, 2), (2, 12), (12, 12))):
    t = casing_face(ORBITAL_T, rng, name, face, rivets=())
    gold_trim(t)
    for (x, y) in rivets:
        if name is None or not any(in_window(name, face, x + dx, y + dy) for dx in (-1, 0, 1, 2) for dy in (-1, 0, 1, 2)):
            rivet(t, x, y, ORBITAL_T, pal=[GOLD_FOIL[4], GOLD_FOIL[3], GOLD_FOIL[2], GOLD_FOIL[1], GOLD_FOIL[0]])
    return t


def ti_face(rng, name=None, face="north"):
    t = casing_face(TITAN_T, rng, name, face, rivets=())
    ti_brackets(t, name, face)
    return t


def glint(t, u, v, n, amt=0.3):
    for i in range(n):
        t.set(u + i, v - i, mix(t.get(u + i, v - i), "#FFFFFF", amt))


def ore_chunk(t, u, v, k):
    body, shade, hi = WET_ORE[k % 3]
    t.set(u, v, hi); t.set(u + 1, v, body); t.set(u, v + 1, body); t.set(u + 1, v + 1, shade)


# ---- ore washer ---------------------------------------------------------------------------

WASH_NOZZLES = (4, 7, 11)   # texture columns of the spray nozzles (model: x = 16 - u)


def washer_front(rng, on, phase=0):
    t = casing_face(ALU_T, rng, "ore_washer")
    (u0, v0, u1, v1, _), (d0, e0, d1, e1, _) = win_list("ore_washer")
    wl = v0 + 2                                      # water line (spray bar hangs above it)
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            if v < wl:
                c = mix(VOID2, "#1A2A3A", (v - v0) / 2.0)
            else:
                f = (v - wl) / max(1, v1 - wl)
                if on:
                    base = mix(WATER[3], WATER[1], f)
                    n = lava_value(u * 2 - phase * 2, v + phase * 2)
                    c = lighten(base, 0.4) if n < 0.28 else darken(base, 0.18) if n > 0.66 else base
                else:
                    c = mix(WATER[2], WATER[0], f * 0.9 + 0.1)
            t.set(u, v, c)
    # surface: churning foam when on, a calm lit line when idle
    for u in range(u0, u1 + 1):
        if on:
            t.set(u, wl, WATER[4] if (u + phase) % 3 else WATER[3])
        else:
            t.set(u, wl, WATER[3] if u % 4 else WATER[2])
    # spray from the nozzles
    if on:
        for u in WASH_NOZZLES:
            for v in range(v0 + 1, wl):
                t.set(u, v, WATER[4] if (v + phase) % 2 else WATER[3])
            t.set(u - 1, wl + 1, WATER[4]); t.set(u + 1, wl + 1, WATER[4])
    # ore: tumbling in the drum when on, resting on the bottom when idle
    for k in range(3):
        if on:
            a = 2 * math.pi * (phase / 8.0 + k / 3.0)
            cu, cv = 7.0 + 3.2 * math.cos(a), (wl + v1) / 2.0 + 1.6 * math.sin(a)
            ore_chunk(t, int(round(cu)), int(round(cv)), k)
        else:
            ore_chunk(t, u0 + 1 + 3 * k, v1 - 1, k)
    # drum ribs at the window edges and a glass glint
    for v in range(wl + 1, v1 + 1):
        t.set(u0, v, darken(t.get(u0, v), 0.35)); t.set(u1, v, darken(t.get(u1, v), 0.25))
    glint(t, u0 + 5, v1 - 1, 3, 0.35); glint(t, u0 + 7, v1 - 1, 2, 0.25)
    win_shadow(t, u0, v0, u1, v1, 0.3)
    # drain grate: bars with water running through when on
    for v in range(e0, e1 + 1):
        for u in range(d0, d1 + 1):
            if (u - d0) % 2 == 0:
                t.set(u, v, IRON[1] if v == e0 else IRON[2])
            else:
                t.set(u, v, (WATER[3] if (v + phase) % 2 else WATER[2]) if on else VOID)
    # inlet valve wheel and status light
    t.set(3, 12, WATER_PIPE[1]); t.set(4, 12, WATER_PIPE[0]); t.set(3, 13, WATER_PIPE[2]); t.set(4, 13, WATER_PIPE[3])
    status_led(t, 12, 12, on)
    return t


def washer_top(rng):
    t = casing_face(ALU_T, rng, "ore_washer", "up")
    u0, v0, u1, v1, _ = win_list("ore_washer", "up")[0]
    rr = rng_for("ore_washer/basin")
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            c = WATER[2] if (u + 2 * v) % 5 else WATER[3]
            if rr.random() < 0.12:
                c = rr.choice([WET_ORE[0][0], WET_ORE[1][0], WET_ORE[1][1], "#3A3430"])
            t.set(u, v, c)
    glint(t, u0 + 1, v1 - 2, 3, 0.4)
    win_shadow(t, u0, v0, u1, v1, 0.5)
    # water inlet pipe running in from the back edge
    for v in range(1, v0 - 1):
        t.set(7, v, WATER_PIPE[1]); t.set(8, v, WATER_PIPE[3])
    return t


def washer_side(rng):
    t = family_side(ALU_T, rng, "aluminum")
    # sight glass showing the water level
    t.rect(4, 2, 6, 8, IRON[2]); t.vline(4, 2, 8, IRON[1]); t.vline(6, 2, 8, IRON[3])
    for v in range(3, 8):
        t.set(5, v, WATER[3] if v >= 5 else "#1A2430")
    t.set(5, 5, WATER[4])
    # blue supply pipe with a valve
    for v in range(2, 10):
        t.set(10, v, WATER_PIPE[0]); t.set(11, v, WATER_PIPE[1]); t.set(12, v, WATER_PIPE[3])
    t.rect(9, 4, 13, 4, "#C82828"); t.set(9, 4, "#FF6A5A"); t.set(13, 4, "#7A1010")
    return t


# ---- induction smelter --------------------------------------------------------------------


def induction_front(rng, on):
    t = ti_face(rng, "induction_smelter")
    u0, v0, u1, v1, _ = win_list("induction_smelter")[0]
    cx, cy = (u0 + u1 + 1) / 2.0, v1 - 2.0
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            brick = (v - v0) % 3 == 2 or (u + (v - v0) // 3 * 2) % 4 == 0
            base = "#2A2630" if brick else "#3A3542"
            if on:
                d = math.hypot((u + 0.5 - cx) / 6.0, (v + 0.5 - cy) / 5.0)
                g = max(0.0, 1.0 - d)
                base = mix(base, "#FF7A20", g * 0.85)
            t.set(u, v, base)
    win_shadow(t, u0, v0, u1, v1, 0.35)
    # current readout + LED under the bay
    t.rect(3, 12, 9, 13, "#140E0C")
    for u in range(4, 9):
        if on or u < 5:
            t.set(u, 12 + (u % 2), "#FFB02E" if on else "#6A4410")
    status_led(t, 11, 12, on)
    t.set(11, 13, TITAN_T.frame[3]); t.set(12, 13, TITAN_T.frame[3])
    return t


def induction_top(rng, on):
    t = ti_face(rng, "induction_smelter", "up")
    u0, v0, u1, v1, _ = win_list("induction_smelter", "up")[0]
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            f = abs((v + 0.5) - (v0 + v1 + 1) / 2.0) / ((v1 - v0 + 1) / 2.0)
            t.set(u, v, mix("#FFD27A", "#A8300E", f) if on else mix("#2A2226", VOID2, f))
    win_shadow(t, u0, v0, u1, v1, 0.4)
    return t


def fin_tex(rng):
    F = TITAN_T.frame
    t = flat(F[2], rng, (F[3], F[1]))
    t.hline(0, 15, 0, F[0]); t.hline(0, 15, 1, F[1])
    return t


def induction_coil(on):
    """Copper tube wound round the crucible, lit orange by the heat when on."""
    Cu = MAT["copper"]
    pal = [Cu[4], Cu[3], Cu[2], Cu[1]] if not on else ["#FFF2D0", "#FFC47A", "#F09040", "#B85A20"]
    t = Tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, pal[(x + 2 * y) % 4] if y % 2 == 0 else pal[2 + (x % 2)])
    return t


def induction_crucible(on):
    t = Tex()
    G = ramp("#26242C", "#34323C", "#46444E", "#5C5A66")
    for y in range(16):
        for x in range(16):
            if on:
                t.set(x, y, "#FFF6D8" if (x + y) % 5 == 0 else "#FFE08A" if y % 3 else "#FFB43C")
            else:
                t.set(x, y, G[3] if y % 5 == 0 else G[1 + (x % 3 == 0)])
    return t


# ---- hydraulic press ----------------------------------------------------------------------


def hpress_front(rng, on):
    t = ti_face(rng, "hydraulic_press")
    u0, v0, u1, v1, _ = win_list("hydraulic_press")[0]
    void_fill(t, u0, v0, u1, v1, top=VOID2, bottom="#1E1C28")
    F = TITAN_T.frame
    # guide columns at the window edges, red oil lines feeding both cylinders
    for v in range(v0, v1 + 1):
        t.set(u0, v, F[3]); t.set(u0 + 1, v, F[2])
        t.set(u1 - 1, v, F[2]); t.set(u1, v, F[3])
    for v in range(v0 + 2, v1 - 1):      # red oil hoses down the back wall
        t.set(u0 + 3, v, "#A01818"); t.set(u1 - 3, v, "#6A0E0E")
    win_shadow(t, u0, v0, u1, v1, 0.3)
    # pressure bar display, e-stop and LED under the bay
    t.rect(3, 12, 9, 13, "#100E14")
    lit = 9 if on else 4
    for u in range(4, lit):
        t.set(u, 12, HAZARD_Y[2] if u < 7 else "#FF5A2A"); t.set(u, 13, HAZARD_Y[1] if u < 7 else "#C8300C")
    t.set(10, 12, "#E02424"); t.set(10, 13, "#8A1010")
    status_led(t, 12, 12, on)
    return t


def hpress_top(rng):
    t = ti_face(rng)
    F = TITAN_T.frame
    for (cx, cy) in ((5.5, 8.0), (10.5, 8.0)):
        for (x, y) in sorted(disc_mask(cx, cy, 2.8)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            t.set(x, y, F[0] if dx + dy < -1.8 else F[1] if dx + dy < 0.8 else F[3])
        t.set(int(cx), int(cy), F[4])
    for x in range(5, 11):              # oil manifold linking the two cylinders
        t.set(x, 12, "#A01818"); t.set(x, 13, "#6A0E0E")
    t.rect(7, 3, 8, 4, TITAN_T.accent); t.set(8, 4, darken(TITAN_T.accent, 0.4))
    return t


def hpress_cyl(rng):
    t = rod_tex(ramp("#F4F2FF", "#CAC6F0", "#9490CC", "#4E4A7A"))
    for y in range(0, 16, 4):
        t.hline(0, 15, y, "#3A366A")
    return t


def hazard_ram():
    t = Tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, HAZARD_Y[1] if (x + y) % 6 < 3 else HAZARD_K[0])
    return t


# ---- precision assembler ------------------------------------------------------------------


def gold_foil(t, rng, u0, v0, u1, v1):
    """Crinkled multi-layer insulation."""
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            n = rng.random()
            t.set(u, v, GOLD_FOIL[4] if n < 0.1 else GOLD_FOIL[3] if n < 0.45 else
                  GOLD_FOIL[2] if n < 0.85 else GOLD_FOIL[1])
    for _ in range(max(1, (u1 - u0 + 1) * (v1 - v0 + 1) // 12)):   # creases
        u, v = rng.randint(u0, u1 - 1), rng.randint(v0, v1)
        t.set(u, v, GOLD_FOIL[4]); t.set(u + 1, v, GOLD_FOIL[1])


def dome_rim(t, name):
    """Glass rim around a stepped window: lit up-left, dark down-right."""
    W = win_pixels(name)
    us = [u for u, _ in W]; vs = [v for _, v in W]
    cx, cy = (min(us) + max(us) + 1) / 2.0, (min(vs) + max(vs) + 1) / 2.0
    for (u, v) in win_ring(name):
        lit = (u + 0.5 - cx) + (v + 0.5 - cy) < 0
        t.set(u, v, GLASS[4] if lit else GLASS[1])


def passembler_front(rng, on):
    name = "precision_assembler"
    t = orb_face(rng, name, rivets=((2, 12), (12, 12)))
    W = win_pixels(name)
    vs = [v for _, v in W]
    v0, v1 = min(vs), max(vs)
    for (u, v) in W:
        f = (v - v0) / float(v1 - v0)
        grid = u % 3 == 0 or v % 3 == 0
        top, bot = ("#E8F6FF", "#9CC4DC") if on else ("#8AA4B8", "#4A6074")
        c = mix(top, bot, f)
        t.set(u, v, darken(c, 0.08) if grid else c)
    dome_rim(t, name)
    for (u, v) in W:   # the dome's own shadow on the back wall
        if (u, v - 1) not in W or (u - 1, v) not in W:
            t.set(u, v, darken(t.get(u, v), 0.3))
    glint(t, 4, 7, 3, 0.45); glint(t, 5, 8, 2, 0.3)
    gold_foil(t, rng_for("pa/foil"), 3, 12, 10, 13)
    status_led(t, 12, 13, on)
    return t


def passembler_top(rng):
    """HEPA filter grille of the clean room between gold foil strips."""
    T = ORBITAL_T
    t = orb_face(rng)
    recess(t, 4, 4, 11, 11, T, fill=T.panel[3])
    for y in range(5, 11):
        for x in range(5, 11):
            t.set(x, y, T.panel[0] if (x + y) % 2 else T.panel[2])
    gold_foil(t, rng_for("pa/topfoil"), 4, 13, 11, 13)
    gold_foil(t, rng_for("pa/topfoil2"), 4, 2, 11, 2)
    return t


def passembler_side(rng):
    t = family_side(ORBITAL_T, rng, "orbital")
    recess(t, 3, 3, 9, 10, ORBITAL_T)
    gold_foil(t, rng_for("pa/sidefoil"), 4, 4, 9, 10)
    return t


# ---- plasma forge -------------------------------------------------------------------------


def plasma_front(rng, on, phase=0):
    name = "plasma_forge"
    t = orb_face(rng, name)
    W = win_pixels(name)
    cx, cy = 8.0, 7.5
    # magnetic ring: copper windings between dark pole shoes, energised violet when on
    Cu = MAT["copper"]
    for (u, v) in win_ring(name):
        a = math.atan2(v + 0.5 - cy, u + 0.5 - cx)
        seg = int((a + math.pi) / (2 * math.pi) * 16) % 2
        lit = (u + 0.5 - cx) + (v + 0.5 - cy) < 0
        if seg:
            c = (Cu[3] if lit else Cu[1])
        else:
            c = (VIOLET_GLOW[2] if lit else VIOLET_GLOW[1]) if on else (IRON[2] if lit else IRON[4])
        t.set(u, v, c)
    # the chamber
    arc = {}
    if on:
        ar = rng_for("pf/arc%d" % phase)
        x = 7.0
        for v in range(4, 12):     # jagged arc between the electrode tips (u 7-8)
            x = max(6.0, min(9.0, x + ar.choice((-1, 0, 0, 1)))) if 5 < v < 10 else (7.0 if v < 8 else 8.0)
            arc[v] = int(x)
    for (u, v) in W:
        d = math.hypot((u + 0.5 - cx) / 5.0, (v + 0.5 - cy) / 5.5)
        if on:
            pulse = 0.12 * math.sin(2 * math.pi * phase / 8.0)
            g = max(0.0, 1.0 - d + pulse)
            c = mix("#12081E", VIOLET_GLOW[1], min(1.0, g * 1.2))
            if v in arc:
                dx = abs(u - arc[v])
                if dx == 0:
                    c = "#F4FFFF"
                elif dx == 1:
                    c = CYAN_GLOW[2]
                elif dx == 2:
                    c = mix(c, VIOLET_GLOW[2], 0.6)
        else:
            c = mix("#2A1840", "#0C0814", min(1.0, d))
        t.set(u, v, c)
    for (u, v) in W:
        if (u, v - 1) not in W or (u - 1, v) not in W:
            t.set(u, v, darken(t.get(u, v), 0.3))
    status_led(t, 12, 14, on)
    t.set(3, 14, ORBITAL_T.accent); t.set(4, 14, ORBITAL_T.accent)
    return t


def plasma_top(rng, on):
    """Sealed round hatch with a gold bolt circle and a violet sight port."""
    T = ORBITAL_T
    t = orb_face(rng, rivets=())
    for (x, y) in sorted(disc_mask(8, 8, 5.2)):
        dx, dy = x + 0.5 - 8, y + 0.5 - 8
        d = math.hypot(dx, dy)
        if d > 4.3:
            c = T.panel[3] if dx + dy > 0 else T.panel[0]
        else:
            c = T.panel[1] if dx + dy < 0 else T.panel[2]
        t.set(x, y, c)
    for k in range(8):
        a = k * math.pi / 4
        t.set(int(8 + 3.6 * math.cos(a)), int(8 + 3.6 * math.sin(a)), T.frame[1])
    port = VIOLET_GLOW if on else ramp("#1A0E2A", "#2A1840", "#3A2458", "#4A3070")
    t.rect(7, 7, 8, 8, port[1]); t.set(7, 7, port[3]); t.set(8, 8, port[0])
    return t


def pole_tex(on):
    """Magnet pole piece: copper winding with a glowing tip when on."""
    Cu = MAT["copper"]
    t = Tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, Cu[3] if (x + y) % 2 else Cu[1])
            if on and (x + y) % 5 == 0:
                t.set(x, y, VIOLET_GLOW[2])
    return t


def electrode_tex(on):
    t = rod_tex(ramp("#E8ECF2", "#B4BAC4", "#7A8290", "#3A3E48"))
    if on:
        for x in range(16):
            for y in range(16):
                if y % 4 == 0:
                    t.set(x, y, CYAN_GLOW[2])
    return t


# ---- crates ----------------------------------------------------------------------------------

SPRUCE = ramp("#241609", "#382412", "#50361E", "#6A4A2A", "#84603A")


def planks(t, rng, pal, x0, y0, x1, y1, vertical=False, board=4):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            k = (x - x0) if vertical else (y - y0)
            c = pal[2] if rng.random() > 0.2 else pal[3 if rng.random() < 0.5 else 1]
            if k % board == board - 1:
                c = pal[0]
            elif k % board == 0:
                c = mix(c, pal[4], 0.3)
            t.set(x, y, c)


def crate_face(rng, top, bronze):
    wood = SPRUCE if bronze else WOOD
    t = Tex()
    planks(t, rng, wood, 0, 0, 15, 15, vertical=top, board=4 if not bronze else 5)
    # outer boards (the crate's frame)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i), (i, 1), (i, 14), (1, i), (14, i)):
            edge = x in (0, 15) or y in (0, 15)
            t.set(x, y, wood[1] if edge else wood[3])
    t.hline(2, 13, 2, wood[0]); t.vline(2, 2, 13, wood[0])
    if not bronze:
        if not top:   # diagonal brace
            for i in range(2, 14):
                t.set(i, 15 - i, wood[3]); t.set(i, 16 - i, wood[1] if i < 14 else wood[3])
        # dark iron corner brackets with nails
        K = ["#1A1A1E", "#2E2E34", "#4A4A52", "#6A6A74"]
        for (cx, cy, sx, sy) in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
            for i in range(4):
                t.set(cx + sx * i, cy, K[2]); t.set(cx, cy + sy * i, K[2])
                t.set(cx + sx * i, cy + sy, K[1]); t.set(cx + sx, cy + sy * i, K[1])
            t.set(cx + sx, cy + sy, K[3])
    else:
        B = [BRONZE_T.frame[i] for i in range(5)]
        # bronze border bands
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                t.set(x, y, B[2])
            t.set(i, 0, B[1]); t.set(0, i, B[1]); t.set(i, 15, B[3]); t.set(15, i, B[3])
        # cross band(s) with bolts
        mids = [(7, 8)]
        for (a, b) in mids:
            t.hline(1, 14, a, B[1]); t.hline(1, 14, b, B[3])
            if top:
                t.vline(a, 1, 14, B[1]); t.vline(b, 1, 14, B[3])
        for (x, y) in ((3, 7), (12, 7), (7, 3) if top else (1, 1), (7, 12) if top else (14, 14)):
            t.set(x, y, B[0]); t.set(x + 1, y + 1, B[4])
        for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
            t.set(x, y, B[0]); t.set(x + 1, y + 1, B[4])
    return t
# ---- miner (area quarry) ------------------------------------------------------------


def hazard_band(t, x0, y0, x1, y1, phase=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            yellow = (x + y + phase) % 4 < 2
            if yellow:
                t.set(x, y, HAZARD_Y[2] if y == y0 else HAZARD_Y[1])
            else:
                t.set(x, y, HAZARD_K[1] if y == y0 else HAZARD_K[0])
    t.hline(x0, x1, y1, darken(HAZARD_Y[0], 0.4))

def miner_top(T, rng):
    """Hatch with the drive motor seen from above."""
    t = panel_base(T, rng)
    recess(t, 3, 3, 12, 12, T)
    cx, cy = 8.0, 8.0
    for (x, y) in sorted(disc_mask(cx, cy, 4.3)):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy)
        lit = -(dx + dy) / (d + 0.01)
        if d > 3.3:
            c = DARK_STEEL[1] if lit > 0 else DARK_STEEL[3]
        else:
            fin = int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 8) % 2
            c = STEEL[2] if fin else STEEL[3]
            if lit > 0.5 and d > 2:
                c = STEEL[1] if fin else STEEL[2]
        t.set(x, y, c)
    t.rect(7, 7, 8, 8, T.frame[2]); t.set(7, 7, T.frame[0]); t.set(8, 8, T.frame[3])
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        t.set(x, y, HAZARD_Y[1])
    return t


def miner_bottom(T, rng):
    """Spiral drill head seen from below."""
    t = panel_base(T, rng, rivets=False)
    recess(t, 2, 2, 13, 13, T, fill=VOID)
    cx, cy = 8.0, 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > 5.3:
                continue
            spiral = (math.atan2(dy, dx) / (2 * math.pi) * 3 + d * 0.22) % 1.0
            if d > 4.4:
                c = STEEL[1] if spiral < 0.35 else STEEL[3]
            elif spiral < 0.3:
                c = STEEL[0] if d < 3 else STEEL[1]
            elif spiral < 0.55:
                c = STEEL[2]
            else:
                c = STEEL[4]
            t.set(x, y, c)
    t.rect(7, 7, 8, 8, "#FFE070"); t.set(8, 8, "#C09020"); t.set(7, 7, "#FFF6C0")
    return t

# ---- energy cell ---------------------------------------------------------------------

BOLT = [
    "....YY",
    "...YYo",
    "..YYo.",
    ".YYo..",
    "YYYYYo",
    "..YYo.",
    ".YYo..",
    "YYo...",
    "Yo....",
]

# ---- cable & item pipe (6x6 cross-section models sample UV 5-11) ----------------------

GLASS = ramp("#4E646C", "#7E9CA4", "#A8C6CC", "#D2E8EA", "#F6FFFF")


def power_cable(T, rng):
    """Dark rubber cable, tier-coloured stripe, copper core in the centre."""
    R = MAT["rubber"]
    Cu = MAT["copper"]
    S = [CYAN_GLOW[2], CYAN_GLOW[1]] if T.glow else [T.frame[0], T.frame[1]]
    t = Tex(fill=R[2])
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.1:
                t.set(x, y, R[1])
    for y in range(16):
        t.set(4, y, R[0]); t.set(11, y, R[0])
        t.set(5, y, R[3]); t.set(6, y, R[4])
        t.set(7, y, S[0]); t.set(8, y, S[1])
        t.set(9, y, R[2]); t.set(10, y, R[1])
        if y % 4 == 3:   # moulding marks along the stripe
            t.set(7, y, mix(S[0], R[2], 0.5)); t.set(8, y, mix(S[1], R[1], 0.5))
    # copper core cross-section (seen on the cable's end faces)
    t.hline(5, 10, 5, R[0]); t.hline(5, 10, 10, R[4])
    t.vline(5, 5, 10, R[0]); t.vline(10, 5, 10, R[3])
    t.rect(6, 6, 9, 9, Cu[2])
    t.set(6, 6, Cu[4]); t.set(7, 6, Cu[3]); t.set(6, 7, Cu[3])
    t.set(8, 7, Cu[3]); t.set(7, 8, Cu[1]); t.set(9, 9, Cu[0]); t.set(9, 8, Cu[1]); t.set(8, 9, Cu[1])
    return t


def item_pipe(T, rng):
    """Bright glass duct with tier-coloured rims and rings (unlike the dark cable)."""
    F = T.frame
    t = Tex(fill=GLASS[2])
    for y in range(16):
        for x in range(16):
            t.set(x, y, GLASS[3] if (x - y) % 6 == 0 else GLASS[2])
    for y in range(16):
        t.set(5, y, F[1]); t.set(10, y, F[3])
        t.set(6, y, GLASS[4]); t.set(7, y, GLASS[3]); t.set(8, y, GLASS[2]); t.set(9, y, GLASS[1])
        t.set(4, y, darken(F[3], 0.3)); t.set(11, y, darken(F[3], 0.3))
    for y in (0, 5, 10, 15):   # tier rings; rows 5/10 frame the centre square
        t.hline(4, 11, y, F[2])
        t.set(5, y, F[0]); t.set(10, y, F[3]); t.set(11, y, F[4]); t.set(4, y, F[3])
    # a faint item silhouette travelling inside
    t.set(7, 2, GLASS[1]); t.set(8, 2, GLASS[1]); t.set(8, 3, GLASS[0])
    if T.glow:
        for y in (0, 5, 10, 15):
            t.set(7, y, CYAN_GLOW[2]); t.set(8, y, CYAN_GLOW[1])
    return t


def item_pipe_extract(rng):
    """Steel connector plate with an orange arrow grille (pipe end that pulls)."""
    t = panel_base(STEEL_TIER, rng)
    recess(t, 3, 3, 12, 12, STEEL_TIER, fill=VOID)
    O = ramp("#8A2C0C", "#D8581A", "#FF8A2A", "#FFC070")
    for k in range(3):
        y0 = 4 + k * 3
        for i, (x, dy) in enumerate([(7, 0), (8, 0), (6, 1), (9, 1), (5, 2), (10, 2), (4, 3), (11, 3)]):
            if y0 + dy > 11:
                continue
            t.set(x, y0 + dy, O[3] if x <= 7 and dy == 0 else (O[2] if x <= 7 else O[1]))
        for (x, dy) in [(6, 2), (9, 2), (5, 3), (10, 3)]:
            if y0 + dy <= 11:
                t.set(x, y0 + dy, O[0])
    return t

# ---- ores (vanilla stone / deepslate look) ----------------------------------------------

STONE = ramp("#6A6A6A", "#747474", "#7E7E7E", "#898989", "#959595")
DEEPSLATE = ramp("#343438", "#3D3D42", "#47474C", "#515156", "#5C5C61")


def smooth_noise(rng, cx=4, cy=4, w=16, h=16):
    """Tileable value noise in 0..1 with cell size cx*cy."""
    gw, gh = w // cx, h // cy
    g = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cx, y / cy
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] * (1 - tx) + g[y0][x1] * tx
            b = g[y1][x0] * (1 - tx) + g[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def stone_base(rng, deep=False):
    pal = DEEPSLATE if deep else STONE
    t = Tex()
    if deep:
        n1, n2 = smooth_noise(rng, 8, 2), smooth_noise(rng, 4, 1)
    else:
        n1, n2 = smooth_noise(rng, 4, 4), smooth_noise(rng, 2, 2)
    for y in range(16):
        for x in range(16):
            v = n1[y][x] * 0.55 + n2[y][x] * 0.3 + rng.random() * 0.15
            t.set(x, y, pal[max(0, min(4, int(v * 5.4 - 0.7)))])
    if deep:   # deepslate's dark horizontal cracks
        for _ in range(6):
            x, y = rng.randint(0, 13), rng.randint(0, 15)
            for i in range(rng.randint(2, 4)):
                t.set((x + i) % 16, y, pal[0])
    return t


def grow_blob(rng, cx, cy, size, taken, lo=1, hi=14):
    """Compact, roundish cluster: grow into the frontier cell nearest the
    (jittered) centre."""
    blob = {(cx, cy)}
    sx, sy = rng.uniform(0.8, 1.3), rng.uniform(0.8, 1.3)
    while len(blob) < size:
        frontier = set()
        for (x, y) in blob:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, y + dy)
                if n not in blob and n not in taken and lo <= n[0] <= hi and lo <= n[1] <= hi:
                    frontier.add(n)
        if not frontier:
            break
        blob.add(min(sorted(frontier), key=lambda p: math.hypot((p[0] - cx) * sx, (p[1] - cy) * sy)
                     + rng.random() * 1.4))
    return blob


def shade_lump(t, blob, pal):
    """Vanilla-ore style cluster. pal = [deep, dark, mid, light, hi]."""
    for (x, y) in blob:
        up, left = (x, y - 1) not in blob, (x - 1, y) not in blob
        down, right = (x, y + 1) not in blob, (x + 1, y) not in blob
        c = pal[2]
        if up or left:
            c = pal[3]
        if down or right:
            c = pal[1]
        if up and left:
            c = pal[4]
        if down and right:
            c = pal[0]
        t.set(x, y, c)


GEMS = {
    "S": [".4.", "432", ".1."],
    "M": [".4.", "432", "321", ".1."],
    "L": ["..4..", ".443.", "44332", ".3221", "..1.."],
}


def ore_dark_rim(t, pixels, amount=0.4):
    """Darken the host rock around ore pixels (vanilla ores do this)."""
    for (x, y) in sorted(pixels):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = (x + dx, y + dy)
                if n not in pixels and t.inside(*n):
                    if dx >= 0 and dy >= 0:
                        t.set(n[0], n[1], darken(t.get(*n), amount))


ORES = {
    # name: (palette, style, clusters, size range)
    "tin": (ramp("#4C545E", "#7C8692", "#AEB8C4", "#D2DAE2", "#F2F6FA"), "lump", 5, (7, 10)),
    "bauxite": (ramp("#4A1A0C", "#8A3818", "#BE5A26", "#DE8040", "#F6AA6A"), "lump", 5, (7, 10)),
    "titanium": (ramp("#46506A", "#8090AC", "#B0C0D8", "#D8E4F2", "#FFFFFF"), "gem", 5, None),
}


def ore_block(name, deep):
    pal, style, count, size = ORES[name]
    t = stone_base(rng_for(("deepslate_" if deep else "") + name + "_ore/stone"), deep)
    rng = rng_for(name + "_ore/clusters")    # same cluster layout for both variants
    if style == "lump":
        centres = []
        for _ in range(400):
            if len(centres) == count:
                break
            cx, cy = rng.randint(2, 13), rng.randint(2, 13)
            if all(math.hypot(cx - a, cy - b) >= 5.0 for a, b in centres):
                centres.append((cx, cy))
        taken = set()
        blobs = []
        for (cx, cy) in centres:
            b = grow_blob(rng, cx, cy, rng.randint(*size), taken, 1, 14)
            taken |= b
            blobs.append(b)
        ore_dark_rim(t, taken, 0.3 if deep else 0.35)
        for b in blobs:
            shade_lump(t, b, pal)
    else:
        spots = [("L", 5, 5), ("M", 11, 3), ("M", 3, 10), ("S", 11, 10), ("S", 8, 12), ("S", 1, 2)]
        pix = {}
        for kind, x0, y0 in spots:
            for dy, row in enumerate(GEMS[kind]):
                for dx, ch in enumerate(row):
                    if ch != ".":
                        pix[(x0 + dx, y0 + dy)] = int(ch)
        ore_dark_rim(t, set(pix), 0.35)
        for (x, y), k in pix.items():
            t.set(x, y, pal[k])
    return t


# ---- storage blocks (vanilla metal-block look) -------------------------------------------


def storage_block(pal, rng, glow=False):
    """pal = [deep, dark, mid, light, hi]."""
    t = Tex(fill=pal[2])
    dither(t, rng, 2, 2, 13, 13, pal[2], mix(pal[2], pal[1], 0.35), mix(pal[2], pal[3], 0.35), 0.12)
    # sheen streaks
    for y in range(2, 14):
        for x in range(2, 14):
            if x + y in (9, 11) or x + y in (19,):
                t.set(x, y, mix(pal[2], pal[3], 0.6))
    # plate seams: two horizontal plates
    t.hline(2, 13, 7, pal[1]); t.hline(2, 13, 8, pal[3])
    # outer & inner bevel
    t.hline(0, 15, 0, pal[3]); t.vline(0, 0, 15, pal[3])
    t.hline(0, 15, 15, pal[0]); t.vline(15, 0, 15, pal[0])
    t.hline(1, 14, 1, pal[4]); t.vline(1, 1, 14, pal[4])
    t.hline(1, 14, 14, pal[1]); t.vline(14, 1, 14, pal[1])
    t.set(15, 0, pal[2]); t.set(0, 15, pal[2]); t.set(14, 1, pal[3]); t.set(1, 14, pal[3])
    # corner bolts
    for (x, y) in [(3, 3), (12, 3), (3, 11), (12, 11)]:
        t.set(x, y, pal[4]); t.set(x, y + 1, pal[1])
    if glow:
        for (x, y) in [(4, 5), (5, 5), (6, 4), (7, 4), (8, 5), (9, 5), (10, 4), (11, 4),
                       (4, 11), (5, 10), (6, 10), (7, 11), (8, 11), (9, 10), (10, 10), (11, 11)]:
            t.set(x, y, CYAN_GLOW[2])
        for (x, y) in [(3, 5), (12, 4), (3, 11), (12, 11)]:
            t.set(x, y, CYAN_GLOW[1])
    return t


# =============================================================================
# 4. Items
# =============================================================================


def item():
    return Tex()


def raw_lump(pal, rng):
    """Vanilla raw-ore style clump (union of lit ellipsoids + mineral flecks)."""
    t = item()
    blobs = [(6.5, 9.0, 4.2, 3.8), (10.2, 6.8, 3.4, 3.2, 0.3), (5.6, 5.4, 2.6, 2.4, 0.6),
             (10.4, 10.8, 2.8, 2.4)]
    field = render_blobs(t, blobs, pal[1:], bias=0.05)
    for (x, y) in sorted(field):
        r = rng.random()
        if r < 0.05:
            t.set(x, y, pal[4])
        elif r < 0.10:
            t.set(x, y, pal[1])
    t.outline(0.5)
    return t


def dust(pal, rng):
    """Heap of powder."""
    t = item()
    blobs = [(8.0, 12.0, 6.6, 2.6), (8.0, 10.3, 4.8, 2.6, 0.5), (8.0, 8.4, 3.0, 2.3, 1.2),
             (8.3, 7.0, 1.6, 1.4, 1.8)]
    field = render_blobs(t, blobs, pal[1:], bias=0.05)
    for (x, y) in sorted(field):
        r = rng.random()
        if r < 0.13:
            t.set(x, y, pal[4] if y < 11 else pal[3])
        elif r < 0.22:
            t.set(x, y, pal[1])
    for (x, y) in [(2, 14), (13, 13), (3, 11)]:   # loose grains
        t.set(x, y, pal[2])
    t.outline(0.5)
    return t


INGOT = [
    "....hhhhhhhhh.",
    "...hTTTTTTTTe.",
    "..hTTTsTTTTee.",
    ".LLLLLLLLLeee.",
    ".FFFFFFFFeee..",
    ".FFFFFFFFee...",
    ".DDDDDDDDe....",
]


def ingot(pal, veins=None):
    """Cabinet-projection ingot bar. pal = [deep, dark, mid, light, hi]."""
    t = item()
    p = {"h": pal[4], "T": pal[3], "s": pal[4], "L": mix(pal[3], pal[4], 0.5), "F": pal[2],
         "D": pal[1], "e": mix(pal[1], pal[2], 0.4)}
    t.sprite(INGOT, p, 1, 4)
    for (x, y) in veins or []:
        t.set(x, y, CYAN_GLOW[2] if y < 8 else CYAN_GLOW[1])
    t.outline(0.45)
    return t


def plate(pal):
    """Flat square sheet with bevelled edges and a sheen."""
    t = item()
    x0, y0, x1, y1 = 2, 2, 13, 13
    t.rect(x0, y0, x1, y1, pal[2])
    t.hline(x0, x1, y0, pal[3]); t.vline(x0, y0, y1, pal[3])
    t.hline(x0, x1, y1, pal[1]); t.vline(x1, y0, y1, pal[1])
    t.set(x0, y0, pal[4]); t.set(x1, y0, pal[2]); t.set(x0, y1, pal[2])
    for i in range(5):   # two diagonal sheen streaks
        t.set(x0 + 2 + i, y1 - 3 - i, mix(pal[2], pal[3], 0.7))
        t.set(x0 + 5 + i, y1 - 3 - i, mix(pal[2], pal[4], 0.5))
    t.outline(0.5)
    return t


def gear_mask(cx, cy, r_body, r_tooth, teeth, half_angle, r_hole=0.0):
    m = set()
    step = 2 * math.pi / teeth
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r_tooth or d < r_hole:
                continue
            a = math.atan2(dy, dx) % step
            a = min(a, step - a)
            if d <= r_body or a < half_angle:
                m.add((x, y))
    return m


def gear(pal):
    """8-tooth gear with an axle hole. pal = [deep, dark, mid, light, hi]."""
    t = item()
    cx = cy = 8.0
    for (x, y) in gear_mask(cx, cy, 4.6, 6.6, 8, 0.27, 1.6):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy)
        lit = -(dx + dy) / (d * 1.414)
        if d > 4.0:
            c = pal[4] if lit > 0.6 else pal[3] if lit > 0.0 else pal[2] if lit > -0.6 else pal[1]
        elif d < 2.7:
            c = pal[1] if lit > 0.2 else (pal[3] if lit < -0.2 else pal[2])   # sunk hub
        else:
            c = pal[3] if lit > 0.35 else pal[2]
        t.set(x, y, c)
    t.outline(0.5)
    return t


def rod(pal):
    """Thin diagonal rod. pal = [deep, dark, mid, light, hi]."""
    t = item()
    render_rod(t, (2.5, 13.5), (13.5, 2.5), 1.3, [pal[1], pal[2], pal[3], pal[4]])
    t.set(13, 2, pal[4])
    t.outline(0.5)
    return t


def wire_spool(pal):
    """Coil of wire wound on a small wooden spool."""
    t = item()
    W = ramp("#5E4636", "#7E6048", "#A07E60")
    for x0 in (2, 12):
        t.rect(x0, 3, x0 + 1, 13, W[1])
        t.vline(x0, 3, 13, W[2]); t.set(x0 + 1, 13, W[0])
    for y in range(4, 13):
        for x in range(4, 12):
            k = (x + (y % 2)) % 2
            c = pal[3] if k == 0 else pal[2]
            if y == 4:
                c = pal[4] if k == 0 else pal[3]
            if y >= 11:
                c = pal[1] if k == 0 else pal[0]
            t.set(x, y, c)
    t.set(11, 3, pal[3]); t.set(12, 2, pal[3]); t.set(13, 1, pal[3]); t.set(14, 1, pal[2])  # loose end
    t.outline(0.5)
    return t


MOLD_STEEL = ramp("#2A2E36", "#434954", "#5C6470", "#7C8592", "#A4ADBA")


def mold(kind):
    """Steel die blank with the product's shape cut into it."""
    S = MOLD_STEEL
    t = item()
    t.rect(1, 1, 14, 14, S[2])
    t.hline(1, 14, 1, S[3]); t.vline(1, 1, 14, S[3])
    t.hline(1, 14, 14, S[0]); t.vline(14, 1, 14, S[0])
    t.set(1, 1, S[4])
    t.hline(2, 13, 2, mix(S[2], S[3], 0.5))
    if kind == "plate":
        cut = {(x, y) for x in range(4, 12) for y in range(4, 12)}
    elif kind == "gear":
        cut = gear_mask(8.0, 8.0, 2.9, 4.6, 8, 0.33)
    elif kind == "rod":
        cut = set()
        for y in range(16):
            for x in range(16):
                d, _, s = seg_dist(x + 0.5, y + 0.5, 4.0, 12.0, 12.0, 4.0)
                if d <= 1.0 and 0 <= s <= 1:
                    cut.add((x, y))
    else:  # wire: a coil loop
        cut = ring_mask(8.0, 8.0, 2.0, 3.6)
    hole = "#15171B"
    for (x, y) in cut:
        t.set(x, y, hole)
    for (x, y) in cut:
        if (x, y - 1) not in cut or (x - 1, y) not in cut:
            t.set(x, y, "#0B0C0F")           # shadowed upper wall
        if (x, y + 1) not in cut or (x + 1, y) not in cut:
            t.set(x, y, S[1])                # lit lower wall
    t.outline(0.5)
    return t


PCB = {
    1: ramp("#0C2A12", "#15441E", "#1F6A2C", "#2E8C3C", "#4CB05A"),
    2: ramp("#0A1A36", "#142C58", "#1E4282", "#2C5CA6", "#4A7ECA"),
    3: ramp("#3A1006", "#6A200C", "#A03414", "#C4501E", "#E47632"),
    4: ramp("#1C0A34", "#321458", "#4C2082", "#6834A6", "#8A52CA"),
    5: ramp("#050507", "#0C0C10", "#16161C", "#24242C", "#3A3A46"),
}
GOLD_TRACE = ("#E8C040", "#9A7418")


def circuit(n):
    """Circuit board for tier n: PCB colour per tier, more parts per tier."""
    P = PCB[n]
    tr = (CYAN_GLOW[2], CYAN_GLOW[0]) if n == 5 else GOLD_TRACE
    t = item()
    x0, y0, x1, y1 = 1, 3, 14, 12
    t.rect(x0, y0, x1, y1, P[2])
    t.hline(x0, x1, y0, P[3]); t.vline(x0, y0, y1, P[3])
    t.hline(x0, x1, y1, P[1]); t.vline(x1, y0, y1, P[1])
    t.set(x0, y0, P[4])
    for x in range(2, 14, 2):   # edge contacts
        t.set(x, 13, "#F2CC4A"); t.set(x, 12, GOLD_TRACE[1])

    def trace(pts):
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            for x in range(min(ax, bx), max(ax, bx) + 1):
                for y in range(min(ay, by), max(ay, by) + 1):
                    t.set(x, y, tr[0])

    def chip(cx0, cy0, cx1, cy1, pins=False):
        if pins:
            for x in range(cx0, cx1 + 1):
                t.set(x, cy0 - 1, "#C8C8D0"); t.set(x, cy1 + 1, "#8A8A94")
        t.rect(cx0, cy0, cx1, cy1, "#1C1C22")
        t.hline(cx0, cx1, cy0, "#44444E")
        t.set(cx0, cy0, "#60606C")

    # tier 1: one chip, a resistor, three traces
    trace([(2, 10), (5, 10), (5, 8)])
    trace([(8, 11), (8, 9), (11, 9)])
    trace([(12, 11), (12, 10)])
    chip(3, 5, 5, 6)
    t.set(8, 5, "#D8C8A0"); t.set(9, 5, "#B03020"); t.set(10, 5, "#D8C8A0")
    if n >= 2:
        trace([(2, 8), (3, 8)])
        trace([(6, 6), (7, 6), (7, 8)])
        chip(10, 7, 12, 8)
    if n >= 3:
        for (x, y) in [(12, 4), (2, 4)]:   # capacitors
            t.set(x, y, "#C8D0DC"); t.set(x, y + 1, "#5A7AA8")
        trace([(9, 10), (10, 10)])
        t.set(6, 11, "#E8E8F0"); t.set(4, 11, "#FF5040")   # SMD parts
    if n >= 4:
        chip(6, 5, 9, 8, pins=True)   # big processor
        t.set(7, 6, "#6A6A78")
    if n >= 5:
        t.rect(7, 6, 8, 7, CYAN_GLOW[2]); t.set(7, 6, CYAN_GLOW[3])
        t.set(6, 5, VIOLET_GLOW[1]); t.set(9, 8, VIOLET_GLOW[1])
    t.outline(0.5)
    return t


def lattice_frame(pal, thick=2, joint=None, bolts=False):
    """Square machine frame with an X brace. pal = [deep, dark, mid, light, hi]."""
    t = item()
    x0, y0, x1, y1 = 1, 1, 14, 14
    mask = set()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            border = x < x0 + thick or x > x1 - thick or y < y0 + thick or y > y1 - thick
            if thick >= 3:
                diag = 0 <= x - y <= 1 or 0 <= x + y - 15 <= 1
            else:
                diag = x == y or x + y == 15
            if border or diag:
                mask.add((x, y))
    for (x, y) in mask:
        t.set(x, y, pal[2])
    t.edge_shade(mask, pal[3], pal[1])
    t.set(x0, y0, pal[4])
    if bolts:
        for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            t.set(x, y, pal[4])
    if joint:
        for (x, y) in [(1, 1), (2, 1), (1, 2), (13, 1), (14, 1), (14, 2), (1, 13), (1, 14), (2, 14),
                       (13, 14), (14, 14), (14, 13)]:
            t.set(x, y, joint[1])
        for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            t.set(x, y, joint[2])
        t.rect(7, 7, 8, 8, joint[1]); t.set(7, 7, joint[3]); t.set(8, 8, joint[2])
    t.outline(0.5)
    return t


MOTOR = [
    "................",
    "................",
    "...ossssssso....",
    "..oLlLlLlLlLo...",
    "..hLlLlLlLlLhd..",
    "..hmlmlmlmlmhdss",
    "..hmlmlmlmlmhduu",
    "..hmdmdmdmdmhd..",
    "..odddddddddo...",
    "...ooooooooo....",
    "....kk....kk....",
    "...kkkk..kkkk...",
    "................",
]


def motor():
    """Small electric motor: copper-wound body, cooling ribs, steel shaft."""
    Cu = MAT["copper"]
    S = MAT["steel"]
    fin = ramp("#5E606A", "#8A8E98", "#B0B4BC", "#D4D8DE")
    pal = {"o": Cu[1], "h": Cu[3], "d": Cu[1], "s": S[4], "u": S[2], "k": S[1],
           "L": fin[3], "l": fin[2], "m": fin[1]}
    t = item()
    t.sprite(MOTOR, pal, 0, 1)
    t.outline(0.5)
    return t


def heating_coil():
    """Flat copper spiral element (stove-coil style) with a lead."""
    Cu = MAT["copper"]
    t = item()
    cx, cy = 7.5, 7.5
    pitch = 2.05
    n = 3000
    for i in range(n):
        th = 3.2 * 2 * math.pi * i / n
        r = 0.4 + pitch * th / (2 * math.pi)
        x, y = cx + r * math.cos(th), cy + r * math.sin(th)
        lit = -(math.cos(th) + math.sin(th))
        c = Cu[4] if lit > 1.0 else Cu[3] if lit > 0.2 else Cu[2] if lit > -0.8 else Cu[1]
        t.set(int(x), int(y), c)
    for (x, y) in [(13, 9), (13, 10), (13, 11), (13, 12), (14, 13)]:
        t.set(x, y, Cu[2])
    t.outline(0.55)
    return t


CARD = ramp("#1A2028", "#28313C", "#36424F", "#4E5E70", "#70849A")
UP_ARROW = [
    "...lG...",
    "..lGGd..",
    ".lGGGGd.",
    "lGGGGGGd",
    "..lGGd..",
    "..lGGd..",
    "..lGGd..",
    "..dddd..",
]


def upgrade_card(kind):
    """Machine upgrade module: dark card, symbol window, gold contacts."""
    B = CARD
    t = item()
    t.rect(2, 1, 13, 14, B[2])
    t.hline(2, 13, 1, B[3]); t.vline(2, 1, 14, B[3])
    t.hline(2, 13, 14, B[1]); t.vline(13, 1, 14, B[1])
    t.set(2, 1, B[4])
    t.set(13, 1, CLEAR); t.set(2, 14, B[2])   # clipped corner
    recess_pal = Tier(0, "card", ["#000000"] * 5, [B[4], B[3], B[2], B[1], B[0]], "#FFFFFF")
    recess(t, 3, 2, 12, 11, recess_pal, fill="#10151A")
    for x in range(4, 12, 2):
        t.set(x, 13, "#F2CC4A"); t.set(x, 12, "#9A7418")
    if kind == "speed":
        t.sprite(UP_ARROW, {"l": "#B8FFC0", "G": "#44E05C", "d": "#1E8A2E"}, 4, 3)
    else:
        t.sprite(BOLT, {"Y": "#FFD83A", "o": "#C88A10"}, 5, 3)
        t.set(9, 3, "#FFF6B0")
    t.outline(0.5)
    return t


WOOD = ramp("#3A2610", "#5E4020", "#86602E", "#A87C40", "#C49A58")


def forge_hammer():
    """Smithing hammer in vanilla-tool pose (handle bottom-left, head top-right)."""
    t = item()
    render_rod(t, (1.8, 14.2), (10.0, 6.0), 1.05, [WOOD[1], WOOD[2], WOOD[3]])
    H = MAT["steel"]
    # heavy head perpendicular to the handle
    for y in range(16):
        for x in range(16):
            d, perp, s = seg_dist(x + 0.5, y + 0.5, 6.6, 2.6, 13.6, 9.6)
            if 0 <= s <= 1 and abs(perp) <= 2.2:
                k = (perp + 2.2) / 4.4   # 0 = up-right side
                c = H[4] if k < 0.25 else H[3] if k < 0.5 else H[2] if k < 0.75 else H[1]
                if s < 0.12:
                    c = H[4]   # striking face
                if s > 0.9:
                    c = H[1]
                t.set(x, y, c)
    t.set(10, 6, H[0])   # socket shadow
    t.outline(0.5)
    return t


def wrench():
    """Tech-mod open-end wrench with a red grip."""
    t = item()
    S = STEEL
    render_rod(t, (2.0, 14.0), (10.5, 5.5), 1.25, [S[3], S[2], S[1], S[0]])
    grip = ramp("#6A1010", "#A81C1C", "#D83A30", "#F07060")
    render_rod(t, (2.0, 14.0), (6.4, 9.6), 1.35, [grip[0], grip[1], grip[2], grip[3]])
    cx, cy = 11.6, 4.4
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx))
            open_mouth = -85 < ang < -5 and d > 1.2
            if d <= 3.7 and not open_mouth and d > 1.3:
                lit = -(dx + dy) / (d + 0.01)
                t.set(x, y, S[0] if lit > 0.5 else S[1] if lit > -0.2 else S[2])
    t.outline(0.5)
    return t


def silicon_chunk():
    S = MAT["silicon"]
    pal = {str(i): S[i] for i in range(5)}
    t = item()
    t.sprite([
        ".....43.....",
        "....4432....",
        "...443321...",
        "..4433221...",
        ".44332211...",
        ".4333221114.",
        "33322211143.",
        "322221114332",
        ".2221114432.",
        "..211143321.",
        "...1143221..",
        "....1221....",
    ], pal, 2, 2)
    t.outline(0.45)
    return t


def silicon_wafer():
    t = item()
    cx, cy = 8.0, 7.6
    rainbow = ["#B89CE0", "#8CB8F0", "#8CE0C8", "#E0D890", "#F0AE98"]
    for (x, y) in sorted(disc_mask(cx, cy, 6.4)):
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        diag = (x + 0.5 - cx) + (y + 0.5 - cy)
        base = mix("#A4A8B2", "#6C7078", (diag + 9) / 18)
        c = mix(base, rainbow[int((x - y + 16) / 4.2) % 5], 0.38)
        if d > 5.5:
            c = "#C8CCD4" if diag < -1 else ("#5A5E68" if diag > 1 else "#8C9098")
        t.set(x, y, c)
    t.hline(6, 9, 14, CLEAR)   # flat notch
    t.hline(6, 9, 13, "#5A5E68")
    t.set(5, 4, "#F4F6FA"); t.set(6, 4, "#DCE0E8"); t.set(5, 5, "#DCE0E8")
    t.outline(0.5)
    return t


# ---- v2 items: bronze tools, drill, coke & fire clay, orbital / ender parts ---------------

BRONZE_M = MAT["bronze"]          # deep .. hi


STICK = ["#3A2410", "#5A3A18", "#7A5426"]


def stick(t, a=(2.2, 13.8), b=(10.0, 6.0)):
    render_rod(t, a, b, 0.95, STICK)


def shade_mask(t, mask, pal, lx=-1.0, ly=-1.0):
    """Fill a pixel set with a metal ramp lit from the top-left edge. pal = deep .. hi."""
    for (x, y) in mask:
        edge_lit = (x + int(lx), y) not in mask or (x, y + int(ly)) not in mask
        edge_dark = (x - int(lx), y) not in mask or (x, y - int(ly)) not in mask
        c = pal[2]
        if edge_lit:
            c = pal[4] if not edge_dark else pal[3]
        elif edge_dark:
            c = pal[1]
        t.set(x, y, c)


def mask_of(fn):
    return {(x, y) for y in range(16) for x in range(16) if fn(x + 0.5, y + 0.5)}


def bronze_tool(kind):
    t = item()
    P = BRONZE_M
    if kind == "sword":
        blade = mask_of(lambda x, y: seg_dist(x, y, 5.0, 11.0, 13.2, 2.8)[0] <= 1.2 - 0.4 * max(0.0, seg_dist(x, y, 5.0, 11.0, 13.2, 2.8)[2] - 0.8) * 5
                        and 0 <= seg_dist(x, y, 5.0, 11.0, 13.2, 2.8)[2] <= 1.06)
        shade_mask(t, blade, P)
        for i in range(7):     # fuller down the blade's middle
            t.set(6 + i, 9 - i, P[3])
        render_rod(t, (1.6, 14.4), (4.4, 11.6), 0.9, STICK)
        guard = mask_of(lambda x, y: seg_dist(x, y, 3.2, 9.8, 6.2, 12.8)[0] <= 0.75)
        shade_mask(t, guard, [BRONZE_M[0], BRONZE_M[1], BRONZE_M[1], BRONZE_M[2], BRONZE_M[3]])
        t.set(1, 14, P[3])
    elif kind == "pickaxe":
        stick(t, (2.2, 13.8), (10.2, 5.8))

        def arc(x, y):
            best = 9
            for i in range(41):
                s = i / 40
                bx = (1 - s) ** 2 * 2.5 + 2 * (1 - s) * s * 13.5 + s * s * 13.5
                by = (1 - s) ** 2 * 2.5 + 2 * (1 - s) * s * 2.5 + s * s * 13.5
                w = 1.25 - 0.55 * abs(s - 0.5) * 2
                best = min(best, math.hypot(x - bx, y - by) - w)
            return best <= 0
        shade_mask(t, mask_of(arc), P)
    elif kind == "axe":
        stick(t, (2.2, 13.8), (11.2, 4.8))
        head = mask_of(lambda x, y: (seg_dist(x, y, 9.0, 3.2, 12.6, 6.8)[0] <= 1.3) or
                       (math.hypot((x - 6.6) * 0.9, (y - 5.6) * 1.0) <= 3.0 and x + y < 13.8 and x - y > -3.5))
        shade_mask(t, head, P)
        for (x, y) in sorted(head):
            if x + y <= 9:   # honed cutting edge
                t.set(x, y, P[4])
    elif kind == "shovel":
        stick(t, (2.2, 13.8), (9.6, 6.4))
        head = mask_of(lambda x, y: ((x + y - 16) * 0.7071 / 2.6) ** 2 + ((x - y - 8.4) * 0.7071 / 3.6) ** 2 <= 1.0
                       or (seg_dist(x, y, 8.4, 7.6, 10.2, 5.8)[0] <= 0.8))
        shade_mask(t, head, P)
    elif kind == "hoe":
        stick(t, (2.2, 13.8), (11.4, 4.6))
        head = mask_of(lambda x, y: seg_dist(x, y, 6.0, 3.0, 12.6, 3.0)[0] <= 1.0 or
                       seg_dist(x, y, 6.0, 3.0, 6.0, 6.0)[0] <= 1.0)
        shade_mask(t, head, P)
    t.outline(0.5)
    return t


def electric_drill():
    """Cordless drill: steel bit pointing up-right, yellow body, black grip, green charge LED."""
    t = item()
    Y = ramp("#7A5A08", "#B88A10", "#E8B818", "#FFD84A", "#FFF0A0")
    K = MAT["rubber"]
    S = STEEL
    # bit
    render_rod(t, (9.8, 6.2), (14.4, 1.6), 0.8, [S[3], S[1], S[0]])
    # chuck
    chuck = mask_of(lambda x, y: seg_dist(x, y, 8.2, 7.8, 10.2, 5.8)[0] <= 1.5)
    shade_mask(t, chuck, [S[4], S[3], S[2], S[1], S[0]])
    # body
    body = mask_of(lambda x, y: seg_dist(x, y, 3.0, 13.0, 8.0, 8.0)[0] <= 2.6 and
                   seg_dist(x, y, 3.0, 13.0, 8.0, 8.0)[2] >= 0.35)
    shade_mask(t, body, Y)
    # grip + battery at the bottom-left
    grip = mask_of(lambda x, y: seg_dist(x, y, 1.8, 14.2, 4.6, 11.4)[0] <= 1.6)
    shade_mask(t, grip, K)
    t.set(3, 12, LED_ON[1])
    t.set(6, 9, "#FFFFFF"); t.set(5, 11, Y[1])
    t.outline(0.5)
    return t


COKE = ramp("#141418", "#24242A", "#3A3A42", "#585862", "#8C8C98")
FIRE_CLAY = ramp("#7A5E3A", "#A8844E", "#C8A468", "#DCBE84", "#EED6A4")


def coke_item(rng):
    t = raw_lump(COKE, rng)
    for (x, y) in [(6, 8), (9, 6), (5, 11), (11, 10), (8, 10), (4, 7)]:
        if t.opaque(x, y):
            t.set(x, y, COKE[0])      # pores
    return t


def fire_clay(rng):
    t = item()
    render_blobs(t, [(8.0, 9.0, 5.2, 4.2), (6.4, 7.0, 3.0, 2.6, 0.5), (10.4, 10.6, 2.8, 2.4)],
                 FIRE_CLAY[1:], bias=0.1, rng=rng, speckle=[FIRE_CLAY[1], "#A07840"], speck_p=0.06)
    t.outline(0.5)
    return t


BRICK = [
    "..............",
    "....hhhhhhhhh.",
    "...hTTTTTTTTe.",
    "..hTTTTTTTTee.",
    ".LLLLLLLLLeee.",
    ".FFFFFFFFFeee.",
    ".FFFFFFFFFee..",
    ".FFFFFFFFFe...",
    ".DDDDDDDDD....",
]


def fire_brick_item(rng):
    P = FIRE_BRICK
    t = item()
    t.sprite(BRICK, {"h": P[4], "T": P[3], "L": mix(P[3], P[4], 0.4), "F": P[2], "D": P[1],
                     "e": mix(P[1], P[2], 0.4)}, 1, 3)
    for (x, y) in [(4, 9), (8, 8), (6, 10), (10, 9), (7, 6)]:
        t.set(x, y, "#8A5A2A")
    t.outline(0.45)
    return t


def orbital_targeting_core():
    """Titanium ring with four clamps around a violet-to-cyan targeting lens."""
    t = item()
    Ti = MAT["titanium"]
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            lit = -(dx + dy) / (d + 0.01)
            if 4.6 <= d <= 6.6:
                t.set(x, y, Ti[4] if lit > 0.6 else Ti[3] if lit > 0 else Ti[2] if lit > -0.6 else Ti[1])
            elif d < 4.6:
                k = d / 4.6
                c = mix(CYAN_GLOW[3], CYAN_GLOW[2], min(1, k * 1.6)) if k < 0.6 else mix(CYAN_GLOW[1], VIOLET_GLOW[1], (k - 0.6) / 0.4)
                t.set(x, y, c)
    for (x0, y0, x1, y1) in ((7, 0, 8, 1), (7, 14, 8, 15), (0, 7, 1, 8), (14, 7, 15, 8)):   # clamps
        t.rect(x0, y0, x1, y1, Ti[2]); t.set(x0, y0, Ti[4])
    for i in (4, 5, 10, 11):     # crosshair ticks
        t.set(i, 8, VIOLET_GLOW[3] if i in (5, 10) else VIOLET_GLOW[2])
        t.set(8, i, VIOLET_GLOW[3] if i in (5, 10) else VIOLET_GLOW[2])
    t.rect(7, 7, 8, 8, "#FFFFFF")
    t.set(5, 5, "#FFFFFF"); t.set(6, 5, CYAN_GLOW[3])        # glint
    t.outline(0.5)
    return t


ENDER = ramp("#0A1E22", "#10424A", "#1A6E6E", "#34A898", "#8CE8D0")      # deep .. hi (pearl teal)
OBSIDIAN = ramp("#0C0814", "#170F24", "#221636", "#30204A", "#46306A")   # deep .. hi


def ender_dust(rng):
    return dust(ENDER, rng)


def recall_charm():
    """Gold-framed amulet on a short chain with an ender eye in the middle."""
    t = item()
    G = MAT["gold"]
    for (x, y) in [(5, 1), (6, 0), (7, 0), (8, 0), (9, 0), (10, 1), (4, 2), (11, 2)]:   # chain loop
        t.set(x, y, G[3] if x < 8 else G[2])
    cx, cy = 8.0, 9.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx * 1.0, dy * 0.95)
            lit = -(dx + dy) / (d + 0.01)
            if 3.6 <= d <= 5.4:
                t.set(x, y, G[4] if lit > 0.5 else G[3] if lit > -0.2 else G[1])
            elif d < 3.6:
                t.set(x, y, mix("#2E8C6A", "#0E3A2E", d / 3.6))
    t.rect(7, 8, 8, 10, "#0A0A10")                       # pupil
    t.set(7, 8, "#6ADCB0")
    t.set(6, 7, "#C8FFE8")                               # glint
    t.set(8, 3, G[3]); t.set(7, 3, G[4])                 # bail
    t.outline(0.5)
    return t


def ender_fluid_frame(phase):
    t = Tex()
    n = smooth_noise(rng_for("ender_fluid"), 4, 4)
    for y in range(16):
        for x in range(16):
            s = math.sin((x + phase) * 0.8 + n[(y + phase) % 16][x] * 5.0) + n[y][(x + 2 * phase) % 16]
            if s > 1.25:
                c = C("#9C7CE8", 200)
            elif s > 0.6:
                c = C("#4A8C9C", 185)
            elif s > -0.2:
                c = C("#1E5A66", 175)
            else:
                c = C("#2A1E5A", 180)
            t.set(x, y, c)
    return t


def ender_anchor_frame(rng):
    P = ramp("#6A4A9A", "#46306A", "#30204A", "#1E1430", "#120C1E")      # hi .. deep
    t = Tex(fill=P[2])
    dither(t, rng, 0, 0, 15, 15, P[2], P[3], P[1], 0.15)
    frame(t, None, pal=P)
    t.hline(2, 13, 7, P[3]); t.hline(2, 13, 8, P[1])
    for (x, y) in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        t.set(x, y, VIOLET_GLOW[2]); t.set(x + 1, y + 1, P[4])
    return t


RUNE = [["x.x", ".x.", "x.x"], ["xxx", "x..", "xxx"], [".x.", "xxx", ".x."], ["x..", "xxx", "..x"]]


def ender_beacon_side(rng):
    t = Tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, OBSIDIAN[1 + (rng.random() < 0.35) + (rng.random() < 0.1)])
    t.hline(0, 15, 0, OBSIDIAN[4]); t.hline(0, 15, 15, OBSIDIAN[0])
    for i, (x0, y0) in enumerate([(2, 3), (6, 7), (10, 3), (6, 11) if False else (11, 10)]):
        for dy, row in enumerate(RUNE[i]):
            for dx, ch in enumerate(row):
                if ch == "x":
                    t.set(x0 + dx, y0 + dy, VIOLET_GLOW[2] if (dx + dy) % 2 else VIOLET_GLOW[1])
    return t


def ender_beacon_top(rng, on):
    t = ender_beacon_side(rng_for("ender_beacon/top"))
    for y in range(16):
        for x in range(16):
            if 0 < x < 15 and 0 < y < 15:
                t.set(x, y, OBSIDIAN[1 + ((x * 7 + y * 3) % 5 == 0)])
    # crystal inset: a diamond of violet
    for y in range(16):
        for x in range(16):
            d = abs(x + 0.5 - 8) + abs(y + 0.5 - 8)
            if d <= 5:
                k = d / 5
                c = mix(VIOLET_GLOW[3] if on else VIOLET_GLOW[2], VIOLET_GLOW[0], k) if on else \
                    mix(VIOLET_GLOW[1], OBSIDIAN[2], k)
                t.set(x, y, c)
            elif d <= 6:
                t.set(x, y, OBSIDIAN[4])
    if on:
        t.set(7, 6, "#FFFFFF"); t.set(6, 7, "#FFFFFF")
    return t


# =============================================================================
# 5. Main
# =============================================================================

METALS = ["tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"]
# conduit stages keep the v1 tier colours: basic, reinforced, advanced, ultimate
CABLE_TIERS = {"copper_cable": 1, "aluminum_cable": 2, "titanium_cable": 3, "superconductor_cable": 5}
PIPE_TIERS = {"bronze_item_pipe": 1, "steel_item_pipe": 2, "aluminum_item_pipe": 3, "titanium_item_pipe": 5}
ANIMATED = {}      # rel path -> frametime


def build():
    """Ordered {relative path: Tex}."""
    out = {}

    def blk(name, fn):
        out["block/%s.png" % name] = fn(rng_for("block/" + name))

    def itm(name, fn):
        out["item/%s.png" % name] = fn(rng_for("item/" + name))

    def ani(name, frames, frametime):
        out["block/%s.png" % name] = anim(frames)
        ANIMATED["block/%s.png" % name] = frametime

    # ---- family casings (side / top / bottom / recess walls)
    # (every machine has its own top; aluminium machines draw their own sides too)
    for kind, T in FAMILY.items():
        if kind != "aluminum":
            blk("%s_machine_side" % kind, lambda r, T=T, kind=kind: family_side(T, r, kind))
            blk("%s_machine_bottom" % kind, lambda r, T=T: family_bottom(T, r))
        blk("%s_machine_inner" % kind, lambda r, T=T: inner_tex(mix(T.panel[4], VOID, 0.45), r))

    # ---- stone age
    blk("quern_side", quern_side)
    blk("quern_bed_top", lambda r: quern_top(r, False, False))
    blk("quern_bed_top_on", lambda r: quern_top(rng_for("block/quern_bed_top"), False, True))
    blk("quern_runner_top", lambda r: quern_top(r, True, False))
    blk("quern_bottom", lambda r: granite(r, ramp("#3E3C3A", "#4E4C4A", "#686664", "#83807C", "#9C9994")))
    blk("quern_handle", quern_handle)
    blk("brick_kiln_front", lambda r: kiln_front(r, False))
    blk("brick_kiln_front_on", lambda r: kiln_front(r, True))
    blk("brick_kiln_side", lambda r: kiln_bricks(r))
    blk("brick_kiln_top", lambda r: kiln_top(r, False))
    blk("brick_kiln_top_on", lambda r: kiln_top(r, True))
    blk("brick_kiln_bottom", lambda r: granite(r, COBBLE))
    blk("brick_kiln_inner", lambda r: inner_tex(SOOT[2], r))
    blk("brick_kiln_inner_on", lambda r: inner_tex("#4A1E0C", r))
    blk("brick_kiln_fuel", lambda r: logs(r, False))
    blk("brick_kiln_fuel_on", lambda r: logs(rng_for("block/brick_kiln_fuel"), True))

    # ---- bronze age
    blk("coke_oven_bricks", coke_oven_bricks)
    blk("fire_bricks", fire_bricks)
    blk("burner_crusher_front", lambda r: burner_crusher_front(r, False))
    blk("burner_crusher_front_on", lambda r: burner_crusher_front(rng_for("block/burner_crusher_front"), True))
    blk("burner_crusher_top", lambda r: hopper_top(BRONZE_T, r, "burner_crusher", ORE_BITS))
    blk("burner_crusher_roller", knurl)
    blk("burner_crusher_roller_end", lambda r: roller_end(r, False))
    blk("burner_crusher_roller_end_on", lambda r: roller_end(r, True))
    blk("burner_press_front", lambda r: burner_press_front(r, False))
    blk("burner_press_front_on", lambda r: burner_press_front(rng_for("block/burner_press_front"), True))
    blk("burner_press_ram", lambda r: rod_tex(BRONZE_T.frame))
    blk("burner_press_head", lambda r: press_head(IRON))
    blk("burner_press_die", lambda r: flat(IRON[3], r, (IRON[4], IRON[2])))
    blk("coke_oven_front", lambda r: coke_oven_front(r, False))
    blk("coke_oven_front_on", lambda r: coke_oven_front(r, True))
    blk("coke_oven_inner", lambda r: inner_tex(IRON[3], r))
    blk("blast_furnace_front", lambda r: blast_furnace_front(r, False))
    blk("blast_furnace_front_on", lambda r: blast_furnace_front(r, True))
    blk("blast_furnace_inner", lambda r: inner_tex("#5A4428", r))

    # ---- electric age
    blk("electric_furnace_front", lambda r: electric_furnace_front(r, False))
    blk("electric_furnace_front_on", lambda r: electric_furnace_front(rng_for("block/electric_furnace_front"), True))
    blk("electric_furnace_coil", lambda r: coil_tex(False))
    blk("electric_furnace_coil_on", lambda r: coil_tex(True))
    blk("crusher_front", lambda r: crusher_front(r, False))
    blk("crusher_front_on", lambda r: crusher_front(rng_for("block/crusher_front"), True))
    blk("crusher_top", lambda r: hopper_top(STEEL_T, r, "crusher", ORE_BITS))
    blk("crusher_jaw", jaw_tex)
    blk("metal_press_front", lambda r: metal_press_front(r, False))
    blk("metal_press_front_on", lambda r: metal_press_front(rng_for("block/metal_press_front"), True))
    blk("metal_press_rod", lambda r: rod_tex(ramp("#FFFFFF", "#DCE2EA", "#9AA4B2", "#5A6270", "#343A44")))
    blk("metal_press_head", lambda r: press_head(STEEL_T.frame, stripes=True))
    blk("metal_press_die", lambda r: flat(STEEL_T.frame[3], r, (STEEL_T.frame[4], STEEL_T.frame[2])))
    blk("metal_press_plate", lambda r: flat(MAT["iron"][3], r, (MAT["iron"][2], MAT["iron"][4])))
    blk("electric_furnace_top", lambda r: furnace_top(r, False))
    blk("electric_furnace_top_on", lambda r: furnace_top(rng_for("block/electric_furnace_top"), True))
    blk("metal_press_top", press_top)
    blk("alloy_smelter_top", funnel_top)
    blk("assembler_top", lambda r: glass_top(r, False))
    blk("assembler_top_on", lambda r: glass_top(rng_for("block/assembler_top"), True))
    blk("burner_press_top", lambda r: hopper_top(BRONZE_T, r, "burner_press", [SOOT[0], SOOT[1], "#2A2A2E"]))
    blk("alloy_smelter_front", lambda r: alloy_smelter_front(r, False))
    blk("alloy_smelter_front_on", lambda r: alloy_smelter_front(rng_for("block/alloy_smelter_front"), True))
    blk("alloy_smelter_crucible", crucible_side)
    blk("alloy_smelter_crucible_top", lambda r: crucible_top(False, MAT["copper"]))
    blk("alloy_smelter_crucible_top_on", lambda r: crucible_top(True, MAT["copper"]))
    blk("assembler_front", lambda r: assembler_front(r, False))
    blk("assembler_front_on", lambda r: assembler_front(rng_for("block/assembler_front"), True))
    blk("assembler_arm", lambda r: flat("#E8861C", r, ("#B8600E", "#FFB040")))
    blk("assembler_joint", lambda r: flat(IRON[2], r, (IRON[3], IRON[1])))
    blk("assembler_work", lambda r: flat(PCB[1][2], r, (PCB[1][1], "#E8C040")))
    blk("combustion_generator_front", lambda r: combustion_front(r, False))
    blk("combustion_generator_front_on", lambda r: combustion_front(rng_for("block/combustion_generator_front"), True))
    blk("combustion_generator_top", lambda r: hopper_top(STEEL_T, r, "combustion_generator", [SOOT[0], SOOT[1], "#2A2A2E"]))
    blk("combustion_generator_slat", lambda r: press_head(STEEL_T.frame))
    blk("auto_farmer_front", lambda r: auto_farmer_front(r, False))
    blk("auto_farmer_front_on", lambda r: auto_farmer_front(rng_for("block/auto_farmer_front"), True))
    blk("auto_farmer_top", lambda r: auto_farmer_top(r, False))
    blk("auto_farmer_top_on", lambda r: auto_farmer_top(rng_for("block/auto_farmer_top"), True))
    blk("auto_farmer_side", auto_farmer_side)
    blk("auto_farmer_tine", lambda r: rod_tex(STEEL_T.frame))
    blk("solar_panel_top", solar_top)
    blk("solar_panel_side", solar_side)
    for cell, n in ENERGY_CELLS.items():
        T = TIERS[n]
        blk(cell + "_front", lambda r, T=T, cell=cell: cell_front(T, r, False, cell))
        blk(cell + "_front_on", lambda r, T=T, cell=cell: cell_front(T, rng_for("block/%s_front" % cell), True, cell))
        blk(cell + "_side", lambda r, T=T, cell=cell: cell_side(T, r, False, cell))
        blk(cell + "_top", lambda r, T=T: cell_top(T, r))
        blk(cell + "_bottom", lambda r, T=T: casing_bottom(T, r))
        blk(cell + "_inner", lambda r, T=T: inner_tex(mix(T.frame[4], VOID, 0.3), r))

    # ---- automation age
    blk("miner_side", lambda r: miner_side_tex(r, False))
    ani("miner_side_on", [miner_side_tex(rng_for("block/miner_side"), True, p / 4.0) for p in range(4)], 4)
    blk("miner_top", lambda r: miner_top(ALU_T, r))
    blk("miner_bottom", lambda r: miner_bottom(ALU_T, r))
    blk("geothermal_generator_front", lambda r: geo_front(r, False))
    ani("geothermal_generator_front_on",
        [geo_front(rng_for("block/geothermal_generator_front"), True, p) for p in range(8)], 6)
    blk("geothermal_generator_side", lambda r: geo_side(r, False))
    ani("geothermal_generator_side_on",
        [geo_side(rng_for("block/geothermal_generator_side"), True, p) for p in range(8)], 6)
    blk("geothermal_generator_top", lambda r: casing_top(ALU_T, r))
    blk("geothermal_generator_bottom", basalt)
    blk("geothermal_generator_bar", lambda r: rod_tex(IRON))

    # ---- automation / industrial / orbital processing machines
    blk("ore_washer_front", lambda r: washer_front(r, False))
    ani("ore_washer_front_on", [washer_front(rng_for("block/ore_washer_front"), True, p) for p in range(8)], 2)
    blk("ore_washer_top", washer_top)
    blk("ore_washer_side", washer_side)
    blk("ore_washer_bottom", lambda r: casing_bottom(ALU_T, r))
    blk("ore_washer_spraybar", lambda r: flat(WATER_PIPE[1], r, (WATER_PIPE[2], WATER_PIPE[0])))
    blk("ore_washer_nozzle", lambda r: flat(IRON[1], r, (IRON[2], IRON[0])))
    blk("induction_smelter_front", lambda r: induction_front(r, False))
    blk("induction_smelter_front_on", lambda r: induction_front(rng_for("block/induction_smelter_front"), True))
    blk("induction_smelter_top", lambda r: induction_top(r, False))
    blk("induction_smelter_top_on", lambda r: induction_top(rng_for("block/induction_smelter_top"), True))
    blk("induction_smelter_fin", fin_tex)
    blk("induction_smelter_coil", lambda r: induction_coil(False))
    blk("induction_smelter_coil_on", lambda r: induction_coil(True))
    blk("induction_smelter_crucible", lambda r: induction_crucible(False))
    blk("induction_smelter_crucible_on", lambda r: induction_crucible(True))
    blk("induction_smelter_melt", lambda r: crucible_top(False, MAT["titanium"]))
    blk("induction_smelter_melt_on", lambda r: crucible_top(True, ramp("#FFB43C", "#FFD27A", "#FFE8A8", "#FFF6D8", "#FFFFFF")))
    blk("hydraulic_press_front", lambda r: hpress_front(r, False))
    blk("hydraulic_press_front_on", lambda r: hpress_front(rng_for("block/hydraulic_press_front"), True))
    blk("hydraulic_press_top", hpress_top)
    blk("hydraulic_press_cylinder", hpress_cyl)
    blk("hydraulic_press_rod", lambda r: rod_tex(ramp("#FFFFFF", "#DCE2EA", "#9AA4B2", "#5A6270", "#343A44")))
    blk("hydraulic_press_ram", lambda r: hazard_ram())
    blk("hydraulic_press_die", lambda r: flat(MAT["titanium"][1], r, (MAT["titanium"][0], MAT["titanium"][2])))
    blk("hydraulic_press_plate", lambda r: flat(MAT["titanium"][3], r, (MAT["titanium"][2], MAT["titanium"][4])))
    blk("precision_assembler_front", lambda r: passembler_front(r, False))
    blk("precision_assembler_front_on", lambda r: passembler_front(rng_for("block/precision_assembler_front"), True))
    blk("precision_assembler_top", passembler_top)
    blk("precision_assembler_side", passembler_side)
    blk("precision_assembler_table", lambda r: flat(ORBITAL_T.panel[1], r, (ORBITAL_T.panel[3], ORBITAL_T.panel[0])))
    blk("precision_assembler_chip", lambda r: flat(PCB[1][3], r, (PCB[1][2], "#E8C040")))
    blk("precision_assembler_arm", lambda r: flat(ORBITAL_T.panel[1], r, (ORBITAL_T.panel[3], "#FFFFFF")))
    blk("precision_assembler_joint", lambda r: flat(GOLD_FOIL[2], r, (GOLD_FOIL[1], GOLD_FOIL[3])))
    blk("precision_assembler_tip", lambda r: flat("#2A2A30", r, ("#1A1A1E", "#5A1A1A")))
    blk("precision_assembler_tip_on", lambda r: flat("#FF2A1A", r, ("#C81010", "#FFB0A0")))
    blk("precision_assembler_laser", lambda r: flat("#FF3A2A", r, ("#FF6A5A", "#FFC0B0")))
    ani("plasma_forge_front_on", [plasma_front(rng_for("block/plasma_forge_front"), True, p) for p in range(8)], 2)
    blk("plasma_forge_front", lambda r: plasma_front(r, False))
    blk("plasma_forge_top", lambda r: plasma_top(r, False))
    blk("plasma_forge_top_on", lambda r: plasma_top(rng_for("block/plasma_forge_top"), True))
    blk("plasma_forge_pole", lambda r: pole_tex(False))
    blk("plasma_forge_pole_on", lambda r: pole_tex(True))
    blk("plasma_forge_electrode", lambda r: electrode_tex(False))
    blk("plasma_forge_electrode_on", lambda r: electrode_tex(True))

    # ---- crates
    blk("wooden_crate_side", lambda r: crate_face(r, False, False))
    blk("wooden_crate_top", lambda r: crate_face(r, True, False))
    blk("bronze_crate_side", lambda r: crate_face(r, False, True))
    blk("bronze_crate_top", lambda r: crate_face(r, True, True))

    # ---- conduits: one colour per stage, like v1
    for name, n in CABLE_TIERS.items():
        blk(name, lambda r, n=n: power_cable(TIERS[n], r))
    for name, n in PIPE_TIERS.items():
        blk(name, lambda r, n=n: item_pipe(TIERS[n], r))
    blk("item_pipe_extract", item_pipe_extract)

    # ---- ender anchor / beacon
    ani("ender_anchor_fluid", [ender_fluid_frame(p) for p in range(8)], 4)
    blk("ender_anchor_frame", ender_anchor_frame)
    blk("ender_beacon_side", ender_beacon_side)
    blk("ender_beacon_top", lambda r: ender_beacon_top(r, False))
    blk("ender_beacon_top_on", lambda r: ender_beacon_top(r, True))

    # ---- world & storage blocks
    blk("tin_ore", lambda r: ore_block("tin", False))
    blk("deepslate_tin_ore", lambda r: ore_block("tin", True))
    blk("bauxite_ore", lambda r: ore_block("bauxite", False))
    blk("deepslate_titanium_ore", lambda r: ore_block("titanium", True))
    for metal in METALS:
        blk("%s_block" % metal, lambda r, metal=metal: storage_block(MAT[metal], r, glow=metal == "quantum_alloy"))

    # ---- items: raw materials
    itm("raw_tin", lambda r: raw_lump(ORES["tin"][0], r))
    itm("raw_bauxite", lambda r: raw_lump(MAT["bauxite"], r))
    itm("raw_titanium", lambda r: raw_lump(MAT["titanium_mineral"], r))
    for name in ["iron", "copper", "gold", "tin", "coal", "quartz", "bauxite", "titanium"]:
        itm("%s_dust" % name, lambda r, name=name: dust(DUST[name], r))
    for metal in METALS:
        veins = QUANTUM_VEINS if metal == "quantum_alloy" else None
        itm("%s_ingot" % metal, lambda r, metal=metal, veins=veins: ingot(MAT[metal], veins))
    itm("coke", coke_item)
    itm("fire_clay", fire_clay)
    itm("fire_brick", fire_brick_item)
    itm("silicon", lambda r: silicon_chunk())
    itm("silicon_wafer", lambda r: silicon_wafer())
    itm("ender_dust", ender_dust)

    # ---- items: parts
    for metal in ["iron", "copper", "tin", "bronze", "steel", "aluminum", "titanium"]:
        itm("%s_plate" % metal, lambda r, metal=metal: plate(MAT[metal]))
    for metal in ["iron", "bronze", "steel", "titanium"]:
        itm("%s_gear" % metal, lambda r, metal=metal: gear(MAT[metal]))
    itm("iron_rod", lambda r: rod(MAT["iron"]))
    itm("steel_rod", lambda r: rod(MAT["steel"]))
    itm("copper_wire", lambda r: wire_spool(MAT["copper"]))
    itm("gold_wire", lambda r: wire_spool(MAT["gold"]))
    for kind in ["plate", "gear", "rod", "wire"]:
        itm("%s_mold" % kind, lambda r, kind=kind: mold(kind))
    itm("basic_circuit", lambda r: circuit(1))
    itm("advanced_circuit", lambda r: circuit(3))
    itm("machine_frame", lambda r: lattice_frame(list(reversed(STEEL_T.frame)), thick=2, bolts=True))
    itm("advanced_machine_frame", lambda r: lattice_frame(list(reversed(TIERS[3].frame)), thick=3, bolts=True))
    itm("motor", lambda r: motor())
    itm("heating_coil", lambda r: heating_coil())
    itm("orbital_targeting_core", lambda r: orbital_targeting_core())
    itm("recall_charm", lambda r: recall_charm())

    # ---- items: upgrades & tools
    itm("speed_upgrade", lambda r: upgrade_card("speed"))
    itm("energy_upgrade", lambda r: upgrade_card("energy"))
    itm("forge_hammer", lambda r: forge_hammer())
    itm("wrench", lambda r: wrench())
    for kind in ["sword", "pickaxe", "axe", "shovel", "hoe"]:
        itm("bronze_%s" % kind, lambda r, kind=kind: bronze_tool(kind))
    itm("electric_drill", lambda r: electric_drill())
    return out


# textures drawn by tools/storage_resources.py (never touched here)
FOREIGN = re.compile(r"^(block|item)/storage_")


def write_all(out, prune=False):
    for sub in ("block", "item"):
        os.makedirs(os.path.join(TEX_DIR, sub), exist_ok=True)
    for rel, t in out.items():
        t.image().save(os.path.join(TEX_DIR, rel))
        meta = os.path.join(TEX_DIR, rel + ".mcmeta")
        if rel in ANIMATED:
            with open(meta, "w") as fh:
                fh.write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n'
                         % ANIMATED[rel])
        elif os.path.exists(meta):
            os.remove(meta)
    removed = []
    if prune:   # textures a previous version drew that no longer exist (v1 tier set)
        for sub in ("block", "item"):
            for f in sorted(os.listdir(os.path.join(TEX_DIR, sub))):
                rel = "%s/%s" % (sub, f[:-7] if f.endswith(".mcmeta") else f)
                if (f.endswith(".png") or f.endswith(".mcmeta")) and rel not in out and not FOREIGN.match(rel):
                    os.remove(os.path.join(TEX_DIR, sub, f))
                    removed.append("%s/%s" % (sub, f))
    return removed


def contact_sheet(out, path, scale=8, cols=14, filt=None):
    entries = [(k, v) for k, v in out.items() if filt is None or filt(k)]
    cell_w, cell_h = 16 * scale + 8, 16 * scale + 22
    rows = (len(entries) + cols - 1) // cols
    img = Image.new("RGBA", (cols * cell_w + 8, rows * cell_h + 8), (48, 50, 56, 255))
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    checker = Image.new("RGBA", (16 * scale, 16 * scale))
    cd = ImageDraw.Draw(checker)
    for y in range(16):
        for x in range(16):
            cd.rectangle([x * scale, y * scale, x * scale + scale - 1, y * scale + scale - 1],
                         fill=(92, 96, 104, 255) if (x + y) % 2 else (78, 82, 90, 255))
    for i, (name, t) in enumerate(entries):
        cx, cy = 8 + (i % cols) * cell_w, 8 + (i // cols) * cell_h
        tile = t.image()
        if tile.height > 16:   # animated strips: show the first frame
            tile = tile.crop((0, 0, 16, 16))
        tile = tile.resize((16 * scale, 16 * scale), Image.NEAREST)
        img.paste(checker, (cx, cy))
        img.alpha_composite(tile, (cx, cy))
        label = os.path.basename(name)[:-4]
        if len(label) > 21:
            label = label[:20] + "~"
        draw.text((cx, cy + 16 * scale + 4), label, fill=(230, 230, 230, 255), font=font)
    img.save(path)


def main():
    out = build()
    removed = write_all(out, prune="--prune" in sys.argv)
    nb = sum(1 for k in out if k.startswith("block/"))
    ni = sum(1 for k in out if k.startswith("item/"))
    print("wrote %d block + %d item textures to %s" % (nb, ni, TEX_DIR))
    if removed:
        print("removed %d obsolete files: %s" % (len(removed), ", ".join(removed)))
    if "--no-preview" not in sys.argv:
        contact_sheet(out, PREVIEW)
        print("preview:", PREVIEW)
    for a in sys.argv[1:]:
        if a.startswith("--sheet="):   # --sheet=<regex>:<path>  debug sheet of a subset
            pat, path = a[len("--sheet="):].split(":", 1)
            contact_sheet(out, path, scale=10, cols=10, filt=lambda k, pat=pat: re.search(pat, k))
    if "--no-models" not in sys.argv:
        # machine block models (tools/models/block/*.json) + tools/model_preview.png
        sys.path.insert(0, os.path.join(ROOT, "tools", "models"))
        import gen_models
        gen_models.main([] if "--no-preview" not in sys.argv else ["--no-preview"])


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Factory Ascent texture generator.

Deterministically draws every block and item texture of the mod as 16x16
vanilla-style pixel art and writes them to
src/main/resources/assets/factoryascent/textures/{block,item}/.

Run:  python3 tools/gen_textures.py            (writes textures + preview)
      python3 tools/gen_textures.py --no-preview
      python3 tools/gen_textures.py "--sheet=<regex>:<out.png>"   (debug sheet)

Layout of this file
  1. colour helpers + palettes (tiers, materials, rocks)
  2. Tex canvas + drawing primitives (rects, masks, blobs, bars, sprites)
  3. block families (casing, machine fronts, miner, solar, energy cell,
     cable/pipe, ores, storage blocks)
  4. item families (raw, dusts, ingots, plates, gears, rods, wires, molds,
     circuits, frames, components, upgrades, tools)

Tiers: basic (iron grey), reinforced (steel blue-grey), advanced (copper),
elite (violet), ultimate (cyan on near-black).
  5. main + contact sheet

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


def led(t, on):
    """Status light in the top-right rivet slot (x 12-13, y 2-3), same spot on
    every machine: dark red when idle, green when working."""
    L = LED_ON if on else LED_OFF
    t.set(12, 2, L[2]); t.set(13, 2, L[1])
    t.set(12, 3, L[1]); t.set(13, 3, L[0])


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


def casing_side(T, rng):
    t = panel_base(T, rng)
    P = T.panel
    recess(t, 5, 5, 10, 10, T)
    t.rect(6, 6, 9, 9, mix(P[2], P[3], 0.2))
    t.set(6, 6, mix(P[2], P[3], 0.55))
    if T.glow:
        t.rect(7, 7, 8, 8, CYAN_GLOW[1])
        t.set(7, 7, CYAN_GLOW[2])
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


# ---- machine fronts --------------------------------------------------------------
# Art area: x 2-13, y 4-13 (12x10). The header strip (y 2-3) holds a rivet at the
# top-left, a nameplate, and the status LED at the top-right on every machine.
AX, AY = 2, 4


def machine_base(T, rng, sunk=False):
    t = panel_base(T, rng, rivets=False)
    F = T.frame
    rivet(t, 2, 2, T)
    t.hline(5, 10, 2, F[3])     # nameplate
    t.hline(5, 10, 3, F[1])
    t.set(5, 3, F[2])
    if sunk:
        recess(t, 2, 4, 13, 13, T, fill=VOID)
    return t


def machine_pal(T, extra=None):
    """Shared sprite palette. Upper-case/lower-case pairs:
    h l m d D = frame hi..deep, H L M q Q = panel hi..deep,
    s t u v w = neutral steel, K k = interior blacks."""
    F, P = T.frame, T.panel
    pal = {
        "K": VOID2, "k": VOID,
        "h": F[0], "l": F[1], "m": F[2], "d": F[3], "D": F[4],
        "H": P[0], "L": P[1], "M": P[2], "q": P[3], "Q": P[4],
        "s": STEEL[0], "t": STEEL[1], "u": STEEL[2], "v": STEEL[3], "w": STEEL[4],
    }
    if extra:
        pal.update(extra)
    return pal


def art(t, rows, pal):
    assert len(rows) == 10, rows
    for r in rows:
        assert len(r) == 12, (r, len(r))
    t.sprite(rows, pal, AX, AY)


FIRE_IDLE = {"A": "#0C0A0A", "B": "#100C0B", "C": "#150E0C", "D": "#1C100C", "E": "#28120C", "F": "#3A160C"}
FIRE_ON = {"A": "#5A1C08", "B": "#96360C", "C": "#D25E14", "D": "#F29426", "E": "#FFCC56", "F": "#FFF2BA"}


def m_electric_furnace(T, on, rng):
    """Arched furnace mouth with a grate; glows orange/yellow when on."""
    t = machine_base(T, rng)
    pal = machine_pal(T, FIRE_ON if on else FIRE_IDLE)
    art(t, [
        "..hlllllll..",
        ".lmdKKKKdmd.",
        ".lmKAAAAKmD.",
        ".lKAABBAAKD.",
        ".lKBBCCBBKD.",
        ".lKCCDDCCKD.",
        ".lKDEFFEDKD.",
        ".lKuvuvuvKD.",
        ".mdDDDDDDDD.",
        "..DDDDDDDD..",
    ], pal)
    if on:
        t.set(AX + 4, AY + 1, "#7A2A0C"); t.set(AX + 7, AY + 1, "#7A2A0C")
    return t


def cog(t, x0, y0, pal, teeth, hub):
    """5x5 cog body at (x0,y0) with 1-px teeth.
    teeth = {side: offsets along that side}, sides 'n','s','w','e'."""
    px = {}
    for y in range(5):
        for x in range(5):
            if (x, y) not in ((0, 0), (4, 0), (0, 4), (4, 4)):
                px[(x0 + x, y0 + y)] = x + y
    for side, offs in teeth.items():
        for o in offs:
            if side == "n":
                px[(x0 + o, y0 - 1)] = o - 1
            elif side == "s":
                px[(x0 + o, y0 + 5)] = o + 5
            elif side == "w":
                px[(x0 - 1, y0 + o)] = o - 1
            elif side == "e":
                px[(x0 + 5, y0 + o)] = o + 5
    for (x, y), k in px.items():
        t.set(x, y, pal[0] if k <= 1 else pal[1] if k <= 3 else pal[2] if k <= 5 else pal[3])
    cx, cy = x0 + 2, y0 + 2
    t.set(cx, cy, hub[0])
    t.set(cx - 1, cy, hub[1]); t.set(cx, cy - 1, hub[1])
    t.set(cx + 1, cy, hub[2]); t.set(cx, cy + 1, hub[2])


def m_crusher(T, on, rng):
    """Two meshing toothed rollers; when on they turn and spit grit."""
    t = machine_base(T, rng, sunk=True)
    t.set(3, 5, STEEL[2]); t.set(12, 5, STEEL[3])   # chute walls
    pal = [STEEL[0], STEEL[1], STEEL[2], STEEL[3]]
    hub = [VOID2, CYAN_GLOW[2], CYAN_GLOW[1]] if T.glow else [T.frame[4], T.frame[1], T.frame[3]]
    a, b = ([1, 3], [0, 2, 4]) if not on else ([0, 2, 4], [1, 3])
    cog(t, 3, 6, pal, {"n": a, "s": a, "w": [1, 3], "e": [1, 3] if not on else [0, 2, 4]}, hub)
    cog(t, 9, 6, pal, {"n": b, "s": b, "e": [1, 3]}, hub)
    if on:
        rs = rng_for("crusher_dust")
        for (x, y) in [(3, 12), (6, 12), (8, 13), (10, 12), (13, 12), (5, 5), (8, 5), (11, 5)]:
            t.set(x, y, "#B8AE9C" if rs.random() < 0.5 else "#8E8676")
        t.set(8, 12, "#D8D0C0"); t.set(7, 4, "#9A9080")
    else:
        t.set(6, 5, "#5E5850"); t.set(9, 5, "#4A4640")
    return t


def m_metal_press(T, on, rng):
    """Hydraulic ram over a die with a plate; when on the ram is down."""
    t = machine_base(T, rng)
    pal = machine_pal(T, {"p": "#D6DAE0", "o": "#9CA2AC", "y": "#FFE070", "Y": "#FFB030"})
    if not on:
        rows = [
            "kkkkkkkkkkkk",
            "kukkkstkkkuk",
            "kukkkstkkkuk",
            "kussssssssuk",
            "kuttttttttuk",
            "kuvvvvvvvvuk",
            "kukkkkkkkkuk",
            "kuppppppppuk",
            "kuooooooooqk",
            "kmmmmmmmmmmk",
        ]
    else:
        rows = [
            "kkkkkkkkkkkk",
            "kukkkstkkkuk",
            "kukkkstkkkuk",
            "kukkkstkkkuk",
            "kussssssssuk",
            "kuttttttttuk",
            "yuvvvvvvvvuy",
            "kYppppppppYk",
            "kuooooooooqk",
            "kmmmmmmmmmmk",
        ]
    art(t, rows, pal)
    t.hline(2, 13, 13, T.frame[3])
    return t


MOLTEN_IDLE = {"A": "#16110F", "B": "#1C1512", "C": "#35302C", "E": "#433C36", "X": "#3A3A3C",
               "Y": "#48484A", "c": VOID, "x": VOID, "g": "#2A2624"}
MOLTEN_ON = {"A": "#4E1E0A", "B": "#7A300C", "C": "#F07A1E", "E": "#FFD27A", "X": "#D8DCE6",
             "Y": "#FFFFFF", "c": "#F07A1E", "x": "#D8DCE6", "g": "#FFB43C"}


def m_alloy_smelter(T, on, rng):
    """Twin crucibles pouring two molten metals into one trough."""
    t = machine_base(T, rng, sunk=True)
    pal = machine_pal(T, MOLTEN_ON if on else MOLTEN_IDLE)
    art(t, [
        "KKsuKKKKsuKK",
        "KBsuBBBBsuBK",
        "sttttusttttu",
        "tCEECvtXYYXv",
        "tCCCCvtXXXXv",
        ".tuuvKKtuuv.",
        "...cKKKKx...",
        "....cKKx....",
        "...tggggv...",
        "mmmmmmmmmmmd",
    ], pal)
    return t


def m_assembler(T, on, rng):
    """Safety-orange gantry arm with a gripper over a workpiece."""
    t = machine_base(T, rng)
    G = (LED_ON[1], LED_ON[2]) if on else ("#1E3A22", "#27482C")
    pal = machine_pal(T, {"g": G[0], "G": G[1],
                          "c": T.accent, "C": lighten(T.accent, 0.5), "b": darken(T.accent, 0.35),
                          "h": "#FFE7A0", "l": "#F4B83A", "m": "#C8861C", "d": "#86560E"})
    art(t, [
        "KKKKKKKKKKKK",
        "KhllmKKKKKKK",
        "hlGmllllllmK",
        "lmmddddddmdK",
        "KlmKKKKKKtuK",
        "KlmKKKKKsttu",
        "KlmKKKKKsKKu",
        "KlmKKKKKCcbK",
        "hlmgdKKKcbbK",
        "ddddddtttttu",
    ], pal)
    if on:
        t.set(AX + 10, AY + 6, "#FFE070")  # weld spark
    return t


def m_combustion(T, on, rng):
    """Fire box behind a grate, exhaust louvres and a pressure gauge."""
    t = machine_base(T, rng)
    pal = machine_pal(T, FIRE_ON if on else FIRE_IDLE)
    pal.update({"W": "#ECEAE0", "n": "#D02010", "x": "#9A9890"})
    art(t, [
        "qqqqqqq.vvv.",
        "LLLLLLL.vWWv",
        "qqqqqqq.vWnv",
        "LLLLLLL.vnxv",
        "wwwwwwwwwvvw",
        "wKsKsKsKsKsv",
        "wAsBsCsBsAsv",
        "wCsDsEsDsCsv",
        "wEsFsFsFsEsv",
        "vvvvvvvvvvvv",
    ], pal)
    if on:
        t.set(AX + 3, AY + 5, FIRE_ON["B"]); t.set(AX + 7, AY + 5, FIRE_ON["B"])
    return t


LAVA_IDLE = {"A": "#240806", "B": "#3E0C08", "C": "#5A140C"}
LAVA_ON = {"A": "#C83A0C", "B": "#FF7A1C", "C": "#FFD050"}


def m_geothermal(T, on, rng):
    """Louvred vent between two pipes with lava glowing behind."""
    t = machine_base(T, rng)
    pal = machine_pal(T, LAVA_ON if on else LAVA_IDLE)
    art(t, [
        "stvkkkkkkstv",
        "stvAABAABstv",
        "stvlllllllst",
        "hlmddddddhlm",
        "stvBCBBCBstv",
        "stvlllllllst",
        "hlmddddddhlm",
        "stvCBCCBCstv",
        "stvlllllllst",
        "stvddddddstv",
    ], pal)
    return t


MACHINES = {
    "electric_furnace": m_electric_furnace,
    "crusher": m_crusher,
    "metal_press": m_metal_press,
    "alloy_smelter": m_alloy_smelter,
    "assembler": m_assembler,
    "combustion_generator": m_combustion,
    "geothermal_generator": m_geothermal,
}


def machine_front(name, T, on, rng):
    t = MACHINES[name](T, on, rng)
    led(t, on)
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


def miner_side(T, rng):
    """Drill shaft running down the face, hazard band at the base."""
    t = panel_base(T, rng)
    recess(t, 5, 2, 10, 11, T, fill=VOID)
    for y in range(3, 11):
        t.set(6, y, STEEL[1]); t.set(7, y, STEEL[0]); t.set(8, y, STEEL[2]); t.set(9, y, STEEL[3])
    for y in (4, 8):   # shaft collars
        t.hline(6, 9, y, T.frame[1]); t.set(9, y, T.frame[3])
        t.hline(6, 9, y + 1, T.frame[3])
    hazard_band(t, 2, 11, 13, 13)
    return t


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


# ---- solar panel --------------------------------------------------------------------

SOLAR = {
    1: dict(cell=ramp("#14285A", "#1E3A78", "#2A4C92"), line="#8CA8DC", bus="#C8D4E8", div=2),
    2: dict(cell=ramp("#101E48", "#18306A", "#223E84"), line="#7890C8", bus="#A8B4C8", div=2),
    3: dict(cell=ramp("#0E1A40", "#15285E", "#1E3678"), line="#6C88C8", bus="#E0955A", div=3),
    4: dict(cell=ramp("#160E38", "#221650", "#2E1E6A"), line="#9C84E0", bus="#C49CF0", div=3),
    5: dict(cell=ramp("#0A0612", "#140C22", "#1C1230"), line="#3AC8C0", bus="#6FF0E8", div=4),
}


def solar_top(T, rng):
    S = SOLAR[T.n]
    cells = S["cell"]
    t = Tex(fill=cells[1])
    frame(t, T)
    div = S["div"]
    size = 12 // div
    for gy in range(div):
        for gx in range(div):
            x0, y0 = 2 + gx * size, 2 + gy * size
            x1, y1 = x0 + size - 1, y0 + size - 1
            t.rect(x0, y0, x1, y1, cells[1])
            t.hline(x0, x1, y1, S["line"])
            t.vline(x1, y0, y1, S["line"])
            t.set(x0, y0, cells[2])
            if size >= 4:
                t.set(x1 - 1, y1 - 1, cells[0])
                t.hline(x0, x1 - 1, y0 + size // 2 - 1, mix(cells[1], S["line"], 0.35))
    t.vline(13, 2, 13, S["bus"])
    t.hline(2, 13, 13, S["bus"])
    for i in range(7):   # diagonal sky reflection
        x, y = 4 + i, 9 - i
        if t.get(x, y)[:3] != C(S["line"])[:3]:
            t.set(x, y, mix(t.get(x, y), "#FFFFFF", 0.18))
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


def energy_cell_front(T, rng):
    """Output face: a big lightning bolt behind a dark window."""
    t = panel_base(T, rng)
    recess(t, 4, 3, 11, 12, T, fill=VOID)
    t.sprite(BOLT, {"Y": "#FFD83A", "o": "#C88A10"}, 5, 3)
    t.set(9, 3, "#FFF6B0")
    return t


def energy_cell_side(T, rng):
    """Vertical charge gauge, half full."""
    t = panel_base(T, rng)
    recess(t, 6, 2, 9, 13, T, fill=VOID)
    full = ["#2A8A3A", "#4AD85E", "#8CF09A"]
    for y in range(3, 13):
        if y >= 8:
            t.set(7, y, full[2] if y % 2 == 0 else full[1])
            t.set(8, y, full[1] if y % 2 == 0 else full[0])
        else:
            t.set(7, y, "#1A1E1C"); t.set(8, y, "#121614")
    for y in range(3, 13, 2):   # scale ticks
        t.set(5, y, T.frame[3]); t.set(10, y, T.frame[3])
    return t


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


def machine_frame(T):
    if T.n == 5:
        return lattice_frame(ramp("#0C1014", "#182026", "#26303A", "#36444E", "#4C5C68"), thick=3,
                             joint=CYAN_GLOW)
    pal = list(reversed(T.frame))
    return lattice_frame(pal, thick=2 if T.n <= 2 else 3, bolts=T.n >= 2)


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


KIT = [
    "....hhhhhh....",
    "....h....h....",
    ".dddddddddddd.",
    ".hlllllllllld.",
    ".hmmmmwmmmmmd.",
    ".hmmmwwwmmmmd.",
    ".hmmwwwwwmmmd.",
    ".ddddwwwddddd.",
    ".hmmmwwwmmmmd.",
    ".hmmmwwwmmmmd.",
    ".hmmmmmmmmmmd.",
    ".dddddddddddd.",
]


def upgrade_kit(T):
    """Tier installer: tier-coloured case, white up-arrow, one pip per tier."""
    F = T.frame
    pal = {"h": F[0], "l": F[1], "m": F[2], "d": F[3], "w": "#FFFFFF"}
    if T.glow:
        pal["m"] = mix(F[3], "#101418", 0.4)
        pal["l"] = F[2]
    t = item()
    t.sprite(KIT, pal, 1, 1)
    t.set(7, 5, "#FFFFFF")
    for (x, y) in [(8, 7), (9, 7), (8, 9), (8, 10), (8, 8)]:
        t.set(x, y, "#D6DCE6")
    for i in range(T.n):
        t.set(8 - T.n + i * 2, 12, "#FFD84A")
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


# =============================================================================
# 5. Main
# =============================================================================

TIER_ORDER = [TIERS[n] for n in range(1, 6)]
METALS = ["tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"]


def build():
    """Ordered {relative path: Tex}."""
    out = {}

    def blk(name, fn):
        out["block/%s.png" % name] = fn(rng_for("block/" + name))

    def itm(name, fn):
        out["item/%s.png" % name] = fn(rng_for("item/" + name))

    # ---- tiered blocks
    for T in TIER_ORDER:
        blk("casing_%s_side" % T.name, lambda r, T=T: casing_side(T, r))
        blk("casing_%s_top" % T.name, lambda r, T=T: casing_top(T, r))
        blk("casing_%s_bottom" % T.name, lambda r, T=T: casing_bottom(T, r))
    for m in MACHINES:
        for T in TIER_ORDER:
            blk("%s_%s_front" % (T.name, m), lambda r, T=T, m=m: machine_front(m, T, False, r))
            blk("%s_%s_front_on" % (T.name, m), lambda r, T=T, m=m: machine_front(m, T, True, r))
    for T in TIER_ORDER:
        blk("%s_miner_side" % T.name, lambda r, T=T: miner_side(T, r))
        blk("%s_miner_top" % T.name, lambda r, T=T: miner_top(T, r))
        blk("%s_miner_bottom" % T.name, lambda r, T=T: miner_bottom(T, r))
    for T in TIER_ORDER:
        blk("%s_solar_panel_top" % T.name, lambda r, T=T: solar_top(T, r))
        blk("%s_energy_cell_front" % T.name, lambda r, T=T: energy_cell_front(T, r))
        blk("%s_energy_cell_side" % T.name, lambda r, T=T: energy_cell_side(T, r))
    for T in TIER_ORDER:
        blk("%s_power_cable" % T.name, lambda r, T=T: power_cable(T, r))
        blk("%s_item_pipe" % T.name, lambda r, T=T: item_pipe(T, r))
    blk("item_pipe_extract", item_pipe_extract)

    # ---- world & storage blocks
    blk("tin_ore", lambda r: ore_block("tin", False))
    blk("deepslate_tin_ore", lambda r: ore_block("tin", True))
    blk("bauxite_ore", lambda r: ore_block("bauxite", False))
    blk("deepslate_bauxite_ore", lambda r: ore_block("bauxite", True))
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
    itm("silicon", lambda r: silicon_chunk())
    itm("silicon_wafer", lambda r: silicon_wafer())

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
    for T in TIER_ORDER:
        itm("%s_circuit" % T.name, lambda r, T=T: circuit(T.n))
    for T in TIER_ORDER:
        itm("%s_machine_frame" % T.name, lambda r, T=T: machine_frame(T))
    itm("motor", lambda r: motor())
    itm("heating_coil", lambda r: heating_coil())

    # ---- items: upgrades & tools
    itm("speed_upgrade", lambda r: upgrade_card("speed"))
    itm("energy_upgrade", lambda r: upgrade_card("energy"))
    itm("forge_hammer", lambda r: forge_hammer())
    itm("wrench", lambda r: wrench())
    for T in TIER_ORDER[1:]:
        itm("%s_upgrade_kit" % T.name, lambda r, T=T: upgrade_kit(T))
    return out


def write_all(out):
    for sub in ("block", "item"):
        os.makedirs(os.path.join(TEX_DIR, sub), exist_ok=True)
    for rel, t in out.items():
        t.image().save(os.path.join(TEX_DIR, rel))


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
        img.paste(tile, (cx, cy), tile)
        label = os.path.basename(name)[:-4]
        if len(label) > 21:
            label = label[:20] + "~"
        draw.text((cx, cy + 16 * scale + 4), label, fill=(230, 230, 230, 255), font=font)
    img.save(path)


def main():
    out = build()
    write_all(out)
    nb = sum(1 for k in out if k.startswith("block/"))
    ni = sum(1 for k in out if k.startswith("item/"))
    print("wrote %d block + %d item textures to %s" % (nb, ni, TEX_DIR))
    if "--no-preview" not in sys.argv:
        contact_sheet(out, PREVIEW)
        print("preview:", PREVIEW)
    for a in sys.argv[1:]:
        if a.startswith("--sheet="):   # --sheet=<regex>:<path>  debug sheet of a subset
            pat, path = a[len("--sheet="):].split(":", 1)
            contact_sheet(out, path, scale=10, cols=10, filt=lambda k, pat=pat: re.search(pat, k))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Textures of the fluids feature (Java: fluid/), drawn with tools/gen_textures.py's canvas and palettes:

  fluids      still + flowing animations for crude oil, diesel, rocket fuel, coolant (liquids) and
              steam, biogas, deuterium, tritium, helium-3 (gases)   -> block/fluid/<id>_still|_flow
  buckets     crude oil, diesel, rocket fuel, coolant
  logistics   fluid pipes in the four stage colours (+ the extracting connector), three tank sizes
              (frame, glass, lid), Pump, Fuelling Port
  power       Boiler, Biogas Digester, Diesel Generator, Steam Turbine (+ rotor)
  oil         Oil Derrick (wellhead, platform, pumpjack paint/steel/weight), Refinery (base, tower, cap)
  materials   Plastic, Tar; Asphalt

Run:  python3 tools/features/fluids_textures.py   (redraws all of them, writes tools/fluids_textures_preview.png)

gen_resources.py loads every tools/features/*.py; generate(ctx) here only draws textures that are missing.
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOLS = HERE.parent
TEX = TOOLS.parent / "src/main/resources/assets/factoryascent/textures"

G = None


def _init():
    global G, C, mix, lighten, darken, ramp, rng_for, Tex, IRON, COPPER, BRONZE, STEEL, DARK, BRICK, TIERS, GLASS
    global HAZ_Y, HAZ_K, PAINT_Y, PAINT_O, PAINT_B, PAINT_G, CONCRETE, TITAN, ALU, WHITE
    if G is not None:
        return
    if str(TOOLS) not in sys.path:
        sys.path.insert(0, str(TOOLS))
    import gen_textures
    G = gen_textures
    C, mix, lighten, darken, ramp, rng_for, Tex = G.C, G.mix, G.lighten, G.darken, G.ramp, G.rng_for, G.Tex
    IRON, COPPER, BRONZE, TITAN, ALU = G.MAT["iron"], G.MAT["copper"], G.MAT["bronze"], G.MAT["titanium"], G.MAT["aluminum"]
    STEEL = ramp("#373B42", "#5A606A", "#868E9A", "#B4BAC4", "#E2E6EC")
    DARK = ramp("#16181C", "#24272D", "#353A42", "#4C525C", "#6A717C")
    BRICK = ramp("#3A1A10", "#6A2E1C", "#8E4028", "#B05A38", "#D07A50")
    TIERS = G.TIERS
    GLASS = ramp("#4E646C", "#7E9CA4", "#A8C6CC", "#D2E8EA", "#F6FFFF")
    HAZ_Y = ramp("#7A5A06", "#D8A818", "#F6CC3A")
    HAZ_K = ramp("#141416", "#26262A")
    PAINT_Y = ramp("#5A4206", "#9A7410", "#D4A41C", "#EEC63E", "#FFE688")
    PAINT_O = ramp("#5A2406", "#9A4410", "#D46A1C", "#F08E3A", "#FFC488")
    PAINT_B = ramp("#0E1C3A", "#1C3466", "#2E5096", "#4874C0", "#7EA6E8")
    PAINT_G = ramp("#1C3016", "#2E4E22", "#44702E", "#62923E", "#8CB85A")
    CONCRETE = ramp("#4A4844", "#66635E", "#85827C", "#A3A09A", "#C4C1BA")
    WHITE = ramp("#7E8692", "#AEB5BF", "#D5DAE0", "#EEF1F4", "#FFFFFF")


# ============================================================ helpers

def tex(fill=None, w=16, h=16):
    return Tex(w, h, fill) if fill is not None else Tex(w, h)


def noise(t, rng, pal, x0=0, y0=0, x1=15, y1=15, base=2, p=0.16):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r = rng.random()
            t.set(x, y, pal[base - 1] if r < p / 2 else pal[base + 1] if r < p else pal[base])


def bevel(t, x0, y0, x1, y1, light, dark):
    t.hline(x0, x1, y0, light)
    t.vline(x0, y0, y1, light)
    t.hline(x0, x1, y1, dark)
    t.vline(x1, y0, y1, dark)


def bolt(t, x, y, pal):
    t.set(x, y, pal[4])
    t.set(x + 1, y, pal[2])
    t.set(x, y + 1, pal[2])
    t.set(x + 1, y + 1, pal[0])


def plate(rng, pal, bolts=True, p=0.14):
    t = tex()
    noise(t, rng, pal, base=2, p=p)
    bevel(t, 0, 0, 15, 15, pal[3], pal[0])
    bevel(t, 1, 1, 14, 14, pal[4], pal[1])
    if bolts:
        for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
            bolt(t, x, y, pal)
    return t


def disc(t, cx, cy, r, col, r0=-1.0):
    for y in range(t.h):
        for x in range(t.w):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 < d <= r:
                t.set(x, y, col)


def hazard(t, x0, y0, x1, y1, phase=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t.set(x, y, HAZ_Y[1 + ((x + y) % 4 == 0)] if ((x + y + phase) // 2) % 2 == 0 else HAZ_K[(x + y) % 2])


def grille(t, x0, y0, x1, y1, pal):
    t.rect(x0, y0, x1, y1, pal[0])
    for y in range(y0 + 1, y1, 2):
        t.hline(x0 + 1, x1 - 1, y, pal[2])
        t.hline(x0 + 1, x1 - 1, y + 1, pal[1]) if y + 1 < y1 else None


def gauge(t, cx, cy, r, needle_angle=-40):
    disc(t, cx, cy, r + 0.6, DARK[0])
    disc(t, cx, cy, r, C("#ECE6D2"))
    for i in range(7):
        a = math.radians(-210 + i * 40)
        t.set(int(cx + math.cos(a) * (r - 0.8)), int(cy + math.sin(a) * (r - 0.8)), DARK[2])
    a = math.radians(needle_angle - 90)
    for k in range(int(r)):
        t.set(int(cx + math.cos(a) * k * 0.9), int(cy + math.sin(a) * k * 0.9), C("#C0281E"))


# ============================================================ fluids (tileable, looping animations)

LIQUIDS = {
    # id: (palette deep..hi, alpha, sheen)
    "crude_oil": (["#07060A", "#100D0C", "#1A1612", "#2A2420", "#4C4438"], 255, True),
    "diesel": (["#5A3A06", "#8E600E", "#C08A1C", "#E0B034", "#FFE27A"], 225, False),
    "rocket_fuel": (["#5A1406", "#9A2A0C", "#D2481C", "#F27A34", "#FFC06A"], 230, False),
    "coolant": (["#063A44", "#0E6A74", "#1EA8B4", "#4CDAE2", "#C4FFFF"], 190, False),
}
GASES = {
    "steam": (["#9AA4AC", "#C4CCD2", "#E2E8EC", "#F4F7F9", "#FFFFFF"], 150),
    "biogas": (["#4A5A1A", "#6E8426", "#94AC3A", "#BCD25E", "#E6F4A0"], 160),
    "deuterium": (["#16306A", "#2A54A8", "#4A82E0", "#86B6FF", "#D4E6FF"], 160),
    "tritium": (["#0E5A26", "#1A9A44", "#34D468", "#7CFF9E", "#D8FFE2"], 170),
    "helium_3": (["#3E2668", "#6A48A4", "#9A78D8", "#C8AEFF", "#F0E6FF"], 150),
}
FRAMES = 16


def _wave(x, y, t, size, terms):
    v = 0.0
    for (kx, ky, kt, ph, amp) in terms:
        v += amp * math.sin(2 * math.pi * (kx * x / size + ky * y / size + kt * t / FRAMES) + ph)
    return v


def _pal_at(pal, v, alpha):
    v = max(0.0, min(0.9999, v))
    i = v * (len(pal) - 1)
    k = int(i)
    c = mix(pal[k], pal[min(k + 1, len(pal) - 1)], i - k)
    return (c[0], c[1], c[2], alpha)


def liquid_frames(fid, flowing):
    pal, alpha, sheen = LIQUIDS[fid]
    pal = [C(p) for p in pal]
    size = 32 if flowing else 16
    frames = []
    for f in range(FRAMES):
        t = tex(w=size, h=size)
        for y in range(size):
            for x in range(size):
                if flowing:
                    # streaks running down the face (v increases), drifting sideways
                    yy = y - f * size / FRAMES
                    v = 0.5 + 0.22 * _wave(x, yy, 0, size, [(2, 1, 0, 0.3, 1.0), (5, 2, 0, 1.7, 0.55), (1, 3, 0, 2.2, 0.35)])
                    v += 0.12 * _wave(x, yy, f, size, [(7, 0, 1, 0.9, 1.0)])
                else:
                    v = 0.5 + 0.2 * _wave(x, y, f, size, [(1, 1, 1, 0.0, 1.0), (2, -1, 1, 1.3, 0.6), (1, 2, -1, 2.6, 0.5)])
                    v += 0.08 * _wave(x, y, f, size, [(4, 3, 2, 0.5, 1.0)])
                c = _pal_at(pal, v, alpha)
                if sheen:
                    # oil: faint rainbow sheen where the surface rises
                    s = _wave(x, y - (f * size / FRAMES if flowing else 0), f, size, [(3, 2, 1, 0.7, 1.0)])
                    if s > 0.82:
                        hue = (x * 7 + y * 3 + f * 5) % 3
                        tint = [C("#3A2A5A"), C("#1E4A4A"), C("#4A3A1A")][hue]
                        c = mix(c, tint, 0.55)[:3] + (alpha,)
                    elif v > 0.74:
                        c = mix(c, C("#5A5248"), 0.5)[:3] + (alpha,)
                t.set(x, y, c)
        frames.append(t)
    return frames


def gas_frames(fid, flowing):
    pal, alpha = GASES[fid]
    pal = [C(p) for p in pal]
    size = 32 if flowing else 16
    frames = []
    for f in range(FRAMES):
        t = tex(w=size, h=size)
        for y in range(size):
            for x in range(size):
                yy = y + (f * size / FRAMES if flowing else 0)  # gases rise
                v = 0.5 + 0.25 * _wave(x, yy, f, size, [(1, 2, 1, 0.4, 1.0), (3, 1, -1, 2.1, 0.5), (2, 3, 2, 1.1, 0.4)])
                a = int(alpha * (0.65 + 0.35 * max(0, min(1, v))))
                t.set(x, y, _pal_at(pal, v, a))
        frames.append(t)
    return frames


def stack(frames):
    w, h = frames[0].w, frames[0].h
    t = Tex(w, h * len(frames))
    for i, f in enumerate(frames):
        for y in range(h):
            for x in range(w):
                t.p[i * h + y][x] = f.p[y][x]
    return t


def bucket(fid):
    pal = [C(p) for p in (LIQUIDS[fid][0])]
    t = tex()
    rows = [
        "................",
        "................",
        "...oooooooooo...",
        "..oIFFFFFFFFIo..",
        "..oIFFFFFFFFIo..",
        "..oIIIIIIIIIIo..",
        "...oIiiiiiiIo...",
        "...oIiiiiiiIo...",
        "...oIiiiiiiIo...",
        "....oIiiiiIo....",
        "....oIiiiiIo....",
        "....oIIIIIIo....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
    ]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "o":
                t.set(x, y, C("#2A2A2E"))
            elif ch == "I":
                t.set(x, y, IRON[3] if x < 8 else IRON[1])
            elif ch == "i":
                t.set(x, y, IRON[2] if (x + y) % 5 else IRON[3])
            elif ch == "F":
                t.set(x, y, pal[3] if (x * 3 + y) % 5 == 0 else pal[2])
    t.set(5, 3, pal[4])
    t.set(6, 3, pal[4])
    if fid == "crude_oil":
        t.set(9, 4, C("#3A2A5A"))
        t.set(6, 7, C("#1A1612"))  # a drip down the side
        t.set(6, 8, C("#1A1612"))
    return t


# ============================================================ pipes & tanks

PIPE_TIERS = {"bronze_fluid_pipe": 1, "steel_fluid_pipe": 2, "aluminum_fluid_pipe": 3, "titanium_fluid_pipe": 5}


def fluid_pipe(T, rng):
    """Riveted dark-steel duct (8 px across, columns 4..11) with tier-coloured flanges and a glass
    window down the middle (translucent) that shows the fluid inside."""
    F = T.frame
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, DARK[2] if (x + y) % 7 else DARK[3])
    for y in range(16):
        t.set(4, y, DARK[0])
        t.set(5, y, DARK[4])
        t.set(10, y, DARK[1])
        t.set(11, y, DARK[0])
        for x in (6, 7, 8, 9):
            g = GLASS[3] if x == 6 else GLASS[2] if x == 7 else GLASS[1]
            t.set(x, y, (g[0], g[1], g[2], 110 if x in (7, 8) else 150))
    for y in (0, 1, 14, 15):   # tier flanges at both ends of every arm and around the core
        t.hline(3, 12, y, F[2] if y in (0, 15) else F[1])
        t.set(4, y, F[3])
        t.set(11, y, F[4])
    for y in (4, 11):          # thin tier rings framing the core square
        t.hline(4, 11, y, F[2])
        t.set(4, y, F[3])
    for y in (2, 13):
        t.set(5, y, F[0])
        t.set(10, y, F[0])
    if T.glow:
        for y in (0, 15):
            t.set(7, y, G.CYAN_GLOW[2])
            t.set(8, y, G.CYAN_GLOW[1])
    return t


def fluid_pipe_extract(rng):
    t = plate(rng, STEEL, bolts=False)
    t.rect(3, 3, 12, 12, C("#101418"))
    B = ramp("#0C3A5A", "#1A6AA0", "#2FA0E0", "#8AD8FF")
    for k in range(3):
        y0 = 4 + k * 3
        for (x, dy) in [(7, 0), (8, 0), (6, 1), (9, 1), (5, 2), (10, 2), (4, 3), (11, 3)]:
            if y0 + dy <= 11:
                t.set(x, y0 + dy, B[3] if dy == 0 else B[2] if x <= 7 else B[1])
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(t, x, y, STEEL)
    return t


TANK_PAL = {"bronze": None, "steel": None, "titanium": None}


def tank_pal(size):
    return {"bronze": BRONZE, "steel": STEEL, "titanium": TITAN}[size]


def tank_frame(size, rng):
    P = tank_pal(size)
    t = tex()
    noise(t, rng, P, base=2, p=0.12)
    bevel(t, 0, 0, 15, 15, P[4], P[0])
    t.vline(1, 1, 14, P[3])
    t.vline(14, 1, 14, P[1])
    for y in (3, 8, 13):
        bolt(t, 7, y, P)
    return t


def tank_glass(size, rng):
    P = tank_pal(size)
    t = tex()
    for y in range(16):
        for x in range(16):
            a = 40 if (x - y) % 9 and (x - y + 3) % 9 else 95
            t.set(x, y, (GLASS[4][0], GLASS[4][1], GLASS[4][2], a))
    # level ticks on the right edge
    for y in range(2, 15, 3):
        t.hline(12, 14, y, (P[3][0], P[3][1], P[3][2], 220))
    for y in range(2, 15, 6):
        t.hline(10, 14, y, (P[4][0], P[4][1], P[4][2], 230))
    return t


def tank_lid(size, rng):
    P = tank_pal(size)
    t = plate(rng, P, bolts=True)
    disc(t, 8, 8, 4.2, P[0])
    disc(t, 8, 8, 3.4, P[3])
    disc(t, 8, 8, 1.6, P[1])
    t.set(7, 7, P[4])
    return t


# ============================================================ machines

def boiler_firebox(rng, on):
    t = G.brick_wall(rng, BRICK, C("#2A2420"), [[8, 8], [4, 8, 4], [8, 8], [4, 8, 4]])
    t.rect(3, 6, 12, 14, IRON[0])
    t.rect(4, 7, 11, 13, IRON[2] if not on else C("#FF8A20"))
    if on:
        for y in range(8, 13):
            for x in range(5, 11):
                t.set(x, y, C("#FFE070") if (x * 2 + y) % 3 == 0 else C("#FFB030") if y > 9 else C("#FF7018"))
    else:
        for y in (8, 10, 12):
            t.hline(5, 10, y, IRON[1])
    t.rect(10, 9, 11, 10, IRON[4])
    t.hline(2, 13, 5, IRON[3])
    return t


def boiler_drum(rng):
    t = tex()
    noise(t, rng, STEEL, base=2, p=0.16)
    for x in (0, 5, 10, 15):
        t.vline(x, 0, 15, STEEL[0])
        for y in range(1, 16, 3):
            t.set(min(15, x + 1), y, STEEL[4])
    t.hline(0, 15, 0, STEEL[4])
    t.hline(0, 15, 15, STEEL[0])
    t.hline(0, 15, 7, STEEL[1])
    return t


def boiler_end(rng):
    t = plate(rng, STEEL, bolts=False)
    for i in range(12):
        a = i * math.pi / 6
        t.set(int(8 + math.cos(a) * 6.3), int(8 + math.sin(a) * 6.3), STEEL[4])
    gauge(t, 8, 7, 3.6, needle_angle=30)
    t.rect(6, 12, 9, 13, BRICK[2])
    return t


def boiler_valve():
    t = tex(C("#8A1C14"))
    disc(t, 8, 8, 7.5, C("#C82A1E"))
    disc(t, 8, 8, 6, C("#8A1C14"), 4.8)
    for a in range(0, 360, 60):
        r = math.radians(a)
        for k in range(1, 6):
            t.set(int(8 + math.cos(r) * k), int(8 + math.sin(r) * k), C("#E04A3A"))
    disc(t, 8, 8, 1.8, IRON[3])
    return t


def pump_body(rng, on):
    t = plate(rng, PAINT_B, bolts=True)
    t.rect(3, 4, 12, 11, DARK[1])
    for y in range(5, 11, 2):
        t.hline(4, 11, y, DARK[3])
    t.rect(5, 13, 6, 13, C("#3CE05A") if on else C("#6A1616"))
    t.rect(9, 13, 10, 13, C("#E0B020"))
    return t


def pump_motor(rng):
    t = tex()
    noise(t, rng, DARK, base=2, p=0.15)
    for x in range(0, 16, 2):
        t.vline(x, 0, 15, DARK[1])
    t.hline(0, 15, 0, DARK[4])
    t.hline(0, 15, 15, DARK[0])
    return t


def pump_top(rng):
    t = plate(rng, PAINT_B, bolts=True)
    disc(t, 8, 8, 4.5, DARK[0])
    disc(t, 8, 8, 3.5, DARK[3])
    for i in range(6):
        a = i * math.pi / 3
        t.set(int(8 + math.cos(a) * 2.5), int(8 + math.sin(a) * 2.5), DARK[4])
    return t


def pipe_steel(rng):
    t = tex()
    noise(t, rng, STEEL, base=2, p=0.12)
    t.vline(0, 0, 15, STEEL[4])
    t.vline(15, 0, 15, STEEL[0])
    for y in (0, 15):
        t.hline(0, 15, y, STEEL[1])
    return t


def digester_tank(rng):
    t = tex()
    noise(t, rng, CONCRETE, base=2, p=0.18)
    for y in (0, 15):
        t.hline(0, 15, y, CONCRETE[0])
    for y in (5, 10):
        t.hline(0, 15, y, CONCRETE[1])
        t.hline(0, 15, y + 1, CONCRETE[3])
    for x in range(1, 16, 5):     # formwork tie holes
        t.set(x, 2, CONCRETE[0])
        t.set(x, 13, CONCRETE[0])
    for x in (4, 12):             # moss / rust streaks
        for y in range(7, 7 + rng.randint(2, 5)):
            t.set(x, y, C("#56662E"))
    return t


def digester_dome(rng):
    t = tex()
    noise(t, rng, PAINT_G, base=2, p=0.2)
    for y in range(0, 16, 4):
        t.hline(0, 15, y, PAINT_G[3])
    for x in range(0, 16, 8):
        t.vline(x, 0, 15, PAINT_G[1])
    return t


def digester_hatch(rng, on):
    t = plate(rng, STEEL, bolts=True)
    disc(t, 8, 8, 5, STEEL[0])
    disc(t, 8, 8, 4.2, STEEL[3])
    disc(t, 8, 8, 3, C("#2A3A16") if not on else C("#6A8A2A"))
    if on:
        for (x, y) in ((7, 7), (9, 8), (8, 9)):
            t.set(x, y, C("#B8DA60"))   # bubbles in the sight glass
    return t


def diesel_panel(rng):
    t = plate(rng, PAINT_Y, bolts=True)
    for y in range(4, 12, 2):     # louvres
        t.hline(3, 12, y, PAINT_Y[0])
        t.hline(3, 12, y + 1, PAINT_Y[3])
    return t


def diesel_front(rng, on):
    t = plate(rng, PAINT_Y, bolts=True)
    t.rect(2, 2, 13, 9, DARK[0])
    for x in range(3, 13):
        for y in range(3, 9):
            t.set(x, y, DARK[2] if (x + y) % 2 else DARK[1])   # radiator core
    t.rect(3, 11, 12, 13, DARK[1])
    t.set(4, 12, C("#3CE05A") if on else C("#6A1616"))
    t.set(6, 12, C("#E0B020") if on else C("#5A4A10"))
    t.rect(8, 12, 11, 12, C("#2FB5B0") if on else C("#1A3A3A"))
    return t


def diesel_top(rng):
    t = plate(rng, PAINT_Y, bolts=True)
    hazard(t, 1, 1, 14, 2)
    hazard(t, 1, 13, 14, 14)
    t.rect(4, 5, 11, 10, DARK[1])
    for x in range(5, 11, 2):
        t.vline(x, 5, 10, DARK[3])
    return t


def exhaust(rng):
    t = tex()
    noise(t, rng, DARK, base=2, p=0.2)
    for y in range(0, 16, 4):
        t.hline(0, 15, y, DARK[1])
    for x in (3, 9):
        for y in range(rng.randint(0, 4), 16, 5):
            t.set(x, y, C("#5A3A22"))   # heat rust
    return t


def turbine_casing(rng):
    t = plate(rng, PAINT_B, bolts=True)
    t.hline(2, 13, 7, PAINT_B[1])
    t.hline(2, 13, 8, PAINT_B[3])
    return t


def turbine_front(rng, on):
    t = plate(rng, PAINT_B, bolts=True)
    disc(t, 8, 8, 6.8, DARK[0])
    disc(t, 8, 8, 6.1, C("#0C0E12"))
    disc(t, 8, 8, 6.8, STEEL[3], 6.1)
    for i in range(16):
        a = i * math.pi / 8
        t.set(int(8 + math.cos(a) * 6.5), int(8 + math.sin(a) * 6.5), STEEL[4] if i % 2 else STEEL[1])
    return t


def turbine_top(rng):
    t = plate(rng, PAINT_B, bolts=True)
    t.rect(3, 3, 12, 12, STEEL[1])
    t.rect(4, 4, 11, 11, STEEL[3])
    gauge(t, 8, 8, 3.2, needle_angle=60)
    return t


def turbine_blade():
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, STEEL[3] if (x + y // 3) % 4 else STEEL[4])
    t.hline(0, 15, 0, STEEL[1])
    t.hline(0, 15, 15, STEEL[1])
    return t


def turbine_hub():
    t = tex(STEEL[2])
    disc(t, 8, 8, 6, STEEL[3])
    disc(t, 8, 8, 3, STEEL[1])
    disc(t, 8, 8, 1.5, STEEL[4])
    return t


def wellhead(rng, on):
    t = plate(rng, DARK, bolts=True)
    t.rect(3, 3, 12, 12, DARK[0])
    disc(t, 8, 8, 4.4, STEEL[1])
    disc(t, 8, 8, 3.2, STEEL[3])
    disc(t, 8, 8, 1.8, C("#1A1612") if not on else C("#2A2420"))
    if on:
        t.set(8, 8, C("#4C4438"))
    hazard(t, 1, 14, 14, 14)
    return t


def derrick_base(rng):
    """Steel grating platform with a hazard edge."""
    t = tex(DARK[0])
    for y in range(16):
        for x in range(16):
            if x % 3 == 0 or y % 3 == 0:
                t.set(x, y, STEEL[2] if (x + y) % 2 else STEEL[1])
    bevel(t, 0, 0, 15, 15, STEEL[4], STEEL[0])
    return t


def derrick_side(rng):
    t = plate(rng, STEEL, bolts=True)
    hazard(t, 1, 1, 14, 3)
    return t


def pumpjack_paint(rng):
    t = tex()
    noise(t, rng, PAINT_O, base=2, p=0.14)
    t.hline(0, 15, 0, PAINT_O[4])
    t.hline(0, 15, 15, PAINT_O[0])
    for x in range(1, 16, 5):
        t.set(x, 2, PAINT_O[0])   # rivets
        t.set(x, 13, PAINT_O[0])
    return t


def pumpjack_steel(rng):
    t = tex()
    noise(t, rng, STEEL, base=1, p=0.2)
    t.hline(0, 15, 0, STEEL[3])
    t.hline(0, 15, 15, STEEL[0])
    return t


def pumpjack_weight(rng):
    t = tex()
    noise(t, rng, DARK, base=2, p=0.18)
    hazard(t, 0, 0, 15, 2)
    hazard(t, 0, 13, 15, 15)
    return t


def refinery_base(rng, on):
    t = plate(rng, STEEL, bolts=True)
    t.rect(3, 7, 12, 13, DARK[0])
    t.rect(4, 8, 11, 12, C("#1A1010") if not on else C("#FF6A18"))
    if on:
        for x in range(4, 12):
            t.set(x, 12, C("#FFE070"))
            if x % 2:
                t.set(x, 11, C("#FFA030"))
    hazard(t, 1, 1, 14, 3)
    return t


def refinery_side(rng):
    t = plate(rng, STEEL, bolts=True)
    t.rect(2, 5, 13, 11, STEEL[1])
    for x in (4, 8, 12):
        t.vline(x, 5, 11, DARK[1])     # pipe runs
    return t


def tower(rng):
    """Distillation column shell: polished steel with tray rings."""
    t = tex()
    for y in range(16):
        for x in range(16):
            t.set(x, y, WHITE[2] if (x + rng.randint(0, 2)) % 6 else WHITE[1])
    for y in (0, 8):
        t.hline(0, 15, y, STEEL[1])
        t.hline(0, 15, y + 1, STEEL[4])
    for x in (3, 11):              # insulation clamps
        t.vline(x, 2, 6, STEEL[2])
        t.vline(x, 10, 14, STEEL[2])
    return t


def tower_top(rng):
    t = tex(WHITE[2])
    disc(t, 8, 8, 6.5, WHITE[1])
    disc(t, 8, 8, 5.5, WHITE[3])
    disc(t, 8, 8, 2.0, DARK[1])
    for i in range(8):
        a = i * math.pi / 4
        t.set(int(8 + math.cos(a) * 6), int(8 + math.sin(a) * 6), STEEL[1])
    return t


def flare():
    t = tex()
    for y in range(16):
        for x in range(16):
            d = abs(x + 0.5 - 8) / (1 + y * 0.45)
            if y > 1 and d < 1:
                t.set(x, y, C("#FFE070") if d < 0.35 else C("#FF9A20") if d < 0.7 else C("#E0441A"))
    return t


def fuelling_body(rng):
    t = plate(rng, WHITE, bolts=True)
    hazard(t, 1, 12, 14, 14, phase=1)
    t.rect(4, 3, 11, 9, PAINT_O[2])
    t.rect(5, 4, 10, 8, PAINT_O[3])
    # flame-and-drop "fuel" sign
    t.set(7, 5, C("#FFFFFF"))
    t.set(8, 5, C("#FFFFFF"))
    t.set(7, 6, C("#FFFFFF"))
    t.set(8, 7, C("#FFFFFF"))
    return t


def fuelling_top(rng):
    t = plate(rng, WHITE, bolts=True)
    disc(t, 8, 8, 4, PAINT_O[1])
    disc(t, 8, 8, 3, DARK[1])
    disc(t, 8, 8, 1.5, DARK[3])
    return t


def hose():
    t = tex(DARK[1])
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                t.set(x, y, DARK[3])
    return t


def nozzle(rng):
    t = tex()
    noise(t, rng, PAINT_O, base=2, p=0.12)
    t.hline(0, 15, 0, PAINT_O[4])
    t.hline(0, 15, 15, PAINT_O[0])
    return t


# ============================================================ materials

def plastic():
    t = tex()
    rows = ["................",
            "................",
            "....aaaaaaaaa...",
            "...aWWWWWWWWWa..",
            "...aWwwwwwwwWa..",
            "..aWwwwwwwwwWa..",
            "..aWwwwwwwwwWa..",
            "..aWwwwwwwwWa...",
            ".aWwwwwwwwwWa...",
            ".aWwwwwwwwwWa...",
            ".aWWWWWWWWWa....",
            "..aaaaaaaaaa....",
            "................",
            "................",
            "................",
            "................"]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "a":
                t.set(x, y, C("#7A8A96"))
            elif ch == "W":
                t.set(x, y, C("#F2F6F8"))
            elif ch == "w":
                t.set(x, y, C("#DDE6EC") if (x + y) % 6 else C("#FFFFFF"))
    t.set(5, 4, C("#FFFFFF"))
    t.set(6, 4, C("#FFFFFF"))
    return t


def tar():
    t = tex()
    disc(t, 8, 9.5, 5.8, C("#0A0908"))
    disc(t, 8, 9.5, 5.0, C("#16130F"))
    disc(t, 7, 6.5, 3.4, C("#16130F"))
    disc(t, 7, 6.5, 2.6, C("#221E18"))
    t.set(6, 5, C("#5A5248"))
    t.set(5, 6, C("#4A4238"))
    t.set(10, 9, C("#3A3428"))
    t.set(8, 14, C("#0A0908"))
    t.set(8, 15, C("#0A0908"))
    return t


def asphalt(rng):
    t = tex()
    A = ramp("#141416", "#1E1E21", "#29292C", "#3A3A3E", "#5A5A60")
    for y in range(16):
        for x in range(16):
            r = rng.random()
            t.set(x, y, A[4] if r < 0.04 else A[3] if r < 0.16 else A[1] if r < 0.32 else A[2])
    return t


# ============================================================ build / write

def build():
    _init()
    out, anim = {}, {}
    R = rng_for

    def blk(name, t):
        out["block/%s.png" % name] = t

    def itm(name, t):
        out["item/%s.png" % name] = t

    for fid in LIQUIDS:
        out["block/fluid/%s_still.png" % fid] = stack(liquid_frames(fid, False))
        out["block/fluid/%s_flow.png" % fid] = stack(liquid_frames(fid, True))
        anim["block/fluid/%s_still.png" % fid] = 3 if fid != "crude_oil" else 5
        anim["block/fluid/%s_flow.png" % fid] = 2 if fid != "crude_oil" else 4
        itm(fid + "_bucket", bucket(fid))
    for fid in GASES:
        out["block/fluid/%s_still.png" % fid] = stack(gas_frames(fid, False))
        out["block/fluid/%s_flow.png" % fid] = stack(gas_frames(fid, True))
        anim["block/fluid/%s_still.png" % fid] = 3
        anim["block/fluid/%s_flow.png" % fid] = 2
    for name, n in PIPE_TIERS.items():
        blk(name, fluid_pipe(TIERS[n], R("block/" + name)))
    # flanges in the pipe's own metal (stage colours read as iron on bronze and copper on aluminium)
    blk("bronze_fluid_pipe", fluid_pipe(G.Tier(1, "bronze", list(reversed(BRONZE)), [], "#D09E4E"), R("block/bronze_fluid_pipe")))
    blk("aluminum_fluid_pipe", fluid_pipe(G.Tier(3, "aluminum", list(reversed(G.ALUMINUM_BLOCK)), [], "#E2E7ED"),
                                          R("block/aluminum_fluid_pipe")))
    blk("fluid_pipe_extract", fluid_pipe_extract(R("fluid_pipe_extract")))
    for size in ("bronze", "steel", "titanium"):
        blk(f"{size}_fluid_tank_frame", tank_frame(size, R(f"{size}_tank_frame")))
        blk(f"{size}_fluid_tank_glass", tank_glass(size, R(f"{size}_tank_glass")))
        blk(f"{size}_fluid_tank_lid", tank_lid(size, R(f"{size}_tank_lid")))
    blk("boiler_firebox", boiler_firebox(R("boiler_firebox"), False))
    blk("boiler_firebox_on", boiler_firebox(R("boiler_firebox"), True))
    blk("boiler_drum", boiler_drum(R("boiler_drum")))
    blk("boiler_end", boiler_end(R("boiler_end")))
    blk("boiler_valve", boiler_valve())
    blk("boiler_brick", G.brick_wall(R("boiler_brick"), BRICK, C("#2A2420"), [[8, 8], [4, 8, 4], [8, 8], [4, 8, 4]]))
    blk("pump_body", pump_body(R("pump_body"), False))
    blk("pump_body_on", pump_body(R("pump_body"), True))
    blk("pump_motor", pump_motor(R("pump_motor")))
    blk("pump_top", pump_top(R("pump_top")))
    blk("fluid_steel_pipe", pipe_steel(R("fluid_steel_pipe")))
    blk("digester_tank", digester_tank(R("digester_tank")))
    blk("digester_dome", digester_dome(R("digester_dome")))
    blk("digester_hatch", digester_hatch(R("digester_hatch"), False))
    blk("digester_hatch_on", digester_hatch(R("digester_hatch"), True))
    blk("diesel_panel", diesel_panel(R("diesel_panel")))
    blk("diesel_front", diesel_front(R("diesel_front"), False))
    blk("diesel_front_on", diesel_front(R("diesel_front"), True))
    blk("diesel_top", diesel_top(R("diesel_top")))
    blk("diesel_exhaust", exhaust(R("diesel_exhaust")))
    blk("turbine_casing", turbine_casing(R("turbine_casing")))
    blk("turbine_front", turbine_front(R("turbine_front"), False))
    blk("turbine_top", turbine_top(R("turbine_top")))
    blk("turbine_rotor_blade", turbine_blade())
    blk("turbine_rotor_hub", turbine_hub())
    blk("wellhead", wellhead(R("wellhead"), False))
    blk("wellhead_on", wellhead(R("wellhead"), True))
    blk("derrick_base", derrick_base(R("derrick_base")))
    blk("derrick_base_side", derrick_side(R("derrick_base_side")))
    blk("pumpjack_paint", pumpjack_paint(R("pumpjack_paint")))
    blk("pumpjack_steel", pumpjack_steel(R("pumpjack_steel")))
    blk("pumpjack_weight", pumpjack_weight(R("pumpjack_weight")))
    blk("refinery_base", refinery_base(R("refinery_base"), False))
    blk("refinery_base_on", refinery_base(R("refinery_base"), True))
    blk("refinery_side", refinery_side(R("refinery_side")))
    blk("refinery_tower", tower(R("refinery_tower")))
    blk("refinery_tower_top", tower_top(R("refinery_tower_top")))
    blk("refinery_flare", flare())
    blk("fuelling_port_body", fuelling_body(R("fuelling_port_body")))
    blk("fuelling_port_top", fuelling_top(R("fuelling_port_top")))
    blk("fuelling_hose", hose())
    blk("fuelling_nozzle", nozzle(R("fuelling_nozzle")))
    blk("asphalt", asphalt(R("asphalt")))
    itm("plastic", plastic())
    itm("tar", tar())
    return out, anim


def write(only_missing):
    out, anim = build()
    for rel, t in out.items():
        path = TEX / rel
        if only_missing and path.exists():
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        t.image().save(path)
        if rel in anim:
            Path(str(path) + ".mcmeta").write_text('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n' % anim[rel])
    return out


def generate(ctx):
    try:
        write(only_missing=True)
    except ImportError:
        pass


def preview(path):
    from PIL import Image, ImageDraw
    out, _ = build()
    items = list(out.items())
    cols, s = 12, 5
    cell_w, cell_h = 16 * s + 10, 16 * s + 22
    img = Image.new("RGBA", (cols * cell_w + 10, ((len(items) + cols - 1) // cols) * cell_h + 10), (40, 42, 48, 255))
    d = ImageDraw.Draw(img)
    for i, (name, t) in enumerate(items):
        im = t.image()
        if im.height > im.width:
            im = im.crop((0, 0, im.width, im.width))
        im = im.resize((16 * s, 16 * s), Image.NEAREST)
        x, y = 10 + (i % cols) * cell_w, 10 + (i // cols) * cell_h
        img.alpha_composite(im, (x, y))
        d.text((x, y + 16 * s + 3), name.split("/")[-1][:-4][:16], fill=(225, 225, 225, 255))
    img.save(path)


if __name__ == "__main__":
    write(only_missing=False)
    preview(TOOLS / "fluids_textures_preview.png")
    print("wrote fluid textures and tools/fluids_textures_preview.png")

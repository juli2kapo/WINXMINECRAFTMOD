#!/usr/bin/env python3
"""Ships: the Bronze Cog (Bronze age sailing ship), the Motor Ship (Electric age) and the Orbital
Shuttle (Orbital age). Java: net.juli2kapo.factoryascent.ships (+ ships.client).

Models
  Each ship is drawn by ShipRenderer from standalone block models ("parts"). A part is built here
  in *ship pixels*: x across (+x = port, the left side when facing forward), y up (0 = the
  entity's feet, i.e. the bottom of its hit box), z forward (the bow). One block model may only
  span -16..32, so every part is cut into 48-px cells; each cell is written as
  block/ship/<part>_<n>.json and assets/factoryascent/ships/parts.json lists, per part, the cells
  and the offset (in ship pixels) at which to draw each one, plus whether the part is translucent.
  The renderer reads that manifest, so the Java side never mirrors coordinates by hand.
  Animated parts (sail, flag, rudder, propeller, radar, flames, legs) are separate parts turned or
  scaled about pivots that ShipRenderer declares (PIVOTS below lists the same points for the preview).

Resources: item models/definitions (flat icons), recipes, lang (en + es), three advancements with
custom triggers (set sail, motor ship, reach orbit in your own shuttle).

Run `python3 tools/features/ships.py --preview` to render tools/ships_models_preview.png.
"""
import importlib.util
import json
import math
import sys
from pathlib import Path

MOD = "factoryascent"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets" / MOD


def _load(name):
    spec = importlib.util.spec_from_file_location(f"ships_dep_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


SAT = _load("satellites")
El, cube, spin, turn, ring, disc = SAT.El, SAT.cube, SAT.spin, SAT.turn, SAT.ring, SAT.disc
_rot, _mul, _app, euler, I3 = SAT._rot, SAT._mul, SAT._app, SAT.euler, SAT.I3
DIRS = SAT.DIRS
CELL = 48

TEX = {k: f"{MOD}:block/{v}" for k, v in {
    "planks": "ship_cog_planks", "deck": "ship_cog_deck", "dark": "ship_cog_dark", "bronze": "ship_bronze",
    "sail": "ship_sail", "rope": "ship_rope", "flag": "ship_flag", "lantern": "ship_lantern", "lamp_off": "ship_lamp_off",
    "iron": "ship_iron", "hull": "ship_hull_steel", "red": "ship_hull_red", "boot": "ship_boot_top",
    "sdeck": "ship_steel_deck", "white": "ship_white", "windows": "ship_windows", "funnel": "ship_funnel",
    "prop": "ship_propeller", "lamp": "ship_lamp", "beam": "ship_beam", "glass": "ship_glass", "rubber": "ship_rubber",
    "tile": "ship_tile_white", "tile_black": "ship_tile_black", "foil": "ship_gold_foil", "engine": "ship_engine",
    "engine_in": "ship_engine_inner", "flame": "ship_flame", "flame_blue": "ship_flame_blue", "stripe": "ship_stripe",
    "hatch": "ship_hatch", "cockpit": "ship_cockpit", "red_light": "ship_light_red", "green_light": "ship_light_green",
    "white_light": "ship_light_white"}.items()}


# ============================================================ geometry helpers

def _sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def _add(a, b):
    return [a[i] + b[i] for i in range(3)]


def _scale(a, s):
    return [v * s for v in a]


def _dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _norm(a):
    n = math.sqrt(_dot(a, a))
    return _scale(a, 1 / n) if n > 1e-9 else a


def uv(u, v, w, h):
    return SAT._uv(u, v, w, h)


def plank(a0, a1, b0, b1, th, outer, inner=None, edge=None, out_dir=None, u=0, v=0, emissive=False, grow=0.3):
    """A box approximating the quad a0 (start, bottom) a1 (start, top) b0 (end, bottom) b1 (end, top),
    th thick, its outer face on the quad's plane. out_dir: a direction the outer face should point
    to (default: away from the ship's centre line). Textures: outer / inner / edge."""
    inner = inner or outer
    edge = edge or inner
    ma, mb = _scale(_add(a0, a1), 0.5), _scale(_add(b0, b1), 0.5)
    uax = _sub(mb, ma)
    length = math.sqrt(_dot(uax, uax)) + grow
    uax = _norm(uax)
    vax = _sub(_scale(_add(a1, b1), 0.5), _scale(_add(a0, b0), 0.5))
    vax = _sub(vax, _scale(uax, _dot(vax, uax)))
    height = math.sqrt(_dot(vax, vax)) + grow * 0.5
    vax = _norm(vax)
    nax = _norm(_cross(vax, uax))  # local +x; [n v u] is a proper rotation
    centre = _scale(_add(_add(a0, a1), _add(b0, b1)), 0.25)
    want = out_dir or [centre[0], 0, 0]
    east_out = _dot(nax, want) >= 0
    # outer surface on the plane, thickness inward
    centre = _sub(centre, _scale(nax, th / 2 if east_out else -th / 2))
    m = [[nax[0], vax[0], uax[0]], [nax[1], vax[1], uax[1]], [nax[2], vax[2], uax[2]]]
    frm = [centre[0] - th / 2, centre[1] - height / 2, centre[2] - length / 2]
    to = [centre[0] + th / 2, centre[1] + height / 2, centre[2] + length / 2]
    faces = {"east": (outer if east_out else inner, uv(u, v, length, height)),
             "west": (inner if east_out else outer, uv(u, v, length, height)),
             "up": (edge, uv(u, v, th, length)), "down": (edge, uv(u, v, th, length)),
             "north": (edge, uv(u, v, th, height)), "south": (edge, uv(u, v, th, height))}
    return El(frm, to, faces, m=m, origin=centre, emissive=emissive)


def rod(p0, p1, r, tex, emissive=False):
    """A square rod (2r thick) from p0 to p1."""
    d = _sub(p1, p0)
    length = math.sqrt(_dot(d, d))
    a = math.degrees(math.acos(max(-1, min(1, d[1] / length))))
    b = math.degrees(math.atan2(d[0], d[2]))
    c = _scale(_add(p0, p1), 0.5)
    e = cube([c[0] - r, c[1] - length / 2, c[2] - r], [c[0] + r, c[1] + length / 2, c[2] + r], tex, emissive=emissive)
    return e.transformed(_mul(_rot("y", b), _rot("x", a)), c)


def box(x0, y0, z0, x1, y1, z1, tex, u=0, v=0, emissive=False, only=None):
    return cube([min(x0, x1), min(y0, y1), min(z0, z1)], [max(x0, x1), max(y0, y1), max(z0, z1)], tex, u=u, v=v,
                emissive=emissive, only=only)


def tiled_box(x0, y0, z0, x1, y1, z1, tex, step=16, **kw):
    """A box split along its longest horizontal axes so no face is wider than `step` px."""
    out = []
    nx = max(1, math.ceil((x1 - x0) / step - 1e-6))
    nz = max(1, math.ceil((z1 - z0) / step - 1e-6))
    for i in range(nx):
        for k in range(nz):
            out.append(box(x0 + (x1 - x0) * i / nx, y0, z0 + (z1 - z0) * k / nz,
                           x0 + (x1 - x0) * (i + 1) / nx, y1, z0 + (z1 - z0) * (k + 1) / nz, tex, **kw))
    return out


def mirror_x(els):
    """The same elements reflected across x = 0 (rotations mirrored)."""
    out = []
    for e in els:
        f = [[-1, 0, 0], [0, 1, 0], [0, 0, 1]]
        m = _mul(_mul(f, e.m), f)
        faces = dict(e.faces)
        if "east" in e.faces or "west" in e.faces:
            faces["east"], faces["west"] = e.faces.get("west"), e.faces.get("east")
            faces = {k: v for k, v in faces.items() if v is not None}
        frm = [-e.to[0], e.frm[1], e.frm[2]]
        to = [-e.frm[0], e.to[1], e.to[2]]
        out.append(El(frm, to, faces, m=m, origin=[-e.origin[0], e.origin[1], e.origin[2]], emissive=e.emissive))
    return out


def loft_sides(stations, strakes, th=1.5, rail=None):
    """Hull sides: stations = [(z, [(y, halfwidth) bottom→top ...])], strakes = texture per band.
    Returns both sides (port built, starboard mirrored)."""
    els = []
    for k in range(len(stations) - 1):
        za, pa = stations[k]
        zb, pb = stations[k + 1]
        for j in range(len(pa) - 1):
            a0 = [pa[j][1], pa[j][0], za]
            a1 = [pa[j + 1][1], pa[j + 1][0], za]
            b0 = [pb[j][1], pb[j][0], zb]
            b1 = [pb[j + 1][1], pb[j + 1][0], zb]
            tex_out, tex_in = strakes[j] if isinstance(strakes[j], tuple) else (strakes[j], strakes[j])
            els.append(plank(a0, a1, b0, b1, th, tex_out, tex_in, u=(k * 7) % 16))
        if rail:
            ya, wa = pa[-1]
            yb, wb = pb[-1]
            els.append(plank([wa - th - 0.4, ya, za], [wa + 0.6, ya, za], [wb - th - 0.4, yb, zb], [wb + 0.6, yb, zb],
                             1.4, rail, out_dir=[0, 1, 0]))
    return els + mirror_x(els)


def bottom(stations, y, th, tex):
    """Flat bottom plates between the lowest points of the stations."""
    els = []
    for k in range(len(stations) - 1):
        za, pa = stations[k]
        zb, pb = stations[k + 1]
        w = min(pa[0][1], pb[0][1]) + 0.8
        els += tiled_box(-w, y - th, za, w, y, zb, tex)
    return els


def deck(stations, y, th, tex, z_from=-999, z_to=999, inset=1.2):
    els = []
    for k in range(len(stations) - 1):
        za, pa = stations[k]
        zb, pb = stations[k + 1]
        z0, z1 = max(za, z_from), min(zb, z_to)
        if z1 <= z0:
            continue

        def width_at(z):
            t = (z - za) / (zb - za)
            return _hw_at(pa, y) * (1 - t) + _hw_at(pb, y) * t
        w = min(width_at(z0), width_at(z1)) - inset
        if w <= 0.5:
            continue
        els += tiled_box(-w, y - th, z0, w, y, z1, tex)
    return els


def _hw_at(profile, y):
    """Half width of a station profile at height y (linear between points)."""
    for j in range(len(profile) - 1):
        (y0, w0), (y1, w1) = profile[j], profile[j + 1]
        if y0 <= y <= y1:
            return w0 + (w1 - w0) * (y - y0) / (y1 - y0) if y1 > y0 else w0
    return profile[-1][1] if y > profile[-1][0] else profile[0][1]


def rails(points, y0, y1, post_tex, rail_tex, every=8, r=0.5):
    """Posts and a top rail along a polyline of (x, z) at deck height y0."""
    els = []
    for (xa, za), (xb, zb) in zip(points, points[1:]):
        d = math.hypot(xb - xa, zb - za)
        n = max(1, round(d / every))
        for i in range(n):
            t = i / n
            x, z = xa + (xb - xa) * t, za + (zb - za) * t
            els.append(box(x - r, y0, z - r, x + r, y1, z + r, post_tex))
        els.append(rod([xa, y1, za], [xb, y1, zb], r * 1.1, rail_tex))
    x, z = points[-1]
    els.append(box(x - r, y0, z - r, x + r, y1, z + r, post_tex))
    return els


def tube_z(x, y, z0, z1, r0, r1, tex, n=8, inner=None, caps=False, th=0.6, **kw):
    """A frustum along +z (z0 radius r0 → z1 radius r1) centred on (x, y)."""
    lo, hi = min(z0, z1), max(z0, z1)
    rlo, rhi = (r0, r1) if z0 <= z1 else (r1, r0)
    els = ring(x, -y, lo, hi, rlo, rhi, tex, n=n, inner=inner, caps=caps, th=th, **kw)
    return turn(els, _rot("x", 90), [0, 0, 0])


def tube_y(x, z, y0, y1, r0, r1, tex, n=8, **kw):
    return ring(x, z, y0, y1, r0, r1, tex, n=n, **kw)


# ============================================================ Bronze Cog

COG_STATIONS = [  # z, profile bottom→top [(y, half width)]
    (-42, [(-3, 5), (6, 11), (22, 13)]),
    (-36, [(-4, 8), (6, 15), (20, 17)]),
    (-20, [(-4, 11), (6, 18), (18, 20)]),
    (0, [(-4, 12), (6, 19.5), (17, 21)]),
    (18, [(-4, 11), (6, 18.5), (18, 20)]),
    (31, [(-4, 7.5), (6, 14), (20, 16)]),
    (41, [(-3, 3), (7, 8), (23, 9.5)]),
    (48, [(-1, 0.6), (9, 1), (26, 1.2)]),
]
COG_DECK_Y = 7
COG_MAST = (0, 4)  # x, z of the mast axis
COG_SAIL_PIVOT = [0, 0, 4]
COG_RUDDER_PIVOT = [0, 0, -44]
COG_FLAG_PIVOT = [0, 100, 4]


def cog_hull():
    T = TEX
    els = loft_sides(COG_STATIONS, [("dark", "planks"), "planks"], th=1.6, rail="dark")
    els += bottom(COG_STATIONS, -3, 1.6, "dark")
    els += deck(COG_STATIONS, COG_DECK_Y, 1.2, "deck", z_from=-41, z_to=44)
    # stem post and keel, sternpost and transom
    els.append(plank([0, -4, 44], [0, 28, 49], [0, -4, 46], [0, 28, 51.5], 2.0, "dark", out_dir=[1, 0, 0]))
    els += tiled_box(-1, -5, -44, 1, -3, 46, "dark")
    els += tiled_box(-13, -3, -43.6, 13, 22.5, -41.8, "planks", step=14)
    els.append(box(-1.2, -5, -45, 1.2, 25, -43.2, "dark"))
    # wale (a dark band along the hull) is the lower strake; bronze bands at the bow and stern
    for z in (-41.5, 45.5):
        els.append(box(-2, 10, z - 0.8, 2, 20, z + 0.8, "bronze"))
    # stern castle: raised deck, front bulkhead with a door, railing, the helm bench
    els += tiled_box(-14.5, 17, -41, 14.5, 18.2, -26, "deck")
    els += tiled_box(-16, COG_DECK_Y, -26.8, 16, 18.2, -25.6, "planks", step=16)
    els.append(box(-3, COG_DECK_Y, -25.4, 3, 15, -25.2, "dark"))  # door
    els.append(box(1.5, 10.5, -25.1, 2.3, 11.3, -24.9, "bronze"))  # door ring
    els += rails([(15, -26), (13.5, -41)], 18.2, 24, "dark", "dark")
    els += rails([(-15, -26), (-13.5, -41)], 18.2, 24, "dark", "dark")
    els += rails([(-13.5, -41), (13.5, -41)], 18.2, 24, "dark", "dark")
    # forecastle platform and railing
    els += tiled_box(-12, 18.5, 30, 12, 19.7, 40, "deck")
    els.append(box(-12, COG_DECK_Y, 29.2, 12, 19.7, 30.4, "planks"))
    # mast (static), fighting top, shrouds
    mx, mz = COG_MAST
    for y in range(COG_DECK_Y, 98, 16):
        els.append(box(mx - 2, y, mz - 2, mx + 2, min(98, y + 16), mz + 2, "dark"))
    els.append(box(mx - 2.6, COG_DECK_Y, mz - 2.6, mx + 2.6, COG_DECK_Y + 3, mz + 2.6, "bronze"))  # mast step
    els += ring(mx, mz, 80, 82, 5.5, 5.5, "planks", n=8, th=1.2)
    els += disc(mx, mz, 80.6, 5.3, "deck", th=0.6)
    els.append(box(mx - 1.2, 98, mz - 1.2, mx + 1.2, 100, mz + 1.2, "bronze"))  # truck
    for side in (1, -1):
        for dz in (-6, 0, 6):
            els.append(rod([mx + side * 1.8, 78, mz], [side * 20.2, 18, mz + dz], 0.35, "rope"))
    els.append(rod([mx, 90, mz + 1.5], [0, 26, 50], 0.35, "rope"))  # forestay
    els.append(rod([mx, 90, mz - 1.5], [0, 24, -41], 0.35, "rope"))  # backstay
    # cargo chest on deck (the 27-slot compartment), bands and latch
    els.append(box(-7, COG_DECK_Y, -22, 7, COG_DECK_Y + 9, -13, "planks"))
    els.append(box(-7.3, COG_DECK_Y + 9, -22.3, 7.3, COG_DECK_Y + 10.5, -12.7, "dark"))
    for x in (-5, 4):
        els.append(box(x, COG_DECK_Y - 0.1, -22.2, x + 1, COG_DECK_Y + 10.6, -12.8, "bronze"))
    els.append(box(-1, COG_DECK_Y + 6.5, -12.8, 1, COG_DECK_Y + 9, -12.5, "bronze"))
    # benches: helm (aft) and passenger (fore)
    els.append(box(-7, COG_DECK_Y, -11, 7, COG_DECK_Y + 4, -7, "dark"))
    els.append(box(-7, COG_DECK_Y, 20, 7, COG_DECK_Y + 4, 24, "dark"))
    # lantern post at the stern
    els.append(box(-1, 18.2, -39.5, 1, 31, -37.5, "dark"))
    els.append(box(-1, 30, -38.5, 1, 31, -35, "dark"))
    # a coil of rope and a barrel
    els += ring(-12, 30, COG_DECK_Y, COG_DECK_Y + 1.5, 3, 3, "rope", n=8, th=1.5)
    els += ring(12, -5, COG_DECK_Y, COG_DECK_Y + 9, 3.2, 3.2, "planks", n=8, th=1)
    els += disc(12, -5, COG_DECK_Y + 9, 3, "deck", th=0.6)
    return els


def cog_lantern(lit):
    glass = "lantern" if lit else "lamp_off"
    els = [box(-2.4, 26, -36.8, 2.4, 26.6, -32.2, "bronze"),
           box(-2, 26.6, -36.4, 2, 30.4, -32.6, glass, emissive=lit),
           box(-2.4, 30.4, -36.8, 2.4, 31, -32.2, "bronze"),
           box(-0.6, 31, -35.1, 0.6, 32, -33.9, "bronze")]
    return els


def cog_sail(furled=False):
    """Yard, bellied sail and sheets, drawn about the mast axis (turned to trim to the wind)."""
    mx, mz = COG_MAST
    els = []
    y_yard = 76
    els += [box(-30 + 15 * i, y_yard - 1.2, mz + 2, -15 + 15 * i, y_yard + 1.2, mz + 4.4, "dark") for i in range(4)]
    els.append(box(-2.5, y_yard - 2, mz + 1.5, 2.5, y_yard + 2, mz + 5, "bronze"))  # parrel
    top, bot, half = y_yard - 1, 26, 28
    belly = 7.0
    nx, ny = 8, 4
    for i in range(nx):
        for j in range(ny):
            xa, xb = -half + 2 * half * i / nx, -half + 2 * half * (i + 1) / nx
            ya, yb = bot + (top - bot) * j / ny, bot + (top - bot) * (j + 1) / ny

            def z_of(x, y):
                t = (y - bot) / (top - bot)
                return mz + 4.6 + belly * (1 - (x / half) ** 2) * (0.35 + 0.65 * math.sin(math.pi * min(1, t * 0.9 + 0.1)))
            a0, a1 = [xa, ya, z_of(xa, ya)], [xa, yb, z_of(xa, yb)]
            b0, b1 = [xb, ya, z_of(xb, ya)], [xb, yb, z_of(xb, yb)]
            # plank() runs along a→b; here a/b are left/right edges, bottom/top as given
            els.append(plank(a0, a1, b0, b1, 0.5, "sail", "sail", "sail", out_dir=[0, 0, 1],
                             u=(i * 4) % 16, v=(j * 4) % 16, grow=0.25))
    # bolt rope along the foot and sheets down to the deck
    for side in (1, -1):
        els.append(rod([side * half, bot, mz + 4.6], [side * 18, 20, mz - 16], 0.3, "rope"))
    return els


def cog_rudder():
    px, _, pz = COG_RUDDER_PIVOT
    els = [box(-1, -5, pz - 9, 1, 14, pz - 0.5, "dark"),
           box(-1.3, 2, pz - 9.5, 1.3, 3, pz - 0.2, "bronze"),
           box(-1.3, 10, pz - 9.5, 1.3, 11, pz - 0.2, "bronze"),
           box(-0.8, 14, pz - 2, 0.8, 23, pz - 0.5, "dark")]
    return els


def cog_flag():
    px, py, pz = COG_FLAG_PIVOT
    # a swallow-tailed pennant streaming aft (-z) from the masthead
    els = [box(-0.3, py + 0.5, pz - 7, 0.3, py + 5.5, pz - 0.8, "flag"),
           box(-0.3, py + 1.5, pz - 13, 0.3, py + 4.5, pz - 7, "flag", u=6),
           box(-0.3, py + 3, pz - 17, 0.3, py + 4.5, pz - 13, "flag", u=2),
           box(-0.3, py + 1.5, pz - 17, 0.3, py + 3, pz - 14.5, "flag", u=2)]
    return els


# ============================================================ Motor Ship

MOTOR_STATIONS = [
    (-60, [(-5, 13), (2, 15.5), (4, 16), (18, 17.5)]),
    (-52, [(-6, 16), (2, 20), (4, 20.5), (17, 22)]),
    (-34, [(-6, 18.5), (2, 22.5), (4, 23), (16, 24)]),
    (0, [(-6, 19), (2, 23), (4, 23.5), (16, 24)]),
    (30, [(-6, 18), (2, 22.5), (4, 23), (17, 24)]),
    (46, [(-6, 12.5), (2, 18), (4, 18.8), (19, 21)]),
    (57, [(-5, 6), (2, 11.5), (4, 12.5), (22, 15)]),
    (66, [(-3, 0.8), (2, 2), (4, 2.5), (25, 4)]),
]
MOTOR_DECK_Y = 11
MOTOR_PROP_PIVOT = [0, -1.5, -61]
MOTOR_RUDDER_PIVOT = [0, 0, -62]
MOTOR_RADAR_PIVOT = [0, 66, -26]
MOTOR_LAMP = [0, 41, -13]  # searchlight lens centre
MOTOR_FUNNEL = (0, -44)


def motor_hull():
    els = loft_sides(MOTOR_STATIONS, ["red", "boot", ("hull", "white")], th=1.6, rail="white")
    els += bottom(MOTOR_STATIONS, -5, 1.6, "red")
    els += deck(MOTOR_STATIONS, MOTOR_DECK_Y, 1.2, "sdeck", z_from=-59, z_to=62)
    els.append(plank([0, -4, 64], [0, 25, 67], [0, -4, 66.5], [0, 25, 69], 1.6, "hull", out_dir=[1, 0, 0]))  # stem
    els += tiled_box(-16, -4, -61.2, 16, 18, -59.6, "hull", step=16)  # transom
    els.append(box(-1, -6, -62, 1, -4, 60, "iron"))  # keel bar
    # stern tube and skeg for the propeller shaft
    els += tube_z(0, -1.5, -59.5, -56, 2.4, 2.4, "iron", n=8, caps=True)
    # bulwark rails fore and aft
    els += rails([(22, 44), (21.5, 50), (14, 57), (4, 64)], 17, 23, "white", "white", every=7)
    els += rails([(-22, 44), (-21.5, 50), (-14, 57), (-4, 64)], 17, 23, "white", "white", every=7)
    els += rails([(-17, -58), (17, -58)], 18, 24, "white", "white", every=8)
    # bridge (wheelhouse): walls with windows, open glass front drawn by motor_glass
    z0, z1, y0, y1 = -36, -14, MOTOR_DECK_Y, 35
    for x in (-15, 13.6):
        els += [box(x, y0, z0 + 11 * i, x + 1.4, y1, z0 + 11 * (i + 1), "windows") for i in range(2)]
    els += [box(-15, y0, z0, 15, y1, z0 + 1.4, "windows", u=0)]
    els += [box(-15, y0, z1 - 1.4, -6, y1, z1, "white"), box(6, y0, z1 - 1.4, 15, y1, z1, "white"),
            box(-6, y0, z1 - 1.4, 6, 22, z1, "white")]
    els += tiled_box(-16.5, y1, z0 - 1.5, 16.5, y1 + 2, z1 + 2.5, "white")  # roof
    els.append(box(-16.6, y1 + 0.5, z1 + 2.5, 16.6, y1 + 1.5, z1 + 3, "hull"))  # roof lip
    # helm console inside, wheel
    els.append(box(-6, y0, z1 - 5, 6, 20, z1 - 1.4, "iron"))
    els.append(box(-1, 20, z1 - 4, 1, 22, z1 - 2, "iron"))
    wheel = ring(0, 0, -0.4, 0.4, 3.5, 3.5, "dark", n=8, th=0.8)
    wheel += [box(-3.3, -0.3, -0.3, 3.3, 0.3, 0.3, "dark"), box(-0.3, -0.3, -3.3, 0.3, 0.3, 3.3, "dark")]
    els += [e.transformed(_rot("x", 70), [0, 0, 0]).moved(0, 24.5, z1 - 3.2) for e in wheel]
    # searchlight on the roof
    lx, ly, lz = MOTOR_LAMP
    els.append(box(lx - 1, y1 + 2, lz - 5, lx + 1, ly - 2, lz - 3, "iron"))
    els += tube_z(lx, ly, lz - 7, lz - 0.5, 3.4, 3.4, "iron", n=8, caps=True)
    # mast with radar platform and a nav light
    mx, my, mz = MOTOR_RADAR_PIVOT
    els.append(box(mx - 1.2, y1 + 2, mz - 1.2, mx + 1.2, my, mz + 1.2, "white"))
    els.append(box(-7, 56, mz - 0.8, 7, 57.6, mz + 0.8, "white"))  # yard
    for x in (-6.5, 6.5):
        els.append(box(x - 0.8, 57.6, mz - 0.8, x + 0.8, 59.2, mz + 0.8, "red_light" if x > 0 else "green_light", emissive=True))
    els.append(box(mx - 0.8, 70.5, mz - 0.8, mx + 0.8, 72.2, mz + 0.8, "white_light", emissive=True))
    els.append(box(mx - 0.4, 67, mz - 0.4, mx + 0.4, 70.5, mz + 0.4, "iron"))
    # funnel with a cap, smoke comes out of the top (particles)
    fx, fz = MOTOR_FUNNEL
    bands = [(MOTOR_DECK_Y, 18, 9), (18, 25, 9), (25, 32, 9), (32, 35, 6), (35, 39, 9), (39, 45, 0)]
    for y0b, y1b, v0 in bands:
        els += ring(fx, fz, y0b, y1b, 6, 6, "funnel", n=8, v0=v0, th=0.8, inner="iron", caps=False)
    els += ring(fx, fz, 45, 46, 6.4, 6.4, "iron", n=8, th=1)
    els.append(box(-2, MOTOR_DECK_Y, fz + 6, 2, 20, fz + 9, "iron"))  # vent
    # engine room skylight and vents aft
    els.append(box(-9, MOTOR_DECK_Y, -58, 9, 15, -50, "white"))
    els.append(box(-8, 15, -57, 8, 16, -51, "glass"))
    # cargo hold: hatch coaming and cover, two crates
    els += tiled_box(-15, MOTOR_DECK_Y, 2, 15, 17, 38, "hull")
    els += tiled_box(-14, 17, 3, 14, 18.5, 37, "sdeck")
    els.append(box(-12, 18.5, 8, -1, 27, 19, "planks"))
    els.append(box(1, 18.5, 22, 12, 25, 33, "planks", u=4))
    els.append(box(-12.2, 22, 8, -0.8, 23, 19, "iron"))
    # foremast with the masthead light, anchor windlass, bollards
    els.append(box(-1, 22, 47, 1, 48, 49, "white"))
    els.append(box(-1, 48, 47, 1, 50, 49, "white_light", emissive=True))
    els.append(box(-5, MOTOR_DECK_Y, 52, 5, 15, 56, "iron"))
    els += tube_z(0, 13, 51, 57, 2, 2, "dark", n=6, caps=True)
    for x, z in ((12, 40), (-12, 40), (12, -46), (-12, -46)):
        els += ring(x, z, MOTOR_DECK_Y, MOTOR_DECK_Y + 3, 1.5, 1.5, "iron", n=6, th=1.5)
    # life ring on the bridge side
    lr = ring(0, 0, -0.6, 0.6, 3.2, 3.2, "stripe", n=8, th=1.4)
    els += [e.transformed(_rot("z", 90), [0, 0, 0]).moved(15.8, 26, -22) for e in lr]
    # passenger benches on the foredeck
    els.append(box(-12, MOTOR_DECK_Y, -8, -5, MOTOR_DECK_Y + 4, -4, "planks"))
    els.append(box(5, MOTOR_DECK_Y, -8, 12, MOTOR_DECK_Y + 4, -4, "planks"))
    els.append(box(-4, MOTOR_DECK_Y, 42, 4, MOTOR_DECK_Y + 4, 46, "planks"))
    return els


def motor_glass():
    """The bridge's front windows (translucent)."""
    z1, y1 = -14, 35
    return [box(-15, 22, z1 - 1, -6, y1, z1 - 0.3, "glass"), box(-6, 22, z1 - 1, 6, y1, z1 - 0.3, "glass"),
            box(6, 22, z1 - 1, 15, y1, z1 - 0.3, "glass")]


def motor_lamp(lit):
    lx, ly, lz = MOTOR_LAMP
    return [box(lx - 2.9, ly - 2.9, lz - 0.7, lx + 2.9, ly + 2.9, lz - 0.2, "lamp" if lit else "lamp_off", emissive=lit)]


def motor_beam():
    lx, ly, lz = MOTOR_LAMP
    els = tube_z(lx, ly, lz, lz + 96, 3, 22, "beam", n=8, th=0.2, inner="beam")
    for e in els:
        e.emissive = True
    return els


def motor_propeller():
    px, py, pz = MOTOR_PROP_PIVOT
    els = tube_z(px, py, pz + 0.5, pz - 2.5, 1.6, 1.0, "prop", n=6, caps=True)
    for k in range(4):
        blade = box(px - 0.4, py + 1, pz - 1.8, px + 0.4, py + 6.5, pz + 0.2, "prop")
        blade = blade.transformed(_rot("y", 25), [px, py + 3, pz - 0.8])
        els += spin([blade], k * 90, [px, py, pz], axis="z")
    return els


def motor_rudder():
    px, _, pz = MOTOR_RUDDER_PIVOT
    return [box(-0.8, -6, pz - 7, 0.8, 5, pz - 0.5, "red"), box(-0.6, 5, pz - 1.8, 0.6, 12, pz - 0.6, "iron")]


def motor_radar():
    px, py, pz = MOTOR_RADAR_PIVOT
    return [box(px - 1.2, py - 1, pz - 1.2, px + 1.2, py + 0.6, pz + 1.2, "iron"),
            box(px - 8, py + 0.6, pz - 0.6, px + 8, py + 2.2, pz + 0.6, "white"),
            box(px - 8, py + 0.9, pz + 0.6, px + 8, py + 1.9, pz + 1.0, "dark")]


# ============================================================ Orbital Shuttle

SH_BODY = [  # z, (half width, bottom y, top y)
    (-36, 11, 7, 28),
    (-24, 12.5, 6, 30),
    (4, 12.5, 6, 30),
    (22, 12, 6.5, 29),
    (32, 10.5, 8, 26),
    (40, 7.5, 10, 22),
    (46, 3.5, 12.5, 18),
    (49, 1, 14, 16),
]
SH_MAIN_ENGINES = [(-6.5, 16), (6.5, 16)]  # (x, y) of the two main bells
SH_ENGINE_EXIT = -44
SH_VTOL = [(-24, -14), (24, -14), (-9, 30), (9, 30)]  # (x, z) of the downward thrusters
SH_VTOL_EXIT = 2.5
SH_STROBE = [0, 51, -34]


def _loft_box(stations, top_tex, bottom_tex, side_tex, th=1.4):
    """A closed lofted body: top, bottom and both side panels between rectangular stations."""
    els = []
    for k in range(len(stations) - 1):
        za, wa, ba, ta = stations[k]
        zb, wb, bb, tb = stations[k + 1]
        # sides (port; mirrored below)
        els.append(plank([wa, ba, za], [wa, ta, za], [wb, bb, zb], [wb, tb, zb], th, side_tex, "tile_black",
                         u=(k * 5) % 16))
        # top and bottom, split in halves
        for s in (0, 1):
            x0a, x1a = (0, wa) if s else (-wa, 0)
            x0b, x1b = (0, wb) if s else (-wb, 0)
            els.append(plank([x0a, ta, za], [x1a, ta, za], [x0b, tb, zb], [x1b, tb, zb], th, top_tex, "tile_black",
                             out_dir=[0, 1, 0]))
            els.append(plank([x0a, ba, za], [x1a, ba, za], [x0b, bb, zb], [x1b, bb, zb], th, bottom_tex, "tile_black",
                             out_dir=[0, -1, 0]))
    sides = [e for i, e in enumerate(els) if i % 5 == 0]
    return els + mirror_x(sides)


def shuttle_body():
    els = _loft_box(SH_BODY, "tile", "tile_black", "tile")
    # tail cap and engine plate
    za, wa, ba, ta = SH_BODY[0]
    els += tiled_box(-wa, ba, za - 1.2, wa, ta, za, "tile_black", step=12)
    # orange stripe along the sides
    for k in range(3):
        z0, z1 = SH_BODY[k][0], SH_BODY[k + 1][0]
        w = min(SH_BODY[k][1], SH_BODY[k + 1][1])
        els.append(box(w, 17, z0 + 0.5, w + 0.3, 20, z1 - 0.5, "stripe"))
        els.append(box(-w - 0.3, 17, z0 + 0.5, -w, 20, z1 - 0.5, "stripe"))
    # cargo bay doors behind the canopy
    els += tiled_box(-9, 30, -30, 9, 31, -10, "hatch")
    els.append(box(-0.3, 30, -30, 0.3, 31.3, -10, "stripe"))
    # delta wings, built as strips from the root to the tip
    for side in (1, -1):
        n = 6
        for i in range(n):
            xa = 12 + (36 - 12) * i / n
            xb = 12 + (36 - 12) * (i + 1) / n
            front = 12 - (12 + 22) * (i + 0.5) / n  # leading edge sweeps back
            rear = -36
            t = 3.2 - 1.8 * i / n
            y0 = 9
            x0, x1 = (xa, xb) if side > 0 else (-xb, -xa)
            for z0, z1 in ((rear, (rear + front) / 2), ((rear + front) / 2, front)):
                els.append(box(x0, y0, z0, x1, y0 + t, z1, {"up": "tile", "down": "tile_black", "north": "tile_black",
                                                            "south": "tile", "east": "tile", "west": "tile"}))
        # elevon and wingtip light
        xt = 36 * side
        els.append(box(xt - 1.5 * side, 9, -36, xt, 11.4, -30, "tile"))
        els.append(box(xt - 0.2 * side if side < 0 else xt - 0.2, 9.2, -33, xt + (1.2 if side > 0 else -1.2), 11.2, -31,
                       "red_light" if side > 0 else "green_light", emissive=True))
        els.append(box(12 * side - (6 if side > 0 else -6), 9, -37.5, 12 * side + (10 if side > 0 else -10), 10.5, -36,
                       "stripe"))
    # vertical tail fin, swept
    for i in range(5):
        y0 = 29 + i * 4.4
        z0 = -36 + i * 3.2
        els.append(box(-1, y0, z0, 1, y0 + 4.6, z0 + 14 - i * 1.6, {"east": "tile", "west": "tile", "up": "tile",
                                                                   "down": "tile", "north": "tile_black", "south": "stripe"}))
    els.append(box(-0.8, 51, -34.5, 0.8, 52, -26, "tile"))
    # main engine bells and the heat shield skirt
    for x, y in SH_MAIN_ENGINES:
        els += tube_z(x, y, -36, SH_ENGINE_EXIT, 3, 5, "engine", n=8, inner="engine_in", th=0.5)
        els += tube_z(x, y, -36.5, -35, 3.2, 3.2, "iron", n=8, caps=True)
    els += tube_z(0, 24, -36, -38.5, 2.4, 2.4, "engine", n=6, inner="engine_in", th=0.4)  # APU
    # downward VTOL thrusters (pods under the wings and the nose)
    for x, z in SH_VTOL:
        els += ring(x, z, SH_VTOL_EXIT, 8, 3.4, 2.4, "engine", n=8, inner="engine_in", th=0.5)
        els.append(box(x - 2.5, 7.5, z - 2.5, x + 2.5, 10, z + 2.5, "tile_black"))
    # RCS blocks on the nose
    for side in (1, -1):
        els.append(box(side * 9.5, 20, 30, side * 11.2, 23, 34, "iron"))
    # cockpit floor, seats and console (seen through the canopy)
    els += tiled_box(-10, 11, -8, 10, 12, 30, "iron")
    for z in (0, 15):
        els.append(box(-4, 12, z - 3, 4, 15, z + 3, "rubber"))
        els.append(box(-4, 15, z - 4, 4, 25, z - 2.5, "rubber"))
    els.append(box(-9, 12, 24, 9, 22, 28, {"up": "cockpit", "south": "iron", "north": "cockpit", "east": "iron",
                                           "west": "iron", "down": "iron"}))
    # gold foil on the service module (aft belly)
    els.append(box(-11.5, 6.8, -34, 11.5, 7.6, -24, "foil"))
    # landing light housings under the nose
    for side in (1, -1):
        els.append(box(side * 4 - 1.5, 7.2, 38, side * 4 + 1.5, 8.5, 41, "iron"))
    return els


def shuttle_canopy():
    """The canopy over the two seats (translucent)."""
    stations = [(-9, 9.5, 30, 30.5), (-2, 10, 30, 36), (14, 10, 30, 37.5), (24, 9.5, 29, 35), (31, 8, 26, 27)]
    els = []
    for k in range(len(stations) - 1):
        za, wa, ba, ta = stations[k]
        zb, wb, bb, tb = stations[k + 1]
        els.append(plank([wa, ba, za], [wa * 0.75, ta, za], [wb, bb, zb], [wb * 0.75, tb, zb], 0.4, "glass"))
        els.append(plank([-wa, ba, za], [-wa * 0.75, ta, za], [-wb, bb, zb], [-wb * 0.75, tb, zb], 0.4, "glass"))
        els.append(plank([-wa * 0.75, ta, za], [wa * 0.75, ta, za], [-wb * 0.75, tb, zb], [wb * 0.75, tb, zb], 0.4,
                         "glass", out_dir=[0, 1, 0]))
    els.append(plank([-9.5, 30, -9], [9.5, 30, -9], [-7, 36, -9], [7, 36, -9], 0.4, "glass", out_dir=[0, 0, -1]))
    return els


def shuttle_frame():
    """Canopy frame (opaque)."""
    els = []
    for z, w, top in ((-2, 10, 36), (14, 10, 37.5), (24, 9.5, 35)):
        els.append(rod([w, 30, z], [w * 0.75, top, z], 0.5, "tile"))
        els.append(rod([-w, 30, z], [-w * 0.75, top, z], 0.5, "tile"))
        els.append(rod([-w * 0.75, top + 0.3, z], [w * 0.75, top + 0.3, z], 0.5, "tile"))
    return els


def shuttle_legs():
    els = []
    for x, z, h in ((13, -20, 7), (-13, -20, 7), (0, 34, 9)):
        els.append(rod([x * 0.8, 7 + (h - 7), z], [x, 1.2, z + (2 if z < 0 else -2)], 0.8, "iron"))
        els.append(rod([x * 0.8, 7 + (h - 7), z + (5 if z < 0 else -5)], [x, 1.2, z + (2 if z < 0 else -2)], 0.5, "dark"))
        els.append(box(x - 2.5, 0, z - 0.5, x + 2.5, 1.2, z + 4.5 if z < 0 else z + 0.5, "iron") if z < 0
                   else box(x - 2.5, 0, z - 4.5, x + 2.5, 1.2, z + 0.5, "iron"))
    return els


def shuttle_flame_main():
    els = []
    for x, y in SH_MAIN_ENGINES:
        els += tube_z(x, y, SH_ENGINE_EXIT, SH_ENGINE_EXIT - 14, 4.4, 5.2, "flame", n=8, th=0.2, inner="flame")
        els += tube_z(x, y, SH_ENGINE_EXIT - 14, SH_ENGINE_EXIT - 34, 5.2, 1, "flame", n=8, th=0.2, inner="flame")
    for e in els:
        e.emissive = True
    return els


def shuttle_flame_vtol():
    els = []
    for x, z in SH_VTOL:
        els += ring(x, z, SH_VTOL_EXIT - 8, SH_VTOL_EXIT, 3.6, 3.0, "flame_blue", n=8, th=0.2, inner="flame_blue", caps=False)
        els += ring(x, z, SH_VTOL_EXIT - 22, SH_VTOL_EXIT - 8, 0.8, 3.6, "flame_blue", n=8, th=0.2, inner="flame_blue", caps=False)
    for e in els:
        e.emissive = True
    return els


def shuttle_strobe():
    x, y, z = SH_STROBE
    return [box(x - 0.9, y, z - 0.9, x + 0.9, y + 1.8, z + 0.9, "white_light", emissive=True)]


def shuttle_landing_lights(lit):
    els = []
    for side in (1, -1):
        els.append(box(side * 4 - 1.2, 6.8, 38.4, side * 4 + 1.2, 7.2, 40.6, "lamp" if lit else "lamp_off", emissive=lit))
    return els


# ============================================================ parts

# part -> (elements, translucent)
def parts():
    return {
        "cog_hull": (cog_hull(), False),
        "cog_sail": (cog_sail(), False),
        "cog_rudder": (cog_rudder(), False),
        "cog_flag": (cog_flag(), False),
        "cog_lantern_on": (cog_lantern(True), False),
        "cog_lantern_off": (cog_lantern(False), False),
        "motor_hull": (motor_hull(), False),
        "motor_glass": (motor_glass(), True),
        "motor_lamp_on": (motor_lamp(True), False),
        "motor_lamp_off": (motor_lamp(False), False),
        "motor_beam": (motor_beam(), True),
        "motor_propeller": (motor_propeller(), False),
        "motor_rudder": (motor_rudder(), False),
        "motor_radar": (motor_radar(), False),
        "shuttle_body": (shuttle_body(), False),
        "shuttle_frame": (shuttle_frame(), False),
        "shuttle_canopy": (shuttle_canopy(), True),
        "shuttle_legs": (shuttle_legs(), False),
        "shuttle_flame_main": (shuttle_flame_main(), True),
        "shuttle_flame_vtol": (shuttle_flame_vtol(), True),
        "shuttle_strobe": (shuttle_strobe(), False),
        "shuttle_landing_on": (shuttle_landing_lights(True), False),
        "shuttle_landing_off": (shuttle_landing_lights(False), False),
    }


# Pivots the renderer turns animated parts about (ship pixels). Mirrored in ShipRenderer.
PIVOTS = {"cog_sail": COG_SAIL_PIVOT, "cog_rudder": COG_RUDDER_PIVOT, "cog_flag": COG_FLAG_PIVOT,
          "motor_propeller": MOTOR_PROP_PIVOT, "motor_rudder": MOTOR_RUDDER_PIVOT, "motor_radar": MOTOR_RADAR_PIVOT,
          "shuttle_flame_main": [0, 16, SH_ENGINE_EXIT], "shuttle_flame_vtol": [0, SH_VTOL_EXIT, 0]}


def _bounds(e):
    return e.frm, e.to


def split_cells(name, els):
    """Groups elements into 48-px cells. Cell k (per axis) covers ship px [48k - 24, 48k + 24), drawn
    as model px -16..32, so model px 0 sits at ship px 48k - 8. Returns {k: (elements in model px,
    offset of model px 0 in ship px)}."""
    cells = {}
    for e in els:
        c = [(e.frm[i] + e.to[i]) / 2 for i in range(3)]
        key = tuple(math.floor((c[i] + 24) / CELL) for i in range(3))
        placed = None
        for dk in [(0, 0, 0)] + [(a, b, d) for a in (-1, 0, 1) for b in (-1, 0, 1) for d in (-1, 0, 1)]:
            k = tuple(key[i] + dk[i] for i in range(3))
            lo = [e.frm[i] - (CELL * k[i] - 8) for i in range(3)]
            hi = [e.to[i] - (CELL * k[i] - 8) for i in range(3)]
            if all(-16 - 1e-6 <= lo[i] and hi[i] <= 32 + 1e-6 for i in range(3)):
                placed = k
                break
        if placed is None:
            raise ValueError(f"{name}: element does not fit any cell: {e.frm} {e.to}")
        cells.setdefault(placed, []).append(e)
    out = {}
    for k, group in cells.items():
        offset = [CELL * k[i] - 8 for i in range(3)]
        out[k] = ([g.moved(*[-o for o in offset]) for g in group], offset)
    return out


def write_parts(ctx):
    manifest = {}
    for name, (els, translucent) in parts().items():
        cells = split_cells(name, els)
        entries = []
        for i, (k, (shifted, offset)) in enumerate(sorted(cells.items())):
            used = {t for e in shifted for (t, _) in e.faces.values()}
            tex = {t: TEX[t] for t in used}
            particle = next(iter(sorted(used)))
            ctx.block_model(f"ship/{name}_{i}", {"ambientocclusion": False, "textures": {**tex, "particle": TEX[particle]},
                                                 "elements": [e.json() for e in shifted]})
            entries.append({"model": f"{MOD}:block/ship/{name}_{i}", "offset": [round(v, 4) for v in offset]})
        manifest[name] = {"translucent": translucent, "cells": entries}
    ctx.write(ASSETS / "ships" / "parts.json", manifest)
    return manifest


# ============================================================ items, recipes, lang, advancements

SHIPS = ["bronze_cog", "motor_ship", "shuttle"]

LANG = [
    ("item.factoryascent.bronze_cog", "Bronze Cog", "Coca de bronce"),
    ("item.factoryascent.motor_ship", "Motor Ship", "Barco a motor"),
    ("item.factoryascent.shuttle", "Orbital Shuttle", "Transbordador orbital"),
    ("entity.factoryascent.bronze_cog", "Bronze Cog", "Coca de bronce"),
    ("entity.factoryascent.motor_ship", "Motor Ship", "Barco a motor"),
    ("entity.factoryascent.shuttle", "Orbital Shuttle", "Transbordador orbital"),

    ("tooltip.factoryascent.bronze_cog",
     "A sailing ship: 2 seats and a 27-slot hold. Wind drives it: fastest with the wind on the beam, "
     "slow straight into it; storms blow harder.",
     "Un velero: 2 asientos y una bodega de 27 casillas. Lo mueve el viento: va más rápido con el viento de "
     "costado y lento contra él; las tormentas soplan más fuerte."),
    ("tooltip.factoryascent.motor_ship",
     "4 seats, a 54-slot hold, headlight and horn. Runs on furnace fuel or FE (put coal or a charged item in its "
     "power slot, or charge it with FE).",
     "4 asientos, bodega de 54 casillas, faro y bocina. Funciona con combustible de horno o FE (pon carbón o un "
     "objeto cargado en su casilla de energía, o cárgalo con FE)."),
    ("tooltip.factoryascent.shuttle",
     "A small spacecraft for 2. Burns Rocket Fuel. Climb above the sky to reach orbit; dive back down to re-enter. "
     "The cabin is sealed: no oxygen needed while seated.",
     "Una pequeña nave para 2. Quema combustible de cohete. Sube por encima del cielo para llegar a la órbita; "
     "desciende para reentrar. La cabina es hermética: no necesitas oxígeno mientras vas sentado."),
    ("tooltip.factoryascent.ship.place_water", "Right-click on water to launch it.",
     "Clic derecho sobre el agua para botarlo."),
    ("tooltip.factoryascent.ship.place_ground", "Right-click on the ground (or a Launch Pad) to place it.",
     "Clic derecho en el suelo (o una plataforma de lanzamiento) para colocarlo."),
    ("tooltip.factoryascent.ship.cargo", "Cargo: %s stacks", "Carga: %s pilas"),
    ("tooltip.factoryascent.ship.fuel", "Fuel: %s", "Combustible: %s"),
    ("tooltip.factoryascent.ship.energy", "Energy: %s FE", "Energía: %s FE"),
    ("tooltip.factoryascent.ship.controls",
     "W/S throttle, A/D steer, E hold & helm, H horn, J lights, Shift to leave",
     "W/S acelerar, A/D girar, E bodega y timón, H bocina, J luces, Shift para bajar"),
    ("tooltip.factoryascent.shuttle.controls",
     "W/S thrust, A/D yaw, Space up, Shift down, E cockpit, J lights, K open the hatch",
     "W/S empuje, A/D girar, Espacio subir, Shift bajar, E cabina, J luces, K abrir la escotilla"),

    ("message.factoryascent.ship.need_water", "Ships must be launched on open water",
     "Los barcos se botan en aguas abiertas"),
    ("message.factoryascent.ship.no_room", "Not enough room here", "No hay espacio aquí"),
    ("message.factoryascent.ship.no_fuel", "Out of fuel", "Sin combustible"),
    ("message.factoryascent.ship.no_power", "No power: put fuel or a charged item in the power slot",
     "Sin energía: pon combustible o un objeto cargado en la casilla de energía"),
    ("message.factoryascent.shuttle.to_orbit", "Leaving the atmosphere…", "Saliendo de la atmósfera…"),
    ("message.factoryascent.shuttle.reentry", "Re-entry! Hold on…", "¡Reentrada! Agárrate…"),
    ("message.factoryascent.shuttle.no_orbit", "The orbit dimension isn't available on this server",
     "La dimensión de la órbita no está disponible en este servidor"),
    ("message.factoryascent.shuttle.docked", "Docked: refuelling from containers next to the pad",
     "Acoplado: repostando de los contenedores junto a la plataforma"),
    ("message.factoryascent.shuttle.hatch_locked", "Land first (or stop in orbit) to open the hatch",
     "Aterriza primero (o detente en órbita) para abrir la escotilla"),
    ("message.factoryascent.shuttle.shift_blocked", "Shift = descend. Land to leave, or press %s to open the hatch",
     "Shift = descender. Aterriza para bajar o pulsa %s para abrir la escotilla"),

    ("gui.factoryascent.ship.hold", "Hold", "Bodega"),
    ("gui.factoryascent.ship.helm", "Helm", "Timón"),
    ("gui.factoryascent.ship.cockpit", "Cockpit", "Cabina"),
    ("gui.factoryascent.ship.speed", "Speed %s km/h", "Velocidad %s km/h"),
    ("gui.factoryascent.ship.heading", "Heading %s° %s", "Rumbo %s° %s"),
    ("gui.factoryascent.ship.wind", "Wind %s from %s", "Viento %s del %s"),
    ("gui.factoryascent.ship.sail_trim", "Sails: %s%% of wind", "Velas: %s%% del viento"),
    ("gui.factoryascent.ship.power", "Power slot", "Casilla de energía"),
    ("gui.factoryascent.ship.power_tip", "Coal, coke, wood… or any FE item (drained into the battery)",
     "Carbón, coque, madera… o cualquier objeto con FE (se descarga en la batería)"),
    ("gui.factoryascent.ship.fuel_slot", "Fuel", "Combustible"),
    ("gui.factoryascent.ship.fuel_tip", "Rocket Fuel: 1 = %s units", "Combustible de cohete: 1 = %s unidades"),
    ("gui.factoryascent.ship.battery", "Battery %s / %s FE", "Batería %s / %s FE"),
    ("gui.factoryascent.ship.tank", "Tank %s / %s", "Tanque %s / %s"),
    ("gui.factoryascent.ship.altitude", "Altitude %s", "Altitud %s"),
    ("gui.factoryascent.ship.vspeed", "Climb %s m/s", "Ascenso %s m/s"),
    ("gui.factoryascent.ship.destination", "Destination: %s", "Destino: %s"),
    ("gui.factoryascent.ship.dest_orbit", "Orbit (climb to %s)", "Órbita (sube a %s)"),
    ("gui.factoryascent.ship.dest_earth", "Earth (descend below %s)", "Tierra (baja de %s)"),
    ("gui.factoryascent.ship.dest_none", "—", "—"),
    ("gui.factoryascent.ship.lights", "Lights: %s", "Luces: %s"),
    ("gui.factoryascent.ship.on", "on", "encendidas"),
    ("gui.factoryascent.ship.off", "off", "apagadas"),
    ("gui.factoryascent.ship.state.landed", "Landed", "En tierra"),
    ("gui.factoryascent.ship.state.docked", "Docked", "Acoplado"),
    ("gui.factoryascent.ship.state.flying", "Flying", "En vuelo"),
    ("gui.factoryascent.ship.state.orbit", "In orbit", "En órbita"),
    ("gui.factoryascent.ship.state.reentry", "RE-ENTRY", "REENTRADA"),
    ("gui.factoryascent.ship.state.afloat", "Afloat", "A flote"),
    ("gui.factoryascent.ship.state.aground", "Aground", "Encallado"),
    ("gui.factoryascent.ship.horn", "Horn", "Bocina"),
    ("gui.factoryascent.ship.toggle_lights", "Lights", "Luces"),
    ("gui.factoryascent.ship.dir.n", "N", "N"), ("gui.factoryascent.ship.dir.ne", "NE", "NE"),
    ("gui.factoryascent.ship.dir.e", "E", "E"), ("gui.factoryascent.ship.dir.se", "SE", "SE"),
    ("gui.factoryascent.ship.dir.s", "S", "S"), ("gui.factoryascent.ship.dir.sw", "SW", "SO"),
    ("gui.factoryascent.ship.dir.w", "W", "O"), ("gui.factoryascent.ship.dir.nw", "NW", "NO"),
    ("gui.factoryascent.ship.wind.calm", "calm", "calma"),
    ("gui.factoryascent.ship.wind.breeze", "breeze", "brisa"),
    ("gui.factoryascent.ship.wind.strong", "strong", "fuerte"),
    ("gui.factoryascent.ship.wind.gale", "gale", "temporal"),

    ("key.category.factoryascent.ships", "Factory Ascent: Ships", "Factory Ascent: Barcos"),
    ("key.factoryascent.ship_horn", "Ship: horn", "Barco: bocina"),
    ("key.factoryascent.ship_lights", "Ship: lights on/off", "Barco: encender/apagar luces"),
    ("key.factoryascent.ship_hatch", "Shuttle: open the hatch (leave)", "Transbordador: abrir la escotilla (salir)"),
]


def advancements(ctx):
    def adv(key, parent, icon, criteria, title_en, desc_en, title_es, desc_es, frame="task"):
        ctx.lang(f"advancements.{MOD}.{key}.title", title_en, title_es)
        ctx.lang(f"advancements.{MOD}.{key}.description", desc_en, desc_es)
        ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
            "parent": f"{MOD}:{parent}",
            "display": {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
                        "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame},
            "criteria": criteria, "requirements": [list(criteria)]})

    def riding(ship):
        return {"trigger": "minecraft:started_riding", "conditions": {"player": [{
            "condition": "minecraft:entity_properties", "entity": "this",
            "predicate": {"vehicle": {"type": f"{MOD}:{ship}"}}}]}}

    adv("bronze_set_sail", "age_bronze", "bronze_cog", {"sail": riding("bronze_cog")}, "Set Sail",
        "Launch a Bronze Cog on open water and climb aboard. Watch the wind!", "¡A toda vela!",
        "Bota una coca de bronce en aguas abiertas y súbete. ¡Atento al viento!")
    adv("electric_motor_ship", "electric_power", "motor_ship", {"ride": riding("motor_ship")}, "Full Steam Ahead",
        "Take the helm of a Motor Ship", "Avante toda", "Toma el timón de un barco a motor", frame="goal")
    adv("orbital_shuttle_orbit", "age_orbital", "shuttle", {"orbit": {"trigger": "minecraft:location", "conditions": {
        "player": [{"condition": "minecraft:entity_properties", "entity": "this",
                    "predicate": {"location": {"dimension": f"{MOD}:orbit"}, "vehicle": {"type": f"{MOD}:shuttle"}}}]}}},
        "We Have Liftoff", "Fly your own Orbital Shuttle into orbit", "Tenemos despegue",
        "Lleva tu propio transbordador orbital hasta la órbita", frame="challenge")


def recipes(ctx):
    ctx.shaped("bronze_cog", [" W ", "SCL", "PBP"],
               {"W": "#minecraft:wool", "S": "#c:rods/wooden", "C": "minecraft:chest", "L": "minecraft:lantern",
                "P": "#c:plates/bronze", "B": "#minecraft:boats"}, "bronze_cog", category="misc")
    ctx.shaped("motor_ship", ["LCH", "PMP", "PXP"],
               {"L": "minecraft:redstone_lamp", "C": "basic_circuit", "H": "minecraft:note_block",
                "P": "#c:plates/steel", "M": "motor", "X": "bronze_cog"}, "motor_ship", category="misc")
    ctx.shaped("shuttle", ["TGT", "AOA", "HCH"],
               {"T": "#c:plates/titanium", "G": "#c:glass_blocks", "A": "advanced_circuit",
                "O": "orbital_targeting_core", "H": "heating_coil", "C": "minecraft:chest"}, "shuttle", category="misc")


def generate(ctx):
    write_parts(ctx)
    for name in SHIPS:
        ctx.flat_item(name)
    recipes(ctx)
    advancements(ctx)
    for key, en, es in LANG:
        ctx.lang(key, en, es)


# ============================================================ preview

def _textures():
    from PIL import Image
    return {k: Image.open(ASSETS / "textures" / "block" / f"{v.split('/')[-1]}.png").convert("RGBA") for k, v in TEX.items()}


def preview(path):
    from PIL import Image, ImageDraw
    drill = _load("drill")
    tex = _textures()
    bg = (40, 44, 52, 255)
    P = parts()

    def quads(names, xform=None):
        els = []
        for n in names:
            e = P[n][0]
            if xform and n in xform:
                e = xform[n](e)
            els += e
        return SAT._quads(els, tex)

    sail_turn = {"cog_sail": lambda e: spin(e, 25, COG_SAIL_PIVOT)}
    scenes = [
        ("Bronze Cog", ["cog_hull", "cog_sail", "cog_rudder", "cog_flag", "cog_lantern_on"], sail_turn, 45, 45),
        ("Motor Ship", ["motor_hull", "motor_glass", "motor_lamp_on", "motor_beam", "motor_propeller", "motor_rudder",
                        "motor_radar"], None, 70, 50),
        ("Orbital Shuttle", ["shuttle_body", "shuttle_frame", "shuttle_canopy", "shuttle_legs", "shuttle_flame_main",
                             "shuttle_flame_vtol", "shuttle_strobe", "shuttle_landing_on"], None, 80, 25),
    ]
    panels = []
    for title, names, xf, scale_px, ycen in scenes:
        q = quads(names, xf)
        for yaw, pitch in ((-140, 20), (-35, 15)):
            m = _mul(_rot("x", pitch), _rot("y", yaw))
            img = drill._render_mixed([(q, lambda p, m=m, ycen=ycen: _app(m, [p[0] / 16, (p[1] - ycen) / 16, p[2] / 16]))],
                                      lambda v, s=scale_px: (320 + v[0] * s, 240 - v[1] * s), (640, 480), bg)
            panels.append((f"{title} {yaw}/{pitch}", img))
    out = Image.new("RGBA", (640 * 2 + 30, (480 + 20) * 3 + 10), (25, 25, 30, 255))
    d = ImageDraw.Draw(out)
    for i, (name, im) in enumerate(panels):
        x = 10 + (i % 2) * 650
        y = 10 + (i // 2) * 500
        d.text((x, y), name, fill=(230, 230, 230, 255))
        out.alpha_composite(im, (x, y + 14))
    out.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        i = sys.argv.index("--preview")
        preview(Path(sys.argv[i + 1]) if len(sys.argv) > i + 1 else ROOT / "tools" / "ships_models_preview.png")
    else:
        print(__doc__)

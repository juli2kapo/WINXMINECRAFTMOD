#!/usr/bin/env python3
"""3D satellites (Survey, Uplink, Guardian) and the launch vehicle drawn on the Launch Pad.

Satellite items: a foil-wrapped bus with deployed solar wings on booms and the type's instrument:
  Survey    gold foil bus, a black telescope pointing down (lens at the bottom), star tracker on top
  Uplink    silver foil bus, a big white parabolic dish tilted up on a mast, whip antennas
  Guardian  armoured gunmetal bus with red stripes, a twin-barrel turret on top and a pod of four
            red-tipped interceptors underneath
Item definition: flat icon in the GUI (items/*.json select on display_context, as the size rays),
the 3D model everywhere else. block/satellite_<kind>_stowed is the same satellite with its wings
folded, drawn inside the rocket's fairing.

Launch vehicle (OrbitalContentClient.RocketRenderer): all octagonal, about 8 blocks tall, built from
standalone block models because one model may only span 48 px:
  lv_stage_lower      engines (five bells under a boat tail), fins, first stage tank   y   0..47.5
  lv_stage_upper      first stage top, grid fins, black interstage, second stage       y  47.5..92
  lv_adapter          payload adapter cone and plate the payload sits on               y  86..96
  lv_fairing_{a,b}    the two fairing halves, clear window band (translucent), jettisoned y  95..130
  lv_kill_vehicle     the anti-satellite missile's kill vehicle (no fairing)           y  88..124
  lv_plume            exhaust plume (translucent, emissive) under the engines
  *_asat              gunmetal livery of the stage models (texture overrides)
Model coordinates are rocket pixels shifted so each model fits in -16..32; SEGMENT_BASE says where
each model's y = 0 sits (the Java renderer mirrors it).

Element rotations use the 26.x Euler form {"x","y","z"} where one axis is not enough.
Run `python3 tools/features/satellites.py --preview` to render tools/satellite_models_preview.png.
"""
import importlib.util
import math
import sys
from pathlib import Path

MOD = "factoryascent"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets" / MOD
DIRS = ("north", "south", "east", "west", "up", "down")


# ============================================================ geometry

def _rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == "y":
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _app(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def _tr(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


I3 = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def euler(x=0, y=0, z=0):
    """The matrix of a {"x","y","z"} element rotation: Rz * Ry * Rx (JOML rotationZYX)."""
    return _mul(_mul(_rot("z", z), _rot("y", y)), _rot("x", x))


class El:
    """A cuboid: from/to, faces {dir: (texture, uv)}, rotation matrix m about origin."""

    def __init__(self, frm, to, faces, m=None, origin=None, emissive=False):
        self.frm, self.to, self.faces = list(frm), list(to), faces
        self.m = m or I3
        self.origin = list(origin) if origin else [8, 8, 8]
        self.emissive = emissive

    def transformed(self, t, pivot):
        """This element turned by matrix t about pivot (composes with its own rotation)."""
        tm = _mul(t, self.m)
        o = self.origin
        d = [a + b - c for a, b, c in zip(_app(t, [o[i] - pivot[i] for i in range(3)]), pivot, o)]
        shift = _app(_tr(tm), d)
        return El([self.frm[i] + shift[i] for i in range(3)], [self.to[i] + shift[i] for i in range(3)],
                  self.faces, tm, o, self.emissive).rebased()

    def rebased(self):
        """The same element rotating about its own centre (keeps from/to near where it is drawn)."""
        c = [(self.frm[i] + self.to[i]) / 2 for i in range(3)]
        q = _app(self.m, [c[i] - self.origin[i] for i in range(3)])
        o2 = [q[i] + self.origin[i] for i in range(3)]
        s = [o2[i] - c[i] for i in range(3)]
        return El([self.frm[i] + s[i] for i in range(3)], [self.to[i] + s[i] for i in range(3)], self.faces,
                  self.m, o2, self.emissive)

    def moved(self, dx, dy, dz):
        d = (dx, dy, dz)
        return El([self.frm[i] + d[i] for i in range(3)], [self.to[i] + d[i] for i in range(3)], self.faces,
                  self.m, [self.origin[i] + d[i] for i in range(3)], self.emissive)

    def json(self):
        r = lambda v: round(v, 4)
        el = {"from": [r(v) for v in self.frm], "to": [r(v) for v in self.to],
              "faces": {d: {"texture": f"#{t}", "uv": [r(v) for v in uv]} for d, (t, uv) in self.faces.items()}}
        if self.m != I3 and any(abs(self.m[i][j] - I3[i][j]) > 1e-6 for i in range(3) for j in range(3)):
            m = self.m
            ry = math.degrees(math.asin(max(-1, min(1, -m[2][0]))))
            rx = math.degrees(math.atan2(m[2][1], m[2][2]))
            rz = math.degrees(math.atan2(m[1][0], m[0][0]))
            rot = {"origin": [r(v) for v in self.origin]}
            nz = [(a, v) for a, v in (("x", rx), ("y", ry), ("z", rz)) if abs(v) > 1e-4]
            if len(nz) == 1:
                rot.update({"axis": nz[0][0], "angle": r(nz[0][1])})
            else:
                rot.update({"x": r(rx), "y": r(ry), "z": r(rz)})
            el["rotation"] = rot
        if self.emissive:
            el["light_emission"] = 15
        return el


def _uv(u0, v0, w, h):
    w, h = min(abs(w), 16), min(abs(h), 16)
    u0, v0 = u0 % 16, v0 % 16
    u0, v0 = min(u0, 16 - w), min(v0, 16 - h)
    return [u0, v0, u0 + w, v0 + h]


def cube(frm, to, tex, u=0, v=0, only=None, **kw):
    """A box with every face on one texture (or {dir: texture}), uv one texel per pixel."""
    sx, sy, sz = (to[i] - frm[i] for i in range(3))
    size = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}
    faces = {}
    for d in only or DIRS:
        tx = tex.get(d) if isinstance(tex, dict) else tex
        if tx is None:
            continue
        faces[d] = (tx, _uv(u, v, *size[d]))
    return El(frm, to, faces, **kw)


def spin(els, deg, pivot, axis="y"):
    return [e.transformed(_rot(axis, deg), pivot) for e in els]


def turn(els, m, pivot):
    return [e.transformed(m, pivot) for e in els]


def ring(cx, cz, y0, y1, r0, r1, tex, n=8, th=0.6, inner=None, u0=0, v0=0, caps=True, sides=None, phase=None, edge=None):
    """An n-sided frustum (a prism when r0 == r1) around the vertical axis through (cx, cz): n
    panels, each tilted to the slope and turned about the axis. Outer faces on tex, inner faces
    on inner (omitted when None), panel edges on edge (default tex). The panel texture runs around
    the ring, one texel per pixel."""
    edge = edge or tex
    phase = 180 / n if phase is None else phase
    h = y1 - y0
    slope = math.degrees(math.atan2(r0 - r1, h))
    length = math.hypot(h, r0 - r1)
    rm = (r0 + r1) / 2
    w = 2 * math.tan(math.pi / n) * max(r0, r1) * 0.5 + 2 * math.tan(math.pi / n) * min(r0, r1) * 0.5 + 0.05
    out = []
    for k in range(n):
        if sides is not None and k not in sides:
            continue
        faces = {"south": (tex, _uv(u0 + k * w, v0, w, length))}
        if inner:
            faces["north"] = (inner, _uv(u0 + k * w, v0, w, length))
        if caps:
            faces["up"] = (edge, _uv(u0, v0, w, th))
            faces["down"] = (edge, _uv(u0, v0, w, th))
        faces["east"] = (edge, _uv(u0, v0, th, length))
        faces["west"] = (edge, _uv(u0, v0, th, length))
        # panel standing at +z, middle of its outer face at radius rm and height (y0+y1)/2
        ym = (y0 + y1) / 2
        e = El([cx - w / 2, ym - length / 2, cz + rm - th], [cx + w / 2, ym + length / 2, cz + rm], faces,
               origin=[cx, ym, cz + rm])
        e = e.transformed(_rot("x", -slope), [cx, ym, cz + rm])  # lean the top in toward the axis
        e = e.transformed(_rot("y", phase + k * 360 / n), [cx, ym, cz])
        out.append(e)
    return out


def disc(cx, cz, y, r, tex, th=0.4, n=8, down=True, up=True):
    """An n-gon plate (four crossed strips), top at y."""
    out = []
    half = math.tan(math.pi / n) * r
    faces_dirs = [d for d in ("up", "down") if (d == "up" and up) or (d == "down" and down)]
    for k in range(n // 2):
        e = cube([cx - r, y - th - k * 0.01, cz - half], [cx + r, y - k * 0.01, cz + half], tex,
                 only=faces_dirs + ["north", "south", "east", "west"], u=8 - min(r, 8), v=8 - half)
        out += spin([e], k * 360 / n, [cx, y, cz])
    return out


def tube(cx, cz, y0, y1, r, tex, n=8, **kw):
    """A prism split into pieces at most 16 px tall (uv limit)."""
    out, y, v = [], y0, 0
    while y < y1 - 1e-6:
        top = min(y1, y + 16)
        out += ring(cx, cz, y, top, r, r, tex, n=n, v0=v, **kw)
        v += top - y
        y = top
    return out


# ============================================================ satellites

SAT_TEX = {k: f"{MOD}:block/{v}" for k, v in {
    "gold": "sat_foil_gold", "silver": "sat_foil_silver", "armor": "sat_armor", "radiator": "sat_radiator",
    "cells": "sat_cells", "back": "sat_panel_back", "steel": "sat_steel", "dark": "sat_dark", "lens": "sat_lens",
    "dish": "sat_dish", "red": "sat_red"}.items()}


def wing(side, deployed, span=11.5, root=11, y=8.5, wz=5.5):
    """One solar array (side +1 east, -1 west): boom from the bus and two hinged panels."""
    els = []
    if deployed:
        x_root = root if side > 0 else 16 - root
        boom = cube([x_root, y - 0.3, 7.7], [x_root + 2.5, y + 0.3, 8.3], "steel") if side > 0 else \
            cube([x_root - 2.5, y - 0.3, 7.7], [x_root, y + 0.3, 8.3], "steel")
        els.append(boom)
        seg = (span - 0.5) / 2
        start = x_root + 2.5 if side > 0 else x_root - 2.5
        for i in range(2):
            a = start + side * (i * (seg + 0.5))
            b = a + side * seg
            x0, x1 = min(a, b), max(a, b)
            els.append(cube([x0, y - 0.25, 8 - wz / 2 - 0.5], [x1, y + 0.25, 8 + wz / 2 + 0.5],
                            {"up": "cells", "down": "back", "north": "steel", "south": "steel", "east": "steel",
                             "west": "steel"}, u=i * 5))
        # panels tilted a little toward the sun
        els = spin(els, -side * 8, [x_root, y, 8], axis="z")
    else:
        # folded flat against the bus side, cells facing out
        x = 16 - 4.6 if side > 0 else 4.6
        x0, x1 = (x, x + 0.6) if side > 0 else (x - 0.6, x)
        els.append(cube([x0, 3, 5], [x1, 13, 11], {"east" if side > 0 else "west": "cells",
                                                   "west" if side > 0 else "east": "back", "up": "steel",
                                                   "down": "steel", "north": "steel", "south": "steel"}))
    return els


def bus(kind):
    skin = {"survey": "gold", "uplink": "silver", "guardian": "armor"}[kind]
    els = [cube([5, 5, 5], [11, 12, 11], {**{d: skin for d in ("north", "south", "east", "west")},
                                          "up": "radiator", "down": "radiator"}, u=5, v=2)]
    if kind != "guardian":  # corner rails
        for x, z in ((4.7, 4.7), (10.6, 4.7), (4.7, 10.6), (10.6, 10.6)):
            els.append(cube([x, 5, z], [x + 0.7, 12, z + 0.7], "steel"))
    if kind == "guardian":
        # chamfered armour plates on the four vertical edges and a skirt
        for k in range(4):
            p = cube([7, 5.2, 11.5], [9, 11.8, 12.4], {"south": "armor", "east": "armor", "west": "armor",
                                                     "up": "armor", "down": "armor"}, u=4, v=0)
            els += spin([p.moved(0, 0, -0.1)], 45 + k * 90, [8, 8, 8])
        els.append(cube([4.5, 4.4, 4.5], [11.5, 5.1, 11.5], "red"))
        els.append(cube([4.5, 11.9, 4.5], [11.5, 12.4, 11.5], "armor"))
    return els


def survey_instrument(deployed=True):
    els = ring(8, 8, 1.5, 5, 2.4, 2.4, "dark")  # telescope barrel
    els += ring(8, 8, 0.2, 1.5, 2.9, 2.9, "gold", inner="dark")  # sun shade
    els += disc(8, 8, 1.6, 2.3, "lens", up=False)
    els += ring(8, 8, 4.2, 5, 2.7, 2.7, "steel")  # mounting collar
    # star tracker and whip on top
    els.append(cube([9, 12, 6], [10.5, 13.5, 7.5], {**{d: "dark" for d in DIRS}, "up": "lens"}))
    els.append(cube([6.7, 12, 9.2], [7.3, 16, 9.8], "steel"))
    els.append(cube([6.5, 16, 9], [7.5, 16.6, 10], "red"))
    # a small side camera
    els.append(cube([11, 7, 7], [12.5, 8.5, 9], {**{d: "dark" for d in DIRS}, "east": "lens"}))
    return els


def uplink_instrument(deployed=True):
    els = [cube([7.5, 12, 7.5], [8.5, 14.5, 8.5], "steel")]  # mast
    dish = ring(8, 8, 14.5, 15.5, 1.2, 3.2, "back", inner="dish", th=0.4)
    dish += ring(8, 8, 15.5, 17, 3.2, 5.6, "back", inner="dish", th=0.4)
    dish += disc(8, 8, 14.9, 1.3, "dish")
    # feed: three struts and the horn at the focus
    dish.append(cube([7.6, 15, 7.6], [8.4, 19.5, 8.4], "steel"))
    dish.append(cube([7.1, 19.5, 7.1], [8.9, 20.5, 8.9], {**{d: "steel" for d in DIRS}, "down": "dark"}))
    els += turn(dish, euler(x=-35), [8, 14.5, 8])
    # whip antennas and a gold thermal band
    els.append(cube([4.8, 12, 4.8], [5.2, 17, 5.2], "steel"))
    els.append(cube([10.8, 12, 10.8], [11.2, 16, 11.2], "steel"))
    els += ring(8, 8, 2.5, 5, 1.2, 1.2, "gold")  # nadir antenna
    els.append(cube([7.3, 1, 7.3], [8.7, 2.5, 8.7], "dish"))
    return els


def guardian_instrument(deployed=True):
    els = ring(8, 8, 12.3, 13.6, 3.2, 2.8, "armor", u0=2)  # turret ring
    els += disc(8, 8, 13.6, 2.8, "dark")
    head = [cube([5.6, 13.6, 5.8], [10.4, 16.4, 10.6], {**{d: "armor" for d in DIRS}, "up": "dark"}, u=3, v=3),
            cube([5.9, 13.9, 10.6], [10.1, 16, 11.8], {**{d: "red" for d in DIRS}}),  # red mantlet
            cube([10.4, 14.2, 7], [11.4, 15.8, 9.4], {**{d: "dark" for d in DIRS}, "east": "lens"})]  # sensor
    for x in (6.4, 8.6):  # twin barrels with red muzzles
        head.append(cube([x, 14.4, 11.8], [x + 1.0, 15.4, 17.9], "dark"))
        head.append(cube([x - 0.15, 14.25, 17.9], [x + 1.15, 15.55, 18.7], "red"))
    # aimed up and to the side; stowed for launch with the barrels straight up
    els += turn(head, euler(x=-22, y=-35) if deployed else euler(x=-80), [8, 14, 8])
    # interceptor pod under the bus: four tubes with red tips
    els.append(cube([5.5, 3.5, 5.5], [10.5, 5, 10.5], "armor", u=3, v=5))
    for x, z in ((6, 6), (8.3, 6), (6, 8.3), (8.3, 8.3)):
        els.append(cube([x, 1.5, z], [x + 1.7, 3.5, z + 1.7], {**{d: "dark" for d in DIRS}, "down": "red"}))
        els.append(cube([x + 0.35, 0.6, z + 0.35], [x + 1.35, 1.5, z + 1.35], "red"))
    # beacon
    els.append(cube([10, 12.3, 5], [11, 13.3, 6], "red", emissive=True))
    return els


INSTRUMENT = {"survey": survey_instrument, "uplink": uplink_instrument, "guardian": guardian_instrument}


def satellite_elements(kind, deployed=True):
    span = {"survey": 11.5, "uplink": 12.5, "guardian": 9.5}[kind]
    els = bus(kind) + INSTRUMENT[kind](deployed)
    for side in (1, -1):
        els += wing(side, deployed, span=span)
    return els


SAT_DISPLAY = {  # the GUI shows the flat icon instead (items/*.json), "gui" here is only a fallback
    "gui": {"rotation": [25, -30, 0], "translation": [0, 0, 0], "scale": [0.48, 0.48, 0.48]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 12, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [10, -40, 0], "translation": [-1, 4.5, -1], "scale": [0.32, 0.32, 0.32]},
    "firstperson_lefthand": {"rotation": [10, -40, 0], "translation": [-1, 4.5, -1], "scale": [0.32, 0.32, 0.32]},
}


def model(els, tex, display=None, particle=None):
    out = {"textures": {**tex, "particle": particle or next(iter(tex.values()))}, "elements": [e.json() for e in els]}
    if display:
        out["display"] = display
    return out


# ============================================================ launch vehicle

LV_TEX = {k: f"{MOD}:block/{v}" for k, v in {
    "hull": "lv_hull", "flag": "lv_hull_flag", "carbon": "lv_carbon", "bell": "lv_bell", "throat": "lv_bell_inner",
    "gridfin": "lv_gridfin", "fin": "lv_fin", "fairing": "lv_fairing", "blanket": "lv_fairing_inner", "glass": "lv_fairing_glass",
    "adapter": "lv_adapter", "seeker": "lv_seeker", "kv": "lv_kv_body", "plume": "lv_plume",
    "steel": "sat_steel", "dark": "sat_dark", "red": "sat_red"}.items()}
ASAT_TEX = {"hull": f"{MOD}:block/lv_hull_asat", "flag": f"{MOD}:block/lv_hull_asat", "fin": f"{MOD}:block/sat_dark"}

R1 = 5.0      # first and second stage radius (10 px: a slim 0.6-block body)
RF = 8.5      # fairing radius (a hammerhead fairing, wider than the stages)
# Where each model's y = 0 sits, in rocket pixels above the pad plate (mirrored in the renderer).
SEGMENT_BASE = {"lv_stage_lower": 15.5, "lv_stage_upper": 63.5, "lv_adapter": 80, "lv_fairing_a": 110,
                "lv_fairing_b": 110, "lv_kill_vehicle": 102, "lv_plume": -30.5}
FAIRING_BASE = 94  # rocket px: fairing hinge height; the payload plate top is at 95
PAYLOAD_BASE = 95
PAYLOAD_SCALE = 1.3  # the stowed satellite is drawn a little larger than the item model


def engines():
    els = []
    for x, z in ((8, 8), (8 - 3, 8), (8 + 3, 8), (8, 8 - 3), (8, 8 + 3)):
        els += ring(x, z, 0.5, 4, 1.7, 0.9, "bell", inner="throat", n=8, th=0.3, caps=False)
        els += disc(x, z, 0.9, 1.5, "throat", up=False, down=True)
        els.append(cube([x - 0.6, 4, z - 0.6], [x + 0.6, 6, z + 0.6], "dark"))
    els += ring(8, 8, 5, 9, R1 - 0.6, R1, "dark")  # boat tail / heat shield skirt
    els += disc(8, 8, 5.4, R1 - 0.6, "dark", up=False)
    return els


def stage_lower():
    els = engines()
    els += tube(8, 8, 9, 47.5, R1, "hull")
    els += ring(8, 8, 9, 10.5, R1 + 0.25, R1 + 0.25, "carbon")  # base band
    # four swept fins
    for k in range(4):
        f = cube([7.6, 5, 8 + R1 - 0.5], [8.4, 16, 8 + R1 + 3], "fin", u=2, v=0)
        f = f.transformed(_rot("x", 12), [8, 5, 8 + R1])
        els += spin([f], 45 + k * 90, [8, 8, 8])
    # flag stripe panel on the tank
    els += ring(8, 8, 26, 42, R1 + 0.05, R1 + 0.05, "flag", sides=[1, 5], th=0.1)
    return els


def stage_upper():
    els = tube(8, 8, 47.5, 66, R1, "hull")
    # grid fins, folded against the top of the first stage
    for k in range(4):
        g = cube([6, 60, 8 + R1 + 0.3], [10, 64, 8 + R1 + 1.1], "gridfin")
        g2 = cube([7.6, 61.5, 8 + R1], [8.4, 62.5, 8 + R1 + 0.4], "dark")
        els += spin([g, g2], k * 90, [8, 60, 8])
    els += ring(8, 8, 66, 76, R1 + 0.2, R1 + 0.2, "carbon")  # interstage
    els += ring(8, 8, 66, 66.8, R1 + 0.5, R1 + 0.5, "carbon")
    els += tube(8, 8, 76, 92, R1, "hull")
    els += ring(8, 8, 83, 84, R1 + 0.15, R1 + 0.15, "carbon")  # second stage band
    return els


def adapter():
    els = ring(8, 8, 90, 94, R1, RF - 0.3, "adapter", th=0.4)
    els += disc(8, 8, 95, RF - 0.4, "adapter")
    els += ring(8, 8, 93.5, 95, RF - 0.2, RF - 0.2, "carbon")
    return els


def fairing_half(sides):
    """Fairing panels for the given octagon sides (0-3: +x half, 4-7: -x half): a clear window
    band framed in white (so the payload shows from every side, even from the ground) under a
    white ogive nose. Drawn translucent."""
    els = ring(8, 8, 95, 97.5, RF, RF, "fairing", inner="blanket", th=0.5, sides=sides)
    els += ring(8, 8, 97.5, 113, RF, RF, "glass", inner="glass", edge="fairing", th=0.5, sides=sides)
    for y0, y1, r0, r1 in ((113, 115, RF, RF), (115, 118, RF, 7.9), (118, 122, 7.9, 6.7),
                           (122, 125.5, 6.7, 5), (125.5, 128, 5, 2.8), (128, 129.5, 2.8, 1)):
        els += ring(8, 8, y0, y1, r0, r1, "fairing", inner="blanket", th=0.5, sides=sides, v0=y0 - 95)
    return els


def kill_vehicle():
    els = ring(8, 8, 90, 94, R1, 2.6, "carbon", th=0.4)
    els += tube(8, 8, 94, 112, 2.2, "kv")
    els += ring(8, 8, 112, 118, 2.2, 1.1, "seeker", th=0.4)
    els += ring(8, 8, 118, 119.5, 1.1, 0.3, "seeker", th=0.3)
    for k in range(4):  # divert thrusters and tail fins
        els += spin([cube([7.7, 95, 10], [8.3, 100, 12.2], "dark"),
                     cube([7.5, 106, 10.1], [8.5, 107.2, 10.8], "red")], 45 + k * 90, [8, 100, 8])
    return els


def plume():
    els = ring(8, 8, -45, -16, 7.5, 1.2, "plume", n=8, th=0.2, caps=False, inner="plume")
    els += ring(8, 8, -16, 0.5, 4.5, 7.5, "plume", n=8, th=0.2, caps=False, inner="plume")
    for e in els:
        e.emissive = True
    return els


def shift(els, base):
    return [e.moved(0, -base, 0) for e in els]


def lv_models():
    """name -> (elements already shifted into model space, textures)."""
    return {
        "lv_stage_lower": (shift(stage_lower(), SEGMENT_BASE["lv_stage_lower"]), LV_TEX),
        "lv_stage_upper": (shift(stage_upper(), SEGMENT_BASE["lv_stage_upper"]), LV_TEX),
        "lv_adapter": (shift(adapter(), SEGMENT_BASE["lv_adapter"]), LV_TEX),
        "lv_fairing_a": (shift(fairing_half([0, 1, 2, 3]), SEGMENT_BASE["lv_fairing_a"]), LV_TEX),
        "lv_fairing_b": (shift(fairing_half([4, 5, 6, 7]), SEGMENT_BASE["lv_fairing_b"]), LV_TEX),
        "lv_kill_vehicle": (shift(kill_vehicle(), SEGMENT_BASE["lv_kill_vehicle"]), LV_TEX),
        "lv_plume": (shift(plume(), SEGMENT_BASE["lv_plume"]), LV_TEX),
    }


def _check_bounds(name, els):
    for e in els:
        for v in e.frm + e.to:
            if v < -16 or v > 32:
                raise ValueError(f"{name}: element out of -16..32: {e.frm} {e.to}")


def generate(ctx):
    A = ctx.ASSETS
    for kind in ("survey", "uplink", "guardian"):
        name = f"{kind}_satellite"
        els = satellite_elements(kind, True)
        _check_bounds(name, els)
        ctx.write(A / "models" / "item" / f"{name}_3d.json", model(els, SAT_TEX, SAT_DISPLAY, SAT_TEX["steel"]))
        ctx.write(A / "models" / "item" / f"{name}_icon.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}_icon"}})
        stowed = satellite_elements(kind, False)
        _check_bounds(name, stowed)
        ctx.block_model(f"satellite_{kind}_stowed", model(stowed, SAT_TEX, particle=SAT_TEX["steel"]))
        ctx.write(A / "items" / f"{name}.json", {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}_icon"}}],
            "fallback": {"type": "minecraft:model", "model": f"{MOD}:item/{name}_3d"}}})
    for name, (els, tex) in lv_models().items():
        _check_bounds(name, els)
        ctx.block_model(name, {"ambientocclusion": False, **model(els, tex, particle=LV_TEX["hull"])})
    for name in ("lv_stage_lower", "lv_stage_upper"):
        ctx.block_model(f"{name}_asat", {"parent": f"{MOD}:block/{name}", "textures": ASAT_TEX})


# ============================================================ preview

def _textures():
    from PIL import Image
    tex = {}
    for key, ref in {**SAT_TEX, **LV_TEX}.items():
        tex[key] = Image.open(ASSETS / "textures" / "block" / f"{ref.split('/')[-1]}.png").convert("RGBA")
    return tex


def _quads(els, textures, dy=0):
    drill = _load("drill")
    out = []
    for e in els:
        for d, (t, uv) in e.faces.items():
            img = textures[t]
            w, h = img.size
            u1, v1, u2, v2 = uv
            nu = max(1, round(abs(u2 - u1) / 16 * w))
            nv = max(1, round(abs(v2 - v1) / 16 * h))
            n = _app(e.m, drill.FACE_NORMALS[d])
            for i in range(nu):
                for j in range(nv):
                    s0, s1, t0, t1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                    px = min(w - 1, max(0, int((u1 + (s0 + s1) / 2 * (u2 - u1)) / 16 * w)))
                    py = min(h - 1, max(0, int((v1 + (t0 + t1) / 2 * (v2 - v1)) / 16 * h)))
                    col = img.getpixel((px, py))
                    if col[3] < 128:
                        continue
                    cs = []
                    for s, tt in ((s0, t0), (s1, t0), (s1, t1), (s0, t1)):
                        p = drill._face_point(d, e.frm, e.to, s, tt)
                        q = _app(e.m, [p[k] - e.origin[k] for k in range(3)])
                        cs.append([q[0] + e.origin[0], q[1] + e.origin[1] + dy, q[2] + e.origin[2]])
                    out.append((cs, col, n, e.emissive))
    return out


def _load(name):
    spec = importlib.util.spec_from_file_location(f"sat_dep_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def preview(path):
    from PIL import Image, ImageDraw
    drill = _load("drill")
    tex = _textures()
    bg = (40, 44, 52, 255)
    panels = []
    for kind in ("survey", "uplink", "guardian"):
        for yaw, pitch in ((-30, 25), (60, -15)):
            q = _quads(satellite_elements(kind), tex)
            m = _mul(drill._rot("x", pitch), drill._rot("y", yaw))
            img = drill._render_mixed([(q, lambda p, m=m: _app(m, [(p[i] - 8) / 16 for i in range(3)]))],
                                      lambda v: (180 + v[0] * 200, 150 - v[1] * 200), (360, 300), bg)
            panels.append((f"{kind} {yaw}/{pitch}", img))
    # rocket, open fairing with a survey satellite
    els = stage_lower() + stage_upper() + adapter()
    fa = spin(fairing_half([0, 1, 2, 3]), 0, [8 + RF, FAIRING_BASE, 8], axis="z")
    fb = spin(fairing_half([4, 5, 6, 7]), 0, [8 - RF, FAIRING_BASE, 8], axis="z")
    sat = [e.moved(0, PAYLOAD_BASE, 0) for e in satellite_elements("uplink", False)]
    q = _quads(els + fa + fb + sat, tex)
    for yaw, pitch in ((-20, 10), (30, 25)):
        m = _mul(drill._rot("x", pitch), drill._rot("y", yaw))
        img = drill._render_mixed([(q, lambda p, m=m: _app(m, [(p[0] - 8) / 16, (p[1] - 64) / 16, (p[2] - 8) / 16]))],
                                  lambda v: (150 + v[0] * 60, 300 - v[1] * 60), (300, 600), bg)
        panels.append((f"rocket {yaw}/{pitch}", img))
    q = _quads(stage_lower() + stage_upper() + kill_vehicle(), tex)
    m = _mul(drill._rot("x", 10), drill._rot("y", -20))
    panels.append(("asat", drill._render_mixed([(q, lambda p: _app(m, [(p[0] - 8) / 16, (p[1] - 64) / 16, (p[2] - 8) / 16]))],
                                               lambda v: (150 + v[0] * 60, 300 - v[1] * 60), (300, 600), bg)))
    W = sum(p[1].width for p in panels[:3]) + 40
    out = Image.new("RGBA", (max(W, sum(p[1].width for p in panels[6:]) + 40), 300 * 2 + 600 + 80), (25, 25, 30, 255))
    d = ImageDraw.Draw(out)
    x = y = 10
    for i, (name, im) in enumerate(panels):
        if i in (3, 6):
            x = 10
            y += panels[i - 1][1].height + 20
        d.text((x, y), name, fill=(230, 230, 230, 255))
        out.alpha_composite(im, (x, y + 12))
        x += im.width + 10
    out.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(Path(sys.argv[sys.argv.index("--preview") + 1]) if len(sys.argv) > sys.argv.index("--preview") + 1
                else ROOT / "tools" / "satellite_models_preview.png")
    else:
        print(__doc__)

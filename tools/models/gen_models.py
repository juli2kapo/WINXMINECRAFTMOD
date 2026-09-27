#!/usr/bin/env python3
"""Factory Ascent machine block models.

Writes one Minecraft block model per machine state into tools/models/block/
(`<id>.json` idle, `<id>_on.json` working) and renders tools/model_preview.png,
a software-rasterised preview of every model using the real textures.

Run after tools/gen_textures.py (the preview samples the generated PNGs):
    python3 tools/models/gen_models.py            (models + preview)
    python3 tools/models/gen_models.py --only=crusher,quern   (preview subset)

Conventions
  * Every model faces NORTH (-Z): the working face is the north face.
  * Coordinates are model pixels 0..16. x = east, y = up, z = south.
  * UVs are written explicitly. By default a face uses "projected" UVs (what
    Minecraft does when uv is omitted): the texture is the 16x16 picture of
    that side of the whole block, and each element face shows the part of the
    picture it covers. So `<id>_front.png` is literally the front view.
  * Faces that are completely covered by another element are dropped, and
    faces lying on the block boundary get a cullface.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT_DIR = os.path.join(HERE, "block")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "factoryascent", "textures", "block")
PREVIEW = os.path.join(ROOT, "tools", "model_preview.png")
MOD = "factoryascent"

DIRS = ("north", "south", "west", "east", "up", "down")


# =============================================================================
# 1. Model DSL
# =============================================================================


class Box:
    """One cuboid element.

    tex   : texture variable for every face, or a dict keyed by face name with
            the fallbacks 'side' (the four horizontal faces), 'end' (up+down)
            and 'all'.
    uv    : {face: [u0, v0, u1, v1]} explicit UV overrides.
    skip  : faces never emitted.
    rot   : (axis, angle, origin) element rotation.
    """

    def __init__(self, fr, to, tex, uv=None, skip=(), rot=None, shade=True):
        self.fr = [float(v) for v in fr]
        self.to = [float(v) for v in to]
        for a, b in zip(self.fr, self.to):
            assert 0 <= a < b <= 16, (fr, to)
        self.tex = tex
        self.uv = uv or {}
        self.skip = set(skip)
        self.rot = rot
        self.shade = shade

    def texvar(self, face):
        t = self.tex
        if isinstance(t, str):
            return t
        if face in t:
            return t[face]
        if face in ("north", "south", "west", "east") and "side" in t:
            return t["side"]
        if face in ("up", "down") and "end" in t:
            return t["end"]
        return t.get("all")


def auto_uv(face, fr, to):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    return {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1],
        "down": [x0, 16 - z1, x1, 16 - z0],
    }[face]


def face_geom(face, fr, to):
    """(origin = texture top-left corner, u-edge vector, v-edge vector, normal)."""
    x0, y0, z0 = fr
    x1, y1, z1 = to
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {
        "north": ((x1, y1, z0), (-dx, 0, 0), (0, -dy, 0), (0, 0, -1)),
        "south": ((x0, y1, z1), (dx, 0, 0), (0, -dy, 0), (0, 0, 1)),
        "west": ((x0, y1, z0), (0, 0, dz), (0, -dy, 0), (-1, 0, 0)),
        "east": ((x1, y1, z1), (0, 0, -dz), (0, -dy, 0), (1, 0, 0)),
        "up": ((x0, y1, z0), (dx, 0, 0), (0, 0, dz), (0, 1, 0)),
        "down": ((x0, y0, z1), (dx, 0, 0), (0, 0, -dz), (0, -1, 0)),
    }[face]


def inside_any(p, boxes, me):
    for b in boxes:
        if b is me or b.rot:
            continue
        if all(b.fr[i] < p[i] < b.to[i] for i in range(3)):
            return True
    return False


def face_hidden(box, face, boxes):
    """True when every texel just outside the face lies inside another element."""
    if box.rot:
        return False
    o, u, v, n = face_geom(face, box.fr, box.to)
    lu = max(abs(c) for c in u)
    lv = max(abs(c) for c in v)
    nu, nv = max(1, int(math.ceil(lu * 2))), max(1, int(math.ceil(lv * 2)))
    for i in range(nu):
        for j in range(nv):
            s, t = (i + 0.5) / nu, (j + 0.5) / nv
            p = [o[k] + s * u[k] + t * v[k] + n[k] * 0.01 for k in range(3)]
            if not inside_any(p, boxes, box):
                return False
    return True


CULL = {"north": (2, 0.0), "south": (2, 16.0), "west": (0, 0.0), "east": (0, 16.0),
        "up": (1, 16.0), "down": (1, 0.0)}


def element_json(box, boxes):
    faces = {}
    for f in DIRS:
        if f in box.skip or box.texvar(f) is None:
            continue
        if face_hidden(box, f, boxes):
            continue
        uv = box.uv.get(f) or auto_uv(f, box.fr, box.to)
        d = {"uv": [round(c, 4) for c in uv], "texture": "#" + box.texvar(f)}
        axis, val = CULL[f]
        coord = box.fr[axis] if f in ("north", "west", "down") else box.to[axis]
        if not box.rot and coord == val:
            d["cullface"] = f
        faces[f] = d
    e = {"from": [round(c, 4) for c in box.fr], "to": [round(c, 4) for c in box.to]}
    if box.rot:
        axis, angle, origin = box.rot
        e["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
    if not box.shade:
        e["shade"] = False
    e["faces"] = faces
    return e


class Model:
    def __init__(self, mid, textures, boxes, on=None, particle=None, desc="", boxes_on=None):
        self.id = mid
        self.textures = textures          # var -> texture file name (no ns)
        self.boxes = boxes
        self.boxes_on = boxes_on or boxes  # elements of the working (_on) model
        self.on = on or {}                # var -> texture file name for _on
        self.particle = particle or next(iter(textures.values()))
        self.desc = desc

    def tex_map(self, on):
        m = dict(self.textures)
        if on:
            m.update(self.on)
        return m

    def to_json(self, on):
        tm = self.tex_map(on)
        used = set()
        boxes = self.boxes_on if on else self.boxes
        elements = [element_json(b, boxes) for b in boxes]
        for e in elements:
            for f in e["faces"].values():
                used.add(f["texture"][1:])
        missing = used - set(tm)
        assert not missing, (self.id, missing)
        tex = {"particle": "%s:block/%s" % (MOD, self.particle)}
        for k in sorted(tm):
            if k in used:
                tex[k] = "%s:block/%s" % (MOD, tm[k])
        return {"parent": "minecraft:block/block", "textures": tex, "elements": elements}


def octo_y(cx, cz, r, y0, y1, tex, c=None, **kw):
    """Vertical octagonal prism (3 non-overlapping boxes); r = half width,
    c = chamfer size."""
    c = c if c is not None else max(1, round(r * 0.4))
    return [
        Box((cx - r + c, y0, cz - r), (cx + r - c, y1, cz + r), tex, **kw),
        Box((cx - r, y0, cz - r + c), (cx - r + c, y1, cz + r - c), tex, **kw),
        Box((cx + r - c, y0, cz - r + c), (cx + r, y1, cz + r - c), tex, **kw),
    ]


def octo_x(cy, cz, r, x0, x1, tex, c=None, **kw):
    """Octagonal prism along X (a wheel/drum seen from the side)."""
    c = c if c is not None else max(1, round(r * 0.4))
    return [
        Box((x0, cy - r + c, cz - r), (x1, cy + r - c, cz + r), tex, **kw),
        Box((x0, cy - r, cz - r + c), (x1, cy - r + c, cz + r - c), tex, **kw),
        Box((x0, cy + r - c, cz - r + c), (x1, cy + r, cz + r - c), tex, **kw),
    ]


def hopper(x0, z0, x1, z1, y0, y1, outer, inner, floor_y=None):
    """Open-topped bin: four walls 1 px thick and a sunken floor."""
    fy = floor_y if floor_y is not None else y0
    return [
        Box((x0, y0, z0), (x1, y1, z0 + 1), {"all": outer, "south": inner}),
        Box((x0, y0, z1 - 1), (x1, y1, z1), {"all": outer, "north": inner}),
        Box((x0, y0, z0 + 1), (x0 + 1, y1, z1 - 1), {"all": outer, "east": inner}),
        Box((x1 - 1, y0, z0 + 1), (x1, y1, z1 - 1), {"all": outer, "west": inner}),
        Box((x0 + 1, y0, z0 + 1), (x1 - 1, fy + 1, z1 - 1), {"all": outer, "up": inner + "_floor"}),
    ]


def T(mid, *parts):
    """Texture var map {part: '<id>_<part>'}."""
    return {p: "%s_%s" % (mid, p) for p in parts}


# =============================================================================
# 2. The machines
# =============================================================================
# Full-block machines are solid cubes with windows carved into their faces
# (gen_textures.LAYOUT). A carved window only ever opens onto one face, so the
# block can keep hiding its neighbours' faces without see-through gaps. Parts
# inside the windows (rollers, rams, crucibles, arms) are separate elements.
# The quern and the solar panel match their smaller collision shapes instead.

sys.path.insert(0, os.path.dirname(HERE))
import gen_textures as gt  # noqa: E402  (LAYOUT is shared with the textures)

CUBE = ((0, 0, 0), (16, 16, 16))
AXIS = {"north": (2, -1), "south": (2, 1), "west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1)}


def carve_box(face, u0, v0, u1, v1, depth, bounds=CUBE):
    """Texture window on a face -> world box (projected-UV convention)."""
    (bx0, by0, bz0), (bx1, by1, bz1) = bounds
    ya, yb = 16 - (v1 + 1), 16 - v0
    if face == "north":
        return (16 - (u1 + 1), ya, bz0), (16 - u0, yb, bz0 + depth)
    if face == "south":
        return (u0, ya, bz1 - depth), (u1 + 1, yb, bz1)
    if face == "west":
        return (bx0, ya, u0), (bx0 + depth, yb, u1 + 1)
    if face == "east":
        return (bx1 - depth, ya, 16 - (u1 + 1)), (bx1, yb, 16 - u0)
    if face == "up":
        return (u0, by1 - depth, v0), (u1 + 1, by1, v1 + 1)
    raise ValueError(face)


def greedy(solid):
    """Split a voxel set into few boxes: try every axis order, keep the best."""
    best = None
    for order in ((0, 1, 2), (0, 2, 1), (1, 0, 2), (1, 2, 0), (2, 0, 1), (2, 1, 0)):
        s = solid.copy()
        boxes = []
        for idx in np.argwhere(s):
            p = tuple(int(v) for v in idx)
            if not s[p]:
                continue
            lo, hi = list(p), [v + 1 for v in p]
            for ax in order:
                while hi[ax] < 16:
                    sl = [slice(lo[k], hi[k]) for k in range(3)]
                    sl[ax] = slice(hi[ax], hi[ax] + 1)
                    if s[tuple(sl)].all():
                        hi[ax] += 1
                    else:
                        break
            s[tuple(slice(lo[k], hi[k]) for k in range(3))] = False
            boxes.append((tuple(lo), tuple(hi)))
        if best is None or len(boxes) < len(best):
            best = boxes
    return best


def carved(mid, faces, bounds=CUBE, inner="inner"):
    """Boxes of a solid block with the LAYOUT windows of `mid` carved out.

    faces: {face: texture var} for the six outer faces. A wall facing out of a
    window through its opening (the recess floor) gets that face's texture;
    the recess's side walls get `inner`.
    """
    (bx0, by0, bz0), (bx1, by1, bz1) = bounds
    solid = np.zeros((16, 16, 16), bool)
    solid[bx0:bx1, by0:by1, bz0:bz1] = True
    holes = []
    for face, wins in gt.LAYOUT.get(mid, {}).items():
        for (u0, v0, u1, v1, d) in wins:
            fr, to = carve_box(face, u0, v0, u1, v1, d, bounds)
            assert solid[fr[0]:to[0], fr[1]:to[1], fr[2]:to[2]].all(), (mid, face, "windows overlap")
            solid[fr[0]:to[0], fr[1]:to[1], fr[2]:to[2]] = False
            holes.append((face, fr, to))
    bmin, bmax = bounds

    def hole_at(p):
        for face, fr, to in holes:
            if all(fr[k] <= p[k] <= to[k] for k in range(3)):
                return face
        return None

    out = []
    for fr, to in greedy(solid):
        tex = {}
        for f in DIRS:
            ax, sgn = AXIS[f]
            coord = to[ax] if sgn > 0 else fr[ax]
            if coord == (bmax[ax] if sgn > 0 else bmin[ax]):
                tex[f] = faces[f]
                continue
            # look just outside the face for a window
            o = [a for a in range(3) if a != ax]
            seen = None
            for i in range(fr[o[0]], to[o[0]]):
                for j in range(fr[o[1]], to[o[1]]):
                    p = [0.0, 0.0, 0.0]
                    p[ax] = coord + 0.5 * sgn
                    p[o[0]], p[o[1]] = i + 0.5, j + 0.5
                    h = hole_at(p)
                    if h:
                        seen = h
                        break
                if seen:
                    break
            if seen:
                tex[f] = faces[f] if seen == f else inner
        out.append(Box(fr, to, tex))
    return out


def faces_of(front, side, top, bottom, back=None):
    return {"north": front, "south": back or side, "west": side, "east": side, "up": top, "down": bottom}


STD = faces_of("front", "side", "top", "bottom")


def fam(kind, **extra):
    """Texture map with an age family's casing."""
    m = {"side": "%s_machine_side" % kind, "top": "%s_machine_top" % kind,
         "bottom": "%s_machine_bottom" % kind, "inner": "%s_machine_inner" % kind}
    m.update(extra)
    return m


def octo_z(cx, cy, r, z0, z1, tex, c=None, **kw):
    """Octagonal prism along Z (a roller seen end-on from the front)."""
    c = c if c is not None else max(1, round(r * 0.4))
    return [
        Box((cx - r + c, cy - r, z0), (cx + r - c, cy + r, z1), tex, **kw),
        Box((cx - r, cy - r + c, z0), (cx - r + c, cy + r - c, z1), tex, **kw),
        Box((cx + r - c, cy - r + c, z0), (cx + r, cy + r - c, z1), tex, **kw),
    ]


# ---- stone age ----------------------------------------------------------------------------


def m_quern():
    i = "quern"
    tx = {"side": "quern_side", "bed_top": "quern_bed_top", "runner_top": "quern_runner_top",
          "bottom": "quern_bottom", "handle": "quern_handle"}
    base = []
    base += octo_y(8, 8, 7, 0, 6, {"side": "side", "up": "bed_top", "down": "bottom"}, c=2)      # bed stone
    base += octo_y(8, 8, 5, 6, 11, {"side": "side", "up": "runner_top", "down": "bottom"}, c=2)  # runner stone
    base.append(Box((7, 2, 0), (9, 4, 1), {"all": "side"}))                                     # flour spout

    # arm from the eye to the rim and an upright peg; a quarter turn further when working
    idle = base + [Box((7.25, 11, 7.25), (12, 12, 8.75), "handle"), Box((10.5, 12, 7.25), (12, 16, 8.75), "handle")]
    on = base + [Box((7.25, 11, 7.25), (8.75, 12, 12), "handle"), Box((7.25, 12, 10.5), (8.75, 16, 12), "handle")]
    return Model(i, tx, idle, on={"bed_top": "quern_bed_top_on"}, boxes_on=on, particle="quern_side",
                 desc="granite bed + runner stones, oak crank (turned when working)")


def m_brick_kiln():
    i = "brick_kiln"
    tx = {"front": "brick_kiln_front", "side": "brick_kiln_side", "top": "brick_kiln_top",
          "bottom": "brick_kiln_bottom", "inner": "brick_kiln_inner", "fuel": "brick_kiln_fuel"}
    on = {"front": "brick_kiln_front_on", "top": "brick_kiln_top_on", "inner": "brick_kiln_inner_on",
          "fuel": "brick_kiln_fuel_on"}
    b = carved(i, STD)
    b += [Box((4, 9, 0), (5, 10, 4), {"north": "front", "all": "inner"}),       # arch shoulders
          Box((11, 9, 0), (12, 10, 4), {"north": "front", "all": "inner"}),
          Box((5, 2, 1.5), (11, 3, 4), "fuel"),                                   # logs on the hearth
          Box((6, 3, 2), (10, 4, 4), "fuel")]
    return Model(i, tx, b, on=on, particle="brick_kiln_side", desc="clay-brick kiln, arched fire mouth, flue")


# ---- bronze age ---------------------------------------------------------------------------


def m_burner_crusher():
    i = "burner_crusher"
    tx = fam("bronze", front=i + "_front", top=i + "_top", roller=i + "_roller", roller_end=i + "_roller_end")
    on = {"front": i + "_front_on", "roller_end": i + "_roller_end_on"}
    b = carved(i, STD)
    for cx in (5.5, 10.5):
        b += octo_z(cx, 10.5, 2.2, 0.5, 3, {"north": "roller_end", "all": "roller"}, c=0.8)
    return Model(i, tx, b, on=on, particle="bronze_machine_side",
                 desc="bronze housing: twin rollers over a firebox, ore hopper on top")


def m_burner_press():
    i = "burner_press"
    tx = fam("bronze", front=i + "_front", top=i + "_top", ram=i + "_ram", head=i + "_head", die=i + "_die")
    on = {"front": i + "_front_on"}
    b = carved(i, STD)
    die = [Box((4, 7, 1), (12, 8, 4), "die")]
    up = [Box((7, 12, 1.5), (9, 14, 3.5), "ram"), Box((4, 10, 1), (12, 12, 4), "head")]
    down = [Box((7, 10, 1.5), (9, 14, 3.5), "ram"), Box((4, 8, 1), (12, 10, 4), "head")]
    return Model(i, tx, b + die + up, on=on, boxes_on=b + die + down, particle="bronze_machine_side",
                 desc="bronze press: ram over a die (down when working), firebox")


def m_coke_oven():
    i = "coke_oven"
    tx = {"front": i + "_front", "bricks": "coke_oven_bricks", "inner": i + "_inner"}
    b = carved(i, faces_of("front", "bricks", "bricks", "bricks"))
    return Model(i, tx, b, on={"front": i + "_front_on"}, particle="coke_oven_bricks",
                 desc="coke oven bricks, recessed iron hatch, glowing peephole")


def m_blast_furnace():
    i = "blast_furnace"
    tx = {"front": i + "_front", "bricks": "fire_bricks", "inner": i + "_inner"}
    b = carved(i, faces_of("front", "bricks", "bricks", "bricks"))
    return Model(i, tx, b, on={"front": i + "_front_on"}, particle="fire_bricks",
                 desc="fire bricks, iron hatch, molten tap hole")


# ---- electric age -------------------------------------------------------------------------


def m_electric_furnace():
    i = "electric_furnace"
    tx = fam("steel", front=i + "_front", top=i + "_top", coil=i + "_coil")
    b = carved(i, STD)
    for y in (5, 8, 11):
        b.append(Box((3, y, 1.5), (13, y + 1, 2.5), "coil"))
    return Model(i, tx, b, on={"front": i + "_front_on", "coil": i + "_coil_on", "top": i + "_top_on"}, particle="steel_machine_side",
                 desc="steel oven, three heating coils (orange-hot when working)")


def m_crusher():
    i = "crusher"
    tx = fam("steel", front=i + "_front", top=i + "_top", jaw=i + "_jaw")
    b = carved(i, STD)
    b += [Box((5, 7, 1), (6.5, 13, 3), "jaw", rot=("z", 22.5, (5.75, 10, 2))),
          Box((9.5, 7, 1), (11, 13, 3), "jaw", rot=("z", -22.5, (10.25, 10, 2)))]
    return Model(i, tx, b, on={"front": i + "_front_on"}, particle="steel_machine_side",
                 desc="steel jaw crusher: V jaws, hopper on top, hazard chute")


def m_metal_press():
    i = "metal_press"
    tx = fam("steel", front=i + "_front", top=i + "_top", rod=i + "_rod", head=i + "_head", die=i + "_die", plate=i + "_plate")
    b = carved(i, STD)
    die = [Box((4, 5, 1), (12, 6, 4), "die")]
    up = [Box((7, 12, 1.5), (9, 15, 3.5), "rod"), Box((4, 10, 1), (12, 12, 4), "head"),
          Box((5, 6, 1.5), (11, 6.5, 3.5), "plate")]
    down = [Box((7, 8.5, 1.5), (9, 15, 3.5), "rod"), Box((4, 6.5, 1), (12, 8.5, 4), "head"),
            Box((5, 6, 1.5), (11, 6.5, 3.5), "plate")]
    return Model(i, tx, b + die + up, on={"front": i + "_front_on"}, boxes_on=b + die + down,
                 particle="steel_machine_side", desc="hydraulic press: chrome ram, hazard head, die")


def m_alloy_smelter():
    i = "alloy_smelter"
    tx = fam("steel", front=i + "_front", top=i + "_top", crucible=i + "_crucible", crucible_top=i + "_crucible_top")
    b = carved(i, STD)
    for cx in (5, 11):
        b += octo_y(cx, 2.25, 1.75, 4, 9, {"side": "crucible", "up": "crucible_top", "down": "crucible"}, c=0.75)
    return Model(i, tx, b, on={"front": i + "_front_on", "crucible_top": i + "_crucible_top_on"},
                 particle="steel_machine_side", desc="two crucibles in a heated bay")


def m_assembler():
    i = "assembler"
    tx = fam("steel", front=i + "_front", top=i + "_top", arm=i + "_arm", joint=i + "_joint", work=i + "_work")
    b = carved(i, STD)
    b += [Box((3, 5, 0.5), (8, 5.5, 3.5), "work"),                  # circuit board on the bench
          Box((10, 5, 1), (13, 7, 3.5), "joint"),                   # arm base
          Box((11, 7, 1.75), (12, 12, 2.75), "arm"),                # upper arm
          Box((10.5, 11.5, 1.25), (12.5, 13.5, 3.25), "joint"),     # elbow
          Box((6, 12, 1.75), (10.5, 13, 2.75), "arm"),              # forearm
          Box((6, 9.5, 1.75), (7, 12, 2.75), "arm"),                # wrist
          Box((5, 8.5, 1.25), (8, 9.5, 3.25), "joint")]             # gripper
    return Model(i, tx, b, on={"front": i + "_front_on", "top": i + "_top_on"}, particle="steel_machine_side",
                 desc="orange robot arm over a circuit board, status screen")


def m_combustion_generator():
    i = "combustion_generator"
    tx = fam("steel", front=i + "_front", top=i + "_top", slat=i + "_slat", grate="geothermal_generator_bar")
    b = carved(i, STD)
    for y in (10.5, 12.5):
        b.append(Box((3, y, 0.5), (13, y + 1, 1.5), "slat"))
    for x in (5.5, 7.5, 9.5):
        b.append(Box((x, 3, 0.5), (x + 1, 8, 1.5), "grate"))
    return Model(i, tx, b, on={"front": i + "_front_on"}, particle="steel_machine_side",
                 desc="grilled fan over a barred firebox, exhaust on top")


def m_solar_panel():
    i = "solar_panel"
    tx = {"top": "solar_panel_top", "side": "solar_panel_side", "bottom": "steel_machine_bottom",
          "inner": "aluminum_machine_inner"}
    b = carved(i, faces_of("side", "side", "top", "bottom"), bounds=((0, 0, 0), (16, 6, 16)))
    return Model(i, tx, b, particle="solar_panel_top", desc="6-px slab: blue cell tray in an aluminium rim")


def m_auto_farmer():
    i = "auto_farmer"
    tx = fam("steel", front=i + "_front", side=i + "_side", top=i + "_top", tine=i + "_tine")
    b = carved(i, STD)
    b.append(Box((3, 12, 1), (13, 13, 3), "tine"))
    for x in (4.5, 7, 9.5, 12):
        b.append(Box((x - 0.5, 10.5, 1.5), (x + 0.5, 12, 2.5), "tine"))
    return Model(i, tx, b, on={"front": i + "_front_on", "top": i + "_top_on"}, particle=i + "_side",
                 desc="green-trimmed planter: rake over seedlings, crop tray on top")


def m_energy_cell(i):
    tx = {"front": i + "_front", "side": i + "_side", "top": i + "_top", "bottom": i + "_bottom", "inner": i + "_inner"}
    b = carved(i, STD)
    return Model(i, tx, b, on={"front": i + "_front_on"}, particle=i + "_side",
                 desc="tier-coloured cell, bolt window + side charge gauges")


# ---- automation age -----------------------------------------------------------------------


def m_miner():
    i = "miner"
    tx = {"side": "miner_side", "top": "miner_top", "bottom": "miner_bottom", "inner": "aluminum_machine_inner"}
    b = carved(i, faces_of("side", "side", "top", "bottom"))
    return Model(i, tx, b, on={"side": "miner_side_on"}, particle="miner_side",
                 desc="aluminium rig, rift windows into The Deep on all sides, drill below")


def m_geothermal():
    i = "geothermal_generator"
    tx = {"front": i + "_front", "side": i + "_side", "top": i + "_top", "bottom": i + "_bottom",
          "inner": "aluminum_machine_inner", "bar": i + "_bar"}
    b = carved(i, STD)
    for x in (5, 7.5, 10):
        b.append(Box((x, 3, 0.5), (x + 1, 13, 1.5), "bar"))
    return Model(i, tx, b, on={"front": i + "_front_on", "side": i + "_side_on"}, particle=i + "_side",
                 desc="aluminium casing, barred lava window, lava channels down the sides")


MODELS = [m_quern, m_brick_kiln, m_burner_crusher, m_burner_press, m_coke_oven, m_blast_furnace,
          m_electric_furnace, m_crusher, m_metal_press, m_alloy_smelter, m_assembler,
          m_combustion_generator, m_solar_panel, m_auto_farmer, m_miner, m_geothermal] + \
    [lambda c=c: m_energy_cell(c) for c in gt.ENERGY_CELLS]


# =============================================================================
# 3. Software preview renderer
# =============================================================================

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}
_tex_cache = {}


def load_tex(name):
    if name not in _tex_cache:
        p = os.path.join(TEX_DIR, name + ".png")
        if os.path.exists(p):
            _tex_cache[name] = np.asarray(Image.open(p).convert("RGBA")).astype(np.float32)
        else:
            a = np.zeros((16, 16, 4), np.float32)
            a[..., 0] = 255; a[..., 2] = 255; a[..., 3] = 255
            a[::2, ::2, :3] = 0; a[1::2, 1::2, :3] = 0
            _tex_cache[name] = a
    return _tex_cache[name]


def rot_point(p, rot):
    if not rot:
        return p
    axis, angle, o = rot
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def render(model, on, cam, size=150, scale=6.0):
    """cam = direction from the block towards the camera."""
    cx, cy, cz = cam
    n = math.sqrt(cx * cx + cy * cy + cz * cz)
    fwd = np.array([-cx / n, -cy / n, -cz / n])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    img = np.zeros((size, size, 4), np.float32)
    zbuf = np.full((size, size), np.inf)
    centre = np.array([8, 8, 8])
    tm = model.tex_map(on)
    js = model.to_json(on)
    for box, e in zip(model.boxes_on if on else model.boxes, js["elements"]):
        for f, fd in e["faces"].items():
            o, u, v, nrm = face_geom(f, box.fr, box.to)
            p0 = rot_point(o, box.rot)
            pu = rot_point(tuple(o[k] + u[k] for k in range(3)), box.rot)
            pv = rot_point(tuple(o[k] + v[k] for k in range(3)), box.rot)
            P0, U, V = np.array(p0) - centre, np.array(pu) - np.array(p0), np.array(pv) - np.array(p0)
            N = np.cross(V, U) if f else None
            nn = rot_point(nrm, (box.rot[0], box.rot[1], (0, 0, 0))) if box.rot else nrm
            if np.dot(nn, fwd) >= -1e-6:
                continue
            o2 = np.array([P0 @ right, -(P0 @ up)]) * scale + size / 2
            u2 = np.array([U @ right, -(U @ up)]) * scale
            v2 = np.array([V @ right, -(V @ up)]) * scale
            det = u2[0] * v2[1] - u2[1] * v2[0]
            if abs(det) < 1e-6:
                continue
            pts = [o2, o2 + u2, o2 + v2, o2 + u2 + v2]
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            x0, x1 = max(0, int(min(xs))), min(size - 1, int(max(xs)) + 1)
            y0, y1 = max(0, int(min(ys))), min(size - 1, int(max(ys)) + 1)
            if x0 > x1 or y0 > y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            dx, dy = gx - o2[0], gy - o2[1]
            s = (dx * v2[1] - dy * v2[0]) / det
            t = (u2[0] * dy - u2[1] * dx) / det
            m = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            if not m.any():
                continue
            depth = (P0 @ fwd) + s * (U @ fwd) + t * (V @ fwd)
            tex = load_tex(tm[fd["texture"][1:]])
            th, tw = tex.shape[0], tex.shape[1]
            th = tw   # animated strips: first frame
            u0, v0, u1, v1 = fd["uv"]
            uu = np.clip(((u0 + s * (u1 - u0)) * tw / 16).astype(int), 0, tw - 1)
            vv = np.clip(((v0 + t * (v1 - v0)) * th / 16).astype(int), 0, th - 1)
            col = tex[vv, uu]
            m &= col[..., 3] > 127
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            m &= depth < sub
            sh = SHADE[f] if box.shade else 1.0
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[m, :3] = col[m, :3] * sh
            region[m, 3] = 255
            sub[m] = depth[m]
    return Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGBA")


VIEWS = [((1.0, 0.85, -1.25), False, "front-left"), ((-1.0, 0.85, -1.25), True, "front-right ON"),
         ((-1.1, 0.9, 1.2), False, "back")]


def preview(models, path):
    cell, pad = 150, 6
    cols = 2   # models per row
    w = cols * (len(VIEWS) * cell + pad * 3) + pad
    rows = (len(models) + cols - 1) // cols
    h = rows * (cell + 30) + pad
    img = Image.new("RGBA", (w, h), (52, 55, 62, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    for k, mdl in enumerate(models):
        ox = pad + (k % cols) * (len(VIEWS) * cell + pad * 3)
        oy = pad + (k // cols) * (cell + 30)
        d.rectangle([ox - 2, oy - 2, ox + len(VIEWS) * cell + 2, oy + cell + 26], fill=(66, 70, 78, 255))
        for j, (cam, on, label) in enumerate(VIEWS):
            r = render(mdl, on, cam, cell)
            img.alpha_composite(r, (ox + j * cell, oy))
        d.text((ox + 2, oy + cell + 2), "%s  (%d el.)" % (mdl.id, len(mdl.to_json(False)["elements"])), fill=(240, 240, 240), font=font)
        d.text((ox + 2, oy + cell + 13), mdl.desc[:64], fill=(170, 176, 186), font=font)
    img.save(path)


# =============================================================================
# 4. Main
# =============================================================================


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    only = None
    for a in argv:
        if a.startswith("--only="):
            only = set(a[len("--only="):].split(","))
    models = [f() for f in MODELS]
    os.makedirs(OUT_DIR, exist_ok=True)
    missing = set()
    for mdl in models:
        for on in (False, True):
            js = mdl.to_json(on)
            assert len(js["elements"]) <= 40, (mdl.id, len(js["elements"]))
            name = mdl.id + ("_on" if on else "")
            with open(os.path.join(OUT_DIR, name + ".json"), "w") as fh:
                json.dump(js, fh, indent=2)
                fh.write("\n")
            for v in js["textures"].values():
                f = v.split("/", 1)[1]
                if not os.path.exists(os.path.join(TEX_DIR, f + ".png")):
                    missing.add(f)
    print("wrote %d models to %s" % (2 * len(models), OUT_DIR))
    if missing:
        print("MISSING TEXTURES:", ", ".join(sorted(missing)))
    if "--no-preview" in argv:
        return
    sel = [m for m in models if only is None or m.id in only]
    preview(sel, PREVIEW)
    print("preview:", PREVIEW)


if __name__ == "__main__":
    main()

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
    def __init__(self, mid, textures, boxes, on=None, particle=None, desc=""):
        self.id = mid
        self.textures = textures          # var -> texture file name (no ns)
        self.boxes = boxes
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
        elements = [element_json(b, self.boxes) for b in self.boxes]
        for e in elements:
            for f in e["faces"].values():
                used.add(f["texture"][1:])
        missing = used - set(tm)
        assert not missing, (self.id, missing)
        tex = {"particle": "%s:block/%s" % (MOD, tm.get("particle_on" if on else "particle", self.particle)
                                             if False else self.particle)}
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


def m_quern():
    i = "quern"
    tx = T(i, "base", "base_top", "stone", "stone_top", "handle", "spout")
    b = []
    b += octo_y(8, 8, 7, 0, 2, {"side": "base", "end": "base_top"}, c=2)     # plinth
    b += octo_y(8, 8, 6, 2, 6, {"side": "base", "end": "base_top"}, c=2)     # lower (bed) stone
    b += octo_y(8, 8, 5, 6, 10, {"side": "stone", "end": "stone_top"}, c=2)  # runner stone
    b.append(Box((7, 3, 0), (9, 5, 2), "spout"))                            # flour spout
    b.append(Box((10, 10, 9), (12, 16, 11), "handle"))                      # crank handle
    return Model(i, tx, b, particle="quern_base",
                 desc="stepped stone base, runner stone, oak crank")


def m_brick_kiln():
    i = "brick_kiln"
    tx = T(i, "base", "base_top", "front", "side", "top", "inner", "fuel", "chimney", "chimney_top")
    on = T(i, "front_on", "inner_on", "fuel_on")
    on = {"front": on["front_on"], "inner": on["inner_on"], "fuel": on["fuel_on"]}
    body = {"north": "front", "side": "side", "end": "top"}
    inner = "inner"
    b = [
        Box((0, 0, 0), (16, 2, 16), {"side": "base", "end": "base_top"}),
        Box((1, 2, 4), (15, 11, 15), body),                                      # main body / back wall
        Box((1, 2, 1), (5, 11, 4), dict(body, west="side", east=inner)),         # pillar (east side = mouth)
        Box((11, 2, 1), (15, 11, 4), dict(body, east="side", west=inner)),
        Box((5, 8, 1), (11, 11, 4), dict(body, down=inner)),                     # lintel
        Box((5, 7, 1), (6, 8, 4), dict(body, down=inner, east=inner)),           # arch shoulders
        Box((10, 7, 1), (11, 8, 4), dict(body, down=inner, west=inner)),
        Box((5, 2, 2), (11, 3, 4), "fuel"),                                      # coal bed
        Box((2, 11, 2), (14, 13, 14), {"side": "side", "north": "front", "end": "top"}),  # shoulder
        Box((3, 13, 3), (13, 14, 13), {"side": "side", "north": "front", "end": "top"}),  # crown
        Box((6, 11, 11), (10, 16, 15), {"side": "chimney", "end": "chimney_top"}),       # chimney
    ]
    return Model(i, tx, b, on=on, particle="brick_kiln_side",
                 desc="terracotta kiln, arched mouth, stepped crown, back chimney")


def burner_firebox(i, y1=4):
    return Box((1, 0, 1), (15, y1, 15), {"north": "firebox", "side": "firebox_side", "up": "firebox_top",
                                         "down": "firebox_top"})


def m_burner_crusher():
    i = "burner_crusher"
    tx = T(i, "firebox", "firebox_side", "firebox_top", "front", "side", "top", "roller_end", "roller",
           "hopper", "hopper_inner", "hopper_inner_floor", "stack", "stack_top")
    on = {"firebox": i + "_firebox_on"}
    body = {"north": "front", "side": "side", "end": "top"}
    b = [burner_firebox(i)]
    b.append(Box((2, 4, 5), (14, 12, 13), body))                    # body (north face = cavity back)
    b.append(Box((2, 4, 2), (3, 12, 5), body))                      # frame posts
    b.append(Box((13, 4, 2), (14, 12, 5), body))
    b.append(Box((3, 11, 2), (13, 12, 5), body))                    # top beam
    b.append(Box((3, 4, 2), (13, 5, 5), body))                      # bottom lip
    for (a0, a1, b0, b1) in ((4, 7, 3, 8), (9, 12, 8, 13)):         # two toothed rollers (axis z)
        b.append(Box((a0, 6, 3), (a1, 10, 5), {"north": "roller_end", "all": "roller"}))
        b.append(Box((b0, 7, 3), (b1, 9, 5), {"north": "roller_end", "all": "roller"}))
    b.append(Box((5, 12, 5), (11, 13, 11), {"all": "hopper"}))       # hopper throat
    b += hopper(3, 3, 13, 13, 13, 16, "hopper", "hopper_inner")
    b.append(Box((11, 4, 13), (13, 16, 15), {"side": "stack", "end": "stack_top"}))  # flue
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="brick firebox, bronze housing, twin rollers, hopper, flue")


def m_burner_press():
    i = "burner_press"
    tx = T(i, "firebox", "firebox_side", "firebox_top", "pedestal", "die", "die_top", "pillar",
           "head", "head_front", "ram", "ram_head", "gauge", "stack", "stack_top")
    on = {"firebox": i + "_firebox_on"}
    b = [burner_firebox(i)]
    b.append(Box((4, 4, 4), (12, 6, 12), "pedestal"))
    b.append(Box((2, 6, 3), (14, 7, 13), {"side": "die", "end": "die_top"}))
    b.append(Box((1, 4, 6), (3, 13, 10), "pillar"))
    b.append(Box((13, 4, 6), (15, 13, 10), "pillar"))
    b.append(Box((1, 13, 5), (15, 16, 11), {"north": "head_front", "south": "head_front", "all": "head"}))
    b.append(Box((6, 10, 6), (10, 13, 10), "ram"))
    b.append(Box((5, 8, 5), (11, 10, 11), "ram_head"))
    b.append(Box((6, 13, 4), (10, 16, 5), {"north": "gauge", "all": "head"}))
    b.append(Box((11, 4, 12), (13, 12, 14), {"side": "stack", "end": "stack_top"}))
    return Model(i, tx, b, on=on, particle=i + "_head",
                 desc="brick firebox, bronze two-post frame, hanging ram over a die")


def m_full(i, front, other, on_front):
    b = [Box((0, 0, 0), (16, 16, 16), {"north": "front", "all": "bricks"})]
    return Model(i, {"front": front, "bricks": other}, b, on={"front": on_front}, particle=other)


def m_coke_oven():
    return m_full("coke_oven", "coke_oven_front", "coke_oven_bricks", "coke_oven_front_on")


def m_blast_furnace():
    return m_full("blast_furnace", "blast_furnace_front", "fire_bricks", "blast_furnace_front_on")


def m_electric_furnace():
    i = "electric_furnace"
    tx = T(i, "front", "side", "top", "bottom", "door", "door_side", "handle", "hood", "hood_top", "foot")
    on = {"door": i + "_door_on", "front": i + "_front_on"}
    b = [
        Box((1, 1, 2), (15, 13, 15), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
        Box((3, 3, 1), (13, 11, 2), {"north": "door", "all": "door_side"}),
        Box((5, 9, 0), (11, 10, 1), "handle"),
        Box((3, 13, 9), (13, 15, 14), {"side": "hood", "end": "hood_top"}),
    ]
    for (x, z) in ((1, 2), (13, 2), (1, 13), (13, 13)):
        b.append(Box((x, 0, z), (x + 2, 1, z + 2), "foot"))
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="steel oven on feet, glass door with heating coils, vent hood")


def m_crusher():
    i = "crusher"
    tx = T(i, "front", "side", "top", "bottom", "jaw", "hopper", "hopper_inner", "hopper_inner_floor",
           "flywheel", "flywheel_rim", "hub")
    on = {"jaw": i + "_jaw_on"}
    body = {"north": "front", "side": "side", "up": "top", "down": "bottom"}
    b = [
        Box((2, 0, 4), (14, 10, 14), body),                         # body, north face = crushing chamber
        Box((2, 0, 2), (4, 10, 4), body),                           # front frame
        Box((12, 0, 2), (14, 10, 4), body),
        Box((4, 8, 2), (12, 10, 4), body),
        Box((4, 0, 2), (12, 3, 4), body),                           # discharge lip
        Box((4, 3, 3), (7, 5, 4), "jaw"),                           # jaws: thick at the bottom = V
        Box((4, 5, 3), (6, 8, 4), "jaw"),
        Box((9, 3, 3), (12, 5, 4), "jaw"),
        Box((10, 5, 3), (12, 8, 4), "jaw"),
        Box((5, 10, 6), (11, 12, 12), "hopper"),                    # throat
    ]
    b += hopper(2, 3, 14, 15, 12, 16, "hopper", "hopper_inner")
    for (x0, x1, hx0, hx1) in ((0, 1, 1, 2), (15, 16, 14, 15)):   # flywheels + hubs
        b += octo_x(5, 8, 4, x0, x1, {"west": "flywheel", "east": "flywheel", "all": "flywheel_rim"}, c=2)
        b.append(Box((hx0, 4, 7), (hx1, 6, 9), "hub"))
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="steel jaw crusher: hopper, V jaws, twin flywheels")


def m_metal_press():
    i = "metal_press"
    tx = T(i, "base", "base_front", "base_top", "column", "head", "head_front", "head_bottom",
           "ram", "plate", "die", "hose", "guide")
    on = {"head_front": i + "_head_front_on"}
    b = [
        Box((1, 0, 1), (15, 5, 15), {"north": "base_front", "side": "base", "end": "base_top"}),
        Box((3, 5, 2), (13, 6, 10), "die"),
        Box((2, 5, 10), (14, 12, 15), "column"),
        Box((2, 12, 2), (14, 16, 15), {"north": "head_front", "side": "head", "up": "head", "down": "head_bottom"}),
        Box((6, 8, 4), (10, 12, 8), "ram"),
        Box((4, 7, 3), (12, 8, 9), "plate"),
        Box((3, 6, 8), (4, 12, 9), "guide"),
        Box((12, 6, 8), (13, 12, 9), "guide"),
        Box((1, 5, 12), (2, 13, 13), "hose"),
        Box((14, 5, 12), (15, 13, 13), "hose"),
    ]
    return Model(i, tx, b, on=on, particle=i + "_head",
                 desc="tall C-frame stamping press: head, exposed ram, die, hoses")


def m_alloy_smelter():
    i = "alloy_smelter"
    tx = T(i, "front", "side", "top", "bottom", "tray", "crucible", "crucible_top", "flue", "flue_top")
    on = {"front": i + "_front_on", "crucible_top": i + "_crucible_top_on"}
    b = [
        Box((1, 0, 1), (15, 10, 15), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
        Box((4, 3, 0), (12, 4, 1), "tray"),
    ]
    b += octo_y(4.5, 6.5, 2.5, 10, 15, {"side": "crucible", "end": "crucible_top"}, c=1)
    b += octo_y(11.5, 6.5, 2.5, 10, 15, {"side": "crucible", "end": "crucible_top"}, c=1)
    b.append(Box((7, 10, 11), (9, 16, 13), {"side": "flue", "end": "flue_top"}))
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="steel body, two crucibles and a flue on top, hot slot")


def m_assembler():
    i = "assembler"
    tx = T(i, "front", "side", "top", "bottom", "screen", "bezel", "arm", "joint", "turret", "gripper", "work")
    on = {"screen": i + "_screen_on"}
    b = [
        Box((1, 0, 1), (15, 9, 15), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
        Box((3, 3, 0), (13, 8, 1), {"north": "screen", "all": "bezel"}),
        Box((9, 9, 9), (14, 10, 14), "turret"),
        Box((10, 10, 10), (13, 12, 13), "joint"),
        Box((10.5, 12, 10.5), (12.5, 15, 12.5), "arm"),
        Box((10, 14, 4), (13, 16, 11), {"all": "arm", "south": "joint", "north": "joint"}),
        Box((10.5, 12, 5), (12.5, 14, 7), "arm"),
        Box((10, 10.5, 4.5), (11, 12, 7.5), "gripper"),
        Box((12, 10.5, 4.5), (13, 12, 7.5), "gripper"),
        Box((8, 9, 3), (14, 9.5, 8), {"all": "work"}),
    ]
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="workbench body, front screen, robot arm over a circuit board")


def m_combustion_generator():
    i = "combustion_generator"
    tx = T(i, "front", "side", "top", "bottom", "door", "door_side", "pipe", "pipe_top", "cap",
           "coil", "coil_end", "fin")
    on = {"door": i + "_door_on"}
    b = [
        Box((1, 0, 2), (15, 12, 15), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
        Box((3, 1, 1), (13, 8, 2), {"north": "door", "all": "door_side"}),
        Box((10, 12, 10), (13, 15, 13), {"side": "pipe", "end": "pipe_top"}),
        Box((9, 15, 9), (14, 16, 14), {"side": "cap", "end": "pipe_top"}),
        Box((3, 12, 3), (8, 15, 8), {"side": "coil", "end": "coil", "west": "coil_end", "east": "coil_end"}),
        Box((2, 12, 3.5), (3, 15.5, 7.5), "coil_end"),
        Box((8, 12, 3.5), (9, 15.5, 7.5), "coil_end"),
    ]
    for z in (5, 8, 11):
        b.append(Box((0, 2, z), (1, 10, z + 1), "fin"))
        b.append(Box((15, 2, z), (16, 10, z + 1), "fin"))
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="steel generator: fire door, exhaust stack, dynamo drum, side fins")


def m_solar_panel():
    i = "solar_panel"
    tx = T(i, "pv", "panel_edge", "base", "base_top", "stand")
    b = [
        Box((1, 0, 1), (15, 1, 15), {"side": "base", "end": "base_top"}),
        Box((3, 1, 10), (13, 4, 12), "stand"),
        Box((3, 1, 3), (13, 2, 5), "stand"),
        Box((1, 3.15, 2), (15, 3.65, 14), {"up": "pv", "all": "panel_edge"},
            uv={"up": [1, 2, 15, 14], "down": [1, 2, 15, 14]},
            rot=("x", -22.5, (8, 3.4, 8))),
    ]
    return Model(i, tx, b, particle=i + "_pv", desc="tilted PV panel on a low stand")


def m_auto_farmer():
    i = "auto_farmer"
    tx = T(i, "front", "side", "top", "bottom", "hopper", "hopper_top", "tank", "tank_top", "arm",
           "head", "head_bottom")
    on = {"front": i + "_front_on", "head_bottom": i + "_head_bottom_on"}
    b = [
        Box((1, 0, 3), (15, 10, 15), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
        Box((3, 10, 7), (9, 11, 13), "hopper"),
        Box((2, 11, 6), (10, 14, 14), {"side": "hopper", "up": "hopper_top", "down": "hopper"}),
        Box((11, 10, 8), (14, 15, 13), {"side": "tank", "end": "tank_top"}),
        Box((5, 12, 1), (7, 13, 6), "arm"),
        Box((10, 13, 9), (11, 14, 10), "arm"),
        Box((3, 10, 0), (9, 12, 3), {"down": "head_bottom", "all": "head"}),
    ]
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="aluminium planter: seed hopper, water tank, sprinkler arm over the front")


def m_miner():
    i = "miner"
    tx = T(i, "side", "top", "bottom", "leg", "foot", "drill", "drill_tip", "mast", "crown", "shaft")
    on = {"side": i + "_side_on"}
    b = [Box((2, 3, 2), (14, 10, 14), {"side": "side", "up": "top", "down": "bottom"})]
    for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        b.append(Box((x, 0, z), (x + 2, 3, z + 2), "foot"))
    b.append(Box((6, 1, 6), (10, 3, 10), "drill"))
    b.append(Box((7, 0, 7), (9, 1, 9), "drill_tip"))
    for (x, z) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        b.append(Box((x, 10, z), (x + 1, 15, z + 1), "mast"))
    b.append(Box((7, 10, 7), (9, 15, 9), "shaft"))
    b.append(Box((3, 15, 3), (13, 16, 13), "crown"))
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="aluminium rig on feet, derrick mast with crown, drill below")


def m_geothermal():
    i = "geothermal_generator"
    tx = T(i, "front", "side", "top", "bottom", "base", "base_top", "post", "post_top", "vent", "vent_top")
    on = {"front": i + "_front_on", "side": i + "_side_on", "base": i + "_base_on", "vent_top": i + "_vent_top_on"}
    b = [
        Box((0, 0, 0), (16, 3, 16), {"side": "base", "end": "base_top"}),
        Box((2, 3, 2), (14, 13, 14), {"north": "front", "side": "side", "up": "top", "down": "bottom"}),
    ]
    for (x, z) in ((1, 1), (12, 1), (1, 12), (12, 12)):
        b.append(Box((x, 3, z), (x + 3, 14, z + 3), {"side": "post", "end": "post_top"}))
    b.append(Box((4, 13, 5), (7, 16, 11), {"side": "vent", "end": "vent_top"}))
    b.append(Box((9, 13, 5), (12, 16, 11), {"side": "vent", "end": "vent_top"}))
    return Model(i, tx, b, on=on, particle=i + "_side",
                 desc="basalt plinth, heavy iron core with lava seams, corner posts, vents")


def m_energy_cell():
    i = "energy_cell"
    tx = T(i, "side", "top", "cap", "cap_top", "terminal", "stud")
    b = [
        Box((2, 2, 2), (14, 14, 14), {"side": "side", "end": "top"}),
        Box((1, 0, 1), (15, 2, 15), {"side": "cap", "end": "cap_top"}),
        Box((1, 14, 1), (15, 16, 15), {"side": "cap", "end": "cap_top"}),
        Box((5, 5, 1), (11, 11, 2), "terminal"),
        Box((7, 7, 0), (9, 9, 1), "stud"),
    ]
    return Model(i, tx, b, particle=i + "_side", desc="steel cell, hazard end caps, copper terminal")


def m_advanced_energy_cell():
    i = "advanced_energy_cell"
    tx = T(i, "side", "top", "post", "post_top", "terminal", "stud")
    b = [Box((2, 0, 2), (14, 16, 14), {"side": "side", "end": "top"})]
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        b.append(Box((x, 0, z), (x + 2, 16, z + 2), {"side": "post", "end": "post_top"}))
    b += [Box((5, 4, 1), (11, 12, 2), "terminal"), Box((4, 5, 1), (5, 11, 2), "terminal"),
          Box((11, 5, 1), (12, 11, 2), "terminal"), Box((7, 7, 0), (9, 9, 1), "stud")]
    return Model(i, tx, b, particle=i + "_side", desc="aluminium cell, green corner posts, round terminal")


def m_industrial_energy_cell():
    i = "industrial_energy_cell"
    tx = T(i, "side", "top", "rib", "terminal", "stud")
    b = [Box((1, 0, 1), (15, 16, 15), {"side": "side", "end": "top"})]
    for y in (3, 6, 9, 12):
        b.append(Box((0, y, 3), (1, y + 1, 13), "rib"))
        b.append(Box((15, y, 3), (16, y + 1, 13), "rib"))
        b.append(Box((3, y, 15), (13, y + 1, 16), "rib"))
    b += [Box((4, 4, 0), (12, 12, 1), "terminal"), Box((6, 6, 0), (10, 10, 0.5), "stud")]
    return Model(i, tx, b, particle=i + "_side", desc="titanium cell with cooling ribs, heavy terminal")


def m_quantum_energy_cell():
    i = "quantum_energy_cell"
    tx = T(i, "frame", "core", "core_front", "stud")
    b = []
    for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):          # vertical posts
        b.append(Box((x, 0, z), (x + 2, 16, z + 2), "frame"))
    for y in (0, 14):                                            # top/bottom rings
        b.append(Box((2, y, 0), (14, y + 2, 2), "frame"))
        b.append(Box((2, y, 14), (14, y + 2, 16), "frame"))
        b.append(Box((0, y, 2), (2, y + 2, 14), "frame"))
        b.append(Box((14, y, 2), (16, y + 2, 14), "frame"))
    b.append(Box((3, 3, 3), (13, 13, 13), {"north": "core_front", "all": "core"}))
    b.append(Box((6, 6, 1), (10, 10, 3), "stud"))
    return Model(i, tx, b, particle=i + "_frame", desc="black cage around a glowing cyan core")


MODELS = [m_quern, m_brick_kiln, m_burner_crusher, m_burner_press, m_coke_oven, m_blast_furnace,
          m_electric_furnace, m_crusher, m_metal_press, m_alloy_smelter, m_assembler,
          m_combustion_generator, m_solar_panel, m_auto_farmer, m_miner, m_geothermal,
          m_energy_cell, m_advanced_energy_cell, m_industrial_energy_cell, m_quantum_energy_cell]


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
    for box, e in zip(model.boxes, js["elements"]):
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
        d.text((ox + 2, oy + cell + 2), "%s  (%d el.)" % (mdl.id, len(mdl.boxes)), fill=(240, 240, 240), font=font)
        d.text((ox + 2, oy + cell + 13), mdl.desc[:64], fill=(170, 176, 186), font=font)
    img.save(path)


# =============================================================================
# 4. Main
# =============================================================================


def main():
    only = None
    for a in sys.argv[1:]:
        if a.startswith("--only="):
            only = set(a[len("--only="):].split(","))
    models = [f() for f in MODELS]
    os.makedirs(OUT_DIR, exist_ok=True)
    missing = set()
    for mdl in models:
        for on in (False, True):
            js = mdl.to_json(on)
            assert len(js["elements"]) <= 25, mdl.id
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
    sel = [m for m in models if only is None or m.id in only]
    preview(sel, PREVIEW)
    print("preview:", PREVIEW)


if __name__ == "__main__":
    main()

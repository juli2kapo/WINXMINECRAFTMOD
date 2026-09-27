#!/usr/bin/env python3
"""Electric Drill: 3D item model, item definition and lang for the mining modes.

The model is a mining drill (IC2 / Mekanism style): a big conical drill head built from 7 stacked square
rings shrinking to a point, each twisted 12 degrees further round the axis so it reads as a spiral, in a steel
collar; behind it a yellow motor body with vents and a glowing charge band, a carry handle on top, a power pack
with charge LEDs and a D-shaped rear grip. 26.2 accepts any element rotation angle (CuboidModelElement reads
it as a free float, no 22.5-degree check). Textures come from tools/features/drill_textures.py.

Item definition (assets/factoryascent/items/electric_drill.json):
  gui                         -> flat sprite item/electric_drill_icon (reads better at 16 px)
  everything else             -> 3D model, picked by two client properties (item/drill/DrillClient.java):
    factoryascent:drill_charged  false -> electric_drill_3d (dark band)
    factoryascent:drilling       true  -> electric_drill_3d_spinning (lit band, animated spiral bit)
                                 false -> electric_drill_3d_charged (lit band)

Run `python3 tools/features/drill.py --preview` to render tools/drill_preview.png (hand, gui and 3/4 views,
using the same transform maths as the game).
"""
import json
import math
import sys
from pathlib import Path

MOD = "factoryascent"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets" / MOD
ATLAS_PX = 32  # electric_drill_parts.png is 32x32, so 1 uv unit = 2 px



def _load_regions():
    """Atlas regions live in drill_textures.py (the atlas drawer); load them from there."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("drill_textures_regions", Path(__file__).with_name("drill_textures.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.REGIONS


REGIONS = _load_regions()


def uv(region, flip=False):
    x0, y0, x1, y1 = (v * 16 / ATLAS_PX for v in REGIONS[region])
    return [x1, y0, x0, y1] if flip else [x0, y0, x1, y1]


def box(frm, to, faces, rotation=None, emissive=False, name=None):
    """faces: {direction: (texture_var, uv)}; east faces are flipped so both sides read front-to-back alike."""
    el = {"from": frm, "to": to, "faces": {d: {"texture": f"#{t}", "uv": u} for d, (t, u) in faces.items()}}
    if rotation:
        el["rotation"] = rotation
    if emissive:
        el["light_emission"] = 15
    if name:
        el["name"] = name
    return el


def parts(side, top=None, bottom=None, front=None, back=None, tex="parts", east_flip=True):
    top = top or side
    bottom = bottom or top
    front = front or side
    back = back or front
    return {"north": (tex, uv(front)), "south": (tex, uv(back)), "west": (tex, uv(side)),
            "east": (tex, uv(side, east_flip)), "up": (tex, uv(top)), "down": (tex, uv(bottom))}


def elements():
    els = []
    # Motor body with a glowing charge band round it.
    els.append(box([4.5, 6, 6], [11.5, 12, 14], parts("casing_side", "casing_top", "casing_top",
                                                     "casing_end", "casing_end"), name="motor_body"))
    band = {d: ("glow", u) for d, u in (("north", [0, 0, 16, 16]), ("south", [0, 0, 16, 16]),
                                         ("west", [0, 0, 2, 16]), ("east", [0, 0, 2, 16]),
                                         ("up", [0, 0, 16, 2]), ("down", [0, 0, 16, 2]))}
    els.append(box([4.25, 5.75, 10], [11.75, 12.25, 11], band, emissive=True, name="charge_band"))
    # Steel collar the cone turns in.
    els.append(box([4, 5, 5], [12, 13, 6], parts("collar_edge", "collar_edge", "collar_edge",
                                                "collar_front", "collar_front", east_flip=False), name="collar"))
    # Conical drill head: 7 stacked square rings shrinking toward the tip, each twisted 12 degrees more,
    # textured with the flute pattern (animated in the spinning model).
    cx, cy = 8, 9
    z = 5.0
    for k, size in enumerate((7.5, 6.5, 5.5, 4.5, 3.5, 2.5, 1.5)):
        length = 1.6
        h = size / 2
        frm = [cx - h, cy - h, round(z - length, 2)]
        to = [cx + h, cy + h, z]
        side_uv = [0, 2 * k, size, 2 * k + length]
        faces = {d: ("bit", side_uv) for d in ("east", "west", "up", "down")}
        faces["north"] = ("bit", [0, 0, size, size])
        els.append(box(frm, to, faces, rotation={"angle": [0, 12, 24, 36, -42, -30, -18][k], "axis": "z",
                                                 "origin": [cx, cy, z]}, name=f"cone_ring_{k}"))
        z = round(z - length, 2)
    tip = {d: ("bit", [0, 0, 1, 1]) for d in ("north", "east", "west", "up", "down")}
    els.append(box([cx - 0.4, cy - 0.4, z - 1.2], [cx + 0.4, cy + 0.4, z], tip, name="cone_tip"))
    # Carry handle over the body: two posts and a bar.
    for z0 in (7, 12):
        els.append(box([7.25, 12, z0], [8.75, 14, z0 + 1], parts("handle"), name="handle_post"))
    els.append(box([7, 14, 6.5], [9, 15, 13.5], parts("handle"), name="carry_handle"))
    # Power pack behind the body, then a D-shaped rear grip.
    els.append(box([5, 6.5, 14], [11, 12.5, 17], parts("battery_side", "battery_top", "battery_top",
                                                      "battery_back", "battery_back"), name="power_pack"))
    els.append(box([7.25, 11, 17], [8.75, 12, 19.5], parts("handle"), name="grip_top"))
    els.append(box([7.25, 6.5, 17], [8.75, 7.5, 19.5], parts("handle"), name="grip_bottom"))
    els.append(box([7, 7.5, 18.5], [9, 11, 20], parts("grip"), name="rear_grip"))
    return els


# Display transforms. Model axes: bit points to -Z (north), top is +Y, grip hangs toward -Y.
DISPLAY = {
    # Third person held frame: +Y = where the player faces, +Z = up. rotation x=90 swings the bit forward.
    "thirdperson_righthand": {"rotation": [80, 0, 0], "translation": [0, 1.5, 3.25], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [80, 0, 0], "translation": [0, 1.5, 3.25], "scale": [0.7, 0.7, 0.7]},
    # First person: camera looks down -Z, so the bit already points into the screen; yaw it toward the crosshair.
    "firstperson_righthand": {"rotation": [8, 10, 0], "translation": [-2.5, 2.5, -2], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [8, 10, 0], "translation": [-2.5, 2.5, -2], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"rotation": [0, -90, 0], "translation": [-1, -1, 0], "scale": [0.72, 0.72, 0.72]},
    "head": {"rotation": [0, 90, 0], "translation": [0, 12, 0], "scale": [0.8, 0.8, 0.8]},
    # Only used if something renders the 3D model in a gui context (the item definition shows the flat icon).
    "gui": {"rotation": [20, -125, 0], "translation": [0.5, 0.5, 0], "scale": [0.55, 0.55, 0.55]},
}


def model_base():
    return {
        "textures": {"parts": f"{MOD}:item/electric_drill_parts", "bit": f"{MOD}:item/electric_drill_bit",
                     "glow": f"{MOD}:item/electric_drill_glow_off", "particle": f"{MOD}:item/electric_drill_parts"},
        "display": DISPLAY,
        "elements": elements(),
    }


def models(ctx):
    item = ctx.ASSETS / "models" / "item"
    ctx.write(item / "electric_drill_3d.json", model_base())
    ctx.write(item / "electric_drill_3d_charged.json", {
        "parent": f"{MOD}:item/electric_drill_3d",
        "textures": {"glow": f"{MOD}:item/electric_drill_glow"}})
    ctx.write(item / "electric_drill_3d_spinning.json", {
        "parent": f"{MOD}:item/electric_drill_3d",
        "textures": {"glow": f"{MOD}:item/electric_drill_glow", "bit": f"{MOD}:item/electric_drill_bit_spin"}})
    ctx.write(item / "electric_drill_icon.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/electric_drill_icon"}})

    def m(name):
        return {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}

    three_d = {
        "type": "minecraft:condition", "property": f"{MOD}:drill_charged",
        "on_false": m("electric_drill_3d"),
        "on_true": {"type": "minecraft:condition", "property": f"{MOD}:drilling",
                    "on_true": m("electric_drill_3d_spinning"), "on_false": m("electric_drill_3d_charged")}}
    ctx.write(ctx.ASSETS / "items" / "electric_drill.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": m("electric_drill_icon")}],
        "fallback": three_d}})


LANG = [
    ("tooltip.factoryascent.electric_drill",
     "Mines faster than netherite while charged. Charge it in an Energy Cell. 100 FE per block.",
     "Pica más rápido que la netherita mientras tenga carga. Cárgalo en una celda de energía. 100 FE por bloque."),
    ("tooltip.factoryascent.electric_drill.mode_line", "Mode: %s", "Modo: %s"),
    ("tooltip.factoryascent.electric_drill.cycle", "Sneak + right-click to change mode",
     "Agáchate + clic derecho para cambiar de modo"),
    ("tooltip.factoryascent.electric_drill.mode.single", "Single", "Individual"),
    ("tooltip.factoryascent.electric_drill.mode.area", "Area 3×3", "Área 3×3"),
    ("tooltip.factoryascent.electric_drill.mode.vein", "Vein", "Veta"),
    ("message.factoryascent.electric_drill.mode", "Drill mode: %s", "Modo del taladro: %s"),
]


def generate(ctx):
    models(ctx)
    for key, en, es in LANG:
        ctx.lang(key, en, es)


# ============================================================ preview (python3 tools/features/drill.py --preview)

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


def _xyz(r):
    """JOML rotationXYZ: v' = Rx * Ry * Rz * v (as ItemTransform.apply)."""
    return _mul(_mul(_rot("x", r[0]), _rot("y", r[1])), _rot("z", r[2]))


def display_fn(t):
    """Model units (0..16) -> transformed space in blocks, exactly as ItemTransform.apply."""
    rot = _xyz(t.get("rotation", [0, 0, 0]))
    tr = [v / 16 for v in t.get("translation", [0, 0, 0])]
    sc = t.get("scale", [1, 1, 1])

    def f(p):
        q = [p[i] / 16 - 0.5 for i in range(3)]
        q = [q[i] * sc[i] for i in range(3)]
        q = _app(rot, q)
        return [q[i] + tr[i] for i in range(3)]
    return f, rot


FACE_NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
                "up": (0, 1, 0), "down": (0, -1, 0)}


def _face_point(d, frm, to, s, t):
    (x0, y0, z0), (x1, y1, z1) = frm, to
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {
        "north": (x1 - s * dx, y1 - t * dy, z0), "south": (x0 + s * dx, y1 - t * dy, z1),
        "west": (x0, y1 - t * dy, z0 + s * dz), "east": (x1, y1 - t * dy, z1 - s * dz),
        "up": (x0 + s * dx, y1, z0 + t * dz), "down": (x0 + s * dx, y0, z1 - t * dz),
    }[d]


def model_quads(model_json, textures):
    """Every texel of every face as (corners in model units, rgba, normal in model space, emissive)."""
    quads = []
    for el in model_json["elements"]:
        rot = el.get("rotation")
        m = _rot(rot["axis"], rot["angle"]) if rot else None
        origin = rot["origin"] if rot else None

        def place(p):
            if m is None:
                return list(p)
            q = _app(m, [p[i] - origin[i] for i in range(3)])
            return [q[i] + origin[i] for i in range(3)]

        for d, face in el["faces"].items():
            img = textures[face["texture"][1:]]
            w, h = img.size
            frame_h = w if h > w else h  # animated strips: first frame
            u1, v1, u2, v2 = face["uv"]
            n = FACE_NORMALS[d]
            if m is not None:
                n = _app(m, n)
            fr, to = el["from"], el["to"]
            # texel grid on the face (sampled at texture resolution)
            nu = max(1, round(abs(u2 - u1) / 16 * w))
            nv = max(1, round(abs(v2 - v1) / 16 * frame_h))
            for i in range(nu):
                for j in range(nv):
                    s0, s1, t0, t1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                    uu = u1 + (s0 + s1) / 2 * (u2 - u1)
                    vv = v1 + (t0 + t1) / 2 * (v2 - v1)
                    px = min(w - 1, max(0, int(uu / 16 * w)))
                    py = min(frame_h - 1, max(0, int(vv / 16 * frame_h)))
                    col = img.getpixel((px, py))
                    if col[3] == 0:
                        continue
                    corners = [place(_face_point(d, fr, to, s, t)) for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1))]
                    quads.append((corners, col, n, "light_emission" in el))
    return quads


def render(quads, to_view, project, size, bg):
    """to_view: model point -> view point (camera looks down -Z); project: view point -> (x, y) pixels."""
    return _render_mixed([(quads, to_view)], project, size, bg)


def _view_normal(corners):
    """Outward normal: corners run along +u then +v, and u x v points into the face."""
    a, b, c = corners[:3]
    u = [b[i] - a[i] for i in range(3)]
    v = [c[i] - a[i] for i in range(3)]
    n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
    ln = math.sqrt(sum(x * x for x in n)) or 1
    return [-x / ln for x in n]


def _cuboid_quads(frm, to, col):
    """Flat-coloured box (for the preview's stand-in player), corners in blocks."""
    out = []
    for d in FACE_NORMALS:
        corners = [_face_point(d, frm, to, s, t) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
        out.append((corners, col, FACE_NORMALS[d], False))
    return out


def preview(path):
    from PIL import Image, ImageDraw
    tex_dir = ASSETS / "textures" / "item"
    base = model_base()

    def load(name):
        return Image.open(tex_dir / f"{name}.png").convert("RGBA")

    textures = {"parts": load("electric_drill_parts"), "bit": load("electric_drill_bit"),
                "glow": load("electric_drill_glow")}
    quads = model_quads(base, textures)
    panels = []
    bg = (58, 62, 70, 255)

    # 1) 3/4 view of the bare model (orthographic).
    view = _mul(_rot("x", 25), _rot("y", -140))

    def v34(p):
        q = _app(view, [p[0] / 16 - 0.5, p[1] / 16 - 0.5, p[2] / 16 - 0.5])
        return q
    panels.append(("3/4 view", render(quads, v34, lambda q: (200 + q[0] * 260, 200 - q[1] * 260), (400, 400), bg)))

    # 2) side view (east face), bit to the right.
    side = _rot("y", -90)
    panels.append(("side", render(quads, lambda p: _app(side, [p[0] / 16 - 0.5, p[1] / 16 - 0.5, p[2] / 16 - 0.5]),
                                  lambda q: (170 + q[0] * 250, 230 - q[1] * 250), (400, 400), bg)))

    # 3) first person, right hand: ItemTransform then applyItemArmTransform, perspective fov 70.
    fp, _ = display_fn(DISPLAY["firstperson_righthand"])

    def fpv(p):
        q = fp(p)
        return [q[0] + 0.56, q[1] - 0.52, q[2] - 0.72]
    W, H = 640, 360
    f = (H / 2) / math.tan(math.radians(35))

    def persp(q):
        z = -q[2] if q[2] < -0.01 else 0.01
        return (W / 2 + q[0] / z * f, H / 2 - q[1] / z * f)
    fp_img = render(quads, fpv, persp, (W, H), (120, 170, 220, 255))
    d = ImageDraw.Draw(fp_img)
    d.line([(W / 2 - 8, H / 2), (W / 2 + 8, H / 2)], fill=(255, 255, 255, 255), width=2)
    d.line([(W / 2, H / 2 - 8), (W / 2, H / 2 + 8)], fill=(255, 255, 255, 255), width=2)
    panels.append(("first person (right hand)", fp_img))

    # 4) third person: held frame (+X right, +Y forward, +Z up) at the hand, then player frame, 3/4 camera.
    tp, _ = display_fn(DISPLAY["thirdperson_righthand"])
    hand = (6 / 16, 2 / 16, 12 / 16)  # right 6 px, forward 2 px, 12 px above the feet (arm hanging)

    def player_frame(p):  # (right, forward, up) in blocks -> world (x = right, y = up, z = -forward)
        return [p[0], p[2], -p[1]]
    def tpv_for(cam):
        def tpv_world(w):
            return _app(cam, [w[0], w[1] - 1.0, w[2]])

        def tpv(p):
            q = tp(p)
            return tpv_world(player_frame([hand[0] + q[0], hand[1] + q[1], hand[2] + q[2]]))
        return tpv_world, tpv

    steve = []
    skin, shirt, pants, eye = (190, 140, 110, 255), (60, 170, 180, 255), (60, 60, 150, 255), (40, 40, 90, 255)
    # boxes in player frame (right, forward, up) in pixels; arms hang straight down (idle pose)
    for frm, to, col in (((-4, -2, 0), (4, 2, 12), pants), ((-4, -2, 12), (4, 2, 24), shirt),
                         ((-4, -4, 24), (4, 4, 32), skin), ((4, -2, 12), (8, 2, 24), shirt),
                         ((-8, -2, 12), (-4, 2, 24), shirt), ((-3, 4, 27), (-1, 4.3, 28), eye),
                         ((1, 4, 27), (3, 4.3, 28), eye)):
        for corners, c, n, e in _cuboid_quads(frm, to, col):
            steve.append(([[v / 16 for v in pt] for pt in corners], c, n, e))

    def ortho(q):
        return (240 + q[0] * 200, 240 - q[1] * 200)
    for label, cam in (("third person, from the right", _mul(_rot("x", 8), _rot("y", -90))),
                       ("third person, front 3/4", _mul(_rot("x", 15), _rot("y", 225)))):
        world, tpv = tpv_for(cam)
        panels.append((label, _render_mixed([(quads, tpv), (steve, lambda p, w=world: w(player_frame(p)))],
                                            ortho, (400, 480), bg)))

    # 5) gui: the flat icon at 16 px and 8x.
    icon = load("electric_drill_icon")
    gui = Image.new("RGBA", (400, 400), (139, 139, 139, 255))
    gui.alpha_composite(icon.resize((256, 256), Image.NEAREST), (72, 40))
    slot = Image.new("RGBA", (18, 18), (55, 55, 55, 255))
    slot.paste((139, 139, 139, 255), (1, 1, 18, 18))
    slot.paste((198, 198, 198, 255), (1, 1, 17, 17))
    slot.paste((139, 139, 139, 255), (1, 1, 17, 17))
    slot.alpha_composite(icon, (1, 1))
    gui.alpha_composite(slot.resize((72, 72), Image.NEAREST), (100, 310))
    gui.alpha_composite(slot, (230, 337))
    panels.append(("gui (icon 16x16, x16 / x4 / x1)", gui))

    # compose
    pad, label_h = 10, 18
    widths = [p[1].width for p in panels]
    total_w = sum(widths[:3]) + pad * 4
    row2_w = sum(widths[3:]) + pad * (len(panels) - 3 + 1)
    out = Image.new("RGBA", (max(total_w, row2_w), 400 + 480 + label_h * 2 + pad * 3), (30, 32, 36, 255))
    d = ImageDraw.Draw(out)
    x = pad
    for name, im in panels[:3]:
        out.alpha_composite(im, (x, label_h + pad))
        d.text((x, pad), name, fill=(230, 230, 230, 255))
        x += im.width + pad
    x = pad
    y2 = label_h * 2 + pad * 2 + 400
    for name, im in panels[3:]:
        out.alpha_composite(im, (x, y2))
        d.text((x, y2 - label_h), name, fill=(230, 230, 230, 255))
        x += im.width + pad
    out.save(path)
    print(f"wrote {path}")


def _render_mixed(groups, project, size, bg):
    """Like render() for several (quads, to_view) groups sharing one depth sort."""
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", size, bg)
    draw = ImageDraw.Draw(img)
    light = (0.35, 0.8, 0.5)
    ln = math.sqrt(sum(c * c for c in light))
    light = [c / ln for c in light]
    items = []
    for quads, to_view in groups:
        for corners, col, n, emissive in quads:
            vc = [to_view(p) for p in corners]
            vn = _view_normal(vc)
            if vn[2] <= 0:
                continue
            k = 1.0 if emissive else 0.45 + 0.55 * max(0.0, sum(a * b for a, b in zip(vn, light)))
            c = tuple(min(255, round(col[i] * k)) for i in range(3)) + (255,)
            items.append((sum(p[2] for p in vc) / 4, [project(p) for p in vc], c))
    items.sort(key=lambda it: it[0])
    for _, pts, c in items:
        draw.polygon(pts, fill=c)
    return img


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(ROOT / "tools" / "drill_preview.png")
    else:
        print(__doc__)

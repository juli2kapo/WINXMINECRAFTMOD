#!/usr/bin/env python3
"""Minimizer and Maximizer Rays: 3D item models, item definitions and the scope recipes.

Both rays share one sci-fi pistol silhouette (models/item/size_ray_base.json and size_ray_scoped_base.json,
texture variables #parts, #energy, #crystal): white shell body with a glowing energy cell, rubber grip and
trigger, a steel barrel wound with three glowing coils and a faceted crystal emitter at the muzzle. The
scoped base adds a scope on two mounts. Per ray only the colour textures change (Minimizer cyan,
Maximizer orange); textures come from tools/features/ray_textures.py.

Item definition per ray:
  gui          -> flat icon ({ray}_icon / {ray}_scoped_icon), picked by minecraft:has_component factoryascent:scoped
  otherwise    -> 3D model, scoped or not (has_component), brighter coils and crystal while charging
                  (minecraft:using_item)

Recipes: ray + spyglass -> the same ray with factoryascent:scoped (vanilla minecraft:crafting_transmute keeps
every component of the input, energy included, then applies the result's components); a scoped ray alone ->
bare ray + spyglass back (factoryascent:size_ray_unscope, SizeRayUnscopeRecipe). They never overlap: attach
takes 2 items, unscope 1.

Run `python3 tools/features/ray.py --preview` to render tools/ray_preview.png.
"""
import importlib.util
import math
import sys
from pathlib import Path

MOD = "factoryascent"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets" / MOD
RAYS = ("minimizer_ray", "maximizer_ray")
ATLAS_PX = 32


def _load(name):
    spec = importlib.util.spec_from_file_location(f"ray_dep_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


REGIONS = _load("ray_textures").REGIONS


def uv(region, flip=False):
    x0, y0, x1, y1 = (v * 16 / ATLAS_PX for v in REGIONS[region])
    return [x1, y0, x0, y1] if flip else [x0, y0, x1, y1]


def box(frm, to, faces, rotation=None, emissive=False, name=None):
    el = {"from": frm, "to": to, "faces": {d: {"texture": f"#{t}", "uv": u} for d, (t, u) in faces.items()}}
    if rotation:
        el["rotation"] = rotation
    if emissive:
        el["light_emission"] = 15
    if name:
        el["name"] = name
    return el


def parts(side, top=None, front=None, back=None):
    top = top or side
    front = front or side
    back = back or front
    return {"north": ("parts", uv(front)), "south": ("parts", uv(back)), "west": ("parts", uv(side)),
            "east": ("parts", uv(side, True)), "up": ("parts", uv(top)), "down": ("parts", uv(top))}


def glow(tex, u_len, v_len):
    """Faces of a glowing element on a 16x16 colour texture."""
    return {d: (tex, [0, 0, u_len, v_len]) for d in ("north", "south", "east", "west", "up", "down")}


def elements(scoped):
    els = [
        box([6.5, 8, 7], [9.5, 11, 15], parts("shell_side", "shell_top", "shell_end"), name="body"),
        box([6.25, 8.5, 9], [9.75, 10.5, 12.5], glow("energy", 4, 2), emissive=True, name="energy_cell"),
        box([7, 8.5, 15], [9, 10.5, 16], parts("frame"), name="rear_cap"),
        box([7.5, 11, 8], [8.5, 11.5, 14], parts("frame"), name="top_rail"),
        box([7, 2.5, 11.5], [9, 8, 14], parts("grip", front="grip_front"),
            rotation={"angle": -15, "axis": "x", "origin": [8, 8, 12.75]}, name="grip"),
        box([7.5, 6, 9], [8.5, 6.5, 11.5], parts("frame"), name="trigger_guard"),
        box([7.6, 6.5, 10], [8.4, 8, 10.75], parts("trigger"), name="trigger"),
        box([7.25, 8.75, 1], [8.75, 10.25, 7], parts("barrel_side", front="barrel_end"), name="barrel"),
    ]
    for i, z0 in enumerate((5.25, 3.5, 1.75)):
        els.append(box([6.75, 8.25, z0], [9.25, 10.75, z0 + 0.75], glow("energy", 3, 1), emissive=True,
                       name=f"coil_{i}"))
    els.append(box([7.1, 8.6, -1.5], [8.9, 10.4, 1], glow("crystal", 4, 4), emissive=True, name="crystal",
                   rotation={"angle": 45, "axis": "z", "origin": [8, 9.5, 0]}))
    els.append(box([7.5, 9, -2.75], [8.5, 10, -1.5], glow("crystal", 2, 2), emissive=True, name="crystal_tip",
                   rotation={"angle": 45, "axis": "z", "origin": [8, 9.5, -2]}))
    if scoped:
        els += [
            box([7.5, 11.5, 8.5], [8.5, 12, 9.5], parts("frame"), name="scope_mount_front"),
            box([7.5, 11.5, 12], [8.5, 12, 13], parts("frame"), name="scope_mount_back"),
            box([7.25, 12, 7], [8.75, 13.5, 15], parts("scope_side", front="lens", back="scope_end"), name="scope"),
        ]
    return els


# Model axes: muzzle to -Z (north), top +Y, grip toward -Y (same frame as the Electric Drill).
DISPLAY = {
    "thirdperson_righthand": {"rotation": [80, 0, 0], "translation": [0, 2.5, 2.5], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [80, 0, 0], "translation": [0, 2.5, 2.5], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [4, 10, 0], "translation": [-2, 2, -1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [4, 10, 0], "translation": [-2, 2, -1], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 90, 0], "translation": [0, 12, 0], "scale": [0.8, 0.8, 0.8]},
    "gui": {"rotation": [20, -125, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}


def base_model(scoped):
    return {"textures": {"parts": f"{MOD}:item/size_ray_parts", "particle": f"{MOD}:item/size_ray_parts"},
            "display": DISPLAY, "elements": elements(scoped)}


def models(ctx):
    item = ctx.ASSETS / "models" / "item"
    ctx.write(item / "size_ray_base.json", base_model(False))
    ctx.write(item / "size_ray_scoped_base.json", base_model(True))
    for ray in RAYS:
        for scoped in (False, True):
            tag = "_scoped" if scoped else ""
            parent = f"{MOD}:item/size_ray{tag}_base"
            for hot in (False, True):
                suffix = "_hot" if hot else ""
                ctx.write(item / f"{ray}_3d{tag}{'_active' if hot else ''}.json", {
                    "parent": parent,
                    "textures": {"energy": f"{MOD}:item/{ray}_energy{suffix}",
                                 "crystal": f"{MOD}:item/{ray}_crystal{suffix}"}})
            ctx.write(item / f"{ray}{tag}_icon.json", {
                "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{ray}{tag}_icon"}})

        def m(name):
            return {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}

        def scoped_switch(on_true, on_false):
            return {"type": "minecraft:condition", "property": "minecraft:has_component",
                    "component": f"{MOD}:scoped", "on_true": on_true, "on_false": on_false}

        def using(name):
            return {"type": "minecraft:condition", "property": "minecraft:using_item",
                    "on_true": m(f"{name}_active"), "on_false": m(name)}

        ctx.write(ctx.ASSETS / "items" / f"{ray}.json", {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "gui", "model": scoped_switch(m(f"{ray}_scoped_icon"), m(f"{ray}_icon"))}],
            "fallback": scoped_switch(using(f"{ray}_3d_scoped"), using(f"{ray}_3d"))}})


def recipes(ctx):
    recipe_dir = ctx.DATA / MOD / "recipe"
    for ray in RAYS:
        ctx.write(recipe_dir / f"{ray}_scope.json", {
            "type": "minecraft:crafting_transmute", "category": "equipment", "group": "size_ray_scope",
            "input": f"{MOD}:{ray}", "material": "minecraft:spyglass",
            "result": {"id": f"{MOD}:{ray}", "components": {f"{MOD}:scoped": {}}}})
    ctx.write(recipe_dir / "size_ray_unscope.json", {"type": f"{MOD}:size_ray_unscope"})


LANG = [
    ("tooltip.factoryascent.size_ray.usage",
     "Hold right-click for 1 s to charge, release to fire (%s FE per shot, %s blocks).",
     "Mantén clic derecho 1 s para cargar y suelta para disparar (%s FE por disparo, %s bloques)."),
    ("tooltip.factoryascent.size_ray.limits", "Sizes from %s%% to %s%%", "Tamaños del %s%% al %s%%"),
    ("tooltip.factoryascent.size_ray.scoped", "Scope: zooms while charging, reaches %s blocks",
     "Mira: amplía al cargar, alcanza %s bloques"),
    ("tooltip.factoryascent.size_ray.scope_hint",
     "Craft with a spyglass to mount a scope (alone in the grid to take it off)",
     "Fabrícalo con un catalejo para montar una mira (solo en la cuadrícula para quitarla)"),
]


def generate(ctx):
    models(ctx)
    recipes(ctx)
    for key, en, es in LANG:
        ctx.lang(key, en, es)


# ============================================================ preview

def preview(path):
    from PIL import Image, ImageDraw
    drill = _load("drill")
    tex_dir = ASSETS / "textures" / "item"

    def load(name):
        return Image.open(tex_dir / f"{name}.png").convert("RGBA")

    parts_tex = load("size_ray_parts")
    bg = (58, 62, 70, 255)
    view = drill._mul(drill._rot("x", 22), drill._rot("y", -135))
    side = drill._rot("y", -90)
    panels = []
    for ray in RAYS:
        for scoped in (False, True):
            for hot in ((False, True) if not scoped else (False,)):
                sfx = "_hot" if hot else ""
                quads = drill.model_quads(base_model(scoped), {
                    "parts": parts_tex, "energy": load(f"{ray}_energy{sfx}"), "crystal": load(f"{ray}_crystal{sfx}")})
                label = f"{ray.split('_')[0]}{' scoped' if scoped else ''}{' charging' if hot else ''}"
                m = view if not hot else side

                def v(p, m=m):
                    return drill._app(m, [p[0] / 16 - 0.5, p[1] / 16 - 0.35, p[2] / 16 - 0.45])
                panels.append((label + (" (side)" if hot else " (3/4)"),
                               drill._render_mixed([(quads, v)], lambda q: (150 + q[0] * 230, 150 - q[1] * 230),
                                                   (300, 300), bg)))

    # First and third person with the Minimizer.
    quads = drill.model_quads(base_model(True), {"parts": parts_tex, "energy": load("minimizer_ray_energy"),
                                                  "crystal": load("minimizer_ray_crystal")})
    fp, _ = drill.display_fn(DISPLAY["firstperson_righthand"])
    Wd, Hd = 480, 300
    f = (Hd / 2) / math.tan(math.radians(35))

    def fpv(p):
        q = fp(p)
        return [q[0] + 0.56, q[1] - 0.52, q[2] - 0.72]

    def persp(q):
        z = -q[2] if q[2] < -0.01 else 0.01
        return (Wd / 2 + q[0] / z * f, Hd / 2 - q[1] / z * f)
    fp_img = drill._render_mixed([(quads, fpv)], persp, (Wd, Hd), (120, 170, 220, 255))
    d = ImageDraw.Draw(fp_img)
    d.line([(Wd / 2 - 8, Hd / 2), (Wd / 2 + 8, Hd / 2)], fill=(255, 255, 255, 255), width=2)
    d.line([(Wd / 2, Hd / 2 - 8), (Wd / 2, Hd / 2 + 8)], fill=(255, 255, 255, 255), width=2)

    tp, _ = drill.display_fn(DISPLAY["thirdperson_righthand"])
    hand = (6 / 16, 2 / 16, 12 / 16)

    def player_frame(p):
        return [p[0], p[2], -p[1]]
    cam = drill._mul(drill._rot("x", 8), drill._rot("y", -90))

    def world(w):
        return drill._app(cam, [w[0], w[1] - 1.0, w[2]])

    def tpv(p):
        q = tp(p)
        return world(player_frame([hand[0] + q[0], hand[1] + q[1], hand[2] + q[2]]))
    steve = []
    for frm, to, col in (((-4, -2, 0), (4, 2, 12), (60, 60, 150, 255)), ((-4, -2, 12), (4, 2, 24), (60, 170, 180, 255)),
                         ((-4, -4, 24), (4, 4, 32), (190, 140, 110, 255)), ((4, -2, 12), (8, 2, 24), (60, 170, 180, 255)),
                         ((1, 4, 27), (3, 4.3, 28), (40, 40, 90, 255))):
        for corners, c, n, e in drill._cuboid_quads(frm, to, col):
            steve.append(([[v / 16 for v in pt] for pt in corners], c, n, e))
    tp_img = drill._render_mixed([(quads, tpv), (steve, lambda p: world(player_frame(p)))],
                                 lambda q: (200 + q[0] * 170, 250 - q[1] * 170), (400, 460), bg)

    # Icons: x8 and x1 in slots.
    icons = Image.new("RGBA", (560, 300), (139, 139, 139, 255))
    for i, name in enumerate(("minimizer_ray_icon", "minimizer_ray_scoped_icon", "maximizer_ray_icon",
                              "maximizer_ray_scoped_icon")):
        im = load(name)
        icons.alpha_composite(im.resize((128, 128), Image.NEAREST), (8 + i * 138, 20))
        icons.alpha_composite(im.resize((32, 32), Image.NEAREST), (8 + i * 138 + 48, 170))
        icons.alpha_composite(im, (8 + i * 138 + 56, 230))

    pad, lh = 10, 16
    rows = [panels[:3], panels[3:], [("first person (scoped minimizer)", fp_img),
                                     ("third person, from the right", tp_img), ("gui icons x8 / x2 / x1", icons)]]
    width = max(sum(p[1].width for p in row) + pad * (len(row) + 1) for row in rows)
    height = sum(max(p[1].height for p in row) + lh + pad for row in rows) + pad
    out = Image.new("RGBA", (width, height), (30, 32, 36, 255))
    d = ImageDraw.Draw(out)
    y = pad
    for row in rows:
        x = pad
        for name, im in row:
            d.text((x, y), name, fill=(230, 230, 230, 255))
            out.alpha_composite(im, (x, y + lh))
            x += im.width + pad
        y += max(p[1].height for p in row) + lh + pad
    out.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(ROOT / "tools" / "ray_preview.png")
    else:
        print(__doc__)

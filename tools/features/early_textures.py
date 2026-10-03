#!/usr/bin/env python3
"""Textures of the early and mid-game additions (see tools/features/early.py for the models).

Stone age: sieve mesh, drying-rack hides, water-wheel paddles, windmill plaster, roof and sails.
Electric: charger pad and front, floodlight lens and housing. Automation (aluminium casing):
block breaker, block placer, vacuum hopper, tree farm. Industrial (titanium casing): industrial
grinder, recycler, mob farm controller. Items: bronze armour (from the vanilla iron icons, re-toned
to bronze), bronze backpack, grappling hook, item magnet, night-vision goggles, tin nugget, scrap,
scrap box; the bronze armour and goggles layers worn on the player.

The machines reuse the age casings drawn by tools/gen_textures.py (steel for Electric, aluminium
for Automation, titanium for Industrial) and add one front per machine, so each still reads as a
different machine. Carved windows are declared in LAYOUT here and merged into gen_textures.LAYOUT,
which the model carving (gen_models.carved) and the window rims both read.

Run:  python3 tools/features/early_textures.py        (redraws all of them)
gen_resources.py only draws the ones that are missing.
"""
import io
import math
import sys
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
TEX = TOOLS.parent / "src/main/resources/assets/factoryascent/textures"
CLIENT_JAR = Path("/root/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")

LAYOUT = {
    "block_breaker": {"north": [(3, 3, 12, 12, 3)]},
    "block_placer": {"north": [(4, 4, 11, 11, 2)]},
    "tree_farm": {"north": [(2, 4, 13, 11, 3)], "up": [(3, 3, 12, 12, 1)]},
    "industrial_grinder": {"north": [(2, 2, 13, 10, 4)], "up": [(4, 4, 11, 11, 3)]},
    "recycler": {"north": [(3, 3, 12, 8, 3), (3, 11, 12, 13, 1)], "up": [(5, 5, 10, 10, 2)]},
    "mob_farm": {"north": [(3, 2, 12, 12, 2)]},
    "vacuum_hopper_box": {"up": [(3, 3, 12, 12, 2)]},
}


def gt():
    import gen_textures
    for k, v in LAYOUT.items():
        gen_textures.LAYOUT.setdefault(k, v)
    return gen_textures


# ============================================================ small helpers

def disc(t, cx, cy, r, col, r0=-1.0):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 < d <= r:
                t.set(x, y, col)


def vanilla(path):
    from PIL import Image
    with zipfile.ZipFile(CLIENT_JAR) as z:
        return Image.open(io.BytesIO(z.read(path))).convert("RGBA")


def retone(img, ramp):
    """Maps every opaque pixel's brightness onto a colour ramp (dark .. light)."""
    out = img.copy()
    px = out.load()
    lum = [0.299 * r + 0.587 * g + 0.114 * b for (r, g, b, a) in (img.getpixel((x, y)) for y in range(img.height) for x in range(img.width)) if a > 0]
    lo, hi = min(lum), max(lum)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / max(1, hi - lo)
            f = t * (len(ramp) - 1)
            i = min(len(ramp) - 2, int(f))
            c0, c1 = ramp[i], ramp[i + 1]
            k = f - i
            px[x, y] = tuple(int(round(c0[j] + (c1[j] - c0[j]) * k)) for j in range(3)) + (a,)
    return out


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


BRONZE_RAMP = [rgb(h) for h in ("#2E1A08", "#5A3812", "#8C5A22", "#B87E36", "#D8A457", "#F2CE86", "#FFF0C4")]
TIN_RAMP = [rgb(h) for h in ("#2E343C", "#56606C", "#86909C", "#B0BAC4", "#D2DAE2", "#F4F8FC")]


# ============================================================ stone age

def sieve_mesh():
    G = gt()
    t = G.Tex()
    string, lo, hi = G.C("#E8E2D0"), G.C("#B8B0A0"), G.C("#FFFFFF")
    for i in range(0, 16, 3):
        for j in range(16):
            t.set(i, j, lo)
            t.set(j, i, string)
    for i in range(0, 16, 3):
        for j in range(0, 16, 3):
            t.set(i, j, hi)
    return t


def drying_rack_hides():
    """Strips hanging from the rack's poles: leather hides and kelp, transparent between them."""
    G = gt()
    t = G.Tex()
    rng = G.rng_for("rack/hides")
    strips = [(1, 3, "#8A5230", "#6A3A1E", 12), (5, 2, "#4E7A2A", "#335A1A", 10), (9, 3, "#9A6038", "#744424", 13),
              (13, 2, "#557F2E", "#3A5E1E", 9)]
    for x0, w, c, d, length in strips:
        for y in range(length):
            for x in range(x0, min(16, x0 + w)):
                col = c if (x - x0) < w - 1 else d
                if y == length - 1 and (x + y) % 2:
                    continue
                t.set(x, y, col if rng.random() > 0.15 else G.darken(col, 0.2))
        t.hline(x0, min(15, x0 + w - 1), 0, "#3A2410")  # folded over the pole
    return t


def water_wheel_paddle():
    """Wet dark oak boards with two iron straps."""
    G = gt()
    t = G.Tex(fill="#4A3218")
    rng = G.rng_for("wheel/paddle")
    for y in range(16):
        for x in range(16):
            base = ["#5A3E20", "#4E361C", "#624626"][(x // 4) % 3]
            t.set(x, y, base if rng.random() > 0.18 else "#3E2A14")
        if y % 5 == 0:
            t.hline(0, 15, y, "#2E1E0E")
    for x in (3, 12):
        t.vline(x, 0, 15, "#6E7278")
        t.vline(x + 1, 0, 15, "#43464C")
        for y in (2, 7, 12):
            t.set(x, y, "#A8ACB2")
    for y in range(12, 16):  # wet line along the bottom
        for x in range(16):
            if rng.random() < 0.3:
                t.set(x, y, "#2C5A7A")
    return t


def windmill_side():
    """Whitewashed plaster between dark timber framing, with a little shuttered window."""
    G = gt()
    t = G.Tex(fill="#E6DFCF")
    rng = G.rng_for("windmill/plaster")
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.2:
                t.set(x, y, rng.choice(["#D8D0BE", "#EFE9DC", "#CFC6B2"]))
    timber, dark = "#5A3A1E", "#3E2610"
    t.rect(0, 0, 1, 15, timber); t.rect(14, 0, 15, 15, timber)
    t.hline(0, 15, 0, dark); t.hline(0, 15, 15, dark); t.hline(0, 15, 8, timber)
    for i in range(7):  # diagonal braces
        t.set(2 + i, 1 + i, timber); t.set(13 - i, 1 + i, timber)
    t.rect(6, 10, 9, 13, "#2A1A0E")  # window
    t.rect(7, 11, 8, 12, "#6A8AA0")
    return t


def windmill_roof():
    G = gt()
    t = G.Tex(fill="#7A4A2A")
    for y in range(16):
        for x in range(16):
            row = y // 3
            c = ["#8C5632", "#7A4A2A", "#6A3E22"][(x + row * 2) % 3]
            t.set(x, y, c)
        if y % 3 == 2:
            t.hline(0, 15, y, "#4A2A14")
    return t


def windmill_sail():
    """Canvas stretched over a wooden lattice."""
    G = gt()
    t = G.Tex(fill="#EDE6D6")
    rng = G.rng_for("windmill/sail")
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.15:
                t.set(x, y, "#DCD3C0")
    for i in range(0, 16, 5):
        t.hline(0, 15, i, "#8A6038"); t.vline(i, 0, 15, "#8A6038")
    t.vline(15, 0, 15, "#6A4626"); t.hline(0, 15, 15, "#6A4626")
    return t


# ============================================================ electric age

def charger_top(on):
    G = gt()
    t = G.casing_face(G.STEEL_T, G.rng_for("charger/top"))
    G.recess(t, 3, 3, 12, 12, G.STEEL_T, fill=G.VOID)
    ring = G.C("#FFE04A") if on else G.C("#6A5A20")
    disc(t, 8, 8, 4.4, ring, 3.2)
    disc(t, 8, 8, 3.2, G.C("#2A2E36"))
    if on:
        disc(t, 8, 8, 1.6, G.C("#FFF6B0"))
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        t.set(x, y, "#C0C8D2")
    return t


def charger_front(on):
    G = gt()
    t = G.casing_face(G.STEEL_T, G.rng_for("charger/front"), rivets=((2, 8), (12, 8)))
    G.recess(t, 3, 8, 12, 12, G.STEEL_T, fill="#10161C")
    # bar gauge of the charge
    for i, x in enumerate(range(4, 12)):
        lit = on and i < 6
        t.vline(x, 9, 11, ("#3CE05A" if i < 3 else "#E8D040" if i < 6 else "#E06030") if lit else "#243028")
    G.status_led(t, 12, 3, on)
    G.hazard_strip(t, 3, 3, 9, 4)
    return t


def floodlight_lens(on):
    G = gt()
    t = G.Tex(fill="#2A2E36")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 6.8:
                if on:
                    c = G.mix("#FFFFFF", "#FFE9A0", min(1, d / 6.8))
                else:
                    c = G.mix("#B8C4CC", "#6A7680", min(1, d / 6.8))
                if (x + y) % 4 == 0 and d > 2:
                    c = G.darken(c, 0.08)
                t.set(x, y, c)
            elif d < 7.8:
                t.set(x, y, "#9AA2AE")
    return t


def floodlight_housing():
    G = gt()
    t = G.Tex(fill="#3A3F48")
    for y in range(16):
        c = "#4C525C" if y % 3 == 0 else "#30343C" if y % 3 == 2 else "#3A3F48"
        t.hline(0, 15, y, c)
    t.vline(0, 0, 15, "#262A30"); t.vline(15, 0, 15, "#262A30")
    t.set(7, 7, G.C("#E0C040")); t.set(8, 7, G.C("#E0C040"))
    return t


# ============================================================ automation age (aluminium)

def alu_side():
    G = gt()
    return G.family_side(G.ALU_T, G.rng_for("alu/side"), "aluminum")


def alu_top():
    G = gt()
    return G.casing_top(G.ALU_T, G.rng_for("alu/top"))


def alu_bottom():
    G = gt()
    return G.casing_bottom(G.ALU_T, G.rng_for("alu/bottom"))


def alu_inner():
    G = gt()
    return G.inner_tex("#3A4048", G.rng_for("alu/inner"))


def breaker_front(on):
    G = gt()
    t = G.casing_face(G.ALU_T, G.rng_for("breaker/front"), "block_breaker")
    u0, v0, u1, v1, _ = G.win_list("block_breaker")[0]
    G.void_fill(t, u0, v0, u1, v1)
    G.win_shadow(t, u0, v0, u1, v1)
    for u in range(u0 - 1, u1 + 2):  # hazard frame around the mouth
        t.set(u, v0 - 2, G.HAZARD_Y[1] if u % 2 else G.HAZARD_K[0])
        t.set(u, v1 + 2, G.HAZARD_Y[1] if u % 2 else G.HAZARD_K[0])
    if on:
        G.grit(t, G.rng_for("breaker/grit"), u0 + 1, v0 + 1, u1 - 1, v1 - 1, 8)
    G.status_led(t, 13, 1, on)
    return t


def breaker_pick():
    G = gt()
    t = G.Tex(fill="#9AA2AE")
    for y in range(16):
        for x in range(16):
            t.set(x, y, G.STEEL[1] if (x + y) % 5 == 0 else G.STEEL[2] if x < 8 else G.STEEL[3])
    return t


def placer_front(on):
    G = gt()
    t = G.casing_face(G.ALU_T, G.rng_for("placer/front"), "block_placer")
    u0, v0, u1, v1, _ = G.win_list("block_placer")[0]
    G.void_fill(t, u0, v0, u1, v1)
    G.win_shadow(t, u0, v0, u1, v1)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):  # arrows pointing out of the face
        t.set(x, y, "#62D0FF")
    t.hline(6, 9, 13, "#62D0FF" if on else "#3A6A80")
    t.set(7, 14, "#62D0FF" if on else "#3A6A80"); t.set(8, 14, "#62D0FF" if on else "#3A6A80")
    return t


def placer_plate():
    G = gt()
    t = G.Tex(fill="#B8A078")
    for y in range(16):
        for x in range(16):
            t.set(x, y, "#C8B088" if (x // 2 + y) % 4 else "#A89068")
    t.rect(0, 0, 15, 1, "#6A6E76"); t.rect(0, 14, 15, 15, "#6A6E76")
    return t


def vacuum_top(on):
    G = gt()
    t = G.casing_face(G.ALU_T, G.rng_for("vacuum/top"), "vacuum_hopper_box", "up")
    u0, v0, u1, v1, _ = G.win_list("vacuum_hopper_box", "up")[0]
    G.void_fill(t, u0, v0, u1, v1)
    # a four-bladed intake fan under a grille
    ang = 0.4 if on else 0.0
    for y in range(v0, v1 + 1):
        for x in range(u0, u1 + 1):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            a = (math.atan2(dy, dx) + ang) % (math.pi / 2)
            r = math.hypot(dx, dy)
            if 1.2 < r < 4.6 and a < 0.7:
                t.set(x, y, "#9AD4FF" if on else "#8A96A4")
            elif r <= 1.2:
                t.set(x, y, "#D6DEE6")
    for y in range(v0, v1 + 1, 2):
        t.hline(u0, u1, y, G.mix(t.get(u0, y), "#C4CCD6", 0.6))
    return t


def vacuum_side(on):
    G = gt()
    t = G.casing_face(G.ALU_T, G.rng_for("vacuum/side"))
    for x in range(3, 13, 2):  # suction slots
        t.vline(x, 4, 9, G.VOID)
    t.hline(2, 13, 11, "#4CB050" if on else "#2E6A30")
    t.hline(2, 13, 12, "#2E6A30")
    return t


def tree_farm_front(on):
    G = gt()
    t = G.casing_face(G.ALU_T, G.rng_for("tree/front"), "tree_farm")
    u0, v0, u1, v1, _ = G.win_list("tree_farm")[0]
    G.void_fill(t, u0, v0, u1, v1)
    G.win_shadow(t, u0, v0, u1, v1)
    if on:
        G.grit(t, G.rng_for("tree/chips"), u0 + 1, v1 - 2, u1 - 1, v1, 9, cols=("#C8A064", "#A07A44", "#E0C08A"))
    t.hline(2, 13, 13, "#4CB050"); t.hline(2, 13, 14, "#2E6A30")
    for x in (3, 6, 9, 12):
        t.set(x, 2, "#6A8A3A")  # little leaf marks along the top
    return t


def tree_farm_top():
    G = gt()
    t = G.casing_face(G.ALU_T, G.rng_for("tree/top"), "tree_farm", "up")
    u0, v0, u1, v1, _ = G.win_list("tree_farm", "up")[0]
    rng = G.rng_for("tree/soil")
    for y in range(v0, v1 + 1):
        for x in range(u0, u1 + 1):
            t.set(x, y, rng.choice(["#6A4A2E", "#5A3E24", "#7A5636"]))
    for (x, y) in ((6, 6), (9, 9), (6, 10), (10, 5)):  # seedlings
        t.set(x, y, "#4CB050"); t.set(x + 1, y, "#2E8A3A"); t.set(x, y - 1, "#7AD06A")
    return t


def tree_farm_saw(on):
    G = gt()
    t = G.Tex(fill="#8A929E")
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi) * 12 + (0.5 if on else 0)) % 1.0
            if r > 7.2 - (1.2 if a < 0.5 else 0):
                t.set(x, y, "#5A606A")
            elif r < 2:
                t.set(x, y, "#3A3E46")
            else:
                t.set(x, y, "#C4CCD6" if (int(r) + (1 if a < 0.5 else 0)) % 3 == 0 else "#A6AEBA")
    return t


# ============================================================ industrial age (titanium)

def ti_top():
    G = gt()
    return G.casing_top(G.TITAN_T, G.rng_for("ti/top"))


def grinder_front(on):
    G = gt()
    t = G.ti_face(G.rng_for("grinder/front"), "industrial_grinder")
    u0, v0, u1, v1, _ = G.win_list("industrial_grinder")[0]
    G.void_fill(t, u0, v0, u1, v1)
    if on:
        G.grit(t, G.rng_for("grinder/grit"), u0, v1 - 2, u1, v1, 12)
    G.win_shadow(t, u0, v0, u1, v1)
    G.hazard_strip(t, 3, 12, 12, 13)
    G.gauge(t, 12, 12, G.TITAN_T, "#D02010" if on else "#606060")
    return t


def grinder_top():
    G = gt()
    return G.hopper_top(G.TITAN_T, G.rng_for("grinder/top"), "industrial_grinder",
                        ["#5A5A60", "#747478", "#C87438", "#B8B8BE", "#3A3430", "#E0B024"])


def grinder_drum(on):
    G = gt()
    t = G.Tex(fill=G.STEEL[2])
    for y in range(16):
        for x in range(16):
            t.set(x, y, G.STEEL[1] if (x + (2 if on else 0)) % 4 == 0 else G.STEEL[3] if (x + (2 if on else 0)) % 4 == 2 else G.STEEL[2])
    return t


def grinder_drum_end(on):
    G = gt()
    t = G.Tex(fill=G.STEEL[3])
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi) * 10 + (0.5 if on else 0)) % 1.0
            if r < 2.2:
                t.set(x, y, G.TITAN_T.accent)
            elif r < 3:
                t.set(x, y, G.STEEL[4])
            else:
                t.set(x, y, G.STEEL[1] if a < 0.5 else G.STEEL[3])
    return t


def recycler_front(on):
    G = gt()
    t = G.ti_face(G.rng_for("recycler/front"), "recycler")
    (u0, v0, u1, v1, _), (c0, d0, c1, d1, _) = G.win_list("recycler")
    G.void_fill(t, u0, v0, u1, v1)
    for u in range(u0, u1 + 1):  # shredder teeth biting down and up
        if u % 2 == 0:
            t.set(u, v0, G.STEEL[1]); t.set(u, v0 + 1, G.STEEL[2])
        else:
            t.set(u, v1, G.STEEL[1]); t.set(u, v1 - 1, G.STEEL[2])
    if on:
        G.grit(t, G.rng_for("recycler/bits"), u0 + 1, v0 + 2, u1 - 1, v1 - 2, 6,
               cols=("#8A8A90", "#C87438", "#6A5A4A", "#B8B8BE"))
    for u in range(c0, c1 + 1):  # conveyor
        for v in range(d0, d1 + 1):
            t.set(u, v, "#2A2A30" if (u + (1 if on else 0)) % 3 else "#4A4A52")
    G.status_led(t, 13, 1, on)
    return t


def recycler_top():
    G = gt()
    t = G.casing_face(G.TITAN_T, G.rng_for("recycler/top"), "recycler", "up")
    u0, v0, u1, v1, _ = G.win_list("recycler", "up")[0]
    G.void_fill(t, u0, v0, u1, v1)
    green = G.C("#4CB050")
    # three chasing arrows round the intake
    for (x, y) in ((4, 3), (5, 3), (6, 3), (11, 4), (12, 5), (12, 6), (3, 12), (3, 11), (4, 12), (11, 12), (12, 11), (12, 12)):
        t.set(x, y, green)
    return t


def mob_farm_front(on):
    G = gt()
    t = G.ti_face(G.rng_for("mobfarm/front"), "mob_farm")
    u0, v0, u1, v1, _ = G.win_list("mob_farm")[0]
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            t.set(u, v, G.mix("#2A1A3A", "#140C1E", (v - v0) / (v1 - v0)))
    # a ghostly mob silhouette in the chamber (brighter while farming)
    body = "#B070E0" if on else "#4A2E6A"
    shape = ["..####..", "..#..#..", "..####..", "...##...", ".######.", "#.####.#", "..#..#..", "..#..#.."]
    for j, row in enumerate(shape):
        for i, ch in enumerate(row):
            if ch == "#":
                t.set(u0 + 1 + i, v0 + 1 + j, body)
    for u in range(u0, u1 + 1, 3):  # cage bars
        t.vline(u, v0, v1, G.STEEL[3])
    t.hline(u0, u1, v0, G.STEEL[3])
    G.status_led(t, 13, 13, on)
    return t


def mob_farm_top():
    G = gt()
    t = G.ti_face(G.rng_for("mobfarm/top"), None, "up")
    G.recess(t, 5, 5, 10, 10, G.TITAN_T, fill="#1A1420")
    disc(t, 8, 8, 2.6, G.C("#9FD8E8"))
    t.hline(5, 10, 8, "#8D949E")
    return t


# ============================================================ items

def item_canvas():
    G = gt()
    return G.Tex()


def backpack():
    G = gt()
    t = G.Tex()
    L, D, H = "#8A5430", "#5E3418", "#B07440"
    B, BD = "#D8A457", "#8C5A22"
    rows = [
        "....DDDDDD......",
        "...DLLLLLLD.....",
        "...D......D.....",
        "..DDDDDDDDDD....",
        ".DHHHHHHHHHHD...",
        ".DLLLLLLLLLLD...",
        ".DLLLbBBbLLLD...",
        ".DLLLBOOBLLLD...",
        ".DLLLbBBbLLLD...",
        ".DDDDDDDDDDDD...",
        ".DLLLLLLLLLLD...",
        ".DLLLLLLLLLLD...",
        ".DHLLLLLLLLLD...",
        "..DDDDDDDDDD....",
    ]
    t.sprite(rows, {"D": D, "L": L, "H": H, "B": B, "b": BD, "O": "#6E4617"}, 1, 1)
    return t.outline(0.4)


def grappling_hook():
    G = gt()
    t = G.Tex()
    rows = [
        "...........bBb..",
        "..........bB..B.",
        "...........BBbB.",
        "..........bB..b.",
        ".........s......",
        "........s.......",
        ".......s........",
        "......s.........",
        ".....s..........",
        "....s...........",
        "...WW...........",
        "..WWW...........",
        ".WWW............",
        ".WW.............",
    ]
    t.sprite(rows, {"B": "#D8A457", "b": "#8C5A22", "s": "#E8E2D0", "W": "#6A4626"}, 0, 1)
    return t.outline(0.45)


def item_magnet():
    G = gt()
    t = G.Tex()
    rows = [
        "..RRR....RRR....",
        "..RRR....RRR....",
        "..SSS....SSS....",
        "..rrr....rrr....",
        "..rrr....rrr....",
        "..rrr....rrr....",
        "..rrr....rrr....",
        "..rrrr..rrrr....",
        "...rrrrrrrr.....",
        "....rrrrrr......",
        ".....YYYY.......",
        ".....YggY.......",
        ".....YYYY.......",
    ]
    t.sprite(rows, {"R": "#E84040", "S": "#C8CED6", "r": "#B02828", "Y": "#E0C040", "g": "#3CE05A"}, 1, 1)
    for (x, y) in ((3, 4), (3, 5), (3, 6), (10, 4)):
        t.set(x, y, "#D84848")
    return t.outline(0.45)


def goggles():
    G = gt()
    t = G.Tex()
    rows = [
        "................",
        "................",
        "................",
        "................",
        "sssssssssssssss.",
        "sFFFFs...sFFFFs.",
        "FGGGGF...FGGGGF.",
        "FGgGGFbbbFGgGGF.",
        "FGGGGF...FGGGGF.",
        ".FFFF.....FFFF..",
    ]
    t.sprite(rows, {"s": "#5A3A20", "F": "#3A3F48", "G": "#3CD86A", "g": "#C8FFD0", "b": "#8D949E"}, 0, 1)
    return t.outline(0.45)


def scrap():
    G = gt()
    t = G.Tex()
    rng = G.rng_for("scrap")
    pieces = [(3, 4, 5, 3, "#8A8A90"), (7, 6, 5, 4, "#C87438"), (4, 9, 4, 3, "#6A6A72"), (9, 10, 4, 3, "#B8B8BE"),
              (6, 3, 3, 2, "#5A4A3A")]
    for x0, y0, w, h, c in pieces:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if rng.random() > 0.12:
                    t.set(x, y, c if rng.random() > 0.3 else G.darken(c, 0.25))
    t.set(8, 7, "#F0A868"); t.set(10, 11, "#FFFFFF")
    return t.outline(0.45)


def scrap_box():
    G = gt()
    t = G.Tex()
    for y in range(3, 14):
        for x in range(2, 14):
            t.set(x, y, "#8A6A3A" if (x + y) % 5 else "#7A5A2E")
    t.hline(2, 13, 3, "#B08A52"); t.hline(2, 13, 13, "#5A3E1E")
    t.vline(2, 3, 13, "#A07E48"); t.vline(13, 3, 13, "#5A3E1E")
    t.hline(2, 13, 8, "#6A7078")
    for (x, y) in ((5, 1), (6, 2), (9, 2), (10, 1), (7, 1)):  # scrap poking out of the lid
        t.set(x, y, "#B8B8BE")
    t.set(8, 2, "#C87438")
    t.set(7, 10, "#4CB050"); t.set(8, 10, "#4CB050"); t.set(8, 11, "#4CB050")
    return t.outline(0.4)


def retoned_item(name, ramp):
    return lambda: retone(vanilla(f"assets/minecraft/textures/item/{name}.png"), ramp)


def goggles_layer():
    """The goggles on the head: a leather strap all round and two green lenses on the face."""
    from PIL import Image
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    strap, lens, rim, glow = (90, 58, 32, 255), (60, 216, 106, 255), (58, 63, 72, 255), (200, 255, 208, 255)
    # head box: front face at (8..15, 8..15), sides (0..7) and (16..23), back (24..31); strap row y=11..12
    for x in range(0, 32):
        for y in (11, 12):
            px[x, y] = strap
    for cx in (9, 13):  # two lenses on the front face
        for x in range(cx, cx + 3):
            for y in range(10, 13):
                px[x, y] = rim
        px[cx + 1, 11] = lens
        px[cx + 1, 10] = glow
    return img


def bronze_layer(which):
    # The worn layer skips the two darkest stops: the full ramp made the armour read as dark leather
    # next to the pale-gold bronze items.
    return lambda: retone(vanilla(f"assets/minecraft/textures/entity/equipment/{which}/iron.png"), BRONZE_RAMP[2:])


BLOCKS = {
    "sieve_mesh": sieve_mesh, "drying_rack_hides": drying_rack_hides, "water_wheel_paddle": water_wheel_paddle,
    "windmill_side": windmill_side, "windmill_roof": windmill_roof, "windmill_sail": windmill_sail,
    "charger_top": lambda: charger_top(False), "charger_top_on": lambda: charger_top(True),
    "charger_front": lambda: charger_front(False), "charger_front_on": lambda: charger_front(True),
    "floodlight_lens": lambda: floodlight_lens(False), "floodlight_lens_on": lambda: floodlight_lens(True),
    "floodlight_housing": floodlight_housing,
    "alu_casing_side": alu_side, "alu_casing_top": alu_top, "alu_casing_bottom": alu_bottom, "alu_casing_inner": alu_inner,
    "block_breaker_front": lambda: breaker_front(False), "block_breaker_front_on": lambda: breaker_front(True),
    "block_breaker_pick": breaker_pick,
    "block_placer_front": lambda: placer_front(False), "block_placer_front_on": lambda: placer_front(True),
    "block_placer_plate": placer_plate,
    "vacuum_hopper_top": lambda: vacuum_top(False), "vacuum_hopper_top_on": lambda: vacuum_top(True),
    "vacuum_hopper_side": lambda: vacuum_side(False), "vacuum_hopper_side_on": lambda: vacuum_side(True),
    "tree_farm_front": lambda: tree_farm_front(False), "tree_farm_front_on": lambda: tree_farm_front(True),
    "tree_farm_top": tree_farm_top,
    "tree_farm_saw": lambda: tree_farm_saw(False), "tree_farm_saw_on": lambda: tree_farm_saw(True),
    "ti_casing_top": ti_top,
    "industrial_grinder_front": lambda: grinder_front(False), "industrial_grinder_front_on": lambda: grinder_front(True),
    "industrial_grinder_top": grinder_top,
    "industrial_grinder_drum": lambda: grinder_drum(False), "industrial_grinder_drum_on": lambda: grinder_drum(True),
    "industrial_grinder_drum_end": lambda: grinder_drum_end(False),
    "industrial_grinder_drum_end_on": lambda: grinder_drum_end(True),
    "recycler_front": lambda: recycler_front(False), "recycler_front_on": lambda: recycler_front(True),
    "recycler_top": recycler_top,
    "mob_farm_front": lambda: mob_farm_front(False), "mob_farm_front_on": lambda: mob_farm_front(True),
    "mob_farm_top": mob_farm_top,
}
ITEMS = {
    "bronze_helmet": retoned_item("iron_helmet", BRONZE_RAMP),
    "bronze_chestplate": retoned_item("iron_chestplate", BRONZE_RAMP),
    "bronze_leggings": retoned_item("iron_leggings", BRONZE_RAMP),
    "bronze_boots": retoned_item("iron_boots", BRONZE_RAMP),
    "tin_nugget": retoned_item("iron_nugget", TIN_RAMP),
    "bronze_backpack": backpack, "grappling_hook": grappling_hook, "item_magnet": item_magnet,
    "night_vision_goggles": goggles, "scrap": scrap, "scrap_box": scrap_box,
}
ENTITY = {
    "humanoid/bronze": bronze_layer("humanoid"),
    "humanoid_leggings/bronze": bronze_layer("humanoid_leggings"),
    "humanoid_baby/bronze": bronze_layer("humanoid_baby"),
    "humanoid/night_vision_goggles": goggles_layer,
}


def paths():
    for name, fn in BLOCKS.items():
        yield TEX / "block" / f"{name}.png", fn
    for name, fn in ITEMS.items():
        yield TEX / "item" / f"{name}.png", fn
    for name, fn in ENTITY.items():
        yield TEX / "entity" / "equipment" / f"{name}.png", fn


def draw(only_missing):
    n = 0
    for path, fn in paths():
        if only_missing and path.exists():
            continue
        img = fn()
        if hasattr(img, "image"):
            img = img.image()
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        n += 1
    return n


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    print(f"wrote {draw(only_missing=False)} early/mid-game textures")

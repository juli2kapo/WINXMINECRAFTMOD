#!/usr/bin/env python3
"""Trains: the Steam Locomotive (Bronze age), the Diesel-Electric Locomotive (Automation age), the
Passenger Car, Cargo Wagon, Tank Wagon, Hopper Wagon, the Coupler and the Train Station.
Java: net.juli2kapo.factoryascent.trains (+ trains.client).

Models
  Every vehicle is drawn by TrainRenderer from standalone block models ("parts"), built here in
  *train pixels*: x across (+x = left when facing forward), y up (0 = the top of the rails, where
  the wheels touch), z forward (the locomotive's chimney / nose end). Parts are cut into 48-px
  cells like the ships' (ships.split_cells) and listed in assets/factoryascent/trains/parts.json,
  which also carries each vehicle's LAYOUT: its static parts, its wheelsets (turned by the
  renderer about their axle) and bogies. Animated pieces (wheels, the steam engine's coupling and
  connecting rods and piston rods, the diesel's radiator fans, the hopper's load and doors, the
  lamps and the headlight beam) are separate parts.

Resources: the Train Station's block model (+ lit variant), blockstate and loot table, item models,
recipes, lang (en + es), advancements.

Run `python3 tools/features/trains.py --preview` to render tools/trains_models_preview.png.
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
    spec = importlib.util.spec_from_file_location(f"trains_dep_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


SHIPS = _load("ships")
SAT = SHIPS.SAT
El, cube, ring, disc, turn, spin = SAT.El, SAT.cube, SAT.ring, SAT.disc, SAT.turn, SAT.spin
_rot, _mul, _app = SAT._rot, SAT._mul, SAT._app
box, tiled_box, rod, plank, mirror_x, tube_z = SHIPS.box, SHIPS.tiled_box, SHIPS.rod, SHIPS.plank, SHIPS.mirror_x, SHIPS.tube_z

PICKAXE_BLOCKS = ["train_station"]

TEX = {k: f"{MOD}:block/train_{v}" for k, v in {
    "green": "green", "boiler": "boiler", "black": "black", "smokebox": "smokebox", "brass": "brass", "red": "red",
    "cabwin": "cab_window", "roof": "roof", "coal": "coal", "wheel_big": "wheel_big", "wheel_small": "wheel_small",
    "rod": "rod", "lamp": "lamp", "lamp_off": "lamp_off", "beam": "beam", "glass": "glass", "yellow": "diesel",
    "stripe": "diesel_stripe", "grille": "grille", "fan": "fan", "dwin": "diesel_window", "coach": "coach",
    "door": "coach_window", "wood": "wood", "seat": "seat", "boxcar": "boxcar", "bdoor": "boxcar_door", "tank": "tank",
    "hopper": "hopper", "ore": "ore", "iron": "iron", "bogie": "bogie", "steel": "steel"}.items()}

FULL = [0, 0, 16, 16]


# ============================================================ shared pieces

def wheel_face(x0, x1, r, tex):
    """A round wheel (texture with transparent corners) centred on the origin, turning about x."""
    return El([x0, -r, -r], [x1, r, r], {"east": (tex, FULL), "west": (tex, FULL)})


def wheelset(r, tex, gauge=5.5, th=1.4):
    """Two wheels and their axle, centred on the axle (the renderer places and turns it)."""
    els = [wheel_face(gauge - th / 2, gauge + th / 2, r, tex), wheel_face(-gauge - th / 2, -gauge + th / 2, r, tex)]
    els.append(box(-gauge, -0.6, -0.6, gauge, 0.6, 0.6, "iron"))
    return els


def bogie_frame(axle_gap, r):
    """A two-axle bogie's side frames, springs and bolster, centred between its axles (at axle height)."""
    els = []
    half = axle_gap / 2 + r + 0.5
    for s in (1, -1):
        x0, x1 = 6.4 * s, 7.4 * s
        els.append(box(x0, -r * 0.55, -half, x1, r * 0.75, half, {"east": "bogie", "west": "bogie", "up": "iron",
                                                                 "down": "iron", "north": "iron", "south": "iron"}))
        for z in (-axle_gap / 2, axle_gap / 2):  # axle boxes
            els.append(box(x1 if s > 0 else x1 - 0.8, -1.2, z - 1.2, x1 + 0.8 if s > 0 else x1, 1.2, z + 1.2, "steel"))
    els.append(box(-6.4, r * 0.4, -1.5, 6.4, r * 0.75 + 0.6, 1.5, "iron"))  # bolster
    return els


def buffers(z_beam, side, y0, y1, half_w=7, beam_tex="red"):
    """Buffer beam (side +1 = front end at +z) and two sprung buffers."""
    els = []
    zb0, zb1 = (z_beam, z_beam + 1.5) if side > 0 else (z_beam - 1.5, z_beam)
    els.append(box(-half_w, y0, zb0, half_w, y1, zb1, beam_tex))
    zs0, zs1 = (zb1, zb1 + 1.0) if side > 0 else (zb0 - 1.0, zb0)
    for x in (4.5, -4.5):
        els.append(box(x - 0.6, (y0 + y1) / 2 - 0.6, zs0, x + 0.6, (y0 + y1) / 2 + 0.6, zs1, "rod"))
        hz0, hz1 = (zs1, zs1 + 0.5) if side > 0 else (zs0 - 0.5, zs0)
        els.append(box(x - 1.3, (y0 + y1) / 2 - 1.3, hz0, x + 1.3, (y0 + y1) / 2 + 1.3, hz1, "steel"))
    return els


def disc_z(x, y, z, r, tex, th=0.5, n=8):
    """An n-gon plate facing ±z (front face at z)."""
    els = disc(x, -y, z, r, tex, th=th, n=n)
    return turn(els, _rot("x", 90), [0, 0, 0])


def coupler():
    """The coupling (drawbar and knuckle) on a vehicle's front end, from the end (z = 0) outward."""
    return [box(-0.7, 5.3, -1.5, 0.7, 6.7, 1.6, "iron"), box(-1.3, 5.0, 1.2, 1.3, 7.0, 2.2, "steel")]


# ============================================================ steam locomotive (46 px over the buffers)

ST_AXLES = [(4, -11), (4, -1.5), (4, 8)]  # (y, z) of the driving axles, wheel radius 4
ST_CRANK = 2.2
ST_CYL_Y = 5.0
ST_MAINROD = 6.0
ST_ROD_X = 6.9


def steam_body():
    els = []
    # frames and running board
    for s in (1, -1):
        els.append(box(3 * s, 2.5, -20, 4 * s, 8, 20, "black"))
    els += tiled_box(-7, 8, -20.5, 7, 9, 20.5, "black")
    els += buffers(20.5, 1, 4, 8.5)
    els += buffers(-20.5, -1, 4, 8.5)
    # cylinders (outside, ahead of the front wheels) and their valve chests
    for s in (1, -1):
        els += tube_z(6.8 * s, ST_CYL_Y, 15.5, 20.5, 2.3, 2.3, "green", n=8, caps=True, th=0.6)
        els += disc_z(6.8 * s, ST_CYL_Y, 20.6, 2.4, "brass", th=0.3)
        els.append(box(5.5 * s, 7.2, 15.5, 7.8 * s, 8.2, 20.5, "black"))
    # boiler barrel, bands, smokebox and its door
    els += tube_z(0, 14, -5, 14, 5, 5, "boiler", n=10, caps=False, th=0.8)
    for z in (-4.5, 3.5, 13):
        els += tube_z(0, 14, z, z + 0.8, 5.25, 5.25, "brass", n=10, caps=False, th=0.3)
    els += tube_z(0, 14, 14, 20.2, 5.4, 5.4, "smokebox", n=10, caps=False, th=0.8)
    els += disc_z(0, 14, 20.4, 5.2, "smokebox", th=0.4, n=10)
    els += disc_z(0, 14, 20.7, 2.0, "black", th=0.3)
    els.append(box(-0.6, 10, 20.4, 0.6, 18, 20.9, "brass"))  # door handle bar
    # saddle under the smokebox
    els.append(box(-4, 9, 14.5, 4, 10.5, 20, "black"))
    # chimney with a brass-capped rim, dome, safety valves and whistle
    els += ring(0, 17.5, 18.5, 25, 1.7, 1.9, "smokebox", n=8, caps=False, th=0.5)
    els += ring(0, 17.5, 24.5, 26, 2.4, 2.4, "black", n=8, caps=True, th=0.6)
    els += ring(0, 4, 18.5, 21.5, 2.3, 1.8, "brass", n=8, caps=False, th=0.6)
    els += disc(0, 4, 22, 1.9, "brass", th=0.6)
    els += ring(0, -2.5, 18.5, 21, 0.7, 0.7, "brass", n=6, caps=True, th=0.4)
    els += ring(0, -4.2, 18.5, 22.5, 0.5, 0.4, "brass", n=6, caps=True, th=0.3)
    # side water tanks (a tank engine), brass-edged
    for s in (1, -1):
        els.append(box(4 * s, 9, -4, 7 * s, 16, 10.5, {"east": "green", "west": "green", "up": "black", "down": "black",
                                                    "north": "green", "south": "green"}))
        els.append(box(4 * s, 16, -4, 7.2 * s, 16.5, 10.5, "brass"))
        els.append(box(6.8 * s, 15.7, 1, 7.4 * s, 17.4, 2.5, "brass"))  # filler cap
    # cab: spectacle plate with windows, side sheets with an opening, back wall, roof
    els.append(box(-7, 9, -6, 7, 23, -5, {"south": "cabwin", "north": "cabwin", "east": "green", "west": "green",
                                         "up": "green", "down": "black"}))
    for s in (1, -1):
        els.append(box(6.2 * s, 9, -17, 7 * s, 15, -6, "green"))
        els.append(box(6.2 * s, 15, -7.5, 7 * s, 23, -6, "green"))
        els.append(box(6.2 * s, 15, -17, 7 * s, 23, -15.5, "green"))
        els.append(box(6.1 * s, 15, -15.5, 7.1 * s, 15.6, -7.5, "brass"))  # armrest
    els.append(box(-7, 9, -17.5, 7, 23, -16.5, {"north": "cabwin", "south": "green", "east": "green", "west": "green",
                                               "up": "green", "down": "black"}))
    els += tiled_box(-7.6, 23, -18.2, 7.6, 24.2, -4.4, "roof")
    els.append(box(-7.8, 22.6, -18.4, 7.8, 23.2, -4.2, "black"))
    # cab floor, firebox backhead and the driver's seat
    els.append(box(-6.2, 9, -16.5, 6.2, 9.4, -6, "wood"))
    els.append(box(-3, 9.4, -7.5, 3, 16, -6, {"north": "smokebox", "south": "black", "east": "black", "west": "black",
                                             "up": "black", "down": "black"}))
    els.append(box(-1, 11, -7.7, 1, 13, -7.5, "red", emissive=True))  # firebox glow
    els.append(box(-4.8, 9.4, -15.5, -1.2, 12, -12.5, "wood"))
    # coal bunker behind the cab
    els.append(box(-7, 9, -20.5, 7, 16, -17.5, "green"))
    els.append(box(-6.4, 15.6, -20.1, 6.4, 16.4, -17.8, "coal"))
    # sandboxes, handrails, steam pipe
    for s in (1, -1):
        els += rod([5.6 * s, 13, -4], [5.6 * s, 13, 19.5], 0.3, "brass")
        els.append(box(3 * s, 19, 6, 4.6 * s, 20.5, 9, "green"))
    return els


def steam_lamp(lit):
    return [box(-1.2, 19.4, 19.2, 1.2, 21.8, 21.2, "black"),
            box(-0.9, 19.7, 21.2, 0.9, 21.5, 21.5, "lamp" if lit else "lamp_off", emissive=lit),
            box(-1.2, 8.8, 21.9, 1.2, 10.8, 22.2, "lamp" if lit else "lamp_off", emissive=lit)]


def steam_rod(side):
    """Coupling rod linking the three crank pins on one side, at the pins' height (drawn parallel)."""
    x = ST_ROD_X * side
    z0, z1 = ST_AXLES[0][1], ST_AXLES[-1][1]
    els = [box(x - 0.35, -0.55, z0 - 0.8, x + 0.35, 0.55, z1 + 0.8, "rod")]
    for _, z in ST_AXLES:
        els.append(box(x - 0.6 * side if side > 0 else x, -0.8, z - 0.8, x if side > 0 else x + 0.6, 0.8, z + 0.8, "brass"))
    return els


def steam_mainrod(side):
    """Connecting rod: from the crank pin (origin) along +z, 16 px long (scaled by the renderer)."""
    x = (ST_ROD_X + 0.7) * side
    return [box(x - 0.3, -0.5, 0, x + 0.3, 0.5, 16, "rod")]


def steam_piston(side):
    """Crosshead and piston rod: from the crosshead (origin) into the cylinder."""
    x = (ST_ROD_X + 0.7) * side
    return [box(x - 0.6, -0.9, -0.9, x + 0.6, 0.9, 0.9, "brass"), box(x - 0.25, -0.25, 0, x + 0.25, 0.25, 6, "rod")]


# ============================================================ diesel-electric (52 px)

DI_BOGIES = [(2.8, -14.5), (2.8, 14.5)]
DI_AXLE_GAP = 7
DI_FANS = [(24.2, -16), (24.2, -7)]


def diesel_body():
    els = []
    els += tiled_box(-7, 7, -23.5, 7, 9, 23.5, "iron")
    els += buffers(23.5, 1, 5, 9, beam_tex="stripe")
    els += buffers(-23.5, -1, 5, 9, beam_tex="stripe")
    # fuel tank and battery boxes between the bogies
    els += tube_z(0, 5.2, -7, 7, 3.6, 3.6, "black", n=8, caps=True, th=0.6)
    for s in (1, -1):
        els.append(box(5.5 * s, 4.5, -9.5, 6.8 * s, 7, -7.5, "iron"))
    # long hood (behind the cab): stripe, grilles, doors
    for s in (1, -1):
        for z0 in range(-23, 9, 8):
            z1 = min(9, z0 + 8)
            tex = "grille" if -19 <= z0 <= -7 else "yellow"
            els.append(box(5.5 * s, 9, z0, 5.6 * s, 13, z1, "stripe"))
            els.append(box(5.5 * s, 13, z0, 5.6 * s, 24, z1, tex))
    els += tiled_box(-5.5, 9, -23, 5.5, 24, 9, "yellow")
    els += tiled_box(-5.7, 24, -23.2, 5.7, 24.3, 9, "roof")
    els.append(box(-5.7, 9, -23.6, 5.7, 24, -23, {"north": "stripe", "south": "yellow", "east": "yellow", "west": "yellow",
                                                 "up": "yellow", "down": "yellow"}))
    # exhaust stack, horn, radiator fan wells
    els += ring(0, 3, 24.2, 27.5, 0.9, 0.9, "black", n=6, caps=True, th=0.4)
    els.append(box(-2.2, 24.2, -1, -0.6, 25.4, 2.5, "steel"))  # horn
    for y, z in DI_FANS:
        els += ring(0, z, 24.2, 24.9, 3.4, 3.4, "iron", n=8, caps=True, th=0.4)
    # cab: lower and upper walls, corner pillars, roof (glass panes are a separate translucent part)
    els += tiled_box(-7, 9, 9, 7, 18, 17, "stripe")
    for s in (1, -1):
        els.append(box(6.2 * s, 18, 9, 7 * s, 24, 10, "yellow"))
        els.append(box(6.2 * s, 18, 16, 7 * s, 24, 17, "yellow"))
    els.append(box(-7, 18, 9, 7, 24, 9.8, "yellow"))
    els.append(box(-0.8, 18, 16.2, 0.8, 24, 17, "yellow"))
    els += tiled_box(-7, 24, 9, 7, 27, 17, "yellow")
    els += tiled_box(-7.4, 27, 8.6, 7.4, 28, 17.4, "roof")
    # short nose ahead of the cab, with the number board and headlight housing
    els += tiled_box(-5.5, 9, 17, 5.5, 17, 23, "stripe")
    els += tiled_box(-5.5, 17, 17, 5.5, 18, 23, "yellow")
    els.append(box(-1.8, 14.6, 23, 1.8, 18.2, 23.4, "black"))
    els.append(box(-2, 25, 17, 2, 26.6, 17.6, "black"))  # roof headlight housing
    # handrails along the walkways
    for s in (1, -1):
        els += rod([6.9 * s, 15, -23], [6.9 * s, 15, 8.5], 0.25, "steel")
        for z in (-22, -12, -2, 8):
            els += rod([6.9 * s, 9, z], [6.9 * s, 15, z], 0.25, "steel")
    # cab interior: desk and seat
    els.append(box(-6, 9, 14.5, 6, 14, 16, "iron"))
    els.append(box(-3, 9, 10.5, 3, 12.5, 13, "seat"))
    return els


def diesel_glass():
    els = [box(-6.2, 18, 16.5, -0.8, 24, 16.8, "glass"), box(0.8, 18, 16.5, 6.2, 24, 16.8, "glass")]
    for s in (1, -1):
        els.append(box(6.5 * s, 18, 10, 6.7 * s, 24, 16, "glass"))
    return els


def diesel_lamp(lit):
    t = "lamp" if lit else "lamp_off"
    return [box(-1.3, 15.2, 23.4, 1.3, 17.6, 23.7, t, emissive=lit), box(-1.4, 25.2, 17.6, 1.4, 26.4, 17.9, t, emissive=lit)]


def diesel_beam():
    """The headlight beam: a widening translucent shaft ahead of the nose."""
    els = []
    for i, (z0, z1, w) in enumerate(((23.8, 36, 1.8), (36, 52, 3.2), (52, 72, 5.0))):
        els.append(box(-w, 16.4 - w * 0.6, z0, w, 16.4 + w * 0.6, z1, "beam", only=["east", "west", "up", "down"], emissive=True))
    return els


def diesel_fan():
    """One radiator fan, centred on its hub (turned about y)."""
    return [box(-3, -0.15, -3, 3, 0.15, 3, {"up": "fan", "down": "fan"}), box(-0.5, -0.4, -0.5, 0.5, 0.4, 0.5, "steel")]


# ============================================================ coach (44 px)

CO_BOGIES = [(2.8, -13), (2.8, 13)]
CO_AXLE_GAP = 6


def coach_body():
    els = []
    els += tiled_box(-6.5, 6, -19.5, 6.5, 8, 19.5, "iron")
    els += buffers(19.5, 1, 4.5, 8)
    els += buffers(-19.5, -1, 4.5, 8)
    els += tiled_box(-7, 8, -19.5, 7, 8.6, 19.5, "wood")
    # side walls: panels under and over the window band, pillars between the windows
    for s in (1, -1):
        els += tiled_box(6.2 * s if s > 0 else -7, 8.6, -19.5, 7 if s > 0 else -6.2, 14, 19.5, "coach")
        els += tiled_box(6.2 * s if s > 0 else -7, 20, -19.5, 7 if s > 0 else -6.2, 23, 19.5, "coach")
        for z in range(-19, 20, 6):
            z0 = max(-19.5, z - 0.6)
            els.append(box(6.2 * s, 14, z0, 7 * s, 20, min(19.5, z + 0.6), "coach"))
    # ends with a door, vestibule handrails
    for side in (1, -1):
        z0, z1 = (18.7, 19.5) if side > 0 else (-19.5, -18.7)
        els.append(box(-7, 8.6, z0, -2.4, 23, z1, "coach"))
        els.append(box(2.4, 8.6, z0, 7, 23, z1, "coach"))
        els.append(box(-2.4, 8.6, z0 + 0.2 * side, 2.4, 21, z1 + 0.2 * side, "door"))
        els.append(box(-2.4, 21, z0, 2.4, 23, z1, "coach"))
    # roof: flat centre and sloping sides
    els += tiled_box(-5, 24.6, -20, 5, 25.6, 20, "roof")
    for s in (1, -1):
        for z0 in (-20, -10, 0, 10):
            els.append(plank([7.6 * s, 23, z0], [5 * s, 24.9, z0], [7.6 * s, 23, z0 + 10], [5 * s, 24.9, z0 + 10], 0.8, "roof",
                             out_dir=[s, 1, 0], grow=0.05))
    els += tiled_box(-7.6, 22.8, -20, 7.6, 23.2, 20, "roof")
    # benches: four seats in two rows facing forward
    for zc in (10, -10):
        for s in (1, -1):
            els.append(box(0.6 * s, 8.6, zc - 2.5, 6.2 * s, 11, zc + 2, "seat"))
            els.append(box(0.6 * s, 11, zc - 3.2, 6.2 * s, 16, zc - 2.4, "seat"))
            els.append(box(0.6 * s, 8.6, zc - 3.4, 6.2 * s, 9.6, zc + 2.2, "wood"))
    # lamps in the ceiling
    for z in (-12, 0, 12):
        els.append(box(-0.8, 22.4, z - 0.8, 0.8, 23, z + 0.8, "lamp", emissive=True))
    return els


def coach_glass():
    els = []
    for s in (1, -1):
        for z in range(-19, 19, 6):
            els.append(box(6.45 * s, 14, z + 0.6, 6.6 * s, 20, z + 5.4, "glass"))
    return els


# ============================================================ box car (38 px)

BX_BOGIES = [(2.8, -10), (2.8, 10)]
BX_AXLE_GAP = 6


def boxcar_body():
    els = []
    els += tiled_box(-6.5, 6, -16.5, 6.5, 8, 16.5, "iron")
    els += buffers(16.5, 1, 4.5, 8)
    els += buffers(-16.5, -1, 4.5, 8)
    els += tiled_box(-7, 8, -16.5, 7, 24, 16.5, "boxcar")
    for s in (1, -1):
        # sliding doors, their rails, the corner posts and ribs
        els.append(box(7 * s, 8.6, -4.5, 7.5 * s, 23, 4.5, "bdoor"))
        els.append(box(7 * s, 23, -9, 7.8 * s, 23.6, 9, "iron"))
        els.append(box(7 * s, 8, -9, 7.6 * s, 8.6, 9, "iron"))
        for z in (-16, -10, 10, 16):
            z0, z1 = (z - 0.5, z + 0.5)
            els.append(box(7 * s, 8, max(-16.6, z0), 7.4 * s, 24, min(16.6, z1), "iron"))
        # ladders at the corners
        els += rod([7.6 * s, 9, 15.5], [7.6 * s, 23, 15.5], 0.25, "steel")
        for y in range(10, 24, 3):
            els += rod([7.6 * s, y, 13.8], [7.6 * s, y, 15.5], 0.2, "steel")
    els += tiled_box(-7.6, 24, -17, 7.6, 25.2, 17, "roof")
    els += tiled_box(-1.5, 25.2, -17, 1.5, 25.6, 17, "wood")  # running board
    return els


# ============================================================ tank wagon (38 px)

TK_BOGIES = [(2.8, -10), (2.8, 10)]
TK_AXLE_GAP = 6


def tank_body():
    els = []
    els += tiled_box(-6.5, 6, -16.5, 6.5, 9, 16.5, "iron")
    els += buffers(16.5, 1, 4.5, 8.5)
    els += buffers(-16.5, -1, 4.5, 8.5)
    for z in (-9, 9):  # saddles
        els.append(box(-5, 9, z - 1.5, 5, 11.5, z + 1.5, "black"))
    els += tube_z(0, 16, -14, 14, 6.5, 6.5, "tank", n=12, caps=False, th=0.8)
    for z in (-14.6, 14):
        els += disc_z(0, 16, z + 0.6, 6.3, "tank", th=0.6, n=12)
    for z in (-7.5, 0, 7.5):
        els += tube_z(0, 16, z - 0.5, z + 0.5, 6.75, 6.75, "iron", n=12, caps=False, th=0.3)
    # dome, valves, walkway, ladder
    els += ring(0, 0, 21.5, 25, 2.6, 2.4, "tank", n=8, caps=False, th=0.5)
    els += disc(0, 0, 25.3, 2.6, "iron", th=0.5)
    els.append(box(-0.6, 25.3, -0.6, 0.6, 26.4, 0.6, "brass"))
    for s in (1, -1):
        els += tiled_box(5.5 * s if s > 0 else -7.5, 8.6, -16, 7.5 if s > 0 else -5.5, 9.2, 16, "wood")
        els += rod([7.2 * s, 9.2, 2], [7.2 * s, 22, 2], 0.25, "steel")
        els += rod([7.2 * s, 9.2, -2], [7.2 * s, 22, -2], 0.25, "steel")
        for y in range(11, 22, 3):
            els += rod([7.2 * s, y, -2], [7.2 * s, y, 2], 0.2, "steel")
    # bottom outlet valve
    els += ring(0, 0, 7, 9.5, 1.2, 1.2, "iron", n=6, caps=True, th=0.4)
    return els


# ============================================================ hopper wagon (34 px)

HP_BOGIES = [(2.8, -9), (2.8, 9)]
HP_AXLE_GAP = 6
HP_FLOOR, HP_TOP = 12, 22


def hopper_body():
    els = []
    els += tiled_box(-6.5, 6.5, -14.5, 6.5, 8, 14.5, "iron")
    els += buffers(14.5, 1, 4.5, 8)
    els += buffers(-14.5, -1, 4.5, 8)
    # vertical walls of the open top
    for s in (1, -1):
        els += tiled_box(6.2 * s if s > 0 else -7, HP_FLOOR, -14.5, 7 if s > 0 else -6.2, HP_TOP, 14.5, "hopper")
    for side in (1, -1):
        z0, z1 = (13.7, 14.5) if side > 0 else (-14.5, -13.7)
        els += tiled_box(-7, HP_FLOOR, z0, 7, HP_TOP, z1, "hopper")
    # the two V chutes under the body (sloped floors) and the side slopes
    for c in (-7, 7):
        for d in (1, -1):
            z_top, z_bot = c + 7 * d, c + 2.2 * d
            els.append(plank([-6.2, HP_FLOOR, z_top], [6.2, HP_FLOOR, z_top], [-6.2, 6.5, z_bot], [6.2, 6.5, z_bot], 0.8,
                             "hopper", out_dir=[0, -1, d], grow=0.1))
        for s in (1, -1):
            els.append(plank([7 * s, HP_FLOOR, c - 7], [7 * s, HP_FLOOR, c + 7], [3 * s, 6.5, c - 2.2], [3 * s, 6.5, c + 2.2],
                             0.8, "hopper", out_dir=[s, -1, 0], grow=0.1))
    # top rim and ribs
    for s in (1, -1):
        els.append(box(6 * s, HP_TOP, -14.7, 7.4 * s, HP_TOP + 0.8, 14.7, "iron"))
        for z in (-9, -3, 3, 9):
            els.append(box(7 * s, HP_FLOOR, z - 0.5, 7.5 * s, HP_TOP, z + 0.5, "iron"))
    for side in (1, -1):
        z = 14.5 * side
        els.append(box(-7.4, HP_TOP, min(z, z - 0.8 * side), 7.4, HP_TOP + 0.8, max(z, z - 0.8 * side) + 0.2, "iron"))
    return els


def hopper_doors(open_):
    els = []
    for c in (-7, 7):
        if open_:
            for s in (1, -1):
                els.append(plank([0.2 * s, 6.5, c - 2.2], [0.2 * s, 6.5, c + 2.2], [2.5 * s, 3.5, c - 2.2], [2.5 * s, 3.5, c + 2.2],
                                 0.5, "iron", out_dir=[0, -1, 0], grow=0.05))
        else:
            els.append(box(-3.2, 6, c - 2.6, 3.2, 6.6, c + 2.6, "iron"))
    return els


def hopper_load():
    """The load: a heap filling the top, its base at y = 0 (the renderer raises it with the fill)."""
    return [box(-6.1, -6, -13.6, 6.1, 0, 13.6, "ore"), box(-4.5, 0, -11, 4.5, 1.2, 11, "ore"), box(-2.5, 1.2, -7, 2.5, 2, 7, "ore")]


# ============================================================ couplings and the station

def station_model(lit):
    """Platform block facing north (the track side); signal post at the back."""
    els = []
    els.append(cube([0, 0, 0], [16, 12, 16], {"up": "top", "down": "side", "north": "side", "south": "side",
                                              "east": "side", "west": "side"}))
    els.append(cube([6.5, 12, 11.5], [9.5, 13, 14.5], "post"))
    els.append(cube([7.25, 13, 12.25], [8.75, 24, 13.75], "post"))
    els.append(cube([5.5, 24, 11.5], [10.5, 31, 14.5], "post"))
    els.append(cube([6.2, 27.6, 11.3], [9.8, 30.6, 11.5], "green" if lit else "green_off", only=["north"]))
    els.append(cube([6.2, 24.4, 11.3], [9.8, 27.4, 11.5], "red_off" if lit else "red", only=["north"]))
    els.append(cube([5, 31, 11], [11, 31.6, 15], "post"))
    for e in els:
        if e.faces and any(t in ("green", "red") for t, _ in e.faces.values()):
            e.emissive = True
    return els


STATION_TEX = {"top": "train_station_top", "side": "train_station_side", "post": "train_station_post",
               "green": "train_signal_green", "green_off": "train_signal_green_off", "red": "train_signal_red",
               "red_off": "train_signal_red_off"}


# ============================================================ parts and layout

def parts():
    p = {
        "steam_body": (steam_body(), False),
        "steam_lamp_on": (steam_lamp(True), False),
        "steam_lamp_off": (steam_lamp(False), False),
        "steam_rod_l": (steam_rod(1), False),
        "steam_rod_r": (steam_rod(-1), False),
        "steam_mainrod_l": (steam_mainrod(1), False),
        "steam_mainrod_r": (steam_mainrod(-1), False),
        "steam_piston_l": (steam_piston(1), False),
        "steam_piston_r": (steam_piston(-1), False),
        "wheel_big": (wheelset(4, "wheel_big"), False),
        "wheel_small": (wheelset(2.8, "wheel_small"), False),
        "bogie_7": (bogie_frame(DI_AXLE_GAP, 2.8), False),
        "bogie_6": (bogie_frame(CO_AXLE_GAP, 2.8), False),
        "diesel_body": (diesel_body(), False),
        "diesel_glass": (diesel_glass(), True),
        "diesel_lamp_on": (diesel_lamp(True), False),
        "diesel_lamp_off": (diesel_lamp(False), False),
        "diesel_beam": (diesel_beam(), True),
        "diesel_fan": (diesel_fan(), False),
        "coach_body": (coach_body(), False),
        "coach_glass": (coach_glass(), True),
        "boxcar_body": (boxcar_body(), False),
        "tank_body": (tank_body(), False),
        "hopper_body": (hopper_body(), False),
        "hopper_doors_open": (hopper_doors(True), False),
        "hopper_doors_closed": (hopper_doors(False), False),
        "hopper_load": (hopper_load(), False),
        "coupler": (coupler(), False),
    }
    return p


def _bogie_axles(bogies, gap):
    out = []
    for y, z in bogies:
        out += [[y, z - gap / 2], [y, z + gap / 2]]
    return out


# per vehicle: static parts, wheelsets [(part, y, z)], bogies [(part, y, z)], half length (px) for the couplers
LAYOUT = {
    "steam_locomotive": {"body": ["steam_body"], "wheels": [["wheel_big", y, z] for y, z in ST_AXLES], "bogies": [],
                         "half": 23},
    "diesel_locomotive": {"body": ["diesel_body"], "glass": ["diesel_glass"],
                          "wheels": [["wheel_small", y, z] for y, z in _bogie_axles(DI_BOGIES, DI_AXLE_GAP)],
                          "bogies": [["bogie_7", y, z] for y, z in DI_BOGIES], "half": 26},
    "passenger_car": {"body": ["coach_body"], "glass": ["coach_glass"],
                      "wheels": [["wheel_small", y, z] for y, z in _bogie_axles(CO_BOGIES, CO_AXLE_GAP)],
                      "bogies": [["bogie_6", y, z] for y, z in CO_BOGIES], "half": 22},
    "cargo_wagon": {"body": ["boxcar_body"], "wheels": [["wheel_small", y, z] for y, z in _bogie_axles(BX_BOGIES, BX_AXLE_GAP)],
                    "bogies": [["bogie_6", y, z] for y, z in BX_BOGIES], "half": 19},
    "tank_wagon": {"body": ["tank_body"], "wheels": [["wheel_small", y, z] for y, z in _bogie_axles(TK_BOGIES, TK_AXLE_GAP)],
                   "bogies": [["bogie_6", y, z] for y, z in TK_BOGIES], "half": 19},
    "hopper_wagon": {"body": ["hopper_body"], "wheels": [["wheel_small", y, z] for y, z in _bogie_axles(HP_BOGIES, HP_AXLE_GAP)],
                     "bogies": [["bogie_6", y, z] for y, z in HP_BOGIES], "half": 17},
}
# Steam engine motion (mirrored in TrainRenderer): crank radius, cylinder axis height, connecting rod length.
MOTION = {"crank": ST_CRANK, "cylinder_y": ST_CYL_Y, "mainrod": ST_MAINROD, "fans": DI_FANS,
          "hopper_floor": HP_FLOOR + 0.5, "hopper_top": HP_TOP - 0.2}


def write_parts(ctx):
    manifest = {}
    for name, (els, translucent) in parts().items():
        cells = SHIPS.split_cells(name, els)
        entries = []
        for i, (shifted, offset) in enumerate(cells):
            used = {t for e in shifted for (t, _) in e.faces.values()}
            tex = {t: TEX[t] for t in sorted(used)}
            particle = next(iter(sorted(used)))
            ctx.block_model(f"train/{name}_{i}", {"ambientocclusion": False, "textures": {**tex, "particle": TEX[particle]},
                                                  "elements": [e.json() for e in shifted]})
            entries.append({"model": f"{MOD}:block/train/{name}_{i}", "offset": [round(v, 4) for v in offset]})
        manifest[name] = {"translucent": translucent, "cells": entries}
    ctx.write(ASSETS / "trains" / "parts.json", {"parts": manifest, "layout": LAYOUT, "motion": MOTION})


def write_station(ctx):
    for lit in (False, True):
        name = "train_station_lit" if lit else "train_station"
        els = station_model(lit)
        used = sorted({t for e in els for (t, _) in e.faces.values()})
        ctx.block_model(name, {"parent": "minecraft:block/block", "ambientocclusion": True,
                               "textures": {**{t: f"{MOD}:block/{STATION_TEX[t]}" for t in used},
                                            "particle": f"{MOD}:block/train_station_side"},
                               "elements": [e.json() for e in els]})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            v = {"model": f"{MOD}:block/{'train_station_lit' if lit else 'train_station'}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},lit={'true' if lit else 'false'}"] = v
    ctx.write(ASSETS / "blockstates" / "train_station.json", {"variants": variants})
    ctx.item_def("train_station", "block/train_station")
    ctx.loot_self("train_station")


# ============================================================ items, recipes, lang, advancements

ITEMS = ["steam_locomotive", "diesel_locomotive", "passenger_car", "cargo_wagon", "tank_wagon", "hopper_wagon", "coupler"]

LANG = [
    ("item.factoryascent.steam_locomotive", "Steam Locomotive", "Locomotora de vapor"),
    ("item.factoryascent.diesel_locomotive", "Diesel-Electric Locomotive", "Locomotora diésel-eléctrica"),
    ("item.factoryascent.passenger_car", "Passenger Car", "Coche de pasajeros"),
    ("item.factoryascent.cargo_wagon", "Cargo Wagon", "Vagón de carga"),
    ("item.factoryascent.tank_wagon", "Tank Wagon", "Vagón cisterna"),
    ("item.factoryascent.hopper_wagon", "Hopper Wagon", "Vagón tolva"),
    ("item.factoryascent.coupler", "Coupler", "Enganche"),
    ("block.factoryascent.train_station", "Train Station", "Estación de tren"),
    ("entity.factoryascent.steam_locomotive", "Steam Locomotive", "Locomotora de vapor"),
    ("entity.factoryascent.diesel_locomotive", "Diesel-Electric Locomotive", "Locomotora diésel-eléctrica"),
    ("entity.factoryascent.passenger_car", "Passenger Car", "Coche de pasajeros"),
    ("entity.factoryascent.cargo_wagon", "Cargo Wagon", "Vagón de carga"),
    ("entity.factoryascent.tank_wagon", "Tank Wagon", "Vagón cisterna"),
    ("entity.factoryascent.hopper_wagon", "Hopper Wagon", "Vagón tolva"),

    ("tooltip.factoryascent.steam_locomotive",
     "Runs on minecart rails. Burns furnace fuel (4 bunker slots, hoppers can fill them) to boil water from its "
     "10,000 mB tank: fill it with buckets or at a Train Station. Up to 36 km/h.",
     "Va por rieles de vagoneta. Quema combustible de horno (4 casillas, las tolvas pueden llenarlas) para hervir "
     "el agua de su tanque de 10.000 mB: llénalo con cubos o en una estación. Hasta 36 km/h."),
    ("tooltip.factoryascent.diesel_locomotive",
     "Traction motors on a 200k FE battery, kept charged by an on-board diesel generator (16,000 mB tank). "
     "Charge or fuel it at a Train Station. Headlight and horn. Up to 65 km/h.",
     "Motores de tracción con una batería de 200k FE que recarga un generador diésel a bordo (tanque de 16.000 mB). "
     "Cárgala o repóstala en una estación. Faro y bocina. Hasta 65 km/h."),
    ("tooltip.factoryascent.passenger_car", "Four seats. Couple it to a locomotive.",
     "Cuatro asientos. Engánchalo a una locomotora."),
    ("tooltip.factoryascent.cargo_wagon",
     "A 54-slot covered wagon. Hoppers above or below the rails load and unload it, like a chest minecart.",
     "Vagón cubierto de 54 casillas. Las tolvas encima o debajo de los rieles lo cargan y descargan, como una vagoneta con cofre."),
    ("tooltip.factoryascent.tank_wagon", "Carries 32,000 mB of any one fluid. Fill and empty it at a Train Station (or by bucket).",
     "Lleva 32.000 mB de un fluido. Llénalo y vacíalo en una estación (o con cubos)."),
    ("tooltip.factoryascent.hopper_wagon",
     "An open-top ore wagon (27 slots): catches items dropped into it and dumps its load on a powered activator rail "
     "(into a container under the rail, or onto the ground).",
     "Vagón tolva abierto (27 casillas): recoge los objetos que caen dentro y descarga sobre un riel activador "
     "encendido (en un contenedor bajo el riel o al suelo)."),
    ("tooltip.factoryascent.coupler",
     "Right-click a locomotive or wagon, then the one next to it to couple them. Sneak-right-click a vehicle to "
     "uncouple the end you clicked.",
     "Clic derecho en una locomotora o vagón y luego en el de al lado para engancharlos. Agáchate y haz clic derecho "
     "para desenganchar el extremo que tocaste."),
    ("tooltip.factoryascent.coupler.max", "Trains: up to %s vehicles", "Trenes: hasta %s vehículos"),
    ("tooltip.factoryascent.train_station",
     "Place it beside the rails. Stops trains (always, on redstone, or never), waits, and loads or unloads them from "
     "the chests, tanks and batteries touching it.",
     "Colócala junto a los rieles. Detiene los trenes (siempre, con redstone o nunca), espera y los carga o descarga "
     "desde los cofres, tanques y baterías que la tocan."),
    ("tooltip.factoryascent.train_station.use", "Right-click to set it up", "Clic derecho para configurarla"),
    ("tooltip.factoryascent.train.place", "Right-click a rail to set it on the track (front toward where you look).",
     "Clic derecho en un riel para ponerlo en la vía (el frente hacia donde miras)."),
    ("tooltip.factoryascent.train.cargo", "Cargo: %s stacks", "Carga: %s pilas"),
    ("tooltip.factoryascent.train.fluid", "Tank: %s mB %s", "Tanque: %s mB de %s"),
    ("tooltip.factoryascent.train.energy", "Battery: %s FE", "Batería: %s FE"),
    ("tooltip.factoryascent.train.controls",
     "W/S throttle (it stays set; past zero is reverse), Space brake, H whistle, J lights, E cab, Shift to leave",
     "W/S regulador (se queda fijo; pasado el cero es marcha atrás), Espacio freno, H silbato, J luces, E cabina, Shift para bajar"),

    ("message.factoryascent.train.occupied", "There is already a vehicle on this rail", "Ya hay un vehículo en este riel"),
    ("message.factoryascent.train.no_fuel", "Out of fuel", "Sin combustible"),
    ("message.factoryascent.train.no_coal", "The firebox is out: put fuel in the bunker (E)",
     "El hogar se apagó: pon combustible en la carbonera (E)"),
    ("message.factoryascent.train.no_water", "The boiler is dry: fill the water tank (bucket or Train Station)",
     "La caldera está seca: llena el tanque de agua (cubo o estación)"),
    ("message.factoryascent.train.no_power", "No power: charge the battery or fill the diesel tank",
     "Sin energía: carga la batería o llena el tanque de diésel"),
    ("message.factoryascent.train.tank", "Tank: %s / %s mB (%s)", "Tanque: %s / %s mB (%s)"),
    ("message.factoryascent.coupler.selected", "Coupler: %s picked. Now click the vehicle next to it.",
     "Enganche: %s elegido. Ahora haz clic en el vehículo de al lado."),
    ("message.factoryascent.coupler.cleared", "Coupler: selection cleared", "Enganche: selección borrada"),
    ("message.factoryascent.coupler.coupled", "Coupled!", "¡Enganchados!"),
    ("message.factoryascent.coupler.uncoupled", "Uncoupled", "Desenganchados"),
    ("message.factoryascent.coupler.no_link", "That vehicle isn't coupled", "Ese vehículo no está enganchado"),
    ("message.factoryascent.coupler.too_far", "Too far apart: put them next to each other on the track",
     "Demasiado lejos: ponlos uno al lado del otro en la vía"),
    ("message.factoryascent.coupler.off_track", "Both vehicles must be on the rails", "Ambos vehículos deben estar sobre los rieles"),
    ("message.factoryascent.coupler.same_train", "They are already in the same train", "Ya están en el mismo tren"),
    ("message.factoryascent.coupler.in_use", "That coupler is already in use", "Ese enganche ya está en uso"),
    ("message.factoryascent.coupler.too_long", "Too long: a train may have at most %s vehicles",
     "Demasiado largo: un tren puede tener como máximo %s vehículos"),
    ("message.factoryascent.coupler.not_track", "They aren't on the same piece of track", "No están en el mismo tramo de vía"),

    ("gui.factoryascent.train.cab", "Cab", "Cabina"),
    ("gui.factoryascent.train.bunker", "Coal bunker", "Carbonera"),
    ("gui.factoryascent.train.power_slot", "Power slot", "Casilla de energía"),
    ("gui.factoryascent.train.power_tip", "Any charged FE item (drained into the battery)",
     "Cualquier objeto con FE (se descarga en la batería)"),
    ("gui.factoryascent.train.hold", "Hold", "Bodega"),
    ("gui.factoryascent.train.tank_title", "Tank", "Tanque"),
    ("gui.factoryascent.train.empty", "empty", "vacío"),
    ("gui.factoryascent.train.speed", "Speed %s km/h", "Velocidad %s km/h"),
    ("gui.factoryascent.train.throttle", "Throttle %s%%", "Regulador %s%%"),
    ("gui.factoryascent.train.reverse", "Reverse %s%%", "Marcha atrás %s%%"),
    ("gui.factoryascent.train.idle", "Throttle closed", "Regulador cerrado"),
    ("gui.factoryascent.train.brake", "BRAKE", "FRENO"),
    ("gui.factoryascent.train.pressure", "Steam %s%%", "Vapor %s%%"),
    ("gui.factoryascent.train.water", "Water %s / %s mB", "Agua %s / %s mB"),
    ("gui.factoryascent.train.fire", "Fire %s%%", "Fuego %s%%"),
    ("gui.factoryascent.train.battery", "Battery %s / %s FE", "Batería %s / %s FE"),
    ("gui.factoryascent.train.diesel", "Diesel %s / %s mB", "Diésel %s / %s mB"),
    ("gui.factoryascent.train.generator", "Generator running", "Generador en marcha"),
    ("gui.factoryascent.train.cars", "Train: %s vehicles", "Tren: %s vehículos"),
    ("gui.factoryascent.train.fill", "Load %s%%", "Carga %s%%"),
    ("gui.factoryascent.train.lights", "Lights: %s", "Luces: %s"),
    ("gui.factoryascent.train.on", "on", "encendidas"),
    ("gui.factoryascent.train.off", "off", "apagadas"),
    ("gui.factoryascent.train.horn", "Horn", "Bocina"),
    ("gui.factoryascent.train.whistle", "Whistle", "Silbato"),
    ("gui.factoryascent.train.toggle_lights", "Lights", "Luces"),
    ("gui.factoryascent.train.stop", "Close throttle", "Cerrar regulador"),
    ("gui.factoryascent.train.status.0", "Running", "En marcha"),
    ("gui.factoryascent.train.status.1", "Stopped at a station", "Detenido en una estación"),
    ("gui.factoryascent.train.status.2", "No fuel", "Sin combustible"),
    ("gui.factoryascent.train.status.3", "End of the track", "Fin de la vía"),
    ("gui.factoryascent.train.status.4", "Blocked by another train", "Bloqueado por otro tren"),
    ("gui.factoryascent.train.status.5", "Waiting (off the rails or cars not loaded)", "Esperando (fuera de la vía o vagones sin cargar)"),
    ("gui.factoryascent.station.stop", "Stop: %s", "Parar: %s"),
    ("gui.factoryascent.station.stop.0", "always", "siempre"),
    ("gui.factoryascent.station.stop.1", "on redstone", "con redstone"),
    ("gui.factoryascent.station.stop.2", "never", "nunca"),
    ("gui.factoryascent.station.dwell", "Wait: %s s", "Espera: %s s"),
    ("gui.factoryascent.station.dwell_done", "Wait: until done", "Espera: hasta terminar"),
    ("gui.factoryascent.station.mode", "%s", "%s"),
    ("gui.factoryascent.station.mode.0", "Load the train", "Cargar el tren"),
    ("gui.factoryascent.station.mode.1", "Unload the train", "Descargar el tren"),
    ("gui.factoryascent.station.mode.2", "No transfers", "Sin transferencias"),
    ("gui.factoryascent.station.no_track", "No rails next to the station!", "¡No hay rieles junto a la estación!"),
    ("gui.factoryascent.station.empty", "No train", "Sin tren"),
    ("gui.factoryascent.station.arriving", "Train arriving: braking", "Tren llegando: frenando"),
    ("gui.factoryascent.station.docked", "%s vehicles docked", "%s vehículos detenidos"),
    ("gui.factoryascent.station.leaves", "Departs in %s s", "Sale en %s s"),
    ("gui.factoryascent.station.waiting_done", "Departs when loading is done", "Sale al terminar la carga"),
    ("gui.factoryascent.station.waiting_signal", "Held while powered", "Retenido mientras haya señal"),
    ("gui.factoryascent.station.powered", "Redstone: %s", "Redstone: %s"),
    ("gui.factoryascent.station.help",
     "Chests, tanks and batteries touching the station trade with the stopped wagons.",
     "Los cofres, tanques y baterías que tocan la estación intercambian con los vagones detenidos."),

    ("key.category.factoryascent.trains", "Factory Ascent: Trains", "Factory Ascent: Trenes"),
    ("key.factoryascent.train_horn", "Train: whistle / horn", "Tren: silbato / bocina"),
    ("key.factoryascent.train_lights", "Train: lights on/off", "Tren: encender/apagar luces"),
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

    def riding(vehicle):
        return {"trigger": "minecraft:started_riding", "conditions": {"player": [{
            "condition": "minecraft:entity_properties", "entity": "this",
            "predicate": {"minecraft:vehicle": {"minecraft:entity_type": f"{MOD}:{vehicle}"}}}]}}

    def has(item):
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"{MOD}:{item}"}]}}

    adv("bronze_steam_locomotive", "age_bronze", "steam_locomotive", {"ride": riding("steam_locomotive")}, "All Aboard!",
        "Climb into the cab of a Steam Locomotive", "¡Pasajeros al tren!", "Súbete a la cabina de una locomotora de vapor",
        frame="goal")
    adv("bronze_coupler", "bronze_steam_locomotive", "coupler", {"get": has("coupler")}, "Couple Up",
        "Make a Coupler to join locomotives and wagons into a train", "Enganchados",
        "Fabrica un enganche para unir locomotoras y vagones en un tren")
    adv("bronze_passenger_car", "bronze_coupler", "passenger_car", {"ride": riding("passenger_car")}, "Tickets, Please",
        "Take a seat in a Passenger Car", "Billetes, por favor", "Siéntate en un coche de pasajeros")
    adv("bronze_cargo_wagon", "bronze_coupler", "cargo_wagon", {"get": has("cargo_wagon")}, "Freight Train",
        "Build a Cargo Wagon", "Tren de mercancías", "Construye un vagón de carga")
    adv("bronze_tank_wagon", "bronze_coupler", "tank_wagon", {"get": has("tank_wagon")}, "Liquid Assets",
        "Build a Tank Wagon to haul fluids by rail", "Activos líquidos", "Construye un vagón cisterna para llevar fluidos por tren")
    adv("bronze_hopper_wagon", "bronze_coupler", "hopper_wagon", {"get": has("hopper_wagon")}, "Ore Train",
        "Build a Hopper Wagon", "Tren minero", "Construye un vagón tolva")
    adv("bronze_train_station", "bronze_coupler", "train_station", {"get": has("train_station")}, "Next Stop",
        "Build a Train Station to stop, load and unload trains", "Próxima parada",
        "Construye una estación para detener, cargar y descargar trenes")
    adv("automation_diesel_locomotive", "automation_diesel", "diesel_locomotive", {"ride": riding("diesel_locomotive")},
        "Diesel Power", "Drive a Diesel-Electric Locomotive", "Fuerza diésel", "Conduce una locomotora diésel-eléctrica",
        frame="goal")


def recipes(ctx):
    BP, SP = "#c:plates/bronze", "#c:plates/steel"
    ctx.shaped("steam_locomotive", ["BCP", "PFP", "GMG"],
               {"B": "minecraft:bucket", "C": "minecraft:bell", "P": BP, "F": "minecraft:furnace", "G": "#c:gears/bronze",
                "M": "minecraft:minecart"}, "steam_locomotive", category="misc")
    ctx.shaped("diesel_locomotive", ["LAH", "MDM", "GXG"],
               {"L": "minecraft:redstone_lamp", "A": "advanced_circuit", "H": "minecraft:note_block", "M": "motor",
                "D": "diesel_generator", "G": "#c:gears/steel", "X": "minecraft:minecart"}, "diesel_locomotive", category="misc")
    ctx.shaped("passenger_car", ["GGG", "SMS", "PPP"],
               {"G": "minecraft:glass_pane", "S": "#minecraft:wooden_slabs", "M": "minecraft:minecart", "P": BP},
               "passenger_car", category="misc")
    ctx.shaped("cargo_wagon", [" X ", "PMP"], {"X": "bronze_crate", "M": "minecraft:minecart", "P": BP}, "cargo_wagon",
               category="misc")
    ctx.shaped("tank_wagon", [" T ", "PMP"], {"T": "bronze_fluid_tank", "M": "minecraft:minecart", "P": BP}, "tank_wagon",
               category="misc")
    ctx.shaped("hopper_wagon", ["P P", "PHP", " M "], {"H": "minecraft:hopper", "M": "minecraft:minecart", "P": BP},
               "hopper_wagon", category="misc")
    ctx.shaped("coupler", ["  C", " R ", "R  "], {"C": "minecraft:iron_chain", "R": "#c:rods/iron"}, "coupler", category="misc")
    ctx.shaped("train_station", ["PXP", "RCR", "SSS"],
               {"P": BP, "X": "minecraft:hopper", "R": "minecraft:rail", "C": "minecraft:comparator",
                "S": "minecraft:stone_bricks"}, "train_station", category="misc")


def generate(ctx):
    write_parts(ctx)
    write_station(ctx)
    for name in ITEMS:
        ctx.flat_item(name)
    recipes(ctx)
    advancements(ctx)
    for key, en, es in LANG:
        ctx.lang(key, en, es)


# ============================================================ preview

def _textures():
    from PIL import Image
    t = {k: Image.open(ASSETS / "textures" / "block" / f"{v.split('/')[-1]}.png").convert("RGBA") for k, v in TEX.items()}
    for k, v in STATION_TEX.items():
        t[k] = Image.open(ASSETS / "textures" / "block" / f"{v}.png").convert("RGBA")
    return t


def vehicle_elements(name, wheel_deg=30, fill=0.8, lit=True, doors_open=False):
    """Everything a vehicle shows, placed as the renderer places it (for the preview)."""
    P = parts()
    lay = LAYOUT[name]
    els = []
    for b in lay["body"] + lay.get("glass", []):
        els += P[b][0]
    for part, y, z in lay["bogies"]:
        els += [e.moved(0, y, z) for e in P[part][0]]
    for part, y, z in lay["wheels"]:
        els += [e.moved(0, y, z) for e in spin(P[part][0], wheel_deg, [0, 0, 0], axis="x")]
    if name == "steam_locomotive":
        els += P["steam_lamp_on" if lit else "steam_lamp_off"][0]
        for side, phase in (("l", 0), ("r", 90)):
            a = math.radians(wheel_deg + phase)
            py, pz = -ST_CRANK * math.sin(a), ST_CRANK * math.cos(a)
            els += [e.moved(0, py, pz) for e in P[f"steam_rod_{side}"][0]]
            ay, az = ST_AXLES[-1]
            pin = [ay + py, az + pz]
            cy = ST_CYL_Y
            cz = pin[1] + math.sqrt(max(0.0, ST_MAINROD ** 2 - (cy - pin[0]) ** 2))
            ang = math.degrees(math.atan2(-(cy - pin[0]), cz - pin[1]))
            rod_els = [El(e.frm[:2] + [e.frm[2] * ST_MAINROD / 16], e.to[:2] + [e.to[2] * ST_MAINROD / 16], e.faces, e.m, e.origin)
                       for e in P[f"steam_mainrod_{side}"][0]]
            els += [e.moved(0, pin[0], pin[1]) for e in spin(rod_els, ang, [0, 0, 0], axis="x")]
            els += [e.moved(0, cy, cz) for e in P[f"steam_piston_{side}"][0]]
    if name == "diesel_locomotive":
        els += P["diesel_lamp_on" if lit else "diesel_lamp_off"][0]
        if lit:
            els += P["diesel_beam"][0]
        for y, z in DI_FANS:
            els += [e.moved(0, y, z) for e in spin(P["diesel_fan"][0], wheel_deg, [0, 0, 0])]
    if name == "hopper_wagon":
        els += P["hopper_doors_open" if doors_open else "hopper_doors_closed"][0]
        if fill > 0:
            y = MOTION["hopper_floor"] + (MOTION["hopper_top"] - MOTION["hopper_floor"]) * fill
            els += [e.moved(0, y, 0) for e in P["hopper_load"][0]]
    els += [e.moved(0, 0, lay["half"] - 1) for e in P["coupler"][0]]
    return els


def preview(path):
    from PIL import Image, ImageDraw
    drill = _load("drill")
    tex = _textures()
    bg = (40, 44, 52, 255)
    names = list(LAYOUT)
    panels = []
    for name in names:
        q = SAT._quads(vehicle_elements(name), tex)
        for yaw, pitch in ((-130, 20), (-40, 15)):
            m = _mul(_rot("x", pitch), _rot("y", yaw))
            img = drill._render_mixed([(q, lambda p, m=m: _app(m, [p[0] / 16, (p[1] - 14) / 16, p[2] / 16]))],
                                      lambda v: (240 + v[0] * 110, 170 - v[1] * 110), (480, 340), bg)
            panels.append((f"{name} {yaw}/{pitch}", img))
    q = SAT._quads(station_model(True), tex)
    m = _mul(_rot("x", 25), _rot("y", -35))
    panels.append(("train_station", drill._render_mixed([(q, lambda p: _app(m, [(p[0] - 8) / 16, (p[1] - 12) / 16, (p[2] - 8) / 16]))],
                                                        lambda v: (240 + v[0] * 110, 170 - v[1] * 110), (480, 340), bg)))
    cols = 4
    rows = (len(panels) + cols - 1) // cols
    out = Image.new("RGBA", (cols * 490 + 10, rows * 365 + 10), (25, 25, 30, 255))
    d = ImageDraw.Draw(out)
    for i, (title, im) in enumerate(panels):
        x, y = 10 + (i % cols) * 490, 10 + (i // cols) * 365
        d.text((x, y), title, fill=(230, 230, 230, 255))
        out.alpha_composite(im, (x, y + 14))
    out.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        i = sys.argv.index("--preview")
        preview(Path(sys.argv[i + 1]) if len(sys.argv) > i + 1 else ROOT / "tools" / "trains_models_preview.png")
    else:
        print(__doc__)

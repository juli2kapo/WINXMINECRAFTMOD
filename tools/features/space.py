"""Space travel and personal gear (Java: space/, space/client/):

  the orbit dimension          data/factoryascent/dimension{,_type}/orbit.json, worldgen/biome/orbit.json:
                               a void with a black starry sky and the sun (fixed attributes, no timeline);
                               the planet below is drawn by space/client/OrbitSky
  vacuum damage                data/factoryascent/damage_type/vacuum.json (+ bypasses_armor tag)
  Crew Capsule                 3D model drawn on the rocket instead of the fairing (block/crew_capsule_lv,
                               rocket pixels like satellites.py, y = 0 at CAPSULE_BASE)
  Return Pod                   block model: heat shield, white cone with a porthole, parachute pack
  Astronaut Suit / Jet Suit    equipment asset astronaut.json (textures in space_textures.py)
  jetpacks                     3D models worn on the back (block/jetpack_*_worn, the player layer draws them)
                               and used as the item model outside the GUI
  Oxygen Compressor / Sealer   orientable machine blocks, on/off
Recipes, advancements, loot, lang (English + Spanish).

Run `python3 tools/features/space.py --preview` to render tools/space_models_preview.png.
"""
import importlib.util
import sys
from pathlib import Path

MOD = "factoryascent"
HERE = Path(__file__).resolve().parent
PICKAXE_BLOCKS = ["return_pod", "oxygen_compressor", "oxygen_sealer"]
CAPSULE_BASE = 110  # rocket px where the capsule model's y = 0 sits (SpaceClient.CAPSULE_BASE)


def _sat():
    spec = importlib.util.spec_from_file_location("satellites_for_space", HERE / "satellites.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _sat()
cube, ring, disc, tube, spin, model, shift = S.cube, S.ring, S.disc, S.tube, S.spin, S.model, S.shift


def t(name):
    return f"{MOD}:block/{name}"


# ============================================================ models

CAPSULE_TEX = {**S.LV_TEX, "capsule": t("capsule_hull"), "mark": t("capsule_mark"), "shield": t("capsule_heat_shield")}


def capsule():
    """A cone capsule (rocket px) on the payload plate: heat-shield skirt, four windows so the crew
    shows, decal panels, docking hatch, and a launch escape tower on top."""
    RF = S.RF

    def r_at(y):
        return RF + (5.0 - RF) * (y - 97) / (116 - 97)

    els = ring(8, 8, 95, 97, RF + 0.1, RF + 0.1, "shield", th=0.6)
    els += ring(8, 8, 97, 100, r_at(97), r_at(100), "capsule", inner="blanket", th=0.5)
    els += ring(8, 8, 100, 108, r_at(100), r_at(108), "glass", inner="glass", edge="capsule", th=0.5, sides=[0, 2, 4, 6], v0=4)
    els += ring(8, 8, 100, 108, r_at(100), r_at(108), "mark", inner="blanket", th=0.5, sides=[1, 5], v0=3)
    els += ring(8, 8, 100, 108, r_at(100), r_at(108), "capsule", inner="blanket", th=0.5, sides=[3, 7], v0=3)
    els += ring(8, 8, 108, 116, r_at(108), 5.0, "capsule", inner="blanket", th=0.5, v0=8)
    els += ring(8, 8, 116, 118, 5.0, 3.4, "capsule", th=0.5)
    els += disc(8, 8, 118, 3.4, "dark")
    els += ring(8, 8, 118, 120, 2.4, 2.4, "dark", th=0.5)
    # launch escape tower: four struts, the motor, its canted nozzles and a red nose
    for k in range(4):
        els += spin([cube([7.7, 120, 9.3], [8.3, 128, 9.9], "steel")], 45 + k * 90, [8, 124, 8])
    els += tube(8, 8, 128, 135, 1.3, "red")
    els += ring(8, 8, 135, 139, 1.3, 0.3, "red", th=0.4)
    for k in range(4):
        els += spin([cube([7.6, 128.5, 9.2], [8.4, 130, 9.8], "dark")], k * 90, [8, 129, 8])
    return els


JET_TEX = {"tank_e": t("jetpack_tank_electric"), "tank_a": t("jetpack_tank_advanced"), "frame": t("jetpack_frame"),
           "nozzle": t("jetpack_nozzle"), "strap": t("jetpack_strap"), "red": t("jetpack_red"),
           "steel": t("sat_steel"), "dark": t("sat_dark"), "glow": t("lv_plume")}


def jetpack(advanced):
    """Worn on the back (block px): z = 8 is the back of the torso, the pack sticks out towards +z;
    y = 0 is the waist, y = 12 the shoulders. Twin tanks either side of a frame, nozzles under them,
    straps over the shoulders."""
    tank, r, x0, x1 = ("tank_a", 2.5, 4.6, 11.4) if advanced else ("tank_e", 2.1, 5.0, 11.0)
    cz = 8.3 + r
    els = [cube([5.5, 1, 8], [10.5, 11, 9.4], "frame")]
    for cx in (x0, x1):
        els += tube(cx, cz, 1.5, 11, r, tank)
        els += disc(cx, cz, 11.6, r - 0.3, "steel" if not advanced else "red")
        els += ring(cx, cz, -1.8, 1.5, (1.9 if advanced else 1.5), (1.2 if advanced else 1.0), "nozzle", inner="dark", th=0.3, caps=False)
        els += disc(cx, cz, 1.6, r - 0.2, "dark", up=False)
    for sx in (4.0, 10.8):  # straps: over the shoulder and down the chest
        els.append(cube([sx, 11.9, 3.6], [sx + 1.2, 12.5, 8.4], "strap"))
        els.append(cube([sx, 4.0, 3.4], [sx + 1.2, 12.5, 3.9], "strap"))
    els.append(cube([4.0, 6.0, 3.3], [12.0, 6.8, 3.8], "strap"))  # chest strap
    if advanced:
        els.append(cube([6.5, 2.5, 9.4], [9.5, 9.5, 12.2], "frame"))   # turbine housing
        els.append(cube([7.0, 8.5, 12.2], [9.0, 9.2, 12.6], "glow"))    # intake glow
        for side, x in ((-1, -1.2), (1, 14.6)):                        # stubby wings
            els.append(cube([x, 7.0, 9.6], [x + 2.6, 7.8, 13.0], "red"))
    else:
        els.append(cube([7.2, 4.0, 9.4], [8.8, 8.0, 10.4], "steel"))   # battery box
    return els


JET_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [1, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, -45, 0], "translation": [1, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 8, 0], "scale": [0.8, 0.8, 0.8]},
}

POD_TEX = {"hull": t("capsule_hull"), "side": t("return_pod_side"), "top": t("return_pod_top"),
           "shield": t("capsule_heat_shield"), "dark": t("sat_dark"), "steel": t("sat_steel")}


def return_pod():
    """Block px, porthole facing south (+z; the blockstate turns it)."""
    els = ring(8, 8, 0, 2, 7.2, 7.2, "shield", th=0.8, phase=0)
    els += disc(8, 8, 0.6, 7.0, "shield", up=False)
    els += ring(8, 8, 2, 11, 7.0, 4.3, "hull", th=0.8, phase=0, sides=[1, 2, 3, 4, 5, 6, 7])
    els += ring(8, 8, 2, 11, 7.0, 4.3, "side", th=0.8, phase=0, sides=[0])
    els += disc(8, 8, 2.6, 6.6, "dark", down=False)  # floor (seen through the open top in the item)
    els += ring(8, 8, 11, 13, 4.3, 3.0, "hull", th=0.8, phase=0)
    els += disc(8, 8, 13, 3.0, "top")
    els.append(cube([6, 13, 6], [10, 14.6, 10], "top"))
    for k in range(4):  # attitude thrusters
        els += spin([cube([7.4, 8, 12.4], [8.6, 9.2, 13.4], "steel")], 45 + k * 90, [8, 8, 8])
    return els


POD_DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
               "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
               "fixed": {"scale": [0.5, 0.5, 0.5]},
               "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
               "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}

SIDES = ("north", "east", "south", "west")
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blocks(ctx):
    A = ctx.ASSETS
    ctx.block_model("crew_capsule_lv", {"ambientocclusion": False,
                                        **model(shift(capsule(), CAPSULE_BASE), CAPSULE_TEX, particle=t("capsule_hull"))})
    for adv in (False, True):
        name = f"jetpack_{'advanced' if adv else 'electric'}_worn"
        ctx.block_model(name, {"ambientocclusion": False, **model(jetpack(adv), JET_TEX, particle=JET_TEX["frame"])})
    # return pod
    ctx.block_model("return_pod", {"ambientocclusion": False, **model(return_pod(), POD_TEX, particle=t("capsule_hull"))})
    pod_rot = {"south": 0, "west": 90, "north": 180, "east": 270}
    ctx.write(A / "blockstates" / "return_pod.json", {"variants": {
        f"facing={f}": {"model": f"{MOD}:block/return_pod", **({"y": r} if r else {})} for f, r in pod_rot.items()}})
    ctx.write(A / "models" / "item" / "return_pod.json", {"parent": f"{MOD}:block/return_pod", "display": POD_DISPLAY})
    ctx.item_def("return_pod", "item/return_pod")
    # oxygen compressor: orientable, front lights up
    for on in (False, True):
        sfx = "_on" if on else ""
        ctx.block_model(f"oxygen_compressor{sfx}", {"parent": "minecraft:block/orientable", "textures": {
            "top": t("oxygen_compressor_top"), "front": t(f"oxygen_compressor_front{sfx}"), "side": t("oxygen_compressor_side")}})
        ctx.block_model(f"oxygen_sealer{sfx}", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": t(f"oxygen_sealer_top{sfx}"), "bottom": t("oxygen_compressor_top"), "side": t(f"oxygen_sealer_side{sfx}")}})
    ctx.write(A / "blockstates" / "oxygen_compressor.json", {"variants": {
        f"facing={f},lit={str(on).lower()}": {"model": f"{MOD}:block/oxygen_compressor{'_on' if on else ''}",
                                             **({"y": ROT[f]} if ROT[f] else {})}
        for f in SIDES for on in (False, True)}})
    ctx.write(A / "blockstates" / "oxygen_sealer.json", {"variants": {
        f"facing={f},lit={str(on).lower()}": {"model": f"{MOD}:block/oxygen_sealer{'_on' if on else ''}"}
        for f in SIDES for on in (False, True)}})
    for name in ("oxygen_compressor", "oxygen_sealer"):
        ctx.write(A / "models" / "item" / f"{name}.json", {"parent": f"{MOD}:block/{name}"})
        ctx.item_def(name, f"item/{name}")
    for block in PICKAXE_BLOCKS:
        ctx.loot_self(block)


def items(ctx):
    A = ctx.ASSETS
    for name in ("astronaut_helmet", "astronaut_suit", "astronaut_leggings", "astronaut_boots", "jet_suit", "crew_capsule"):
        ctx.flat_item(name)
    for name, worn in (("electric_jetpack", "jetpack_electric_worn"), ("advanced_jetpack", "jetpack_advanced_worn")):
        ctx.write(A / "models" / "item" / f"{name}_icon.json", {"parent": "minecraft:item/generated",
                                                                "textures": {"layer0": f"{MOD}:item/{name}"}})
        ctx.write(A / "models" / "item" / f"{name}_3d.json", {"parent": f"{MOD}:block/{worn}", "display": JET_DISPLAY})
        ctx.write(A / "items" / f"{name}.json", {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}_icon"}}],
            "fallback": {"type": "minecraft:model", "model": f"{MOD}:item/{name}_3d"}}})
    ctx.write(A / "equipment" / "jetpack.json", {"layers": {"humanoid": [{"texture": f"{MOD}:jetpack"}]}})  # the harness
    ctx.write(A / "equipment" / "astronaut.json", {"layers": {
        "humanoid": [{"texture": f"{MOD}:astronaut"}],
        "humanoid_leggings": [{"texture": f"{MOD}:astronaut"}]}})


# ============================================================ data

def dimension(ctx):
    D = ctx.DATA / MOD
    sky = {"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#000000"}
    ctx.write(D / "dimension_type" / "orbit.json", {
        "ambient_light": 0.1,
        "attributes": {
            **sky,
            "minecraft:visual/star_brightness": 1.0,
            "minecraft:visual/sun_angle": 300.0,
            "minecraft:visual/moon_angle": 180.0,
            "minecraft:visual/sky_light_factor": 1.0,
            "minecraft:visual/sky_light_color": "#dde8ff",
            "minecraft:visual/ambient_light_color": "#141824",
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "explodes": False},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/water_evaporates": True,
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 6000,
                                                             "replace_current_music": True, "sound": "minecraft:music.end"}},
        },
        "cardinal_light": "default", "coordinate_scale": 1.0, "has_ceiling": False,
        "has_ender_dragon_fight": False, "has_fixed_time": True, "has_skylight": True,
        "height": 256, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 256, "min_y": 0,
        "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0, "skybox": "overworld"})
    features = [[] for _ in range(11)]
    ctx.write(D / "worldgen" / "biome" / "orbit.json", {
        "attributes": sky, "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"},
        "features": features, "has_precipitation": False, "spawn_costs": {},
        "spawners": {k: [] for k in ["ambient", "axolotls", "creature", "misc", "monster",
                                     "underground_water_creature", "water_ambient", "water_creature"]},
        "temperature": 0.5})
    ctx.write(D / "dimension" / "orbit.json", {
        "type": f"{MOD}:orbit",
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": f"{MOD}:orbit", "features": False, "lakes": False, "structure_overrides": [],
            "layers": [{"block": "minecraft:air", "height": 1}]}}})
    ctx.write(D / "damage_type" / "vacuum.json", {"exhaustion": 0.0, "message_id": f"{MOD}.vacuum",
                                                  "scaling": "never", "effects": "freezing"})
    for tag in ("bypasses_armor", "bypasses_shield", "no_knockback", "bypasses_cooldown"):
        ctx.write(ctx.DATA / "minecraft" / "tags" / "damage_type" / f"{tag}.json", {"replace": False, "values": [f"{MOD}:vacuum"]})
    ctx.write(D / "tags" / "entity_type" / "sealed_vehicles.json", {"replace": False, "values": []})


def recipes(ctx):
    TP, SP, AC = "#c:plates/titanium", "#c:plates/steel", "advanced_circuit"
    # Electric age: the first jetpack
    ctx.shaped("electric_jetpack", ["PCP", "PEP", "M M"], {"P": SP, "C": "basic_circuit", "E": "energy_cell", "M": "motor"},
               "electric_jetpack", category="equipment")
    # Industrial: titanium, advanced circuits, heating coils for afterburners
    ctx.shaped("advanced_jetpack", ["TAT", "TJT", "H H"], {"T": TP, "A": AC, "J": "electric_jetpack", "H": "heating_coil"},
               "advanced_jetpack", category="equipment")
    # Orbital: the suit, capsule, pod and life support
    ctx.shaped("astronaut_helmet", ["TAT", "TGT"], {"T": TP, "A": AC, "G": "minecraft:tinted_glass"},
               "astronaut_helmet", category="equipment")
    ctx.shaped("astronaut_suit", ["W W", "TMT", "THT"], {"W": "minecraft:white_wool", "T": TP, "M": "motor", "H": "heating_coil"},
               "astronaut_suit", category="equipment")
    ctx.shaped("astronaut_leggings", ["TWT", "W W", "T T"], {"T": TP, "W": "minecraft:white_wool"},
               "astronaut_leggings", category="equipment")
    ctx.shaped("astronaut_boots", ["W W", "T T"], {"T": TP, "W": "minecraft:white_wool"}, "astronaut_boots", category="equipment")
    ctx.write(ctx.DATA / MOD / "recipe" / "jet_suit.json", {
        "type": f"{MOD}:jet_suit", "category": "equipment",
        "input": f"{MOD}:astronaut_suit", "material": f"{MOD}:advanced_jetpack", "result": {"id": f"{MOD}:jet_suit"}})
    ctx.shaped("crew_capsule", ["TGT", "TOT", "HFH"], {"T": TP, "G": "minecraft:tinted_glass", "O": "orbital_targeting_core",
                                                      "H": "fire_bricks", "F": "advanced_machine_frame"}, "crew_capsule")
    ctx.shaped("return_pod", ["TGT", "WAW", "HHH"], {"T": TP, "G": "minecraft:tinted_glass", "W": "minecraft:white_wool",
                                                    "A": AC, "H": "fire_bricks"}, "return_pod")
    ctx.shaped("oxygen_compressor", ["TGT", "MFM", "TCT"], {"T": TP, "G": "minecraft:glass", "M": "motor",
                                                           "F": "advanced_machine_frame", "C": AC}, "oxygen_compressor")
    ctx.shaped("oxygen_sealer", ["GGG", "TXT", "TCT"], {"G": "minecraft:glass", "T": TP, "X": "oxygen_compressor", "C": AC},
               "oxygen_sealer")


def code_advancement(ctx, key, parent, icon, frame, en, es, hidden=False):
    display = {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    if hidden:
        display["hidden"] = True
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}", "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": display, "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def advancements(ctx):
    code_advancement(ctx, "jetpack_flight", "electric_circuit", "electric_jetpack", "goal",
                     ("Up, Up and Away", "Take off with a jetpack (hold jump in the air)"),
                     ("¡Arriba y lejos!", "Despega con una mochila propulsora (mantén saltar en el aire)"))
    ctx.advancement("space_suit", "orbital_pad", "astronaut_helmet", ["astronaut_suit"], "Suit Up",
                    "Make an Astronaut Suit and fill it with air in an Oxygen Compressor",
                    "Vístete para la ocasión",
                    "Fabrica un traje de astronauta y llénalo de aire en un compresor de oxígeno")
    code_advancement(ctx, "space_orbit", "orbital_pad", "crew_capsule", "goal",
                     ("One Small Step", "Ride a Crew Capsule into orbit"),
                     ("Un pequeño paso", "Viaja a la órbita en una cápsula tripulada"))
    code_advancement(ctx, "space_reentry", "space_orbit", "return_pod", "task",
                     ("Re-entry", "Come back down from orbit in a Return Pod and land in one piece"),
                     ("Reentrada", "Vuelve de la órbita en una cápsula de retorno y aterriza de una pieza"))
    code_advancement(ctx, "space_houston", "space_orbit", "astronaut_helmet", "challenge",
                     ("Houston, We Have a Problem", "Go to space without a suit. Once."),
                     ("Houston, tenemos un problema", "Ve al espacio sin traje. Una vez."), hidden=True)
    ctx.advancement("space_jet_suit", "space_suit", "jet_suit", ["jet_suit"], "Rocketeer",
                    "Build an Advanced Jetpack into your suit: a Jet Suit",
                    "Hombre cohete", "Integra una mochila propulsora avanzada en tu traje: un traje propulsor")


def lang(L):
    B, I, T, M, G, H = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"gui.{MOD}", f"hud.{MOD}"
    L(f"{B}.return_pod", "Return Pod", "Cápsula de retorno")
    L(f"{B}.oxygen_compressor", "Oxygen Compressor", "Compresor de oxígeno")
    L(f"{B}.oxygen_sealer", "Oxygen Sealer", "Sellador de oxígeno")
    L(f"{I}.astronaut_helmet", "Astronaut Helmet", "Casco de astronauta")
    L(f"{I}.astronaut_suit", "Astronaut Suit", "Traje de astronauta")
    L(f"{I}.astronaut_leggings", "Astronaut Leggings", "Pantalones de astronauta")
    L(f"{I}.astronaut_boots", "Astronaut Boots", "Botas de astronauta")
    L(f"{I}.jet_suit", "Jet Suit", "Traje propulsor")
    L(f"{I}.electric_jetpack", "Electric Jetpack", "Mochila propulsora eléctrica")
    L(f"{I}.advanced_jetpack", "Advanced Jetpack", "Mochila propulsora avanzada")
    L(f"{I}.crew_capsule", "Crew Capsule", "Cápsula tripulada")
    L(f"entity.{MOD}.rocket_seat", "Rocket Seat", "Asiento del cohete")
    L(f"orbital.{MOD}.dimension.{MOD}.orbit", "orbit", "la órbita")
    L(f"key.{MOD}.jetpack_hover", "Jetpack hover mode", "Modo flotar de la mochila")
    L(f"key.category.{MOD}.space_gear", "Factory Ascent: gear", "Factory Ascent: equipo")
    L(f"death.attack.{MOD}.vacuum", "%1$s went to space without a suit", "%1$s fue al espacio sin traje")
    L(f"death.attack.{MOD}.vacuum.player", "%1$s ran out of air in space while fighting %2$s",
      "%1$s se quedó sin aire en el espacio mientras luchaba contra %2$s")

    L(f"{T}.suit_oxygen", "Air %s %s%% (%s)", "Aire %s %s%% (%s)")
    L(f"{T}.suit_piece", "Astronaut Suit: wear all four pieces to breathe in space",
      "Traje de astronauta: lleva las cuatro piezas para respirar en el espacio")
    L(f"{T}.suit_helmet", "Can come off inside an Oxygen Sealer's bubble",
      "Te lo puedes quitar dentro de la burbuja de un sellador de oxígeno")
    L(f"{T}.suit_chest", "Holds the suit's air. Only used where there is no air (orbit)",
      "Guarda el aire del traje. Solo se usa donde no hay aire (órbita)")
    L(f"{T}.suit_refill", "Refill it in an Oxygen Compressor (slot, or stand next to it)",
      "Rellénalo en un compresor de oxígeno (ranura, o de pie a su lado)")
    L(f"{T}.jetpack", "Wear it, hold jump to fly (%s FE/t)", "Póntela y mantén saltar para volar (%s FE/t)")
    L(f"{T}.jetpack_hover_on", "Hover: on", "Flotar: activado")
    L(f"{T}.jetpack_hover_off", "Hover: off", "Flotar: desactivado")
    L(f"{T}.jetpack_hover_hint", "Hover key (H) or sneak-use: toggle hover. Charge it in an Energy Cell or Charger",
      "Tecla de flotar (H) o usar agachado: flotar. Cárgala en una celda de energía o un cargador")
    L(f"{T}.crew_capsule", "Mount it on a Launch Pad, then Board from the Launch Controller",
      "Móntala en una plataforma de lanzamiento y sube desde el controlador de lanzamiento")
    L(f"{T}.crew_capsule_fuel", "A crewed launch burns %s fuel and takes you to orbit",
      "Un lanzamiento tripulado gasta %s de combustible y te lleva a la órbita")
    L(f"{T}.crew_capsule_suit", "No suit, no air: without a full Astronaut Suit you won't survive up there",
      "Sin traje no hay aire: sin el traje de astronauta completo no sobrevivirás allá arriba")
    L(f"{T}.return_pod", "Use it in orbit to go back down over your launch site",
      "Úsala en la órbita para volver sobre tu sitio de lanzamiento")
    L(f"{T}.return_pod_how", "Heat shield and parachute included. Every station deck starts with one",
      "Con escudo térmico y paracaídas. Toda cubierta de estación empieza con una")
    L(f"{T}.oxygen_compressor", "Fills Astronaut Suits with air: one in its slot, or worn by players standing on or next to it",
      "Llena de aire los trajes de astronauta: uno en su ranura, o los que llevan los jugadores encima o al lado")
    L(f"{T}.oxygen_compressor_use", "%s FE per second of air", "%s FE por segundo de aire")
    L(f"{T}.oxygen_sealer", "Keeps a breathable bubble %s blocks around itself: helmets off inside",
      "Mantiene una burbuja respirable de %s bloques a su alrededor: sin casco dentro")
    L(f"{T}.oxygen_sealer_use", "Uses %s FE/t", "Consume %s FE/t")

    for key, en, es in [
        ("orbit_arrived", "You are in orbit. The Return Pod takes you back down.",
         "Estás en órbita. La cápsula de retorno te lleva de vuelta."),
        ("orbit_no_suit", "NO SUIT! There is no air up here...", "¡SIN TRAJE! Aquí arriba no hay aire..."),
        ("orbit_unavailable", "Orbit isn't reachable on this server: the capsule came back down.",
         "La órbita no está disponible en este servidor: la cápsula volvió a bajar."),
        ("reentry", "Re-entry! Hold on...", "¡Reentrada! Agárrate..."),
        ("reentry_landed", "Touchdown. Welcome home.", "Aterrizaje. Bienvenido a casa."),
        ("return_pod_ground", "The Return Pod only works in orbit", "La cápsula de retorno solo funciona en la órbita"),
        ("vacuum", "NO AIR! You are suffocating", "¡SIN AIRE! Te estás asfixiando"),
        ("oxygen_low", "Air low: %s s left", "Queda poco aire: %s s"),
        ("crew_board_first", "Board the Crew Capsule to launch it", "Sube a la cápsula tripulada para lanzarla"),
        ("crew_no_capsule", "There's no Crew Capsule on this pad", "No hay una cápsula tripulada en esta plataforma"),
        ("crew_full", "Someone is already aboard", "Ya hay alguien a bordo"),
        ("crew_riding", "Get off what you are riding first", "Primero bájate de lo que estás montando"),
        ("crew_too_far", "Stand next to the pad to board", "Ponte junto a la plataforma para subir"),
        ("crew_boarded", "Strapped in. Liftoff in 3 seconds (sneak to climb out before then).",
         "Cinturones puestos. Despegue en 3 segundos (agáchate para bajar antes)."),
        ("crew_no_suit", "No suit: you will not survive in orbit!", "Sin traje: ¡no sobrevivirás en la órbita!"),
        ("jetpack_none", "You aren't wearing a jetpack", "No llevas una mochila propulsora"),
        ("jetpack_hover_on", "Jetpack hover: ON", "Flotar: ACTIVADO"),
        ("jetpack_hover_off", "Jetpack hover: OFF", "Flotar: DESACTIVADO"),
        ("sealer_on", "Oxygen Sealer running: breathable air within %s blocks (%s FE stored)",
         "Sellador en marcha: aire respirable en %s bloques (%s FE almacenados)"),
        ("sealer_off", "Oxygen Sealer off: needs power (%s FE/t)", "Sellador apagado: necesita energía (%s FE/t)"),
    ]:
        L(f"{M}.{key}", en, es)
    for key, en, es in [
        ("pad.board", "Board", "Subir"),
        ("pad.board_tip", "Climb into the Crew Capsule: the countdown starts at once",
         "Sube a la cápsula tripulada: la cuenta atrás empieza enseguida"),
        ("pad.board_no_suit_tip", "You are not wearing a full Astronaut Suit with air. You will not survive in orbit!",
         "No llevas el traje de astronauta completo con aire. ¡No sobrevivirás en la órbita!"),
        ("pad.status.board", "Ready to board", "Listo para subir"),
        ("pad.status.no_suit", "No suit: fatal!", "Sin traje: ¡mortal!"),
        ("compressor.insert", "Put in an Astronaut Suit", "Pon un traje de astronauta"),
        ("compressor.full", "Suit full", "Traje lleno"),
        ("compressor.no_power", "No power", "Sin energía"),
        ("compressor.filling", "Filling...", "Llenando..."),
        ("compressor.slot_tip", "Astronaut Suit or Jet Suit (or just stand next to the compressor wearing it)",
         "Traje de astronauta o traje propulsor (o ponte al lado del compresor con él puesto)"),
    ]:
        L(f"{G}.{key}", en, es)
    for key, en, es in [
        ("air", "O₂", "O₂"), ("jet", "JET", "JET"), ("hover", "HOVER", "FLOTAR"),
        ("breath_bubble", "sealed area", "zona sellada"), ("breath_cabin", "cabin air", "aire de cabina"),
        ("no_air", "NO AIR", "SIN AIRE"),
        ("no_air_hint", "Put on a full Astronaut Suit with air, or find an Oxygen Sealer's bubble",
         "Ponte el traje de astronauta completo con aire, o busca la burbuja de un sellador de oxígeno"),
        ("air_low", "AIR LOW", "POCO AIRE"),
        ("t_minus", "T-%s", "T-%s"), ("climb_out", "Sneak to climb out", "Agáchate para bajar"),
        ("altitude", "Altitude %s m", "Altitud %s m"),
    ]:
        L(f"{H}.{key}", en, es)


def generate(ctx):
    blocks(ctx)
    items(ctx)
    dimension(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx.lang)


# ============================================================ preview

def preview(path):
    """Renders the capsule on the rocket, both jetpacks and the Return Pod (satellites.py's rasteriser)."""
    from PIL import Image
    drill = S._load("drill")
    tex_keys = {**CAPSULE_TEX, **JET_TEX, **POD_TEX}
    textures = {k: Image.open(S.ASSETS / "textures" / "block" / f"{v.split('/')[-1]}.png").convert("RGBA")
                for k, v in tex_keys.items()}
    bg = (28, 30, 38, 255)
    panels = []
    rocket = S.stage_lower() + S.stage_upper() + S.adapter() + capsule()
    for yaw, pitch in ((-20, 10), (35, 20)):
        m = S._mul(drill._rot("x", pitch), drill._rot("y", yaw))
        q = S._quads(rocket, {**textures, **{k: Image.open(S.ASSETS / "textures" / "block" / f"{v.split('/')[-1]}.png").convert("RGBA")
                                             for k, v in S.LV_TEX.items()}})
        panels.append(drill._render_mixed([(q, lambda p, m=m: S._app(m, [(p[0] - 8) / 16, (p[1] - 100) / 16, (p[2] - 8) / 16]))],
                                          lambda v: (150 + v[0] * 110, 200 - v[1] * 110), (300, 420), bg))
    for els in (jetpack(False), jetpack(True), return_pod()):
        for yaw, pitch in ((150, 20), (-120, 15)):
            m = S._mul(drill._rot("x", pitch), drill._rot("y", yaw))
            q = S._quads(els, textures)
            panels.append(drill._render_mixed([(q, lambda p, m=m: S._app(m, [(p[i] - (6 if i == 1 else 8)) / 16 for i in range(3)]))],
                                              lambda v: (110 + v[0] * 160, 110 - v[1] * 160), (220, 220), bg))
    out = Image.new("RGBA", (300 * 2 + 220 * 3 + 40, 460), (20, 20, 26, 255))
    out.alpha_composite(panels[0], (10, 20))
    out.alpha_composite(panels[1], (320, 20))
    for i, im in enumerate(panels[2:]):
        out.alpha_composite(im, (640 + (i // 2) * 225, 10 + (i % 2) * 225))
    out.save(path)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(HERE.parent / "space_models_preview.png")
        print("wrote tools/space_models_preview.png")

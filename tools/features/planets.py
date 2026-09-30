"""The planets beyond Earth orbit (Java: space/planet/, space/client/SpaceSkies):

  dimensions         data/factoryascent/dimension{,_type}/{moon,mars,io}.json: noise generators with their
                     own noise settings (worldgen/noise_settings), noises, a biome each, surface rules
  Moon               grey regolith over moon rock, maria lowlands and highlands, impact craters
                     (factoryascent:crater feature), Helium-3-rich regolith; black sky with the sun and Earth
  Mars               red dunes and sand over layered rock, deep flat-floored canyons, craters, hematite ore and
                     buried Martian ice; a butterscotch sky with blue sunsets (timeline mars_day), dust storms
  Io                 sulfur plains over black volcanic rock, lava lakes (lava sea level), volcano cones,
                     Ionite ore; a black sky with giant Jupiter
  heat damage type   data/factoryascent/damage_type/heat.json (Io without a Thermal Lining)
Blocks, items, loot, recipes, advancements (landing on each planet) and lang (English + Spanish).
"""

MOD = "factoryascent"
PICKAXE_BLOCKS = ["moon_rock", "hematite_ore", "martian_ice", "io_rock", "ionite_ore"]
SHOVEL_BLOCKS = ["moon_regolith", "helium_3_regolith", "mars_sand"]
TERRAIN = SHOVEL_BLOCKS + PICKAXE_BLOCKS
ITEMS = ["helium_3", "raw_hematite", "ionite", "helium_3_fuel_cell", "thermal_lining", "ion_drive", "star_chart"]
SPAWNERS = ["ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient", "water_creature"]
HEIGHT = 256

# The vanilla day timeline's sky tracks (26.2 data/minecraft/timeline/day.json), less clouds; Mars' copy
# turns its sunrise and sunset blue.
DAY_TRACKS = {'minecraft:gameplay/sky_light_level': {'keyframes': [{'ticks': 133, 'value': 1.0}, {'ticks': 11867, 'value': 1.0}, {'ticks': 13670, 'value': 0.26666668}, {'ticks': 22330, 'value': 0.26666668}], 'modifier': 'multiply'}, 'minecraft:visual/fog_color': {'keyframes': [{'ticks': 133, 'value': '#ffffff'}, {'ticks': 11867, 'value': '#ffffff'}, {'ticks': 13670, 'value': '#0c0c16'}, {'ticks': 22330, 'value': '#161616'}], 'modifier': 'multiply'}, 'minecraft:visual/sky_color': {'keyframes': [{'ticks': 133, 'value': '#ffffff'}, {'ticks': 11867, 'value': '#ffffff'}, {'ticks': 13670, 'value': '#000000'}, {'ticks': 22330, 'value': '#000000'}], 'modifier': 'multiply'}, 'minecraft:visual/sky_light_color': {'keyframes': [{'ticks': 730, 'value': '#ffffff'}, {'ticks': 11270, 'value': '#ffffff'}, {'ticks': 13140, 'value': '#7a7aff'}, {'ticks': 22860, 'value': '#7a7aff'}], 'modifier': 'multiply'}, 'minecraft:visual/sky_light_factor': {'keyframes': [{'ticks': 730, 'value': 1.0}, {'ticks': 11270, 'value': 1.0}, {'ticks': 13140, 'value': 0.24}, {'ticks': 22860, 'value': 0.24}], 'modifier': 'multiply'}, 'minecraft:visual/star_angle': {'ease': {'cubic_bezier': [0.362, 0.241, 0.638, 0.759]}, 'keyframes': [{'ticks': 6000, 'value': 360.0}, {'ticks': 6000, 'value': 0.0}]}, 'minecraft:visual/star_brightness': {'keyframes': [{'ticks': 92, 'value': 0.037}, {'ticks': 627, 'value': 0.0}, {'ticks': 11373, 'value': 0.0}, {'ticks': 11732, 'value': 0.016}, {'ticks': 11959, 'value': 0.044}, {'ticks': 12399, 'value': 0.143}, {'ticks': 12729, 'value': 0.258}, {'ticks': 13228, 'value': 0.5}, {'ticks': 22772, 'value': 0.5}, {'ticks': 23032, 'value': 0.364}, {'ticks': 23356, 'value': 0.225}, {'ticks': 23758, 'value': 0.101}], 'modifier': 'maximum'}, 'minecraft:visual/sun_angle': {'ease': {'cubic_bezier': [0.362, 0.241, 0.638, 0.759]}, 'keyframes': [{'ticks': 6000, 'value': 360.0}, {'ticks': 6000, 'value': 0.0}]}, 'minecraft:visual/sunrise_sunset_color': {'keyframes': [{'ticks': 71, 'value': '#5fefa333'}, {'ticks': 310, 'value': '#29f5ba33'}, {'ticks': 565, 'value': '#06fbd433'}, {'ticks': 730, 'value': '#00ffe533'}, {'ticks': 11270, 'value': '#00ffe533'}, {'ticks': 11397, 'value': '#04fcd833'}, {'ticks': 11522, 'value': '#0ff9cb33'}, {'ticks': 11690, 'value': '#29f5ba33'}, {'ticks': 11929, 'value': '#5fefa333'}, {'ticks': 12243, 'value': '#b1e78733'}, {'ticks': 12358, 'value': '#cce47e33'}, {'ticks': 12512, 'value': '#e9e07233'}, {'ticks': 12613, 'value': '#f6dd6b33'}, {'ticks': 12732, 'value': '#feda6333'}, {'ticks': 12841, 'value': '#fed75c33'}, {'ticks': 13035, 'value': '#ecd25133'}, {'ticks': 13252, 'value': '#c1cc4733'}, {'ticks': 13775, 'value': '#36be3733'}, {'ticks': 13888, 'value': '#1fbb3533'}, {'ticks': 14039, 'value': '#09b73333'}, {'ticks': 14192, 'value': '#00b33333'}, {'ticks': 21807, 'value': '#00b23333'}, {'ticks': 21961, 'value': '#09b73333'}, {'ticks': 22112, 'value': '#1fbb3533'}, {'ticks': 22225, 'value': '#36be3733'}, {'ticks': 22748, 'value': '#c1cc4733'}, {'ticks': 22965, 'value': '#ecd25133'}, {'ticks': 23159, 'value': '#fed75c33'}, {'ticks': 23272, 'value': '#feda6333'}, {'ticks': 23488, 'value': '#e9e07233'}, {'ticks': 23642, 'value': '#cce47e33'}, {'ticks': 23757, 'value': '#b1e78733'}]}}


def ns(x):
    return x if ":" in x else f"{MOD}:{x}"


def state(name):
    return {"Name": ns(name)}


# ============================================================ density functions

def noise(name, weight, xz=1.0, y=0.0):
    return {"type": "minecraft:mul", "argument1": weight,
            "argument2": {"type": "minecraft:noise", "noise": f"{MOD}:{name}", "xz_scale": xz, "y_scale": y}}


def add(*args):
    out = args[0]
    for a in args[1:]:
        out = {"type": "minecraft:add", "argument1": out, "argument2": a}
    return out


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def gradient(base, slope=40.0):
    """1 below the ground line at y = base, falling 1/slope per block above it."""
    return {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": HEIGHT,
            "from_value": base / slope, "to_value": (base - HEIGHT) / slope}


def raw(name, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": f"{MOD}:{name}", "xz_scale": xz, "y_scale": y}


NOISES = {
    "moon_hills": (-7, [1.0, 1.0, 0.5, 0.25]),
    "moon_maria": (-9, [1.0, 0.5]),
    "moon_rough": (-4, [1.0, 0.5]),
    "mars_hills": (-8, [1.0, 1.0, 0.5]),
    "mars_dunes": (-5, [1.0, 0.3]),
    "mars_canyon": (-9, [1.0, 0.4]),
    "mars_patch": (-5, [1.0, 1.0]),
    "io_plains": (-8, [1.0, 0.5, 0.25]),
    "io_volcano": (-8, [1.0]),
    "io_rough": (-4, [1.0, 0.5]),
    "io_patch": (-5, [1.0, 0.5]),
}


def moon_density():
    return add(gradient(74), noise("moon_hills", 0.45), noise("moon_maria", 0.4), noise("moon_rough", 0.05, 1.0, 1.0))


def mars_density():
    canyon = clamp(mul(add(0.085, mul(-1.0, {"type": "minecraft:abs", "argument": raw("mars_canyon")})), 22.0), 0.0, 1.0)
    return add(gradient(72), noise("mars_hills", 0.5), noise("mars_dunes", 0.07), mul(-1.15, canyon),
               noise("moon_rough", 0.03, 1.0, 1.0))


def io_density():
    cone = clamp(mul(add(raw("io_volcano"), -0.42), 3.2), 0.0, 1.0)
    return add(gradient(64), noise("io_plains", 0.25), mul(1.3, {"type": "minecraft:square", "argument": cone}),
               noise("io_rough", 0.06, 1.0, 1.0))


# ============================================================ surface rules

def block_rule(name):
    return {"type": "minecraft:block", "result_state": state(name)}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


BEDROCK = cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, block_rule("minecraft:bedrock"))


def floor(depth_extra=True, offset=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "add_surface_depth": depth_extra,
            "secondary_depth_range": 0, "surface_type": "floor"}


def above(y):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": 0, "add_stone_depth": False}


def patch(noise_name, lo, hi=10.0):
    return {"type": "minecraft:noise_threshold", "noise": f"{MOD}:{noise_name}", "min_threshold": lo, "max_threshold": hi}


def surfaces():
    moon = seq(BEDROCK, cond(floor(), block_rule("moon_regolith")))
    mars = seq(BEDROCK, cond(floor(), seq(
        cond({"type": "minecraft:steep"}, block_rule("mars_rock")),
        cond({"type": "minecraft:not", "invert": above(40)}, block_rule("mars_rock")),   # canyon floors: bare rock
        cond(patch("mars_patch", 0.55), block_rule("mars_rock")),
        block_rule("mars_sand"))))
    io = seq(BEDROCK, cond(floor(False), seq(
        cond({"type": "minecraft:not", "invert": above(60)}, block_rule("minecraft:magma_block")),  # lake shores
        cond({"type": "minecraft:steep"}, block_rule("minecraft:basalt")),
        cond(patch("io_patch", 0.1), block_rule("minecraft:sulfur")),
        cond(patch("io_patch", -0.35), block_rule("io_rock")),
        block_rule("minecraft:smooth_basalt"))),
        cond(floor(True, 1), cond(patch("io_patch", 0.1), block_rule("minecraft:sulfur"))))
    return {"moon": moon, "mars": mars, "io": io}


def noise_settings(name, density, default_block, default_fluid, sea_level, surface):
    router = {k: 0.0 for k in ["barrier", "continents", "depth", "erosion", "fluid_level_floodedness", "fluid_level_spread",
                               "lava", "ridges", "temperature", "vegetation", "vein_gap", "vein_ridged", "vein_toggle"]}
    router["preliminary_surface_level"] = 70.0
    router["final_density"] = {"type": "minecraft:interpolated", "argument": density}
    return {"aquifers_enabled": False, "default_block": state(default_block), "default_fluid": state(default_fluid),
            "disable_mob_generation": True, "legacy_random_source": False,
            "noise": {"height": HEIGHT, "min_y": 0, "size_horizontal": 1, "size_vertical": 2},
            "noise_router": router, "ore_veins_enabled": False, "sea_level": sea_level, "spawn_target": [],
            "surface_rule": surface}


# ============================================================ features

def ore(ctx, name, block, targets, size, count, lo, hi, air=0.0):
    wg = ctx.DATA / MOD / "worldgen"
    ctx.write(wg / "configured_feature" / f"{name}.json", {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": air, "size": size,
        "targets": [{"state": state(block), "target": {"predicate_type": "minecraft:block_match", "block": ns(t)}} for t in targets]}})
    ctx.write(wg / "placed_feature" / f"{name}.json", {"feature": f"{MOD}:{name}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo},
                                                        "max_inclusive": {"absolute": hi}}},
        {"type": "minecraft:biome"}]})
    return f"{MOD}:{name}"


def crater(ctx, name, chance):
    wg = ctx.DATA / MOD / "worldgen"
    ctx.write(wg / "configured_feature" / "crater.json", {"type": f"{MOD}:crater", "config": {}})
    ctx.write(wg / "placed_feature" / f"{name}.json", {"feature": f"{MOD}:crater", "placement": [
        {"type": "minecraft:rarity_filter", "chance": chance}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    return f"{MOD}:{name}"


def biome(ctx, name, attributes, local=(), ores=(), particles=None):
    features = [[] for _ in range(11)]
    features[2] = list(local)   # local_modifications: craters
    features[6] = list(ores)    # underground_ores
    attrs = dict(attributes)
    if particles:
        attrs["minecraft:visual/ambient_particles"] = particles
    ctx.write(ctx.DATA / MOD / "worldgen" / "biome" / f"{name}.json", {
        "attributes": attrs, "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"},
        "features": features, "has_precipitation": False, "spawn_costs": {},
        "spawners": {k: [] for k in SPAWNERS}, "temperature": 2.0 if name == "io" else -0.5})


def dimension_type(ctx, name, attributes, ambient=0.1, timelines=None, clock=None):
    base = {
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "explodes": False},
        "minecraft:gameplay/can_start_raid": False,
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:gameplay/water_evaporates": name != "mars",
        "minecraft:visual/cloud_color": "#00ffffff",
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 6000,
                                                         "replace_current_music": True, "sound": "minecraft:music.end"}},
    }
    base.update(attributes)
    obj = {"ambient_light": ambient, "attributes": base, "cardinal_light": "default", "coordinate_scale": 1.0,
           "has_ceiling": False, "has_ender_dragon_fight": False, "has_fixed_time": clock is None, "has_skylight": True,
           "height": HEIGHT, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": HEIGHT, "min_y": 0,
           "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0, "skybox": "overworld"}
    if timelines:
        obj["timelines"] = timelines
    if clock:
        obj["default_clock"] = clock
    ctx.write(ctx.DATA / MOD / "dimension_type" / f"{name}.json", obj)
    ctx.write(ctx.DATA / MOD / "dimension" / f"{name}.json", {
        "type": f"{MOD}:{name}",
        "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:fixed", "biome": f"{MOD}:{name}"},
                      "settings": f"{MOD}:{name}"}})


def blue_sunset(track):
    out = {k: v for k, v in track.items() if k != "keyframes"}
    frames = []
    for f in track["keyframes"]:
        v = f["value"]  # "#AARRGGBB"
        frames.append({**f, "value": "#" + v[1:3] + v[7:9] + v[5:7] + v[3:5]})
    out["keyframes"] = frames
    return out


def worlds(ctx):
    wg = ctx.DATA / MOD / "worldgen"
    for name, (first, amps) in NOISES.items():
        ctx.write(wg / "noise" / f"{name}.json", {"firstOctave": first, "amplitudes": amps})
    surf = surfaces()
    ctx.write(wg / "noise_settings" / "moon.json", noise_settings("moon", moon_density(), "moon_rock", "minecraft:air", -64, surf["moon"]))
    ctx.write(wg / "noise_settings" / "mars.json", noise_settings("mars", mars_density(), "mars_rock", "minecraft:air", -64, surf["mars"]))
    ctx.write(wg / "noise_settings" / "io.json", noise_settings("io", io_density(), "io_rock", "minecraft:lava", 58, surf["io"]))

    # Moon: black sky, a fixed low sun, craters and Helium-3 in the regolith
    black = {"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#000000"}
    moon_ores = [ore(ctx, "helium_3_regolith", "helium_3_regolith", ["moon_regolith"], 7, 18, 40, 130)]
    biome(ctx, "moon", black, [crater(ctx, "moon_crater", 3)], moon_ores)
    dimension_type(ctx, "moon", {**black, "minecraft:visual/star_brightness": 1.0, "minecraft:visual/sun_angle": 320.0,
                                  "minecraft:visual/moon_angle": 180.0, "minecraft:visual/sky_light_factor": 1.0,
                                  "minecraft:visual/sky_light_color": "#e8ecff", "minecraft:visual/ambient_light_color": "#101218"})

    # Mars: a butterscotch sky on a day cycle (blue sunsets), dust in the air
    tracks = dict(DAY_TRACKS)
    tracks["minecraft:visual/sunrise_sunset_color"] = blue_sunset(DAY_TRACKS["minecraft:visual/sunrise_sunset_color"])
    ctx.write(ctx.DATA / MOD / "timeline" / "mars_day.json", {"clock": "minecraft:overworld", "period_ticks": 24000, "tracks": tracks})
    mars_sky = {"minecraft:visual/sky_color": "#d9a27a", "minecraft:visual/fog_color": "#c98f68"}
    mars_ores = [ore(ctx, "hematite_ore", "hematite_ore", ["mars_rock"], 8, 12, 0, 90),
                 ore(ctx, "martian_ice", "martian_ice", ["mars_rock", "mars_sand"], 18, 4, 28, 70)]
    biome(ctx, "mars", mars_sky, [crater(ctx, "mars_crater", 7)], mars_ores,
          particles=[{"particle": {"type": "minecraft:dust", "color": [0.78, 0.45, 0.3], "scale": 1.0}, "probability": 0.004}])
    dimension_type(ctx, "mars", {**mars_sky, "minecraft:visual/moon_angle": 180.0, "minecraft:visual/sky_light_color": "#fff0e0",
                                  "minecraft:visual/ambient_light_color": "#1c1210",
                                  "minecraft:visual/fog_start_distance": 32.0},
                   timelines=[f"{MOD}:mars_day"], clock="minecraft:overworld")

    # Io: black sky, low sun, lava lakes, ash in the air
    io_sky = {"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#120c08"}
    io_ores = [ore(ctx, "ionite_ore", "ionite_ore", ["io_rock"], 5, 8, 0, 80)]
    biome(ctx, "io", io_sky, [], io_ores, particles=[{"particle": {"type": "minecraft:ash"}, "probability": 0.012}])
    dimension_type(ctx, "io", {**io_sky, "minecraft:visual/star_brightness": 1.0, "minecraft:visual/sun_angle": 60.0,
                                "minecraft:visual/moon_angle": 180.0, "minecraft:visual/sky_light_factor": 1.0,
                                "minecraft:visual/sky_light_color": "#fff4e0", "minecraft:visual/ambient_light_color": "#1a1008",
                                "minecraft:gameplay/fast_lava": True}, ambient=0.15)

    ctx.write(ctx.DATA / MOD / "damage_type" / "heat.json", {"exhaustion": 0.0, "message_id": f"{MOD}.heat",
                                                             "scaling": "never", "effects": "burning"})


# ============================================================ blocks, items, loot

def loot_drop(ctx, name, drop, lo=1, hi=1, fortune=True, silk=True):
    entry = {"type": "minecraft:item", "name": ns(drop), "functions": []}
    if hi > lo or lo != 1:
        entry["functions"].append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if fortune:
        entry["functions"].append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                                   "formula": "minecraft:ore_drops"})
    entry["functions"].append({"function": "minecraft:explosion_decay"})
    children = []
    if silk:
        children.append({"type": "minecraft:item", "name": ns(name), "conditions": [{
            "condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]})
    children.append(entry)
    ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": children}]}],
        "random_sequence": f"{MOD}:blocks/{name}"})


def blocks(ctx):
    A = ctx.ASSETS
    for name in TERRAIN:
        ctx.block_model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{name}"}})
        ctx.write(A / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        ctx.item_def(name, f"block/{name}")
    for name in ("moon_regolith", "moon_rock", "mars_sand", "mars_rock", "io_rock"):
        ctx.loot_self(name)
    loot_drop(ctx, "helium_3_regolith", "helium_3", 1, 2)
    loot_drop(ctx, "hematite_ore", "raw_hematite")
    loot_drop(ctx, "ionite_ore", "ionite", 1, 2)
    loot_drop(ctx, "martian_ice", "minecraft:ice", fortune=False)
    for name in ITEMS:
        ctx.flat_item(name)
    D = ctx.DATA
    ctx.write(D / "minecraft" / "tags" / "block" / "mineable" / "shovel.json", {"replace": False, "values": [ns(b) for b in SHOVEL_BLOCKS]})


def recipes(ctx):
    TP = "#c:plates/titanium"
    ctx.shaped("helium_3_fuel_cell", [" T ", "HHH", " T "], {"T": TP, "H": "helium_3"}, "helium_3_fuel_cell")
    ctx.shaped("thermal_lining", ["GWG", "RHR", "GWG"], {"G": "minecraft:gold_ingot", "W": "minecraft:white_wool",
                                                        "R": "raw_hematite", "H": "helium_3"}, "thermal_lining")
    ctx.shaped("ion_drive", ["TIT", "ICI", "TMT"], {"T": TP, "I": "ionite", "C": "advanced_circuit", "M": "motor"}, "ion_drive")
    ctx.shaped("star_chart", [" G ", "GMG", " C "], {"G": "minecraft:glowstone_dust", "M": "minecraft:map", "C": "basic_circuit"},
               "star_chart")
    ctx.write(ctx.DATA / MOD / "recipe" / "iron_from_hematite.json", {
        "type": "minecraft:smelting", "category": "misc", "ingredient": ns("raw_hematite"),
        "result": {"id": "minecraft:iron_ingot", "count": 2}, "experience": 0.8, "cookingtime": 200})
    ctx.write(ctx.DATA / MOD / "recipe" / "iron_from_hematite_blasting.json", {
        "type": "minecraft:blasting", "category": "misc", "ingredient": ns("raw_hematite"),
        "result": {"id": "minecraft:iron_ingot", "count": 2}, "experience": 0.8, "cookingtime": 100})
    ctx.machine("crushing", "iron_dust_from_hematite", [("raw_hematite", 1)], "iron_dust", 4, time=60, min_grade=3)
    ctx.shapeless("moon_rock_to_gravel", ["moon_rock"], "minecraft:gravel")
    ctx.shapeless("ice_from_martian_ice", ["martian_ice"], "minecraft:ice")


def code_advancement(ctx, key, parent, icon, frame, en, es, hidden=False):
    display = {"icon": {"id": ns(icon)}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    if hidden:
        display["hidden"] = True
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}", "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": display, "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def advancements(ctx):
    code_advancement(ctx, "planet_moon", "space_orbit", "moon_regolith", "goal",
                     ("The Eagle Has Landed", "Land on the Moon"), ("El Águila ha aterrizado", "Aterriza en la Luna"))
    code_advancement(ctx, "planet_mars", "planet_moon", "mars_sand", "goal",
                     ("Red Planet", "Land on Mars"), ("El planeta rojo", "Aterriza en Marte"))
    code_advancement(ctx, "planet_io", "planet_mars", "ionite", "challenge",
                     ("Under Jupiter", "Land on Io, Jupiter's volcanic moon"),
                     ("Bajo Júpiter", "Aterriza en Ío, la luna volcánica de Júpiter"))
    ctx.advancement("planet_helium", "planet_moon", "helium_3", ["helium_3"], "Fusion Fuel",
                    "Dig Helium-3 out of the Moon's regolith", "Combustible de fusión",
                    "Extrae helio-3 del regolito lunar")


def lang(L):
    B, I, T, M, G = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"gui.{MOD}"
    for key, en, es in [
        ("moon_regolith", "Moon Regolith", "Regolito lunar"), ("moon_rock", "Moon Rock", "Roca lunar"),
        ("helium_3_regolith", "Helium-3 Regolith", "Regolito con helio-3"), ("mars_sand", "Martian Sand", "Arena marciana"),
        ("mars_rock", "Martian Rock", "Roca marciana"), ("hematite_ore", "Hematite Ore", "Mena de hematita"),
        ("martian_ice", "Martian Ice", "Hielo marciano"), ("io_rock", "Io Rock", "Roca de Ío"),
        ("ionite_ore", "Ionite Ore", "Mena de ionita"),
    ]:
        L(f"{B}.{key}", en, es)
    for key, en, es in [
        ("helium_3", "Helium-3", "Helio-3"), ("raw_hematite", "Raw Hematite", "Hematita en bruto"),
        ("ionite", "Ionite", "Ionita"), ("helium_3_fuel_cell", "Helium-3 Fuel Cell", "Pila de helio-3"),
        ("thermal_lining", "Thermal Lining", "Forro térmico"), ("ion_drive", "Ion Drive", "Propulsor iónico"),
        ("star_chart", "Star Chart", "Carta estelar"),
    ]:
        L(f"{I}.{key}", en, es)
    for key, en, es in [
        ("earth", "Earth", "la Tierra"), ("earth_orbit", "Earth orbit", "Órbita terrestre"), ("moon", "Moon", "Luna"),
        ("mars", "Mars", "Marte"), ("io", "Io", "Ío"), ("jupiter", "Jupiter", "Júpiter"),
    ]:
        L(f"planet.{MOD}.{key}", en, es)
    for key, en, es in [("moon", "the Moon", "la Luna"), ("mars", "Mars", "Marte"), ("io", "Io", "Ío")]:
        L(f"orbital.{MOD}.dimension.{MOD}.{key}", en, es)
    L(f"death.attack.{MOD}.heat", "%1$s was cooked on Io's surface", "%1$s se coció en la superficie de Ío")
    L(f"death.attack.{MOD}.heat.player", "%1$s was cooked on Io while fighting %2$s",
      "%1$s se coció en Ío mientras luchaba contra %2$s")
    for key, en, es in [
        ("helium_3_regolith", "Moon regolith rich in Helium-3. Found only on the Moon", "Regolito lunar rico en helio-3. Solo se encuentra en la Luna"),
        ("thermal_lining", "Keeps Io's heat out of an Astronaut Suit or Jet Suit", "Mantiene el calor de Ío fuera de un traje de astronauta o propulsor"),
        ("thermal_lining_use", "Use it while wearing the suit to sew it in", "Úsalo con el traje puesto para coserlo"),
        ("thermal_lining_on", "Thermal Lining: safe on Io", "Forro térmico: a salvo en Ío"),
        ("ion_drive", "In an Orbital Shuttle's hold: every trip costs half the fuel and time",
         "En la bodega de un transbordador orbital: cada viaje cuesta la mitad de combustible y tiempo"),
        ("star_chart", "Use it to see the planets and every station out there", "Úsala para ver los planetas y todas las estaciones"),
        ("helium_3_fuel_cell", "Shuttle fuel: worth 4 Rocket Fuel", "Combustible de transbordador: vale 4 combustibles de cohete"),
    ]:
        L(f"{T}.{key}", en, es)
    for key, en, es in [
        ("too_hot", "TOO HOT! A suit Thermal Lining keeps Io's heat out", "¡DEMASIADO CALOR! Un forro térmico en el traje frena el calor de Ío"),
        ("lining_no_suit", "Wear an Astronaut Suit or Jet Suit first", "Primero ponte un traje de astronauta o propulsor"),
        ("lining_already", "This suit already has a Thermal Lining", "Este traje ya tiene forro térmico"),
        ("lining_done", "Thermal Lining sewn into your suit", "Forro térmico cosido en tu traje"),
        ("nav.ok", "Engaged", "En marcha"),
        ("nav.not_in_space", "Navigation needs Earth orbit: climb out of the atmosphere first",
         "La navegación necesita estar en órbita terrestre: sal primero de la atmósfera"),
        ("nav.same_place", "You are already there: pick another destination", "Ya estás allí: elige otro destino"),
        ("nav.no_fuel", "Not enough fuel: the trip needs %s", "No hay combustible suficiente: el viaje necesita %s"),
        ("nav.busy", "Already cruising", "Ya estás en crucero"),
        ("nav.board_first", "Board the shuttle to fly it", "Sube al transbordador para pilotarlo"),
        ("nav.climb", "Climb above y %s to leave for %s", "Sube por encima de y %s para partir hacia %s"),
        ("nav.cruise", "Cruising to %s (%s s)...", "En crucero hacia %s (%s s)..."),
        ("nav.arrived", "Arrived: %s", "Llegada: %s"),
        ("nav.unavailable", "%s isn't reachable on this server", "%s no está disponible en este servidor"),
        ("nav.stranded", "Not enough fuel to leave: %s needed to reach Earth orbit",
         "No hay combustible para salir: hacen falta %s para llegar a la órbita terrestre"),
    ]:
        L(f"{M}.{key}", en, es)
    for key, en, es in [
        ("nav.title", "Navigation", "Navegación"), ("nav.engage", "Engage", "Iniciar viaje"),
        ("nav.here", "  (here)", "  (aquí)"), ("nav.at", "At: %s", "En: %s"),
        ("nav.atmosphere", "in the atmosphere", "en la atmósfera"),
        ("nav.cruising", "Cruising to %s: %s%%", "En crucero hacia %s: %s%%"),
        ("nav.not_in_space", "Reach Earth orbit to navigate", "Llega a la órbita para navegar"),
        ("nav.stranded", "Not enough fuel to leave", "No hay combustible para salir"),
        ("nav.climb", "Climb above y %s: %s", "Sube sobre y %s: %s"),
        ("nav.pick", "Pick a destination", "Elige un destino"),
        ("nav.need_fuel", "Needs %s fuel", "Necesita %s de combustible"),
        ("nav.ready", "Ready: %s fuel, %s s", "Listo: %s combustible, %s s"),
        ("nav.selected", "Course: %s", "Rumbo: %s"),
        ("ship.state.cruise", "Cruising", "En crucero"),
        ("ship.dest_leave", "space above y %s", "el espacio sobre y %s"),
        ("chart.stations", "Stations: %s", "Estaciones: %s"),
        ("chart.none", "No stations yet. Place a Station Core in orbit to found one.",
         "Aún no hay estaciones. Coloca un núcleo de estación en órbita para fundar una."),
        ("chart.where", "%s  %s, %s", "%s  %s, %s"),
    ]:
        L(f"{G}.{key}", en, es)
    L(f"sky.{MOD}.unidentified", "Unidentified %s", "%s no identificado")
    L(f"sky.{MOD}.satellite", "%s (%s)", "%s (%s)")


def generate(ctx):
    worlds(ctx)
    blocks(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx.lang)

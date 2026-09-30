"""Space stations (Java: space/station/, the Air Vent in space/):

  Station Hull        four colours (white, gray, dark, orange), each as a block, slab and stairs
  Station Window      clear glass in a steel frame (holds air)
  Airlock             a door that holds air while closed (factoryascent:airtight tag + AirVolume rules)
  Docking Port        a shuttle parked on it refuels from containers touching it
  Station Core        claims the station for a team; status screen (modules, power, air)
  Air Vent            a small Oxygen Sealer (a quarter of the volume and energy)
  Magnetic Boots      Astronaut Boots that hold you to the floor in low gravity
Models, blockstates, loot, tags, recipes, advancements, lang (English + Spanish).
"""

MOD = "factoryascent"
COLORS = {"white": ("White", "blanco", "minecraft:white_dye"), "gray": ("Gray", "gris", "minecraft:light_gray_dye"),
          "dark": ("Dark", "oscuro", "minecraft:gray_dye"), "orange": ("Orange", "naranja", "minecraft:orange_dye")}
HULLS = [f"station_hull_{c}{s}" for c in COLORS for s in ("", "_slab", "_stairs")]
PICKAXE_BLOCKS = HULLS + ["station_window", "airlock_door", "docking_port", "station_core", "air_vent"]

# vanilla oak_stairs blockstate pattern: (facing, half, shape) -> (model suffix, x, y); uvlock whenever rotated
STAIRS = {}
for f, base in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
    for half in ("bottom", "top"):
        for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
            model = "" if shape == "straight" else ("_inner" if shape.startswith("inner") else "_outer")
            y = base
            if half == "bottom" and shape.endswith("left"):
                y -= 90
            if half == "top" and shape.endswith("right"):
                y += 90
            STAIRS[(f, half, shape)] = (model, 180 if half == "top" else 0, y % 360)


def t(name):
    return f"{MOD}:block/{name}"


def blocks(ctx):
    A = ctx.ASSETS
    for c in COLORS:
        name = f"station_hull_{c}"
        tex = {"bottom": t(name), "top": t(name), "side": t(name)}
        ctx.block_model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": t(name)}})
        ctx.write(A / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        ctx.block_model(f"{name}_slab", {"parent": "minecraft:block/slab", "textures": tex})
        ctx.block_model(f"{name}_slab_top", {"parent": "minecraft:block/slab_top", "textures": tex})
        ctx.write(A / "blockstates" / f"{name}_slab.json", {"variants": {
            "type=bottom": {"model": f"{MOD}:block/{name}_slab"}, "type=top": {"model": f"{MOD}:block/{name}_slab_top"},
            "type=double": {"model": f"{MOD}:block/{name}"}}})
        for sfx, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            ctx.block_model(f"{name}_stairs{sfx}", {"parent": f"minecraft:block/{parent}", "textures": tex})
        variants = {}
        for (f, half, shape), (sfx, x, y) in sorted(STAIRS.items()):
            v = {"model": f"{MOD}:block/{name}_stairs{sfx}"}
            if x:
                v["x"] = x
            if y:
                v["y"] = y
            if x or y:
                v["uvlock"] = True
            variants[f"facing={f},half={half},shape={shape}"] = v
        ctx.write(A / "blockstates" / f"{name}_stairs.json", {"variants": variants})
        for item in (name, f"{name}_slab", f"{name}_stairs"):
            ctx.item_def(item, f"block/{item}")
        ctx.loot_self(name)
        ctx.loot_self(f"{name}_stairs")
        ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / f"{name}_slab.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}_slab",
                "functions": [{"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [
                    {"condition": "minecraft:block_state_property", "block": f"{MOD}:{name}_slab", "properties": {"type": "double"}}]},
                    {"function": "minecraft:explosion_decay"}]}]}],
            "random_sequence": f"{MOD}:blocks/{name}_slab"})

    # window: translucent glass in a frame
    ctx.block_model("station_window", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                       "textures": {"all": t("station_window")}})
    ctx.write(A / "blockstates" / "station_window.json", {"variants": {"": {"model": f"{MOD}:block/station_window"}}})
    ctx.item_def("station_window", "block/station_window")
    ctx.loot_self("station_window")

    # airlock door (vanilla door models and rotations)
    for half, hname in (("bottom", "lower"), ("top", "upper")):
        for hinge in ("left", "right"):
            for opened in ("", "_open"):
                ctx.block_model(f"airlock_door_{half}_{hinge}{opened}", {
                    "parent": f"minecraft:block/door_{half}_{hinge}{opened}", "render_type": "minecraft:cutout",
                    "textures": {"bottom": t("airlock_door_bottom"), "top": t("airlock_door_top")}})
    variants = {}
    for f, base in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half, hname in (("bottom", "lower"), ("top", "upper")):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    y = (base + ((90 if hinge == "left" else 270) if opened else 0)) % 360
                    v = {"model": f"{MOD}:block/airlock_door_{half}_{hinge}{'_open' if opened else ''}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={f},half={hname},hinge={hinge},open={str(opened).lower()}"] = v
    ctx.write(A / "blockstates" / "airlock_door.json", {"variants": variants})
    ctx.flat_item("airlock_door")
    ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / "airlock_door.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:airlock_door", "conditions": [
                {"condition": "minecraft:block_state_property", "block": f"{MOD}:airlock_door", "properties": {"half": "lower"}}]}]}],
        "random_sequence": f"{MOD}:blocks/airlock_door"})

    # docking port and station core
    for name, top, side, bottom in (("docking_port", "docking_port_top", "docking_port_side", "docking_port_side"),
                                    ("station_core", "station_core_top", "station_core_side", "station_core_top")):
        ctx.block_model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": t(top), "side": t(side), "bottom": t(bottom)}})
        ctx.write(A / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        ctx.item_def(name, f"block/{name}")
        ctx.loot_self(name)

    # air vent: like the oxygen sealer (facing, lit)
    for on in (False, True):
        sfx = "_on" if on else ""
        ctx.block_model(f"air_vent{sfx}", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": t(f"air_vent_top{sfx}"), "bottom": t("air_vent_side"), "side": t("air_vent_side")}})
    ctx.write(A / "blockstates" / "air_vent.json", {"variants": {
        f"facing={f},lit={str(on).lower()}": {"model": f"{MOD}:block/air_vent{'_on' if on else ''}"}
        for f in ("north", "east", "south", "west") for on in (False, True)}})
    ctx.item_def("air_vent", "block/air_vent")
    ctx.loot_self("air_vent")

    ctx.flat_item("magnetic_boots")

    ctx.write(ctx.DATA / MOD / "tags" / "block" / "airtight.json", {"replace": False, "values": [
        f"{MOD}:station_window"] + [f"{MOD}:{h}" for h in HULLS if not h.endswith(("slab", "stairs"))]})


def recipes(ctx):
    SP, AP, TP = "#c:plates/steel", "#c:plates/aluminum", "#c:plates/titanium"
    for c, (_, _, dye) in COLORS.items():
        name = f"station_hull_{c}"
        ctx.shaped(name, ["SAS", "ADA", "SAS"], {"S": SP, "A": AP, "D": dye}, name, count=8, category="building")
        ctx.shaped(f"{name}_slab", ["HHH"], {"H": name}, f"{name}_slab", count=6, category="building")
        ctx.shaped(f"{name}_stairs", ["H  ", "HH ", "HHH"], {"H": name}, f"{name}_stairs", count=4, category="building")
        ctx.shapeless(f"{name}_from_dye", ["#factoryascent:station_hulls", dye], name)
    ctx.write(ctx.DATA / MOD / "tags" / "item" / "station_hulls.json", {"replace": False, "values": [
        f"{MOD}:station_hull_{c}" for c in COLORS]})
    ctx.shaped("station_window", [" S ", "SGS", " S "], {"S": SP, "G": "minecraft:tinted_glass"}, "station_window", count=4,
               category="building")
    ctx.shaped("airlock_door", ["SS", "GC", "SS"], {"S": SP, "G": "minecraft:glass", "C": "basic_circuit"}, "airlock_door",
               category="redstone")
    ctx.shaped("docking_port", ["SHS", "HCH", "SHS"], {"S": SP, "H": "minecraft:hopper", "C": "advanced_circuit"}, "docking_port")
    ctx.shaped("station_core", ["TAT", "AOA", "TAT"], {"T": TP, "A": "advanced_circuit", "O": "orbital_targeting_core"}, "station_core")
    ctx.shaped("air_vent", ["SBS", "BMB", "SCS"], {"S": SP, "B": "minecraft:iron_bars", "M": "motor", "C": "basic_circuit"}, "air_vent")
    ctx.shaped("magnetic_boots", ["MBM", " R "], {"M": "item_magnet", "B": "astronaut_boots", "R": "minecraft:redstone_block"},
               "magnetic_boots", category="equipment")


def code_advancement(ctx, key, parent, icon, frame, en, es):
    display = {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
               "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame}
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}", "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": display, "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def advancements(ctx):
    code_advancement(ctx, "station_core", "space_orbit", "station_core", "goal",
                     ("Home Among the Stars", "Claim a station in space with a Station Core"),
                     ("Un hogar entre las estrellas", "Reclama una estación en el espacio con un núcleo de estación"))
    code_advancement(ctx, "space_sealed", "space_orbit", "oxygen_sealer", "goal",
                     ("Pressurised", "Seal a room in space and fill it with air"),
                     ("Presurizado", "Sella una sala en el espacio y llénala de aire"))
    ctx.advancement("station_boots", "space_suit", "magnetic_boots", ["magnetic_boots"], "Stick the Landing",
                    "Make Magnetic Boots to walk station decks in zero-g", "Aterrizaje pegado",
                    "Fabrica botas magnéticas para caminar por la estación en gravedad cero")


def lang(L):
    B, I, T, M, G = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"gui.{MOD}.station"
    for c, (en, es, _) in COLORS.items():
        L(f"{B}.station_hull_{c}", f"{en} Station Hull", f"Casco de estación {es}")
        L(f"{B}.station_hull_{c}_slab", f"{en} Station Hull Slab", f"Losa de casco de estación {es}")
        L(f"{B}.station_hull_{c}_stairs", f"{en} Station Hull Stairs", f"Escaleras de casco de estación {es}")
    for key, en, es in [("station_window", "Station Window", "Ventana de estación"), ("airlock_door", "Airlock", "Esclusa"),
                        ("docking_port", "Docking Port", "Puerto de atraque"), ("station_core", "Station Core", "Núcleo de estación"),
                        ("air_vent", "Air Vent", "Rejilla de aire")]:
        L(f"{B}.{key}", en, es)
    L(f"{I}.magnetic_boots", "Magnetic Boots", "Botas magnéticas")
    for key, en, es in [
        ("docking_port", "An Orbital Shuttle that sets down on it (in orbit: ease down onto it) is held in place",
         "Un transbordador orbital que se posa encima (en órbita: bájalo con suavidad) queda sujeto"),
        ("docking_port_fuel", "Docked shuttles refuel with Rocket Fuel from containers touching the port",
         "Los transbordadores atracados cargan combustible de cohete de los contenedores que tocan el puerto"),
        ("station_core", "Claims the station around it for your team. Right-click: modules, power and air",
         "Reclama la estación que lo rodea para tu equipo. Clic derecho: módulos, energía y aire"),
        ("station_core_claim", "Other teams can't break blocks inside the claim", "Otros equipos no pueden romper bloques dentro"),
        ("magnetic_boots", "Hold you to the floor in low gravity: walk station decks like on Earth",
         "Te sujetan al suelo en baja gravedad: camina por la estación como en la Tierra"),
        ("magnetic_boots_sneak", "Count as Astronaut Boots. Sneak to switch the magnets off",
         "Cuentan como botas de astronauta. Agáchate para apagar los imanes"),
    ]:
        L(f"{T}.{key}", en, es)
    for key, en, es in [
        ("station_claimed", "Station claimed: %s (blocks within %s are your team's)", "Estación reclamada: %s (los bloques a %s son de tu equipo)"),
        ("station_protected", "This block belongs to the station %s", "Este bloque pertenece a la estación %s"),
    ]:
        L(f"{M}.{key}", en, es)
    for key, en, es in [
        ("owner", "Team: %s", "Equipo: %s"), ("modules", "Modules", "Módulos"),
        ("hull", "Hull panels: %s   Windows: %s", "Paneles de casco: %s   Ventanas: %s"),
        ("airlocks", "Airlocks: %s   Docking ports: %s", "Esclusas: %s   Puertos de atraque: %s"),
        ("blocks", "Blocks in the station: %s", "Bloques en la estación: %s"),
        ("power", "Power", "Energía"), ("energy", "Stored: %s / %s FE in %s machines", "Almacenado: %s / %s FE en %s máquinas"),
        ("solar", "Solar panels: %s", "Paneles solares: %s"), ("air", "Life support", "Soporte vital"),
        ("no_sealer", "No Oxygen Sealer or Air Vent: no air aboard", "Sin sellador ni rejilla de aire: no hay aire a bordo"),
        ("sealers", "Sealers and vents: %s, sealed: %s, air: %s blocks", "Selladores y rejillas: %s, sellados: %s, aire: %s bloques"),
        ("leaking", "HULL BREACH: %s rooms leaking!", "BRECHA EN EL CASCO: ¡%s salas pierden aire!"),
        ("unsealed", "%s rooms open to space", "%s salas abiertas al espacio"),
        ("all_sealed", "All rooms sealed", "Todas las salas selladas"),
        ("air_here", "Air at the core: breathable", "Aire en el núcleo: respirable"),
        ("vacuum_here", "Air at the core: vacuum", "Aire en el núcleo: vacío"),
    ]:
        L(f"{G}.{key}", en, es)


def generate(ctx):
    blocks(ctx)
    recipes(ctx)
    advancements(ctx)
    lang(ctx.lang)

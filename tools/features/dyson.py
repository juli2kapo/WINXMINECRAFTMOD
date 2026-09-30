"""The Dyson Sphere (Quantum age capstone): Stellar Alloy, Solar Collectors, the Mass Driver (breech +
rails, coils and shots drawn by a renderer), the Dyson Receiver (3x3 rectenna, sun-tracking dish drawn
by a renderer), the Dyson Monitor (screen + hologram); recipes, advancements (milestones 10/25/50/100%)
and lang (English + Spanish). Textures: dyson_textures.py."""

MOD = "factoryascent"
PICKAXE_BLOCKS = ["mass_driver", "mass_driver_rail", "dyson_receiver", "dyson_receiver_array", "dyson_monitor"]
SIDES = ("north", "east", "south", "west")
ALL = SIDES + ("up", "down")


def t(name):
    return f"{MOD}:block/{name}"


def box(frm, to, tex, faces=ALL, uv=None, cull=None, rot=None):
    out = {}
    for f in faces:
        face = {"texture": tex[f] if isinstance(tex, dict) else tex}
        if uv:
            face["uv"] = uv[f] if isinstance(uv, dict) else uv
        if cull and f in cull:
            face["cullface"] = f
        out[f] = face
    el = {"from": frm, "to": to, "faces": out}
    if rot:
        el["rotation"] = rot
    return el


BLOCK_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


# ---------------------------------------------------------------- models

def rail_elements():
    """Four guide posts and two coil rings (2 px thick walls) around an open bore; the renderer
    lights the rings from outside (half-size 6.4 px) as the coils charge."""
    post = {"#": "#post"}
    els = []
    for x0, z0 in ((1, 1), (12, 1), (1, 12), (12, 12)):
        els.append(box([x0, 0, z0], [x0 + 3, 16, z0 + 3], "#post", uv={**{s: [x0, 0, x0 + 3, 16] for s in SIDES},
                                                                        "up": [x0, z0, x0 + 3, z0 + 3], "down": [x0, z0, x0 + 3, z0 + 3]},
                       cull=("up", "down")))
    for y0 in (4, 10):
        y1 = y0 + 2
        els += [
            box([2, y0, 2], [14, y1, 4], "#coil", uv=[0, y0, 12, y1]),
            box([2, y0, 12], [14, y1, 14], "#coil", uv=[0, y0, 12, y1]),
            box([2, y0, 4], [4, y1, 12], "#coil", uv=[0, y0, 8, y1]),
            box([12, y0, 4], [14, y1, 12], "#coil", uv=[0, y0, 8, y1]),
        ]
    return els


def receiver_elements():
    """The centre: a 10 px pedestal block with a gold emitter; the dish pivots at its top."""
    return [
        box([0, 0, 0], [16, 8, 16], {"up": "#top", "down": "#bottom", **{s: "#side" for s in SIDES}},
            uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 8, 16, 16] for s in SIDES}},
            cull=("down", "north", "east", "south", "west")),
        box([5, 8, 5], [11, 10, 11], {"up": "#top", "down": "#bottom", **{s: "#side" for s in SIDES}},
            uv={"up": [5, 5, 11, 11], "down": [5, 5, 11, 11], **{s: [5, 2, 11, 4] for s in SIDES}}),
    ]


def dish_elements():
    """The dish at 1/3 scale (the renderer scales it ×3 around its stem): a stepped gold bowl on a stem,
    three feed struts and a glowing focus. y = 0 is the pivot."""
    face = {"up": "#dish", "down": "#back", **{s: "#back" for s in SIDES}}
    els = [
        box([7, 0, 7], [9, 1.5, 9], "#back", uv=[6, 6, 8, 8]),
        box([4, 1.5, 4], [12, 2.2, 12], face, uv={"up": [4, 4, 12, 12], "down": [4, 4, 12, 12], **{s: [4, 0, 12, 1] for s in SIDES}}),
        # bowl steps rising towards the rim
        box([2, 2.2, 2], [14, 2.8, 4], face, uv={"up": [2, 2, 14, 4], "down": [2, 2, 14, 4], **{s: [2, 0, 14, 1] for s in SIDES}}),
        box([2, 2.2, 12], [14, 2.8, 14], face, uv={"up": [2, 12, 14, 14], "down": [2, 12, 14, 14], **{s: [2, 0, 14, 1] for s in SIDES}}),
        box([2, 2.2, 4], [4, 2.8, 12], face, uv={"up": [2, 4, 4, 12], "down": [2, 4, 4, 12], **{s: [4, 0, 12, 1] for s in SIDES}}),
        box([12, 2.2, 4], [14, 2.8, 12], face, uv={"up": [12, 4, 14, 12], "down": [12, 4, 14, 12], **{s: [4, 0, 12, 1] for s in SIDES}}),
        box([0, 2.8, 0], [16, 3.6, 2], face, uv={"up": [0, 0, 16, 2], "down": [0, 0, 16, 2], **{s: [0, 0, 16, 1] for s in SIDES}}),
        box([0, 2.8, 14], [16, 3.6, 16], face, uv={"up": [0, 14, 16, 16], "down": [0, 14, 16, 16], **{s: [0, 0, 16, 1] for s in SIDES}}),
        box([0, 2.8, 2], [2, 3.6, 14], face, uv={"up": [0, 2, 2, 14], "down": [0, 2, 2, 14], **{s: [2, 0, 14, 1] for s in SIDES}}),
        box([14, 2.8, 2], [16, 3.6, 14], face, uv={"up": [14, 2, 16, 14], "down": [14, 2, 16, 14], **{s: [2, 0, 14, 1] for s in SIDES}}),
        # feed struts and focus
        box([7.6, 3.6, 1], [8.4, 9, 1.8], "#back", uv=[7, 0, 8, 6], rot={"origin": [8, 3.6, 1.4], "axis": "x", "angle": 45}),
        box([7.6, 3.6, 14.2], [8.4, 9, 15], "#back", uv=[7, 0, 8, 6], rot={"origin": [8, 3.6, 14.6], "axis": "x", "angle": -45}),
        box([7.4, 2.2, 7.4], [8.6, 8.5, 8.6], "#back", uv=[7, 0, 8, 6]),
        box([6.5, 8.5, 6.5], [9.5, 10, 9.5], "#feed", uv=[0, 0, 3, 3]),
    ]
    return els


def monitor_elements():
    return [
        box([1, 0, 1], [15, 9, 15], {"north": "#front", "south": "#side", "east": "#side", "west": "#side",
                                     "up": "#top", "down": "#side"},
            uv={"north": [1, 3, 15, 12], **{s: [1, 7, 15, 16] for s in ("south", "east", "west")},
                "up": [1, 1, 15, 15], "down": [1, 1, 15, 15]}),
        box([5, 9, 5], [11, 10.5, 11], {"up": "#top", "down": "#side", **{s: "#side" for s in SIDES}},
            uv={"up": [5, 5, 11, 11], "down": [5, 5, 11, 11], **{s: [5, 0, 11, 1.5] for s in SIDES}}),
    ]


RAIL_TEX = {"post": t("mass_driver_rail"), "coil": t("mass_driver_coil"), "particle": t("mass_driver_rail")}
RECEIVER_TEX = {"top": t("dyson_receiver_top"), "side": t("dyson_receiver_side"), "bottom": t("mass_driver_bottom"),
                "particle": t("dyson_receiver_side")}
DISH_TEX = {"dish": t("dyson_dish"), "back": t("dyson_dish_back"), "feed": t("dyson_dish_feed"), "particle": t("dyson_dish")}
MONITOR_TEX = {"front": t("dyson_monitor_front"), "side": t("dyson_monitor_side"), "top": t("dyson_monitor_top"),
               "particle": t("dyson_monitor_side")}


def lang(L):
    B, I, T, M, G = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"gui.{MOD}.dyson"
    L(f"{B}.mass_driver", "Mass Driver", "Acelerador de masas")
    L(f"{B}.mass_driver_rail", "Mass Driver Rail", "Riel del acelerador de masas")
    L(f"{B}.dyson_receiver", "Dyson Receiver", "Receptor Dyson")
    L(f"{B}.dyson_receiver_array", "Receiver Array", "Matriz receptora")
    L(f"{B}.dyson_monitor", "Dyson Monitor", "Monitor Dyson")
    L(f"{I}.stellar_alloy_ingot", "Stellar Alloy Ingot", "Lingote de aleación estelar")
    L(f"{I}.dyson_collector", "Solar Collector", "Colector solar")

    L(f"{T}.dyson_collector", "One panel of your team's Dyson swarm: it orbits the sun and beams its light down as power",
      "Un panel del enjambre Dyson de tu equipo: orbita el sol y envía su luz a tierra como energía")
    L(f"{T}.dyson_collector_launch", "Fire it into solar orbit with a Mass Driver (or one per rocket from a Launch Pad)",
      "Dispáralo a la órbita solar con un acelerador de masas (o uno por cohete desde una plataforma de lanzamiento)")
    L(f"{T}.mass_driver", "Electromagnetic launcher: fires Solar Collectors straight up into solar orbit, for your team's Dyson swarm",
      "Lanzador electromagnético: dispara colectores solares hacia arriba, a la órbita solar, para el enjambre Dyson de tu equipo")
    L(f"{T}.mass_driver_multiblock", "Multiblock: stack %s Mass Driver Rails on top, with open sky above them",
      "Multibloque: apila %s rieles encima, con cielo abierto sobre ellos")
    L(f"{T}.mass_driver_energy", "%s FE per shot, at most one shot every %s s. Takes energy from cables on any side",
      "%s FE por disparo, como mucho un disparo cada %s s. Toma energía de cables por cualquier lado")
    L(f"{T}.mass_driver_use", "Right-click with Solar Collectors to load it (hoppers and pipes work too); sneak-use to unload",
      "Clic derecho con colectores solares para cargarlo (también tolvas y tuberías); agachado para descargarlo")
    L(f"{T}.mass_driver_rail", "Stack %s of these on a Mass Driver: their coils fling the collectors to the sun",
      "Apila %s de estos sobre un acelerador de masas: sus bobinas lanzan los colectores hacia el sol")
    L(f"{T}.dyson_receiver", "Turns your team's Dyson swarm into power: the more collectors around the sun, the more FE",
      "Convierte el enjambre Dyson de tu equipo en energía: cuantos más colectores alrededor del sol, más FE")
    L(f"{T}.dyson_receiver_power", "%s FE/t per collector in full sun (shared by your receivers), up to %s FE/t per receiver",
      "%s FE/t por colector a pleno sol (repartido entre tus receptores), hasta %s FE/t por receptor")
    L(f"{T}.dyson_receiver_multiblock", "Multiblock: surround it with 8 Receiver Arrays. Needs open sky and daylight (always on in space)",
      "Multibloque: rodéalo con 8 matrices receptoras. Necesita cielo abierto y luz de día (siempre activo en el espacio)")
    L(f"{T}.dyson_receiver_output", "Energy comes out of the bottom (and top) of the centre block",
      "La energía sale por abajo (y arriba) del bloque central")
    L(f"{T}.dyson_receiver_array", "Eight of these around a Dyson Receiver make its 3×3 rectenna",
      "Ocho de estas alrededor de un receptor Dyson forman su rectena de 3×3")
    L(f"{T}.dyson_monitor", "Shows your team's Dyson Sphere: a hologram on top, and progress, power and milestones on right-click",
      "Muestra la esfera Dyson de tu equipo: un holograma encima, y progreso, energía e hitos con clic derecho")

    L(f"{M}.mass_driver", "Mass Driver · %s/%s collectors · coils %s%% · %s",
      "Acelerador de masas · %s/%s colectores · bobinas %s%% · %s")
    L(f"{M}.mass_driver.ready", "firing", "disparando")
    L(f"{M}.mass_driver.charging", "charging", "cargando")
    L(f"{M}.mass_driver.incomplete", "needs %s rails stacked on top", "faltan %s rieles apilados encima")
    L(f"{M}.mass_driver.empty", "load Solar Collectors", "carga colectores solares")
    L(f"{M}.mass_driver.blocked", "the sky above the rails is blocked", "el cielo sobre los rieles está tapado")
    L(f"{M}.mass_driver.complete", "your Dyson Sphere is complete!", "¡tu esfera Dyson está completa!")
    L(f"{M}.mass_driver.unowned", "right-click it with collectors to claim it", "haz clic derecho con colectores para reclamarlo")
    L(f"{M}.dyson_receiver.incomplete", "Dyson Receiver: needs 8 Receiver Arrays around it",
      "Receptor Dyson: faltan 8 matrices receptoras alrededor")
    L(f"{M}.dyson_receiver.unowned", "Dyson Receiver: no owner", "Receptor Dyson: sin dueño")
    L(f"{M}.dyson_receiver.no_swarm", "Dyson Receiver: your team has no collectors around the sun yet",
      "Receptor Dyson: tu equipo aún no tiene colectores alrededor del sol")
    L(f"{M}.dyson_receiver.no_sun", "Dyson Receiver: %s collectors, but no sun here (night, or the sky is blocked)",
      "Receptor Dyson: %s colectores, pero aquí no hay sol (de noche, o el cielo está tapado)")
    L(f"{M}.dyson_receiver.status", "Dyson Receiver: %s FE/t from %s collectors · sun %s%%",
      "Receptor Dyson: %s FE/t de %s colectores · sol %s%%")
    L(f"{M}.dyson.first", "☀ Your first Solar Collector reached solar orbit! Look at the sun…",
      "☀ ¡Tu primer colector solar llegó a la órbita solar! Mira al sol…")
    L(f"{M}.dyson.milestone", "☀ Dyson Sphere %s%% complete (%s/%s collectors)",
      "☀ Esfera Dyson completa al %s%% (%s/%s colectores)")
    L(f"{M}.dyson.pad_arrived", "A Solar Collector reached solar orbit (%s/%s)", "Un colector solar llegó a la órbita solar (%s/%s)")
    L(f"{M}.dyson.full", "Your Dyson Sphere is already complete: the collector burns up in the sun",
      "Tu esfera Dyson ya está completa: el colector se quema en el sol")
    L(f"{M}.dyson.info", "Team %s: %s/%s Solar Collectors (%s%%), %s FE/t in full sun",
      "Equipo %s: %s/%s colectores solares (%s%%), %s FE/t a pleno sol")

    L(f"{G}.team", "Team %s", "Equipo %s")
    L(f"{G}.completion", "Completion", "Progreso")
    L(f"{G}.collectors", "Collectors", "Colectores")
    L(f"{G}.launched", "Launched", "Lanzados")
    L(f"{G}.potential", "Output, full sun", "Potencia a pleno sol")
    L(f"{G}.received", "Receivers take", "Recibido")
    L(f"{G}.milestones", "Milestones", "Hitos")
    L(f"{G}.milestone.first", "First collector in orbit", "Primer colector en órbita")
    L(f"{G}.milestone.10", "10%: a glinting ring", "10%: un anillo brillante")
    L(f"{G}.milestone.25", "25%: a swarm of rings", "25%: un enjambre de anillos")
    L(f"{G}.milestone.50", "50%: the lattice closes in", "50%: la estructura se cierra")
    L(f"{G}.milestone.100", "100%: Type II civilization", "100%: civilización de tipo II")
    L(f"{G}.hint_empty", "Build Solar Collectors and fire them at the sun with a Mass Driver (or a Launch Pad) to start your sphere.",
      "Fabrica colectores solares y dispáralos al sol con un acelerador de masas (o una plataforma de lanzamiento) para empezar tu esfera.")
    L(f"{G}.hint", "Every collector adds power to your Dyson Receivers. Look at the sun: your swarm grows there.",
      "Cada colector suma energía a tus receptores Dyson. Mira al sol: tu enjambre crece ahí.")
    L(f"{G}.hint_complete", "The sphere is closed. The whole star works for your factory.",
      "La esfera está cerrada. Toda la estrella trabaja para tu fábrica.")


def code_advancement(ctx, key, parent, icon, frame, en, es):
    """An advancement the mod grants from code (criterion 'done', see DysonService.award)."""
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}",
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
                    "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame},
        "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def generate(ctx):
    A = ctx.ASSETS
    # ---------------------------------------------------------------- mass driver
    ctx.block_model("mass_driver", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": t("mass_driver_top"), "side": t("mass_driver_side"), "bottom": t("mass_driver_bottom")}})
    ctx.write(A / "blockstates" / "mass_driver.json", {"variants": {"": {"model": f"{MOD}:block/mass_driver"}}})
    ctx.item_def("mass_driver", "block/mass_driver")
    ctx.block_model("mass_driver_rail", {"parent": "minecraft:block/block", "textures": RAIL_TEX, "elements": rail_elements()})
    ctx.write(A / "blockstates" / "mass_driver_rail.json", {"variants": {"": {"model": f"{MOD}:block/mass_driver_rail"}}})
    ctx.item_def("mass_driver_rail", "block/mass_driver_rail")

    # ---------------------------------------------------------------- receiver
    ctx.block_model("dyson_receiver", {"parent": "minecraft:block/block", "textures": RECEIVER_TEX, "elements": receiver_elements()})
    ctx.block_model("dyson_receiver_dish", {"parent": "minecraft:block/block", "ambientocclusion": False,
                                            "textures": DISH_TEX, "elements": dish_elements()})
    ctx.write(A / "blockstates" / "dyson_receiver.json", {"variants": {"": {"model": f"{MOD}:block/dyson_receiver"}}})
    # the item shows the pedestal with the dish (at 1/3 scale) on top
    dish_on_top = [dict(e, **{"from": [e["from"][0], e["from"][1] + 10, e["from"][2]], "to": [e["to"][0], e["to"][1] + 10, e["to"][2]]},
                        **({"rotation": dict(e["rotation"], origin=[e["rotation"]["origin"][0], e["rotation"]["origin"][1] + 10,
                                                                     e["rotation"]["origin"][2]])} if "rotation" in e else {}))
                   for e in dish_elements()]
    ctx.write(A / "models" / "item" / "dyson_receiver.json", {
        "parent": "minecraft:block/block", "textures": {**RECEIVER_TEX, **DISH_TEX},
        "elements": receiver_elements() + dish_on_top,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.55, 0.55, 0.55]}}})
    ctx.item_def("dyson_receiver", "item/dyson_receiver")
    ctx.block_model("dyson_receiver_array", {
        "parent": "minecraft:block/block",
        "textures": {"top": t("dyson_receiver_array_top"), "side": t("dyson_receiver_array_side"),
                     "bottom": t("mass_driver_bottom"), "particle": t("dyson_receiver_array_side")},
        "elements": [box([0, 0, 0], [16, 6, 16], {"up": "#top", "down": "#bottom", **{s: "#side" for s in SIDES}},
                         uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 10, 16, 16] for s in SIDES}},
                         cull=("down", "north", "east", "south", "west"))]})
    ctx.write(A / "blockstates" / "dyson_receiver_array.json", {"variants": {"": {"model": f"{MOD}:block/dyson_receiver_array"}}})
    model = {"parent": f"{MOD}:block/dyson_receiver_array", "display": BLOCK_DISPLAY}
    ctx.write(A / "models" / "item" / "dyson_receiver_array.json", model)
    ctx.item_def("dyson_receiver_array", "item/dyson_receiver_array")

    # ---------------------------------------------------------------- monitor
    ctx.block_model("dyson_monitor", {"parent": "minecraft:block/block", "textures": MONITOR_TEX, "elements": monitor_elements()})
    ctx.write(A / "blockstates" / "dyson_monitor.json", {"variants": {
        f"facing={f}": ({"model": f"{MOD}:block/dyson_monitor", "y": y} if y else {"model": f"{MOD}:block/dyson_monitor"})
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    ctx.item_def("dyson_monitor", "block/dyson_monitor")

    for item in ("stellar_alloy_ingot", "dyson_collector"):
        ctx.flat_item(item)
    for block in PICKAXE_BLOCKS:
        ctx.loot_self(block)
    ctx.write(ctx.DATA / "c" / "tags" / "item" / "ingots" / "stellar_alloy.json",
              {"replace": False, "values": [f"{MOD}:stellar_alloy_ingot"]})

    # ---------------------------------------------------------------- recipes (Quantum age)
    SA, Q, SC = "stellar_alloy_ingot", "#c:ingots/quantum_alloy", "superconductor_cable"
    # Plasma Forge (grade 7): quantum alloy fused with glowstone into a white-gold alloy that loves sunlight.
    ctx.machine("alloying", "stellar_alloy_ingot", [(Q, 1), ("minecraft:glowstone_dust", 4)], SA, count=2,
                time=160, min_grade=7)
    # Precision Assembler (grade 6): the Solar Collector, two at a time.
    ctx.machine("assembling", "dyson_collector", [(SA, 1), ("silicon_wafer", 4), ("advanced_circuit", 1),
                                                  ("#c:plates/aluminum", 2)], "dyson_collector", count=2, time=200, min_grade=6)
    ctx.shaped("mass_driver", ["SCS", "QFQ", "SES"], {"S": SA, "C": SC, "Q": Q, "F": "advanced_machine_frame",
                                                     "E": "quantum_energy_cell"}, "mass_driver")
    ctx.shaped("mass_driver_rail", ["QCQ", "S S", "QCQ"], {"Q": Q, "C": SC, "S": SA}, "mass_driver_rail", count=2)
    ctx.shaped("dyson_receiver", ["SDS", "AFA", "SES"], {"S": SA, "D": "ground_station", "A": "advanced_circuit",
                                                        "F": "advanced_machine_frame", "E": "quantum_energy_cell"}, "dyson_receiver")
    ctx.shaped("dyson_receiver_array", ["WWW", "SPS"], {"W": "silicon_wafer", "S": SA, "P": "#c:plates/titanium"},
               "dyson_receiver_array", count=4)
    ctx.shaped("dyson_monitor", ["TGT", "AOA", "TST"], {"T": "#c:plates/titanium", "G": "minecraft:tinted_glass",
                                                       "A": "advanced_circuit", "O": "orbital_targeting_core", "S": SA},
               "dyson_monitor")

    # ---------------------------------------------------------------- advancements (branch of age_quantum)
    ctx.advancement("dyson_stellar", "age_quantum", SA, [SA], "Starforged",
                    "Fuse Stellar Alloy in the Plasma Forge: the first step towards a Dyson Sphere",
                    "Forjado en las estrellas", "Funde aleación estelar en la forja de plasma: el primer paso hacia una esfera Dyson")
    ctx.advancement("dyson_mass_driver", "dyson_stellar", "mass_driver", ["mass_driver"], "Next Stop: the Sun",
                    "Build a Mass Driver", "Próxima parada: el sol", "Construye un acelerador de masas")
    code_advancement(ctx, "dyson_first", "dyson_stellar", "dyson_collector", "goal",
                     ("Here Comes the Sun", "Put your team's first Solar Collector into solar orbit"),
                     ("Aquí viene el sol", "Pon el primer colector solar de tu equipo en órbita solar"))
    code_advancement(ctx, "dyson_receiver", "dyson_first", "dyson_receiver", "task",
                     ("Sunbeam", "Power a Dyson Receiver with your team's swarm"),
                     ("Rayo de sol", "Alimenta un receptor Dyson con el enjambre de tu equipo"))
    code_advancement(ctx, "dyson_10", "dyson_first", "dyson_collector", "task",
                     ("Swarm Seeded", "Your Dyson Sphere is 10% complete: a ring of collectors glints around the sun"),
                     ("Enjambre sembrado", "Tu esfera Dyson está completa al 10%: un anillo de colectores brilla alrededor del sol"))
    code_advancement(ctx, "dyson_25", "dyson_10", "mass_driver_rail", "task",
                     ("Eclipse Season", "Your Dyson Sphere is 25% complete: rings of collectors cross the sun"),
                     ("Temporada de eclipses", "Tu esfera Dyson está completa al 25%: anillos de colectores cruzan el sol"))
    code_advancement(ctx, "dyson_50", "dyson_25", "dyson_monitor", "goal",
                     ("Half a Star", "Your Dyson Sphere is 50% complete: a lattice closes around the sun"),
                     ("Media estrella", "Tu esfera Dyson está completa al 50%: una estructura se cierra alrededor del sol"))
    code_advancement(ctx, "dyson_100", "dyson_50", "dyson_receiver", "challenge",
                     ("Type II Civilization", "Complete a Dyson Sphere: the whole light of the sun is yours"),
                     ("Civilización de tipo II", "Completa una esfera Dyson: toda la luz del sol es tuya"))

    lang(ctx.lang)

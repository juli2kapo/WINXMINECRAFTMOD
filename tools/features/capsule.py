"""Capsule machines and phone linking: the Size Chamber, the Mob Releaser (Automation age, after the
Mob Capsule), the Link Card and the Phone Dock (after the Factory Phone).

Block models (idle + active, four facings), item models, recipes, loot, lang (en + es) for these and
for the related changes (Wither capture, broken Recall Charm links, the phone's multi-beacon Recall
app and device links, approximate survey imaging), and advancements. Textures: capsule_textures.py.
Code: src/main/java/.../capsule/ and .../phone/dock/.
"""

MOD = "factoryascent"
PICKAXE_BLOCKS = ["size_chamber", "mob_releaser", "phone_dock"]
SIDES = ("north", "east", "south", "west")
ALL = SIDES + ("up", "down")


def t(name):
    return f"{MOD}:block/{name}"


def box(frm, to, tex, faces=ALL, uv=None, cull=None):
    out = {}
    for f in faces:
        face = {"texture": tex[f] if isinstance(tex, dict) else tex}
        if uv:
            face["uv"] = uv[f] if isinstance(uv, dict) else uv
        if cull and f in cull:
            face["cullface"] = f
        out[f] = face
    return {"from": frm, "to": to, "faces": out}


def side_uv(y0, y1):
    return {s: [0, 16 - y1, 16, 16 - y0] for s in SIDES}


# ---------------------------------------------------------------- Size Chamber

def size_chamber_model(active):
    """A steel plinth (control panel in front), a glass tube with corner rails, an emitter cap."""
    els = [box([0, 0, 0], [16, 5, 16], {"north": "#front", "east": "#base", "south": "#base", "west": "#base",
                                        "up": "#top", "down": "#top"},
               uv={**{s: [0, 0, 16, 16] for s in SIDES}, "up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}, cull=("down",)),
           box([3, 5, 3], [13, 13, 13], "#glass", faces=ALL, uv=[0, 0, 16, 16]),
           box([1, 13, 1], [15, 16, 15], {"up": "#top", "down": "#base", **{s: "#base" for s in SIDES}},
               uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 0, 16, 3] for s in SIDES}})]
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        els.append(box([x, 5, z], [x + 2, 13, z + 2], "#base", faces=SIDES, uv=[x, 3, x + 2, 11]))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"front": t("size_chamber_front"), "base": t("size_chamber_base"), "top": t("size_chamber_top"),
                         "glass": {"sprite": t("size_chamber_glass_active" if active else "size_chamber_glass"),
                                   "force_translucent": True},
                         "particle": t("size_chamber_base")},
            "elements": els}


# ---------------------------------------------------------------- Mob Releaser

def mob_releaser_model(active):
    """A squat launcher: a body 12 px tall with the tube grid on top and a muzzle lip in front."""
    top = "#top"
    els = [box([0, 0, 0], [16, 12, 16], {"north": "#front", "east": "#side", "south": "#side", "west": "#side",
                                         "up": top, "down": "#side"},
               uv={**{s: [0, 4, 16, 16] for s in SIDES}, "up": [0, 0, 16, 16], "down": [0, 0, 16, 16]},
               cull=("down", "north", "east", "south", "west")),
           # rim around the tube grid
           box([0, 12, 0], [16, 13, 1], "#side", faces=ALL, uv=[0, 0, 16, 1]),
           box([0, 12, 15], [16, 13, 16], "#side", faces=ALL, uv=[0, 0, 16, 1]),
           box([0, 12, 1], [1, 13, 15], "#side", faces=ALL, uv=[0, 0, 1, 14]),
           box([15, 12, 1], [16, 13, 15], "#side", faces=ALL, uv=[0, 0, 1, 14])]
    return {"parent": "minecraft:block/block",
            "textures": {"front": t("mob_releaser_front"), "side": t("mob_releaser_side"),
                         "top": t("mob_releaser_top_active" if active else "mob_releaser_top"),
                         "particle": t("mob_releaser_side")},
            "elements": els}


# ---------------------------------------------------------------- Phone Dock

def phone_dock_model(active):
    """A desk computer: a low desk with the card slot, a monitor at the back, the phone cradle in front."""
    els = [box([0, 0, 0], [16, 5, 16], {"up": "#top", "down": "#body", **{s: "#body" for s in SIDES}},
               uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 11, 16, 16] for s in SIDES}}, cull=("down",)),
           # monitor at the back (south edge in model space faces the player when FACING=north... the front is north)
           box([2, 5, 11], [14, 15, 13], {"north": "#screen", "south": "#body", "east": "#body", "west": "#body",
                                          "up": "#body", "down": "#body"},
               uv={"north": [1, 1, 15, 15], "south": [1, 1, 15, 11], "east": [0, 0, 2, 10], "west": [0, 0, 2, 10],
                   "up": [0, 0, 12, 2], "down": [0, 0, 12, 2]}),
           box([7, 5, 13], [9, 8, 14], "#body", faces=ALL, uv=[0, 0, 2, 3]),
           # cradle: a tilted pad
           box([4, 5, 2], [12, 6, 8], {"up": "#cradle", "north": "#cradle", "south": "#cradle", "east": "#cradle",
                                       "west": "#cradle", "down": "#cradle"},
               uv={"up": [2, 2, 14, 14], **{s: [0, 0, 8, 1] for s in SIDES}, "down": [0, 0, 8, 6]}),
           box([4, 6, 7], [12, 9, 8], "#cradle", faces=ALL, uv=[0, 0, 8, 3])]
    return {"parent": "minecraft:block/block",
            "textures": {"body": t("phone_dock_body"), "top": t("phone_dock_top"), "cradle": t("phone_dock_cradle"),
                         "screen": t("phone_dock_screen_on" if active else "phone_dock_screen"),
                         "particle": t("phone_dock_body")},
            "elements": els}


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_blockstate(ctx, name):
    variants = {}
    for f, y in ROT.items():
        for active in (False, True):
            v = {"model": f"{MOD}:block/{name}{'_active' if active else ''}"}
            if y:
                v["y"] = y
            variants[f"active={'true' if active else 'false'},facing={f}"] = v
    ctx.write(ctx.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


def generate(ctx):
    A = ctx.ASSETS
    for name, model in (("size_chamber", size_chamber_model), ("mob_releaser", mob_releaser_model),
                        ("phone_dock", phone_dock_model)):
        ctx.block_model(name, model(False))
        ctx.block_model(f"{name}_active", model(True))
        facing_blockstate(ctx, name)
        ctx.item_def(name, f"block/{name}_active" if name != "phone_dock" else f"block/{name}")
        ctx.loot_self(name)

    # Link Card: switches texture once it holds a link
    for name in ("link_card", "link_card_written"):
        ctx.write(A / "models" / "item" / f"{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
    ctx.write(A / "items" / "link_card.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": f"{MOD}:link_card",
        "on_true": {"type": "minecraft:model", "model": f"{MOD}:item/link_card_written"},
        "on_false": {"type": "minecraft:model", "model": f"{MOD}:item/link_card"}}})

    # ---------------------------------------------------------------- recipes
    ctx.shaped("size_chamber", ["GAG", "DMD", "CFC"], {
        "G": "minecraft:glass", "A": "#c:plates/aluminum", "D": "ender_dust", "M": "mob_capsule",
        "C": "advanced_circuit", "F": "machine_frame"}, "size_chamber")
    ctx.shaped("mob_releaser", ["ADA", "EFE", "ARA"], {
        "A": "#c:plates/aluminum", "D": "minecraft:dispenser", "E": "ender_dust", "F": "machine_frame",
        "R": "minecraft:redstone"}, "mob_releaser")
    ctx.shaped("phone_dock", ["GCG", "PFP", "PPP"], {
        "G": "minecraft:glass_pane", "C": "basic_circuit", "P": "#c:plates/aluminum", "F": "machine_frame"}, "phone_dock")
    ctx.shapeless("link_card", ["minecraft:paper", "minecraft:paper", "minecraft:redstone", "basic_circuit"],
                  "link_card", count=8)

    # ---------------------------------------------------------------- advancements
    ctx.advancement("automation_size_chamber", "automation_mob_capsule", "size_chamber", ["size_chamber"],
                    "Bottled Growth", "Build a Size Chamber: resize and heal the mob inside a Mob Capsule",
                    "Crecimiento embotellado", "Construye una cámara de tamaño: cambia el tamaño y cura a la criatura de una cápsula")
    ctx.advancement("automation_mob_releaser", "automation_mob_capsule", "mob_releaser", ["mob_releaser"],
                    "Release the Hounds", "Build a Mob Releaser: let nine captured mobs out at once with a redstone pulse",
                    "Suelten a los perros", "Construye un liberador de criaturas: suelta nueve criaturas capturadas a la vez con un pulso de redstone")
    code_advancement(ctx, "capsule_wither", "automation_mob_capsule", "nether_star", "challenge",
                     ("Pocket Apocalypse", "Capture a Wither in a Mob Capsule (beat it below 10% health first)"),
                     ("Apocalipsis de bolsillo", "Captura un Wither en una cápsula (bájale la vida por debajo del 10% antes)"))
    ctx.advancement("automation_phone_dock", "automation_phone", "phone_dock", ["phone_dock", "link_card"],
                    "Docking Station", "Make Link Cards or a Phone Dock: record blocks on cards and load them into your phone",
                    "Estación de acople", "Fabrica tarjetas de enlace o una base de teléfono: graba bloques en tarjetas y cárgalos en el teléfono")

    lang(ctx.lang)


def code_advancement(ctx, key, parent, icon, frame, en, es):
    """An advancement the mod grants from code (criterion 'done')."""
    icon_id = icon if ":" in icon else (f"minecraft:{icon}" if icon == "nether_star" else f"{MOD}:{icon}")
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}",
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {"icon": {"id": icon_id}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
                    "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame},
        "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def lang(L):
    B, I, T, G, M = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"gui.{MOD}", f"message.{MOD}"
    # ---- Size Chamber
    L(f"{B}.size_chamber", "Size Chamber", "Cámara de tamaño")
    L(f"{T}.size_chamber", "Resizes the mob in a filled Mob Capsule, then heals it, with FE",
      "Cambia el tamaño de la criatura de una cápsula llena y luego la cura, con FE")
    L(f"{T}.size_chamber.howto", "Set the target size on its screen; the mob keeps size and health when released",
      "Elige el tamaño en su pantalla; la criatura conserva tamaño y vida al soltarla")
    L(f"{G}.size_chamber.empty", "Insert a filled Mob Capsule", "Pon una cápsula llena")
    L(f"{G}.size_chamber.target", "Target size: %s", "Tamaño objetivo: %s")
    L(f"{G}.size_chamber.limits", "Limits: %s – %s", "Límites: %s – %s")
    L(f"{G}.size_chamber.size", "Size: %s", "Tamaño: %s")
    L(f"{G}.size_chamber.size_to", "Size: %s → %s", "Tamaño: %s → %s")
    L(f"{G}.size_chamber.health", "Health %s / %s", "Vida %s / %s")
    for i, (en, es) in enumerate((("Empty", "Vacía"), ("Resizing…", "Cambiando tamaño…"), ("Healing…", "Curando…"),
                                  ("Done: full health", "Lista: vida completa"), ("Not enough energy", "Energía insuficiente"))):
        L(f"{G}.size_chamber.status.{i}", en, es)
    L(f"{G}.size_chamber.status.1_pct", "Resizing… %s%%", "Cambiando tamaño… %s%%")
    for k, en, es in (("half", "Halve the target size", "Reduce el tamaño objetivo a la mitad"),
                      ("less", "Target size ×0.8", "Tamaño objetivo ×0,8"),
                      ("reset", "Back to normal size", "Volver al tamaño normal"),
                      ("more", "Target size ×1.25", "Tamaño objetivo ×1,25"),
                      ("double", "Double the target size", "Duplica el tamaño objetivo")):
        L(f"{G}.size_chamber.btn.{k}", en, es)
    # ---- Mob Releaser
    L(f"{B}.mob_releaser", "Mob Releaser", "Liberador de criaturas")
    L(f"{T}.mob_releaser", "Lets every captured mob in its 9 capsule slots out at once",
      "Suelta a la vez todas las criaturas capturadas de sus 9 ranuras de cápsula")
    L(f"{T}.mob_releaser.howto", "Redstone pulse or Release All; set the release point on its screen",
      "Pulso de redstone o Liberar todo; elige el punto de salida en su pantalla")
    L(f"{G}.mob_releaser.loaded", "Loaded: %s / %s", "Cargadas: %s / %s")
    L(f"{G}.mob_releaser.redstone", "Fires on a pulse", "Con un pulso")
    L(f"{G}.mob_releaser.last", "Last: %s released", "Última: %s")
    L(f"{G}.mob_releaser.ahead", "Ahead: %s", "Delante: %s")
    L(f"{G}.mob_releaser.up", "Up: %s", "Arriba: %s")
    L(f"{G}.mob_releaser.release", "Release All", "Liberar todo")
    L(f"{G}.mob_releaser.release_tip", "Let every captured mob out now", "Suelta ahora todas las criaturas capturadas")
    L(f"{G}.mob_releaser.nearer", "Release point one block nearer", "Punto de salida un bloque más cerca")
    L(f"{G}.mob_releaser.farther", "Release point one block farther", "Punto de salida un bloque más lejos")
    L(f"{G}.mob_releaser.lower", "Release point one block lower", "Punto de salida un bloque más abajo")
    L(f"{G}.mob_releaser.higher", "Release point one block higher", "Punto de salida un bloque más arriba")
    # ---- Mob Capsule additions
    L(f"{M}.mob_capsule.refused.wither_health", "The Wither is too strong: weaken it below %s%% health first",
      "El Wither es demasiado fuerte: bájale la vida por debajo del %s%% primero")
    L(f"{T}.mob_capsule.size", "Size: %s%%", "Tamaño: %s%%")
    # ---- Link Card / Phone Dock
    L(f"{I}.link_card", "Link Card", "Tarjeta de enlace")
    L(f"{I}.link_card.written", "Link Card (%s)", "Tarjeta de enlace (%s)")
    L(f"{T}.link_card.blank", "Blank: sneak-use it on a machine, cable, tank, terminal, Ender Beacon, Dyson Receiver…",
      "En blanco: agáchate y úsala sobre una máquina, cable, tanque, terminal, baliza de ender, receptor Dyson…")
    for k, en, es in ((0, "Storage terminal (Storage app)", "Terminal de almacenamiento (app Almacén)"),
                      (1, "Device (Machines app)", "Dispositivo (app Máquinas)"),
                      (2, "Energy network (Power app)", "Red de energía (app Energía)"),
                      (3, "Ender Beacon (Recall app)", "Baliza de ender (app Regreso)")):
        L(f"{T}.link_card.kind.{k}", en, es)
    L(f"{T}.link_card.at", "At %s, %s, %s (%s)", "En %s, %s, %s (%s)")
    L(f"{T}.link_card.dock", "Insert it into a Phone Dock with your phone to add the link",
      "Ponla en una base de teléfono con tu teléfono para añadir el enlace")
    L(f"{T}.link_card.howto", "Sneak-use: record a block (again to overwrite). Sneak-use in the air: wipe",
      "Agáchate y usa: graba un bloque (otra vez para sobrescribir). En el aire: borrar")
    L(f"{M}.link_card.recorded", "Recorded %s at %s, %s, %s", "Grabado: %s en %s, %s, %s")
    L(f"{M}.link_card.wiped", "Link Card wiped", "Tarjeta de enlace borrada")
    L(f"{M}.link_card.not_linkable", "The phone can't watch this block", "El teléfono no puede vigilar este bloque")
    L(f"{B}.phone_dock", "Phone Dock", "Base de teléfono")
    L(f"{T}.phone_dock", "A desk computer for the Factory Phone: loads Link Cards into it, manages its links and charges it",
      "Un ordenador de escritorio para el teléfono: carga tarjetas de enlace, gestiona sus enlaces y lo carga")
    L(f"{T}.phone_dock.howto", "Phone in the cradle, written cards in the reader: blank cards come out below",
      "Teléfono en la base, tarjetas grabadas en el lector: salen en blanco abajo")
    L(f"{G}.phone_dock.no_phone", "Put your phone in the cradle", "Pon tu teléfono en la base")
    L(f"{G}.phone_dock.how1", "1. Sneak-use a card on a block", "1. Usa una tarjeta agachado")
    L(f"{G}.phone_dock.how2", "2. Card into the reader", "2. Tarjeta al lector")
    L(f"{G}.phone_dock.how3", "3. Link added to phone", "3. Enlace al teléfono")
    L(f"{G}.phone_dock.no_links", "No links yet: insert written Link Cards", "Aún sin enlaces: pon tarjetas grabadas")
    L(f"{G}.phone_dock.remove", "Remove this link from the phone", "Quitar este enlace del teléfono")
    for k, en, es in ((1, "Link added to the phone", "Enlace añadido al teléfono"),
                      (2, "Put a phone in the cradle first", "Primero pon un teléfono en la base"),
                      (3, "Phone limit reached for that kind of link", "Límite del teléfono para ese tipo de enlace"),
                      (4, "Card tray full: take the blank cards out", "Bandeja llena: saca las tarjetas en blanco"),
                      (5, "That energy network is already linked", "Esa red de energía ya está enlazada")):
        L(f"{G}.phone_dock.result.{k}", en, es)
    L(f"{G}.phone_dock.slot.phone", "Factory Phone", "Teléfono de fábrica")
    L(f"{G}.phone_dock.slot.card_in", "Card reader: written Link Cards", "Lector: tarjetas de enlace grabadas")
    L(f"{G}.phone_dock.slot.card_out", "Blank cards come out here", "Aquí salen las tarjetas en blanco")
    # ---- phone: device links, beacons, Dyson receivers
    L(f"{M}.phone.linked_beacon", "Linked %s: the Recall app can take you there",
      "%s enlazada: la app Regreso puede llevarte allí")
    L(f"{M}.phone.too_many_beacons", "This phone already knows %s Ender Beacons", "Este teléfono ya conoce %s balizas de ender")
    L(f"{T}.phone.beacons", "Ender Beacons: %s", "Balizas de ender: %s")
    L(f"{G}.phone.device.online", "Online", "En línea")
    L(f"{G}.phone.device.fluid", "%s mB stored", "%s mB guardados")
    L(f"{G}.phone.dyson.receivers", "Receivers on: %s/%s", "Receptores: %s/%s")
    L(f"{G}.phone.dyson.receivers_out", "Output %s FE/t", "Salida %s FE/t")
    L(f"{G}.phone.dyson.no_receivers", "Link Dyson Receivers with a Link Card to watch them here",
      "Enlaza receptores Dyson con una tarjeta para verlos aquí")
    L(f"{G}.phone.recall.none", "No Ender Beacons linked", "No hay balizas de ender enlazadas")
    L(f"{G}.phone.recall.how_link", "Sneak-use a Link Card on your Ender Beacon and insert it in a Phone Dock with this "
      "phone (or sneak-use the phone on the beacon). A Recall Charm you carry also shows up here. No charm needed.",
      "Agáchate y usa una tarjeta de enlace en tu baliza de ender y ponla en una base de teléfono con este teléfono "
      "(o agáchate y usa el teléfono en la baliza). Un amuleto de regreso que lleves también aparece aquí. No hace falta amuleto.")
    L(f"{G}.phone.recall.gone_hint_charm", "Destroyed: rebuild it there or link your Recall Charm to another beacon",
      "Destruida: reconstrúyela allí o enlaza tu amuleto a otra baliza")
    L(f"{G}.phone.recall.from_charm", "from your Recall Charm", "de tu amuleto de regreso")
    L(f"{G}.phone.recall.gone_hint", "Destroyed: rebuild it here or unlink it in a Phone Dock",
      "Destruida: reconstrúyela aquí o quita el enlace en una base de teléfono")
    # ---- Recall Charm
    L(f"{M}.charm_beacon_destroyed", "Your %s at %s, %s, %s (%s) was destroyed", "Tu %s en %s, %s, %s (%s) fue destruida")
    L(f"{M}.charm_no_pearl_at", "%s has no ender pearl: load one first", "%s no tiene perla de ender: carga una primero")
    L(f"{T}.charm_broken", "Beacon destroyed: rebuild it there or link another", "Baliza destruida: reconstrúyela o enlaza otra")

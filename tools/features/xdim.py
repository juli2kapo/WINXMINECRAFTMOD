"""Interdimensional links: the Ender Link (Automation age) and the Quantum Entangler (Quantum age).
Block models (two per block: idle and transferring, the second with an animated swirl/field), recipes,
advancements (Portal Plumbing and friends), lang (English + Spanish). Textures: xdim_textures.py.
Code: src/main/java/.../xdim/."""

MOD = "factoryascent"
PICKAXE_BLOCKS = ["ender_link", "quantum_entangler"]
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


# ---------------------------------------------------------------- Ender Link

def ender_elements():
    """An obsidian plinth, four purpur pillars, an end-stone cap ring with the swirl in its middle.
    The core (a glowing cube) floats between the pillars, drawn by the block entity renderer."""
    els = [box([1, 0, 1], [15, 3, 15], {"up": "#base_top", "down": "#base_top", **{s: "#base" for s in SIDES}},
               uv={"up": [1, 1, 15, 15], "down": [1, 1, 15, 15], **{s: [1, 6, 15, 9] for s in SIDES}}, cull=("down",))]
    for x, z in ((1, 1), (12, 1), (1, 12), (12, 12)):
        els.append(box([x, 3, z], [x + 3, 13, z + 3], "#pillar", faces=SIDES, uv=[x, 3, x + 3, 13]))
    # the cap ring
    for frm, to in (([1, 13, 1], [15, 16, 4]), ([1, 13, 12], [15, 16, 15]), ([1, 13, 4], [4, 16, 12]), ([12, 13, 4], [15, 16, 12])):
        els.append(box(frm, to, "#cap"))
    # the swirl, seen from above and from below, plus a glowing seal on the plinth
    els.append(box([4, 14.5, 4], [12, 14.5, 12], "#swirl", faces=("up", "down"), uv=[4, 4, 12, 12]))
    els.append(box([4, 3.02, 4], [12, 3.02, 12], "#swirl", faces=("up",), uv=[4, 4, 12, 12]))
    return els


def ender_model(active):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"base": t("ender_link_base"), "base_top": t("ender_link_base_top"), "pillar": t("ender_link_pillar"),
                         "cap": t("ender_link_cap"), "swirl": t("ender_link_swirl_active" if active else "ender_link_swirl"),
                         "particle": t("ender_link_pillar")},
            "elements": ender_elements()}


# ---------------------------------------------------------------- Quantum Entangler

def quantum_elements():
    """A gunmetal cage (twelve edges) on a base plate, an emitter plate on top, and four
    translucent interference-field panes; the double core floats inside."""
    els = [box([0, 0, 0], [16, 2, 16], {"up": "#base", "down": "#base", **{s: "#frame" for s in SIDES}},
               uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 7, 16, 9] for s in SIDES}}, cull=ALL),
           box([0, 14, 0], [16, 16, 16], {"up": "#top", "down": "#base", **{s: "#frame" for s in SIDES}},
               uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 7, 16, 9] for s in SIDES}},
               cull=("up", "north", "east", "south", "west"))]
    # punch the emitter's view hole: instead of a full top-underside, the field pane below it glows
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        els.append(box([x, 2, z], [x + 2, 14, z + 2], "#frame", faces=SIDES, uv=[7, 2, 9, 14]))
    f = "#field"
    els += [
        box([2, 2, 0.6], [14, 14, 0.6], f, faces=("north", "south"), uv=[2, 2, 14, 14]),
        box([2, 2, 15.4], [14, 14, 15.4], f, faces=("north", "south"), uv=[2, 2, 14, 14]),
        box([0.6, 2, 2], [0.6, 14, 14], f, faces=("east", "west"), uv=[2, 2, 14, 14]),
        box([15.4, 2, 2], [15.4, 14, 14], f, faces=("east", "west"), uv=[2, 2, 14, 14]),
    ]
    return els


def quantum_model(active):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"base": t("quantum_entangler_base"), "top": t("quantum_entangler_top"), "frame": t("quantum_entangler_frame"),
                         "field": {"sprite": t("quantum_entangler_field_active" if active else "quantum_entangler_field"),
                                   "force_translucent": True},
                         "particle": t("quantum_entangler_frame")},
            "elements": quantum_elements()}


def code_advancement(ctx, key, parent, icon, frame, en, es):
    """An advancement the mod grants from code (criterion 'done', see XdimService.reward)."""
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
    for name, model in (("ender_link", ender_model), ("quantum_entangler", quantum_model)):
        ctx.block_model(name, model(False))
        ctx.block_model(f"{name}_active", model(True))
        ctx.write(A / "blockstates" / f"{name}.json", {"variants": {
            "active=false": {"model": f"{MOD}:block/{name}"},
            "active=true": {"model": f"{MOD}:block/{name}_active"}}})
        ctx.item_def(name, f"block/{name}_active")
        ctx.loot_self(name)

    # ---------------------------------------------------------------- recipes
    # Automation age: obsidian and pearls around an advanced circuit on a machine frame; a pair at a time.
    ctx.shaped("ender_link", ["OPO", "DCD", "OFO"], {
        "O": "minecraft:obsidian", "P": "minecraft:ender_pearl", "D": "ender_dust", "C": "advanced_circuit",
        "F": "machine_frame"}, "ender_link", count=2)
    # Quantum age: an Ender Link entangled with quantum alloy, a quantum circuit (Io's ionite) and superconductors.
    ctx.shaped("quantum_entangler", ["TCT", "SLS", "TQT"], {
        "T": "#c:plates/titanium", "C": "quantum_circuit", "S": "superconductor_cable", "L": "ender_link",
        "Q": "#c:ingots/quantum_alloy"}, "quantum_entangler")

    # ---------------------------------------------------------------- advancements
    ctx.advancement("xdim_ender_link", "age_automation", "ender_link", ["ender_link"], "Ender Logistics",
                    "Craft a pair of Ender Links: tune both to one channel and what goes in here comes out there",
                    "Logística de ender",
                    "Fabrica un par de enlaces de ender: sintonízalos en un canal y lo que entra aquí sale allí")
    code_advancement(ctx, "xdim_first_link", "xdim_ender_link", "ender_link", "task",
                     ("Across the Void", "Move something between two dimensions through linked endpoints"),
                     ("A través del vacío", "Mueve algo entre dos dimensiones con extremos enlazados"))
    code_advancement(ctx, "xdim_portal_plumbing", "xdim_first_link", "ender_link", "goal",
                     ("Portal Plumbing", "Pipe lava from the Nether straight into the Overworld through an Ender Link"),
                     ("Fontanería de portales", "Lleva lava del Nether directamente al Supramundo con un enlace de ender"))
    ctx.advancement("xdim_quantum", "age_quantum", "quantum_entangler", ["quantum_entangler"], "Spooky Action at a Distance",
                    "Build a Quantum Entangler: items, fluids and energy between any two worlds",
                    "Acción fantasmal a distancia",
                    "Construye un entrelazador cuántico: objetos, fluidos y energía entre dos mundos cualesquiera")
    code_advancement(ctx, "xdim_space_freight", "xdim_quantum", "quantum_entangler", "challenge",
                     ("Interplanetary Supply Line", "Send something to or from orbit or another planet through a Quantum Entangler"),
                     ("Línea de suministro interplanetaria", "Envía algo hacia o desde la órbita u otro planeta con un entrelazador cuántico"))

    lang(ctx.lang)


def lang(L):
    B, T, M, G, J = f"block.{MOD}", f"tooltip.{MOD}", f"message.{MOD}.xdim", f"gui.{MOD}.xdim", f"jei.{MOD}.xdim"
    L(f"{B}.ender_link", "Ender Link", "Enlace de ender")
    L(f"{B}.quantum_entangler", "Quantum Entangler", "Entrelazador cuántico")
    L(f"{T}.ender_link", "Moves items and fluids to every other endpoint on its channel",
      "Mueve objetos y fluidos a los demás extremos de su canal")
    L(f"{T}.ender_link.rates", "Up to %s items/s and %s mB/s per link", "Hasta %s objetos/s y %s mB/s por enlace")
    L(f"{T}.ender_link.reach", "Reach: inside one dimension, or Overworld ↔ Nether. Free to run",
      "Alcance: dentro de una dimensión, o Supramundo ↔ Nether. Funciona gratis")
    L(f"{T}.quantum_entangler", "Moves items, fluids and energy to every other endpoint on its channel",
      "Mueve objetos, fluidos y energía a los demás extremos de su canal")
    L(f"{T}.quantum_entangler.rates", "Up to %s items/s, %s mB/s and %s FE/t per entangler",
      "Hasta %s objetos/s, %s mB/s y %s FE/t por entrelazador")
    L(f"{T}.quantum_entangler.reach", "Reach: any two dimensions, orbit and planets included. Costs FE (more between worlds)",
      "Alcance: dos dimensiones cualesquiera, órbita y planetas incluidos. Cuesta FE (más entre mundos)")
    L(f"{T}.xdim_howto", "Right-click: channel and faces. Wrench a face: off / send / receive. Keeps its own chunk loaded while tuned",
      "Clic derecho: canal y caras. Llave en una cara: apagada / enviar / recibir. Mantiene su chunk cargado mientras está sintonizado")
    L(f"{M}.not_yours", "This link belongs to another team", "Este enlace pertenece a otro equipo")
    L(f"{M}.face", "%s face: %s", "Cara %s: %s")

    for i, (en, es) in enumerate((("Off", "Apagada"), ("Send", "Enviar"), ("Receive", "Recibir"))):
        L(f"{G}.mode.{i}", en, es)
    for i, (en, es) in enumerate((("Off", "No"), ("Send", "Env."), ("Recv", "Rec."))):
        L(f"{G}.mode_short.{i}", en, es)
    what = {"item": ("items", "objetos"), "fluid": ("fluids", "fluidos"), "energy": ("energy", "energía")}
    for r, (en, es) in what.items():
        L(f"{G}.mode_tip.0.{r}", f"This face ignores {en}" + (" (energy fed here runs the entangler)" if r == "energy" else ""),
          f"Esta cara ignora {es}" + (" (la energía que entra aquí alimenta el entrelazador)" if r == "energy" else ""))
        L(f"{G}.mode_tip.1.{r}", f"Takes {en} in here and sends them down the channel",
          f"Recibe {es} por aquí y los envía por el canal")
        L(f"{G}.mode_tip.2.{r}", f"Puts out the {en} arriving from the channel", f"Saca por aquí {es} que llegan del canal")
    L(f"{G}.click_cycle", "Left-click: next mode, right-click: previous", "Clic izquierdo: siguiente modo, derecho: anterior")
    L(f"{G}.no_energy", "Ender Links carry no energy: build a Quantum Entangler",
      "Los enlaces de ender no llevan energía: construye un entrelazador cuántico")
    L(f"{G}.col.items", "Items", "Objetos")
    L(f"{G}.col.fluids", "Fluids", "Fluidos")
    L(f"{G}.col.energy", "FE", "FE")
    for d, en, es in (("up", "Top", "Arriba"), ("down", "Bottom", "Abajo"), ("north", "North", "Norte"),
                      ("south", "South", "Sur"), ("west", "West", "Oeste"), ("east", "East", "Este")):
        L(f"{G}.face.{d}", en, es)
    for i, (en, es) in enumerate((("Idle", "En espera"), ("Transferring", "Transfiriendo"), ("No channel", "Sin canal"),
                                  ("No partner", "Sin pareja"), ("No power", "Sin energía"), ("Channel denied", "Canal denegado"))):
        L(f"{G}.status.{i}", en, es)
    L(f"{G}.power", "Power", "Energía")
    L(f"{G}.power_value", "%s FE · spent %s FE/s", "%s FE · gasta %s FE/s")
    L(f"{G}.ender_note", "Items + fluids, no FE", "Objetos + fluidos, sin FE")
    L(f"{G}.ender_note2", "Same dim. or Nether", "Misma dim. o Nether")
    L(f"{G}.no_channel", "Not tuned: pick or create a channel", "Sin sintonizar: elige o crea un canal")
    L(f"{G}.tuned", "Channel: %s", "Canal: %s")
    L(f"{G}.no_channels", "No channels yet.", "Aún no hay canales.")
    L(f"{G}.no_channels2", "Type a name below, then +", "Escribe un nombre y pulsa +")
    L(f"{G}.name", "Channel name", "Nombre del canal")
    L(f"{G}.name_hint", "New channel…", "Nuevo canal…")
    for k, en, es in (("create", "+", "+"), ("public_short", "Public", "Público"), ("private_short", "Team", "Equipo"),
                      ("rename", "Rename", "Nombrar"), ("toggle", "Open up", "Abrir"), ("make_private", "Private", "Privado"),
                      ("unlink", "Unlink", "Soltar")):
        L(f"{G}.btn.{k}", en, es)
    L(f"{G}.tip.create", "Create a channel with the typed name and tune to it", "Crea un canal con el nombre escrito y sintonízalo")
    L(f"{G}.tip.public_short", "New channels will be public: anyone can tune to them", "Los canales nuevos serán públicos: cualquiera puede sintonizarlos")
    L(f"{G}.tip.private_short", "New channels will be private to your team", "Los canales nuevos serán privados de tu equipo")
    L(f"{G}.tip.rename", "Rename the tuned channel to the typed name", "Renombra el canal sintonizado con el nombre escrito")
    L(f"{G}.tip.toggle", "Switch the tuned channel between team-only and public", "Cambia el canal sintonizado entre solo equipo y público")
    L(f"{G}.tip.unlink", "Untune this endpoint (right-click: delete your channel)", "Desintoniza este extremo (clic derecho: borra tu canal)")
    L(f"{G}.throughput", "Items %s↑ %s↓/s · Fluid %s↑ %s↓ mB/s · FE %s↑ %s↓/t", "Objetos %s↑ %s↓/s · Fluido %s↑ %s↓ mB/s · FE %s↑ %s↓/t")
    L(f"{G}.anchor_none", "Not tuned: its chunk loads only while someone is near", "Sin sintonizar: su chunk solo carga si hay alguien cerca")
    L(f"{G}.anchor_on", "Keeps its own chunk loaded: works while you are far away", "Mantiene su chunk cargado: funciona aunque estés lejos")
    L(f"{G}.anchor_limit", "Team chunk-loading limit reached: works only while loaded", "Límite de carga de chunks del equipo alcanzado: solo funciona si está cargado")
    L(f"{G}.no_endpoints", "No endpoints on this channel", "No hay extremos en este canal")
    L(f"{G}.unloaded", "unloaded", "descargado")
    L(f"{G}.more", "…and %s more", "…y %s más")
    L(f"{G}.team", "· %s", "· %s")
    L(f"{G}.dim.overworld", "Overworld", "Supramundo")
    L(f"{G}.dim.nether", "Nether", "Nether")
    L(f"{G}.dim.end", "The End", "El End")

    L(f"{J}.ender_link", "Ender Link (Automation age): endpoints tuned to the same channel share items and fluids, inside one dimension or between the Overworld and the Nether. Free to run.",
      "Enlace de ender (Era de la Automatización): los extremos sintonizados en el mismo canal comparten objetos y fluidos, dentro de una dimensión o entre el Supramundo y el Nether. Funciona gratis.")
    L(f"{J}.quantum_entangler", "Quantum Entangler (Quantum age): items, fluids and energy between any two dimensions, orbit and planets included, much faster. Every transfer costs FE from its operating buffer (fed through faces whose energy mode is off), more between worlds.",
      "Entrelazador cuántico (Era Cuántica): objetos, fluidos y energía entre dos dimensiones cualesquiera, órbita y planetas incluidos, mucho más rápido. Cada transferencia cuesta FE de su reserva (alimentada por caras con la energía apagada), más entre mundos.")
    L(f"{J}.howto", "Right-click to pick or create a channel (private to your team, or public) and set each face to send or receive items, fluids or energy; a Wrench cycles a face. Pipes and cables connect normally. A tuned endpoint keeps its own chunk loaded.",
      "Clic derecho para elegir o crear un canal (privado de tu equipo o público) y configurar cada cara para enviar o recibir objetos, fluidos o energía; la llave cambia una cara. Tuberías y cables conectan normalmente. Un extremo sintonizado mantiene su chunk cargado.")
    L(f"{J}.lava", "Nether lava: a Pump on a lava lake → fluid pipe → Ender Link (send). At home: Ender Link (receive) → fluid pipe → Magmatic Generator or tank.",
      "Lava del Nether: una bomba sobre un lago de lava → tubería de fluidos → enlace de ender (enviar). En casa: enlace de ender (recibir) → tubería → generador magmático o tanque.")
    L(f"{J}.cost", "Costs: FE per item and per bucket times the distance factor (1 in one dimension, 2 between two, +2 for each end in orbit or on a planet); energy loses a small share on the way.",
      "Costes: FE por objeto y por cubo por el factor de distancia (1 en una dimensión, 2 entre dos, +2 por cada extremo en órbita o en un planeta); la energía pierde una pequeña parte por el camino.")

"""Ender tech resources: Ender Anchor (chunk loader), Ender Beacon + Recall Charm, Ender Dust."""
from pathlib import Path

MOD = "factoryascent"
TEXTURES = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"
PICKAXE_BLOCKS = ["ender_anchor", "ender_beacon"]


def tex(name, fallback):
    return f"{MOD}:{name}" if (TEXTURES / f"{name}.png").exists() else fallback


def translucent(sprite):
    return {"sprite": sprite, "force_translucent": True}


def box(frm, to, texture, faces=("north", "east", "south", "west", "up", "down"), uv=None, cull=False):
    out = {}
    for f in faces:
        face = {"texture": texture}
        if uv:
            face["uv"] = uv
        if cull:
            face["cullface"] = f
        out[f] = face
    return {"from": frm, "to": to, "faces": out}


SIDES = ("north", "east", "south", "west")
INSET = 0.02  # water sits just inside the glass so the two never z-fight


def anchor_elements(half, dy=0):
    """One half of the stasis chamber. Obsidian corner pillars run the full two blocks; glass
    panes fill the sides between them; soul sand floor (lower half); a crying-obsidian rim
    around a glass lid (upper half); water inside. dy shifts the half up (for the item model)."""
    def up(v):
        return [v[0], v[1] + dy, v[2]]
    els = []
    # corner pillars
    for x, z in [(0, 0), (14, 0), (0, 14), (14, 14)]:
        faces = SIDES + (("down",) if half == "lower" else ("up",))
        els.append(box(up([x, 0, z]), up([x + 2, 16, z + 2]), "#frame", faces=faces))
    if half == "lower":
        els.append(box(up([0.01, 0, 0.01]), up([15.99, 4, 15.99]), "#base", cull=False))
        # obsidian sill along the bottom edge of the glass
        for frm, to in [([2, 4, 0], [14, 5, 1]), ([2, 4, 15], [14, 5, 16]), ([0, 4, 2], [1, 5, 14]), ([15, 4, 2], [16, 5, 14])]:
            els.append(box(up(frm), up(to), "#frame"))
        glass_y0, water_y1 = 5, 16
        els.append(box(up([1, 5, 1]), up([15, 16, 15]), "#water", faces=SIDES))
    else:
        # crying-obsidian rim and glass lid
        for frm, to in [([2, 14, 0], [14, 16, 2]), ([2, 14, 14], [14, 16, 16]), ([0, 14, 2], [2, 16, 14]), ([14, 14, 2], [16, 16, 14])]:
            els.append(box(up(frm), up(to), "#rim"))
        els.append(box(up([2, 15.5, 2]), up([14, 15.5, 14]), "#glass", faces=("up", "down"), uv=[2, 2, 14, 14]))
        glass_y0 = 0
        els.append(box(up([1, 0, 1]), up([15, 13, 15]), "#water", faces=SIDES + ("up",)))
    top = 14 if half == "upper" else 16
    # glass panes between the pillars, set just inside the frame line
    # The glass texture has a frame drawn on its border; sample only its clear middle so the two
    # halves read as one continuous pane.
    clear = [2, 2, 14, 14]
    els += [
        box(up([2, glass_y0, 0.5]), up([14, top, 0.5]), "#glass", faces=("north", "south"), uv=clear),
        box(up([2, glass_y0, 15.5]), up([14, top, 15.5]), "#glass", faces=("north", "south"), uv=clear),
        box(up([0.5, glass_y0, 2]), up([0.5, top, 14]), "#glass", faces=("east", "west"), uv=clear),
        box(up([15.5, glass_y0, 2]), up([15.5, top, 14]), "#glass", faces=("east", "west"), uv=clear),
    ]
    return els


ANCHOR_TEXTURES = {
    "base": "minecraft:block/soul_sand",
    "frame": "minecraft:block/obsidian",
    "rim": "minecraft:block/crying_obsidian",
    "glass": translucent("minecraft:block/glass"),
    "water": translucent(tex("block/ender_anchor_water", "minecraft:block/blue_stained_glass")),
    "particle": "minecraft:block/obsidian",
}


def anchor_model(half):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": ANCHOR_TEXTURES, "elements": anchor_elements(half)}


def anchor_item_model():
    """Both halves stacked and shrunk, so the inventory icon shows the whole chamber."""
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": ANCHOR_TEXTURES,
            "elements": anchor_elements("lower") + anchor_elements("upper", dy=16),
            "display": {
                "gui": {"rotation": [30, 225, 0], "translation": [0, -3.5, 0], "scale": [0.36, 0.36, 0.36]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [0.3, 0.3, 0.3]},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1], "scale": [0.2, 0.2, 0.2]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
            }}


def beacon_model():
    """A one-block stasis chamber under a trapdoor lid: obsidian corner posts, glass sides, soul
    sand floor, water, and an oak trapdoor on top (vanilla stasis chambers are closed with one;
    its holes show the water under it). The pearl is drawn by the block entity renderer."""
    clear = [2, 2, 14, 14]
    els = [box([0.01, 0, 0.01], [15.99, 4, 15.99], "#base")]
    for x, z in [(0, 0), (14, 0), (0, 14), (14, 14)]:
        els.append(box([x, 0, z], [x + 2, 13, z + 2], "#frame", faces=SIDES + ("down",)))
    els += [
        box([2, 4, 0.5], [14, 13, 0.5], "#glass", faces=("north", "south"), uv=clear),
        box([2, 4, 15.5], [14, 13, 15.5], "#glass", faces=("north", "south"), uv=clear),
        box([0.5, 4, 2], [0.5, 13, 14], "#glass", faces=("east", "west"), uv=clear),
        box([15.5, 4, 2], [15.5, 13, 14], "#glass", faces=("east", "west"), uv=clear),
        box([1, 4, 1], [15, 12.5, 15], "#water", faces=SIDES + ("up",)),
        # the trapdoor lid
        {"from": [0, 13, 0], "to": [16, 16, 16], "faces": {
            "up": {"texture": "#lid", "cullface": "up"}, "down": {"texture": "#lid"},
            **{f: {"texture": "#lid", "uv": [0, 0, 16, 3]} for f in SIDES}}},
    ]
    textures = dict(ANCHOR_TEXTURES)
    textures["lid"] = "minecraft:block/oak_trapdoor"
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": textures, "elements": els}


def generate(ctx):
    A = ctx.ASSETS
    ctx.block_model("ender_anchor_lower", anchor_model("lower"))
    ctx.block_model("ender_anchor_upper", anchor_model("upper"))
    ctx.write(A / "models" / "item" / "ender_anchor.json", anchor_item_model())
    ctx.write(A / "blockstates" / "ender_anchor.json", {"variants": {
        f"enabled={on},half={half}": {"model": f"{MOD}:block/ender_anchor_{half}"}
        for on in ("false", "true") for half in ("lower", "upper")}})
    ctx.item_def("ender_anchor", "item/ender_anchor")

    ctx.block_model("ender_beacon", beacon_model())
    ctx.write(A / "blockstates" / "ender_beacon.json", {"variants": {
        "enabled=false": {"model": f"{MOD}:block/ender_beacon"},
        "enabled=true": {"model": f"{MOD}:block/ender_beacon"}}})
    ctx.item_def("ender_beacon", "block/ender_beacon")

    # Bubbles inside the chamber use the vanilla bubble sprite (see StasisBubbleParticle).
    ctx.write(A / "particles" / "stasis_bubble.json", {"textures": ["minecraft:bubble"]})
    ctx.flat_item("ender_dust")
    ctx.flat_item("recall_charm")
    # Only the lower half drops the item (like a door); breaking the top takes the bottom with it.
    ctx.write(ctx.DATA / MOD / "loot_table" / "blocks" / "ender_anchor.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:ender_anchor"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"},
                                  {"condition": "minecraft:block_state_property", "block": f"{MOD}:ender_anchor",
                                   "properties": {"half": "lower"}}]}],
        "random_sequence": f"{MOD}:blocks/ender_anchor"})
    ctx.loot_self("ender_beacon")

    # ---------------------------------------------------------------- recipes
    # A stasis chamber: obsidian corners, glass sides, water in the middle, soul sand at the bottom. The bucket comes back.
    ctx.shaped("ender_anchor", ["OGO", "GWG", "OSO"], {
        "O": "minecraft:obsidian", "G": "minecraft:glass", "S": "minecraft:soul_sand",
        "W": "minecraft:water_bucket"}, "ender_anchor")
    ctx.machine("crushing", "ender_dust", [("minecraft:ender_pearl", 1)], "ender_dust", 2, time=60, min_grade=3)
    # A stasis chamber closed with a trapdoor, like the vanilla build it is based on.
    ctx.shaped("ender_beacon", ["OTO", "GWG", "OSO"], {
        "O": "minecraft:obsidian", "T": "#minecraft:wooden_trapdoors", "G": "minecraft:glass",
        "W": "minecraft:water_bucket", "S": "minecraft:soul_sand"}, "ender_beacon")
    # The remote trigger: gold, ender dust and a pearl of its own.
    ctx.shaped("recall_charm", [" G ", "DPD", " G "], {
        "G": "#c:ingots/gold", "D": "ender_dust", "P": "minecraft:ender_pearl"}, "recall_charm", category="equipment")

    # ---------------------------------------------------------------- advancements
    ctx.advancement("electric_anchor", "age_electric", "ender_anchor", ["ender_anchor"], "Always Loaded",
                    "Build an Ender Anchor (a stasis chamber of glass, water and soul sand) and drop a pearl in",
                    "Siempre cargado",
                    "Construye un ancla de ender (una cámara de estasis de vidrio, agua y arena de almas) y echa una perla")
    ctx.advancement("electric_recall", "electric_anchor", "recall_charm", ["recall_charm"],
                    "There's No Place Like Home",
                    "Load an Ender Beacon with a pearl, link a Recall Charm to it, and channel your way home",
                    "No hay lugar como el hogar",
                    "Carga una baliza de ender con una perla, vincula un amuleto de retorno y canaliza el viaje a casa")

    # ---------------------------------------------------------------- lang
    L = ctx.lang
    L(f"block.{MOD}.ender_anchor", "Ender Anchor", "Ancla de ender")
    L(f"block.{MOD}.ender_beacon", "Ender Beacon", "Baliza de ender")
    L(f"item.{MOD}.ender_dust", "Ender Dust", "Polvo de ender")
    L(f"item.{MOD}.recall_charm", "Recall Charm", "Amuleto de retorno")
    L(f"itemGroup.{MOD}.utility", "Factory Ascent: Utility", "Factory Ascent: Utilidades")
    T = f"tooltip.{MOD}"
    L(f"{T}.ender_anchor", "A stasis chamber: keeps a %s×%s area of chunks loaded",
      "Una cámara de estasis: mantiene cargada un área de %s×%s chunks")
    L(f"{T}.ender_anchor_pearl", "Right-click with an ender pearl (or put one in its slot) to start it; right-click to open it. Breaking it loses the pearl",
      "Clic derecho con una perla de ender (o ponla en su ranura) para activarla; clic derecho para abrirla. Al romperla se pierde la perla")
    L(f"{T}.two_tall", "Two blocks tall", "Ocupa dos bloques de alto")
    L(f"{T}.ender_beacon", "A stasis chamber your Recall Charm can trigger from anywhere. Sneak-use a charm on it to link",
      "Una cámara de estasis que tu amuleto de retorno puede activar desde cualquier lugar. Agáchate y usa un amuleto sobre ella")
    L(f"{T}.ender_beacon_pearl", "Right-click with an ender pearl to load it; right-click to open it and name it. Each recall uses the pearl up",
      "Clic derecho con una perla de ender para cargarla; clic derecho para abrirla y ponerle nombre. Cada retorno gasta la perla")
    L(f"{T}.charm_unlinked", "Not linked: sneak-use it on an Ender Beacon",
      "Sin vincular: agáchate y úsalo sobre una baliza de ender")
    L(f"{T}.charm_linked", "Home: %s, %s, %s (%s)", "Hogar: %s, %s, %s (%s)")
    L(f"{T}.charm_howto", "Hold use for %s s to recall home. Taking damage breaks the channel",
      "Mantén pulsado %s s para volver a casa. Recibir daño interrumpe el canal")
    M = f"message.{MOD}"
    L(f"{M}.anchor_limit", "You already have %s Ender Anchors", "Ya tienes %s anclas de ender")
    L(f"{M}.anchor_active", "Keeping %s×%s chunks loaded", "Manteniendo %s×%s chunks cargados")
    L(f"{M}.anchor_empty", "Empty: drop an ender pearl in to start it",
      "Vacía: echa una perla de ender para activarla")
    L(f"{M}.beacon_loaded", "A pearl is waiting: ready for a recall", "Hay una perla esperando: lista para un retorno")
    L(f"{M}.beacon_empty", "Empty: load an ender pearl to be able to recall here",
      "Vacía: carga una perla de ender para poder volver aquí")
    L(f"{M}.beacon_not_yours", "This Ender Beacon belongs to someone else",
      "Esta baliza de ender pertenece a otra persona")
    L(f"{M}.charm_linked", "Recall Charm linked to this Ender Beacon", "Amuleto vinculado a esta baliza de ender")
    L(f"{M}.charm_unlinked", "Link the charm first: sneak-use it on an Ender Beacon",
      "Primero vincula el amuleto: agáchate y úsalo sobre una baliza de ender")
    L(f"{M}.charm_other_dimension", "Your Ender Beacon is in another dimension",
      "Tu baliza de ender está en otra dimensión")
    L(f"{M}.charm_channel", "Recalling… %s", "Retornando… %s")
    L(f"{M}.charm_interrupted", "Recall interrupted!", "¡Retorno interrumpido!")
    L(f"{M}.charm_beacon_gone", "Your Ender Beacon is gone", "Tu baliza de ender ya no existe")
    L(f"{M}.charm_blocked", "Something is blocking the space above your Ender Beacon",
      "Algo bloquea el espacio sobre tu baliza de ender")
    L(f"{M}.charm_no_pearl", "Your Ender Beacon has no pearl loaded",
      "Tu baliza de ender no tiene ninguna perla cargada")

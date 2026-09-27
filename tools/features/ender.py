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


def anchor_model():
    """Soul sand base, obsidian-purple corner posts and rim, glass tank, ender water inside.
    The eye of ender is drawn by the block entity renderer so it can float and turn."""
    frame = "#frame"
    elements = [box([0, 0, 0], [16, 3, 16], "#base")]
    for x, z in [(0, 0), (14, 0), (0, 14), (14, 14)]:
        elements.append(box([x, 3, z], [x + 2, 16, z + 2], frame))
    elements += [
        box([2, 14, 0], [14, 16, 2], frame), box([2, 14, 14], [14, 16, 16], frame),
        box([0, 14, 2], [2, 16, 14], frame), box([14, 14, 2], [16, 16, 14], frame),
        # glass walls between the posts
        box([2, 3, 0.5], [14, 14, 0.5], "#glass", faces=("north", "south")),
        box([2, 3, 15.5], [14, 14, 15.5], "#glass", faces=("north", "south")),
        box([0.5, 3, 2], [0.5, 14, 14], "#glass", faces=("east", "west")),
        box([15.5, 3, 2], [15.5, 14, 14], "#glass", faces=("east", "west")),
        box([2, 15.5, 2], [14, 15.5, 14], "#glass", faces=("up", "down")),
        # the ender water: fills the lower part; the eye floats above it when the anchor is awake
        box([1, 3, 1], [15, 9, 15], "#fluid", faces=("north", "east", "south", "west", "up")),
    ]
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "base": "minecraft:block/soul_sand",
            "frame": tex("block/ender_anchor_frame", "minecraft:block/crying_obsidian"),
            "glass": translucent("minecraft:block/glass"),
            "fluid": translucent(tex("block/ender_anchor_fluid", "minecraft:block/purple_stained_glass")),
            "particle": "minecraft:block/glass",
        },
        "elements": elements,
    }


def beacon_model(on):
    side = tex("block/ender_beacon_side", "minecraft:block/obsidian")
    top = tex("block/ender_beacon_top_on" if on else "block/ender_beacon_top", "minecraft:block/crying_obsidian")
    return {"parent": "minecraft:block/cube_bottom_top",
            "textures": {"side": side, "top": top, "bottom": "minecraft:block/obsidian"}}


def generate(ctx):
    A = ctx.ASSETS
    ctx.block_model("ender_anchor", anchor_model())
    ctx.write(A / "blockstates" / "ender_anchor.json", {"variants": {
        "enabled=false": {"model": f"{MOD}:block/ender_anchor"},
        "enabled=true": {"model": f"{MOD}:block/ender_anchor"}}})
    ctx.item_def("ender_anchor", "block/ender_anchor")

    ctx.block_model("ender_beacon", beacon_model(False))
    ctx.block_model("ender_beacon_on", beacon_model(True))
    ctx.write(A / "blockstates" / "ender_beacon.json", {"variants": {
        "powered=false": {"model": f"{MOD}:block/ender_beacon"},
        "powered=true": {"model": f"{MOD}:block/ender_beacon_on"}}})
    ctx.item_def("ender_beacon", "block/ender_beacon")

    ctx.flat_item("ender_dust")
    ctx.flat_item("recall_charm")
    ctx.loot_self("ender_anchor")
    ctx.loot_self("ender_beacon")

    # ---------------------------------------------------------------- recipes
    ctx.shaped("ender_anchor", ["GGG", "GEG", "SWS"], {
        "G": "minecraft:glass", "E": "minecraft:ender_eye", "S": "minecraft:soul_sand",
        "W": "minecraft:water_bucket"}, "ender_anchor")
    ctx.machine("crushing", "ender_dust", [("minecraft:ender_pearl", 1)], "ender_dust", 2, time=60, min_grade=3)
    ctx.shaped("ender_beacon", ["OEO", "DFD", "OOO"], {
        "O": "minecraft:obsidian", "E": "minecraft:ender_eye", "D": "ender_dust",
        "F": "advanced_machine_frame"}, "ender_beacon")
    ctx.shaped("recall_charm", [" G ", "DED", " C "], {
        "G": "#c:ingots/gold", "D": "ender_dust", "E": "minecraft:ender_eye",
        "C": "advanced_circuit"}, "recall_charm", category="equipment")

    # ---------------------------------------------------------------- advancements
    ctx.advancement("electric_anchor", "age_electric", "ender_anchor", ["ender_anchor"], "Always Loaded",
                    "Build an Ender Anchor from glass, water, an eye of ender and soul sand, and feed it pearls",
                    "Siempre cargado",
                    "Construye un ancla de ender con vidrio, agua, un ojo de ender y arena de almas, y dale perlas")
    ctx.advancement("automation_recall", "age_automation", "recall_charm", ["recall_charm"],
                    "There's No Place Like Home",
                    "Craft a Recall Charm, link it to a charged Ender Beacon, and channel your way home",
                    "No hay lugar como el hogar",
                    "Fabrica un amuleto de retorno, vincúlalo a una baliza de ender cargada y canaliza el viaje a casa")

    # ---------------------------------------------------------------- lang
    L = ctx.lang
    L(f"block.{MOD}.ender_anchor", "Ender Anchor", "Ancla de ender")
    L(f"block.{MOD}.ender_beacon", "Ender Beacon", "Baliza de ender")
    L(f"item.{MOD}.ender_dust", "Ender Dust", "Polvo de ender")
    L(f"item.{MOD}.recall_charm", "Recall Charm", "Amuleto de retorno")
    L(f"itemGroup.{MOD}.utility", "Factory Ascent: Utility", "Factory Ascent: Utilidades")
    T = f"tooltip.{MOD}"
    L(f"{T}.ender_anchor", "Keeps a %s×%s area of chunks loaded while it has ender pearls",
      "Mantiene cargada un área de %s×%s chunks mientras tenga perlas de ender")
    L(f"{T}.ender_anchor_fuel", "Right-click with ender pearls (or pipe them in): %s min per pearl",
      "Clic derecho con perlas de ender (o por tubería): %s min por perla")
    L(f"{T}.ender_beacon", "Home point for Recall Charms. Sneak-use a charm on it to link",
      "Punto de retorno para amuletos. Agáchate y usa un amuleto sobre ella para vincularlo")
    L(f"{T}.ender_beacon_cost", "Charge it with FE: %s FE per recall",
      "Cárgala con FE: %s FE por retorno")
    L(f"{T}.charm_unlinked", "Not linked: sneak-use it on an Ender Beacon",
      "Sin vincular: agáchate y úsalo sobre una baliza de ender")
    L(f"{T}.charm_linked", "Home: %s, %s, %s (%s)", "Hogar: %s, %s, %s (%s)")
    L(f"{T}.charm_howto", "Hold use for %s s to recall home. Taking damage breaks the channel",
      "Mantén pulsado %s s para volver a casa. Recibir daño interrumpe el canal")
    M = f"message.{MOD}"
    L(f"{M}.anchor_limit", "You already have %s Ender Anchors", "Ya tienes %s anclas de ender")
    L(f"{M}.anchor_free", "Keeping %s×%s chunks loaded", "Manteniendo %s×%s chunks cargados")
    L(f"{M}.anchor_asleep", "Asleep: give it ender pearls to load chunks",
      "Dormida: dale perlas de ender para cargar chunks")
    L(f"{M}.anchor_status", "Keeping %s×%s chunks loaded · %sh %smin left (%s pearls stored)",
      "Manteniendo %s×%s chunks cargados · quedan %sh %smin (%s perlas guardadas)")
    L(f"{M}.beacon_status", "%s / %s FE · enough for %s recalls", "%s / %s FE · alcanza para %s retornos")
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
    L(f"{M}.charm_no_energy", "Your Ender Beacon doesn't have enough energy",
      "Tu baliza de ender no tiene energía suficiente")

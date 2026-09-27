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
    """One half of the stasis chamber: glass all round, soul sand floor (lower), water column,
    open-water surface near the top (upper). dy shifts the half up (for the item model)."""
    def up(v):
        return [v[0], v[1] + dy, v[2]]
    els = []
    if half == "lower":
        els.append(box(up([0, 0, 0]), up([16, 4, 16]), "#base", cull=True))
        els.append(box(up([0, 4, 0]), up([16, 16, 16]), "#glass", faces=SIDES, cull=True))
        els.append(box(up([INSET, 4, INSET]), up([16 - INSET, 16, 16 - INSET]), "#water", faces=SIDES))
    else:
        els.append(box(up([0, 0, 0]), up([16, 16, 16]), "#glass", faces=SIDES + ("up",), cull=True))
        els.append(box(up([INSET, 0, INSET]), up([16 - INSET, 14, 16 - INSET]), "#water", faces=SIDES + ("up",)))
    return els


ANCHOR_TEXTURES = {
    "base": "minecraft:block/soul_sand",
    "glass": translucent("minecraft:block/glass"),
    "water": translucent(tex("block/ender_anchor_water", "minecraft:block/blue_stained_glass")),
    "particle": "minecraft:block/glass",
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


def beacon_model(on):
    side = tex("block/ender_beacon_side", "minecraft:block/obsidian")
    top = tex("block/ender_beacon_top_on" if on else "block/ender_beacon_top", "minecraft:block/crying_obsidian")
    return {"parent": "minecraft:block/cube_bottom_top",
            "textures": {"side": side, "top": top, "bottom": "minecraft:block/obsidian"}}


def generate(ctx):
    A = ctx.ASSETS
    ctx.block_model("ender_anchor_lower", anchor_model("lower"))
    ctx.block_model("ender_anchor_upper", anchor_model("upper"))
    ctx.write(A / "models" / "item" / "ender_anchor.json", anchor_item_model())
    ctx.write(A / "blockstates" / "ender_anchor.json", {"variants": {
        f"enabled={on},half={half}": {"model": f"{MOD}:block/ender_anchor_{half}"}
        for on in ("false", "true") for half in ("lower", "upper")}})
    ctx.item_def("ender_anchor", "item/ender_anchor")

    ctx.block_model("ender_beacon", beacon_model(False))
    ctx.block_model("ender_beacon_on", beacon_model(True))
    ctx.write(A / "blockstates" / "ender_beacon.json", {"variants": {
        "powered=false": {"model": f"{MOD}:block/ender_beacon"},
        "powered=true": {"model": f"{MOD}:block/ender_beacon_on"}}})
    ctx.item_def("ender_beacon", "block/ender_beacon")

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
    # A stasis chamber: glass all round, water in the middle, soul sand at the bottom. The bucket comes back.
    ctx.shaped("ender_anchor", ["GGG", "GWG", "GSG"], {
        "G": "minecraft:glass", "S": "minecraft:soul_sand", "W": "minecraft:water_bucket"}, "ender_anchor")
    ctx.machine("crushing", "ender_dust", [("minecraft:ender_pearl", 1)], "ender_dust", 2, time=60, min_grade=3)
    ctx.shaped("ender_beacon", ["OEO", "DFD", "OOO"], {
        "O": "minecraft:obsidian", "E": "minecraft:ender_eye", "D": "ender_dust",
        "F": "advanced_machine_frame"}, "ender_beacon")
    ctx.shaped("recall_charm", [" G ", "DED", " C "], {
        "G": "#c:ingots/gold", "D": "ender_dust", "E": "minecraft:ender_eye",
        "C": "advanced_circuit"}, "recall_charm", category="equipment")

    # ---------------------------------------------------------------- advancements
    ctx.advancement("electric_anchor", "age_electric", "ender_anchor", ["ender_anchor"], "Always Loaded",
                    "Build an Ender Anchor (a stasis chamber of glass, water and soul sand) and drop a pearl in",
                    "Siempre cargado",
                    "Construye un ancla de ender (una cámara de estasis de vidrio, agua y arena de almas) y echa una perla")
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
    L(f"{T}.ender_anchor", "A stasis chamber: keeps a %s×%s area of chunks loaded",
      "Una cámara de estasis: mantiene cargada un área de %s×%s chunks")
    L(f"{T}.ender_anchor_pearl", "Right-click with an ender pearl to start it. It runs until broken; breaking it loses the pearl",
      "Clic derecho con una perla de ender para activarla. Funciona hasta que se rompe; al romperla se pierde la perla")
    L(f"{T}.two_tall", "Two blocks tall", "Ocupa dos bloques de alto")
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
    L(f"{M}.anchor_active", "Keeping %s×%s chunks loaded", "Manteniendo %s×%s chunks cargados")
    L(f"{M}.anchor_empty", "Empty: drop an ender pearl in to start it",
      "Vacía: echa una perla de ender para activarla")
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

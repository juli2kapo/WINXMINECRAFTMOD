"""Mob tools (Automation Age): Mob Capsule, Minimizer Ray and Maximizer Ray.

Item models and definitions (the capsule switches texture when it holds a mob), recipes, the
factoryascent:capsule_blacklist entity-type tag, lang (en + es) and two advancements.
Textures come from tools/features/mobs_textures.py.
"""

MOD = "factoryascent"

BLACKLIST = ["minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian",
             "minecraft:player"]

LANG = [
    ("item.factoryascent.mob_capsule", "Mob Capsule", "Cápsula de criaturas"),
    ("item.factoryascent.mob_capsule.full", "Mob Capsule (%s)", "Cápsula de criaturas (%s)"),
    ("item.factoryascent.minimizer_ray", "Minimizer Ray", "Rayo minimizador"),
    ("item.factoryascent.maximizer_ray", "Maximizer Ray", "Rayo maximizador"),

    ("tooltip.factoryascent.mob_capsule",
     "Hold right-click on a mob for 3 s to trap it. Keep looking at it and stay close. Right-click a block to release.",
     "Mantén clic derecho sobre una criatura 3 s para atraparla. Sigue mirándola y no te alejes. "
     "Clic derecho en un bloque para liberarla."),
    ("tooltip.factoryascent.mob_capsule.contains", "Contains: %s", "Contiene: %s"),
    ("tooltip.factoryascent.mob_capsule.baby", "Baby %s", "%s (cría)"),
    ("tooltip.factoryascent.mob_capsule.health", "Health: %s / %s", "Vida: %s / %s"),
    ("tooltip.factoryascent.mob_capsule.custom_name", "Name: %s", "Nombre: %s"),
    ("tooltip.factoryascent.mob_capsule.release", "Right-click a block to release it.",
     "Clic derecho en un bloque para liberarla."),

    ("tooltip.factoryascent.minimizer_ray",
     "Shrinks any creature: smaller mobs fit through 1-block gaps and are easier to move. Maximizer undoes it.",
     "Encoge a cualquier criatura: las criaturas pequeñas pasan por huecos de 1 bloque y son más fáciles "
     "de mover. El maximizador lo deshace."),
    ("tooltip.factoryascent.maximizer_ray",
     "Grows any creature, up to 4× its size. Also undoes the Minimizer.",
     "Agranda a cualquier criatura, hasta 4 veces su tamaño. También deshace el minimizador."),
    # size_ray.usage / limits / scoped / scope_hint live in tools/features/ray.py with the ray models.

    ("message.factoryascent.mob_capsule.capturing", "Capturing %s… %s%%", "Capturando %s… %s%%"),
    ("message.factoryascent.mob_capsule.captured", "Captured %s!", "¡%s capturado!"),
    ("message.factoryascent.mob_capsule.released", "Released %s", "%s liberado"),
    ("message.factoryascent.mob_capsule.no_room", "Not enough room to release it here",
     "No hay espacio para liberarla aquí"),
    ("message.factoryascent.mob_capsule.no_target", "No creature in range (6 blocks)",
     "No hay ninguna criatura al alcance (6 bloques)"),
    ("message.factoryascent.mob_capsule.failed.released", "Capture cancelled: you let go",
     "Captura cancelada: soltaste el botón"),
    ("message.factoryascent.mob_capsule.failed.looked_away", "Capture failed: you looked away",
     "Captura fallida: dejaste de mirarla"),
    ("message.factoryascent.mob_capsule.failed.too_far", "Capture failed: too far away (6 blocks max)",
     "Captura fallida: demasiado lejos (máximo 6 bloques)"),
    ("message.factoryascent.mob_capsule.failed.died", "Capture failed: the creature is gone",
     "Captura fallida: la criatura ya no está"),
    ("message.factoryascent.mob_capsule.refused.not_mob", "Only living mobs can be captured",
     "Solo se pueden capturar criaturas vivas"),
    ("message.factoryascent.mob_capsule.refused.blacklist", "%s is too powerful to be captured",
     "%s es demasiado poderoso para ser capturado"),
    ("message.factoryascent.mob_capsule.refused.riding", "Can't capture %s while it rides or is ridden",
     "No se puede capturar a %s mientras monta o es montado"),
    ("message.factoryascent.mob_capsule.refused.leashed", "Unleash %s first", "Primero quítale la rienda a %s"),

    ("message.factoryascent.size_ray.no_energy", "Not enough energy (%s FE per shot)",
     "Energía insuficiente (%s FE por disparo)"),
    ("message.factoryascent.size_ray.ready", "Charged! Release to fire", "¡Cargado! Suelta para disparar"),
    ("message.factoryascent.size_ray.hit", "%s is now at %s%% size", "%s ahora tiene un %s%% de su tamaño"),
]


def models(ctx):
    for name in ("mob_capsule", "mob_capsule_full"):
        ctx.write(ctx.ASSETS / "models" / "item" / f"{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
    ctx.write(ctx.ASSETS / "items" / "mob_capsule.json", {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:has_component",
        "component": f"{MOD}:captured_mob",
        "on_true": {"type": "minecraft:model", "model": f"{MOD}:item/mob_capsule_full"},
        "on_false": {"type": "minecraft:model", "model": f"{MOD}:item/mob_capsule"}}})
    # The rays' 3D models and item definitions come from tools/features/ray.py.


def recipes(ctx):
    ctx.shaped("mob_capsule", ["GSG", "EBE", "GSG"],
               {"G": "minecraft:glass", "S": "#c:ingots/steel", "E": "ender_dust", "B": "basic_circuit"},
               "mob_capsule", category="equipment")
    for name, special in (("minimizer_ray", "minecraft:fermented_spider_eye"),
                          ("maximizer_ray", "minecraft:golden_apple")):
        # An eye of ender focuses the beam at the muzzle, ender dust is wound along the barrel,
        # and the shrinking or growing catalyst sits in the grip.
        ctx.shaped(name, [" DE", "ACD", "XA "],
                   {"D": "ender_dust", "E": "minecraft:ender_eye", "A": "#c:plates/aluminum",
                    "C": "advanced_circuit", "X": special},
                   name, category="equipment")


def generate(ctx):
    models(ctx)
    recipes(ctx)
    ctx.write(ctx.DATA / MOD / "tags" / "entity_type" / "capsule_blacklist.json", {"values": BLACKLIST})
    for key, en, es in LANG:
        ctx.lang(key, en, es)
    ctx.advancement("automation_mob_capsule", "age_automation", "mob_capsule", ["mob_capsule"],
                    "Pocket Monster", "Craft a Mob Capsule: hold right-click on a mob for 3 s to trap it",
                    "Monstruo de bolsillo", "Fabrica una cápsula de criaturas: mantén clic derecho 3 s sobre una criatura")
    ctx.advancement("automation_size_ray", "age_automation", "minimizer_ray", ["minimizer_ray", "maximizer_ray"],
                    "Honey, I Shrunk the Cow", "Craft a Minimizer or Maximizer Ray to resize any creature",
                    "Cariño, encogí a la vaca", "Fabrica un rayo minimizador o maximizador para cambiar el tamaño "
                    "de cualquier criatura")

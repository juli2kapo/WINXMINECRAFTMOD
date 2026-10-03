"""Station payloads (Java: stationkit/): the Station Kit and the Cargo Pod for the Launch Pad, the
crew pod a capsule leaves in orbit when there is no station, and the Station Core's cargo hold.
Item models, recipes, advancements, lang (English + Spanish). Textures: stationkit_textures.py.
"""

MOD = "factoryascent"


def code_advancement(ctx, key, parent, icon, frame, en, es):
    """An advancement the mod grants from code (criterion 'done')."""
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:{parent}",
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {"icon": {"id": f"{MOD}:{icon}"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
                    "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": frame},
        "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", en[0], es[0])
    ctx.lang(f"advancements.{MOD}.{key}.description", en[1], es[1])


def generate(ctx):
    for name in ("station_kit", "cargo_pod"):
        ctx.flat_item(name)
    # A folded module: hull panels around a Station Core, an Oxygen Sealer, an Airlock and a window.
    ctx.shaped("station_kit", ["HWH", "ACO", "HHH"],
               {"H": "station_hull_white", "W": "station_window", "A": "airlock_door", "C": "station_core",
                "O": "oxygen_sealer"}, "station_kit", category="misc")
    # A freight canister: a chest in a titanium/aluminium shell over a fire-brick heat shield.
    ctx.shaped("cargo_pod", [" T ", "ACA", "AFA"],
               {"T": "#c:plates/titanium", "A": "#c:plates/aluminum", "C": "minecraft:chest", "F": "fire_bricks"},
               "cargo_pod", category="misc")
    code_advancement(ctx, "station_kit", "space_orbit", "station_kit", "goal",
                     ("Home Away From Home", "Launch a Station Kit and unfold a sealed module in orbit"),
                     ("Un hogar lejos del hogar", "Lanza un kit de estación y despliega un módulo sellado en órbita"))
    code_advancement(ctx, "station_cargo", "station_kit", "cargo_pod", "task",
                     ("Special Delivery", "Ship building materials to your station in a Cargo Pod"),
                     ("Entrega especial", "Envía materiales de construcción a tu estación en una cápsula de carga"))
    L = ctx.lang
    I, T, M, G = f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"gui.{MOD}"
    L(f"{I}.station_kit", "Station Kit", "Kit de estación")
    L(f"{I}.cargo_pod", "Cargo Pod", "Cápsula de carga")
    L(f"{T}.station_kit", "A folded station module: a sealed 7×7 room with a Station Core, an Oxygen Sealer, windows, "
      "an Airlock and a Return Pod",
      "Un módulo de estación plegado: una sala sellada de 7×7 con núcleo de estación, sellador de oxígeno, ventanas, "
      "esclusa y cápsula de retorno")
    L(f"{T}.station_kit_how", "Mount it on a Launch Pad and launch: it unfolds in orbit straight above the pad "
      "(the space there must be empty)",
      "Móntalo en una plataforma de lanzamiento y lánzalo: se despliega en órbita justo encima "
      "(ese espacio debe estar vacío)")
    L(f"{T}.cargo_pod", "27 slots of freight for your station in orbit",
      "27 huecos de carga para tu estación en órbita")
    L(f"{T}.cargo_pod_how", "Right-click to load it. Launched from a pad, it is unloaded into the Station Core above the pad",
      "Clic derecho para cargarla. Lanzada desde una plataforma, se descarga en el núcleo de estación de encima")
    L(f"{T}.cargo_pod_load", "Loaded: %s / %s stacks", "Carga: %s / %s montones")
    L(f"{T}.return_pod_cabin", "Sneak-use in orbit to sit inside: its cabin has air",
      "Úsala agachado en órbita para sentarte dentro: su cabina tiene aire")
    L(f"{T}.station_core_cargo", "Sneak-use (empty hand) for the cargo hold, where Cargo Pods are unloaded",
      "Úsalo agachado (mano vacía) para abrir la bodega, donde se descargan las cápsulas de carga")
    L(f"{T}.crew_capsule_arrive", "In orbit you land at your team's station above the pad; with none, the capsule "
      "floats there as a pod (no free platform: launch a Station Kit)",
      "En órbita llegas a la estación de tu equipo sobre la plataforma; si no hay, la cápsula flota allí "
      "(no hay plataforma gratis: lanza un kit de estación)")
    L(f"{G}.station.cargo", "%s: cargo hold", "%s: bodega")
    L(f"{M}.crew_pod_arrived", "In orbit, with no station above your launch site: your capsule floats here as a pod. "
      "Sneak to climb out, sneak-use the pod to climb back in (it has air), use it to go home. "
      "Launch a Station Kit from the same pad to build a station here.",
      "En órbita, sin estación sobre tu sitio de lanzamiento: tu cápsula flota aquí. Agáchate para salir, "
      "úsala agachado para volver a entrar (tiene aire) y úsala para volver a casa. Lanza un kit de estación "
      "desde la misma plataforma para construir aquí una estación.")
    L(f"{M}.crew_pod_in", "Inside the pod: cabin air. Sneak to climb out.",
      "Dentro de la cápsula: aire de cabina. Agáchate para salir.")
    L(f"{M}.kit_earth_only", "Station Kits and Cargo Pods fly to Earth orbit: launch them from the Overworld",
      "Los kits de estación y las cápsulas de carga van a la órbita terrestre: lánzalos desde el mundo normal")
    L(f"{M}.kit_occupied", "Something is already built in orbit above this pad (at %s, %s, %s): the kit needs empty space",
      "Ya hay algo construido en órbita sobre esta plataforma (en %s, %s, %s): el kit necesita espacio vacío")
    L(f"{M}.kit_aborted", "The Station Kit found its spot taken and came back down",
      "El kit de estación encontró su sitio ocupado y volvió a bajar")
    L(f"{M}.kit_deployed", "Station Kit deployed: %s is ready in orbit at %s, %s, %s",
      "Kit de estación desplegado: %s está lista en órbita en %s, %s, %s")
    L(f"{M}.cargo_no_station", "No station of your team in orbit above this pad (%s, %s, %s): launch a Station Kit first",
      "Tu equipo no tiene estación en órbita sobre esta plataforma (%s, %s, %s): lanza antes un kit de estación")
    L(f"{M}.cargo_returned", "The Cargo Pod found no station and came back down",
      "La cápsula de carga no encontró estación y volvió a bajar")
    L(f"{M}.cargo_delivered", "Cargo Pod unloaded: %s stacks in the hold of %s",
      "Cápsula de carga descargada: %s montones en la bodega de %s")

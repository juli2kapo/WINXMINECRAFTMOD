"""Factory Phone resources: the item (flat icon in inventories, a 3D phone in hand whose screen
glows while it is open), recipe (Automation age), advancements and lang (English + Spanish).
Textures: phone_textures.py. The screen and the app icons/wallpapers are drawn in code
(phone/client/PhoneScreen.java) from textures/gui/phone/."""

MOD = "factoryascent"
P = f"gui.{MOD}.phone"


def m(name):
    return {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}


def phone_model(lit):
    """A slim slab of a phone: graphite body, bezel front with the screen as its own quad
    (light_emission 15 when lit, so it glows in the dark)."""
    screen = {"from": [5.5, 2.5, 8.51], "to": [10.5, 12.5, 8.51],
              "faces": {"south": {"texture": "#screen", "uv": [3, 1, 13, 15]}}}
    if lit:
        screen["light_emission"] = 15
    return {
        "parent": "minecraft:item/handheld",
        "textures": {"back": f"{MOD}:item/factory_phone_back", "side": f"{MOD}:item/factory_phone_side",
                     "front": f"{MOD}:item/factory_phone_front",
                     "screen": f"{MOD}:item/factory_phone_screen_{'on' if lit else 'off'}",
                     "particle": f"{MOD}:item/factory_phone_side"},
        "elements": [
            {"from": [5, 1, 7.5], "to": [11, 14, 8.5], "faces": {
                "north": {"texture": "#back", "uv": [5, 2, 11, 15]},
                "south": {"texture": "#front", "uv": [5, 2, 11, 15]},
                "east": {"texture": "#side", "uv": [0, 2, 1, 15]},
                "west": {"texture": "#side", "uv": [0, 2, 1, 15]},
                "up": {"texture": "#side", "uv": [5, 0, 11, 1]},
                "down": {"texture": "#side", "uv": [5, 0, 11, 1]}}},
            # side buttons
            {"from": [11, 9, 7.75], "to": [11.25, 12, 8.25], "faces": {
                "east": {"texture": "#side", "uv": [0, 0, 1, 3]}, "up": {"texture": "#side", "uv": [0, 0, 1, 1]},
                "down": {"texture": "#side", "uv": [0, 0, 1, 1]}, "north": {"texture": "#side", "uv": [0, 0, 1, 3]},
                "south": {"texture": "#side", "uv": [0, 0, 1, 3]}}},
            screen,
        ],
        "display": {
            "thirdperson_righthand": {"rotation": [-90, 180, 180], "translation": [0, 4, 1], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [-90, 180, 180], "translation": [0, 4, 1], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [-5, -20, 0], "translation": [0.5, 4.5, 0], "scale": [0.42, 0.42, 0.42]},
            "firstperson_lefthand": {"rotation": [-5, 20, 0], "translation": [0.5, 4.5, 0], "scale": [0.42, 0.42, 0.42]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        },
    }


LANG = [
    (f"item.{MOD}.factory_phone", "Factory Phone", "Teléfono de fábrica"),
    # item
    (f"tooltip.{MOD}.phone.links", "Watching %s machines · %s power networks · storage: %s",
     "Vigila %s máquinas · %s redes de energía · almacén: %s"),
    (f"tooltip.{MOD}.phone.open", "Right-click to open. Charge it in a Charger", "Clic derecho para abrirlo. Cárgalo en un cargador"),
    (f"tooltip.{MOD}.phone.link", "Sneak-use on a machine, power cable or storage terminal to link it",
     "Úsalo agachado en una máquina, cable de energía o terminal de almacenamiento para vincularlo"),
    (f"tooltip.{MOD}.phone.signal", "Network apps need your team's Uplink Satellite overhead",
     "Las apps de red necesitan el satélite de enlace de tu equipo en órbita"),
    (f"message.{MOD}.phone.battery_empty", "The phone's battery is empty: charge it in a Charger",
     "La batería del teléfono está vacía: cárgalo en un cargador"),
    (f"message.{MOD}.phone.no_terminal", "This storage network has no terminal to link", "Esta red de almacenamiento no tiene terminal que vincular"),
    (f"message.{MOD}.phone.unlinked", "Unlinked %s", "%s desvinculado"),
    (f"message.{MOD}.phone.too_many_machines", "The phone already watches %s machines: unlink one in Settings",
     "El teléfono ya vigila %s máquinas: desvincula una en Ajustes"),
    (f"message.{MOD}.phone.too_many_networks", "The phone already has %s energy networks: unlink one in Settings",
     "El teléfono ya tiene %s redes de energía: desvincula una en Ajustes"),
    (f"message.{MOD}.phone.network_already", "This energy network is already linked", "Esta red de energía ya está vinculada"),
    (f"message.{MOD}.phone.linked_storage", "Storage app linked to %s", "App Almacén vinculada a %s"),
    (f"message.{MOD}.phone.linked_power", "Power app: watching this %s network", "App Energía: vigilando la red de este %s"),
    (f"message.{MOD}.phone.linked_machine", "Machines app: watching %s", "App Máquinas: vigilando %s"),
    # phone OS
    (f"{P}.no_signal", "No signal", "Sin señal"),
    (f"{P}.no_signal_title", "No signal", "Sin señal"),
    (f"{P}.no_signal_body", "This app needs your team's Uplink Satellite in orbit over this dimension. Launch one from a Launch Pad.",
     "Esta app necesita el satélite de enlace de tu equipo en órbita sobre esta dimensión. Lanza uno desde una plataforma."),
    (f"{P}.loading", "Loading", "Cargando"),
    (f"{P}.no_view", "This app needs a newer version on your side", "Esta app necesita una versión más nueva de tu lado"),
    (f"{P}.day", "Day %s", "Día %s"),
    (f"{P}.day_team", "Day %s · %s", "Día %s · %s"),
    (f"{P}.dead", "Battery empty", "Batería agotada"),
    (f"{P}.at", "At %s, %s, %s", "En %s, %s, %s"),
    (f"{P}.other_dimension", "In another dimension", "En otra dimensión"),
    (f"{P}.out_of_range", "Out of range: no signal", "Fuera de alcance: sin señal"),
    (f"{P}.unloaded", "Not loaded (an Ender Anchor keeps it loaded)", "No cargado (un ancla de ender lo mantiene cargado)"),
    (f"{P}.gone", "Gone: the block was removed", "Desaparecido: se quitó el bloque"),
    (f"{P}.idle_no_input", "Idle: no input", "Inactiva: sin entrada"),
    (f"{P}.reactor.running", "Reactor running", "Reactor en marcha"),
    (f"{P}.reactor.idle", "Reactor idle", "Reactor inactivo"),
    (f"{P}.reactor.scram", "SCRAM: rods inserted", "SCRAM: barras insertadas"),
    (f"{P}.reactor.melted", "MELTDOWN", "FUSIÓN DEL NÚCLEO"),
    (f"{P}.low_battery", "Battery low", "Batería baja"),
    (f"{P}.low_battery_body", "Charge the phone in a Charger", "Carga el teléfono en un cargador"),
    (f"{P}.alert.stopped_title", "%s stopped", "%s se detuvo"),
    (f"{P}.alert.stopped", "%s", "%s"),
    (f"{P}.alert.gone_title", "%s is gone", "%s desapareció"),
    (f"{P}.alert.gone", "Nothing at %s any more", "Ya no hay nada en %s"),
    (f"{P}.alert.hot_title", "%s OVERHEATING", "%s SOBRECALENTADO"),
    (f"{P}.alert.hot", "Core at %s°C, meltdown at %s°C!", "¡Núcleo a %s°C, fusión a %s°C!"),
    # app names
    (f"{P}.app.map", "Map", "Mapa"),
    (f"{P}.app.storage", "Storage", "Almacén"),
    (f"{P}.app.team", "Team", "Equipo"),
    (f"{P}.app.machines", "Machines", "Máquinas"),
    (f"{P}.app.power", "Power", "Energía"),
    (f"{P}.app.recall", "Recall", "Retorno"),
    (f"{P}.app.dyson", "Dyson", "Dyson"),
    (f"{P}.app.settings", "Settings", "Ajustes"),
    # map
    (f"{P}.map.surveying", "Survey satellite overhead", "Satélite de reconocimiento arriba"),
    (f"{P}.map.no_survey", "No survey satellite here", "Sin satélite de reconocimiento"),
    (f"{P}.map.chunks", "Mapped: %s chunks", "Mapeado: %s chunks"),
    (f"{P}.map.position", "You: %s, %s", "Tú: %s, %s"),
    (f"{P}.map.hint", "Closing the map returns here", "Al cerrar el mapa vuelves aquí"),
    (f"{P}.map.open", "Open map", "Abrir mapa"),
    # storage
    (f"{P}.storage.unlinked", "No storage linked", "Sin almacén vinculado"),
    (f"{P}.storage.how", "Sneak-use the phone on a Storage Terminal or Storage Controller to link its network.",
     "Usa el teléfono agachado en una terminal o controlador de almacenamiento para vincular su red."),
    (f"{P}.storage.items", "%s / %s items", "%s / %s objetos"),
    (f"{P}.storage.types", "%s / %s item types", "%s / %s tipos de objeto"),
    (f"{P}.storage.empty", "The network is empty", "La red está vacía"),
    (f"{P}.storage.open", "Open terminal", "Abrir terminal"),
    # team
    (f"{P}.team.solo", "No team (create one on the Team screen)", "Sin equipo (crea uno en la pantalla de equipo)"),
    (f"{P}.team.name", "Team %s", "Equipo %s"),
    (f"{P}.team.screen", "Team…", "Equipo…"),
    (f"{P}.team.empty", "No messages yet", "Aún no hay mensajes"),
    (f"{P}.team.hint", "Message your team…", "Escribe a tu equipo…"),
    (f"{P}.team.send", "Send", "Enviar"),
    (f"{P}.team.slow_down", "Slow down a little", "Más despacio"),
    # machines
    (f"{P}.machines.none", "No machines watched", "No vigilas ninguna máquina"),
    (f"{P}.machines.how", "Sneak-use the phone on a machine, generator or reactor controller to watch it (up to %s). "
                          "The phone alerts you when one stops or a reactor overheats. Within %s blocks it works without signal.",
     "Usa el teléfono agachado en una máquina, generador o controlador de reactor para vigilarlo (hasta %s). "
     "Te avisa cuando una se detiene o un reactor se sobrecalienta. A menos de %s bloques funciona sin señal."),
    # power
    (f"{P}.power.none", "No energy networks linked", "No hay redes de energía vinculadas"),
    (f"{P}.power.how", "Sneak-use the phone on any power cable to follow that network's energy (up to %s networks).",
     "Usa el teléfono agachado en cualquier cable de energía para seguir esa red (hasta %s redes)."),
    (f"{P}.power.cables", "%s cables · max %s FE/t", "%s cables · máx. %s FE/t"),
    (f"{P}.power.total", "Total: +%s / -%s FE/t", "Total: +%s / -%s FE/t"),
    # recall
    (f"{P}.recall.no_charm", "No Recall Charm on you", "No llevas un amuleto de retorno"),
    (f"{P}.recall.how", "Carry a Recall Charm linked to an Ender Beacon: the phone can start its recall from here.",
     "Lleva un amuleto de retorno vinculado a una baliza de ender: el teléfono puede iniciar el retorno desde aquí."),
    (f"{P}.recall.beacon", "Ender Beacon", "Baliza de ender"),
    (f"{P}.recall.dim", "Dimension: %s", "Dimensión: %s"),
    (f"{P}.recall.state.ready", "Pearl loaded: ready", "Perla cargada: listo"),
    (f"{P}.recall.state.no_pearl", "No pearl in the beacon", "La baliza no tiene perla"),
    (f"{P}.recall.state.gone", "The beacon is gone", "La baliza ya no está"),
    (f"{P}.recall.state.unknown", "Beacon out of reach", "Baliza fuera de alcance"),
    (f"{P}.recall.cooling", "Cooling down: %s s", "Enfriando: %s s"),
    (f"{P}.recall.rule", "Stand still for %s s (damage breaks it)", "Quédate quieto %s s (el daño lo cancela)"),
    (f"{P}.recall.go", "Recall", "Volver"),
    (f"{P}.recall.cancel", "Cancel", "Cancelar"),
    (f"{P}.recall.channelling", "Channelling: stand still…", "Canalizando: quédate quieto…"),
    (f"{P}.recall.cooldown", "The charm is still cooling down", "El amuleto aún se está enfriando"),
    (f"{P}.recall.moved", "You moved: recall cancelled", "Te moviste: retorno cancelado"),
    # dyson
    (f"{P}.dyson.none", "No Dyson Cube yet", "Aún no hay cubo de Dyson"),
    (f"{P}.dyson.how", "Launch Solar Collectors into solar orbit with a Mass Driver to start your team's Dyson Cube.",
     "Lanza colectores solares a la órbita solar con un acelerador de masas para empezar el cubo de Dyson de tu equipo."),
    (f"{P}.dyson.collectors", "Collectors: %s / %s", "Colectores: %s / %s"),
    (f"{P}.dyson.power", "Swarm power: %s FE/t", "Potencia del enjambre: %s FE/t"),
    (f"{P}.dyson.received", "Received now: %s FE/t", "Recibido ahora: %s FE/t"),
    # settings
    (f"{P}.settings.alerts", "Notifications", "Notificaciones"),
    (f"{P}.settings.sound", "Ringtone sound", "Sonido de aviso"),
    (f"{P}.settings.ringtone", "Ringtone", "Tono"),
    (f"{P}.settings.wallpaper", "Wallpaper", "Fondo"),
    (f"{P}.settings.battery", "Battery: %s / %s FE", "Batería: %s / %s FE"),
    (f"{P}.settings.linked", "Linked devices (%s)", "Dispositivos vinculados (%s)"),
    (f"{P}.settings.no_links", "Nothing linked. Sneak-use the phone on a machine, cable or storage terminal.",
     "Nada vinculado. Usa el teléfono agachado en una máquina, cable o terminal."),
    (f"{P}.ringtone.chime", "Chime", "Campanilla"),
    (f"{P}.ringtone.bell", "Bell", "Campana"),
    (f"{P}.ringtone.pling", "Pling", "Pling"),
    (f"{P}.ringtone.bit", "8-bit", "8 bits"),
    (f"{P}.wallpaper.0", "Circuit", "Circuito"),
    (f"{P}.wallpaper.1", "Orbit", "Órbita"),
    (f"{P}.wallpaper.2", "Sunset", "Atardecer"),
    (f"{P}.wallpaper.3", "Dyson", "Dyson"),
]


def code_advancement(ctx, key, parent, icon, frame, en, es):
    """An advancement the mod grants from code (criterion 'done', see PhoneContent.award)."""
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
    # item: flat icon in GUIs, on the ground and in frames; a 3D phone in hand that lights up while open
    ctx.write(A / "models" / "item" / "factory_phone.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/factory_phone"}})
    ctx.write(A / "models" / "item" / "factory_phone_3d.json", phone_model(False))
    ctx.write(A / "models" / "item" / "factory_phone_3d_on.json", phone_model(True))
    ctx.write(A / "items" / "factory_phone.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed"], "model": m("factory_phone")}],
        "fallback": {"type": "minecraft:condition", "property": f"{MOD}:phone_screen_on",
                     "on_true": m("factory_phone_3d_on"), "on_false": m("factory_phone_3d")}}})

    # Automation age: aluminium case, a glass screen, an advanced circuit, a silicon wafer modem and redstone.
    ctx.shaped("factory_phone", ["PGP", "WCW", "PRP"], {
        "P": "#c:plates/aluminum", "G": "minecraft:black_stained_glass_pane", "W": "silicon_wafer",
        "C": "advanced_circuit", "R": "minecraft:redstone"}, "factory_phone", category="equipment")

    ctx.advancement("automation_phone", "age_automation", "factory_phone", ["factory_phone"], "There's an App for That",
                    "Craft a Factory Phone. Sneak-use it on machines to watch them",
                    "Hay una app para eso",
                    "Fabrica un teléfono de fábrica. Úsalo agachado en máquinas para vigilarlas", frame="goal")
    code_advancement(ctx, "phone_alert", "automation_phone", "factory_phone", "task",
                     ("You Have One New Alert", "Get a phone alert from a machine you watch"),
                     ("Tienes una alerta nueva", "Recibe una alerta en el teléfono de una máquina que vigilas"))
    code_advancement(ctx, "phone_chat", "automation_phone", "factory_phone", "task",
                     ("Can You Hear Me Now?", "Send a team chat message from your phone over the uplink"),
                     ("¿Me oyes ahora?", "Envía un mensaje al chat de tu equipo desde el teléfono por el enlace"))

    for key, en, es in LANG:
        ctx.lang(key, en, es)

"""Orbital age resources: Launch Pad (3x3 of launch_pad around a launch_controller), rocket models
drawn on the pad, Ground Station (dish drawn by a renderer), Orbital Radar (antenna drawn by a
renderer), satellites (survey, uplink, guardian), the Anti-Satellite missile, rocket fuel, the
Wireless Terminal; the Team and Radar screens' text; recipes, advancements and lang (English +
Spanish)."""
import json
from pathlib import Path

MOD = "factoryascent"
TEXTURES = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"
PICKAXE_BLOCKS = ["launch_pad", "launch_controller", "ground_station", "orbital_radar"]
SIDES = ("north", "east", "south", "west")


def t(name):
    return f"{MOD}:block/{name}"


def box(frm, to, tex, faces=("north", "east", "south", "west", "up", "down"), uv=None, cull=None, rot=None):
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


def plate_model(top, side):
    """A 4 px high plate (the pad's collision shape)."""
    return {"parent": "minecraft:block/block",
            "textures": {"top": t(top), "side": t(side), "bottom": t("launch_pad_side"), "particle": t(side)},
            "elements": [box([0, 0, 0], [16, 4, 16],
                             {"up": "#top", "down": "#bottom", **{s: "#side" for s in SIDES}},
                             uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], **{s: [0, 12, 16, 16] for s in SIDES}},
                             cull=("down", "north", "east", "south", "west"))]}


PLATE_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def rocket_model(kind, body="rocket_body", fin="rocket_fin"):
    """The rocket standing on the pad, modelled in a 32 px tall box; the renderer scales it 1.5x
    (about 3 blocks tall). Engine bell, hull with a coloured band, three-step nose, four fins.
    The anti-satellite missile is the same airframe in gunmetal with a red warhead."""
    tex = {"body": t(body), "nose": t(f"rocket_nose_{kind}"), "fin": t(fin),
           "engine": t("rocket_engine"), "particle": t(body)}
    hull = {"up": "#engine", "down": "#engine", **{s: "#body" for s in SIDES}}
    els = [
        box([6, 0, 6], [10, 2, 10], {"down": "#engine", "up": "#engine", **{s: "#engine" for s in SIDES}},
            uv={"down": [4, 4, 12, 12], "up": [4, 4, 12, 12], **{s: [0, 0, 16, 4] for s in SIDES}}),
        box([5, 2, 5], [11, 18, 11], hull, uv={"up": [5, 5, 11, 11], "down": [4, 4, 12, 12], **{s: [5, 0, 11, 16] for s in SIDES}}),
        box([5, 18, 5], [11, 22, 11], {**{s: "#body" for s in SIDES}, "up": "#body", "down": "#body"},
            uv={s: [5, 8, 11, 12] for s in SIDES + ("up", "down")}),
        box([5.5, 22, 5.5], [10.5, 25, 10.5], "#nose", uv={s: [3, 0, 13, 6] for s in SIDES + ("up", "down")}),
        box([6.5, 25, 6.5], [9.5, 28, 9.5], "#nose", uv={s: [4, 6, 12, 12] for s in SIDES + ("up", "down")}),
        box([7.5, 28, 7.5], [8.5, 31, 8.5], "#nose", uv={s: [7, 10, 9, 16] for s in SIDES + ("up", "down")}),
    ]
    for frm, to in [([2, 0, 7.5], [5, 8, 8.5]), ([11, 0, 7.5], [14, 8, 8.5]),
                    ([7.5, 0, 2], [8.5, 8, 5]), ([7.5, 0, 11], [8.5, 8, 14])]:
        els.append(box(frm, to, "#fin", uv={s: [0, 0, 6, 16] for s in SIDES + ("up", "down")}))
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": els}


def station_base_elements():
    base = {"base": "#base"}
    return [
        box([2, 0, 2], [14, 3, 14], "#base", uv={**{s: [2, 12, 14, 15] for s in SIDES}, "up": [2, 2, 14, 14], "down": [2, 2, 14, 14]}),
        box([4, 3, 4], [12, 5, 12], "#base", uv={**{s: [4, 4, 12, 6] for s in SIDES}, "up": [4, 4, 12, 12], "down": [4, 4, 12, 12]}),
        box([6.5, 5, 6.5], [9.5, 9, 9.5], "#base", uv={**{s: [6, 2, 9, 6] for s in SIDES}, "up": [6, 6, 9, 9], "down": [6, 6, 9, 9]}),
    ]


def dish_elements():
    """The dish on its yoke, tilted 22.5 degrees back; the renderer turns it around the vertical axis."""
    tilt = {"origin": [8, 10, 8], "axis": "x", "angle": -22.5}
    face = {"up": "#dish", "down": "#back", **{s: "#back" for s in SIDES}}
    return [
        box([7, 8.5, 7], [9, 10.5, 9], "#base", uv=[6, 6, 8, 8]),  # yoke
        box([2, 10, 2], [14, 11, 14], face, uv={"up": [2, 2, 14, 14], "down": [2, 2, 14, 14], **{s: [2, 0, 14, 1] for s in SIDES}}, rot=tilt),
        # raised rim
        box([1, 10.5, 2], [2, 12.5, 14], face, uv={"up": [0, 2, 1, 14], "down": [0, 2, 1, 14], **{s: [2, 0, 14, 2] for s in SIDES}}, rot=tilt),
        box([14, 10.5, 2], [15, 12.5, 14], face, uv={"up": [15, 2, 16, 14], "down": [15, 2, 16, 14], **{s: [2, 0, 14, 2] for s in SIDES}}, rot=tilt),
        box([1, 10.5, 1], [15, 12.5, 2], face, uv={"up": [0, 0, 16, 1], "down": [0, 0, 16, 1], **{s: [0, 0, 14, 2] for s in SIDES}}, rot=tilt),
        box([1, 10.5, 14], [15, 12.5, 15], face, uv={"up": [0, 15, 16, 16], "down": [0, 15, 16, 16], **{s: [0, 0, 14, 2] for s in SIDES}}, rot=tilt),
        # feed arm and receiver
        box([7.5, 11, 7.5], [8.5, 16, 8.5], "#feed", uv=[4, 2, 5, 12], rot=tilt),
        box([7, 16, 7], [9, 17.5, 9], "#feed", uv=[0, 0, 2, 2], rot=tilt),
    ]


def radar_antenna_elements():
    """Mast, turntable and a tilted phased-array panel with an emitter horn; the renderer spins it."""
    tilt = {"origin": [8, 12, 8], "axis": "x", "angle": 22.5}
    panel = {"north": "#array", "south": "#back", "east": "#back", "west": "#back", "up": "#back", "down": "#back"}
    return [
        box([6, 9, 6], [10, 10, 10], "#base", uv=[4, 4, 8, 8]),  # turntable
        box([7.5, 10, 7.5], [8.5, 13, 8.5], "#base", uv=[6, 6, 7, 9]),  # mast
        box([1, 11, 7], [15, 19, 8.5], panel, uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16],
                                                  **{f: [0, 0, 2, 16] for f in ("east", "west", "up", "down")}}, rot=tilt),
        box([7, 14, 5], [9, 16, 7], "#array", uv=[6, 6, 8, 8], rot=tilt),  # emitter horn
    ]


RADAR_TEX = {"base": t("orbital_radar_base"), "array": t("orbital_radar_array"), "back": t("orbital_radar_back"),
             "particle": t("orbital_radar_base")}

STATION_TEX = {"base": t("ground_station_base"), "dish": t("ground_station_dish"), "back": t("ground_station_dish_back"),
               "feed": t("ground_station_feed"), "particle": t("ground_station_base")}


def lang(L):
    B, I, T, M, O = f"block.{MOD}", f"item.{MOD}", f"tooltip.{MOD}", f"message.{MOD}", f"orbital.{MOD}"
    L(f"{B}.launch_pad", "Launch Pad", "Plataforma de lanzamiento")
    L(f"{B}.launch_controller", "Launch Controller", "Controlador de lanzamiento")
    L(f"{B}.ground_station", "Ground Station", "Estación terrena")
    L(f"{I}.survey_satellite", "Survey Satellite", "Satélite de reconocimiento")
    L(f"{I}.uplink_satellite", "Uplink Satellite", "Satélite de enlace")
    L(f"{I}.rocket_fuel", "Rocket Fuel", "Combustible de cohete")
    L(f"{B}.orbital_radar", "Orbital Radar", "Radar orbital")
    L(f"{I}.guardian_satellite", "Guardian Satellite", "Satélite guardián")
    L(f"{I}.asat_missile", "Anti-Satellite Missile", "Misil antisatélite")
    L(f"{O}.type.defense", "Guardian", "Guardián")
    L(f"{I}.wireless_terminal", "Wireless Terminal", "Terminal inalámbrica")
    L(f"{O}.survey_map", "Survey Map", "Mapa de reconocimiento")
    L(f"{O}.type.survey", "Survey", "Reconocimiento")
    L(f"{O}.type.uplink", "Uplink", "Enlace")
    L(f"{O}.dimension.minecraft.overworld", "the Overworld", "el Mundo Superior")
    L(f"{O}.dimension.minecraft.the_nether", "the Nether", "el Nether")
    L(f"{O}.dimension.minecraft.the_end", "the End", "el End")
    L(f"{O}.dimension.{MOD}.the_deep", "The Deep", "Las Profundidades")

    L(f"{T}.launch_pad", "Eight of these around a Launch Controller make the 3×3 Launch Pad",
      "Ocho de estas alrededor de un controlador de lanzamiento forman la plataforma de 3×3")
    L(f"{T}.launch_controller", "Centre of the 3×3 Launch Pad: mount a satellite, fuel it, launch it",
      "Centro de la plataforma de 3×3: monta un satélite, cárgale combustible y lánzalo")
    L(f"{T}.launch_controller_how",
      "Needs %s fuel per launch (Blaze Powder 1, Rocket Fuel 4). Launch with redstone or sneak-use flint and steel",
      "Necesita %s de combustible por lanzamiento (polvo de blaze 1, combustible de cohete 4). Lanza con redstone o agachado con mechero")
    L(f"{T}.launch_controller_multiblock", "Multiblock: surround it with 8 Launch Pad blocks",
      "Multibloque: rodéalo con 8 bloques de plataforma de lanzamiento")
    L(f"{T}.ground_station", "Right-click to list your team's satellites over this dimension",
      "Clic derecho para ver los satélites de tu equipo sobre esta dimensión")
    L(f"{T}.ground_station_map", "With a Survey Satellite up: right-click with an empty map to map the area",
      "Con un satélite de reconocimiento en órbita: clic derecho con un mapa vacío para cartografiar la zona")
    L(f"{T}.survey_satellite", "In orbit: Ground Stations in this dimension make filled maps of their area",
      "En órbita: las estaciones terrenas de esta dimensión crean mapas completos de su zona")
    L(f"{T}.uplink_satellite", "In orbit: your team has signal everywhere in this dimension (Wireless Terminal)",
      "En órbita: tu equipo tiene señal en toda esta dimensión (terminal inalámbrica)")
    L(f"{T}.satellite_launch", "Mount it on a Launch Pad. It covers the dimension it is launched from. Rename it in an anvil to name it",
      "Móntalo en una plataforma de lanzamiento. Cubre la dimensión desde la que se lanza. Renómbralo en un yunque para ponerle nombre")
    L(f"{T}.guardian_satellite",
      "In orbit: intercepts the next Anti-Satellite missile fired at your team's satellites in this dimension (used up doing so)",
      "En órbita: intercepta el próximo misil antisatélite disparado contra los satélites de tu equipo en esta dimensión (se consume al hacerlo)")
    L(f"{T}.launch_controller_automation", "Hoppers and pipes can insert payloads and fuel; they launch for whoever placed the controller",
      "Tolvas y tuberías pueden insertar cargas y combustible; se lanzan para quien colocó el controlador")
    L(f"{T}.ground_station_console", "Sneak-use with an empty hand: Team screen (teams, deorbit)",
      "Agachado con la mano vacía: pantalla de equipo (equipos, desorbitar)")
    L(f"{T}.wireless_console", "Sneak-use in the air: Team screen (teams, deorbit)",
      "Agachado en el aire: pantalla de equipo (equipos, desorbitar)")
    L(f"{T}.orbital_radar", "Lists every satellite over this dimension: yours and other teams'",
      "Muestra todos los satélites sobre esta dimensión: los tuyos y los de otros equipos")
    L(f"{T}.orbital_radar_lock", "Track a foreign contact to lock it (uses %s FE/t while tracking). Its owners are warned",
      "Rastrea un contacto extranjero para fijarlo (usa %s FE/t mientras rastrea). Sus dueños reciben un aviso")
    L(f"{T}.orbital_radar_asat", "Pick a locked contact as target, then right-click the radar with an Anti-Satellite Missile",
      "Elige un contacto fijado como objetivo y luego haz clic derecho en el radar con un misil antisatélite")
    L(f"{T}.asat_missile", "Destroys a foreign satellite over the dimension it is launched from",
      "Destruye un satélite extranjero sobre la dimensión desde la que se lanza")
    L(f"{T}.asat_unprogrammed", "Unprogrammed: lock a target on an Orbital Radar, pick it, then right-click the radar with this",
      "Sin programar: fija un objetivo en un radar orbital, elígelo y luego haz clic derecho en el radar con esto")
    L(f"{T}.asat_target", "Target: %s", "Objetivo: %s")
    L(f"{T}.asat_radar", "Locked by the radar at %s, %s, %s (%s)", "Fijado por el radar en %s, %s, %s (%s)")
    L(f"{T}.asat_launch", "Mount it on a Launch Pad like a satellite (%s fuel). The radar must still hold the lock",
      "Móntalo en una plataforma de lanzamiento como un satélite (%s de combustible). El radar debe mantener la fijación")
    L(f"{T}.wireless_unlinked", "Not linked: sneak-use it on a Storage Terminal",
      "Sin vincular: agáchate y úsala sobre una terminal de almacenamiento")
    L(f"{T}.wireless_linked", "Linked to the terminal at %s, %s, %s (%s)", "Vinculada a la terminal en %s, %s, %s (%s)")
    L(f"{T}.wireless_needs_uplink",
      "Works anywhere in the same dimension with uplink coverage, while the terminal's chunk is loaded",
      "Funciona en cualquier lugar de la misma dimensión con cobertura de enlace, si el chunk de la terminal está cargado")

    L(f"{M}.pad_no_controller", "This plate needs a Launch Controller in the middle of the 3×3",
      "Esta placa necesita un controlador de lanzamiento en el centro del 3×3")
    L(f"{M}.pad_incomplete", "Launch Pad incomplete: surround the controller with 8 Launch Pad blocks",
      "Plataforma incompleta: rodea el controlador con 8 bloques de plataforma de lanzamiento")
    L(f"{M}.pad_busy", "A launch is in progress", "Hay un lanzamiento en curso")
    L(f"{M}.pad_occupied", "A satellite is already mounted", "Ya hay un satélite montado")
    L(f"{M}.pad_empty", "Mount a satellite first", "Primero monta un satélite")
    L(f"{M}.pad_no_fuel", "Not enough fuel: %s/%s", "No hay suficiente combustible: %s/%s")
    L(f"{M}.pad_blocked", "Something is blocking the rocket's path", "Algo bloquea la trayectoria del cohete")
    L(f"{M}.pad_status_none", "no satellite", "sin satélite")
    L(f"{M}.pad_status", "Payload: %s · Fuel %s/%s (%s per launch)", "Carga: %s · Combustible %s/%s (%s por lanzamiento)")
    L(f"{M}.countdown", "Launch in %s…", "Lanzamiento en %s…")
    L(f"{M}.reached_orbit", "%s '%s' reached orbit over %s", "%s «%s» alcanzó la órbita sobre %s")
    L(f"{M}.satellite_line", "• %s '%s' over %s", "• %s «%s» sobre %s")
    L(f"{M}.station_header", "Satellites over %s (team %s): %s", "Satélites sobre %s (equipo %s): %s")
    L(f"{M}.station_empty", "None yet. Launch one from a Launch Pad", "Ninguno aún. Lanza uno desde una plataforma de lanzamiento")
    L(f"{M}.station_age", " (%s days in orbit)", " (%s días en órbita)")
    L(f"{M}.station_signal_on", "Uplink signal: online", "Señal de enlace: en línea")
    L(f"{M}.station_signal_off", "Uplink signal: none (launch an Uplink Satellite)",
      "Señal de enlace: ninguna (lanza un satélite de enlace)")
    L(f"{M}.station_survey_on", "Survey: online. Use an empty map on the station",
      "Reconocimiento: en línea. Usa un mapa vacío en la estación")
    L(f"{M}.station_survey_off", "Survey: none (launch a Survey Satellite)",
      "Reconocimiento: ninguno (lanza un satélite de reconocimiento)")
    L(f"{M}.station_no_survey", "Your team has no Survey Satellite over this dimension",
      "Tu equipo no tiene un satélite de reconocimiento sobre esta dimensión")
    L(f"{M}.station_mapping", "Downloading survey imagery…", "Descargando imágenes de reconocimiento…")
    L(f"{M}.wireless_linked", "Wireless Terminal linked", "Terminal inalámbrica vinculada")
    L(f"{M}.wireless_unlinked", "Link it first: sneak-use it on a Storage Terminal",
      "Primero vincúlala: agáchate y úsala sobre una terminal de almacenamiento")
    L(f"{M}.wireless_other_dimension", "The linked terminal is in another dimension",
      "La terminal vinculada está en otra dimensión")
    L(f"{M}.no_signal", "No uplink signal: your team needs an Uplink Satellite over this dimension",
      "Sin señal de enlace: tu equipo necesita un satélite de enlace sobre esta dimensión")
    L(f"{M}.wireless_unloaded", "The linked terminal's chunk isn't loaded (an Ender Anchor can keep it loaded)",
      "El chunk de la terminal vinculada no está cargado (un ancla de ender puede mantenerlo cargado)")
    L(f"{M}.wireless_gone", "The linked Storage Terminal is gone", "La terminal de almacenamiento vinculada ya no existe")

    L(f"{M}.station_console_hint", "Sneak-use the station with an empty hand for the Team screen (teams, deorbit)",
      "Usa la estación agachado con la mano vacía para la pantalla de equipo (equipos, desorbitar)")
    L(f"{M}.deorbit_done", "Deorbit command sent", "Orden de desorbitar enviada")
    L(f"{M}.deorbit_gone", "That satellite is no longer in orbit", "Ese satélite ya no está en órbita")
    L(f"{M}.deorbit_not_yours", "That satellite doesn't belong to your team", "Ese satélite no pertenece a tu equipo")
    L(f"{M}.deorbited", "%s deorbited %s '%s' over %s: it burned up on re-entry",
      "%s desorbitó %s «%s» sobre %s: se desintegró al reentrar")
    L(f"{M}.radar_unowned", "This radar has no owner: break it and place it again",
      "Este radar no tiene dueño: rómpelo y colócalo de nuevo")
    L(f"{M}.radar_foreign", "This radar belongs to team %s", "Este radar pertenece al equipo %s")
    L(f"{M}.radar_contact_gone", "That contact is gone", "Ese contacto ya no está")
    L(f"{M}.radar_own", "That satellite is your team's own", "Ese satélite es de tu propio equipo")
    L(f"{M}.radar_already_locked", "Already locked", "Ya está fijado")
    L(f"{M}.radar_not_locked", "Lock the contact first", "Primero fija el contacto")
    L(f"{M}.radar_label", "%s (team %s)", "%s (equipo %s)")
    L(f"{M}.radar_locked", "Radar lock: %s '%s' of team %s", "Radar fijado: %s «%s» del equipo %s")
    L(f"{M}.radar_tracked_warning", "Warning: your %s '%s' is being tracked by a radar of team %s over %s",
      "Aviso: tu %s «%s» está siendo rastreado por un radar del equipo %s sobre %s")
    L(f"{M}.radar_header", "Over %s · team %s · %s/%s FE", "Sobre %s · equipo %s · %s/%s FE")
    L(f"{M}.radar_row_own", "%s '%s' (yours)", "%s «%s» (tuyo)")
    L(f"{M}.radar_row_locked", "%s '%s' · %s", "%s «%s» · %s")
    L(f"{M}.radar_row_unknown", "Unidentified contact #%s", "Contacto no identificado n.º %s")
    L(f"{M}.asat_programmed", "Missile programmed: %s", "Misil programado: %s")
    L(f"{M}.asat_no_designation", "Pick a locked target on this radar's screen first",
      "Primero elige un objetivo fijado en la pantalla de este radar")
    L(f"{M}.asat_disabled", "Anti-Satellite missiles are disabled on this server",
      "Los misiles antisatélite están desactivados en este servidor")
    L(f"{M}.asat_unprogrammed", "The missile has no target: program it at an Orbital Radar",
      "El misil no tiene objetivo: prográmalo en un radar orbital")
    L(f"{M}.asat_target_gone", "The target is no longer in orbit over this dimension",
      "El objetivo ya no está en órbita sobre esta dimensión")
    L(f"{M}.asat_own_team", "You can't fire at your own team's satellites", "No puedes disparar a los satélites de tu propio equipo")
    L(f"{M}.asat_no_lock", "No radar lock: the radar that programmed the missile must be loaded, here, and still locked on",
      "Sin fijación de radar: el radar que programó el misil debe estar cargado, aquí, y mantener la fijación")
    L(f"{M}.asat_foreign_radar", "The missile was programmed at another team's radar",
      "El misil fue programado en el radar de otro equipo")
    L(f"{M}.asat_missed", "The Anti-Satellite missile found no target and burned up",
      "El misil antisatélite no encontró su objetivo y se desintegró")
    L(f"{M}.asat_intercepted_owner", "Your Guardian Satellite '%s' intercepted a missile from team %s aimed at '%s'. The guardian was lost",
      "Tu satélite guardián «%s» interceptó un misil del equipo %s dirigido a «%s». El guardián se perdió")
    L(f"{M}.asat_intercepted_shooter", "Your missile was intercepted by a Guardian Satellite: '%s' of team %s survived",
      "Tu misil fue interceptado por un satélite guardián: «%s» del equipo %s sobrevivió")
    L(f"{M}.asat_destroyed_owner", "Your %s '%s' was shot down by team %s (%s)",
      "Tu %s «%s» fue derribado por el equipo %s (%s)")
    L(f"{M}.asat_destroyed_shooter", "Target destroyed: %s '%s' of team %s", "Objetivo destruido: %s «%s» del equipo %s")

    G = f"gui.{MOD}"
    L(f"{G}.team.title", "Team & Satellites", "Equipo y satélites")
    L(f"{G}.team.solo", "Solo team (just you)", "Equipo individual (solo tú)")
    L(f"{G}.team.team", "Team %s", "Equipo %s")
    L(f"{G}.team.members", "Members: %s", "Miembros: %s")
    L(f"{G}.team.invites", "Invites:", "Invitaciones:")
    L(f"{G}.team.no_invites", "none", "ninguna")
    L(f"{G}.team.join", "Join %s", "Unirse a %s")
    L(f"{G}.team.leave", "Leave", "Salir")
    L(f"{G}.team.name", "Name", "Nombre")
    L(f"{G}.team.name_hint", "new team name", "nombre del equipo")
    L(f"{G}.team.player_hint", "player to invite", "jugador a invitar")
    L(f"{G}.team.create", "Create", "Crear")
    L(f"{G}.team.create_tip", "Found a team with this name (you must be on your own)",
      "Funda un equipo con este nombre (debes estar solo)")
    L(f"{G}.team.invite", "Invite", "Invitar")
    L(f"{G}.team.invite_tip", "Invite the online player with this name", "Invita al jugador conectado con este nombre")
    L(f"{G}.team.online", "Online:", "Conectados:")
    L(f"{G}.team.nobody", "nobody else", "nadie más")
    L(f"{G}.team.satellites", "Satellites in orbit: %s", "Satélites en órbita: %s")
    L(f"{G}.team.no_satellites", "None yet", "Ninguno aún")
    L(f"{G}.team.deorbit", "Deorbit", "Desorbitar")
    L(f"{G}.team.deorbit_confirm", "Sure?", "¿Seguro?")
    L(f"{G}.team.deorbit_tip", "Bring it down: it burns up on re-entry (click twice)",
      "Hazlo bajar: se desintegra al reentrar (dos clics)")
    L(f"{G}.radar.contacts", "Contacts: %s", "Contactos: %s")
    L(f"{G}.radar.none", "Nothing in orbit over this dimension", "Nada en órbita sobre esta dimensión")
    L(f"{G}.radar.track", "Track", "Rastrear")
    L(f"{G}.radar.track_tip", "Track it until the radar locks on (needs power). Its owners will be warned",
      "Rastréalo hasta fijarlo (necesita energía). Sus dueños recibirán un aviso")
    L(f"{G}.radar.stop", "Stop", "Parar")
    L(f"{G}.radar.target", "Target", "Apuntar")
    L(f"{G}.radar.target_tip", "Pick it as the target for Anti-Satellite missiles programmed at this radar",
      "Elígelo como objetivo de los misiles antisatélite programados en este radar")
    L(f"{G}.radar.targeted", "TARGET", "OBJETIVO")
    L(f"{G}.radar.hint", "Right-click the radar with an Anti-Satellite Missile to program it with the target, then launch it from a Launch Pad",
      "Haz clic derecho en el radar con un misil antisatélite para programarle el objetivo y lánzalo desde una plataforma")

    TM = f"{M}.team"
    L(f"{TM}.ok", "Done", "Hecho")
    L(f"{TM}.bad_name", "Team names are 1–24 letters, digits, - or _",
      "Los nombres de equipo tienen de 1 a 24 letras, dígitos, - o _")
    L(f"{TM}.name_taken", "A team with that name already exists", "Ya existe un equipo con ese nombre")
    L(f"{TM}.already_in_team", "You are already on a team: leave it first", "Ya estás en un equipo: sal primero")
    L(f"{TM}.not_in_team", "You are not on a team", "No estás en ningún equipo")
    L(f"{TM}.no_such_team", "There is no team with that name", "No hay ningún equipo con ese nombre")
    L(f"{TM}.not_invited", "You need an invite to join that team", "Necesitas una invitación para unirte a ese equipo")
    L(f"{TM}.self", "You can't invite yourself", "No puedes invitarte a ti mismo")
    L(f"{TM}.target_in_team", "They are already on your team", "Ya está en tu equipo")
    L(f"{TM}.created", "Team %s created", "Equipo %s creado")
    L(f"{TM}.invite_sent", "Invited %s", "Invitaste a %s")
    L(f"{TM}.invited_you", "%s invited you to team %s: %s", "%s te invitó al equipo %s: %s")
    L(f"{TM}.joined", "You joined team %s", "Te uniste al equipo %s")
    L(f"{TM}.member_joined", "%s joined the team", "%s se unió al equipo")
    L(f"{TM}.left", "You left team %s. Its satellites stay with it", "Saliste del equipo %s. Sus satélites se quedan con él")
    L(f"{TM}.member_left", "%s left the team", "%s salió del equipo")
    L(f"{TM}.info_solo", "You are on your own (solo team). Create one with /factoryascent team create <name>",
      "Estás solo (equipo individual). Crea uno con /factoryascent team create <nombre>")
    L(f"{TM}.info", "Team %s: %s", "Equipo %s: %s")
    L(f"{TM}.info_satellites", "Satellites in orbit: %s", "Satélites en órbita: %s")
    L(f"{TM}.none", "No teams yet", "Aún no hay equipos")
    L(f"{TM}.list_header", "%s teams:", "%s equipos:")
    L(f"{TM}.list_entry", "• %s (%s): %s", "• %s (%s): %s")
    L(f"{TM}.no_such_player", "No online player called %s", "No hay ningún jugador conectado llamado %s")
    L(f"{TM}.invited_screen", "…or accept it on the Team screen (sneak-use a Wireless Terminal or a Ground Station)",
      "…o acéptala en la pantalla de equipo (usa agachado una terminal inalámbrica o una estación terrena)")


def code_advancement(ctx, key, parent, icon, frame, en, es):
    """An advancement the mod grants from code (criterion 'done', see OrbitalContent.award)."""
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
    # ---------------------------------------------------------------- launch pad
    ctx.block_model("launch_pad", plate_model("launch_pad_top", "launch_pad_side"))
    ctx.block_model("launch_controller", plate_model("launch_controller_top", "launch_controller_side"))
    ctx.block_model("launch_controller_on", plate_model("launch_controller_top_on", "launch_controller_side_on"))
    ctx.write(A / "blockstates" / "launch_pad.json", {"variants": {"": {"model": f"{MOD}:block/launch_pad"}}})
    ctx.write(A / "blockstates" / "launch_controller.json", {"variants": {
        "powered=false": {"model": f"{MOD}:block/launch_controller"},
        "powered=true": {"model": f"{MOD}:block/launch_controller_on"}}})
    for name in ("launch_pad", "launch_controller"):
        model = json.loads((A / "models" / "block" / f"{name}.json").read_text())
        model["display"] = PLATE_DISPLAY
        ctx.write(A / "models" / "item" / f"{name}.json", model)
        ctx.item_def(name, f"item/{name}")
    for kind in ("survey", "uplink", "guardian"):
        ctx.block_model(f"orbital_rocket_{kind}", rocket_model(kind))
    ctx.block_model("orbital_rocket_asat", rocket_model("asat", body="rocket_body_asat", fin="rocket_fin_asat"))

    # ---------------------------------------------------------------- ground station
    ctx.block_model("ground_station", {"parent": "minecraft:block/block", "textures": STATION_TEX,
                                       "elements": station_base_elements()})
    ctx.block_model("ground_station_dish", {"parent": "minecraft:block/block", "ambientocclusion": False,
                                            "textures": STATION_TEX, "elements": dish_elements()})
    ctx.write(A / "blockstates" / "ground_station.json", {"variants": {"": {"model": f"{MOD}:block/ground_station"}}})
    ctx.write(A / "models" / "item" / "ground_station.json", {
        "parent": "minecraft:block/block", "textures": STATION_TEX,
        "elements": station_base_elements() + dish_elements(),
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.625, 0.625, 0.625]}}})
    ctx.item_def("ground_station", "item/ground_station")

    # ---------------------------------------------------------------- orbital radar
    ctx.block_model("orbital_radar", {"parent": "minecraft:block/block", "textures": RADAR_TEX,
                                      "elements": station_base_elements()})
    ctx.block_model("orbital_radar_antenna", {"parent": "minecraft:block/block", "ambientocclusion": False,
                                              "textures": RADAR_TEX, "elements": radar_antenna_elements()})
    ctx.write(A / "blockstates" / "orbital_radar.json", {"variants": {"": {"model": f"{MOD}:block/orbital_radar"}}})
    ctx.write(A / "models" / "item" / "orbital_radar.json", {
        "parent": "minecraft:block/block", "textures": RADAR_TEX,
        "elements": station_base_elements() + radar_antenna_elements(),
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.625, 0.625, 0.625]}}})
    ctx.item_def("orbital_radar", "item/orbital_radar")

    for item in ("survey_satellite", "uplink_satellite", "guardian_satellite", "asat_missile", "rocket_fuel",
                 "wireless_terminal"):
        ctx.flat_item(item)
    for block in PICKAXE_BLOCKS:
        ctx.loot_self(block)

    # ---------------------------------------------------------------- recipes (Orbital age: titanium, advanced circuits)
    TP, AC = "#c:plates/titanium", "advanced_circuit"
    ctx.shaped("launch_pad", ["PPP", "SBS"], {"P": TP, "S": "#c:plates/steel", "B": "#c:storage_blocks/steel"},
               "launch_pad", count=4)
    ctx.shaped("launch_controller", ["PAP", "CFC", "PRP"], {
        "P": TP, "A": AC, "C": "minecraft:comparator", "F": "advanced_machine_frame", "R": "minecraft:redstone_block"},
        "launch_controller")
    ctx.shaped("ground_station", ["PPP", "ACA", "SFS"], {
        "P": TP, "A": AC, "C": "minecraft:compass", "S": "#c:plates/steel", "F": "advanced_machine_frame"}, "ground_station")
    ctx.shaped("survey_satellite", ["PSP", "COC", "PAP"], {
        "P": TP, "S": "minecraft:spyglass", "C": "solar_panel", "O": "orbital_targeting_core", "A": AC}, "survey_satellite")
    ctx.shaped("uplink_satellite", ["PLP", "COC", "PAP"], {
        "P": TP, "L": "minecraft:lightning_rod", "C": "solar_panel", "O": "orbital_targeting_core", "A": AC}, "uplink_satellite")
    # Orbital warfare: everything needs an Orbital Targeting Core (Precision Assembler, grade 6).
    ctx.shaped("guardian_satellite", ["PSP", "COC", "PAP"], {
        "P": TP, "S": "minecraft:shield", "C": "solar_panel", "O": "orbital_targeting_core", "A": AC}, "guardian_satellite")
    ctx.shaped("orbital_radar", ["PDP", "AOA", "SFS"], {
        "P": TP, "D": "ground_station", "A": AC, "O": "orbital_targeting_core", "S": "#c:plates/steel",
        "F": "advanced_machine_frame"}, "orbital_radar")
    ctx.shaped("asat_missile", [" O ", "PTP", "PRP"], {
        "O": "orbital_targeting_core", "P": TP, "T": "minecraft:tnt", "R": "rocket_fuel"}, "asat_missile")
    ctx.shaped("wireless_terminal", [" L ", "PTP", "PAP"], {
        "L": "minecraft:lightning_rod", "P": TP, "T": "storage_terminal", "A": AC}, "wireless_terminal", category="equipment")
    # Coal dust and blaze powder in the Alloy Smelter: one can is a full launch (4 units, like 4 blaze powder).
    ctx.machine("alloying", "rocket_fuel", [("#c:dusts/coal", 2), ("minecraft:blaze_powder", 1)], "rocket_fuel",
                time=100, min_grade=3)

    # ---------------------------------------------------------------- advancements (branch of age_orbital)
    ctx.advancement("orbital_pad", "age_orbital", "launch_controller", ["launch_controller"], "Launch Control",
                    "Build a Launch Pad: a Launch Controller surrounded by 8 Launch Pad blocks",
                    "Control de lanzamiento",
                    "Construye una plataforma: un controlador de lanzamiento rodeado de 8 bloques de plataforma")
    code_advancement(ctx, "orbital_launch", "orbital_pad", "survey_satellite", "goal",
                     ("Liftoff!", "Fuel a rocket, mount a satellite and launch it into orbit"),
                     ("¡Despegue!", "Carga combustible, monta un satélite y lánzalo a la órbita"))
    code_advancement(ctx, "orbital_uplink", "orbital_launch", "uplink_satellite", "challenge",
                     ("Uplink Online", "Put an Uplink Satellite in orbit: your team has signal in the whole dimension"),
                     ("Enlace en línea", "Pon un satélite de enlace en órbita: tu equipo tiene señal en toda la dimensión"))
    ctx.advancement("orbital_station", "orbital_launch", "ground_station", ["ground_station"], "Ground Control",
                    "Build a Ground Station and use an empty map on it to map the area from orbit",
                    "Control de tierra",
                    "Construye una estación terrena y usa un mapa vacío en ella para cartografiar la zona desde la órbita")
    ctx.advancement("orbital_wireless", "orbital_uplink", "wireless_terminal", ["wireless_terminal"], "Look, No Cables",
                    "Link a Wireless Terminal to a Storage Terminal and open it from anywhere with signal",
                    "Mira, sin cables",
                    "Vincula una terminal inalámbrica a una terminal de almacenamiento y ábrela desde cualquier lugar con señal")

    code_advancement(ctx, "orbital_radar_lock", "orbital_launch", "orbital_radar", "task",
                     ("Contact!", "Lock an Orbital Radar onto another team's satellite"),
                     ("¡Contacto!", "Fija un radar orbital en el satélite de otro equipo"))
    code_advancement(ctx, "orbital_shootdown", "orbital_radar_lock", "asat_missile", "challenge",
                     ("Kessler Syndrome", "Shoot down another team's satellite with an Anti-Satellite Missile"),
                     ("Síndrome de Kessler", "Derriba el satélite de otro equipo con un misil antisatélite"))

    lang(ctx.lang)

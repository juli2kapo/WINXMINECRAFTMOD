#!/usr/bin/env python3
"""Satellites flying in Earth orbit (satellites package): text and the collision advancement.

The Overworld's satellites fly as real bodies in factoryascent:orbit, a few hundred blocks above the
stations, circling over the pad that launched them. A ship that touches one crashes: both explode.
No models or textures here: the bodies reuse the 3D satellite models of tools/features/satellites.py.
"""

MOD = "factoryascent"

LANG = [
    ("entity.factoryascent.orbiting_satellite", "Satellite", "Satélite"),
    ("gui.factoryascent.satellite_tracker.title", "Satellite tracker", "Rastreador de satélites"),
    ("gui.factoryascent.satellite_tracker.none", "No satellites in orbit", "No hay satélites en órbita"),
    ("gui.factoryascent.satellite_tracker.distance", "%s m away, %s m up", "a %s m, %s m de altura"),
    ("gui.factoryascent.satellite_tracker.count", "%s satellites in orbit", "%s satélites en órbita"),
    ("message.factoryascent.satellite_collision_owner",
     "Your %s '%s' was destroyed: %s flew a %s into it!",
     "Tu %s '%s' fue destruido: ¡%s lo embistió con un %s!"),
    ("message.factoryascent.satellite_collision_drifting",
     "Your %s '%s' was destroyed in a collision with a drifting %s.",
     "Tu %s '%s' fue destruido al chocar con un %s a la deriva."),
    ("message.factoryascent.satellite_collision_crew",
     "Collision with the %s '%s'! Your %s breaks apart.",
     "¡Choque con el %s '%s'! Tu %s se hace pedazos."),
]


def generate(ctx):
    for key, en, es in LANG:
        ctx.lang(key, en, es)
    # Code-granted (SatelliteBodies.collide), in the Orbital tree after flying a shuttle to orbit.
    key = "orbital_satellite_collision"
    ctx.write(ctx.DATA / MOD / "advancement" / f"{key}.json", {
        "parent": f"{MOD}:orbital_shuttle_orbit",
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {"icon": {"id": f"{MOD}:uplink_satellite"}, "title": {"translate": f"advancements.{MOD}.{key}.title"},
                    "description": {"translate": f"advancements.{MOD}.{key}.description"}, "frame": "challenge"},
        "requirements": [["done"]]})
    ctx.lang(f"advancements.{MOD}.{key}.title", "Space Junk", "Chatarra espacial")
    ctx.lang(f"advancements.{MOD}.{key}.description",
             "Fly a ship into a satellite in orbit: both blow apart",
             "Estrella una nave contra un satélite en órbita: ambos saltan en pedazos")

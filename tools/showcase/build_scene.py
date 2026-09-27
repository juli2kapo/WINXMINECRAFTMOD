#!/usr/bin/env python3
"""Builds the showcase scene on a running server over RCON (used for screenshots)."""
import socket
import struct

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "showcase"
M = "factoryascent"
TIERS = ["basic", "reinforced", "advanced", "elite", "ultimate"]
MACHINES = ["electric_furnace", "crusher", "metal_press", "alloy_smelter", "assembler",
            "miner", "combustion_generator", "solar_panel", "geothermal_generator", "energy_cell"]


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection((HOST, PORT))
        self.id = 0
        self.send(3, PASSWORD)

    def send(self, kind, body):
        self.id += 1
        data = struct.pack("<ii", self.id, kind) + body.encode() + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)
        size = struct.unpack("<i", self.sock.recv(4))[0]
        resp = b""
        while len(resp) < size:
            resp += self.sock.recv(size - len(resp))
        return resp[8:-2].decode(errors="replace")

    def cmd(self, c):
        out = self.send(2, c)
        if out and any(w in out for w in ("rror", "nknown", "ncorrect", "nvalid", "not loaded")):
            print(c, "->", out)
        return out


r = Rcon()
r.cmd("forceload add -16 -16 31 31")
r.cmd("gamerule advance_time false")
r.cmd("gamerule advance_weather false")
r.cmd("gamerule spawn_mobs false")
r.cmd("gamerule respawn_radius 0")
r.cmd("time set 6000")
r.cmd("weather clear")
r.cmd("setworldspawn 0 -60 0")
r.cmd("fill -12 -60 -4 30 -50 16 air")

# Wall of every machine in every tier, facing the spawn point (north).
for x, m in zip(range(-5, 5), MACHINES):
    for y, t in zip(range(-59, -54), TIERS):
        r.cmd(f"setblock {x} {y} 9 {M}:{t}_{m}[facing=north]")
r.cmd("fill -6 -60 9 5 -60 9 minecraft:polished_deepslate")

# Front row: ores and storage blocks.
row = ["tin_ore", "deepslate_tin_ore", "bauxite_ore", "deepslate_bauxite_ore", "deepslate_titanium_ore",
       "tin_block", "bronze_block", "steel_block", "aluminum_block", "titanium_block", "quantum_alloy_block"]
for x, b in zip(range(-5, 6), row):
    r.cmd(f"setblock {x} -60 6 {M}:{b}")

# Cables (one of each tier, joined) and pipes (one of each tier, between two chests).
for x, t in zip(range(-5, 0), TIERS):
    r.cmd(f"setblock {x} -60 4 {M}:{t}_power_cable")
r.cmd(f"setblock -6 -60 4 {M}:basic_energy_cell[facing=east]")
r.cmd("setblock 1 -60 4 minecraft:chest[facing=north]")
for x, t in zip(range(2, 7), TIERS):
    r.cmd(f"setblock {x} -60 4 {M}:{t}_item_pipe")
r.cmd("setblock 7 -60 4 minecraft:chest[facing=north]")

# A running machine for the GUI screenshot, 20 blocks east.
r.cmd(f"setblock 20 -59 2 {M}:advanced_crusher[facing=north]")
r.cmd(f"setblock 20 -60 2 minecraft:polished_deepslate")
r.cmd('data merge block 20 -59 2 {energy:{energy:60000},inventory:{stacks:['
      '{id:"minecraft:raw_iron",count:64},{},{},{id:"factoryascent:speed_upgrade",count:1},{},{}]}}')
print("scene built")
r.cmd("save-all flush")

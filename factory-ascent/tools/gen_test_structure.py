#!/usr/bin/env python3
"""Writes the GameTest arena: a 9x7x9 box with a stone floor, as a gzipped structure NBT."""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/factoryascent/structure/test_arena.nbt"
DATA_VERSION = 4903  # Minecraft 26.2
SIZE = (9, 7, 9)


def s(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named(tag_id, name, payload):
    return bytes([tag_id]) + s(name) + payload


def compound(entries):  # entries: list of (tag_id, name, payload)
    return b"".join(named(t, n, p) for t, n, p in entries) + b"\x00"


def int_(v):
    return struct.pack(">i", v)


def lst(elem_id, payloads):
    return bytes([elem_id]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


blocks = [compound([(3, "state", int_(0)), (9, "pos", lst(3, [int_(x), int_(0), int_(z)]))])
          for x in range(SIZE[0]) for z in range(SIZE[2])]
root = compound([
    (3, "DataVersion", int_(DATA_VERSION)),
    (9, "size", lst(3, [int_(v) for v in SIZE])),
    (9, "palette", lst(10, [compound([(8, "Name", s("minecraft:stone"))])])),
    (9, "blocks", lst(10, blocks)),
    (9, "entities", lst(10, [])),
])
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(gzip.compress(bytes([10]) + s("") + root))
print("wrote", OUT)

#!/usr/bin/env python3
"""Textures of the interdimensional links (16x16): the Ender Link (obsidian plinth, purpur pillars,
end-stone cap, an ender swirl that spins while it transfers) and the Quantum Entangler (gunmetal
frame with cyan light lines, hex base plates, a translucent interference field that flows while it
transfers). Animated textures are vertical strips with a .png.mcmeta next to them.

Run:  python3 tools/features/xdim_textures.py      (redraws all of them)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily).
"""
import json
import math
import random
from pathlib import Path

TEX = Path(__file__).resolve().parents[2] / "src/main/resources/assets/factoryascent/textures"


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


OBSIDIAN = [rgb(h) for h in ("#3B2A5A", "#2A1C44", "#1C1230", "#120B20", "#0A0612")]
PURPUR = [rgb(h) for h in ("#E2B8E2", "#C690C6", "#A86FA8", "#835283", "#5E3A5E")]
ENDSTONE = [rgb(h) for h in ("#F4F4C4", "#E2E2A8", "#CACA8C", "#A8A870", "#7C7C52")]
VIOLET = [rgb(h) for h in ("#F0D0FF", "#C890FF", "#9A50F0", "#6A28C0", "#3A1080")]
GUN = [rgb(h) for h in ("#8C94A8", "#646C80", "#474E60", "#30364A", "#1C2030")]
CYAN = [rgb(h) for h in ("#E0FFFF", "#80F4FF", "#30C8F0", "#1878B0", "#0A3C60")]
OUT = rgb("#08060E")


class Canvas:
    def __init__(self, h=16, fill=None):
        self.h = h
        self.px = [[fill] * 16 for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < self.h and c is not None:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def bevel(self, x0, y0, x1, y1, light, dark):
        for x in range(x0, x1 + 1):
            self.put(x, y0, light)
            self.put(x, y1, dark)
        for y in range(y0, y1 + 1):
            self.put(x0, y, light)
            self.put(x1, y, dark)

    def save(self, path):
        from PIL import Image
        img = Image.new("RGBA", (16, self.h), (0, 0, 0, 0))
        for y in range(self.h):
            for x in range(16):
                c = self.px[y][x]
                if c is not None:
                    img.putpixel((x, y), c)
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)


def noise(c, pal, seed, lo=1, hi=3):
    r = random.Random(seed)
    for y in range(c.h):
        for x in range(16):
            c.put(x, y, pal[r.randint(lo, hi)])


# ---------------------------------------------------------------- Ender Link

def ender_link_base():
    """The plinth's side: obsidian with a violet rune band."""
    c = Canvas()
    noise(c, OBSIDIAN, 11, 1, 3)
    c.bevel(0, 0, 15, 15, OBSIDIAN[0], OBSIDIAN[4])
    for x in range(2, 14):
        c.put(x, 7, VIOLET[3] if x % 3 else VIOLET[1])
        c.put(x, 8, VIOLET[4])
    for x in (3, 7, 11):
        c.put(x, 6, VIOLET[2])
        c.put(x + 1, 9, VIOLET[2])
    return c


def ender_link_base_top():
    """The plinth's top: obsidian with a ring of inlaid ender eyes."""
    c = Canvas()
    noise(c, OBSIDIAN, 12, 1, 3)
    c.bevel(0, 0, 15, 15, OBSIDIAN[0], OBSIDIAN[4])
    for i in range(32):
        a = i / 32 * math.tau
        c.put(round(7.5 + math.cos(a) * 5.5), round(7.5 + math.sin(a) * 5.5), VIOLET[3])
    for x, y in ((7, 2), (2, 7), (13, 8), (8, 13)):
        c.rect(x - 1, y - 1, x + 1, y + 1, rgb("#1E5A48"))
        c.put(x, y, rgb("#60E0B0"))
    c.rect(6, 6, 9, 9, OBSIDIAN[4])
    return c


def ender_link_pillar():
    """Purpur pillar: fluted, with dark grooves."""
    c = Canvas()
    noise(c, PURPUR, 13, 1, 2)
    for y in range(16):
        c.put(0, y, PURPUR[0])
        c.put(15, y, PURPUR[4])
        for x in (4, 8, 12):
            c.put(x, y, PURPUR[3])
        for x in (5, 9, 13):
            c.put(x, y, PURPUR[0] if y % 4 else PURPUR[1])
    for x in range(16):
        c.put(x, 0, PURPUR[0])
        c.put(x, 15, PURPUR[4])
    return c


def ender_link_cap():
    """The cap ring: end-stone bricks with a purpur inlay."""
    c = Canvas()
    noise(c, ENDSTONE, 14, 1, 2)
    for x in range(16):
        c.put(x, 0, ENDSTONE[0])
        c.put(x, 7, ENDSTONE[4])
        c.put(x, 8, ENDSTONE[0])
        c.put(x, 15, ENDSTONE[4])
    for y in range(8):
        c.put(7, y, ENDSTONE[4])
        c.put(8, y, ENDSTONE[0])
    for y in range(8, 16):
        c.put(3, y, ENDSTONE[4])
        c.put(12, y, ENDSTONE[4])
    for x in range(5, 11):
        c.put(x, 11, PURPUR[2])
    return c


def swirl_frame(t, palette, bright, transparent=False):
    """One frame of a spiral: two arms turning with t (0..1)."""
    c = Canvas(fill=None if transparent else palette[4])
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r > 8.2:
                continue
            ang = math.atan2(dy, dx)
            v = math.sin(2 * ang + r * 0.9 - t * math.tau)
            v = (v + 1) / 2 * max(0.0, 1 - r / 9)
            v = v * bright + (0.25 if r < 2 else 0)
            idx = 4 - min(4, int(v * 5))
            col = palette[idx]
            if transparent:
                col = (col[0], col[1], col[2], int(40 + 200 * min(1, v)))
            c.put(x, y, col)
    return c


def strip(frames):
    out = Canvas(h=16 * len(frames))
    for i, f in enumerate(frames):
        for y in range(16):
            for x in range(16):
                out.px[i * 16 + y][x] = f.px[y][x]
    return out


def ender_link_swirl():
    return swirl_frame(0.0, VIOLET, 0.45)


def ender_link_swirl_active():
    return strip([swirl_frame(i / 8, VIOLET, 1.0) for i in range(8)])


# ---------------------------------------------------------------- Quantum Entangler

def quantum_frame():
    """Gunmetal frame bar with a cyan light line down the middle."""
    c = Canvas()
    noise(c, GUN, 21, 1, 2)
    c.bevel(0, 0, 15, 15, GUN[0], GUN[4])
    for y in range(1, 15):
        c.put(7, y, CYAN[2])
        c.put(8, y, CYAN[3])
    for y in (3, 12):
        c.rect(5, y, 10, y, GUN[3])
        c.put(7, y, CYAN[0])
        c.put(8, y, CYAN[1])
    return c


def quantum_base():
    """Base/top plates: a hex grid with cyan nodes."""
    c = Canvas()
    noise(c, GUN, 22, 2, 3)
    c.bevel(0, 0, 15, 15, GUN[0], GUN[4])
    for y in range(1, 15):
        for x in range(1, 15):
            if (x + (y // 4) * 2) % 4 == 0 and y % 4 != 0:
                c.put(x, y, GUN[4])
            if y % 4 == 0:
                c.put(x, y, GUN[4] if x % 4 else CYAN[3])
    for x, y in ((4, 4), (12, 4), (8, 8), (4, 12), (12, 12)):
        c.put(x, y, CYAN[1])
    return c


def quantum_top():
    """The top emitter: a cyan lens in a gunmetal ring."""
    c = quantum_base()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 5.2:
                c.put(x, y, GUN[3] if r > 4.2 else mix(CYAN[1], CYAN[4], r / 4.2))
    c.put(6, 6, CYAN[0])
    c.put(7, 6, CYAN[0])
    return c


def field_frame(t, bright):
    """Translucent interference field: diagonal cyan wave lines that drift with t."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = math.sin((x + y) * 0.8 - t * math.tau) * math.sin((x - y) * 0.5 + t * math.tau * 2)
            v = (v + 1) / 2
            a = int(25 + 150 * v ** 3 * bright)
            col = mix(CYAN[3], CYAN[0], v)
            c.put(x, y, (col[0], col[1], col[2], a))
    # a faint edge so the pane reads as glass
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            c.put(x, y, (CYAN[2][0], CYAN[2][1], CYAN[2][2], 120))
    return c


def quantum_field():
    return field_frame(0.0, 0.35)


def quantum_field_active():
    return strip([field_frame(i / 8, 1.0) for i in range(8)])


BLOCKS = {
    "ender_link_base": ender_link_base, "ender_link_base_top": ender_link_base_top,
    "ender_link_pillar": ender_link_pillar, "ender_link_cap": ender_link_cap,
    "ender_link_swirl": ender_link_swirl, "ender_link_swirl_active": ender_link_swirl_active,
    "quantum_entangler_frame": quantum_frame, "quantum_entangler_base": quantum_base,
    "quantum_entangler_top": quantum_top, "quantum_entangler_field": quantum_field,
    "quantum_entangler_field_active": quantum_field_active,
}
ANIMATED = {"ender_link_swirl_active": 2, "quantum_entangler_field_active": 2}


def draw(only_missing):
    for name, fn in BLOCKS.items():
        path = TEX / "block" / f"{name}.png"
        if not (only_missing and path.exists()):
            fn().save(path)
        if name in ANIMATED:
            meta = TEX / "block" / f"{name}.png.mcmeta"
            text = json.dumps({"animation": {"frametime": ANIMATED[name], "interpolate": True}}, indent=2) + "\n"
            if not meta.exists() or meta.read_text() != text:
                meta.write_text(text)


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    draw(only_missing=False)
    print(f"wrote {len(BLOCKS)} xdim textures")

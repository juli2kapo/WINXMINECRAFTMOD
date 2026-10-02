#!/usr/bin/env python3
"""Textures of the capsule machines and the phone dock (16x16):

- Size Chamber: a steel plinth with a cyan band and a control panel, a glass tube with size marks
  (a glowing, scanning variant while it works) and an emitter cap.
- Mob Releaser: a squat magenta-trimmed launcher with a 3x3 grid of capsule tubes on top (lit while
  loaded) and a launch arrow on its front.
- Phone Dock: a white desk computer: body, monitor (dark / showing the link list), the cradle.
- Link Card: a teal data card with a gold chip, blank and written (lit traces).

Run:  python3 tools/features/capsule_textures.py      (redraws all of them, and a preview sheet)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily).
"""
import json
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/factoryascent/textures"
PREVIEW = ROOT / "tools/capsule_preview.png"


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


STEEL = [rgb(h) for h in ("#C8CED6", "#A4ACB8", "#848C9A", "#646C7A", "#444A56")]
CYAN = [rgb(h) for h in ("#E0FFFF", "#90F0FF", "#40C8E0", "#1890A8", "#0A5060")]
MAGENTA = [rgb(h) for h in ("#FFD0FF", "#F090F0", "#C050D0", "#8A2A9A", "#4A1258")]
WHITE = [rgb(h) for h in ("#F4F6F8", "#E0E4EA", "#C8CED6", "#A8B0BC", "#7C8492")]
DARK = rgb("#14181E")
GOLD = [rgb(h) for h in ("#FFF0A0", "#F0C840", "#C89820", "#8A6410")]
TEAL = [rgb(h) for h in ("#7AD8D0", "#48B0A8", "#2C847E", "#1A5A56", "#0C3432")]


class Canvas:
    def __init__(self, h=16, fill=None):
        self.h = h
        self.px = [[fill] * 16 for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < self.h and c is not None:
            self.px[y][x] = c

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
        return img


def noise(c, pal, seed, lo=1, hi=2, y0=0, y1=None):
    r = random.Random(seed)
    for y in range(y0, (y1 if y1 is not None else c.h - 1) + 1):
        for x in range(16):
            c.put(x, y, pal[r.randint(lo, hi)])


def strip(frames):
    c = Canvas(16 * len(frames))
    for i, f in enumerate(frames):
        for y in range(16):
            for x in range(16):
                c.px[i * 16 + y][x] = f.px[y][x]
    return c


# ---------------------------------------------------------------- Size Chamber

def size_chamber_base():
    """Plinth side: brushed steel, a cyan light band, corner bolts."""
    c = Canvas()
    noise(c, STEEL, 21)
    c.bevel(0, 0, 15, 15, STEEL[0], STEEL[4])
    for x in range(1, 15):
        c.put(x, 9, CYAN[2] if x % 4 else CYAN[1])
        c.put(x, 10, CYAN[4])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.put(x, y, STEEL[4])
        c.put(x - 1, y - 1, STEEL[0])
    return c


def size_chamber_front():
    """Plinth front: the control panel (a small screen with a size gauge and two buttons)."""
    c = size_chamber_base()
    c.rect(3, 2, 12, 7, DARK)
    c.bevel(2, 1, 13, 8, STEEL[4], STEEL[0])
    for x in range(4, 12):
        c.put(x, 6, CYAN[3])
    for i, h in enumerate((1, 2, 3, 2, 4, 3, 1, 2)):
        for y in range(6 - h, 6):
            c.put(4 + i, y, CYAN[1] if i == 4 else CYAN[2])
    c.put(5, 12, rgb("#E05050"))
    c.put(10, 12, rgb("#50E070"))
    return c


def size_chamber_glass(active=False, phase=0.0):
    """The tube: tinted glass with size ticks along one edge; scanning rings while it works."""
    c = Canvas()
    edge = CYAN[3] if not active else CYAN[2]
    for y in range(16):
        for x in range(16):
            c.put(x, y, (120, 220, 240, 70) if not active else (110, 230, 255, 110))
    for y in range(16):
        c.put(0, y, edge)
        c.put(15, y, edge)
        if y % 3 == 0:
            c.put(1, y, CYAN[1])
            if y % 6 == 0:
                c.put(2, y, CYAN[1])
    for y in range(2, 14):
        c.put(12, y, (230, 255, 255, 120))
    if active:
        ring = int(phase * 16) % 16
        for x in range(1, 15):
            c.put(x, ring, (200, 255, 255, 200))
            c.put(x, (ring + 8) % 16, (150, 240, 255, 150))
    return c


def size_chamber_glass_active():
    return strip([size_chamber_glass(True, i / 8) for i in range(8)])


def size_chamber_top():
    """The emitter cap: steel with a cyan lens ring."""
    c = Canvas()
    noise(c, STEEL, 23)
    c.bevel(0, 0, 15, 15, STEEL[0], STEEL[4])
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 3.5 <= d < 5.2:
                c.put(x, y, CYAN[2] if d < 4.4 else CYAN[3])
            elif d < 3.5:
                c.put(x, y, CYAN[1] if d < 1.5 else CYAN[4])
    return c


# ---------------------------------------------------------------- Mob Releaser

def mob_releaser_side():
    """Launcher side: dark steel with a magenta stripe and vents."""
    c = Canvas()
    noise(c, STEEL, 31, 2, 3)
    c.bevel(0, 0, 15, 15, STEEL[1], STEEL[4])
    for x in range(1, 15):
        c.put(x, 3, MAGENTA[2])
        c.put(x, 4, MAGENTA[3])
    for y in (8, 10, 12):
        for x in range(3, 13):
            c.put(x, y, STEEL[4])
            c.put(x, y + 1, STEEL[1])
    return c


def mob_releaser_front():
    """Front: the launch arrow and a redstone port."""
    c = mob_releaser_side()
    c.rect(3, 7, 12, 13, STEEL[3])
    for y in range(8, 13):
        c.put(7, y, MAGENTA[1])
        c.put(8, y, MAGENTA[1])
    for i in range(3):
        c.put(7 - i, 9 + i, MAGENTA[2])
        c.put(8 + i, 9 + i, MAGENTA[2])
    c.put(7, 8, MAGENTA[0])
    c.put(8, 8, MAGENTA[0])
    c.put(13, 13, rgb("#C02020"))
    return c


def mob_releaser_top(active=False):
    """Top: nine capsule tubes; lit magenta inside while capsules are loaded."""
    c = Canvas()
    noise(c, STEEL, 33, 2, 3)
    c.bevel(0, 0, 15, 15, STEEL[1], STEEL[4])
    for i in range(3):
        for j in range(3):
            x0, y0 = 1 + i * 5, 1 + j * 5
            c.rect(x0, y0, x0 + 3, y0 + 3, STEEL[4])
            c.rect(x0 + 1, y0 + 1, x0 + 2, y0 + 2, MAGENTA[1] if active else DARK)
            if active:
                c.put(x0 + 1, y0 + 1, MAGENTA[0])
            c.put(x0, y0, STEEL[0])
    return c


# ---------------------------------------------------------------- Phone Dock

def phone_dock_body():
    """Desk computer body: white plastic with a teal trim line and a vent."""
    c = Canvas()
    noise(c, WHITE, 41, 1, 2)
    c.bevel(0, 0, 15, 15, WHITE[0], WHITE[3])
    for x in range(1, 15):
        c.put(x, 12, TEAL[1])
    for x in range(10, 14):
        c.put(x, 4, WHITE[4])
        c.put(x, 6, WHITE[4])
    return c


def phone_dock_screen(on=False):
    """The monitor: dark glass, or the link list (coloured rows with little remove crosses)."""
    c = Canvas()
    c.rect(0, 0, 15, 15, WHITE[2])
    c.bevel(0, 0, 15, 15, WHITE[1], WHITE[4])
    c.rect(1, 1, 14, 12, rgb("#0E141C"))
    if on:
        cols = [TEAL[0], rgb("#60E070"), rgb("#F0D050"), rgb("#C090FF")]
        for i in range(5):
            y = 2 + i * 2
            c.put(2, y, cols[i % 4])
            for x in range(4, 4 + (7 - (i * 3) % 4)):
                c.put(x, y, rgb("#D8E4F0"))
            c.put(12, y, rgb("#F06060"))
        c.rect(1, 12, 14, 12, TEAL[2])
    else:
        c.put(3, 3, rgb("#2A3444"))
        c.put(4, 2, rgb("#2A3444"))
    c.rect(6, 13, 9, 14, WHITE[3])
    c.put(8, 13, rgb("#60E070") if on else WHITE[4])
    return c


def phone_dock_cradle():
    """The phone cradle: a teal rubber pad with a gold charging contact."""
    c = Canvas()
    noise(c, TEAL, 43, 2, 3)
    c.bevel(0, 0, 15, 15, TEAL[1], TEAL[4])
    c.rect(6, 12, 9, 13, GOLD[1])
    c.put(6, 12, GOLD[0])
    for y in range(2, 10, 2):
        for x in range(3, 13):
            c.put(x, y, TEAL[3])
    return c


def phone_dock_top():
    """Desk top: white with the card reader slot."""
    c = phone_dock_body()
    c.rect(3, 6, 12, 7, DARK)
    c.put(3, 5, WHITE[4])
    c.put(12, 8, WHITE[0])
    return c


# ---------------------------------------------------------------- Link Card

def link_card(written=False):
    c = Canvas()
    c.rect(1, 3, 14, 12, TEAL[2])
    c.bevel(1, 3, 14, 12, TEAL[0], TEAL[4])
    for x in (1, 14):
        for y in (3, 12):
            c.put(x, y, None)
    # gold chip
    c.rect(3, 6, 6, 9, GOLD[2])
    c.bevel(3, 6, 6, 9, GOLD[0], GOLD[3])
    c.put(4, 7, GOLD[1])
    c.put(5, 8, GOLD[1])
    # traces / label lines
    line = rgb("#E8FFF8") if written else TEAL[3]
    for x in range(8, 13):
        c.put(x, 6, line)
    for x in range(8, 12):
        c.put(x, 8, line)
    for x in range(8, 13):
        c.put(x, 10, TEAL[3] if not written else rgb("#9AF0FF"))
    if written:
        c.put(12, 4, rgb("#60FF90"))
        c.put(7, 7, rgb("#9AF0FF"))
        c.put(7, 8, rgb("#9AF0FF"))
    return c


BLOCKS = {
    "size_chamber_base": size_chamber_base, "size_chamber_front": size_chamber_front,
    "size_chamber_glass": size_chamber_glass, "size_chamber_glass_active": size_chamber_glass_active,
    "size_chamber_top": size_chamber_top,
    "mob_releaser_side": mob_releaser_side, "mob_releaser_front": mob_releaser_front,
    "mob_releaser_top": mob_releaser_top, "mob_releaser_top_active": lambda: mob_releaser_top(True),
    "phone_dock_body": phone_dock_body, "phone_dock_screen": phone_dock_screen,
    "phone_dock_screen_on": lambda: phone_dock_screen(True), "phone_dock_cradle": phone_dock_cradle,
    "phone_dock_top": phone_dock_top,
}
ITEMS = {"link_card": link_card, "link_card_written": lambda: link_card(True)}
ANIMATED = {"size_chamber_glass_active": 2}


def draw(only_missing):
    drawn = []
    for folder, table in (("block", BLOCKS), ("item", ITEMS)):
        for name, fn in table.items():
            path = TEX / folder / f"{name}.png"
            if not (only_missing and path.exists()):
                drawn.append((name, fn().save(path)))
            if name in ANIMATED:
                meta = TEX / folder / f"{name}.png.mcmeta"
                text = json.dumps({"animation": {"frametime": ANIMATED[name], "interpolate": True}}, indent=2) + "\n"
                if not meta.exists() or meta.read_text() != text:
                    meta.write_text(text)
    return drawn


def preview(drawn):
    from PIL import Image
    scale = 8
    sheet = Image.new("RGBA", (len(drawn) * (16 * scale + 8) + 8, 16 * scale + 16), (40, 44, 52, 255))
    for i, (name, img) in enumerate(drawn):
        frame = img.crop((0, 0, 16, 16)).resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(frame, (8 + i * (16 * scale + 8), 8))
    sheet.save(PREVIEW)


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    preview(draw(only_missing=False))
    print(f"wrote {len(BLOCKS) + len(ITEMS)} capsule/dock textures")

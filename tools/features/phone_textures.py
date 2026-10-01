#!/usr/bin/env python3
"""Textures of the Factory Phone: the inventory icon (16x16), the 3D model's body, bezel and screen
(off / glowing), the 24x24 home-screen app icons and the 132x196 wallpapers.

Run:  python3 tools/features/phone_textures.py      (redraws all of them, and a preview sheet)

gen_resources.py loads every tools/features/*.py, so this module also has a generate(ctx) that only
draws textures that are missing (Pillow is imported lazily, so resources still generate without it
once the PNGs exist).
"""
import math
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/factoryascent/textures"
PREVIEW = ROOT / "tools/phone_preview.png"


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


class Canvas:
    def __init__(self, w, h, fill=None):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            if len(c) == 4 and c[3] < 255 and self.px[y][x] is not None:
                a = c[3] / 255
                base = self.px[y][x]
                c = tuple(round(base[i] * (1 - a) + c[i] * a) for i in range(3)) + (max(base[3], c[3]),)
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else None

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.put(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)

    def disc(self, cx, cy, r, c):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.put(x, y, c)

    def ring(self, cx, cy, r0, r1, c):
        for y in range(int(cy - r1 - 1), int(cy + r1 + 2)):
            for x in range(int(cx - r1 - 1), int(cx + r1 + 2)):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if r0 <= d <= r1:
                    self.put(x, y, c)

    def save(self, path):
        from PIL import Image
        img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        return img


OUT = rgb("#08090D")
BODY = [rgb(h) for h in ("#6A7488", "#4A5262", "#353B48", "#252A34", "#171A21")]
SCREEN_OFF = [rgb(h) for h in ("#2A3240", "#1A2028", "#10141A")]


# ============================================================ item icon + 3D model textures

def phone_icon():
    """Inventory icon: a graphite phone, slightly tilted feel, with a lit home screen of tiny icons."""
    c = Canvas(16, 16)
    # body x 4..11, y 1..14
    c.rect(4, 1, 11, 14, OUT)
    c.rect(5, 2, 10, 13, BODY[2])
    c.rect(5, 2, 10, 2, BODY[1])
    c.rect(5, 2, 5, 13, BODY[1])
    # screen x 5..10 y 3..12
    for y in range(3, 12):
        for x in range(5, 11):
            c.put(x, y, mix(rgb("#2C5AA8"), rgb("#14284E"), (y - 3) / 8))
    icons = [rgb("#4CD07A"), rgb("#3FB5C8"), rgb("#F0A040"), rgb("#E8C040"), rgb("#B070F0"), rgb("#9AA4BC")]
    k = 0
    for y in (6, 8, 10):
        for x in (6, 9):
            c.put(x, y, icons[k % len(icons)])
            k += 1
    c.put(7, 4, rgb("#FFFFFF"))
    c.put(8, 4, rgb("#C8D8FF"))
    # home button
    c.put(7, 13, BODY[0])
    c.put(8, 13, BODY[0])
    # side button highlight
    c.put(11, 4, BODY[1])
    c.put(11, 5, BODY[1])
    return c


def phone_back():
    """The back of the phone: brushed graphite, a camera bump and a small gear logo."""
    rng = random.Random(7)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.put(x, y, BODY[2] if rng.random() > 0.15 else BODY[3])
    # (the model shows u 5..11, v 3..15)
    c.rect(6, 4, 8, 6, OUT)
    c.put(7, 5, rgb("#3A5A9A"))
    c.put(9, 4, rgb("#E8E0C0"))
    # gear logo
    for x, y in ((8, 10), (7, 10), (9, 10), (8, 9), (8, 11), (7, 9), (9, 11), (7, 11), (9, 9)):
        c.put(x, y, BODY[1])
    c.put(8, 10, BODY[3])
    for y in range(16):
        c.put(5, y, BODY[1])
        c.put(10, y, BODY[3])
    return c


def phone_side():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.put(x, y, BODY[1] if (x + y) % 5 else BODY[2])
    return c


def phone_front():
    """The bezel around the screen (the screen itself is a separate quad)."""
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, BODY[4])
    c.rect(5, 3, 10, 14, BODY[4])
    for x in range(5, 11):
        c.put(x, 3, BODY[2])
    c.put(7, 3, OUT)
    c.put(8, 3, OUT)
    c.put(7, 14, BODY[1])
    c.put(8, 14, BODY[1])
    return c


def screen_off():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(SCREEN_OFF[0], SCREEN_OFF[2], y / 15))
    for i in range(6):
        c.put(10 - i, 2 + i, rgb("#3A4458"))
    return c


def screen_on():
    """The glowing screen: a blue home screen with a clock bar and a grid of app icons."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.put(x, y, mix(rgb("#3A78D8"), rgb("#1A3470"), y / 15))
    for x in range(16):
        c.put(x, 0, rgb("#0A1428"))
    c.rect(5, 2, 10, 3, rgb("#FFFFFF"))
    icons = [rgb("#4CD07A"), rgb("#3FC8D8"), rgb("#5A8CF0"), rgb("#F0A040"), rgb("#F0D040"), rgb("#B070F0"),
             rgb("#F0C060"), rgb("#B0B8C8"), rgb("#4CD07A")]
    k = 0
    for gy in (6, 10, 13):
        for gx in (2, 7, 12):
            c.rect(gx, gy, gx + 2, gy + 1 if gy == 13 else gy + 2, icons[k])
            k += 1
    return c


# ============================================================ app icons (24x24)

def tile(top, bottom):
    """The rounded-square icon background with a vertical gradient and a top highlight."""
    c = Canvas(24, 24)
    for y in range(24):
        for x in range(24):
            corner = min(x, 23 - x), min(y, 23 - y)
            if corner[0] + corner[1] < 3 and min(corner) < 2:
                if corner[0] + corner[1] < 2:
                    continue
            c.put(x, y, mix(top, bottom, y / 23))
    for x in range(3, 21):
        c.put(x, 1, mix(top, rgb("#FFFFFF"), 0.35))
    for x in range(3, 21):
        c.put(x, 22, mix(bottom, OUT, 0.35))
    return c


def icon_map():
    c = tile(rgb("#3CC07A"), rgb("#1E7046"))
    paper = [rgb("#F4ECD0"), rgb("#E0D4B0"), rgb("#C8B890")]
    # a folded map in three panels
    for i, x0 in enumerate((4, 9, 14)):
        dy = 1 if i % 2 else 0
        for y in range(6 + dy, 18 + dy):
            for x in range(x0, x0 + 6):
                c.put(x, y, paper[i % 2])
        c.line(x0, 6 + dy, x0, 17 + dy, paper[2])
    # terrain: a lake, forest, a road
    c.disc(8, 13, 2.2, rgb("#4A90D8"))
    for x, y in ((13, 9), (15, 10), (12, 11), (16, 12)):
        c.put(x, y, rgb("#3A9A4A"))
        c.put(x, y + 1, rgb("#2A7A38"))
    c.line(5, 9, 18, 16, rgb("#C07040"))
    # red pin
    c.disc(15, 7, 2, rgb("#E83C3C"))
    c.put(15, 10, rgb("#A02020"))
    c.put(15, 9, rgb("#C02828"))
    c.put(14, 6, rgb("#FFB0B0"))
    return c


def icon_storage():
    c = tile(rgb("#3FC0D0"), rgb("#1A6A78"))
    # a storage drive with four cells
    c.rect(4, 5, 19, 19, OUT)
    c.rect(5, 6, 18, 18, rgb("#5A6478"))
    c.rect(5, 6, 18, 6, rgb("#8A94A8"))
    colors = [rgb("#4CD07A"), rgb("#F0C040"), rgb("#E06060"), rgb("#60A0F0")]
    for i, (x, y) in enumerate(((6, 8), (12, 8), (6, 13), (12, 13))):
        c.rect(x, y, x + 4, y + 3, rgb("#2A3040"))
        c.rect(x + 1, y + 1, x + 3, y + 2, colors[i])
        c.put(x + 4, y, rgb("#9AF0FF"))
    c.rect(5, 18, 18, 18, rgb("#3A4252"))
    return c


def icon_team():
    c = tile(rgb("#5A8CF0"), rgb("#2A4AA0"))
    skin = rgb("#F2D2B0")
    # two people
    for cx, shirt in ((8, rgb("#F0C040")), (15, rgb("#E8E8F0"))):
        c.disc(cx, 9, 2.6, skin)
        c.put(cx - 1, 8, rgb("#5A3A20"))
        c.put(cx + 1, 8, rgb("#5A3A20"))
        for y in range(13, 19):
            w = 3 + (y - 13) // 2
            for x in range(cx - w, cx + w + 1):
                c.put(x, y, shirt)
    # speech bubble
    c.rect(13, 3, 20, 7, rgb("#FFFFFF"))
    c.put(14, 8, rgb("#FFFFFF"))
    c.put(15, 5, rgb("#5A8CF0"))
    c.put(17, 5, rgb("#5A8CF0"))
    c.put(19, 5, rgb("#5A8CF0"))
    return c


def icon_machines():
    c = tile(rgb("#F09A40"), rgb("#9A4A14"))
    steel = [rgb("#E8ECF2"), rgb("#B8C0CC"), rgb("#7A8494")]
    cx, cy = 11.5, 12.5
    for y in range(24):
        for x in range(24):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy) ** 0.5
            a = math.atan2(dy, dx)
            tooth = (math.cos(a * 8) > 0.3)
            if d < 4:
                continue
            if d <= 6.5 or (d <= 8.5 and tooth):
                c.put(x, y, steel[0] if dy < -2 else steel[1] if dy < 3 else steel[2])
    c.ring(cx, cy, 2.6, 3.6, steel[2])
    # an alert dot
    c.disc(18.5, 5.5, 2.6, rgb("#E83C3C"))
    c.put(18, 4, rgb("#FFFFFF"))
    c.put(18, 5, rgb("#FFFFFF"))
    c.put(18, 7, rgb("#FFFFFF"))
    return c


def icon_power():
    c = tile(rgb("#F0D040"), rgb("#A07810"))
    bolt = [(15, 2), (5, 13), (11, 13), (8, 22), (19, 9), (13, 9), (17, 2)]
    # fill the bolt polygon by scanline
    for y in range(24):
        xs = []
        n = len(bolt)
        for i in range(n):
            (x0, y0), (x1, y1) = bolt[i], bolt[(i + 1) % n]
            if (y0 <= y < y1) or (y1 <= y < y0):
                xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for x in range(math.ceil(xs[i]), math.floor(xs[i + 1]) + 1):
                c.put(x, y, rgb("#FFFFFF") if x < 11 else rgb("#FFF4C0"))
    for i in range(len(bolt)):
        (x0, y0), (x1, y1) = bolt[i], bolt[(i + 1) % len(bolt)]
        c.line(x0, y0, x1, y1, rgb("#7A5408"))
    return c


def icon_recall():
    c = tile(rgb("#A060E8"), rgb("#4A1E88"))
    # an ender pearl inside a swirl
    for i in range(60):
        a = i * 0.25
        r = 3 + i * 0.13
        x, y = 12 + math.cos(a) * r, 12 + math.sin(a) * r
        c.put(round(x), round(y), rgb("#E0B8FF") if i % 3 else rgb("#C080FF"))
    c.disc(12, 12, 3.6, rgb("#1E5A50"))
    c.disc(12, 12, 2.4, rgb("#2E8A7A"))
    c.put(11, 11, rgb("#9AF0D8"))
    c.put(12, 12, rgb("#0A2A24"))
    return c


def icon_dyson():
    c = tile(rgb("#2A3260"), rgb("#0E1230"))
    c.disc(12, 12, 4.2, rgb("#FFB020"))
    c.disc(12, 12, 2.8, rgb("#FFE070"))
    c.put(11, 11, rgb("#FFFFFF"))
    # the square lattice around it
    gold = rgb("#E8C060")
    for x in range(4, 21):
        if x % 3 != 2:
            c.put(x, 4, gold)
            c.put(x, 20, gold)
    for y in range(4, 21):
        if y % 3 != 2:
            c.put(4, y, gold)
            c.put(20, y, gold)
    for x, y in ((4, 4), (20, 4), (4, 20), (20, 20)):
        c.put(x, y, rgb("#FFFFFF"))
    return c


def icon_settings():
    c = tile(rgb("#9AA4BC"), rgb("#4A5268"))
    # three sliders
    for i, (y, knob) in enumerate(((7, 15), (12, 8), (17, 13))):
        c.rect(5, y, 18, y, rgb("#2A3040"))
        c.rect(5, y, knob, y, rgb("#4CD07A") if i == 1 else rgb("#E8ECF2"))
        c.rect(knob - 1, y - 2, knob + 1, y + 2, rgb("#FFFFFF"))
        c.put(knob + 1, y + 2, rgb("#7A8494"))
    return c


APP_ICONS = {"map": icon_map, "storage": icon_storage, "team": icon_team, "machines": icon_machines,
             "power": icon_power, "recall": icon_recall, "dyson": icon_dyson, "settings": icon_settings}


# ============================================================ wallpapers (132x196)

WW, WH = 132, 196


def wall_circuit():
    """Deep blue with glowing circuit traces and pads."""
    rng = random.Random(11)
    c = Canvas(WW, WH)
    for y in range(WH):
        for x in range(WW):
            c.put(x, y, mix(rgb("#0E2350"), rgb("#050A1C"), y / WH))
    trace = rgb("#2A6AC8")
    glow = rgb("#7AC8FF")
    for _ in range(26):
        x, y = rng.randrange(4, WW - 4), rng.randrange(4, WH - 4)
        length = rng.randrange(20, 70)
        dx, dy = rng.choice(((1, 0), (0, 1), (1, 1), (-1, 1)))
        for i in range(length):
            if rng.random() < 0.06:
                dx, dy = rng.choice(((1, 0), (0, 1), (1, 1)))
            x, y = x + dx, y + dy
            if not (0 <= x < WW and 0 <= y < WH):
                break
            c.put(x, y, trace)
        c.rect(x - 1, y - 1, x + 1, y + 1, glow)
    for _ in range(18):
        x, y = rng.randrange(WW), rng.randrange(WH)
        c.put(x, y, glow)
    return c


def wall_orbit():
    """Space: stars, the planet's limb at the bottom and a satellite with solar wings."""
    rng = random.Random(23)
    c = Canvas(WW, WH)
    for y in range(WH):
        for x in range(WW):
            c.put(x, y, mix(rgb("#05060E"), rgb("#101836"), y / WH))
    for _ in range(140):
        x, y = rng.randrange(WW), rng.randrange(WH)
        b = rng.randrange(120, 255)
        c.put(x, y, (b, b, min(255, b + 30), 255))
    # planet
    pcx, pcy, r = WW // 2, WH + 70, 130
    for y in range(WH - 70, WH):
        for x in range(WW):
            d = ((x - pcx) ** 2 + (y - pcy) ** 2) ** 0.5
            if d <= r:
                t = (r - d) / 40
                base = mix(rgb("#3A7AD0"), rgb("#1A3A80"), t)
                if (x * 7 + y * 3) % 23 < 6 and d < r - 3:
                    base = mix(base, rgb("#3C9A50"), 0.6)
                c.put(x, y, base)
            elif d <= r + 3:
                c.put(x, y, mix(rgb("#8AD0FF"), rgb("#101836"), (d - r) / 3))
    # satellite
    sx, sy = 84, 52
    c.rect(sx - 3, sy - 3, sx + 3, sy + 3, rgb("#D0C090"))
    c.rect(sx - 2, sy - 2, sx + 2, sy + 2, rgb("#B0A070"))
    for wx in (sx - 18, sx + 5):
        c.rect(wx, sy - 2, wx + 12, sy + 2, rgb("#2A3E9A"))
        for k in range(0, 13, 3):
            c.put(wx + k, sy - 2, rgb("#5A7AE0"))
    c.line(sx, sy - 3, sx, sy - 8, rgb("#D0D0D0"))
    c.put(sx, sy - 9, rgb("#FF6060"))
    return c


def wall_sunset():
    """An industrial skyline (chimneys, a cooling tower, a rocket on its pad) against a sunset."""
    c = Canvas(WW, WH)
    stops = [(0, rgb("#1A1440")), (0.45, rgb("#8A2E6A")), (0.7, rgb("#F07A3A")), (0.85, rgb("#FFC060"))]
    for y in range(WH):
        t = y / WH
        for i in range(len(stops) - 1):
            if stops[i][0] <= t <= stops[i + 1][0]:
                col = mix(stops[i][1], stops[i + 1][1], (t - stops[i][0]) / (stops[i + 1][0] - stops[i][0]))
                break
        else:
            col = stops[-1][1]
        for x in range(WW):
            c.put(x, y, col)
    c.disc(WW // 2 + 18, 150, 16, rgb("#FFE6A0"))
    sil = rgb("#140A1E")
    ground = 168
    c.rect(0, ground, WW - 1, WH - 1, sil)
    for x0, w, h in ((4, 14, 30), (20, 10, 18), (32, 22, 40), (58, 12, 24), (96, 18, 34), (116, 14, 22)):
        c.rect(x0, ground - h, x0 + w, ground, sil)
    # chimneys with smoke
    for x0, h in ((40, 70), (48, 58), (100, 64)):
        c.rect(x0, ground - h, x0 + 3, ground, sil)
        for k in range(10):
            c.disc(x0 + 2 + k * 1.5, ground - h - 3 - k * 4, 2 + k * 0.3, rgb("#3A2A4A", 90))
    # cooling tower
    for y in range(ground - 36, ground):
        t = (y - (ground - 36)) / 36
        half = 7 - 2 * math.sin(t * math.pi) + t * 2
        c.rect(round(74 - half), y, round(74 + half), y, sil)
    # rocket on a pad
    c.rect(124, ground - 30, 126, ground - 6, sil)
    c.put(125, ground - 31, sil)
    c.rect(121, ground - 6, 129, ground, sil)
    # windows
    rng = random.Random(5)
    for _ in range(30):
        x, y = rng.randrange(4, 128), rng.randrange(ground - 30, ground)
        if c.get(x, y) == sil:
            c.put(x, y, rgb("#FFD070"))
    return c


def wall_dyson():
    """The sun inside a golden lattice of collectors, on black."""
    rng = random.Random(41)
    c = Canvas(WW, WH)
    for y in range(WH):
        for x in range(WW):
            c.put(x, y, rgb("#06040A"))
    for _ in range(90):
        c.put(rng.randrange(WW), rng.randrange(WH), rgb("#8A8AA0"))
    cx, cy = WW // 2, 112
    for r in range(40, 0, -1):
        c.disc(cx, cy, r, mix(rgb("#FFF0A0"), rgb("#401808"), r / 40) if r < 20 else rgb("#401808", 40))
    c.disc(cx, cy, 14, rgb("#FFD040"))
    c.disc(cx, cy, 9, rgb("#FFF4C0"))
    gold, dim = rgb("#E8B040"), rgb("#5A4418")
    for s in (34, 46):
        for i in range(-s, s + 1):
            on = (i // 4) % 2 == 0
            col = gold if on else dim
            c.put(cx + i, cy - s, col)
            c.put(cx + i, cy + s, col)
            c.put(cx - s, cy + i, col)
            c.put(cx + s, cy + i, col)
    for i in range(-46, 47, 6):
        c.line(cx + i, cy - 46, cx + i * 34 // 46, cy - 34, dim)
    return c


WALLPAPERS = [wall_circuit, wall_orbit, wall_sunset, wall_dyson]


def paths():
    yield TEX / "item" / "factory_phone.png", phone_icon
    yield TEX / "item" / "factory_phone_back.png", phone_back
    yield TEX / "item" / "factory_phone_side.png", phone_side
    yield TEX / "item" / "factory_phone_front.png", phone_front
    yield TEX / "item" / "factory_phone_screen_off.png", screen_off
    yield TEX / "item" / "factory_phone_screen_on.png", screen_on
    for name, fn in APP_ICONS.items():
        yield TEX / "gui" / "phone" / "app" / f"{name}.png", fn
    for i, fn in enumerate(WALLPAPERS):
        yield TEX / "gui" / "phone" / f"wallpaper_{i}.png", fn


def draw(only_missing):
    for path, fn in paths():
        if only_missing and path.exists():
            continue
        fn().save(path)


def preview():
    from PIL import Image
    sheet = Image.new("RGBA", (4 * (WW + 8) + 8, WH + 16 + 3 * 104), (24, 26, 32, 255))
    for i, fn in enumerate(WALLPAPERS):
        sheet.paste(Image.open(TEX / "gui" / "phone" / f"wallpaper_{i}.png"), (8 + i * (WW + 8), 8))
    y = WH + 16
    for i, name in enumerate(APP_ICONS):
        img = Image.open(TEX / "gui" / "phone" / "app" / f"{name}.png").resize((96, 96), Image.NEAREST)
        sheet.alpha_composite(img, (8 + (i % 5) * 104, y + (i // 5) * 104))
    for i, name in enumerate(("factory_phone", "factory_phone_screen_on", "factory_phone_back")):
        img = Image.open(TEX / "item" / f"{name}.png").resize((96, 96), Image.NEAREST)
        sheet.alpha_composite(img, (8 + (3 + i) * 104, y + 104))
    sheet.save(ROOT / "tools" / "phone_textures_preview.png")


def generate(ctx):
    try:
        draw(only_missing=True)
    except ImportError:
        pass


if __name__ == "__main__":
    draw(only_missing=False)
    preview()
    print("wrote phone textures")

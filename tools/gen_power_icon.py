"""Generador de iconos de poder para el HUD (textures/gui/powers/<power>.png).

La receta se reconstruyo decodificando los iconos que ya existian (el script
original se habia perdido). Es consistente en los 27 iconos:

  - 16x16 RGBA, borde de 1px con las 4 esquinas transparentes.
  - Fondo con gradiente DIAGONAL: color = f(x + y). Por eso cada archivo tiene
    ~25 azules oscuros casi identicos; es generado, no dibujado a mano.
  - Brillo especular de 2px arriba a la izquierda (y=1: x=1,2 / y=2: x=1).
  - Motivo plano encima, en la paleta fija del elemento.

Paletas de borde por elemento (el borde va tenido del elemento):
  Fire #3A1208 | Water #0A1C34 | Ice #0C1E2C | Music #2C0E28 | Tech #120E2C
  Dark #100A1A | Nature #10240E | SunAndMoon #2A200E
  Storm: usa el neutro #181C24 (sus dos iconos originales son asi; se respeta
  esa excepcion para que los tres iconos de Storm sean consistentes entre si).

El nombre del archivo tiene que ser el nombre del enum EnumPowers en minuscula
(PowerHudOverlay resuelve el icono asi).

Uso: editar ART/COLORS/paleta y correr `python tools/gen_power_icon.py`.
Ahora mismo genera ride_the_storm.png (nube montada + rayo).
"""
import zlib, struct

W = H = 16
BORDER = (0x18, 0x1C, 0x24)
BG_TL  = (0x2D, 0x35, 0x40)   # esquina superior izquierda (x+y = 2)
BG_BR  = (0x22, 0x28, 0x33)   # esquina inferior derecha  (x+y = 28)
SHINE  = (0xF2, 0xF6, 0xFA)

# Paleta Storm, tal cual la usan storm_field.png y tornado.png
CLOUD_LT = (0xC2, 0xCE, 0xDC)
CLOUD    = (0x8A, 0x98, 0xB0)
CLOUD_DK = (0x4A, 0x54, 0x68)
BOLT     = (0xFF, 0xE8, 0x6A)

# Motivo: nube montada (arriba) + rayo saliendo por debajo/atras.
# '.' = fondo, 'L' claro, 'M' medio, 'D' oscuro, 'Y' rayo
ART = [
    "..............",
    "..............",
    "....LLLLLL....",
    "..LLLLLLLLLL..",
    ".DLLLLLLLLLLD.",
    "DMMMMMMMMMMMMD",
    ".DMMMMMMMMMMD.",
    "...DDMMMMDD...",
    "......YY......",
    ".....YY.......",
    "....YYYY......",
    "......YY......",
    ".....YY.......",
    "..............",
]
COLORS = {'L': CLOUD_LT, 'M': CLOUD, 'D': CLOUD_DK, 'Y': BOLT}


def bg(x, y):
    """Gradiente diagonal: interpola BG_TL -> BG_BR segun (x+y)."""
    t = (x + y - 2) / 26.0
    t = max(0.0, min(1.0, t))
    return tuple(round(BG_TL[i] + (BG_BR[i] - BG_TL[i]) * t) for i in range(3))


rows = []
for y in range(H):
    row = bytearray()
    for x in range(W):
        # esquinas transparentes
        if (x, y) in ((0, 0), (W-1, 0), (0, H-1), (W-1, H-1)):
            row += bytes((0, 0, 0, 0)); continue
        # borde
        if x == 0 or y == 0 or x == W-1 or y == H-1:
            row += bytes(BORDER + (255,)); continue
        # brillo especular arriba a la izquierda (igual que los demas iconos)
        if (y == 1 and x in (1, 2)) or (y == 2 and x == 1):
            row += bytes(SHINE + (255,)); continue
        ch = ART[y-1][x-1]
        px = COLORS.get(ch) or bg(x, y)
        row += bytes(px + (255,))
    rows.append(bytes(row))

raw = b''.join(b'\x00' + r for r in rows)
def chunk(t, d):
    c = t + d
    return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(raw, 9))
       + chunk(b'IEND', b''))
out = 'src/main/resources/assets/minewinx/textures/gui/powers/ride_the_storm.png'
open(out, 'wb').write(png)
print('escrito %s (%d bytes)' % (out, len(png)))

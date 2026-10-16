#!/usr/bin/env python3 -I
"""Баннер релиза для Telegram в стиле Hairline: docs/banners/telegram-0.0.3-beta-3.png (1280×720).

Фон — контурная карта, новые обои приложения (app/src/main/res/drawable-nodpi/backdrop_topo.webp):
это настоящая картинка из релиза, а не нарисованный макет экрана.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "app/src/main/res/font"
S = 2
W, H = 1280 * S, 720 * S
BG = (8, 8, 11)
LINE = (52, 56, 72)
TEXT = (244, 244, 246)
MUTED = (160, 162, 176)
ACCENT = (74, 124, 251)
VERSION = "0.0.3 beta-3"


def font(name, size):
    from PIL import ImageFont
    return ImageFont.truetype(str(FONTS / name), size * S)


# Фон: полоса из портретных обоев по ширине баннера, с самыми живыми линиями.
wall = Image.open(ROOT / "app/src/main/res/drawable-nodpi/backdrop_topo.webp").convert("RGB")
scale = W / wall.width
wall = wall.resize((W, int(wall.height * scale)), Image.LANCZOS)
top = int(wall.height * 0.40)
img = wall.crop((0, top, W, top + H))
img = ImageEnhance.Brightness(img).enhance(1.45)

# Слева темнее, чтобы текст читался; справа линии остаются видны.
shade = Image.new("L", (W, H), 0)
sd = ImageDraw.Draw(shade)
for x in range(W):
    t = x / W
    a = 235 if t < 0.38 else max(100, int(235 * max(0.0, 1 - (t - 0.38) / 0.5) ** 1.3))
    sd.line((x, 0, x, H), fill=a)
img = Image.composite(Image.new("RGB", (W, H), BG), img, shade)
d = ImageDraw.Draw(img)

# значок: рамка, W и кольцо — как на прежнем баннере
box = (96 * S, 168 * S, 96 * S + 384 * S, 168 * S + 384 * S)
d.rounded_rectangle(box, radius=6 * S, fill=BG, outline=TEXT, width=2 * S)
ox, oy, k = 96 * S, 168 * S, 384 * S / 108
m = 0.8


def pt(x, y):
    return ox + (54 + (x - 54) * m) * k, oy + (54 + (y - 54) * m) * k


pts = [pt(*p) for p in [(28, 40), (41, 74), (54, 50), (67, 74), (80, 40)]]
width = int(7 * m * k)
d.line(pts, fill=TEXT, width=width, joint="curve")
for p in (pts[0], pts[-1]):
    d.ellipse((p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2), fill=TEXT)
rx, ry = pt(54, 28)
rr = 7.5 * m * k
d.ellipse((rx - rr, ry - rr, rx + rr, ry + rr), outline=ACCENT, width=int(3.6 * m * k))

tx = 560 * S
d.text((tx, 196 * S), f"НОВЫЙ ВЫПУСК · {VERSION.upper()}", font=font("jetbrains_mono_medium.ttf", 18), fill=ACCENT)
d.text((tx, 232 * S), "w0y", font=font("unbounded_extrabold.ttf", 132), fill=TEXT)
d.text((tx, 400 * S), "Новый плеер и обновления.", font=font("unbounded_semibold.ttf", 30), fill=TEXT)
d.text((tx, 446 * S), "Обложка летит в мини-плеер, итоги года.", font=font("unbounded_semibold.ttf", 22), fill=(190, 192, 204))

x = tx
for label in ("ПОСТЕР", "ОБНОВЛЕНИЯ ИЗ GITHUB", "ИТОГИ ГОДА"):
    f = font("jetbrains_mono_medium.ttf", 17)
    w = d.textlength(label, font=f) + 36 * S
    d.rounded_rectangle((x, 510 * S, x + w, 552 * S), radius=3 * S, fill=BG, outline=LINE, width=S)
    d.text((x + 18 * S, 520 * S), label, font=f, fill=TEXT)
    x += w + 12 * S

d.text((tx, 648 * S), "github.com/texfi-w0y/texfi-w0y · открытый код, AGPL-3.0", font=font("jetbrains_mono_medium.ttf", 16), fill=MUTED)
d.rectangle((0, H - 6 * S, W, H), fill=ACCENT)

out = ROOT / "docs/banners/telegram-0.0.3-beta-3.png"
img.resize((1280, 720), Image.LANCZOS).save(out, optimize=True)
print("ok", out)

#!/usr/bin/env python3 -I
"""Рисует баннер для Telegram и GitHub в стиле Hairline: docs/banners/telegram.png (1280×720)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "app/src/main/res/font"
S = 2  # суперсэмплинг
W, H = 1280 * S, 720 * S
BG = (10, 10, 13)
LINE = (38, 40, 52)
TEXT = (244, 244, 246)
MUTED = (150, 152, 166)
ACCENT = (74, 124, 251)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size * S)


img = Image.new("RGB", (W, H), BG)
# холодная заливка из верхнего угла
wash = Image.new("RGB", (W, H), BG)
glow = Image.new("L", (W, H), 0)
ImageDraw.Draw(glow).ellipse((-300 * S, -500 * S, 900 * S, 500 * S), fill=70)
glow = glow.filter(ImageFilter.GaussianBlur(160 * S))
img = Image.composite(Image.new("RGB", (W, H), (22, 34, 78)), img, glow)
d = ImageDraw.Draw(img)

# сетка 48 px
for x in range(0, 1280, 48):
    d.line((x * S, 0, x * S, H), fill=LINE, width=S)
for y in range(0, 720, 48):
    d.line((0, y * S, W, y * S), fill=LINE, width=S)

# значок: рамка, W и кольцо
box = (96 * S, 168 * S, 96 * S + 384 * S, 168 * S + 384 * S)
d.rounded_rectangle(box, radius=6 * S, fill=BG, outline=TEXT, width=2 * S)
ox, oy, k = 96 * S, 168 * S, 384 * S / 108
m = 0.8  # доля рамки, которую занимает рисунок


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

# текст справа
tx = 560 * S
d.text((tx, 196 * S), "TEXFI · ANDROID · LINUX", font=font("jetbrains_mono_medium.ttf", 18), fill=MUTED)
d.text((tx, 232 * S), "w0y", font=font("unbounded_extrabold.ttf", 132), fill=TEXT)
d.text((tx, 400 * S), "Клиент YouTube Music.", font=font("unbounded_semibold.ttf", 30), fill=TEXT)
d.text((tx, 446 * S), "Быстрый старт, тонкие линии, свои файлы.", font=font("unbounded_semibold.ttf", 22), fill=ACCENT)

# чипы
x = tx
for label in ("БЕЗ РЕКЛАМЫ", "СВОИ MP3 И WAV", "ТЕКСТЫ ПЕСЕН"):
    f = font("jetbrains_mono_medium.ttf", 17)
    w = d.textlength(label, font=f) + 36 * S
    d.rounded_rectangle((x, 510 * S, x + w, 552 * S), radius=3 * S, outline=LINE, width=S)
    d.text((x + 18 * S, 520 * S), label, font=f, fill=TEXT)
    x += w + 12 * S

d.text((tx, 648 * S), "github.com/texfi-w0y/texfi-w0y · открытый код, AGPL-3.0", font=font("jetbrains_mono_medium.ttf", 16), fill=MUTED)
d.rectangle((0, H - 6 * S, W, H), fill=ACCENT)

out = ROOT / "docs/banners/telegram.png"
img.resize((1280, 720), Image.LANCZOS).save(out, optimize=True)
print("ok", out)

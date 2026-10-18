#!/usr/bin/env python3
"""Свои обложки для демо-библиотеки промо-ролика: абстракции, нарисованные кодом.

Настоящие обложки и названия в ролик не попадают: ни чужих прав, ни русских песен автора.
    python3 tools/promo/make_covers.py   →  cache/covers/c01.png … c12.png (800×800)
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "cache", "covers")
os.makedirs(OUT, exist_ok=True)
N = 800

PALETTES = [
    ((20, 14, 60), (92, 60, 255), (255, 120, 90)),
    ((8, 30, 40), (20, 160, 170), (250, 220, 140)),
    ((40, 10, 30), (210, 50, 110), (255, 170, 120)),
    ((10, 18, 40), (50, 110, 255), (170, 230, 255)),
    ((30, 24, 10), (220, 150, 40), (255, 235, 180)),
    ((14, 32, 22), (60, 190, 120), (220, 255, 190)),
    ((34, 12, 52), (170, 80, 240), (255, 190, 240)),
    ((10, 10, 14), (110, 110, 130), (240, 240, 250)),
    ((46, 18, 10), (240, 90, 40), (255, 210, 120)),
    ((6, 24, 56), (30, 90, 200), (255, 120, 160)),
    ((24, 28, 12), (150, 180, 50), (250, 250, 170)),
    ((38, 8, 18), (130, 20, 60), (255, 130, 110)),
]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(c0, c1, angle):
    img = Image.new("RGB", (N, N))
    px = img.load()
    ca, sa = math.cos(angle), math.sin(angle)
    for y in range(N):
        for x in range(N):
            t = ((x - N / 2) * ca + (y - N / 2) * sa) / N + 0.5
            px[x, y] = mix(c0, c1, max(0.0, min(1.0, t)))
    return img


def orb(img, cx, cy, r, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (int(255 * alpha),))
    layer = layer.filter(ImageFilter.GaussianBlur(r * 0.35))
    img.paste(layer, (0, 0), layer)


def make(i, pal):
    rnd = random.Random(1000 + i)
    c0, c1, c2 = pal
    img = gradient(c0, c1, rnd.uniform(0, math.pi))
    kind = i % 4
    d = ImageDraw.Draw(img, "RGBA")
    if kind == 0:  # закат: большой круг и горизонты
        orb(img, N * 0.5, N * 0.44, N * 0.26, c2, 0.9)
        d = ImageDraw.Draw(img, "RGBA")
        d.ellipse([N * 0.26, N * 0.2, N * 0.74, N * 0.68], fill=c2 + (255,))
        for k in range(9):
            y = N * (0.46 + k * 0.058)
            d.rectangle([0, y, N, y + N * (0.004 + 0.006 * k)], fill=c0 + (255,))
    elif kind == 1:  # дуги
        for k in range(7):
            r = N * (0.12 + k * 0.1)
            d.arc([N / 2 - r, N * 0.9 - r, N / 2 + r, N * 0.9 + r], 180, 360, fill=mix(c1, c2, k / 7) + (255,), width=int(N * 0.028))
        orb(img, N * 0.5, N * 0.9, N * 0.2, c2, 0.7)
    elif kind == 2:  # ромбы-сетка
        step = N // 5
        for gx in range(-1, 6):
            for gy in range(-1, 6):
                cx, cy = gx * step + (step // 2 if gy % 2 else 0), gy * step
                if rnd.random() < 0.55:
                    s = step * rnd.uniform(0.22, 0.42)
                    d.polygon([(cx, cy - s), (cx + s, cy), (cx, cy + s), (cx - s, cy)], fill=mix(c1, c2, rnd.random()) + (230,))
        orb(img, N * 0.3, N * 0.3, N * 0.22, c2, 0.35)
    else:  # орбиты
        for k in range(4):
            orb(img, N * rnd.uniform(0.2, 0.8), N * rnd.uniform(0.2, 0.8), N * rnd.uniform(0.12, 0.26), mix(c1, c2, rnd.random()), 0.8)
        d = ImageDraw.Draw(img, "RGBA")
        d.ellipse([N * 0.3, N * 0.3, N * 0.7, N * 0.7], outline=c2 + (255,), width=int(N * 0.012))
        d.ellipse([N * 0.18, N * 0.18, N * 0.82, N * 0.82], outline=c2 + (140,), width=int(N * 0.006))
    # Зерно плёнки.
    noise = Image.effect_noise((N, N), 24).convert("RGB")
    img = ImageChops.add(img, ImageChops.multiply(noise, Image.new("RGB", (N, N), (22, 22, 22))), 1.0, -4)
    return img


for i, pal in enumerate(PALETTES, 1):
    make(i, pal).save(os.path.join(OUT, "c%02d.png" % i))
print("ok", len(PALETTES))

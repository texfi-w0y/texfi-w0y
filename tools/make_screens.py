#!/usr/bin/env python3
"""Собирает все скриншоты из docs/screenshots/raw/ в одну картинку w0y-screens.png
и уменьшенную копию w0y-screens-small.webp. Нужен Pillow: pip install pillow.
Результат детерминирован: порядок по имени файла, никакой случайности."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "docs/screenshots/raw"
OUT = ROOT / "docs/screenshots"
LABEL = "W0Y  ·  V0.0.3 BETA-1  ·  HAIRLINE"

BG = (10, 9, 12)
BLUE = (74, 124, 251)
SAND = (224, 168, 96)
LINE = (30, 31, 40)
OUTLINE = (92, 96, 116)
MUTED = (150, 152, 166)
H = 1280          # высота каждого скриншота
GAP = 56          # просвет между рамками
PAD = 96          # поля картинки
R = 30            # радиус рамки
SHADOW = 0        # Hairline: без теней
BORDER = 2


def font(size):
    f = ROOT / "app/src/main/res/font/jetbrains_mono_medium.ttf"
    return ImageFont.truetype(str(f), size) if f.exists() else ImageFont.load_default()


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *img.size), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def main():
    files = sorted(p for p in RAW.iterdir() if p.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp"))
    if not files:
        raise SystemExit(f"В {RAW} нет изображений.")
    shots = []
    for p in files:
        im = Image.open(p).convert("RGB")
        w = round(im.width * H / im.height)
        shots.append(im.resize((w, H), Image.LANCZOS))
    # одна строка, если до пяти; иначе две примерно равной ширины
    if len(shots) <= 5:
        rows = [shots]
    else:
        k = (len(shots) + 1) // 2
        rows = [shots[:k], shots[k:]]
    row_w = [sum(s.width for s in r) + GAP * (len(r) - 1) for r in rows]
    width = max(row_w) + PAD * 2 + SHADOW
    head = 110
    height = PAD + head + len(rows) * H + (len(rows) - 1) * GAP + PAD + SHADOW
    canvas = Image.new("RGBA", (width, height), BG + (255,))
    d = ImageDraw.Draw(canvas)
    for x in range(0, width, 48):
        d.line((x, 0, x, height), fill=LINE, width=1)
    for y in range(0, height, 48):
        d.line((0, y, width, y), fill=LINE, width=1)
    d.text((PAD, PAD // 2 + 10), LABEL, font=font(30), fill=MUTED)
    y = PAD + head
    for r, rw in zip(rows, row_w):
        x = PAD + (width - PAD * 2 - SHADOW - rw) // 2
        for s in r:
            canvas.alpha_composite(rounded(s, R - BORDER), (x, y))
            d.rounded_rectangle((x, y, x + s.width, y + H), R, outline=OUTLINE, width=BORDER)
            x += s.width + GAP
        y += H + GAP
    big = canvas.convert("RGB")
    OUT.mkdir(parents=True, exist_ok=True)
    big.save(OUT / "w0y-screens.png", optimize=True)
    small = big.resize((1600, round(big.height * 1600 / big.width)), Image.LANCZOS)
    small.save(OUT / "w0y-screens-small.webp", quality=82, method=6)
    print("готово:", OUT / "w0y-screens.png", OUT / "w0y-screens-small.webp")


if __name__ == "__main__":
    main()

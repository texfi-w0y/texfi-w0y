#!/usr/bin/env python3
"""Ролик w0y для TikTok в стиле Hairline: 1080×1920, 60 fps, 15 с.

Сюжет: сайт → «Download» → загрузка → значок на экране → приложение →
итоговая карточка. Сайт и приложение — настоящие скриншоты (папка shots/),
монтаж идёт по долям 120 BPM (доля = 0.5 с), чтобы любой трек на 120 BPM
ложился на удары. Звук здесь — только короткие эффекты (клик, «вуш», звонок);
музыку добавляют в самом TikTok: тренд-треки нельзя класть в файл рекламы.

    python3 tools/ad/make_ad.py preview     # контактный лист кадров
    python3 tools/ad/make_ad.py render      # tools/ad/out/w0y-tiktok-ad.mp4
"""
import math
import os
import random
import struct
import subprocess
import sys
import wave

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SHOTS = os.path.join(HERE, "shots")
SHOTS2 = os.path.join(HERE, "shots2")
OUT = os.path.join(HERE, "out")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")

W, H, FPS = 1080, 1920, 60
DUR = 15.0
# Сетка трека «Crush x Burnt Rice» (замер по файлу): ~151 BPM, фраза = такт×4,
# первый сильный удар на 0.14 с, дальше каждые 1.587 с (удар баса после паузы).
BPM = 151.2
BEAT = 60.0 / BPM
BAR = 4 * BEAT
T0 = 0.14


def bar(n):
    return T0 + n * BAR


T_TAP, T_PRESS, T_DONE = bar(2), bar(3), bar(4)
T_APP, T_PLAYER, T_LAST, T_END = bar(5), bar(6), bar(7), bar(8)

BG = (10, 10, 12)
SURFACE = (24, 24, 28)
LINE = (46, 46, 54)
TEXT = (244, 244, 246)
MUTED = (150, 150, 160)
BLUE = (74, 125, 251)
DOT = (36, 36, 44)

# Экран телефона: пропорции скриншота 1080×2424.
SW, SH = 660, 1481
BEZEL = 14
PHONE_W, PHONE_H = SW + 2 * BEZEL, SH + 2 * BEZEL
PHONE_X = (W - PHONE_W) // 2
PHONE_Y = 520


# ------------------------------------------------------------------ easing
def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def lerp(a, b, t):
    return a + (b - a) * t


def out_cubic(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def in_cubic(t):
    t = clamp(t)
    return t**3


def in_out(t):
    t = clamp(t)
    return 3 * t * t - 2 * t * t * t


def spring(t, zeta=0.45, omega=16.0):
    """Недодемпфированная пружина от 0 к 1 с перелётом."""
    if t <= 0:
        return 0.0
    wd = omega * math.sqrt(1 - zeta * zeta)
    return 1 - math.exp(-zeta * omega * t) * (
        math.cos(wd * t) + zeta * omega / wd * math.sin(wd * t)
    )


# ------------------------------------------------------------------ fonts
_fonts = {}


def font(name, size):
    key = (name, size)
    if key not in _fonts:
        paths = {
            "display": os.path.join(FONTS, "unbounded_extrabold.ttf"),
            "semi": os.path.join(FONTS, "unbounded_semibold.ttf"),
            "mono": os.path.join(FONTS, "jetbrains_mono_medium.ttf"),
            "sans": os.path.expanduser("~/.local/share/fonts/texfi/Inter-Regular.ttf"),
        }
        _fonts[key] = ImageFont.truetype(paths[name], size)
    return _fonts[key]


def text_width(txt, f, spacing=0):
    return sum(f.getlength(c) + spacing for c in txt) - spacing if spacing else f.getlength(txt)


def draw_text(d, xy, txt, f, fill, spacing=0, anchor="l"):
    x, y = xy
    if spacing:
        w = text_width(txt, f, spacing)
        if anchor == "m":
            x -= w / 2
        elif anchor == "r":
            x -= w
        for c in txt:
            d.text((x, y), c, font=f, fill=fill)
            x += f.getlength(c) + spacing
    else:
        d.text((x, y), txt, font=f, fill=fill, anchor={"l": "ls", "m": "ms", "r": "rs"}[anchor] if False else None)


# ------------------------------------------------------------------ assets
def load_assets():
    a = {}
    a["site"] = Image.open(os.path.join(SHOTS, "site_dark.png")).convert("RGB").resize((SW, SH), Image.LANCZOS)
    for name in ("home", "player", "playlist"):
        a[name] = Image.open(os.path.join(SHOTS2, name + ".png")).convert("RGB").resize((SW, SH), Image.LANCZOS)
    a["icon"] = make_icon(256)
    # Положение кнопки Download на скриншоте сайта: ищем светлую полосу по центру.
    site = a["site"]
    rows = [y for y in range(int(SH * 0.4), int(SH * 0.7)) if sum(site.getpixel((SW // 2 + dx, y))[0] for dx in (-40, 0, 40)) > 3 * 200]
    top, bottom = rows[0], rows[-1]
    xs = [x for x in range(SW) if site.getpixel((x, (top + bottom) // 2))[0] > 200]
    a["button"] = (xs[0], top, xs[-1], bottom)
    return a


def make_icon(size):
    """Значок «W с кольцом»: те же числа, что в ic_icon_w_fg.xml."""
    s = size * 4
    img = Image.new("RGBA", (s, s), (14, 14, 18, 255))
    d = ImageDraw.Draw(img)
    k = s / 108.0
    sc = 0.7

    def p(x, y):
        return ((54 + (x - 54) * sc) * k, (54 + (y - 54) * sc) * k)

    pts = [p(*q) for q in [(28, 40), (41, 74), (54, 50), (67, 74), (80, 40)]]
    w = 7 * sc * k
    d.line(pts, fill=(244, 244, 246, 255), width=int(w), joint="curve")
    for x, y in pts:
        d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=(244, 244, 246, 255))
    cx, cy = p(54, 33)
    r = 7.5 * sc * k
    d.ellipse((cx - r - 1.8 * sc * k, cy - r - 1.8 * sc * k, cx + r + 1.8 * sc * k, cy + r + 1.8 * sc * k), outline=(74, 124, 251, 255), width=int(3.6 * sc * k))
    img = img.resize((size, size), Image.LANCZOS)
    mask = rounded_mask((size, size), int(size * 0.24))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def rounded_mask(size, radius, scale=4):
    w, h = size
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), radius=radius * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


# ------------------------------------------------------------------ background
def make_backgrounds():
    base = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(base)
    for y in range(24, H, 48):
        for x in range(24, W, 48):
            d.ellipse((x - 1.5, y - 1.5, x + 1.5, y + 1.5), fill=DOT)
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    g = Image.new("L", (W, H), 0)
    ImageDraw.Draw(g).ellipse((W // 2 - 520, 980 - 520, W // 2 + 520, 980 + 520), fill=255)
    g = g.filter(ImageFilter.GaussianBlur(220)).point(lambda v: int(v * 0.30))
    glow.paste(Image.new("RGB", (W, H), BLUE), (0, 0), g)
    from PIL import ImageChops

    lit = ImageChops.add(base, glow)
    return base, lit


# ------------------------------------------------------------------ phone
def make_bezel():
    """Корпус телефона с вырезом под экран; экран подкладывается снизу."""
    layer = Image.new("RGBA", (PHONE_W, PHONE_H), (0, 0, 0, 0))
    body = rounded_mask((PHONE_W, PHONE_H), 78)
    hole = Image.new("L", (PHONE_W, PHONE_H), 0)
    hole.paste(rounded_mask((SW, SH), 64), (BEZEL, BEZEL))
    from PIL import ImageChops

    ring = ImageChops.subtract(body, hole)
    fill = Image.new("RGBA", (PHONE_W, PHONE_H), (20, 20, 24, 255))
    layer.paste(fill, (0, 0), ring)
    # Тонкая светлая кромка корпуса.
    edge = Image.new("L", (PHONE_W, PHONE_H), 0)
    e = ImageDraw.Draw(edge)
    e.rounded_rectangle((0, 0, PHONE_W - 1, PHONE_H - 1), radius=78, outline=255, width=2)
    layer.paste(Image.new("RGBA", (PHONE_W, PHONE_H), (62, 62, 72, 255)), (0, 0), edge)
    # Камера.
    cam = Image.new("L", (PHONE_W, PHONE_H), 0)
    ImageDraw.Draw(cam).ellipse((PHONE_W // 2 - 13, BEZEL + 22, PHONE_W // 2 + 13, BEZEL + 48), fill=255)
    layer.paste(Image.new("RGBA", (PHONE_W, PHONE_H), (6, 6, 8, 255)), (0, 0), cam)
    return layer


def make_shadow():
    pad = 90
    sh = Image.new("L", (PHONE_W + pad * 2, PHONE_H + pad * 2), 0)
    ImageDraw.Draw(sh).rounded_rectangle((pad, pad + 30, pad + PHONE_W, pad + 30 + PHONE_H), radius=78, fill=255)
    sh = sh.filter(ImageFilter.GaussianBlur(40)).point(lambda v: int(v * 0.65))
    return sh, pad


# ------------------------------------------------------------------ screens
def zoom(img, s, cx, cy):
    if s <= 1.0001:
        return img
    w, h = img.size
    cw, ch = w / s, h / s
    x0 = clamp(cx - cw / 2, 0, w - cw)
    y0 = clamp(cy - ch / 2, 0, h - ch)
    return img.crop((int(x0), int(y0), int(x0 + cw), int(y0 + ch))).resize((w, h), Image.BICUBIC)


def draw_icon_at(img, icon, cx, cy, size):
    if size < 2:
        return
    ic = icon.resize((int(size), int(size)), Image.LANCZOS)
    img.paste(ic, (int(cx - size / 2), int(cy - size / 2)), ic)


def site_screen(a, t):
    img = a["site"].copy()
    d = ImageDraw.Draw(img, "RGBA")
    bx0, by0, bx1, by1 = a["button"]
    bcx, bcy = (bx0 + bx1) / 2, (by0 + by1) / 2
    # Кнопка: после нажатия превращается в полосу загрузки.
    if t >= T_PRESS:
        p = clamp((t - T_PRESS) / (T_DONE - T_PRESS))
        d.rounded_rectangle((bx0, by0, bx1, by1), radius=6, fill=BG, outline=(90, 90, 100), width=2)
        fw = (bx1 - bx0) * p
        if fw > 2:
            d.rounded_rectangle((bx0, by0, bx0 + fw, by1), radius=6, fill=BLUE)
        label = "DOWNLOADED" if p >= 1 else "DOWNLOADING  %d%%" % int(p * 100)
        f = font("mono", 24)
        draw_text(d, (bcx, bcy - 14), label, f, TEXT, spacing=3, anchor="m")
    if T_PRESS <= t < T_PRESS + 0.1:
        d.rounded_rectangle((bx0, by0, bx1, by1), radius=6, fill=(0, 0, 0, 90))
    # Палец-индикатор.
    if T_PRESS - 1.1 <= t < T_PRESS + 0.6:
        q = out_cubic((t - (T_PRESS - 1.1)) / 0.9)
        fx = lerp(SW * 0.82, bcx, q)
        fy = lerp(SH * 0.80, bcy + 8, q)
        fade = 1 - clamp((t - (T_PRESS + 0.2)) / 0.35)
        r = 36 * (0.86 if T_PRESS <= t < T_PRESS + 0.12 else 1.0)
        d.ellipse((fx - r, fy - r, fx + r, fy + r), fill=(255, 255, 255, int(70 * fade)), outline=(255, 255, 255, int(200 * fade)), width=3)
        if t >= T_PRESS:
            rp = clamp((t - T_PRESS) / 0.5)
            rr = lerp(r, 130, out_cubic(rp))
            d.ellipse((fx - rr, fy - rr, fx + rr, fy + rr), outline=(255, 255, 255, int(160 * (1 - rp) * fade)), width=3)
    # Наезд камеры на кнопку перед нажатием.
    s = 1 + 0.16 * in_out((t - T_TAP) / 0.9) - 0.16 * in_out((t - (T_DONE - 0.7)) / 0.7)
    img = zoom(img, s, bcx, bcy)
    d = ImageDraw.Draw(img, "RGBA")
    # Уведомление о загрузке сверху экрана.
    if T_PRESS + 0.1 <= t < T_DONE + 0.3:
        enter = spring((t - (T_PRESS + 0.1)) / 1.0, 0.7, 14.0)
        leave = in_cubic((t - (T_DONE - 0.15)) / 0.3)
        y = lerp(-150, 34, clamp(enter, 0, 1.2)) - leave * 190
        pr = clamp((t - (T_PRESS + 0.2)) / (T_DONE - T_PRESS - 0.3))
        x0, x1 = 22, SW - 22
        d.rounded_rectangle((x0, y, x1, y + 124), radius=28, fill=(26, 26, 31, 246), outline=(70, 70, 80, 255), width=2)
        ic = a["icon"].resize((68, 68), Image.LANCZOS)
        img.paste(ic, (x0 + 24, int(y + 28)), ic)
        draw_text(d, (x0 + 112, y + 24), "TexFi-w0y-android.apk", font("semi", 23), TEXT)
        sub = "7.7 MB  ·  %d%%" % int(pr * 100) if pr < 1 else "Download complete"
        draw_text(d, (x0 + 112, y + 60), sub, font("mono", 19), MUTED, spacing=1)
        d.rounded_rectangle((x0 + 112, y + 98, x1 - 26, y + 104), radius=3, fill=(60, 60, 70, 255))
        d.rounded_rectangle((x0 + 112, y + 98, x0 + 112 + (x1 - 26 - x0 - 112) * out_cubic(pr), y + 104), radius=3, fill=BLUE)
    return img


HOME_POS = (1, 2)  # колонка, ряд значка w0y


def home_geom():
    col, row = HOME_POS
    cx = SW * (0.125 + 0.25 * col)
    cy = 250 + 205 * row
    return cx, cy, 116


def home_screen(a, t):
    img = Image.new("RGB", (SW, SH), (9, 10, 16))
    d = ImageDraw.Draw(img, "RGBA")
    # Мягкий свет обоев.
    for i in range(6):
        d.ellipse((-200 + i * 40, 300 + i * 60, SW + 200 - i * 40, 1200 - i * 40), outline=None, fill=(40, 60, 120, 5))
    d.text((40, 40), "12:30", font=font("sans", 26), fill=(230, 230, 235))
    tints = [(34, 38, 52), (42, 36, 48), (30, 44, 44), (46, 40, 34), (36, 36, 44)]
    cx0, cy0, size = home_geom()
    t_icon = t - (T_DONE + 0.1)
    for row in range(5):
        for col in range(4):
            if (col, row) == HOME_POS:
                continue
            cx = SW * (0.125 + 0.25 * col)
            cy = 250 + 205 * row
            enter = out_cubic((t - T_DONE - 0.03 * (row * 4 + col)) / 0.35)
            sz = size * (0.7 + 0.3 * enter)
            if row == 4:
                continue
            tone = tints[(row * 3 + col) % len(tints)]
            d.rounded_rectangle((cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2), radius=30, fill=tone + (int(255 * enter),))
            d.rounded_rectangle((cx - 30, cy + size / 2 + 14, cx + 30, cy + size / 2 + 22), radius=4, fill=(120, 120, 130, int(150 * enter)))
    # Нижняя панель экрана.
    d.rounded_rectangle((26, SH - 220, SW - 26, SH - 90), radius=48, fill=(255, 255, 255, 18))
    for i in range(4):
        cx = SW * (0.125 + 0.25 * i)
        d.rounded_rectangle((cx - 42, SH - 198, cx + 42, SH - 114), radius=24, fill=tints[i % len(tints)] + (255,))
    # Значок w0y: выскакивает пружиной с кольцом.
    k = spring(t_icon, 0.42, 15.0) if t_icon > 0 else 0.0
    draw_icon_at(img, a["icon"], cx0, cy0, size * k * (1.0 + 0.12 * math.sin(clamp(t_icon * 4, 0, math.pi)) * 0))
    if t_icon > 0:
        rp = clamp(t_icon / 0.6)
        rr = size * (0.6 + 1.1 * out_cubic(rp))
        d.ellipse((cx0 - rr, cy0 - rr, cx0 + rr, cy0 + rr), outline=BLUE + (int(220 * (1 - rp)),), width=4)
        d.text((cx0, cy0 + size / 2 + 14), "w0y", font=font("sans", 24), fill=(240, 240, 244, int(255 * out_cubic(t_icon / 0.3))), anchor="ma")
    return img


def launch_screen(a, t, target):
    """Открытие приложения: окно растёт из значка до всего экрана."""
    q = in_out((t - (T_APP - 0.6)) / 0.6)
    home = home_screen(a, t)
    if q <= 0:
        return home
    cx0, cy0, size = home_geom()
    x0 = lerp(cx0 - size / 2, 0, q)
    y0 = lerp(cy0 - size / 2, 0, q)
    x1 = lerp(cx0 + size / 2, SW, q)
    y1 = lerp(cy0 + size / 2, SH, q)
    radius = int(lerp(30, 64, q))
    w, h = int(x1 - x0), int(y1 - y0)
    if w < 4 or h < 4:
        return home
    mask = rounded_mask((w, h), radius)
    scale = target.resize((w, h), Image.BICUBIC)
    cover = Image.new("RGB", (w, h), (14, 14, 18))
    scale = Image.blend(cover, scale, clamp((q - 0.25) / 0.5))
    home.paste(scale, (int(x0), int(y0)), mask)
    return home


def slide(old, new, q):
    q = out_cubic(q)
    out = Image.new("RGB", (SW, SH))
    shift_old = int(-SW * 0.28 * q)
    shade = Image.blend(old, Image.new("RGB", (SW, SH), (0, 0, 0)), 0.45 * q)
    out.paste(shade, (shift_old, 0))
    out.paste(new, (int(SW * (1 - q)), 0))
    return out


def app_screen(a, t, name, t0):
    img = a[name]
    s = 1 + 0.06 * clamp((t - t0) / 3.5)
    return zoom(img, s, SW / 2, SH * 0.45)


APP_SEQ = [("home", T_APP), ("player", T_PLAYER), ("playlist", T_LAST)]


def screen_at(a, t):
    if t < T_DONE:
        return site_screen(a, t)
    if t < T_APP:
        return launch_screen(a, t, app_screen(a, T_APP, "home", T_APP))
    cur = None
    for i, (name, t0) in enumerate(APP_SEQ):
        if t >= t0:
            cur = i
    name, t0 = APP_SEQ[cur]
    img = app_screen(a, t, name, t0)
    if cur > 0 and t - t0 < 0.45:
        pname, pt0 = APP_SEQ[cur - 1]
        old = app_screen(a, t0, pname, pt0)
        return slide(old, img, (t - t0) / 0.45)
    return img


# ------------------------------------------------------------------ titles
SCENES = [
    (T0, T_TAP, "TEXFI  /  W0Y", [[("YouTube Music,", TEXT)], [("minus the ads.", BLUE)]]),
    (T_TAP, T_DONE, "01  /  DOWNLOAD", [[("Tap", TEXT)], [("download.", BLUE)]]),
    (T_DONE, T_APP, "02  /  INSTALL", [[("Install it.", TEXT)], [("Open it.", BLUE)]]),
    (T_APP, T_PLAYER, "03  /  LISTEN", [[("The next track is", TEXT)], [("prefetched.", BLUE)]]),
    (T_PLAYER, T_LAST, "03  /  LISTEN", [[("Speed, pitch, reverb", TEXT)], [("per song.", BLUE)]]),
    (T_LAST, T_END, "03  /  LISTEN", [[("Zero ads.", TEXT)], [("Zero telemetry.", BLUE)]]),
]
TITLE_X = 84
TITLE_MAX = 912


def fit_font(lines, maxw, start=84):
    size = start
    while size > 40:
        f = font("display", size)
        if all(sum(text_width(s, f) for s, _ in line) <= maxw for line in lines):
            return f, size
        size -= 2
    return font("display", 40), 40


def draw_titles(base, t):
    layer = Image.new("RGBA", (W, 420), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    drawn = False
    for t0, t1, label, lines in SCENES:
        if not (t0 <= t < t1):
            continue
        drawn = True
        local = t - t0
        out_a = 1 - in_cubic((t - (t1 - 0.22)) / 0.22) if t1 < T_END else 1.0
        # Метка раздела в духе сайта: номер, линия.
        la = out_cubic(local / 0.35) * out_a
        f = font("mono", 26)
        draw_text(d, (TITLE_X, 40), label, f, MUTED + (int(255 * la),), spacing=4)
        lw = text_width(label, f, 4)
        d.line((TITLE_X + lw + 24, 52, W - TITLE_X, 52), fill=LINE + (int(255 * la),), width=2)
        tf, size = fit_font(lines, TITLE_MAX)
        for i, line in enumerate(lines):
            a = clamp(out_cubic((local - 0.06 * i) / 0.4)) * out_a
            dy = (1 - out_cubic((local - 0.06 * i) / 0.45)) * 34 - (1 - out_a) * 18
            x = TITLE_X
            y = 95 + i * int(size * 1.18) + dy
            for seg, col in line:
                d.text((x, y), seg, font=tf, fill=col + (int(255 * a),))
                x += tf.getlength(seg)
    if drawn:
        base.paste(layer, (0, 170), layer)


# ------------------------------------------------------------------ end card
def draw_end(base, t):
    t0 = T_END
    if t < t0:
        return
    local = t - t0
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def appear(delay, dur=0.45):
        return clamp(out_cubic((local - delay) / dur))

    # Значок.
    k = spring(local - 0.35, 0.45, 15.0) if local > 0.35 else 0.0
    ic = 230
    draw_icon_at(layer, make_icon_cached(), W // 2, 470, ic * k)
    if local > 0.35:
        rp = clamp((local - 0.35) / 0.7)
        rr = ic * (0.58 + 0.9 * out_cubic(rp))
        d.ellipse((W // 2 - rr, 470 - rr, W // 2 + rr, 470 + rr), outline=BLUE + (int(220 * (1 - rp)),), width=4)
    # Имя.
    a = appear(0.55)
    d.text((W // 2, 700 + (1 - a) * 26), "w0y", font=font("display", 168), fill=TEXT + (int(255 * a),), anchor="mt")
    a = appear(0.7)
    d.text((W // 2, 930 + (1 - a) * 20), "An open YouTube Music client", font=font("sans", 38), fill=MUTED + (int(255 * a),), anchor="mt")
    # Метки с сайта.
    a = appear(0.85)
    tags = ["AGPL-3.0", "0 ADS", "0 TELEMETRY"]
    f = font("mono", 26)
    widths = [text_width(s, f, 3) + 44 for s in tags]
    total = sum(widths) + 18 * (len(tags) - 1)
    x = (W - total) / 2
    for s, w in zip(tags, widths):
        d.rounded_rectangle((x, 1030, x + w, 1092), radius=6, outline=(90, 90, 100, int(255 * a)), width=2)
        draw_text(d, (x + 22, 1048), s, f, MUTED + (int(255 * a),), spacing=3)
        x += w + 18
    # Кнопка: как на сайте — светлая плашка, моно-подпись.
    a = appear(1.0)
    press = 1 - 0.035 * math.exp(-((local - 1.7) ** 2) / 0.012)
    bw, bh = 820 * press, 120 * press
    bx, by = W / 2 - bw / 2, 1190 + (1 - a) * 24 + (120 - bh) / 2
    d.rounded_rectangle((bx, by, bx + bw, by + bh), radius=8, fill=TEXT + (int(255 * a),))
    draw_text(d, (W / 2, by + bh / 2 - 17), "DOWNLOAD  ·  V0.0.3 BETA-2", font("mono", 32), BG + (int(255 * a),), spacing=4, anchor="m")
    a = appear(1.15)
    d.text((W // 2, 1360), "texfi-hub.vercel.app", font=font("sans", 40), fill=TEXT + (int(255 * a),), anchor="mt")
    d.text((W // 2, 1420), "Android  ·  free  ·  open source", font=font("mono", 26), fill=MUTED + (int(255 * a),), anchor="mt")
    base.paste(layer, (0, 0), layer)


_icon_cache = {}


def make_icon_cached():
    if "i" not in _icon_cache:
        _icon_cache["i"] = make_icon(256)
    return _icon_cache["i"]


# ------------------------------------------------------------------ звук
SR = 44100


def render_audio(path):
    n = int(DUR * SR)
    buf = [0.0] * n
    rnd = random.Random(7)

    def add(t0, samples, gain=1.0):
        i0 = int(t0 * SR)
        for i, v in enumerate(samples):
            if 0 <= i0 + i < n:
                buf[i0 + i] += v * gain

    def whoosh(dur=0.45, up=True):
        out = []
        lp = 0.0
        for i in range(int(dur * SR)):
            x = i / (dur * SR)
            env = math.sin(math.pi * x) ** 2
            cut = (0.02 + 0.30 * (x if up else 1 - x))
            lp += (rnd.uniform(-1, 1) - lp) * cut
            out.append(lp * env * 1.6)
        return out

    def click():
        out = []
        for i in range(int(0.09 * SR)):
            x = i / SR
            out.append(math.sin(2 * math.pi * 1500 * x) * math.exp(-x * 70) * 0.9 + rnd.uniform(-1, 1) * math.exp(-x * 160) * 0.25)
        return out

    def tone(freq, dur, decay=7.0, harm=2.0):
        out = []
        for i in range(int(dur * SR)):
            x = i / SR
            out.append((math.sin(2 * math.pi * freq * x) + 0.35 * math.sin(2 * math.pi * freq * harm * x)) * math.exp(-x * decay))
        return out

    def pop():
        out = []
        for i in range(int(0.28 * SR)):
            x = i / SR
            f = 260 + 520 * math.exp(-x * 22)
            out.append(math.sin(2 * math.pi * f * x) * math.exp(-x * 14))
        return out

    # Вуши перед каждой сменой сцены — на 0.1 с раньше удара.
    for t0 in [T0 - 0.05, T_TAP - 0.1, T_DONE - 0.12, T_APP - 0.7, T_APP - 0.1, T_PLAYER - 0.1, T_LAST - 0.1, T_END - 0.1]:
        add(max(0.0, t0), whoosh(0.5, True), 0.14)
    add(T_PRESS, click(), 0.55)
    # Загрузка: короткие «тики» с растущей высотой.
    steps = 12
    span = T_DONE - T_PRESS - 0.4
    for i in range(steps):
        add(T_PRESS + 0.2 + i * (span / steps), tone(700 + 70 * i, 0.06, 40, 1.0), 0.07)
    # Готово: два звонких удара на ударе трека.
    add(T_DONE - 0.15, tone(1046, 0.5, 6), 0.18)
    add(T_DONE, tone(1568, 0.7, 5), 0.15)
    add(T_DONE + 0.1, pop(), 0.35)
    # Итоговая карточка.
    add(T_END + 0.35, pop(), 0.35)
    add(T_END + 0.9, tone(523, 0.9, 3.5), 0.12)
    add(T_END + 0.9, tone(784, 0.9, 3.5), 0.10)
    add(T_END + 1.7, click(), 0.30)
    peak = max(abs(v) for v in buf) or 1.0
    scale = 0.85 / peak if peak > 0.85 else 1.0
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for v in buf:
            s = int(max(-1, min(1, v * scale)) * 32000)
            frames += struct.pack("<hh", s, s)
        w.writeframes(bytes(frames))


# ------------------------------------------------------------------ кадр
class Renderer:
    def __init__(self):
        self.a = load_assets()
        self.base, self.lit = make_backgrounds()
        self.bezel = make_bezel()
        self.shadow, self.pad = make_shadow()
        self.screen_mask = rounded_mask((SW, SH), 64)

    def frame(self, t):
        beat = ((t - T0) % BEAT) / BEAT if t >= T0 else 1.0
        pulse = math.exp(-beat * 5.0)
        bg = Image.blend(self.base, self.lit, clamp(0.55 + 0.45 * pulse))
        draw_titles(bg, t)
        # Телефон: въезд снизу на 1.0–1.7, выезд вниз на 13.0.
        enter = clamp(spring((t - (T0 + BEAT)) / 1.2, 0.72, 13.0), 0, 1.15)
        leave = in_cubic((t - T_END) / 0.7)
        if t >= T0 + BEAT and leave < 1:
            dy = (1 - enter) * 1100 + leave * 1500
            px, py = PHONE_X, int(PHONE_Y + dy)
            bg.paste((0, 0, 0), (px - self.pad, py - self.pad), self.shadow)
            screen = screen_at(self.a, t)
            bg.paste(screen, (px + BEZEL, py + BEZEL), self.screen_mask)
            bg.paste(self.bezel, (px, py), self.bezel)
        draw_end(bg, t)
        return bg


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "preview"
    r = Renderer()
    os.makedirs(OUT, exist_ok=True)
    if mode == "preview":
        times = [0.3, 1.4, 4.0, 5.1, 6.1, 6.7, 7.8, 8.9, 10.3, 12.0, 13.6, 14.7]
        sheet = Image.new("RGB", (6 * 360, 2 * 640))
        for i, t in enumerate(times):
            fr = r.frame(t).resize((360, 640), Image.LANCZOS)
            sheet.paste(fr, ((i % 6) * 360, (i // 6) * 640))
        sheet.save(os.path.join(OUT, "preview.png"))
        print("preview.png", [round(t, 1) for t in times])
        return
    if mode == "frame":
        t = float(sys.argv[2])
        r.frame(t).save(os.path.join(OUT, "frame_%0.2f.png" % t))
        return
    wav = os.path.join(OUT, "sfx.wav")
    render_audio(wav)
    out = os.path.join(OUT, "w0y-tiktok-ad.mp4")
    total = int(DUR * FPS)
    cmd = [
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % (W, H), "-r", str(FPS), "-i", "-",
        "-i", wav,
        "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", out,
    ]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for i in range(total):
        p.stdin.write(r.frame(i / FPS).tobytes())
        if i % 120 == 0:
            print("кадр", i, "/", total, flush=True)
    p.stdin.close()
    p.wait()
    silent = os.path.join(OUT, "w0y-tiktok-ad-silent.mp4")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", out, "-an", "-c:v", "copy", silent], check=True)
    print("готово:", out)


if __name__ == "__main__":
    main()

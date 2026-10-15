#!/usr/bin/env python3
"""Ролик w0y для TikTok в стиле Hairline: 1080×1920, 60 fps, 30 с, со звуком.

Весь монтаж привязан к настоящей сетке трека «Crush x Burnt Rice» (замер по
файлу, analyze.py): доля B = 0.39735 с, сильные удары — на долях 0/16/…,
ролик начинается ровно на доле 48 трека (S = 19.1918 с). Картинка реагирует
на звук: пульс фона и телефона берётся из баса трека, визуализатор в телефоне
рисуется по спектру этого же куска (cache/bands.json).

    python3 tools/ad/analyze.py 19.1918      # спектр трека → cache/bands.json
    python3 tools/ad/make_ad.py clips        # куски клипов → cache/clip_*
    python3 tools/ad/make_ad.py preview      # контактный лист кадров
    python3 tools/ad/make_ad.py frame 12.5   # один кадр
    python3 tools/ad/make_ad.py render       # out/w0y-tiktok-ad-30s*.mp4

Структура по долям (n): 0–16 заход, 16 — дроп, 16–45 функции приложения,
45–52 спад, 52–72 «чем отличается», 72 — только значок.
"""
import json
import math
import os
import random
import struct
import subprocess
import sys
import wave

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SHOTS2 = os.path.join(HERE, "shots2")
CACHE = os.path.join(HERE, "cache")
OUT = os.path.join(HERE, "out")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")
MP3 = os.path.expanduser("~/Загрузки/2hollis - Crush x Shawn Wasabi -BURNT RICE (Mashup).mp3")

W, H, FPS = 1080, 1920, 60
DUR = 30.0
B = 0.39735  # доля трека, с
S = 19.1918  # доля 48 трека = начало ролика


def nb(n):
    """Время (с ролика) доли n."""
    return n * B


BG = (10, 10, 12)
LINE = (46, 46, 54)
TEXT = (244, 244, 246)
MUTED = (150, 150, 160)
BLUE = (74, 125, 251)
GOLD = (232, 170, 90)
DOT = (36, 36, 44)

SW, SH = 660, 1481  # экран телефона (пропорции скриншота 1080×2424)
BEZEL = 14
PHONE_W, PHONE_H = SW + 2 * BEZEL, SH + 2 * BEZEL
PHONE_X = (W - PHONE_W) // 2
PHONE_Y = 520
STATUS_CUT = 66  # строка состояния (часы/батарея) заменяется фоном

# Квадрат визуализатора на экране плеера (координаты экрана 660×1481).
VX0, VY0, VX1, VY1 = 81, 233, 581, 733
VW = VX1 - VX0


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
    if t <= 0:
        return 0.0
    wd = omega * math.sqrt(1 - zeta * zeta)
    return 1 - math.exp(-zeta * omega * t) * (math.cos(wd * t) + zeta * omega / wd * math.sin(wd * t))


# ------------------------------------------------------------------ шрифты
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


def draw_text(d, xy, txt, f, fill, spacing=0):
    x, y = xy
    if not spacing:
        d.text((x, y), txt, font=f, fill=fill)
        return
    for c in txt:
        d.text((x, y), c, font=f, fill=fill)
        x += f.getlength(c) + spacing


def rounded_mask(size, radius, scale=4):
    w, h = size
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), radius=radius * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


# ------------------------------------------------------------------ звук трека → признаки
class Audio:
    """Огибающие полос и «пинок» баса по кадрам (60 к/с) из cache/bands.json."""

    def __init__(self):
        d = json.load(open(os.path.join(CACHE, "bands.json")))
        self.n = len(d["low"])
        self.nb = len(d["bands"])
        self.env = []
        for b in d["bands"]:
            ref = sorted(b)[int(len(b) * 0.97)] or 1.0
            prev, e = 0.0, []
            for v in b:
                prev = max(min(1.3, v / ref), prev * 0.80)
                e.append(prev)
            self.env.append(e)
        # Пики с задержкой удержания (колпачки визуализатора).
        self.cap = []
        for e in self.env:
            c, prev, hold = [], 0.0, 0
            for v in e:
                if v >= prev:
                    prev, hold = v, 7
                elif hold > 0:
                    hold -= 1
                else:
                    prev = max(v, prev - 0.022)
                c.append(prev)
            self.cap.append(c)
        low = d["low"]
        ref = sorted(low)[int(len(low) * 0.97)] or 1.0
        lown = [min(1.3, v / ref) for v in low]
        onset = [0.0] + [max(0.0, lown[i] - lown[i - 1]) for i in range(1, self.n)]
        oref = sorted(onset)[int(len(onset) * 0.96)] or 1.0
        self.kick, prev = [], 0.0
        for o in onset:
            prev = max(min(1.2, o / oref * 0.9), prev * 0.90)
            self.kick.append(prev)
        self.level, prev = [], 0.0
        for v in lown:
            prev = prev * 0.93 + v * 0.07
            self.level.append(prev)

    def _i(self, t):
        return int(clamp(t * FPS, 0, self.n - 1))

    def spec(self, t, n, delay=0.0, caps=False):
        i = self._i(t - delay)
        src = self.cap if caps else self.env
        out = []
        for k in range(n):
            p = k / max(1, n - 1) * (self.nb - 1)
            a = int(p)
            f = p - a
            b = min(self.nb - 1, a + 1)
            out.append(src[a][i] * (1 - f) + src[b][i] * f)
        return out

    def pulse(self, t):
        return self.kick[self._i(t)]

    def glow(self, t):
        return self.level[self._i(t)]

    def first_onset(self, t0, thr=0.5):
        i = int(t0 * FPS)
        while i < self.n and self.kick[i] < thr:
            i += 1
        return i / FPS


# ------------------------------------------------------------------ ассеты
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
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), rounded_mask((size, size), int(size * 0.24)))
    return out


def clean_status(img):
    """Убирает часы и батарею из кадра экрана: повторяет строку под ними."""
    row = img.crop((0, STATUS_CUT + 4, SW, STATUS_CUT + 5)).resize((SW, STATUS_CUT + 4))
    img.paste(row, (0, 0))
    return img


def load_shot(name):
    im = Image.open(os.path.join(SHOTS2, name + ".png")).convert("RGB").resize((SW, SH), Image.LANCZOS)
    return clean_status(im)


def make_backgrounds():
    base = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(base)
    for y in range(24, H, 48):
        for x in range(24, W, 48):
            d.ellipse((x - 1.5, y - 1.5, x + 1.5, y + 1.5), fill=DOT)
    g = Image.new("L", (W, H), 0)
    ImageDraw.Draw(g).ellipse((W // 2 - 520, 980 - 520, W // 2 + 520, 980 + 520), fill=255)
    g = g.filter(ImageFilter.GaussianBlur(220)).point(lambda v: int(v * 0.34))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    glow.paste(Image.new("RGB", (W, H), BLUE), (0, 0), g)
    return base, ImageChops.add(base, glow)


def make_bezel():
    layer = Image.new("RGBA", (PHONE_W, PHONE_H), (0, 0, 0, 0))
    body = rounded_mask((PHONE_W, PHONE_H), 78)
    hole = Image.new("L", (PHONE_W, PHONE_H), 0)
    hole.paste(rounded_mask((SW, SH), 64), (BEZEL, BEZEL))
    ring = ImageChops.subtract(body, hole)
    layer.paste(Image.new("RGBA", (PHONE_W, PHONE_H), (20, 20, 24, 255)), (0, 0), ring)
    edge = Image.new("L", (PHONE_W, PHONE_H), 0)
    ImageDraw.Draw(edge).rounded_rectangle((0, 0, PHONE_W - 1, PHONE_H - 1), radius=78, outline=255, width=2)
    layer.paste(Image.new("RGBA", (PHONE_W, PHONE_H), (62, 62, 72, 255)), (0, 0), edge)
    cam = Image.new("L", (PHONE_W, PHONE_H), 0)
    ImageDraw.Draw(cam).ellipse((PHONE_W // 2 - 13, BEZEL + 22, PHONE_W // 2 + 13, BEZEL + 48), fill=255)
    layer.paste(Image.new("RGBA", (PHONE_W, PHONE_H), (6, 6, 8, 255)), (0, 0), cam)
    return layer


def make_shadow():
    pad = 90
    sh = Image.new("L", (PHONE_W + pad * 2, PHONE_H + pad * 2), 0)
    ImageDraw.Draw(sh).rounded_rectangle((pad, pad + 30, pad + PHONE_W, pad + 30 + PHONE_H), radius=78, fill=255)
    return sh.filter(ImageFilter.GaussianBlur(40)).point(lambda v: int(v * 0.65)), pad


def zoom(img, s, cx, cy):
    if s <= 1.0001:
        return img
    w, h = img.size
    cw, ch = w / s, h / s
    x0 = clamp(cx - cw / 2, 0, w - cw)
    y0 = clamp(cy - ch / 2, 0, h - ch)
    return img.crop((int(x0), int(y0), int(x0 + cw), int(y0 + ch))).resize((w, h), Image.BICUBIC)


# ------------------------------------------------------------------ клипы
CLIP_WINDOWS = {
    "sound": ("clips/sound.mp4", 2.9, 1.8),
    "stand": ("clips/stand.mp4", 0.4, 1.8),
    "editor": ("clips/editor.mp4", 0.2, 1.6),
    "settings": ("clips/settings.mp4", 0.9, 1.8),
}


def build_clips():
    for k, (f, ss, d) in CLIP_WINDOWS.items():
        out = os.path.join(CACHE, "clip_" + k)
        os.makedirs(out, exist_ok=True)
        for x in os.listdir(out):
            os.remove(os.path.join(out, x))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", str(ss), "-t", str(d), "-i", os.path.join(HERE, f),
                        "-vf", "fps=60,scale=%d:%d:flags=lanczos" % (SW, SH), "-q:v", "2", out + "/%04d.jpg"], check=True)
        print(k, len(os.listdir(out)))


_clip_cache = {}


def clip_frame(name, local):
    d = os.path.join(CACHE, "clip_" + name)
    n = len(os.listdir(d))
    i = int(clamp(local * FPS, 0, n - 1)) + 1
    key = (name, i)
    if key not in _clip_cache:
        if len(_clip_cache) > 40:
            _clip_cache.clear()
        _clip_cache[key] = clean_status(Image.open(os.path.join(d, "%04d.jpg" % i)).convert("RGB"))
    return _clip_cache[key]


# ------------------------------------------------------------------ визуализатор по спектру
class Viz:
    """Рисует Lines / Orbit / Terrain в квадрате плеера по спектру трека."""

    def __init__(self, audio):
        self.au = audio
        base = clean_status(Image.open(os.path.join(CACHE, "viz_base.png")).convert("RGB").resize((SW, SH), Image.LANCZOS))
        self.base = base
        cover = Image.open(os.path.join(SHOTS2, "player.png")).convert("RGB").crop((150, 408, 936, 1190)).resize((VW, VW), Image.LANCZOS)
        bg = cover.filter(ImageFilter.GaussianBlur(30))
        bg = Image.blend(Image.new("RGB", (VW, VW), (10, 6, 8)), bg, 0.34)
        self.bg = bg
        self.mask = rounded_mask((VW, VW), 30)

    def frame(self, style, t, t0=0.0):
        img = self.base.copy()
        sq = self.bg.copy()
        sz = VW * 2
        ov = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        au = self.au
        if style == "orbit":
            self._orbit(d, sz, au, t)
        elif style == "lines":
            self._lines(d, sz, au, t)
        else:
            self._terrain(d, sz, au, t)
        ov = ov.resize((VW, VW), Image.LANCZOS)
        sq.paste(ov, (0, 0), ov)
        # Тонкая синяя рамка, как у обложки в плеере.
        ImageDraw.Draw(sq).rounded_rectangle((0, 0, VW - 1, VW - 1), radius=30, outline=(74, 125, 251), width=2)
        img.paste(sq, (VX0, VY0), self.mask)
        return img

    def _orbit(self, d, sz, au, t):
        cx = cy = sz / 2
        n = 72
        r0 = sz * 0.20
        sp = au.spec(t, 37)
        cp = au.spec(t, 37, caps=True)
        rot = t * 0.12
        for ring, a in ((0.55, 120), (0.78, 90), (1.0, 70)):
            r = r0 * ring
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(225, 225, 232, a), width=2)
        for i in range(n):
            j = abs(i - n / 2) / (n / 2)  # 0 внизу … 1 вверху
            k = min(36, int(j * 36))
            v = sp[k] ** 0.95
            c = cp[k] ** 0.95
            ang = (i / n) * 2 * math.pi + math.pi / 2 + rot
            ca, sa = math.cos(ang), math.sin(ang)
            L = sz * (0.012 + 0.20 * v)
            r1 = r0 * 1.08
            d.line((cx + ca * r1, cy + sa * r1, cx + ca * (r1 + L), cy + sa * (r1 + L)), fill=(246, 246, 250, 235), width=5)
            rc = r1 + sz * (0.026 + 0.215 * c)
            d.ellipse((cx + ca * rc - 6, cy + sa * rc - 6, cx + ca * rc + 6, cy + sa * rc + 6), fill=GOLD + (230,))
        pa = (t * 0.35) % (2 * math.pi) - math.pi / 2
        px, py = cx + math.cos(pa) * r0 * 1.0, cy + math.sin(pa) * r0 * 1.0
        d.ellipse((px - 9, py - 9, px + 9, py + 9), fill=BLUE + (255,))

    def _lines(self, d, sz, au, t):
        n = 44
        x0, x1 = sz * 0.05, sz * 0.95
        base = sz * 0.60
        sp = au.spec(t, n)
        cp = au.spec(t, n, caps=True)
        for i in range(n):
            x = lerp(x0, x1, i / (n - 1))
            v = sp[i] ** 0.9
            c = cp[i] ** 0.9
            h = sz * (0.012 + 0.40 * v)
            d.line((x, base, x, base - h), fill=(246, 246, 250, 235), width=7)
            d.line((x, base + 6, x, base + 6 + h * 0.32), fill=(246, 246, 250, 70), width=7)
            yc = base - sz * (0.03 + 0.405 * c)
            d.ellipse((x - 8, yc - 8, x + 8, yc + 8), fill=GOLD + (235,))

    def _terrain(self, d, sz, au, t):
        rows = 13
        n = 56
        x0, x1 = sz * 0.07, sz * 0.93
        for k in range(rows - 1, -1, -1):
            sp = au.spec(t, n, delay=k * 0.11)
            y0 = sz * 0.88 - k * sz * 0.052
            pts = []
            for i in range(n):
                x = lerp(x0, x1, i / (n - 1))
                edge = math.sin(math.pi * i / (n - 1)) ** 0.8
                pts.append((x, y0 - sp[i] * sz * 0.20 * edge))
            d.polygon(pts + [(x1, sz * 0.97), (x0, sz * 0.97)], fill=(18, 9, 11, 255))
            col = GOLD + (255,) if k == 0 else (240, 240, 246, int(235 - k * 11))
            d.line(pts, fill=col, width=5 if k == 0 else 4, joint="curve")


# ------------------------------------------------------------------ телефон и экраны
class Screens:
    def __init__(self, audio):
        self.shots = {n: load_shot(n) for n in ("home", "player", "playlist", "yours", "yours2", "search")}
        self.viz = Viz(audio)

    def still(self, name, t, t0, push=0.06, span=3.0, cx=0.5, cy=0.45):
        s = 1 + push * clamp((t - t0) / span)
        return zoom(self.shots[name], s, SW * cx, SH * cy)


# Экраны по долям: (n0, n1, фабрика). Порядок = порядок показа.
def screen_program(sc):
    P = []

    def add(n0, n1, fn):
        P.append((nb(n0), nb(n1), fn))

    add(0, 8, lambda t, t0: sc.still("home", t, t0, 0.07, nb(8)))
    add(8, 12, lambda t, t0: sc.still("player", t, t0, 0.05, nb(4), 0.5, 0.42))
    add(12, 13, lambda t, t0: sc.still("playlist", t, t0, 0.04, nb(1)))
    add(13, 14, lambda t, t0: sc.still("yours2", t, t0, 0.04, nb(1)))
    add(14, 15, lambda t, t0: sc.still("search", t, t0, 0.04, nb(1)))
    add(15, 16, lambda t, t0: sc.still("home", t, t0, 0.05, nb(1)))
    add(16, 20, lambda t, t0: sc.viz.frame("orbit", t))
    add(20, 22, lambda t, t0: sc.viz.frame("lines", t))
    add(22, 24, lambda t, t0: sc.viz.frame("terrain", t))
    add(24, 28, lambda t, t0: clip_frame("sound", t - t0))
    add(28, 32, lambda t, t0: clip_frame("stand", t - t0))
    add(32, 34, lambda t, t0: sc.still("yours2", t, t0, 0.16, nb(2), 0.5, 0.82))
    add(34, 36, lambda t, t0: clip_frame("editor", t - t0))
    add(36, 40, lambda t, t0: clip_frame("settings", t - t0))
    add(40, 46, lambda t, t0: sc.still("home", t, t0, 0.10, nb(6), 0.5, 0.35))
    return P


def screen_at(prog, t):
    for t0, t1, fn in prog:
        if t0 <= t < t1:
            return fn(t, t0)
    return prog[-1][2](t, prog[-1][0])


CUTS = [nb(n) for n in (0, 8, 12, 13, 14, 15, 16, 20, 22, 24, 28, 32, 34, 36, 40)]

PHONE_IN = 0.0
PHONE_OUT = nb(45)

# ------------------------------------------------------------------ заголовки
# (n0, n1, метка, строки [[(текст, цвет)]]) — смена жёсткая, строго на доле.
SCENES = [
    (0, 8, "TEXFI  /  W0Y", [[("YouTube Music,", TEXT)], [("minus the ads.", BLUE)]]),
    (8, 16, "01  /  LISTEN", [[("Built for", TEXT)], [("listening.", BLUE)]]),
    (16, 24, "02  /  VISUALIZER", [[("Live visualizer.", TEXT)], [("No microphone.", BLUE)]]),
    (24, 28, "03  /  SOUND", [[("Speed, pitch, reverb", TEXT)], [("per song.", BLUE)]]),
    (28, 32, "04  /  STAND MODE", [[("Stand mode.", TEXT)], [("A clock that plays.", BLUE)]]),
    (32, 36, "05  /  PLAYLISTS", [[("Colour and icon", TEXT)], [("per playlist.", BLUE)]]),
    (36, 40, "06  /  SETTINGS", [[("Every detail", TEXT)], [("is a setting.", BLUE)]]),
    (40, 45, "07  /  HOME", [[("Speed dial", TEXT)], [("for your tracks.", BLUE)]]),
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
    for n0, n1, label, lines in SCENES:
        if not (nb(n0) <= t < nb(n1)):
            continue
        drawn = True
        local = t - nb(n0)
        la = out_cubic(local / 0.28)
        f = font("mono", 26)
        draw_text(d, (TITLE_X, 40), label, f, MUTED + (int(255 * la),), spacing=4)
        lw = text_width(label, f, 4)
        d.line((TITLE_X + lw + 24, 52, W - TITLE_X, 52), fill=LINE + (int(255 * la),), width=2)
        tf, size = fit_font(lines, TITLE_MAX)
        for i, line in enumerate(lines):
            a = clamp(out_cubic((local - 0.05 * i) / 0.3))
            dy = (1 - out_cubic((local - 0.05 * i) / 0.34)) * 40
            x = TITLE_X
            y = 95 + i * int(size * 1.18) + dy
            for seg, col in line:
                d.text((x, y), seg, font=tf, fill=col + (int(255 * a),))
                x += tf.getlength(seg)
    if drawn:
        base.paste(layer, (0, 170), layer)


# Заявления «чем отличается»: (n0, n1, слой, [(строки, подпись)])
def draw_claims(base, t, au):
    if not (nb(46) <= t < nb(71.6)):
        return
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    la = out_cubic((t - nb(46)) / 0.4)
    f = font("mono", 26)
    label = "WHAT SETS IT APART"
    draw_text(d, (TITLE_X, 210), label, f, MUTED + (int(255 * la),), spacing=4)
    lw = text_width(label, f, 4)
    d.line((TITLE_X + lw + 24, 222, W - TITLE_X, 222), fill=LINE + (int(255 * la),), width=2)

    def block(n_in, n_out, y, words, tag, size=132):
        if not (nb(n_in) <= t < nb(n_out)):
            return
        local = t - nb(n_in)
        tf = font("display", size)
        for i, (seg, col) in enumerate(words):
            a = clamp(out_cubic((local - 0.05 * i) / 0.3))
            dy = (1 - out_cubic((local - 0.05 * i) / 0.38)) * 60
            d.text((TITLE_X, y + i * int(size * 1.12) + dy), seg, font=tf, fill=col + (int(255 * a),))
        wlast = tf.getlength(words[-1][0])
        ly = y + (len(words) - 1) * int(size * 1.12) + int(size * 1.08)
        q = out_cubic((local - 0.15) / 0.5)
        d.rounded_rectangle((TITLE_X, ly + 10, TITLE_X + max(4, wlast * q), ly + 14), radius=2, fill=BLUE + (255,))
        ta = out_cubic((local - 0.25) / 0.3)
        draw_text(d, (TITLE_X, ly + 40 + (1 - ta) * 14), tag, font("mono", 28), MUTED + (int(255 * ta),), spacing=4)

    block(48, 56, 560, [("Zero", TEXT), ("ads.", BLUE)], "NO BANNERS.  NO PRE-ROLLS.")
    block(56, 64, 560, [("Zero", TEXT), ("telemetry.", BLUE)], "NO ANALYTICS.  NO TRACKERS.")
    block(64, 71.6, 560, [("Open", TEXT), ("source.", BLUE)], "AGPL-3.0  ·  CODE ON GITHUB")
    base.paste(layer, (0, 0), layer)


# ------------------------------------------------------------------ финал: только значок
def draw_icon_end(base, t, au, icon):
    t0 = nb(72)
    if t < t0:
        return
    local = t - t0
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = W // 2, 840
    ic = 300
    k = spring(local, 0.5, 15.0)
    pulse = au.pulse(t) if t > nb(73) else 0.0
    size = ic * clamp(k, 0, 1.25) * (1 + 0.035 * pulse)
    if size > 2:
        im = icon.resize((int(size), int(size)), Image.LANCZOS)
        layer.paste(im, (int(cx - size / 2), int(cy - size / 2)), im)
    second = END_HIT
    for tr in (t0, second):
        rp = clamp((t - tr) / 0.9)
        if 0 < rp < 1:
            rr = ic * (0.58 + 1.25 * out_cubic(rp))
            d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=BLUE + (int(210 * (1 - rp)),), width=4)
    base.paste(layer, (0, 0), layer)


END_HIT = nb(72) + 0.4  # уточняется по басу в Renderer.__init__


# ------------------------------------------------------------------ кадр
class Renderer:
    def __init__(self):
        global END_HIT
        self.au = Audio()
        END_HIT = self.au.first_onset(28.8, 0.35)
        self.base, self.lit = make_backgrounds()
        self.bezel = make_bezel()
        self.shadow, self.pad = make_shadow()
        self.screen_mask = rounded_mask((SW, SH), 64)
        self.sc = Screens(self.au)
        self.prog = screen_program(self.sc)
        self.icon = make_icon(320)

    def phone(self, bg, t):
        enter = clamp(spring((t + 0.18) / 1.1, 0.72, 13.0), 0, 1.15)
        leave = in_cubic((t - PHONE_OUT) / 0.95)
        if leave >= 1:
            return
        dy = (1 - enter) * 1000 + leave * 1500
        # Удар на каждой смене экрана + пульс баса.
        bump = 0.0
        for tc in CUTS:
            if tc <= t:
                age = t - tc
                bump = 0.040 * math.exp(-age * 9.0)
        ps = 1.0 + bump + 0.014 * self.au.pulse(t)
        screen = screen_at(self.prog, t)
        lay = Image.new("RGBA", (PHONE_W, PHONE_H), (0, 0, 0, 0))
        lay.paste(screen, (BEZEL, BEZEL), self.screen_mask)
        lay.alpha_composite(self.bezel)
        sh = Image.new("RGBA", (PHONE_W + 2 * self.pad, PHONE_H + 2 * self.pad), (0, 0, 0, 0))
        sh.paste((0, 0, 0, 255), (0, 0), self.shadow)
        lay2 = Image.new("RGBA", sh.size, (0, 0, 0, 0))
        lay2.alpha_composite(sh)
        lay2.alpha_composite(lay, (self.pad, self.pad))
        if abs(ps - 1) > 0.002:
            nw, nh = int(lay2.width * ps), int(lay2.height * ps)
            lay2 = lay2.resize((nw, nh), Image.BICUBIC)
        cx = PHONE_X + PHONE_W / 2
        cy = PHONE_Y + dy + PHONE_H / 2
        bg.paste(lay2, (int(cx - lay2.width / 2), int(cy - lay2.height / 2)), lay2)

    def frame(self, t):
        au = self.au
        g = clamp(0.40 + 0.35 * au.glow(t) + 0.45 * au.pulse(t) * (1 if (nb(0) <= t < nb(8) or nb(16) <= t < nb(46) or t >= nb(72)) else 0.3))
        bg = Image.blend(self.base, self.lit, clamp(g))
        if t < nb(46):
            self.phone(bg, t)
        draw_titles(bg, t)
        draw_claims(bg, t, au)
        draw_icon_end(bg, t, au, self.icon)
        # Вспышка на дропе.
        age = t - nb(16)
        if 0 <= age < 0.5:
            a = 0.55 * math.exp(-age * 11)
            bg = Image.blend(bg, Image.new("RGB", (W, H), (255, 255, 255)), a)
        # Затемнение в конце.
        if t > DUR - 0.5:
            bg = Image.blend(bg, Image.new("RGB", (W, H), BG), 0.9 * clamp((t - (DUR - 0.5)) / 0.5))
        return bg


# ------------------------------------------------------------------ звук (эффекты поверх трека)
SR = 44100


def render_sfx(path, au):
    n = int(DUR * SR)
    buf = [0.0] * n
    rnd = random.Random(7)

    def add(t0, samples, gain=1.0):
        i0 = int(t0 * SR)
        for i, v in enumerate(samples):
            if 0 <= i0 + i < n:
                buf[i0 + i] += v * gain

    def tick():
        return [math.sin(2 * math.pi * 2200 * (i / SR)) * math.exp(-(i / SR) * 90) * 0.7 + rnd.uniform(-1, 1) * math.exp(-(i / SR) * 200) * 0.3 for i in range(int(0.06 * SR))]

    def whoosh(dur, up=True, amp=1.4):
        out, lp = [], 0.0
        for i in range(int(dur * SR)):
            x = i / (dur * SR)
            env = (x ** 2.2) if up else (1 - x) ** 2
            lp += (rnd.uniform(-1, 1) - lp) * (0.02 + 0.40 * (x if up else 1 - x))
            out.append(lp * env * amp)
        return out

    def boom():
        return [math.sin(2 * math.pi * (46 + 90 * math.exp(-(i / SR) * 18)) * (i / SR)) * math.exp(-(i / SR) * 5.5) for i in range(int(0.7 * SR))]

    def shimmer(freq, dur):
        return [(math.sin(2 * math.pi * freq * (i / SR)) + 0.4 * math.sin(2 * math.pi * freq * 2.01 * (i / SR))) * math.exp(-(i / SR) * 3.2) for i in range(int(dur * SR))]

    # Тики на каждой смене экрана.
    for tc in CUTS[1:]:
        add(tc, tick(), 0.22)
    # Раскрутка перед дропом и сам дроп.
    add(nb(12), whoosh(nb(4), True, 1.2), 0.20)
    add(nb(16), boom(), 0.30)
    # Уход телефона и заявления — мягкие вуши на доле.
    add(nb(45), whoosh(0.9, False, 1.2), 0.16)
    for nn in (48, 56, 64):
        add(nb(nn), whoosh(0.35, False, 1.0), 0.16)
        add(nb(nn), tick(), 0.18)
    # Подъём в значок и сам значок.
    add(nb(72) - 1.05, whoosh(1.05, True, 1.5), 0.20)
    add(nb(72), boom(), 0.22)
    add(nb(72), shimmer(1568, 1.2), 0.07)
    add(END_HIT, shimmer(784, 1.0), 0.05)
    peak = max(abs(v) for v in buf) or 1.0
    scale = 0.9 / peak if peak > 0.9 else 1.0
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for v in buf:
            s = int(max(-1, min(1, v * scale)) * 32000)
            frames += struct.pack("<hh", s, s)
        w.writeframes(bytes(frames))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "preview"
    os.makedirs(OUT, exist_ok=True)
    if mode == "clips":
        build_clips()
        return
    r = Renderer()
    if mode == "preview":
        ns = [2, 6, 10, 12.5, 14.5, 16.3, 18, 21, 23, 26, 30, 34, 38, 42, 47, 50, 54, 58, 66, 70, 72.2, 73.5, 74.5, 75.3]
        times = [min(nb(n), DUR - 0.01) for n in ns]
        cols, tw, th = 8, 270, 480
        sheet = Image.new("RGB", (cols * tw, 3 * th))
        for i, t in enumerate(times):
            fr = r.frame(t).resize((tw, th), Image.LANCZOS)
            sheet.paste(fr, ((i % cols) * tw, (i // cols) * th))
        sheet.save(os.path.join(OUT, "preview.png"))
        print("preview.png", [round(t, 2) for t in times], "END_HIT", round(END_HIT, 3))
        return
    if mode == "frame":
        t = float(sys.argv[2])
        r.frame(t).save(os.path.join(OUT, "frame_%0.2f.png" % t))
        return
    sfx = os.path.join(OUT, "sfx.wav")
    render_sfx(sfx, r.au)
    video = os.path.join(OUT, "_video30.mp4")
    total = int(DUR * FPS)
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % (W, H), "-r", str(FPS), "-i", "-",
           "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p", "-movflags", "+faststart", video]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for i in range(total):
        p.stdin.write(r.frame(i / FPS).tobytes())
        if i % 120 == 0:
            print("кадр", i, "/", total, flush=True)
    p.stdin.close()
    p.wait()
    final = os.path.join(OUT, "w0y-tiktok-ad-30s.mp4")
    flt = ("[1:a]afade=t=in:d=0.04,afade=t=out:st=%.2f:d=%.2f,volume=0.92[m];[2:a]volume=1.0[s];"
           "[m][s]amix=inputs=2:normalize=0,alimiter=limit=0.95[a]") % (DUR - 0.9, 0.9)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", video, "-ss", "%.4f" % S, "-t", str(DUR), "-i", MP3, "-i", sfx,
                    "-filter_complex", flt, "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "256k", "-shortest", final], check=True)
    only_sfx = os.path.join(OUT, "w0y-tiktok-ad-30s-no-track.mp4")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", video, "-i", sfx, "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", only_sfx], check=True)
    print("готово:", final)


if __name__ == "__main__":
    main()

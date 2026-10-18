#!/usr/bin/env python3
"""Звуки для ролика: удар на каждый «прыжок» камеры, свист на каждом переходе, щелчок на клике, нарастание перед дропом.
Времена берутся из сценария PromoRender.kt, поэтому звук всегда совпадает с картинкой.
  tools/promo/make_sfx.py <PromoRender.kt> <выход.wav>
"""
import math, random, re, struct, sys, wave

SR = 44100
LEN = 46.0
src = open(sys.argv[1], encoding="utf-8").read()
buf = [0.0] * int(SR * LEN)
rnd = random.Random(7)


def add(t, samples, gain):
    i = int(t * SR)
    for k, v in enumerate(samples):
        if 0 <= i + k < len(buf):
            buf[i + k] += v * gain


def thud(freq=58.0, dur=0.32):
    n = int(SR * dur)
    out = []
    ph = 0.0
    for k in range(n):
        x = k / n
        f = freq * (1.0 + 1.6 * math.exp(-x * 18))
        ph += 2 * math.pi * f / SR
        env = math.exp(-x * 7)
        click = (rnd.random() * 2 - 1) * math.exp(-k / (SR * 0.004)) * 0.5
        out.append(math.sin(ph) * env + click)
    return out


def whoosh(dur=0.34):
    n = int(SR * dur)
    out = []
    lp = 0.0
    for k in range(n):
        x = k / n
        a = 0.04 + 0.5 * x  # фильтр раскрывается к концу
        lp += a * ((rnd.random() * 2 - 1) - lp)
        out.append(lp * (x ** 1.5) * 1.6)
    return out


def tick():
    n = int(SR * 0.05)
    return [math.sin(2 * math.pi * 2200 * k / SR) * math.exp(-k / (SR * 0.008)) for k in range(n)]


def riser(dur=2.0):
    n = int(SR * dur)
    out = []
    lp = 0.0
    ph = 0.0
    for k in range(n):
        x = k / n
        lp += (0.02 + 0.45 * x) * ((rnd.random() * 2 - 1) - lp)
        ph += 2 * math.pi * (180 + 1400 * x * x) / SR
        out.append((lp * 1.3 + math.sin(ph) * 0.25) * (x ** 2))
    return out


cams = sorted({float(t) for t in re.findall(r"s\.at\(([\d.]+)\) \{[^}]*x\.camera\(", src)})
cuts = sorted({float(t) for t in re.findall(r"cut\(([\d.]+), Tr\.", src)} | {6.0, 43.0})
clicks = sorted({float(t) for t in re.findall(r"x\.click\(([\d.]+)", src)})
words = [1.0, 2.0, 6.5, 43.0, 43.5] + [float(t) for t in re.findall(r"cut\(([\d.]+), Tr\.", src)]

add(4.0, riser(2.0), 0.55)
for t in cams:
    add(t, thud(), 0.30)
for t in cuts:
    add(t - 0.30, whoosh(), 0.45)
    add(t, thud(48.0, 0.45), 0.55)
for t in clicks:
    add(t, tick(), 0.25)
for t in words:
    add(t, thud(70.0, 0.25), 0.35)
add(1.0, thud(45.0, 0.6), 0.6)
add(6.0, thud(40.0, 0.9), 0.9)
add(6.0, whoosh(0.9)[::-1], 0.5)

peak = max(abs(v) for v in buf) or 1.0
scale = 0.9 / peak
with wave.open(sys.argv[2], "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v * scale)) * 32767)) for v in buf))
print(f"cams={len(cams)} cuts={len(cuts)} clicks={len(clicks)}")

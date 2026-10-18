#!/usr/bin/env python3
"""Темп и сетка долей трека для монтажа промо-ролика (чистый Python, без numpy).

    python3 tools/promo/analyze_beats.py "<трек>.webm"

Печатает BPM, фазу первой доли, энергию по секундам и пишет cache/beats.json:
{"bpm":..,"beat":..,"first":..,"energy":[[сек, RMS]...]}.
"""
import array
import json
import math
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
src = sys.argv[1]
SR = 4000
FPS = 100  # кадров огибающей в секунду


def pcm(af):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", src, "-ac", "1", "-ar", str(SR), "-af", af, "-f", "s16le", "-"], capture_output=True, check=True).stdout
    a = array.array("h")
    a.frombytes(raw)
    return a


def envelope(a):
    n = SR // FPS
    return [math.sqrt(sum(v * v for v in a[i * n:(i + 1) * n]) / n) for i in range(len(a) // n)]


kick = envelope(pcm("lowpass=f=140"))
full = envelope(pcm("anull"))
# Всплески баса: положительная разность с лёгким сглаживанием.
onset = [max(0.0, kick[i] - kick[i - 1]) for i in range(1, len(kick))]
mean = sum(onset) / len(onset)
onset = [v - mean for v in onset]

best = (0, 0)
for lag in range(int(FPS * 60 / 200), int(FPS * 60 / 70) + 1):
    score = sum(onset[i] * onset[i + lag] for i in range(len(onset) - lag)) / (len(onset) - lag)
    # Мягкая подсказка в пользу 100–170 BPM: так выбирается основная доля, а не вдвое быстрее/медленнее.
    bpm = 60 * FPS / lag
    score *= 1.0 if 100 <= bpm <= 170 else 0.7
    if score > best[0]:
        best = (score, lag)
lag = best[1]
# Уточнение лага долями кадра по нескольким долям подряд.
bpm0 = 60 * FPS / lag
beat = lag / FPS
# Фаза: на каком сдвиге внутри доли бас сильнее всего.
phase_scores = []
for ph in range(lag):
    phase_scores.append(sum(onset[i] for i in range(ph, len(onset), lag)))
first = max(range(lag), key=lambda p: phase_scores[p]) / FPS
print("BPM ~ %.1f, доля %.4f с, первая доля %.3f с" % (bpm0, beat, first))
energy = [[s, sum(full[s * FPS:(s + 1) * FPS]) / FPS] for s in range(len(full) // FPS)]
for s, e in energy:
    print("%3d с %s" % (s, "#" * int(e / 120)))
json.dump({"bpm": bpm0, "beat": beat, "first": first, "energy": energy}, open(os.path.join(HERE, "cache", "beats.json"), "w"))

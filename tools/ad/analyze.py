#!/usr/bin/env python3
"""Спектр трека по кадрам (60 к/с) для звукореактивного ролика.

Берёт кусок mp3, режет на 24 лог-полосы через ffmpeg (bandpass), считает
RMS на кадр. Результат: cache/bands.json = {"fps":60,"start":S,"bands":[[...]*N],"low":[...]}.
"""
import array
import json
import math
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MP3 = os.path.expanduser("~/Загрузки/2hollis - Crush x Shawn Wasabi -BURNT RICE (Mashup).mp3")
S = float(sys.argv[1]) if len(sys.argv) > 1 else 19.071 + 0.119 - 0.119  # старт в треке
DUR = 30.0
FPS = 60
SR = 22050
NB = 24
edges = [40 * (11000 / 40) ** (i / NB) for i in range(NB + 1)]


def band(lo, hi):
    if lo < 100:
        flt = "lowpass=f=%d:p=2" % hi
    else:
        flt = "highpass=f=%d:p=2,lowpass=f=%d:p=2" % (lo, hi)
    cmd = ["ffmpeg", "-v", "error", "-ss", "%.4f" % S, "-t", "%.3f" % DUR, "-i", MP3, "-ac", "1", "-ar", str(SR),
           "-af", flt, "-f", "s16le", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    a = array.array("h")
    a.frombytes(raw)
    n = int(SR / FPS)
    out = []
    for i in range(int(DUR * FPS)):
        seg = a[i * n:(i + 1) * n]
        out.append(math.sqrt(sum(v * v for v in seg) / max(1, len(seg))) if seg else 0.0)
    return out


bands = [band(edges[i], edges[i + 1]) for i in range(NB)]
low = band(0, 140)
json.dump({"fps": FPS, "start": S, "edges": edges, "bands": bands, "low": low}, open(os.path.join(HERE, "cache", "bands.json"), "w"))
print("ok", len(bands), len(bands[0]))

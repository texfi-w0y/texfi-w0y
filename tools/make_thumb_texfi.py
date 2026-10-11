#!/usr/bin/env python3
"""Превью для YouTube (1280x720) к ролику TexFi: python3 tools/make_thumb_texfi.py -> docs/promo/texfi-thumb.png"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_promo as mp  # noqa: E402

W, H = 1280, 720
mp.W, mp.H = W, H
import make_promo_texfi as tf  # noqa: E402

tf.W, tf.H = W, H
from make_promo import AMBER, BLUE, CREAM, INK, Canvas, flat_bg, pf  # noqa: E402


def main():
    img = Image.new("RGB", (W, H))
    c = Canvas(img)
    flat_bg(c, BLUE)
    # верхняя кремовая полоса-плашка с названием
    c.rect(0, 0, W, 8, CREAM)
    c.text("TEXFI", 70 + 9, 190 + 9, pf(124), INK, "lm")
    c.text("TEXFI", 70, 190, pf(124), CREAM, "lm")
    c.text("4 APPS.", 70 + 7, 370 + 7, pf(72), INK, "lm")
    c.text("4 APPS.", 70, 370, pf(72), AMBER, "lm")
    c.text("ONE LOOK.", 70 + 7, 490 + 7, pf(72), INK, "lm")
    c.text("ONE LOOK.", 70, 490, pf(72), CREAM, "lm")
    names = [a[0] for a in tf.APPS]
    for i, n in enumerate(names):
        cx, cy, s = 860 + (i % 2) * 240, 250 + (i // 2) * 240, 200
        tf.BLUE = INK  # жёсткая тень плитки на синем фоне должна быть тёмной
        tf.tile(c, cx, cy, s, n, 1.0, True)
    c.rect(0, H - 8, W, H, CREAM)
    out = os.path.join(mp.ROOT, "docs", "promo", "texfi-thumb.png")
    img.save(out)
    print(out)


if __name__ == "__main__":
    main()

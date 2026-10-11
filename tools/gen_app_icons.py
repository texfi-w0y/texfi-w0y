#!/usr/bin/env python3
"""Генератор иконок приложения w0y.

Рисует пять вариантов в одной сетке 108×108 (как adaptive icon) и пишет:
  - assets/app-icons/<name>.svg           — целая иконка на фоне #0a090c (для превью и правок);
  - app/src/main/res/drawable/ic_<name>_fg.xml     — векторный foreground;
  - app/src/main/res/drawable/ic_<name>_mono.xml   — однотонный слой для themed icons;
  - app/src/main/res/mipmap-anydpi-v26/<name>.xml  — adaptive icon (фон — сплошной #0a090c).

Запуск: python3 tools/gen_app_icons.py  (только стандартная библиотека).
Безопасная зона Android — круг 66 dp в центре, поэтому всё рисунок лежит в 18…90.
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
BG = "#0A090C"
BLUE = "#4A7CFB"
BLUE_HI = "#7FB5FF"
SAND = "#E0A860"
WHITE = "#F4F4F6"
GREY = "#8A8A96"

# Каждый элемент: ("path", d, kind, color, extra) — kind: fill | stroke
# extra: width, alpha, cap, evenodd, gradient=(c1,c2,x1,y1,x2,y2)
LEAF = "M32 78 C27 48 50 27 78 29 C80 55 58 79 32 78 Z"


def ring(cx, cy, r):
    return f"M{cx - r} {cy} a{r} {r} 0 1 0 {2 * r} 0 a{r} {r} 0 1 0 {-2 * r} 0 Z"


def bar(cx, top, bottom, w=7):
    r = w / 2
    return f"M{cx - r} {top + r} a{r} {r} 0 0 1 {w} 0 L{cx + r} {bottom - r} a{r} {r} 0 0 1 {-w} 0 Z"


def icons():
    out = {}
    # 1. leaf — единственный не-линейный
    out["icon_leaf"] = dict(
        full=[
            ("path", LEAF, "fill", BLUE, dict(gradient=(BLUE_HI, BLUE, 40, 30, 70, 78))),
            ("path", "M31 80 L69 41", "stroke", WHITE, dict(width=3, cap="round")),
            ("path", ring(30, 81, 4.6), "fill", SAND, {}),
        ],
        mono=[
            ("path", LEAF, "fill", "#FFFFFF", {}),
            ("path", "M28 83 L66 44", "stroke", "#FFFFFF", dict(width=3.4, cap="round")),
        ],
    )
    # 2. w — по умолчанию: W и кольцо-ноль над средней вершиной
    w = "M28 40 L41 74 L54 50 L67 74 L80 40"
    out["icon_w"] = dict(
        full=[
            ("path", w, "stroke", WHITE, dict(width=7, cap="round", join="round")),
            ("path", ring(54, 33, 7.5), "stroke", BLUE, dict(width=3.6)),
        ],
        mono=[
            ("path", w, "stroke", "#FFFFFF", dict(width=7, cap="round", join="round")),
            ("path", ring(54, 33, 7.5), "stroke", "#FFFFFF", dict(width=3.6)),
        ],
    )
    # 3. bars — шесть столбиков, синий и песочный
    heights = [(40, 68), (28, 80), (36, 72), (22, 86), (34, 74), (42, 66)]
    xs = [22.5 + 12.6 * i for i in range(6)]
    colors = [WHITE, WHITE, SAND, WHITE, BLUE, WHITE]
    full = [("path", bar(x, t, b), "fill", c, {}) for x, (t, b), c in zip(xs, heights, colors)]
    mono = [("path", bar(x, t, b), "fill", "#FFFFFF", {}) for x, (t, b) in zip(xs, heights)]
    out["icon_bars"] = dict(full=full, mono=mono)
    # 4. rings — затухающие круги и песочный Play
    play = "M50 46 L50 62 L63 54 Z"
    out["icon_rings"] = dict(
        full=[
            ("path", ring(54, 54, 35), "stroke", BLUE, dict(width=2.2, alpha=0.28)),
            ("path", ring(54, 54, 28), "stroke", BLUE, dict(width=2.2, alpha=0.5)),
            ("path", ring(54, 54, 21), "stroke", BLUE, dict(width=2.2, alpha=0.8)),
            ("path", ring(54, 54, 13), "fill", SAND, {}),
            ("path", play, "fill", BG, {}),
        ],
        mono=[
            ("path", ring(54, 54, 35), "stroke", "#FFFFFF", dict(width=2.4, alpha=0.5)),
            ("path", ring(54, 54, 24), "stroke", "#FFFFFF", dict(width=2.4)),
            ("path", ring(54, 54, 13) + " " + play, "fill", "#FFFFFF", dict(evenodd=True)),
        ],
    )
    # 5. leaf_line — контур пера: белая линия, синий стержень, серые насечки
    ticks = "M43 68 L39 60 M51 60 L47 52 M59 52 L55 44 M49 66 L57 68 M58 56 L66 58"
    out["icon_leaf_line"] = dict(
        full=[
            ("path", LEAF, "stroke", WHITE, dict(width=3, join="round")),
            ("path", "M31 80 L69 41", "stroke", BLUE, dict(width=3.6, cap="round")),
            ("path", ticks, "stroke", GREY, dict(width=2.2, cap="round")),
        ],
        mono=[
            ("path", LEAF, "stroke", "#FFFFFF", dict(width=3, join="round")),
            ("path", "M28 83 L66 44", "stroke", "#FFFFFF", dict(width=3.6, cap="round")),
            ("path", ticks, "stroke", "#FFFFFF", dict(width=2.2, cap="round")),
        ],
    )
    return out


# Рисунок чуть меньше, чем сетка 18…90: на домашнем экране он выглядел крупнее прежнего значка.
SCALE = 0.7


def svg_of(items, bg=True):
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">']
    grads = []
    body = []
    for i, (_, d, kind, color, x) in enumerate(items):
        attrs = ""
        if "gradient" in x:
            c1, c2, x1, y1, x2, y2 = x["gradient"]
            grads.append(
                f'<linearGradient id="g{i}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
                f'<stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/></linearGradient>'
            )
            color = f"url(#g{i})"
        if kind == "fill":
            attrs = f'fill="{color}"'
            if x.get("alpha"):
                attrs += f' fill-opacity="{x["alpha"]}"'
            if x.get("evenodd"):
                attrs += ' fill-rule="evenodd"'
        else:
            attrs = f'fill="none" stroke="{color}" stroke-width="{x["width"]}" stroke-linecap="{x.get("cap", "butt")}" stroke-linejoin="{x.get("join", "miter")}"'
            if x.get("alpha"):
                attrs += f' stroke-opacity="{x["alpha"]}"'
        body.append(f'<path d="{d}" {attrs}/>')
    if grads:
        parts.append("<defs>" + "".join(grads) + "</defs>")
    if bg:
        parts.append(f'<rect width="108" height="108" fill="{BG}"/>')
    parts.append(f'<g transform="translate(54 54) scale({SCALE}) translate(-54 -54)">')
    parts += body
    parts.append("</g>")
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def vector_of(items):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:aapt="http://schemas.android.com/aapt"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
        f'    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">',
    ]
    for _, d, kind, color, x in items:
        c = "#" + color.lstrip("#").upper()
        head = f'    <path android:pathData="{d}"'
        if kind == "fill":
            extra = ""
            if x.get("alpha"):
                extra += f'\n        android:fillAlpha="{x["alpha"]}"'
            if x.get("evenodd"):
                extra += '\n        android:fillType="evenOdd"'
            if "gradient" in x:
                c1, c2, x1, y1, x2, y2 = x["gradient"]
                lines.append(head + extra + ">")
                lines.append('        <aapt:attr name="android:fillColor">')
                lines.append(
                    f'            <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
                    f'android:endX="{x2}" android:endY="{y2}">'
                )
                lines.append(f'                <item android:offset="0" android:color="{c1}" />')
                lines.append(f'                <item android:offset="1" android:color="{c2}" />')
                lines.append("            </gradient>")
                lines.append("        </aapt:attr>")
                lines.append("    </path>")
            else:
                lines.append(head + f'\n        android:fillColor="{c}"' + extra + " />")
        else:
            extra = f'\n        android:strokeColor="{c}"\n        android:strokeWidth="{x["width"]}"'
            extra += f'\n        android:strokeLineCap="{x.get("cap", "butt")}"'
            extra += f'\n        android:strokeLineJoin="{x.get("join", "miter")}"'
            if x.get("alpha"):
                extra += f'\n        android:strokeAlpha="{x["alpha"]}"'
            lines.append(head + extra + " />")
    lines.append("    </group>")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def adaptive(name):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/icon_bg" />\n'
        f'    <foreground android:drawable="@drawable/ic_{name}_fg" />\n'
        f'    <monochrome android:drawable="@drawable/ic_{name}_mono" />\n'
        "</adaptive-icon>\n"
    )


def main():
    res = ROOT / "app/src/main/res"
    assets = ROOT / "assets/app-icons"
    assets.mkdir(parents=True, exist_ok=True)
    (res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    for name, spec in icons().items():
        (assets / f"{name}.svg").write_text(svg_of(spec["full"]), encoding="utf-8")
        (res / f"drawable/ic_{name}_fg.xml").write_text(vector_of(spec["full"]), encoding="utf-8")
        (res / f"drawable/ic_{name}_mono.xml").write_text(vector_of(spec["mono"]), encoding="utf-8")
        (res / f"mipmap-anydpi-v26/{name}.xml").write_text(adaptive(name), encoding="utf-8")
    print("ok")


if __name__ == "__main__":
    main()

#!/usr/bin/env bash
# Склейка ролика «w0y with desktop»: беззвучное видео из PromoRender + кусок трека + звуки ударов.
#   tools/promo/assemble.sh <видео.mp4> "<трек>.webm" <выход-папка> [<sfx.wav из make_sfx.py>]
# Дроп трека — на 15.0 с, в ролике — на 6.0 с, поэтому звук берётся с 9.0 с. Длина 46 с.
set -euo pipefail
video="$1"; track="$2"; out="$3"; sfx="${4:-}"
mkdir -p "$out"
if [ -n "$sfx" ]; then
  ffmpeg -y -v error -i "$video" -ss 9.0 -t 46 -i "$track" -i "$sfx" \
    -filter_complex "[1:a]volume=0.8[m];[2:a]volume=0.9[s];[m][s]amix=inputs=2:duration=first:normalize=0,afade=t=in:st=0:d=0.4,afade=t=out:st=44:d=2,loudnorm=I=-14:TP=-1.5:LRA=11[a]" \
    -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -shortest -movflags +faststart "$out/w0y-with-desktop.mp4"
else
  ffmpeg -y -v error -i "$video" -ss 9.0 -t 46 -i "$track" \
    -filter_complex "[1:a]afade=t=in:st=0:d=0.4,afade=t=out:st=44:d=2,loudnorm=I=-14:TP=-1.5:LRA=11[a]" \
    -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -shortest -movflags +faststart "$out/w0y-with-desktop.mp4"
fi
cp "$video" "$out/w0y-with-desktop-no-track.mp4"
ls -la "$out"

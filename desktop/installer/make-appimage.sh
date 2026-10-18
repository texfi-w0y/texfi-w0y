#!/usr/bin/env bash
# Собирает w0y-linux-x86_64.AppImage из папки приложения (createDistributable) вместе с libmpv и её библиотеками.
# Нужны: appimagetool в PATH (или APPIMAGETOOL=путь), libmpv.so.2 в системе (пакет libmpv2).
# Запуск: installer/make-appimage.sh <папка w0y> <выходной файл>
set -euo pipefail
APP="${1:?папка приложения}"; OUT="${2:?выходной файл}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
AD="$(mktemp -d)/w0y.AppDir"
mkdir -p "$AD/usr/lib" "$AD/usr/share/icons/hicolor/256x256/apps"
cp -r "$APP" "$AD/usr/w0y"
cp "$HERE/src/main/resources/icons/w0y-192.png" "$AD/w0y.png"
cp "$HERE/src/main/resources/icons/w0y-192.png" "$AD/usr/share/icons/hicolor/256x256/apps/w0y.png"

# libmpv и всё, что она тянет, кроме того, что должно быть системным (libc, графика, звук, окна).
MPV="$(ldconfig -p | awk '/libmpv\.so\.2 /{print $NF; exit}')"
[ -n "$MPV" ] || { echo "libmpv.so.2 не найдена в системе" >&2; exit 1; }
EXCLUDE='linux-vdso|ld-linux|libc\.so|libm\.so|libdl|librt|libpthread|libresolv|libGL|libEGL|libGLX|libOpenGL|libdrm|libgbm|libX|libxcb|libwayland|libvdpau|libva|libasound|libpulse|libpipewire|libsystemd|libstdc\+\+|libgcc_s|libudev|libdbus|libxkbcommon'
{ echo "$MPV"; ldd "$MPV" | awk '/=> \//{print $3}'; } | sort -u | while read -r lib; do
  base="$(basename "$lib")"
  echo "$base" | grep -Eq "$EXCLUDE" && continue
  cp -L "$lib" "$AD/usr/lib/$base"
done
dpkg -s libmpv2 2>/dev/null | sed -n '/^Package:/p;/^Version:/p' > "$AD/usr/lib/libmpv-build.txt" || true

cat > "$AD/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
export LD_LIBRARY_PATH="$HERE/usr/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
exec "$HERE/usr/w0y/bin/w0y" "$@"
RUN
chmod +x "$AD/AppRun"
cat > "$AD/w0y.desktop" <<'DESK'
[Desktop Entry]
Type=Application
Name=w0y
GenericName=YouTube Music
Comment=w0y: YouTube Music client
Exec=w0y
Icon=w0y
Terminal=false
Categories=AudioVideo;Audio;Player;
StartupWMClass=com-texfi-w0y-desktop-MainKt
DESK
ARCH=x86_64 "${APPIMAGETOOL:-appimagetool}" --appimage-extract-and-run "$AD" "$OUT"

#!/usr/bin/env bash
# Запуск для разработки на отдельных данных: настоящая библиотека (~/.local/share/w0y)
# не трогается, а управляющий сокет свой, чтобы не поднимать уже запущенный w0y.
# W0Y_DEV_DATA=папка ./dev.sh   — своя папка данных (по умолчанию /tmp/w0y-dev-data)
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/toolchain/jdk}"
export XDG_DATA_HOME="${W0Y_DEV_DATA:-/tmp/w0y-dev-data}"
export W0Y_CONTROL="${XDG_RUNTIME_DIR:-/tmp}/w0y-dev-control.sock"
mkdir -p "$XDG_DATA_HOME"
exec systemd-run --user --scope -q -p MemoryMax=4500M -p CPUQuota=300% nice -n 10 \
  ./gradlew run -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 "$@"

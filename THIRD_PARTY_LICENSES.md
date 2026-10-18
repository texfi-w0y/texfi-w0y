# Сторонние компоненты w0y / Third-party components

w0y распространяется по лицензии **GNU AGPL-3.0** (файл `LICENSE`). Ниже то, что входит в сборки,
и на каких условиях. Тексты лицензий лежат в каталоге `licenses/` рядом с приложением.

w0y is licensed under **GNU AGPL-3.0** (`LICENSE`). Below is what the builds contain and under which terms.

| Компонент | Где используется | Лицензия |
|---|---|---|
| **libmpv** (mpv) и вшитый в неё **FFmpeg** | воспроизведение на Windows и Linux | GPL-2.0-or-later (сборка с GPL-кодеками) или LGPL-2.1-or-later (сборка без них); какая именно лежит в установщике, указано в журнале сборки CI и в `libmpv-build.txt` рядом с библиотекой. Совместимо с AGPL-3.0: GPL-2.0-or-later допускает объединение с GPL-3.0/AGPL-3.0. |
| **JNA** | вызов libmpv из JVM | Apache-2.0 или LGPL-2.1+ (на выбор; используется как Apache-2.0) |
| **Kotlin, kotlinx.coroutines, kotlinx.serialization** | всё | Apache-2.0 |
| **Compose Multiplatform / Jetpack Compose** | интерфейс | Apache-2.0 |
| **Ktor**, **OkHttp**, **Coil** | сеть, картинки | Apache-2.0 |
| **innertubex** (YouTube Music API) | запросы к YouTube Music | GPL-3.0 (`licenses/GPL-3.0-innertubex.txt`) |
| **Material Icons** | значки | Apache-2.0 (`licenses/Apache-2.0-material-icons.txt`) |
| **dbus-java** | MPRIS на Linux | MIT |
| **Unbounded**, **JetBrains Mono**, **Press Start 2P** | шрифты | SIL OFL-1.1 (`licenses/OFL-*.txt`) |
| **OpenJDK (JetBrains Runtime / Temurin)** | среда выполнения внутри установщика | GPL-2.0 с Classpath Exception |

Приложение не содержит телеметрии и не отправляет данные никуда, кроме запросов к YouTube и (если не выключено)
проверки обновлений на GitHub.

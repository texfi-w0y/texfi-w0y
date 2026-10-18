# Промо-ролик «w0y with desktop»

16:9, 1920×1080, 30 к/с, 46 с. Снимается из настоящего приложения кадр за кадром вне экрана
(`desktop/src/test/kotlin/com/texfi/w0y/desktop/PromoRender.kt`), экран автора не занят.
Библиотека в кадре выдуманная: английские названия, обложки нарисованы кодом — чужих обложек
и русских песен автора в ролике нет.

1. `python3 tools/promo/make_covers.py` — обложки в `cache/covers/`.
2. `python3 tools/promo/analyze_beats.py "<трек>.webm"` — темп трека (120 BPM, дроп на 15.0 с).
3. Рендер (в фоне, минуты; `-Dw0y.promo.scale=0.5` — быстрый черновик):
   `cd desktop && ./gradlew test --tests '*PromoRender*' -Dw0y.promo=/путь/silent.mp4 -Dw0y.promo.assets=$PWD/../tools/promo/cache/covers`
   Только стоп-кадры для проверки: `-Dw0y.promo=папка -Dw0y.promo.stills=7.5,15,27`.
4. `tools/promo/assemble.sh /путь/silent.mp4 "<трек>.webm" <папка-вывода>` — со звуком и без.

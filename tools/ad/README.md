# Рекламный ролик w0y (заготовка)

Всё для TikTok-ролика 15 с, 1080×1920, 60 fps. Собирается из настоящих экранов.

1. **Демо-сборка.** `./gradlew assembleBench` — в bench-сборке есть приёмник
   `DemoSeedReceiver` (`app/src/bench/…`, в release его нет). Он через DAO,
   без синхронизации с аккаунтом, подкладывает лайки, плейлисты, историю и
   закрепы. Описание данных — `app/src/bench/assets/demo_seed.json`: только
   англоязычные треки, без кириллицы (аудитория не должна выдавать язык автора).
2. **Данные на телефон.**
   - проба без записи: `adb shell am broadcast -n com.texfi.w0y.bench/com.texfi.w0y.demo.DemoSeedReceiver -a com.texfi.w0y.DEMO_SEED --ez dry true --es seed "$(base64 -w0 demo_seed.json)"`
   - запись: то же без `--ez dry true`. Смотреть результат: `adb logcat -s DemoSeed`.
   - треку можно задать точный `id` вместо `q` (так сделан «Crush x Burnt Rice»).
3. **Экраны.** `adb exec-out screencap -p > tools/ad/shots2/<имя>.png`:
   `home`, `player`, `playlist` (нужны ролику). Сайт: `shots/site_dark.png`
   (`chromium --headless=new --force-dark-mode --window-size=390,876 --force-device-scale-factor=3 --screenshot=…`).
4. **Рендер.** `python3 tools/ad/make_ad.py preview` (контактный лист) и `render`
   (`tools/ad/out/`). Монтаж привязан к сетке трека «Crush x Burnt Rice»:
   ~151 BPM, такт 1.587 с, первый удар на 0.14 с. Сами песни в файл не вшиваются:
   музыку добавляют в TikTok с начала трека (0:00).

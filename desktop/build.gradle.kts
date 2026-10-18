import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// w0y для Linux: отдельный Gradle-проект рядом с Android-приложением.
// Общий с телефоном код — разбор ответов YouTube (YtJson, SongItem,
// SyncMerge) — подключается из app/ исходниками, а не копией: правка
// формата ответа в одном месте чинит оба приложения.
plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

kotlin { jvmToolchain(21) }

// Версия одна на все платформы: ../version.properties.
val versionProps = Properties().apply { file("../version.properties").inputStream().use(::load) }
val appVersion: String = versionProps.getProperty("versionName")
// jpackage принимает только числа через точку: «0.0.4-beta-1» → «0.0.4».
val installerVersion: String = appVersion.substringBefore('-')
version = appVersion

val shared = "../app/src/main/java/com/texfi/w0y/data"

sourceSets.main {
    kotlin.srcDir("src/main/kotlin")
}

// Только то, что не знает про Android.
val sharedSources = tasks.register<Sync>("sharedSources") {
    from(shared) {
        include("YtJson.kt", "SongItem.kt", "SyncMerge.kt", "CleanMatch.kt", "Profanity.kt", "UpdateCore.kt")
    }
    into(layout.buildDirectory.dir("generated/shared/com/texfi/w0y/data"))
}
// Иконки обоих стилей — те же, что на телефоне: пиксельные сетки и контуры Material Rounded.
val sharedIcons = tasks.register<Sync>("sharedIcons") {
    from("../app/src/main/java/com/texfi/w0y/ui/components") { include("Sprites.kt") }
    from("../app/src/main/java/com/texfi/w0y/ui/icons") { include("SmoothIcons.kt") }
    into(layout.buildDirectory.dir("generated/shared/com/texfi/w0y/ui"))
}
// Разбор спектра для визуализатора — тот же код, что на телефоне.
val sharedPlayback = tasks.register<Sync>("sharedPlayback") {
    from("../app/src/main/java/com/texfi/w0y/playback") { include("SpectrumAnalyzer.kt") }
    into(layout.buildDirectory.dir("generated/shared/com/texfi/w0y/playback"))
}
sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/shared")) }
tasks.named("compileKotlin") { dependsOn(sharedSources, sharedIcons, sharedPlayback) }

// Шрифты Hairline — те же файлы, что у телефона (Unbounded, JetBrains Mono).
val androidFonts = tasks.register<Sync>("androidFonts") {
    from("../app/src/main/res/font") { include("unbounded_semibold.ttf", "unbounded_extrabold.ttf", "jetbrains_mono_medium.ttf") }
    into(layout.buildDirectory.dir("generated/fonts"))
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/fonts")) }

// Лицензии составных частей: текст из корня репозитория лежит в ресурсах приложения («Настройки → Приложение → Лицензии»).
val licenseFiles = tasks.register<Sync>("licenseFiles") {
    from("..") { include("THIRD_PARTY_LICENSES.md") }
    from("../licenses") { into("licenses") }
    into(layout.buildDirectory.dir("generated/licenses"))
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/licenses")) }

// Строки интерфейса — те же файлы, что у Android: один перевод на оба приложения.
// Читаются при запуске как обычный XML (см. i18n/Strings.kt), ничего не копируется руками.
val androidStrings = tasks.register<Sync>("androidStrings") {
    val res = "../app/src/main/res"
    from("$res/values") { include("strings.xml"); into("en") }
    from("$res/values-ru") { include("strings.xml"); into("ru") }
    from("$res/values-uk") { include("strings.xml"); into("uk") }
    from("$res/values-pl") { include("strings.xml"); into("pl") }
    into(layout.buildDirectory.dir("generated/strings/strings"))
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/strings")) }
tasks.named("processResources") { dependsOn(androidStrings, androidFonts, licenseFiles) }

// Версия для самого приложения (экран «О приложении», проверка обновлений, Copy debug info).
val versionFile = tasks.register("versionFile") {
    val out = layout.buildDirectory.file("generated/version/version.txt")
    inputs.property("v", appVersion)
    outputs.file(out)
    doLast { out.get().asFile.also { it.parentFile.mkdirs() }.writeText(appVersion) }
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/version")) }
tasks.named("processResources") { dependsOn(versionFile) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.components.resources)
    implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // D-Bus: управление с клавиатуры, из шторки и плагинов окружения (MPRIS).
    implementation("com.github.hypfvieh:dbus-java-core:5.1.0")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.0")
    // libmpv напрямую (Windows и Linux): без процесса и сокетов.
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("org.slf4j:slf4j-nop:2.0.17")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "com.texfi.w0y.desktop.MainKt"
        nativeDistributions {
            // AppImage здесь — папка-приложение (app-image jpackage); из неё CI собирает установщик, zip, tar.gz и .AppImage.
            targetFormats(TargetFormat.AppImage)
            // Нативные библиотеки (libmpv-2.dll и др.) кладёт CI в appResources/<ОС>-x64: они уезжают рядом с приложением.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
            // java.net.http нужен загрузкам и текстам; jlink сам его не находит.
            modules("java.net.http", "java.naming", "jdk.crypto.ec", "jdk.unsupported", "jdk.security.auth")
            packageName = "w0y"
            packageVersion = installerVersion
            description = "TexFi w0y — клиент YouTube Music"
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }
            windows { iconFile.set(project.file("src/main/resources/icon.ico")) }
        }
    }
}

// Проверка без окна: поиск, ссылка на звук и mpv. ./gradlew probe
tasks.register<JavaExec>("probe") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.texfi.w0y.desktop.ProbeKt"
}

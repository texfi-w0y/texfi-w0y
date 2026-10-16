import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // Плагин kotlin.android с AGP 9 не нужен: поддержка Kotlin встроена
    // в сам AGP, и попытка применить его валит конфигурацию.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.texfi.w0y"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.texfi.w0y"
        minSdk = 26
        targetSdk = 36
        // versionCode растёт, а не следует за именем версии: Android не ставит
        // сборку с меньшим кодом поверх установленной, и пришлось бы стирать данные.
        versionCode = 12
        versionName = "0.0.3-beta-2"
        // Короткий хеш коммита для «Copy debug info»: по нему видно, из какого кода собран APK.
        val commit =
            runCatching {
                providers
                    .exec { commandLine("git", "rev-parse", "--short", "HEAD") }
                    .standardOutput.asText.get().trim()
            }.getOrDefault("").ifBlank { "unknown" }
        buildConfigField("String", "GIT_COMMIT", "\"$commit\"")
    }

    // Подпись берётся из переменных окружения — так один и тот же файл
    // работает и локально, и в Actions, и при этом ни пароля, ни keystore
    // в репозитории нет. Нет переменных — нет release-конфига, сборка
    // падает обратно на debug-ключ (такой APK не раздаём).
    val keystorePath = System.getenv("ANDROID_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "texfi"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            // Отдельный applicationId, чтобы debug и релиз жили на телефоне
            // рядом: иначе для проверки сборки приходится сносить рабочую.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Для замеров на телефоне: всё как в release (R8, без debug-кода),
        // но подпись debug-ключом и отдельный id — встаёт рядом с рабочей
        // сборкой и не трогает её данные. Раздавать такой APK нельзя.
        create("bench") {
            initWith(getByName("release"))
            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // Схемы Room в репозитории: без них миграции пишутся вслепую.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    testOptions {
        // Смоук-тесты ходят в сеть через библиотеки, которые попутно пишут
        // в android.util.Log; на JVM его нет, и без заглушки тест падает
        // не по делу.
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material3)

    implementation(libs.activity.compose)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.innertubex)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.timber)
    // LAME на чистой Java: MP3 при сохранении на устройство (LGPL).
    implementation(libs.jump3r)
    implementation(libs.backdrop)
    testImplementation(libs.junit)
    // Robolectric нужен, чтобы ловить android-специфичные поломки локально:
    // на чистой JVM подключается JVM-вариант библиотеки, а на телефоне —
    // android-вариант, и ведут они себя по-разному.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.hilt.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}

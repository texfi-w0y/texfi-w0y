package com.texfi.w0y.desktop

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * QuickJS решает шифр ссылок YouTube (его зовёт innertubex), и он обязан загрузиться раньше libmpv.
 *
 * Если libmpv уже в процессе, внутренние вызовы QuickJS (`js_malloc` и родня) подменяются одноимёнными
 * функциями из зависимостей mpv (MuJS) и первая же JS-среда падает с SIGSEGV: приложение умирало целиком на
 * первом треке, которому нужен шифр. Проверено: libmpv → QuickJS падает всегда, QuickJS → libmpv работает.
 * Поэтому до запуска звука один раз создаём и закрываем пустую среду: библиотека загружена, позже она
 * просто переиспользуется.
 */
object QuickJsFirst {
    private val loaded: Boolean by lazy {
        runCatching {
            runBlocking {
                val js = QuickJs.create(Dispatchers.Default)
                try { js.evaluate<Int>("1") } finally { js.close() }
            }
        }.onFailure { System.err.println("QuickJS: ${it.message}") }.isSuccess
    }

    fun ensure() = loaded
}

package com.texfi.w0y.desktop

import com.dokar.quickjs.QuickJs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Регрессия: после libmpv первая JS-среда QuickJS роняла процесс (SIGSEGV), и приложение умирало на первом
 * треке с шифром. Загрузка libmpv теперь сначала поднимает QuickJS; здесь — что после неё среды создаются.
 */
class QuickJsOrderTest {
    @Test
    fun quickJsWorksAfterLibmpvIsLoaded() {
        LibMpv.available() // грузит libmpv (если она есть), заодно проверяя порядок
        assertTrue(QuickJsFirst.ensure())
        val sum = runBlocking {
            val js = QuickJs.create(Dispatchers.Default)
            try { js.evaluate<Int>("40 + 2") } finally { js.close() }
        }
        assertEquals(42, sum)
    }
}

package com.texfi.w0y

import com.texfi.w0y.data.AppIcon
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Каждая иконка из настроек существует в манифесте как алиас: иначе выбор иконки роняет приложение. */
class AppIconManifestTest {
    @Test
    fun everyIconHasAnAlias() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        AppIcon.entries.forEach {
            assertTrue("нет алиаса ${it.alias}", manifest.contains("android:name=\"${it.alias}\""))
        }
    }
}

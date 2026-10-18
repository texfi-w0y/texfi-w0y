package com.texfi.w0y.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private fun isolate() {
        System.setProperty("w0y.nokeyring", "1")
        System.setProperty("w0y.config", Files.createTempDirectory("w0y-config").toString())
    }

    @Test
    fun sessionRoundTripsAndIsNotStoredAsPlainText() {
        if (Platform.isWindows) return // DPAPI проверяется на самом Windows: smoke и ручная проверка
        isolate()
        val cookie = "SAPISID=abc123; __Secure-3PSID=zzz"
        val stored = SecureText.protect(cookie)
        assertTrue(stored.startsWith("enc1:"))
        assertFalse(stored.contains("abc123"))
        assertEquals(cookie, SecureText.unprotect(stored))
    }

    @Test
    fun legacyPlainSessionStillReadsAndTamperedOneDoesNot() {
        isolate()
        assertEquals("old=1", SecureText.unprotect("old=1"))
        if (Platform.isWindows) return
        val stored = SecureText.protect("x=1")
        val broken = stored.dropLast(4) + "AAAA"
        assertNull(SecureText.unprotect(broken))
    }

    @Test
    fun storeEncryptsCookieOnDiskButReadsItBack() {
        if (Platform.isWindows) return
        isolate()
        val dir = Files.createTempDirectory("w0y-data")
        System.setProperty("w0y.data", dir.toString())
        val store = Store()
        store.save(Library(cookie = "SID=secret-value", accountName = "me"))
        val onDisk = Files.readString(dir.resolve("library.json"))
        assertFalse(onDisk.contains("secret-value"), "cookie не должна лежать открытым текстом")
        assertEquals("SID=secret-value", Store().load().cookie)
    }

    @Test
    fun backupKeepsDataButNeverTheSession() {
        val lib = Library(cookie = "SID=secret", accountName = "me", volume = 42, searchHistory = listOf("a", "b"))
        val text = Backup.export(lib, now = 1L)
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("\"me\""))
        val back = Backup.parse(text).getOrThrow()
        assertEquals(42, back.volume)
        assertEquals(listOf("a", "b"), back.searchHistory)
        val restored = Backup.restore(Library(cookie = "SID=current", accountName = "current"), back)
        assertEquals("SID=current", restored.cookie)
        assertEquals(42, restored.volume)
    }

    @Test
    fun backupRejectsForeignAndNewerFiles() {
        assertTrue(Backup.parse("{}").isFailure)
        assertTrue(Backup.parse("""{"format":"other","version":1,"platform":"x","app":"1","createdAt":0,"library":{}}""").isFailure)
        assertTrue(Backup.parse("""{"format":"w0y-backup","version":99,"platform":"x","app":"1","createdAt":0,"library":{}}""").isFailure)
        assertNotNull(Backup.parse("""{"format":"w0y-backup","version":1,"platform":"x","app":"1","createdAt":0,"library":{}}""").getOrNull())
    }

    @Test
    fun diagRedactsSecretsAndFormatsLocalTime() {
        val text = Diag.redact("cookie=SID123 and https://x.test/a?token=SECRET")
        assertFalse(text.contains("SID123"))
        assertFalse(text.contains("SECRET"))
        val stamp = Diag.stamp(java.time.Instant.parse("2026-10-17T17:08:00Z").toEpochMilli(), java.util.TimeZone.getTimeZone("Europe/Warsaw"))
        assertEquals("17 Oct, 19:08", stamp)
    }

    @Test
    fun visualizerStylesFollowTheLook() {
        assertEquals(listOf(VisualizerStyle.LINES, VisualizerStyle.ORBIT, VisualizerStyle.TERRAIN), VisualizerStyle.of(UiStyle.HAIRLINE))
        // Старый выбор из Pixel/Smooth превращается в ближайший свой стиль Hairline.
        assertEquals(VisualizerStyle.LINES, VisualizerStyle.SEGMENTS.forUi(UiStyle.HAIRLINE))
        assertEquals(VisualizerStyle.ORBIT, VisualizerStyle.WAVE.forUi(UiStyle.HAIRLINE))
        assertEquals(VisualizerStyle.SEGMENTS, VisualizerStyle.LINES.forUi(UiStyle.PIXEL))
        assertTrue(VisualizerStyle.of(UiStyle.PIXEL).none { it.hair })
    }

    @Test
    fun newDefaultsMatchThePhone() {
        val d = DSettings()
        assertFalse(d.doubleClickPlay) // один клик включает трек, как на телефоне
        assertTrue(d.eqEnabled)
        assertEquals(CoverShape.ROUNDED, d.hlCoverShape)
        assertEquals(RecEndMode.CONTINUE, d.recEndMode)
        assertTrue(d.homeRecBlocks)
        // Старый файл настроек читается: новых полей в нём нет.
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(DSettings.serializer(), "{\"language\":\"RU\"}")
        assertEquals(LineWeight.THIN, old.hlWeight)
    }
}

package com.texfi.w0y

import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.UpdateManager
import com.texfi.w0y.data.UpdateParsing
import com.texfi.w0y.data.UpdateState
import com.texfi.w0y.data.UpdateVersion
import java.security.MessageDigest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Обновления с GitHub: порядок версий, разбор релиза, загрузка с проверкой суммы, «что нового». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateTest {
    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val apkSha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val base = "https://github.com/texfi-w0y/texfi-w0y/releases/download/v0.0.4/"

    private fun release(tag: String = "v0.0.4", digest: String? = "sha256:$apkSha", host: String = base, body: String = "## Новое\n- **Обои** `topo`\n- [ссылка](https://x.y)\n<!-- скрыто -->\n---") =
        """{"tag_name":"$tag","draft":false,"body":${org.json.JSONObject.quote(body)},"assets":[
            {"name":"TexFi-w0y-android.apk","size":${apk.size},"browser_download_url":"${host}TexFi-w0y-android.apk"${digest?.let { ",\"digest\":\"$it\"" } ?: ""}},
            {"name":"SHA256SUMS.txt","size":100,"browser_download_url":"${host}SHA256SUMS.txt"}]}"""

    @Test
    fun versionOrder() {
        assertTrue(UpdateVersion.isNewer("v0.0.4", "0.0.3-beta-2"))
        assertTrue(UpdateVersion.isNewer("v0.0.3-beta-3", "0.0.3-beta-2"))
        assertTrue("релиз старше беты той же версии", UpdateVersion.isNewer("v0.0.3", "0.0.3-beta-9"))
        assertFalse(UpdateVersion.isNewer("v0.0.3-beta-2", "0.0.3-beta-2-bench"))
        assertFalse(UpdateVersion.isNewer("v0.0.3-beta-1", "0.0.3-beta-2-debug"))
        assertTrue(UpdateVersion.isNewer("v0.1.0", "0.0.9"))
        assertFalse("нечитаемое — молчим", UpdateVersion.isNewer("nightly", "0.0.3-beta-2"))
        assertEquals("0.0.3 beta-2", UpdateVersion.label("v0.0.3-beta-2"))
        assertEquals("v0.0.3-beta-2", UpdateVersion.tagOf("0.0.3-beta-2-bench"))
    }

    @Test
    fun parsesReleaseAndCleansNotes() {
        val info = UpdateParsing.parseRelease(release())!!
        assertEquals("v0.0.4", info.tag)
        assertEquals(apkSha, info.sha256)
        assertTrue(info.sumsUrl!!.endsWith("SHA256SUMS.txt"))
        assertEquals("Новое\n• Обои topo\n• ссылка", info.notes)
    }

    @Test
    fun rejectsForeignHostAndMissingApk() {
        assertNull("чужой хост не доверяем", UpdateParsing.parseRelease(release(host = "https://evil.example/")))
        assertNull(UpdateParsing.parseRelease("""{"tag_name":"v1.0.0","assets":[]}"""))
        assertNull(UpdateParsing.parseRelease("не json"))
        assertNull(UpdateParsing.parseRelease("""{"tag_name":"v1.0.0","draft":true,"assets":[]}"""))
    }

    @Test
    fun sumsLookup() {
        val sums = "$apkSha  TexFi-w0y-android.apk\n${"a".repeat(64)}  w0y-linux-x86_64.tar.gz\n"
        assertEquals(apkSha, UpdateParsing.sumFor(sums, "TexFi-w0y-android.apk"))
        assertNull(UpdateParsing.sumFor(sums, "other.apk"))
    }

    private fun manager(version: String = "0.0.3-beta-2", code: Int = 12, handler: (String) -> Pair<Int, ByteArray>): UpdateManager {
        val client =
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val (status, body) = handler(url)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("m")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }).build()
        return UpdateManager(ApplicationProvider.getApplicationContext(), client).also {
            it.versionName = version
            it.versionCode = code
        }
    }

    private fun waitFor(m: UpdateManager, done: (UpdateState) -> Boolean): UpdateState {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            val s = m.state.value
            if (done(s)) return s
            Thread.sleep(20)
        }
        error("не дождались: ${m.state.value}")
    }

    @Test
    fun checkFindsNewerAndDownloadVerifiesSum() {
        val m = manager { url ->
            when {
                url.endsWith("/releases/latest") -> 200 to release().toByteArray()
                url.endsWith("TexFi-w0y-android.apk") -> 200 to apk
                else -> 404 to ByteArray(0)
            }
        }
        m.check()
        val found = waitFor(m) { it is UpdateState.Available } as UpdateState.Available
        m.download(found.info, installAfter = false)
        val ready = waitFor(m) { it is UpdateState.Ready || it is UpdateState.Failed }
        assertTrue("$ready", ready is UpdateState.Ready)
        assertEquals(apk.size.toLong(), (ready as UpdateState.Ready).file.length())
    }

    @Test
    fun wrongSumIsRejectedAndFileDeleted() {
        val m = manager { url ->
            when {
                url.endsWith("/releases/latest") -> 200 to release(digest = "sha256:${"0".repeat(64)}").toByteArray()
                url.endsWith("TexFi-w0y-android.apk") -> 200 to apk
                else -> 404 to ByteArray(0)
            }
        }
        m.check()
        val found = waitFor(m) { it is UpdateState.Available } as UpdateState.Available
        m.download(found.info, installAfter = false)
        val failed = waitFor(m) { it is UpdateState.Ready || it is UpdateState.Failed }
        assertTrue("$failed", failed is UpdateState.Failed && failed.reason == UpdateState.Reason.BAD_FILE)
        assertFalse(java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "update/w0y-update.apk").exists())
    }

    @Test
    fun sameVersionIsUpToDateAndErrorsAreTyped() {
        val same = manager("0.0.4") { 200 to release().toByteArray() }
        same.check()
        assertEquals(UpdateState.UpToDate, waitFor(same) { it !is UpdateState.Checking && it !is UpdateState.Idle })
        val limited = manager { 403 to ByteArray(0) }
        limited.check()
        assertEquals(UpdateState.Failed(UpdateState.Reason.RATE_LIMIT), waitFor(limited) { it is UpdateState.Failed })
    }

    @Test
    fun deferRemembersVersion() {
        val m = manager { 200 to release().toByteArray() }
        m.check()
        val found = waitFor(m) { it is UpdateState.Available } as UpdateState.Available
        assertNull(m.deferredTag.value.takeIf { it == found.info.tag })
        m.defer(found.info)
        assertEquals("v0.0.4", m.deferredTag.value)
    }

    @Test
    fun whatsNewAppearsOnlyAfterAnUpdate() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("w0y_update", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val notes = release(tag = "v0.0.3-beta-2")
        // Свежая установка: код запоминается, окна нет.
        val fresh = manager(code = 12) { 200 to notes.toByteArray() }
        fresh.onLaunch(autoCheck = false)
        Thread.sleep(300)
        assertNull(fresh.whatsNew.value)
        // Обновились: код вырос — окно с описанием своей версии.
        val updated = manager(code = 13) { url -> if (url.endsWith("/releases/tags/v0.0.3-beta-2")) 200 to notes.toByteArray() else 404 to ByteArray(0) }
        updated.onLaunch(autoCheck = false)
        val end = System.currentTimeMillis() + 5_000
        while (updated.whatsNew.value == null && System.currentTimeMillis() < end) Thread.sleep(20)
        assertNotNull(updated.whatsNew.value)
        assertEquals("0.0.3 beta-2", updated.whatsNew.value!!.label)
        updated.dismissWhatsNew()
        assertNull(updated.whatsNew.value)
        // Второй запуск той же версии — тишина.
        val again = manager(code = 13) { 200 to notes.toByteArray() }
        again.onLaunch(autoCheck = false)
        Thread.sleep(300)
        assertNull(again.whatsNew.value)
    }
}

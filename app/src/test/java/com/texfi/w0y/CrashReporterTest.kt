package com.texfi.w0y

import com.texfi.w0y.data.CrashReporter
import com.texfi.w0y.data.DebugInfoFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test
    fun crashRoundTripsIntoEventWithScreenAndTrimmedStack() {
        val text = describeForTest(IllegalStateException("boom https://x.test/a?token=SECRET123"), "LIBRARY")
        val event = CrashReporter.parseForTest(text)
        assertNotNull(event)
        assertEquals("CRASH LIBRARY", event!!.kind)
        assertEquals("IllegalStateException", event.result)
        val detail = event.detail!!
        assertTrue(detail.lines().size <= 11)
        assertFalse("ссылка с параметрами вырезается", detail.contains("SECRET123"))
        val line = DebugInfoFormatter.line(event)
        assertTrue(line.contains("CRASH LIBRARY"))
    }

    @Test
    fun brokenFileIsIgnored() {
        assertEquals(null, CrashReporter.parseForTest("garbage"))
    }

    private fun describeForTest(e: Throwable, screen: String) = CrashReporter.describeForTest(e, screen)
}

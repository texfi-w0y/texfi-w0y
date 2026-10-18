package com.texfi.w0y.data

import android.content.Context
import android.os.Build
import com.metrolist.innertubex.InnerTube
import com.texfi.w0y.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Всё, что попадает в «Copy debug info», кроме журнала: поля фиксированы, свободного текста нет. */
data class DebugContext(
    val appVersion: String,
    val build: Int,
    val commit: String,
    val mode: String,
    val device: String,
    val androidRelease: String,
    val api: Int,
    val manufacturer: String,
    val abi: String,
    val systemLocale: String,
    val requestHl: String,
    val requestGl: String,
    val network: NetworkSnapshot,
    val signedIn: Boolean,
    /** Итог последней проверки соединения, уже отформатированный; `null`, если не запускалась. */
    val lastTest: String?,
)

object DebugInfoFormatter {
    private const val EVENTS = 20

    fun format(context: DebugContext, events: List<LogEvent>): String =
        Redact.scrub(
            buildString {
                appendLine("w0y debug info")
                appendLine("app: ${context.appVersion} (build ${context.build}), commit ${context.commit}, ${context.mode}")
                appendLine("device: ${context.manufacturer} ${context.device}, Android ${context.androidRelease} (API ${context.api}), ${context.abi}")
                appendLine("system locale: ${context.systemLocale}")
                appendLine("requests use: hl=${context.requestHl} gl=${context.requestGl}")
                val n = context.network
                appendLine("network: ${if (n.online) n.transport else "none"}, vpn: ${yesNo(n.vpn)}, private DNS: ${n.privateDns}")
                appendLine("data saver: ${n.dataSaver}, battery saver: ${yesNo(n.powerSave)}")
                appendLine("signed in: ${yesNo(context.signedIn)}")
                appendLine()
                appendLine("last ${EVENTS} events: network and crashes (newest last):")
                val recent = events.takeLast(EVENTS)
                if (recent.isEmpty()) appendLine("  none") else recent.forEach { appendLine("  " + line(it)) }
                appendLine()
                appendLine("connection test:")
                appendLine(context.lastTest?.prependIndent("  ") ?: "  not run")
            },
        )

    /** Время записи — в местной зоне и полным форматом («17 Oct, 19:08»): прежнее «01-04 …Z» читалось как неверное. */
    fun line(event: LogEvent, zone: TimeZone = TimeZone.getDefault()): String {
        val stamp = SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH).apply { timeZone = zone }.format(Date(event.timeMs))
        return buildString {
            append(stamp).append("  ").append(event.kind).append("  ").append(event.result)
            event.httpCode?.let { append("  http=").append(it) }
            event.durationMs?.let { append("  ").append(it).append("ms") }
            event.bytes?.let { append("  ").append(it).append("B") }
            event.host?.let { append("  ").append(it) }
            event.detail?.let { append("\n      ").append(it.replace("\n", "\n      ")) }
        }
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}

/**
 * Последняя линия обороны: если в свободный текст всё же попало что-то
 * похожее на cookie или токен, оно вырезается. Основная защита — что в
 * отчёт вообще не кладутся запросы, cookie и данные аккаунта.
 */
object Redact {
    private val patterns =
        listOf(
            Regex("""(?i)\b(SAPISID|APISID|SID|HSID|SSID|__Secure-[A-Z0-9]+|LOGIN_INFO|VISITOR_INFO1_LIVE|YSC)=[^;\s]+"""),
            Regex("""(?i)\b(authorization|cookie|set-cookie)\s*[:=]\s*\S+"""),
            Regex("""(?i)\bbearer\s+[A-Za-z0-9._\-]{8,}"""),
            Regex("""\b[A-Za-z0-9_\-]{40,}\b"""),
            Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""),
        )

    fun scrub(text: String): String = patterns.fold(text) { acc, re -> re.replace(acc, "[removed]") }
}

@Singleton
class DebugInfoCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val events: EventLog,
    private val network: NetworkInfo,
    private val innerTube: InnerTube,
    private val connectionTest: ConnectionTestStore,
) {
    fun collect(): String {
        val locale = YouTubeLocaleResolver.forApp(context)
        val effective = innerTube.locale
        val info =
            DebugContext(
                appVersion = BuildConfig.VERSION_NAME,
                build = BuildConfig.VERSION_CODE,
                commit = BuildConfig.GIT_COMMIT,
                mode = if (BuildConfig.DEBUG) "debug" else "release",
                device = Build.MODEL.orEmpty(),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                api = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                systemLocale = Locale.getDefault().toLanguageTag(),
                requestHl = effective.hl.ifBlank { locale.hl },
                requestGl = effective.gl.ifBlank { locale.gl },
                network = network.snapshot(),
                signedIn = !innerTube.cookie.isNullOrBlank(),
                lastTest = connectionTest.last,
            )
        return DebugInfoFormatter.format(info, events.snapshot())
    }
}

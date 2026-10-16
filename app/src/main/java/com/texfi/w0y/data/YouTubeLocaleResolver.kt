package com.texfi.w0y.data

import android.content.Context
import android.os.LocaleList
import android.telephony.TelephonyManager
import com.metrolist.innertubex.models.YouTubeLocale
import java.util.Locale

/**
 * Собирает локаль запроса к YouTube: язык отдельно, страна отдельно.
 *
 * Проверено живыми запросами (см. LocaleProbeTest): YouTube принимает в `hl`
 * код языка — `en`, `ru`, `pl`, а также известные региональные варианты вроде
 * `en-GB`, но на произвольную пару «язык + страна» отвечает 400. У телефона
 * с английским языком и польским регионом системный тег как раз `en-PL`,
 * и поэтому падал вообще любой запрос.
 *
 * `gl` при этом может быть любой страной — региональная выдача сохраняется.
 */
object YouTubeLocaleResolver {
    /**
     * Локаль запроса с учётом языка, выбранного в самом приложении.
     *
     * Без этого интерфейс на русском получал бы ленты YouTube на языке
     * телефона: человек переключил язык в настройках, а «Listen again» так
     * и осталось. Страна при этом берётся из системы и сети как раньше —
     * выбор языка не должен менять региональную выдачу.
     */
    fun forApp(context: Context): YouTubeLocale =
        resolve(
            system = Locale.getDefault(),
            countryCandidates = systemCountries(context),
            chosenLanguage = LocalePrefs.current(context).tag,
        )

    fun forDevice(context: Context): YouTubeLocale = resolve(Locale.getDefault(), systemCountries(context))

    /** Чистая функция: её проверяет тест, без Android под рукой. */
    fun resolve(
        system: Locale,
        countryCandidates: List<String>,
        chosenLanguage: String? = null,
    ): YouTubeLocale {
        val language =
            chosenLanguage?.lowercase(Locale.ROOT)?.takeIf(::validLanguage)
                ?: system.language.lowercase(Locale.ROOT).takeIf(::validLanguage)
                ?: LANG_FALLBACK
        val country =
            (listOf(system.country) + countryCandidates)
                .firstOrNull { it.length == 2 && it.all(Char::isLetter) }
                ?.uppercase(Locale.ROOT)
                ?: COUNTRY_FALLBACK
        return YouTubeLocale(gl = country, hl = language)
    }

    /** Код языка: две-три латинские буквы; `und`, пустая строка и мусор не годятся. */
    private fun validLanguage(code: String): Boolean = code != "und" && code.length in 2..3 && code.all { it in 'a'..'z' }

    /** Английский — язык, который YouTube принимает всегда. */
    private const val LANG_FALLBACK = "en"

    /** Страна по умолчанию: без неё региональная выдача просто шире. */
    private const val COUNTRY_FALLBACK = "US"

    private fun systemCountries(context: Context): List<String> {
        val fromList =
            LocaleList.getDefault().let { list ->
                (0 until list.size()).map { list[it].country }
            }
        val fromNetwork =
            runCatching {
                val telephony = context.getSystemService(TelephonyManager::class.java)
                // Страна сети точнее системных настроек: именно её YouTube
                // ждёт в gl для региональной выдачи.
                listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso)
            }.getOrDefault(emptyList())
        return fromList + fromNetwork
    }
}

package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.appVersionLabel
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground
import com.texfi.w0y.ui.theme.PixelBigNumber
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

/**
 * «О приложении» — тот же экран, что в m0ney, и намеренно тот же.
 *
 * Смысл не в версии и не в лицензии: это единственное место, где человек,
 * поставивший w0y из каталога, узнаёт, что у автора есть ещё три
 * приложения. Сайт он не увидит никогда — цепочку замыкает вот этот
 * список, и поэтому он здесь не рекламный блок, а часть продукта.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    experiments: Boolean,
    onUnlockExperiments: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        // Открывать нечем — не падаем: на голой системе без браузера
        // это нормальная ситуация, а не ошибка приложения.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .statusBarsPadding(),
    ) {
        // Та же шапка, что у всех экранов настроек: назад слева, крупный заголовок, линия.
        com.texfi.w0y.ui.components.ScreenTitle(title = stringResource(R.string.about_title), onBack = onBack)

        LazyColumn(
            Modifier.padding(horizontal = 18.dp).then(if (com.texfi.w0y.ui.components.LocalBottomInset.current > 0.dp) Modifier else Modifier.navigationBarsPadding()),
            contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { NumberedSection("01", stringResource(R.string.about_section_app)) }
            item { AppFacts(experiments, onUnlockExperiments) }

            item { NumberedSection("02", stringResource(R.string.about_section_open)) }
            item {
                LinkRow(
                    sprite = Sprites.github,
                    title = stringResource(R.string.about_source_title),
                    subtitle = REPO_SHORT,
                    onClick = { open(REPO_URL) },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.license,
                    title = stringResource(R.string.about_license_title),
                    subtitle = stringResource(R.string.about_license_text),
                    onClick = { open(LICENSE_URL) },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.globe,
                    title = stringResource(R.string.about_ecosystem_title),
                    subtitle = HUB_SHORT,
                    onClick = { open(HUB_URL) },
                )
            }

            item { NumberedSection("03", stringResource(R.string.about_section_family)) }
            item {
                LinkRow(
                    sprite = Sprites.markFokus,
                    title = "TexFi f0kus",
                    subtitle = stringResource(R.string.about_fokus_text),
                    onClick = { open("$HUB_URL/download/fokus") },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.markMoney,
                    title = "TexFi m0ney",
                    subtitle = stringResource(R.string.about_money_text),
                    onClick = { open("$HUB_URL/download/money") },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.markFiles,
                    title = "TexFi files",
                    subtitle = stringResource(R.string.about_files_text),
                    onClick = { open("$HUB_URL/download/files") },
                )
            }

            item { NumberedSection("04", stringResource(R.string.about_section_support)) }
            item { DonateCard(onClick = { open(DONATE_URL) }) }

        }
    }
}

/** Заголовок раздела: номер акцентом, дальше подпись и линия до края. */
@Composable
private fun NumberedSection(number: String, title: String) {
    val colors = LocalW0yColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number, style = PixelSectionLabel, color = colors.accent)
        Spacer(Modifier.width(10.dp))
        Text(title, style = PixelTitle, color = colors.text)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .background(colors.border),
        )
    }
}

/** Карточка с версией и тем, что про приложение можно сказать честно. */
@Composable
private fun AppFacts(
    experiments: Boolean,
    onUnlockExperiments: () -> Unit,
) {
    // Эксперименты открываются семью нажатиями на версию — как режим
    // разработчика в самом Android. Прятать иначе было бы нечестно:
    // это не секрет, просто не то, что стоит показывать каждому.
    var versionTaps by remember { mutableIntStateOf(0) }
    val colors = LocalW0yColors.current
    val haptic = com.texfi.w0y.ui.components.rememberHaptics()
    val bounce = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var egg by remember { mutableStateOf<Int?>(null) }
    PixelCard {
        Text(
            text = "texfi w0y",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier =
                Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            haptic(com.texfi.w0y.ui.components.Buzz.EGG)
                            egg = R.string.egg_feather
                        },
                    )
                },
        )
        // Крупный пиксельный шрифт широкий, и длинная версия в него не
        // влезает: у debug-сборки к имени добавляется суффикс, и «v0.0.1
        // beta-1-debug» переносилось посреди слова. Длинное имя набирается
        // на размер меньше — лучше, чем перенос в середине версии.
        val version = "v$appVersionLabel"
        Text(
            text = version,
            style = if (version.length > VERSION_FITS) PixelTitle else PixelBigNumber,
            color = colors.text,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    }
                    .clickable(indication = null, interactionSource = null) {
                        versionTaps++
                        scope.launch {
                            bounce.snapTo(0.9f)
                            bounce.animateTo(
                                1f,
                                androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 700f),
                            )
                        }
                        if (versionTaps == UNLOCK_TAPS && !experiments) {
                            haptic(com.texfi.w0y.ui.components.Buzz.EGG)
                            onUnlockExperiments()
                        } else if (versionTaps == MANY_TAPS) {
                            haptic(com.texfi.w0y.ui.components.Buzz.ERROR)
                            egg = R.string.egg_many_taps
                        }
                    },
        )
        egg?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val left = UNLOCK_TAPS - versionTaps
        when {
            experiments && versionTaps > 0 ->
                Text(
                    text = stringResource(R.string.about_experiments_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent,
                )
            !experiments && versionTaps >= UNLOCK_HINT_FROM ->
                Text(
                    text = stringResource(R.string.about_experiments_left, left),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
        }
        Text(
            text = stringResource(R.string.about_build, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.text,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.about_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
        // Цифры не переводятся, поэтому в ресурсах лежат только подписи.
        Fact("0", stringResource(R.string.about_fact_ads))
        Fact("0", stringResource(R.string.about_fact_telemetry))
        Fact("AGPL-3.0", stringResource(R.string.about_fact_license))
    }
}

/** Строка факта: квадратная точка, значение пиксельным, подпись обычным. */
@Composable
private fun Fact(value: String, label: String) {
    val colors = LocalW0yColors.current
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(colors.accent))
        Spacer(Modifier.width(8.dp))
        Text(value, style = PixelSectionLabel, color = colors.text)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = PixelSectionLabel,
            color = colors.textMuted,
        )
    }
}

/** Карточка-ссылка: знак слева, стрелка «наружу» справа. */
@Composable
private fun LinkRow(
    sprite: List<String>,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.985f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.width(10.dp))
            PixelSprite(
                rows = Sprites.chevronRight,
                color = colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Единственная кнопка в приложении, которая просит денег. Без нажима. */
@Composable
private fun DonateCard(onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.985f)
            .styledClip(8)
            .background(colors.surface)
            // Единственная карточка с акцентной рамкой на экране — как
            // в m0ney: просьбу о поддержке видно, но она не кричит.
            .border(2.dp, colors.accent, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.about_section_support),
                style = PixelSectionLabel,
                color = colors.accent,
            )
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelSprite(
                    rows = Sprites.heart,
                    color = colors.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.about_donate_title),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.text,
                )
            }
            Text(
                text = stringResource(R.string.about_donate_text),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Start,
            )
        }
    }
}

private const val REPO_URL = "https://github.com/texfi-w0y/texfi-w0y"
private const val REPO_SHORT = "github.com/texfi-w0y/texfi-w0y"
private const val LICENSE_URL = "https://github.com/texfi-w0y/texfi-w0y/blob/main/LICENSE"
private const val HUB_URL = "https://texfi-hub.vercel.app"
private const val HUB_SHORT = "texfi-hub.vercel.app"

/** Тот же адрес, что у остальных приложений TexFi: копилка одна на всех. */
private const val DONATE_URL = "https://github.com/sponsors/mistqkw"

/** Сколько знаков версии влезает в строку крупным пиксельным шрифтом. */
private const val VERSION_FITS = 12

private const val MANY_TAPS = 25
private const val UNLOCK_TAPS = 7
private const val UNLOCK_HINT_FROM = 3

package com.texfi.w0y.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.R
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.StatsRepository
import com.texfi.w0y.recs.RecEngine
import com.texfi.w0y.recs.TasteProfile
import com.texfi.w0y.ui.components.ConfirmDialog
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SegmentedBar
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.screenBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TasteState(
    val top: List<Pair<String, Int>> = emptyList(),
    val excluded: List<String> = emptyList(),
    val learning: Boolean = true,
    val loaded: Boolean = false,
)

@HiltViewModel
class TasteViewModel @Inject constructor(
    private val engine: RecEngine,
    private val settings: SettingsRepository,
    private val stats: StatsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TasteState())
    val state: StateFlow<TasteState> = _state.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val s = settings.settings.first()
            val taste = engine.tasteProfile()
            // Исключённые не входят в профиль, поэтому показываем их из настроек.
            val excluded = TasteProfile.parseExcluded(s.tasteExcluded).sorted()
            _state.value = TasteState(taste.top(), excluded, s.tasteLearning, loaded = true)
        }
    }

    fun exclude(artist: String) {
        viewModelScope.launch {
            val now = TasteProfile.parseExcluded(settings.settings.first().tasteExcluded)
            settings.setTasteExcluded(TasteProfile.formatExcluded(now + TasteProfile.key(artist)))
            reload()
        }
    }

    fun bringBack(artist: String) {
        viewModelScope.launch {
            val now = TasteProfile.parseExcluded(settings.settings.first().tasteExcluded)
            settings.setTasteExcluded(TasteProfile.formatExcluded(now - TasteProfile.key(artist)))
            reload()
        }
    }

    fun forgetShown() {
        viewModelScope.launch { engine.forgetSeen() }
    }

    /** Счётчики прослушивания обнуляются (как «сбросить итоги») и забывается показанное. */
    fun resetTaste() {
        viewModelScope.launch {
            stats.reset()
            engine.forgetSeen()
            reload()
        }
    }

    fun setLearning(value: Boolean) {
        viewModelScope.launch {
            settings.setTasteLearning(value)
            reload()
        }
    }
}

/**
 * «Мой вкус»: на чём построены подборки. Всё считается на телефоне из
 * прослушиваний и лайков; здесь можно исключить исполнителя, забыть показанное,
 * сбросить вкус или выключить учёт совсем.
 */
@Composable
fun TasteScreen(onBack: () -> Unit, viewModel: TasteViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.taste_reset_title),
            text = stringResource(R.string.taste_reset_text),
            confirm = stringResource(R.string.stats_reset_confirm),
            dismiss = stringResource(R.string.library_cancel),
            onConfirm = {
                confirmReset = false
                viewModel.resetTaste()
            },
            onDismiss = { confirmReset = false },
        )
    }
    Column(Modifier.fillMaxSize().screenBackground()) {
        ScreenTitle(title = stringResource(R.string.taste_title), onBack = onBack)
        LazyColumn(
            Modifier.padding(horizontal = 18.dp),
            contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(),
        ) {
            item {
                Text(stringResource(R.string.taste_intro), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(12.dp))
                if (!state.learning) {
                    Text(stringResource(R.string.taste_off_note), style = HairLabelStyle, color = colors.secondary)
                    Spacer(Modifier.height(12.dp))
                }
                PixelButton(
                    text = stringResource(if (state.learning) R.string.taste_turn_off else R.string.taste_turn_on),
                    onClick = { viewModel.setLearning(!state.learning) },
                    fill = colors.surfaceHigh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.taste_top).asLabel(), style = HairLabelStyle, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                if (state.loaded && state.top.isEmpty()) {
                    Text(stringResource(R.string.taste_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }
            items(state.top, key = { "top-" + it.first }) { (artist, weight) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(artist, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1)
                        SegmentedBar(
                            progress = { weight / 100f },
                            lit = colors.accent,
                            dim = colors.surfaceHigh,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            height = 4.dp,
                            cell = 8.dp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PixelButton(text = stringResource(R.string.taste_exclude), onClick = { viewModel.exclude(artist) }, fill = colors.surfaceHigh)
                }
            }
            if (state.excluded.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.taste_excluded).asLabel(), style = HairLabelStyle, color = colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                }
                items(state.excluded, key = { "ex-" + it }) { artist ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(artist, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
                        PixelButton(text = stringResource(R.string.taste_return), onClick = { viewModel.bringBack(artist) }, fill = colors.surfaceHigh)
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(
                        text = stringResource(R.string.taste_forget_shown),
                        onClick = viewModel::forgetShown,
                        fill = colors.surfaceHigh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                    Text(stringResource(R.string.taste_forget_shown_hint), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    PixelButton(
                        text = stringResource(R.string.taste_reset),
                        onClick = { confirmReset = true },
                        fill = colors.surfaceHigh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

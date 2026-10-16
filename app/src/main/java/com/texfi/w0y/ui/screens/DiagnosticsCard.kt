package com.texfi.w0y.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.R
import com.texfi.w0y.data.ConnectionTest
import com.texfi.w0y.data.DebugInfoCollector
import com.texfi.w0y.data.EventLog
import com.texfi.w0y.data.StepState
import com.texfi.w0y.data.TestStep
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Диагностика соединения и отчёт для багов: всё локально, наружу ничего не уходит. */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val collector: DebugInfoCollector,
    private val test: ConnectionTest,
    private val log: EventLog,
) : ViewModel() {
    private val _steps = MutableStateFlow<List<TestStep>>(emptyList())
    val steps: StateFlow<List<TestStep>> = _steps.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null

    /** Запускается только нажатием; повторное нажатие во время проверки игнорируется. */
    fun runTest() {
        if (_running.value) return
        _running.value = true
        job =
            viewModelScope.launch {
                try {
                    test.run().collect { _steps.value = it }
                } finally {
                    _running.value = false
                }
            }
    }

    suspend fun debugInfo(): String = withContext(Dispatchers.IO) { collector.collect() }

    fun clearLog() = log.clear()
}

/** Кладёт текст в буфер обмена и коротко сообщает об этом. */
fun copyToClipboard(context: Context, label: String, text: String, toast: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DiagnosticsCard(modifier: Modifier = Modifier, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val copiedText = stringResource(R.string.diag_copied)
    val clearedText = stringResource(R.string.diag_cleared)
    val testLabel = stringResource(R.string.diag_test)
    val copyLabel = stringResource(R.string.diag_copy)
    val clearLabel = stringResource(R.string.diag_clear)

    PixelCard(modifier = modifier.fillMaxWidth(), label = stringResource(R.string.diag_title)) {
        Text(
            text = stringResource(R.string.diag_text),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(
                text = testLabel,
                onClick = viewModel::runTest,
                enabled = !running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = testLabel },
            )
            PixelButton(
                text = copyLabel,
                onClick = { scope.launch { copyToClipboard(context, "w0y debug info", viewModel.debugInfo(), copiedText) } },
                fill = colors.surfaceHigh,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = copyLabel },
            )
            PixelButton(
                text = clearLabel,
                onClick = {
                    viewModel.clearLog()
                    Toast.makeText(context, clearedText, Toast.LENGTH_SHORT).show()
                },
                fill = colors.surfaceHigh,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = clearLabel },
            )
        }
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step -> StepRow(index + 1, step) }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: TestStep) {
    val colors = LocalW0yColors.current
    val status =
        when (step.state) {
            StepState.Ok -> "OK"
            StepState.Failed -> "FAILED"
            StepState.Running -> "…"
            StepState.Skipped -> "SKIPPED"
            StepState.Pending -> "–"
        }
    val statusColor =
        when (step.state) {
            StepState.Ok -> colors.text
            StepState.Failed -> colors.accentText
            else -> colors.textMuted
        }
    Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
        Text("$number", style = PixelSectionLabel, color = colors.textMuted, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            step.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
        }
        Spacer(Modifier.width(10.dp))
        Text(status, style = PixelSectionLabel, color = statusColor)
    }
}

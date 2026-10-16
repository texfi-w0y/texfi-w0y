package com.texfi.w0y

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Пустая активити под Hilt для тестов оболочки: без связи с сервисом воспроизведения. */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()

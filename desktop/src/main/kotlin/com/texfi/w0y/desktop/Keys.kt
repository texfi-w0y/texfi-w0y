package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import com.texfi.w0y.desktop.i18n.S
import java.awt.event.KeyEvent as Awt

/**
 * Действия, которые можно повесить на клавиши. У каждого есть сочетания по
 * умолчанию; свои хранятся в настройках (id → сочетания) и заменяют стандартные.
 *
 * Сочетание — строка «ctrl+shift+k»: модификаторы по алфавиту (alt, ctrl, meta, shift),
 * потом имя клавиши в нижнем регистре. Кнопки мультимедиа на клавиатуре обрабатывает
 * сама система через MPRIS, поэтому в приложении они не заняты: иначе пауза сработала бы дважды.
 */
enum class Action(
    val id: String,
    val labelKey: String,
    val defaults: List<String>,
    /** Срабатывает, даже когда фокус в поле ввода (сочетания с Ctrl/Alt и функциональные клавиши). */
    val inText: Boolean = false,
    /** Можно держать зажатой: громкость, перемотка. */
    val repeat: Boolean = false,
    val group: Int,
) {
    PLAY_PAUSE("play_pause", "d_act_play_pause", listOf("space", "k"), group = 0),
    NEXT("next", "d_act_next", listOf("n", "ctrl+right"), inText = false, group = 0),
    PREVIOUS("previous", "d_act_previous", listOf("p", "ctrl+left"), group = 0),
    SEEK_BACK("seek_back", "d_act_seek_back", listOf("left", "j"), repeat = true, group = 0),
    SEEK_FORWARD("seek_forward", "d_act_seek_forward", listOf("right", "l"), repeat = true, group = 0),
    VOLUME_UP("volume_up", "d_act_volume_up", listOf("up", "="), repeat = true, group = 0),
    VOLUME_DOWN("volume_down", "d_act_volume_down", listOf("down", "-"), repeat = true, group = 0),
    MUTE("mute", "d_act_mute", listOf("m"), group = 0),
    SHUFFLE("shuffle", "d_act_shuffle", listOf("s"), group = 0),
    REPEAT("repeat", "d_act_repeat", listOf("r"), group = 0),
    SPEED_DOWN("speed_down", "d_act_speed_down", listOf("["), repeat = true, group = 0),
    SPEED_UP("speed_up", "d_act_speed_up", listOf("]"), repeat = true, group = 0),
    LIKE("like", "d_act_like", listOf("f"), group = 1),
    DOWNLOAD("download", "d_act_download", listOf("d"), group = 1),
    SLEEP("sleep", "d_act_sleep", listOf("t"), group = 1),
    STAND("stand", "d_act_stand", listOf("b"), group = 1),
    VISUALIZER("visualizer", "d_act_visualizer", listOf("v"), group = 1),
    LYRICS("lyrics", "d_act_lyrics", listOf("y"), group = 1),
    SEARCH("search", "d_act_search", listOf("ctrl+k", "slash"), inText = false, group = 2),
    TAB_HOME("tab_home", "d_act_tab_home", listOf("1"), group = 2),
    TAB_SEARCH("tab_search", "d_act_tab_search", listOf("2"), group = 2),
    TAB_LIBRARY("tab_library", "d_act_tab_library", listOf("3"), group = 2),
    TAB_PLAYER("tab_player", "d_act_tab_player", listOf("4", "q"), group = 2),
    TAB_SETTINGS("tab_settings", "d_act_tab_settings", listOf("5", "ctrl+comma"), inText = false, group = 2),
    BACK("back", "d_act_back", listOf("escape", "alt+left"), inText = true, group = 2),
    HELP("help", "d_act_help", listOf("shift+slash", "f1"), group = 3),
    FULLSCREEN("fullscreen", "d_act_fullscreen", listOf("f11"), inText = true, group = 3),
    MINI("mini", "d_act_mini", listOf("ctrl+m"), inText = true, group = 3),
    QUIT("quit", "d_act_quit", listOf("ctrl+q"), inText = true, group = 3),
    ;

    val label: String get() = S(labelKey)

    companion object {
        fun byId(id: String): Action? = entries.firstOrNull { it.id == id }
        val groupKeys = listOf("d_keys_playback", "d_keys_track", "d_keys_navigation", "d_keys_window")
    }
}

/** Чтение сочетаний с учётом переназначений. */
object Keymap {
    fun bindings(action: Action, custom: Map<String, List<String>>): List<String> = custom[action.id] ?: action.defaults

    /** Какое действие занимает сочетание [combo], кроме [except]; null — свободно. */
    fun owner(combo: String, custom: Map<String, List<String>>, except: Action? = null): Action? =
        Action.entries.firstOrNull { it != except && combo in bindings(it, custom) }

    fun find(combo: String, custom: Map<String, List<String>>): Action? = Action.entries.firstOrNull { combo in bindings(it, custom) }

    /** Красивая запись для показа: «Ctrl+K», «Space», «←». */
    fun pretty(combo: String): String =
        combo.split('+').joinToString("+") {
            when (it) {
                "ctrl" -> "Ctrl"
                "alt" -> "Alt"
                "shift" -> "Shift"
                "meta" -> "Super"
                "left" -> "←"
                "right" -> "→"
                "up" -> "↑"
                "down" -> "↓"
                "slash" -> "/"
                "comma" -> ","
                "period" -> "."
                "escape" -> "Esc"
                "space" -> "Space"
                "enter" -> "Enter"
                "tab" -> "Tab"
                "backspace" -> "Backspace"
                else -> if (it.length == 1) it.uppercase() else it.replaceFirstChar(Char::uppercase)
            }
        }

    // Раскладка ЙЦУКЕН → клавиши QWERTY: сочетания работают и с русской раскладкой.
    private const val CYR = "йцукенгшщзхъфывапролджэячсмитьбю"
    private const val LAT = "qwertyuiop[]asdfghjkl;'zxcvbnm,."

    /** Имя клавиши для события; null — клавиша не годится для сочетаний (чистый модификатор). */
    fun name(event: KeyEvent): String? {
        val code = event.key.nativeKeyCode
        when (code) {
            Awt.VK_SPACE -> return "space"
            Awt.VK_LEFT, Awt.VK_KP_LEFT -> return "left"
            Awt.VK_RIGHT, Awt.VK_KP_RIGHT -> return "right"
            Awt.VK_UP, Awt.VK_KP_UP -> return "up"
            Awt.VK_DOWN, Awt.VK_KP_DOWN -> return "down"
            Awt.VK_ESCAPE -> return "escape"
            Awt.VK_ENTER -> return "enter"
            Awt.VK_TAB -> return "tab"
            Awt.VK_BACK_SPACE -> return "backspace"
            Awt.VK_DELETE -> return "delete"
            Awt.VK_HOME -> return "home"
            Awt.VK_END -> return "end"
            Awt.VK_PAGE_UP -> return "pageup"
            Awt.VK_PAGE_DOWN -> return "pagedown"
            Awt.VK_SLASH, Awt.VK_DIVIDE -> return "slash"
            Awt.VK_COMMA -> return "comma"
            Awt.VK_PERIOD -> return "period"
            Awt.VK_OPEN_BRACKET -> return "["
            Awt.VK_CLOSE_BRACKET -> return "]"
            Awt.VK_MINUS, Awt.VK_SUBTRACT -> return "-"
            Awt.VK_EQUALS, Awt.VK_ADD -> return "="
            Awt.VK_SHIFT, Awt.VK_CONTROL, Awt.VK_ALT, Awt.VK_META, Awt.VK_ALT_GRAPH, Awt.VK_CAPS_LOCK, Awt.VK_WINDOWS -> return null
        }
        if (code in Awt.VK_F1..Awt.VK_F12) return "f${code - Awt.VK_F1 + 1}"
        if (code in Awt.VK_0..Awt.VK_9 || code in Awt.VK_A..Awt.VK_Z) {
            return code.toChar().lowercaseChar().toString()
        }
        if (code in Awt.VK_NUMPAD0..Awt.VK_NUMPAD9) return (code - Awt.VK_NUMPAD0).toString()
        // Кириллица и прочее: смотрим на символ и переводим в положение на QWERTY.
        val ch = event.utf16CodePoint.takeIf { it > 0 }?.let { Character.toChars(it).concatToString().lowercase() } ?: return null
        val i = CYR.indexOf(ch)
        if (i >= 0) {
            return when (val l = LAT[i]) {
                ';', '\'' -> null
                ',' -> "comma"
                '.' -> "period"
                else -> l.toString()
            }
        }
        return ch.takeIf { it.length == 1 && !it[0].isISOControl() }
    }

    /** Сочетание из события: «ctrl+shift+k». Shift у печатных знаков вроде «?» остаётся: «shift+slash». */
    fun combo(event: KeyEvent): String? {
        val key = name(event) ?: return null
        val mods = buildList {
            if (event.isAltPressed) add("alt")
            if (event.isCtrlPressed) add("ctrl")
            if (event.isMetaPressed) add("meta")
            if (event.isShiftPressed) add("shift")
        }
        return (mods + key).joinToString("+")
    }
}

/** Где сейчас фокус: в поле ввода обычные клавиши принадлежат полю, не плееру. */
object KeyContext {
    var textFocused by mutableStateOf(false)

    /** Ждём новое сочетание в настройках: приложение молчит, событие забирает диалог. */
    var capturing by mutableStateOf(false)
}

/** Поле ввода сообщает, что в нём печатают: Space и буквы не должны ставить паузу. */
fun Modifier.textFieldFocus(): Modifier = onFocusChanged { KeyContext.textFocused = it.isFocused }

/** Горячие клавиши приложения: вешается на корень окна. */
fun Modifier.appKeys(app: AppState): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || KeyContext.capturing) return@onKeyEvent false
        val combo = Keymap.combo(event) ?: return@onKeyEvent false
        val action = Keymap.find(combo, app.st.keymap) ?: return@onKeyEvent false
        // В поле ввода работают только сочетания, которые не печатаются: Esc, Ctrl+…, F-клавиши.
        if (KeyContext.textFocused && !(action.inText || combo.contains("ctrl+") || combo.contains("alt+") || combo.matches(Regex("f\\d+")))) {
            return@onKeyEvent false
        }
        Actions.run(app, action)
        true
    }

/** Исполнитель действий: общий для клавиш, сокета управления и кнопок. */
object Actions {
    /** Полный экран: окно следит за этим флагом. */
    var fullscreen by mutableStateOf(false)

    /** Окно ставит сюда выход; до этого действие ничего не делает. */
    var quit: () -> Unit = {}

    /** Мини-плеер: маленькое окно поверх остальных вместо главного. */
    var mini by mutableStateOf(false)

    var helpOpen by mutableStateOf(false)
    var searchFocusTick by mutableStateOf(0)

    fun run(app: AppState, action: Action) {
        val p = app.player
        val step = app.st.seekStepSec.coerceAtLeast(1).toDouble()
        when (action) {
            Action.PLAY_PAUSE -> p.toggle()
            Action.NEXT -> p.nextClick()
            Action.PREVIOUS -> p.previous()
            Action.SEEK_BACK -> p.seekBy(-step)
            Action.SEEK_FORWARD -> p.seekBy(step)
            Action.VOLUME_UP -> p.changeVolume(p.volume + 5)
            Action.VOLUME_DOWN -> p.changeVolume(p.volume - 5)
            Action.MUTE -> p.toggleMute()
            Action.SHUFFLE -> p.toggleShuffle()
            Action.REPEAT -> p.cycleRepeat()
            Action.SPEED_DOWN -> p.nudgeSpeed(-0.05f)
            Action.SPEED_UP -> p.nudgeSpeed(0.05f)
            Action.LIKE -> p.current?.let(app::toggleLike)
            Action.DOWNLOAD -> p.current?.let { app.downloads.download(it) }
            Action.SLEEP -> if (p.sleepRemaining != null || p.sleepAfterTrack) p.cancelSleep() else p.startSleep(app.st.sleepTimerDefaultMin)
            Action.STAND -> app.stand.toggle()
            Action.VISUALIZER -> app.setSt { it.copy(playerArt = if (it.playerArt == PlayerArt.COVER) PlayerArt.VISUALIZER else PlayerArt.COVER) }
            Action.LYRICS -> app.setSt { it.copy(showLyrics = !it.showLyrics) }
            Action.SEARCH -> {
                app.tab(Screen.Search)
                searchFocusTick++
            }
            Action.TAB_HOME -> app.tab(Screen.Home)
            Action.TAB_SEARCH -> app.tab(Screen.Search)
            Action.TAB_LIBRARY -> app.tab(Screen.Library)
            Action.TAB_PLAYER -> app.tab(Screen.Player)
            Action.TAB_SETTINGS -> app.tab(Screen.Settings)
            Action.BACK -> if (helpOpen) helpOpen = false else if (app.stand.on) app.stand.off() else app.back()
            Action.HELP -> helpOpen = !helpOpen
            Action.FULLSCREEN -> fullscreen = !fullscreen
            Action.MINI -> mini = !mini
            Action.QUIT -> quit()
        }
    }
}

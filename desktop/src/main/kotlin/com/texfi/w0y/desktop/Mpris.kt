package com.texfi.w0y.desktop

import androidx.compose.runtime.snapshotFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt64
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MediaPlayer2 : DBusInterface {
    fun Raise()

    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MediaPlayer2Player : DBusInterface {
    fun Next()

    fun Previous()

    fun Pause()

    fun PlayPause()

    fun Stop()

    fun Play()

    fun Seek(offset: Long)

    fun SetPosition(trackId: DBusPath, position: Long)

    fun OpenUri(uri: String)
}

/**
 * MPRIS: приложение появляется на шине D-Bus как медиаплеер. Через него работают
 * медиаклавиши, виджеты шторки и панели (waybar, KDE, GNOME) и playerctl:
 * пауза, следующий, предыдущий, перемотка и «что сейчас играет».
 */
class Mpris(private val app: AppState, private val scope: CoroutineScope) : MediaPlayer2, MediaPlayer2Player, Properties {
    private var connection: DBusConnection? = null
    private val started = AtomicBoolean(false)

    private val rootIface = "org.mpris.MediaPlayer2"
    private val playerIface = "org.mpris.MediaPlayer2.Player"

    fun start(): Boolean {
        val ok =
            runCatching {
                val c = DBusConnectionBuilder.forSessionBus().build()
                c.requestBusName("org.mpris.MediaPlayer2.w0y")
                c.exportObject("/org/mpris/MediaPlayer2", this)
                connection = c
            }.onFailure { System.err.println("MPRIS недоступен: $it"); it.printStackTrace() }.isSuccess
        if (!ok) return false
        started.set(true)
        scope.launch {
            snapshotFlow { Snapshot(app.player.current?.id, app.player.playing, app.player.loading, app.player.duration > 0, app.st.shuffle, app.st.repeat) }
                .distinctUntilChanged()
                .collect { notifyChanged() }
        }
        return true
    }

    fun stop() {
        runCatching { connection?.close() }
    }

    private data class Snapshot(val id: String?, val playing: Boolean, val loading: Boolean, val hasDuration: Boolean, val shuffle: Boolean, val repeat: RepeatMode)

    private fun notifyChanged() {
        val c = connection ?: return
        runCatching {
            c.sendMessage(Properties.PropertiesChanged("/org/mpris/MediaPlayer2", playerIface, playerProps(), emptyList()))
        }
    }

    private fun trackId(id: String?) = DBusPath("/org/w0y/track/" + (id ?: "none").replace(Regex("[^A-Za-z0-9_]"), "_"))

    private fun metadata(): Map<String, Variant<*>> {
        val song = app.player.current ?: return mapOf("mpris:trackid" to Variant(trackId(null)))
        val map = linkedMapOf<String, Variant<*>>()
        map["mpris:trackid"] = Variant(trackId(song.id))
        map["xesam:title"] = Variant(song.title)
        map["xesam:artist"] = Variant(arrayOf(song.artist), "as")
        song.album?.let { map["xesam:album"] = Variant(it) }
        song.thumbnailUrl?.let { map["mpris:artUrl"] = Variant(it) }
        if (app.player.duration > 0) map["mpris:length"] = Variant(UInt64((app.player.duration * 1_000_000).toLong()), "t")
        return map
    }

    private fun playerProps(): Map<String, Variant<*>> {
        val p = app.player
        return mapOf(
            "PlaybackStatus" to Variant(if (p.playing) "Playing" else if (p.current == null) "Stopped" else "Paused"),
            "Metadata" to Variant(metadata(), "a{sv}"),
            "Volume" to Variant(p.volume / 100.0),
            "Position" to Variant((p.position * 1_000_000).toLong(), "x"),
            "Rate" to Variant(p.soundNow.speed.toDouble()),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "LoopStatus" to Variant(when (app.st.repeat) { RepeatMode.OFF -> "None"; RepeatMode.ALL -> "Playlist"; RepeatMode.ONE -> "Track" }),
            "Shuffle" to Variant(app.st.shuffle),
            "CanGoNext" to Variant(p.queue.isNotEmpty()),
            "CanGoPrevious" to Variant(p.queue.isNotEmpty()),
            "CanPlay" to Variant(p.current != null),
            "CanPause" to Variant(p.current != null),
            "CanSeek" to Variant(p.duration > 0),
            "CanControl" to Variant(true),
        )
    }

    private fun rootProps(): Map<String, Variant<*>> =
        mapOf(
            "CanQuit" to Variant(true),
            "CanRaise" to Variant(true),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("w0y"),
            "DesktopEntry" to Variant("w0y"),
            "SupportedUriSchemes" to Variant(arrayOf<String>(), "as"),
            "SupportedMimeTypes" to Variant(arrayOf<String>(), "as"),
        )

    // ---- org.freedesktop.DBus.Properties
    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(interfaceName: String?, propertyName: String?): A {
        val all = if (interfaceName == playerIface) playerProps() else rootProps()
        return all[propertyName]?.value as A
    }

    override fun GetAll(interfaceName: String?): Map<String, Variant<*>> = if (interfaceName == playerIface) playerProps() else rootProps()

    override fun <A> Set(interfaceName: String?, propertyName: String?, value: A) {
        if (interfaceName != playerIface) return
        scope.launch {
            when (propertyName) {
                "Volume" -> (value as? Double)?.let { app.player.changeVolume((it * 100).toInt()) }
                "Shuffle" -> (value as? Boolean)?.let { app.player.setShuffle(it) }
                "LoopStatus" ->
                    app.setSt {
                        it.copy(repeat = when (value as? String) { "Track" -> RepeatMode.ONE; "Playlist" -> RepeatMode.ALL; else -> RepeatMode.OFF })
                    }
            }
        }
    }

    // ---- org.mpris.MediaPlayer2
    override fun Raise() { Control.raiseHook() }

    override fun Quit() {
        app.shutdown()
        exitProcess(0)
    }

    // ---- org.mpris.MediaPlayer2.Player
    override fun Next() { app.player.nextClick() }

    override fun Previous() = app.player.previous()

    override fun Pause() {
        if (app.player.playing) app.player.toggle()
    }

    override fun PlayPause() = app.player.toggle()

    override fun Stop() {
        if (app.player.playing) app.player.toggle()
    }

    override fun Play() {
        if (!app.player.playing) app.player.toggle()
    }

    override fun Seek(offset: Long) = app.player.seekSeconds(app.player.position + offset / 1_000_000.0)

    override fun SetPosition(trackId: DBusPath, position: Long) = app.player.seekSeconds(position / 1_000_000.0)

    override fun OpenUri(uri: String) = Unit

    override fun isRemote(): Boolean = false

    override fun getObjectPath(): String = "/org/mpris/MediaPlayer2"
}

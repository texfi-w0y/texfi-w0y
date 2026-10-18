package com.texfi.w0y.desktop

import com.texfi.w0y.data.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: String? = null,
    val thumb: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
) {
    fun toItem() = SongItem(id, title, artist, album, duration, thumb, artistId, albumId)
}

fun SongItem.stored() = StoredSong(id, title, artist, album, durationText, thumbnailUrl, artistId, albumId)

@Serializable
data class StoredPlaylist(
    /** Стабильный локальный ключ: индекс в списке менялся бы при сверке с аккаунтом. */
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val songs: List<StoredSong> = emptyList(),
    /** Идентификатор плейлиста в аккаунте YouTube (без VL), если он там есть. */
    val remoteId: String? = null,
    /** false — чужой плейлист из библиотеки: править его YouTube не даёт. */
    val editable: Boolean = true,
    /** Какие треки были в плейлисте при прошлой сверке — основа трёхстороннего слияния. */
    val base: List<String>? = null,
    val cover: String? = null,
    val syncedAt: Long = 0L,
    /** Описание, цвет (ARGB), значок и своя обложка: только на этом компьютере, как на телефоне. */
    val note: String? = null,
    val accent: Int? = null,
    val glyph: String? = null,
    val customCover: String? = null,
)

@Serializable
data class StoredSound(val speed: Float = 1f, val pitch: Float = 1f, val reverb: String = "OFF")

@Serializable
data class StoredDownload(val path: String, val song: StoredSong)

@Serializable
data class Library(
    val liked: List<StoredSong> = emptyList(),
    val history: List<StoredSong> = emptyList(),
    val playlists: List<StoredPlaylist> = emptyList(),
    val queue: List<StoredSong> = emptyList(),
    val queueIndex: Int = 0,
    val cookie: String? = null,
    val accountName: String? = null,
    val accountAvatar: String? = null,
    val volume: Int = 100,
    val sound: Map<String, StoredSound> = emptyMap(),
    val downloads: Map<String, StoredDownload> = emptyMap(),
    /** Лайки, которые были в аккаунте при прошлой сверке; null — сверки не было. */
    val likesBase: List<String>? = null,
    val settings: DSettings = DSettings(),
    val searchHistory: List<String> = emptyList(),
    /** Сколько раз включали исполнителя (по нижнему регистру) — вкус слушателя для рекомендаций. */
    val artistPlays: Map<String, Int> = emptyMap(),
    /** Журнал прослушиваний для статистики: считается, если проиграно хотя бы полтрека. */
    val playLog: List<StoredPlay> = emptyList(),
    /** Закреплённые на «быстром наборе»: «song:ID», «album:ID», «artist:ID». */
    val dialPins: List<String> = emptyList(),
    /** Спрятанные с «быстрого набора» — не возвращаются, пока их не закрепят. */
    val dialHidden: List<String> = emptyList(),
    /** Свои файлы («Мои файлы»): треки с идентификатором `local:…`, сами файлы лежат в папке данных. */
    val localFiles: List<StoredSong> = emptyList(),
    /** Названия и обложки закреплённых плиток быстрого набора по ключу («artist:…», «album:…»). */
    val dialInfo: Map<String, StoredDial> = emptyMap(),
)

/** Снимок закреплённой плитки быстрого набора: артист, альбом или трек из поиска в журнале не лежат. */
@Serializable
data class StoredDial(val title: String, val subtitle: String? = null, val thumb: String? = null)

/** Одно прослушивание: что, когда (мс) и сколько секунд реально играло. */
@Serializable
data class StoredPlay(val song: StoredSong, val at: Long, val seconds: Int)

/** Всё, что приложение помнит, — одним JSON-файлом в ~/.local/share/w0y. */
class Store {
    companion object {
        fun dirPath(): String = Platform.dataDir().toString()

        fun openDir() = Platform.openPath(dirPath())
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir: Path = Platform.dataDir()
    private val file = dir.resolve("library.json")

    /** Если файл есть, но не читается, он не пропадает: уходит в «.broken-…», а приложение стартует с чистой библиотекой. */
    var loadProblem: String? = null
        private set

    fun load(): Library =
        (if (!Files.exists(file)) Library() else runCatching { json.decodeFromString<Library>(Files.readString(file)) }.getOrElse { e ->
            loadProblem = e.message
            runCatching { Files.copy(file, dir.resolve("library.json.broken-${System.currentTimeMillis()}")) }
            Library()
        })
            // Сессия лежит на диске зашифрованной; в памяти — обычным текстом. Не расшифровалась — вход не выполнен.
            .let { lib -> lib.copy(cookie = lib.cookie?.let(SecureText::unprotect)) }
            // Плейлисты из прошлой версии были без id — заводим, иначе у всех он будет общим.
            .let { lib -> lib.copy(playlists = lib.playlists.map { if (it.id.isBlank()) it.copy(id = java.util.UUID.randomUUID().toString()) else it }) }

    /** Раз за запуск откладывает копию рабочего файла: на случай, если что-то пойдёт не так позже. */
    private var backedUp = false

    @Synchronized
    fun save(library: Library) {
        runCatching {
            Files.createDirectories(dir)
            if (!backedUp && Files.exists(file) && loadProblem == null) {
                backedUp = true
                Files.copy(file, dir.resolve("library.json.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            // Через временный файл: оборванная запись не должна убить библиотеку.
            val tmp = dir.resolve("library.json.tmp")
            Files.writeString(tmp, json.encodeToString(Library.serializer(), library.copy(cookie = library.cookie?.let(SecureText::protect))))
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

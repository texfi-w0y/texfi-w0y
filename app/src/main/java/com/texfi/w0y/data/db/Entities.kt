package com.texfi.w0y.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundProfile

/**
 * Трек, который приложение когда-либо видело: в плейлисте, в лайках,
 * в истории или в загрузках. Одна запись на трек, связи — отдельными
 * таблицами, иначе один и тот же трек хранился бы по разу на каждый список.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val downloadState: Int = DOWNLOAD_NONE,
    /**
     * «Твоя версия»: своя скорость, тон и эхо именно этого трека.
     *
     * Лежит рядом с треком, а не отдельной таблицей, ровно по одной
     * причине: метка версии нужна в каждом списке, а списки и так
     * возвращают эту строку целиком — иначе к каждому пришлось бы
     * приделывать join.
     */
    val speed: Float? = null,
    val pitch: Float? = null,
    val reverb: String? = null,
    /**
     * Почему трек не скачался — имя из [com.texfi.w0y.playback.DownloadFailure].
     * Лежит рядом с состоянием загрузки: список загрузок показывает причину
     * прямо в строке, и молчаливого «не вышло» больше нет.
     */
    val downloadError: String? = null,
    /**
     * Длительность, которую узнал сам плеер. В выдаче YouTube она бывает
     * не везде, а без неё не посчитать ни минуты старых записей истории,
     * ни половину трека для правила прослушивания.
     */
    val durationMs: Long? = null,
    /** Все исполнители со ссылками — [com.texfi.w0y.data.ArtistLink.encode]. */
    val artistLinks: String? = null,
) {
    /** Версия трека, если она вообще задана: скорость обязательна. */
    fun sound(): SoundProfile? =
        speed?.let {
            SoundProfile(
                speed = it,
                pitch = pitch ?: 1f,
                reverb = reverb?.let { name -> runCatching { Reverb.valueOf(name) }.getOrNull() } ?: Reverb.OFF,
            )
        }

    fun toItem(): SongItem =
        SongItem(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationText = durationText,
            thumbnailUrl = thumbnailUrl,
            artistId = artistId,
            albumId = albumId,
            explicit = explicit,
            sound = sound(),
            artists = com.texfi.w0y.data.ArtistLink.decode(artistLinks),
        )

    companion object {
        const val DOWNLOAD_NONE = 0
        const val DOWNLOAD_QUEUED = 1
        const val DOWNLOAD_DONE = 2

        /**
         * Трек из выдачи. Версию звучания здесь не заполняем намеренно:
         * она принадлежит пользователю, как лайк, и приходящий от YouTube
         * трек о ней ничего не знает.
         */
        fun from(song: SongItem): SongEntity =
            SongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationText = song.durationText,
                thumbnailUrl = song.thumbnailUrl,
                artistId = song.artistId,
                albumId = song.albumId,
                explicit = song.explicit,
                artistLinks = com.texfi.w0y.data.ArtistLink.encode(song.artists),
            )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Идентификатор плейлиста в аккаунте, если он пришёл синхронизацией. */
    val remoteId: String? = null,
    /** Обложка плейлиста из аккаунта, в том числе выбранная самим пользователем. */
    val coverUrl: String? = null,
    /**
     * Можно ли менять плейлист в аккаунте. У своих — да; плейлист, который
     * только добавлен в библиотеку, YouTube править не даёт, и показывать
     * для него кнопки правки значило бы обещать невозможное.
     */
    @ColumnInfo(defaultValue = "1") val remoteEditable: Boolean = true,
    /**
     * Какие треки были в плейлисте при последней синхронизации, через
     * перевод строки. С этим снимком видно, что изменилось здесь, а что
     * в аккаунте, — и слияние не теряет правки ни с одной из сторон.
     */
    val syncBase: String? = null,
    /** Когда плейлист последний раз сверялся с аккаунтом, мс. */
    @ColumnInfo(defaultValue = "0") val syncedAt: Long = 0L,
    /** Папка, в которую плейлист разложен; null — лежит просто в списке. Только на этом телефоне. */
    val folderId: Long? = null,
    /** Цвет плейлиста (ARGB) — рамка плитки и значок; null — без цвета. Только на этом телефоне. */
    val accent: Int? = null,
    /** Значок плейлиста — имя глифа Hairline. */
    val glyph: String? = null,
    /** Описание, которое видно над списком треков. */
    val note: String? = null,
    /** Своя обложка — файл на телефоне; важнее обложки из аккаунта. */
    val customCover: String? = null,
    /**
     * Плейлист оторван от аккаунта и остаётся только на телефоне: исчез на
     * сервере или аккаунт сменили, а в нём лежат свои файлы. Такой плейлист
     * не отправляется в аккаунт заново.
     */
    @ColumnInfo(defaultValue = "0") val detached: Boolean = false,
)

/**
 * Свой файл в плейлисте. Живёт отдельно от `playlist_songs`: синхронизация
 * с аккаунтом переписывает ту таблицу целиком, а своим файлам в YouTube
 * делать нечего. При показе накладывается на состав плейлиста по позиции.
 */
@Entity(
    tableName = "local_playlist_items",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")],
)
data class LocalPlaylistItemEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long,
)

/** Папка для плейлистов: их можно раскладывать, как файлы. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId"), Index("playlistId", "position")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long,
)

/**
 * Одно включение трека.
 *
 * Строка заводится на старте — так «недавнее» показывает всё, что включали,
 * — а прослушиванием она становится, только когда реально проиграна хотя бы
 * половина трека (или 4 минуты у длинных): см. [com.texfi.w0y.data.ListenRule].
 * Перемотка вперёд в [listenedMs] не попадает: считается проигранное время,
 * а не позиция.
 *
 * Записи до правила ([legacy]) остаются как были: тогда засчитывался любой
 * старт, и отделить дослушанное от пропущенного задним числом нельзя.
 */
@Entity(tableName = "history", indices = [Index("playedAt"), Index("songId")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
    /** Сколько реально проиграно, мс. У старых записей — 0: тогда не мерили. */
    @ColumnInfo(defaultValue = "0") val listenedMs: Long = 0,
    /** Засчитано ли как прослушивание. */
    @ColumnInfo(defaultValue = "1") val counted: Boolean = false,
    /** Запись до правила прослушивания: считалась по старым правилам. */
    @ColumnInfo(defaultValue = "1") val legacy: Boolean = false,
)

/**
 * Закреплённая плитка на главной.
 *
 * Хранит не ссылку на трек, а самодостаточный снимок: закрепить можно
 * альбом или артиста, которых в локальной библиотеке вообще нет, и плитка
 * должна рисоваться без сети.
 */
@Entity(tableName = "pins")
data class PinEntity(
    /** «вид:идентификатор» — один объект нельзя закрепить дважды. */
    @PrimaryKey val key: String,
    val kind: String,
    val targetId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val pinnedAt: Long,
) {
    companion object {
        const val KIND_SONG = "song"
        const val KIND_ALBUM = "album"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_ARTIST = "artist"

        fun key(kind: String, targetId: String) = "$kind:$targetId"
    }
}

/** Три столбца версии звучания — без остальной строки трека. */
data class SoundRow(
    val speed: Float?,
    val pitch: Float?,
    val reverb: String?,
) {
    fun toProfile(): SoundProfile? =
        speed?.let {
            SoundProfile(
                speed = it,
                pitch = pitch ?: 1f,
                reverb = reverb?.let { name -> runCatching { Reverb.valueOf(name) }.getOrNull() } ?: Reverb.OFF,
            )
        }
}

/** Трек вместе с числом прослушиваний — для «часто слушаешь». */
data class SongPlays(
    @androidx.room.Embedded val song: SongEntity,
    val plays: Int,
)

/** Исполнитель и сколько его слушали — сигнал вкуса для рекомендаций. */
data class ArtistPlays(
    val artist: String,
    val plays: Int,
)

/** Длительность трека и сколько раз его включали — сырьё для «итогов». */
data class DurationPlays(
    val durationText: String?,
    val plays: Int,
)

/**
 * Включение для графиков: когда, сколько проиграно и, для старых записей,
 * длительность трека — по ней оцениваются их минуты.
 */
data class ListenRow(
    val playedAt: Long,
    val listenedMs: Long,
    val legacy: Boolean,
    val counted: Boolean,
    val durationMs: Long?,
    val durationText: String?,
)

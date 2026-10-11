package com.texfi.w0y.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/** Адрес трека для плеера: id в хосте, название и исполнитель — для запасного источника. */
fun w0yUri(song: SongItem): Uri =
    if (com.texfi.w0y.data.LocalMusic.isLocal(song.id)) {
        Uri.parse("file://" + localPath(song.id))
    } else {
        remoteUri(song)
    }

/** Папка своих файлов; выставляется при старте приложения (см. W0yApp). */
@Volatile var localMusicDir: java.io.File? = null

private fun localPath(id: String): String = java.io.File(localMusicDir, id.removePrefix(com.texfi.w0y.data.LocalMusic.PREFIX)).path

private fun remoteUri(song: SongItem): Uri =
    Uri
        .Builder()
        .scheme("w0y")
        .authority(song.id)
        .appendQueryParameter("t", song.title)
        .appendQueryParameter("a", song.artist)
        .apply { song.durationText?.let { appendQueryParameter("d", it) } }
        .build()

/**
 * Подменяет схему `w0y://<videoId>` на настоящую ссылку потока в момент
 * загрузки.
 *
 * Так очередь можно собрать мгновенно из одних идентификаторов, не дожидаясь
 * извлечения потоков, а ключ кэша остаётся стабильным (videoId), хотя сама
 * ссылка живёт несколько часов и меняется.
 */
@OptIn(UnstableApi::class)
class StreamResolver(
    private val repository: YouTubeRepository,
    private val fallback: FallbackAudio,
    private val settings: SettingsRepository,
    /** Своё качество для загрузок; null — то же, что у воспроизведения. */
    private val downloadQuality: (() -> com.texfi.w0y.data.Quality)? = null,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != "w0y") return dataSpec
        val videoId = dataSpec.key ?: dataSpec.uri.host ?: return dataSpec
        // Блокирующий вызов здесь уместен: метод вызывается на загрузочном
        // потоке ExoPlayer, который именно этого результата и ждёт.
        fallback.remember(videoId, dataSpec.uri.getQueryParameter("t"), dataSpec.uri.getQueryParameter("a"), dataSpec.uri.getQueryParameter("d"))
        val audio =
            try {
                val quality = downloadQuality?.invoke()
                val stream = runBlocking { repository.stream(videoId, quality) }
                repository.playable(repository.streamKey(videoId, quality), stream)
            } catch (error: Exception) {
                val prefs = runBlocking { settings.settings.first() }
                if (!prefs.fallbackAudio) throw error
                Timber.w(error, "Основной поток $videoId недоступен, ищу запасной")
                runBlocking {
                    fallback.resolve(
                        videoId = videoId,
                        title = dataSpec.uri.getQueryParameter("t"),
                        artist = dataSpec.uri.getQueryParameter("a"),
                        source = prefs.fallbackSource,
                    )
                } ?: throw error
            }
        return dataSpec
            .buildUpon()
            .setUri(audio.url)
            .setKey(videoId)
            .setHttpRequestHeaders(audio.headers)
            .build()
    }
}

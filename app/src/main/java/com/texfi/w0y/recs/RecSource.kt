package com.texfi.w0y.recs

import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Что приложение умеет спрашивать у YouTube Music для подборок — и ничего больше.
 *
 * Проверено по клиенту: радио по треку (`next` со станцией RDAMVM), страница
 * исполнителя (релизы и похожие), главная с лентами. Чартов, настроений и
 * дат выхода клиент не отдаёт — на них подборки не строятся.
 */
interface RecSource {
    /** Радио по треку: чем YouTube продолжил бы его. Бросает исключение, если сети нет. */
    suspend fun radio(videoId: String): List<SongItem>

    /** Для своего файла — двойник или ближайший по исполнителю трек из каталога. */
    suspend fun anchorFor(local: SongItem): SongItem?

    /** Релизы исполнителя со страницы артиста. */
    suspend fun releases(artistBrowseId: String): List<PlaylistCard>
}

@Singleton
class YoutubeRecSource @Inject constructor(
    private val youtube: YouTubeRepository,
) : RecSource {
    override suspend fun radio(videoId: String): List<SongItem> = youtube.radio(videoId)

    override suspend fun anchorFor(local: SongItem): SongItem? = youtube.findEquivalent(local) ?: youtube.similarSeed(local)

    override suspend fun releases(artistBrowseId: String): List<PlaylistCard> = youtube.artist(artistBrowseId).releases
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecsModule {
    @Binds
    abstract fun source(impl: YoutubeRecSource): RecSource
}

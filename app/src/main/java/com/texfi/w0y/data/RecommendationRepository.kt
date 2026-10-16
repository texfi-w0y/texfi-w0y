package com.texfi.w0y.data

import com.texfi.w0y.recs.RecEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Что включить дальше. Вся логика отбора живёт в [RecEngine]; здесь остался
 * прежний вход для очереди — чтобы плеер не знал про устройство подборок.
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val engine: RecEngine,
) {
    suspend fun continuation(
        seed: SongItem,
        exclude: Set<String> = emptySet(),
        limit: Int = 25,
    ): List<SongItem> = engine.continuation(seed, exclude, limit)
}

package com.texfi.w0y.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.searchStore: DataStore<Preferences> by preferencesDataStore("searches")

/**
 * Последние запросы.
 *
 * Нужны двум вещам сразу: показать недавнее под пустым полем поиска и
 * подсказать рекомендациям, чем человек интересовался, — по одному этому
 * сигналу «бета» уже угадывает заметно лучше, чем по чистой выдаче YouTube.
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    val recent: Flow<List<String>> =
        context.searchStore.data.map { prefs ->
            SearchHistoryLogic.clean(prefs[Keys.RECENT].orEmpty().split(SEPARATOR))
        }

    suspend fun remember(query: String) {
        // Выключено — значит выключено: ничего не пишем, а не пишем «тихо».
        if (!settings.settings.first().saveSearchHistory) return
        if (query.trim().length < MIN_LENGTH) return
        // Повтор поднимается наверх, а не задваивается: список коротких
        // запросов иначе мгновенно забивается одним и тем же словом.
        val updated = SearchHistoryLogic.remember(recent.first(), query)
        context.searchStore.edit { it[Keys.RECENT] = updated.joinToString(SEPARATOR) }
    }

    /** Убирает одну запись, не трогая остальные. */
    suspend fun remove(query: String) {
        val updated = recent.first().filterNot { it.equals(query, ignoreCase = true) }
        context.searchStore.edit { it[Keys.RECENT] = updated.joinToString(SEPARATOR) }
    }

    suspend fun clear() = context.searchStore.edit { it.remove(Keys.RECENT) }

    private object Keys {
        val RECENT = stringPreferencesKey("recent")
    }

    private companion object {
        const val SEPARATOR = "\n"
        /** Пустое и из одних пробелов не сохраняется; остальное — запрос пользователя. */
        const val MIN_LENGTH = 1
    }
}

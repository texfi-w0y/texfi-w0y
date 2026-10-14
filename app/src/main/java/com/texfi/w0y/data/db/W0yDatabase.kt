package com.texfi.w0y.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class,
        PinEntity::class,
        FolderEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class W0yDatabase : RoomDatabase() {
    abstract fun dao(): W0yDao

    companion object {
        /**
         * Закрепления на главной и ссылки трека на артиста с альбомом.
         *
         * Настоящая миграция, а не пересоздание базы: в ней лежат плейлисты
         * и лайки, собранные руками, — терять их при обновлении нельзя.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN artistId TEXT")
                    db.execSQL("ALTER TABLE songs ADD COLUMN albumId TEXT")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pins (
                            key TEXT NOT NULL PRIMARY KEY,
                            kind TEXT NOT NULL,
                            targetId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            subtitle TEXT,
                            thumbnailUrl TEXT,
                            pinnedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }

        /**
         * «Твоя версия» трека: своя скорость, тон и эхо.
         *
         * Три столбца, а не таблица: метка версии нужна в каждом списке, а
         * списки и так возвращают строку трека целиком. NULL значит «играть
         * как все» — так у старых треков после обновления ничего не меняется.
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN speed REAL")
                    db.execSQL("ALTER TABLE songs ADD COLUMN pitch REAL")
                    db.execSQL("ALTER TABLE songs ADD COLUMN reverb TEXT")
                }
            }

        /**
         * Плейлисты аккаунта как свои: обложка, признак «можно править» и
         * снимок для двустороннего слияния. Старые плейлисты остаются как
         * были — правимые, без снимка (первая сверка его заведёт).
         */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE playlists ADD COLUMN coverUrl TEXT")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN remoteEditable INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN syncBase TEXT")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")
                }
            }

        /**
         * Правило прослушивания и причины сбоев загрузки.
         *
         * Старые строки истории получают legacy = 1 и counted = 1: они
         * писались на старте трека, и что из них дослушано, уже не узнать.
         * Ничего не удаляется и не пересчитывается — итоги за прошлое
         * остаются теми же, новое правило действует с обновления.
         */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE history ADD COLUMN listenedMs INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE history ADD COLUMN counted INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE history ADD COLUMN legacy INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_history_songId ON history(songId)")
                    db.execSQL("ALTER TABLE songs ADD COLUMN downloadError TEXT")
                    db.execSQL("ALTER TABLE songs ADD COLUMN durationMs INTEGER")
                }
            }

        /** 6 → 7: у трека все исполнители со ссылками, а не только первый — для фитов. */
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN artistLinks TEXT")
                }
            }

        /** 7 → 8: папки для плейлистов. */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS folders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN folderId INTEGER")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN accent INTEGER")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN glyph TEXT")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN note TEXT")
                    db.execSQL("ALTER TABLE playlists ADD COLUMN customCover TEXT")
                }
            }

        /** Все миграции по порядку — и для приложения, и для тестов. */
        val ALL by lazy { arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8) }

        /** Метка «E»: её показывает список, поэтому хранится вместе с треком. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN explicit INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}

package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.AiChatMessageEntity
import com.example.model.AiChatSessionEntity
import com.example.model.CustomLyricEntity
import com.example.model.PlaylistEntity
import com.example.model.PlaylistTrackCrossRef
import com.example.model.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAddedSec DESC")
    fun observeAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isGeneralAudio = 0 AND isFavorite = 1 ORDER BY lastPlayedAt DESC, title ASC")
    fun observeFavoriteTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isGeneralAudio = 0 AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 30")
    fun observeRecentlyPlayed(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isGeneralAudio = 0 AND playCount > 0 ORDER BY playCount DESC LIMIT 30")
    fun observeMostPlayed(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): Track?

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksSnapshot(): List<Track>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: Track)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, favorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :trackId")
    suspend fun recordPlay(trackId: String, playedAt: Long)

    @Query("DELETE FROM tracks WHERE id NOT IN (:validIds)")
    suspend fun deleteMissingTracks(validIds: List<String>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<String>)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavoriteForIds(ids: List<String>, favorite: Boolean)

    @Query("UPDATE tracks SET isGeneralAudio = :isGeneralAudio WHERE id IN (:ids)")
    suspend fun setGeneralAudioForIds(ids: List<String>, isGeneralAudio: Boolean)

    @Query("UPDATE tracks SET lastPlayedAt = 0 WHERE id IN (:ids)")
    suspend fun clearPlayHistoryForIds(ids: List<String>)

    @Query("UPDATE tracks SET lastPlayedAt = 0")
    suspend fun clearAllRecentHistory()

    @Query("UPDATE tracks SET playCount = 0")
    suspend fun clearMostPlayedStats()

    @Query("UPDATE tracks SET isFavorite = 0, playCount = 0, lastPlayedAt = 0")
    suspend fun clearAllFavoritesAndPlayStats()

    @Query("DELETE FROM tracks")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY position ASC, addedAt ASC")
    fun observeAllRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
    suspend fun updatePlaylistInfo(playlistId: Long, name: String, description: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTracksToPlaylist(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    suspend fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<String>)

    @Query("DELETE FROM playlist_tracks WHERE trackId IN (:trackIds)")
    suspend fun removeTracksFromAllPlaylists(trackIds: List<String>)

    @Query("SELECT * FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun getRefsForTrack(trackId: String): List<PlaylistTrackCrossRef>

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackPosition(playlistId: Long, trackId: String, position: Int)

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearAllPlaylistTracks()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()
}

@Dao
interface CustomLyricDao {
    @Query("SELECT * FROM custom_lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getCustomLyric(trackId: String): CustomLyricEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomLyric(entity: CustomLyricEntity)

    @Query("UPDATE custom_lyrics SET offsetMs = :offsetMs WHERE trackId = :trackId")
    suspend fun updateOffset(trackId: String, offsetMs: Long)

    @Query("DELETE FROM custom_lyrics WHERE trackId = :trackId")
    suspend fun deleteCustomLyric(trackId: String)

    @Query("DELETE FROM custom_lyrics")
    suspend fun clearAllCustomLyrics()
}

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC, id DESC")
    fun observeSessions(): Flow<List<AiChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC, id DESC")
    suspend fun getSessionsSnapshot(): List<AiChatSessionEntity>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeMessagesForSession(sessionId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesSnapshot(sessionId: Long): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AiChatSessionEntity): Long

    @Query("UPDATE ai_chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameSession(sessionId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ai_chat_sessions SET updatedAt = :updatedAt, modelId = :modelId, reasoningEffort = :effort WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, updatedAt: Long, modelId: String, effort: String)

    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSessionMessages(sessionId: Long)

    @Query("DELETE FROM ai_chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM ai_chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM ai_chat_sessions")
    suspend fun clearAllSessions()
}

@Database(
    entities = [
        Track::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        CustomLyricEntity::class,
        AiChatSessionEntity::class,
        AiChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AuralisDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun customLyricDao(): CustomLyricDao
    abstract fun aiChatDao(): AiChatDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_chat_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `reasoningEffort` TEXT NOT NULL,
                        `boundTrackTitle` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `reasoningContent` TEXT,
                        `reasoningDurationMs` INTEGER NOT NULL,
                        `reasoningEffortUsed` TEXT,
                        `modelUsed` TEXT,
                        `usedVision` INTEGER NOT NULL,
                        `attachedTrackLabel` TEXT,
                        `errorSummary` TEXT,
                        `errorRawDetail` TEXT,
                        `httpStatusCode` INTEGER,
                        `errorEndpoint` TEXT,
                        `suggestedEqJson` TEXT,
                        `curatedTitlesCsv` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `ai_chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_sessionId_createdAt` ON `ai_chat_messages` (`sessionId`, `createdAt`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isGeneralAudio` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ai_chat_messages` ADD COLUMN `suggestedPlaylistJson` TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AuralisDatabase? = null

        fun getInstance(context: Context): AuralisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuralisDatabase::class.java,
                    "auralis_studio.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

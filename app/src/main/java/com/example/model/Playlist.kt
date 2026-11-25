package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithCount(
    val playlist: PlaylistEntity,
    val trackCount: Int,
    val coverArtworkPath: String?
)

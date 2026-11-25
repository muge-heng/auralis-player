package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.ScanFilterConfig
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LocalMusicScanner"

data class ScanProgress(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val matchedCount: Int = 0,
    val currentFile: String = ""
)

class LocalMusicScanner(
    private val context: Context,
    private val metadataExtractor: MetadataExtractor
) {

    suspend fun scanDeviceMediaStore(
        filter: ScanFilterConfig,
        existingTracksById: Map<String, Track>,
        onProgress: (ScanProgress) -> Unit
    ): List<Track> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val minDurationMs = filter.minDurationSec * 1000L
        val minSizeBytes = filter.minSizeKb * 1024L

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        if (filter.excludeSystemAudio) {
            selectionParts.add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        }
        if (minDurationMs > 0) {
            selectionParts.add("${MediaStore.Audio.Media.DURATION} >= ?")
            selectionArgs.add(minDurationMs.toString())
        }
        if (minSizeBytes > 0) {
            selectionParts.add("${MediaStore.Audio.Media.SIZE} >= ?")
            selectionArgs.add(minSizeBytes.toString())
        }

        val selection = selectionParts.joinToString(" AND ").ifBlank { null }
        val args = selectionArgs.toTypedArray().takeIf { it.isNotEmpty() }

        var scanned = 0
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                }

                while (cursor.moveToNext()) {
                    scanned++
                    val mediaId = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "track_$mediaId"
                    val rawPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val folderPath = normalizeFolder(rawPath)

                    if (isExcludedByFolder(folderPath, displayName, filter)) {
                        continue
                    }

                    onProgress(
                        ScanProgress(
                            isScanning = true,
                            scannedCount = scanned,
                            matchedCount = results.size,
                            currentFile = displayName
                        )
                    )

                    val contentUri = ContentUris.withAppendedId(collection, mediaId)
                    val trackId = "ms_$mediaId"
                    val existing = existingTracksById[trackId]
                    val sizeBytes = cursor.getLong(sizeCol)
                    val durationMs = cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val isMusicFlag = if (isMusicCol >= 0) cursor.getInt(isMusicCol) != 0 else true
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    // Reuse deep metadata if already scanned and file size unchanged
                    if (existing != null && existing.sizeBytes == sizeBytes && existing.durationMs == durationMs) {
                        val passesStrict = passesStrictMusicCriteria(
                            hasAnyMeta = existing.hasValidArtist || existing.hasValidAlbum || !existing.artworkPath.isNullOrBlank() || existing.genre.isNotBlank() || existing.year > 0,
                            hasArtist = existing.hasValidArtist,
                            hasAlbumOrArt = existing.hasValidAlbum || !existing.artworkPath.isNullOrBlank(),
                            filter = filter
                        )
                        if (!passesStrict && !filter.classifyUnverifiedAsGeneralAudio && !existing.isGeneralAudio) {
                            continue
                        }
                        results.add(
                            existing.copy(
                                contentUri = contentUri.toString(),
                                folderPath = folderPath,
                                albumArtUri = albumArtUri
                            )
                        )
                        continue
                    }

                    val details = metadataExtractor.extract(contentUri, displayName, sizeBytes)
                    val mediaTitle = cursor.getString(titleCol)?.takeIf { com.example.model.isValidMusicTag(it) }
                    val mediaArtist = cursor.getString(artistCol)?.takeIf { com.example.model.isValidMusicTag(it, "未知艺术家") }
                    val mediaAlbum = cursor.getString(albumCol)?.takeIf { com.example.model.isValidMusicTag(it, "未知专辑") }

                    val resolvedArtist = details.artist?.takeIf { com.example.model.isValidMusicTag(it, "未知艺术家") }
                        ?: mediaArtist
                        ?: "未知艺术家"
                    val resolvedAlbum = details.album?.takeIf { com.example.model.isValidMusicTag(it, "未知专辑") }
                        ?: mediaAlbum
                        ?: "未知专辑"

                    val hasArtist = com.example.model.isValidMusicTag(resolvedArtist, "未知艺术家")
                    val hasAlbum = com.example.model.isValidMusicTag(resolvedAlbum, "未知专辑")
                    val hasArt = !details.artworkPath.isNullOrBlank()
                    val hasAnyMeta = hasArtist || hasAlbum || hasArt ||
                        !details.title.isNullOrBlank() || !details.genre.isNullOrBlank() || details.year > 0

                    val passesStrict = passesStrictMusicCriteria(
                        hasAnyMeta = hasAnyMeta,
                        hasArtist = hasArtist,
                        hasAlbumOrArt = hasAlbum || hasArt,
                        filter = filter
                    )
                    val autoGeneralAudio = MetadataExtractor.classifyAudioAsGeneral(
                        rawTitleTag = details.title ?: mediaTitle,
                        artist = resolvedArtist,
                        album = resolvedAlbum,
                        artworkPath = details.artworkPath,
                        genre = details.genre.orEmpty(),
                        year = details.year,
                        isLossless = details.isLossless,
                        isMusicFlag = isMusicFlag
                    )

                    if (!passesStrict && !filter.classifyUnverifiedAsGeneralAudio) {
                        continue
                    }
                    val finalIsGeneralAudio = existing?.isGeneralAudio ?: (!passesStrict || autoGeneralAudio)

                    results.add(
                        Track(
                            id = trackId,
                            contentUri = contentUri.toString(),
                            title = details.title ?: mediaTitle ?: displayName.substringBeforeLast("."),
                            artist = resolvedArtist,
                            album = resolvedAlbum,
                            albumArtist = details.albumArtist.orEmpty(),
                            genre = details.genre.orEmpty(),
                            year = details.year,
                            trackNumber = details.trackNumber,
                            durationMs = if (durationMs > 0) durationMs else details.durationMs,
                            sizeBytes = sizeBytes,
                            folderPath = folderPath,
                            fileName = displayName,
                            mimeType = details.mimeType.ifBlank { cursor.getString(mimeCol) ?: "audio/mpeg" },
                            codecLabel = details.codecLabel,
                            bitrateKbps = details.bitrateKbps,
                            sampleRateHz = details.sampleRateHz,
                            bitDepth = details.bitDepth,
                            isLossless = details.isLossless,
                            isHiRes = details.isHiRes,
                            artworkPath = details.artworkPath,
                            albumArtUri = albumArtUri,
                            embeddedLyrics = details.embeddedLyrics,
                            dateAddedSec = cursor.getLong(dateCol),
                            playCount = existing?.playCount ?: 0,
                            lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                            isFavorite = existing?.isFavorite ?: false,
                            isGeneralAudio = finalIsGeneralAudio
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio permission denied during MediaStore scan", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore scan error", e)
        }

        onProgress(ScanProgress(isScanning = false, scannedCount = scanned, matchedCount = results.size))
        results
    }

    suspend fun importAudioUris(
        uris: List<Uri>,
        folderLabel: String = "手动导入"
    ): List<Track> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<Track>()
        for (uri in uris) {
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val (displayName, sizeBytes) = queryUriMeta(uri)
                val track = metadataExtractor.buildTrackFromUri(uri, displayName, sizeBytes, folderLabel)
                imported.add(track)
            } catch (e: Exception) {
                Log.w(TAG, "Failed importing $uri: ${e.message}")
            }
        }
        imported
    }

    suspend fun importFolderTree(
        treeUri: Uri,
        filter: ScanFilterConfig,
        onProgress: (ScanProgress) -> Unit
    ): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "alac", "aiff", "ape", "wma")
        val tracks = mutableListOf<Track>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to rootDocId.substringAfterLast("/").substringAfterLast(":").ifBlank { "导入目录" })

        var scanned = 0
        while (queue.isNotEmpty()) {
            val (docId, folderName) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val childId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        val size = c.getLong(3)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!isExcludedByFolder(name, name, filter)) {
                                queue.add(childId to name)
                            }
                        } else {
                            val ext = name.substringAfterLast(".", "").lowercase()
                            if (mime.startsWith("audio/") || ext in audioExtensions) {
                                scanned++
                                if (size >= filter.minSizeKb * 1024L || size <= 0L) {
                                    onProgress(ScanProgress(true, scanned, tracks.size, name))
                                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                    val track = metadataExtractor.buildTrackFromUri(docUri, name, size, folderName)
                                    if (track.durationMs >= filter.minDurationSec * 1000L || track.durationMs == 0L) {
                                        tracks.add(track)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Folder scan error in $folderName: ${e.message}")
            }
        }
        onProgress(ScanProgress(false, scanned, tracks.size))
        tracks
    }

    fun findSidecarLyric(track: Track): Pair<String, String>? {
        // Attempt direct filesystem sidecar discovery when path is readable
        val baseName = track.fileName.substringBeforeLast(".")
        if (baseName.isBlank()) return null
        val candidates = listOf("qrc", "yrc", "krc", "ttml", "lrc", "srt", "txt")
        val possibleDirs = listOfNotNull(
            File("/storage/emulated/0/${track.folderPath}"),
            File("/storage/emulated/0/Music"),
            File("/storage/emulated/0/Download")
        )
        for (dir in possibleDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (ext in candidates) {
                val f = File(dir, "$baseName.$ext")
                if (f.exists() && f.canRead() && f.length() in 1..2_000_000) {
                    val content = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                    if (!content.isNullOrBlank()) {
                        return content to "本地同名歌词 (.${ext})"
                    }
                }
            }
        }
        return null
    }

    private fun queryUriMeta(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast("/") ?: "未知音频"
        var size = 0L
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return name to size
    }

    private fun normalizeFolder(rawPath: String): String {
        val cleaned = rawPath.trim().trimEnd('/')
        if (cleaned.isBlank()) return "Music"
        val afterRoot = cleaned.substringAfter("/storage/emulated/0/", cleaned)
        return if (afterRoot.contains(".")) {
            afterRoot.substringBeforeLast("/", "Music")
        } else {
            afterRoot
        }
    }

    private fun isExcludedByFolder(
        folderPath: String,
        displayName: String,
        filter: ScanFilterConfig
    ): Boolean {
        val lowerPath = folderPath.lowercase()
        val lowerName = displayName.lowercase()
        if (filter.excludedFolders.any { ex ->
                val token = ex.trim().lowercase()
                token.isNotEmpty() && (lowerPath.contains(token) || lowerName.startsWith("ptt-"))
            }
        ) {
            return true
        }
        if (filter.includedFoldersOnly.isNotEmpty()) {
            return filter.includedFoldersOnly.none { inc ->
                lowerPath.contains(inc.trim().lowercase())
            }
        }
        return false
    }

    private fun passesStrictMusicCriteria(
        hasAnyMeta: Boolean,
        hasArtist: Boolean,
        hasAlbumOrArt: Boolean,
        filter: ScanFilterConfig
    ): Boolean {
        if (filter.requireMetadata && !hasAnyMeta) return false
        if (filter.requireArtist && !hasArtist) return false
        if (filter.requireAlbumOrArt && !hasAlbumOrArt) return false
        return true
    }
}

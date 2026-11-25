package com.example.data.lyrics

import android.util.Base64
import android.util.Log
import com.example.model.LyricSource
import com.example.model.OnlineLyricCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "OnlineLyricClient"
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

data class OnlineLyricSearchResult(
    val candidates: List<OnlineLyricCandidate>,
    val sourceSummaries: Map<LyricSource, String> = emptyMap()
)

/**
 * Multi-source online lyric client supporting:
 * - 网易云音乐 (NetEase Cloud Music: LRC + bilingual tlyric + native YRC)
 * - QQ 音乐 (QQ Music: LRC + bilingual trans)
 * - 酷狗音乐 (Kugou Music: native word-by-word KRC + LRC)
 * - LRCLIB (Open synced lyrics catalog)
 */
class OnlineLyricClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        source: LyricSource = LyricSource.ALL
    ): Result<OnlineLyricSearchResult> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("请输入要检索的曲名"))
        }
        val cleanArtist = if (artist == "未知艺术家") "" else artist.trim()
        val cleanAlbum = if (album == "未知专辑") "" else album.trim()

        val targets = if (source == LyricSource.ALL) {
            LyricSource.selectableProviders
        } else {
            listOf(source)
        }

        val summaries = mutableMapOf<LyricSource, String>()
        val results = coroutineScope {
            targets.map { target ->
                async {
                    val outcome = when (target) {
                        LyricSource.NETEASE -> searchNetEase(cleanTitle, cleanArtist)
                        LyricSource.QQ_MUSIC -> searchQQMusic(cleanTitle, cleanArtist)
                        LyricSource.KUGOU -> searchKugou(cleanTitle, cleanArtist, durationMs)
                        LyricSource.LRCLIB -> searchLrcLib(cleanTitle, cleanArtist, cleanAlbum)
                        LyricSource.ALL -> Result.success(emptyList())
                    }
                    target to outcome
                }
            }.awaitAll()
        }

        val allCandidates = mutableListOf<OnlineLyricCandidate>()
        var lastErr: Throwable? = null
        results.forEach { (src, res) ->
            res.fold(
                onSuccess = { list ->
                    allCandidates.addAll(list)
                    summaries[src] = if (list.isEmpty()) "无匹配" else "${list.size} 条"
                },
                onFailure = { err ->
                    lastErr = err
                    summaries[src] = err.localizedMessage?.take(36) ?: "连接受限"
                    Log.w(TAG, "Lyric source ${src.id} failed: ${err.message}")
                }
            )
        }

        val ranked = allCandidates.sortedWith(
            compareByDescending<OnlineLyricCandidate> { it.isWordSynced }
                .thenByDescending { it.hasTranslation }
                .thenByDescending { !it.syncedLyrics.isNullOrBlank() }
        )

        if (ranked.isEmpty() && lastErr != null && targets.size == 1) {
            Result.failure(lastErr!!)
        } else {
            Result.success(OnlineLyricSearchResult(ranked, summaries))
        }
    }

    // -------------------------------------------------------------------------
    // 1. 网易云音乐 (NetEase Cloud Music)
    // -------------------------------------------------------------------------
    private suspend fun searchNetEase(
        title: String,
        artist: String
    ): Result<List<OnlineLyricCandidate>> = runCatching {
        val keyword = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val searchUrl = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", keyword)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "6")
            .build()

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://music.163.com/")
            .get()
            .build()

        val songs = client.newCall(searchReq).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            val arr = root.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length().coerceAtMost(5)) {
                    val s = arr.optJSONObject(i) ?: continue
                    val id = s.optLong("id", 0L)
                    if (id <= 0L) continue
                    val songName = s.optString("name", title)
                    val artistsArr = s.optJSONArray("artists")
                    val artistNames = buildList {
                        if (artistsArr != null) {
                            for (a in 0 until artistsArr.length()) {
                                val n = artistsArr.optJSONObject(a)?.optString("name").orEmpty()
                                if (n.isNotBlank()) add(n)
                            }
                        }
                    }.joinToString(" / ").ifBlank { artist }
                    val albumName = s.optJSONObject("album")?.optString("name").orEmpty()
                    val durSec = s.optLong("duration", 0L) / 1000.0
                    add(NetEaseHit(id, songName, artistNames, albumName, durSec))
                }
            }
        }

        coroutineScope {
            songs.map { hit ->
                async { fetchNetEaseLyric(hit) }
            }.awaitAll().filterNotNull()
        }
    }

    private data class NetEaseHit(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Double
    )

    private fun fetchNetEaseLyric(hit: NetEaseHit): OnlineLyricCandidate? {
        return runCatching {
            val url = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
                .addQueryParameter("id", hit.id.toString())
                .addQueryParameter("lv", "1")
                .addQueryParameter("tv", "1")
                .addQueryParameter("yv", "1")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "https://music.163.com/")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string().orEmpty())
                val yrc = root.optJSONObject("yrc")?.optString("lyric", "")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val lrc = root.optJSONObject("lrc")?.optString("lyric", "")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val tlyric = root.optJSONObject("tlyric")?.optString("lyric", "")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                val primary = yrc ?: lrc ?: return null
                val hasTrans = !tlyric.isNullOrBlank() && yrc == null
                val mergedSynced = if (hasTrans) "$lrc\n$tlyric" else primary
                val hasTimestamps = mergedSynced.contains("[")

                OnlineLyricCandidate(
                    id = hit.id,
                    trackName = hit.title,
                    artistName = hit.artist,
                    albumName = hit.album,
                    durationSec = hit.durationSec,
                    syncedLyrics = if (hasTimestamps) mergedSynced else null,
                    plainLyrics = if (!hasTimestamps) mergedSynced else null,
                    source = LyricSource.NETEASE,
                    isWordSynced = yrc != null,
                    hasTranslation = hasTrans,
                    previewSnippet = extractPreviewSnippet(mergedSynced)
                )
            }
        }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // 2. QQ 音乐 (QQ Music)
    // -------------------------------------------------------------------------
    private suspend fun searchQQMusic(
        title: String,
        artist: String
    ): Result<List<OnlineLyricCandidate>> = runCatching {
        val keyword = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val hits = searchQQViaMusicU(keyword).ifEmpty { searchQQViaLegacySoso(keyword) }

        coroutineScope {
            hits.take(5).map { hit ->
                async { fetchQQMusicLyric(hit) }
            }.awaitAll().filterNotNull()
        }
    }

    private data class QQHit(
        val id: Long,
        val songMid: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Double
    )

    private fun searchQQViaMusicU(keyword: String): List<QQHit> = runCatching {
        val payload = JSONObject().put(
            "req_1",
            JSONObject()
                .put("method", "DoSearchForQQMusicDesktop")
                .put("module", "music.search.SearchCgiService")
                .put(
                    "param",
                    JSONObject()
                        .put("query", keyword)
                        .put("page_num", 1)
                        .put("num_per_page", 6)
                        .put("search_type", 0)
                )
        )
        val req = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://y.qq.com/")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val root = JSONObject(resp.body?.string().orEmpty())
            val list = root.optJSONObject("req_1")
                ?.optJSONObject("data")
                ?.optJSONObject("body")
                ?.optJSONObject("song")
                ?.optJSONArray("list") ?: return emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val obj = list.optJSONObject(i) ?: continue
                    val mid = obj.optString("mid", "").ifBlank { obj.optString("songmid", "") }
                    if (mid.isBlank()) continue
                    val singers = obj.optJSONArray("singer")
                    val singerStr = buildList {
                        if (singers != null) {
                            for (s in 0 until singers.length()) {
                                val n = singers.optJSONObject(s)?.optString("name").orEmpty()
                                if (n.isNotBlank()) add(n)
                            }
                        }
                    }.joinToString(" / ")
                    val albumName = obj.optJSONObject("album")?.optString("name").orEmpty()
                    add(
                        QQHit(
                            id = obj.optLong("id", i.toLong()),
                            songMid = mid,
                            title = obj.optString("title", obj.optString("name", keyword)),
                            artist = singerStr,
                            album = albumName,
                            durationSec = obj.optDouble("interval", 0.0)
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun searchQQViaLegacySoso(keyword: String): List<QQHit> = runCatching {
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp".toHttpUrl().newBuilder()
            .addQueryParameter("w", keyword)
            .addQueryParameter("p", "1")
            .addQueryParameter("n", "6")
            .addQueryParameter("format", "json")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://y.qq.com/")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val raw = resp.body?.string().orEmpty().trim()
            val jsonText = if (raw.startsWith("callback(") && raw.endsWith(")")) {
                raw.removePrefix("callback(").removeSuffix(")")
            } else {
                raw
            }
            val list = JSONObject(jsonText)
                .optJSONObject("data")
                ?.optJSONObject("song")
                ?.optJSONArray("list") ?: return emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val obj = list.optJSONObject(i) ?: continue
                    val mid = obj.optString("songmid", "")
                    if (mid.isBlank()) continue
                    val singers = obj.optJSONArray("singer")
                    val singerStr = buildList {
                        if (singers != null) {
                            for (s in 0 until singers.length()) {
                                val n = singers.optJSONObject(s)?.optString("name").orEmpty()
                                if (n.isNotBlank()) add(n)
                            }
                        }
                    }.joinToString(" / ")
                    add(
                        QQHit(
                            id = obj.optLong("songid", i.toLong()),
                            songMid = mid,
                            title = obj.optString("songname", keyword),
                            artist = singerStr,
                            album = obj.optString("albumname", ""),
                            durationSec = obj.optDouble("interval", 0.0)
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun fetchQQMusicLyric(hit: QQHit): OnlineLyricCandidate? = runCatching {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg".toHttpUrl().newBuilder()
            .addQueryParameter("songmid", hit.songMid)
            .addQueryParameter("format", "json")
            .addQueryParameter("nobase64", "1")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://y.qq.com/")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val raw = resp.body?.string().orEmpty().trim()
            val cleanJson = if (raw.startsWith("MusicJsonCallback(") && raw.endsWith(")")) {
                raw.removePrefix("MusicJsonCallback(").removeSuffix(")")
            } else {
                raw
            }
            val obj = JSONObject(cleanJson)
            val lrcRaw = decodeQQField(obj.optString("lyric", ""))
            if (lrcRaw.isBlank()) return null
            val transRaw = decodeQQField(obj.optString("trans", ""))
            val hasTrans = transRaw.isNotBlank() && transRaw.contains("[")
            val combined = if (hasTrans) "$lrcRaw\n$transRaw" else lrcRaw
            val hasTimestamps = combined.contains("[")

            OnlineLyricCandidate(
                id = hit.id,
                trackName = hit.title,
                artistName = hit.artist,
                albumName = hit.album,
                durationSec = hit.durationSec,
                syncedLyrics = if (hasTimestamps) combined else null,
                plainLyrics = if (!hasTimestamps) combined else null,
                source = LyricSource.QQ_MUSIC,
                isWordSynced = false,
                hasTranslation = hasTrans,
                previewSnippet = extractPreviewSnippet(combined)
            )
        }
    }.getOrNull()

    private fun decodeQQField(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "null") return ""
        val decoded = if (!trimmed.contains("[") && trimmed.length > 24 && trimmed.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))) {
            runCatching { String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(trimmed)
        } else {
            trimmed
        }
        return decoded
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#10;", "\n")
            .replace("&#13;", "\r")
            .replace("&#32;", " ")
            .replace("&#39;", "'")
            .replace("&#58;", ":")
            .replace("&#46;", ".")
            .trim()
    }

    // -------------------------------------------------------------------------
    // 3. 酷狗音乐 (Kugou Music: KRC word-by-word & LRC)
    // -------------------------------------------------------------------------
    private suspend fun searchKugou(
        title: String,
        artist: String,
        durationMs: Long
    ): Result<List<OnlineLyricCandidate>> = runCatching {
        val keyword = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")
        val urlBuilder = "https://krcs.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "mobi")
            .addQueryParameter("keyword", keyword)
        if (durationMs > 0L) {
            urlBuilder.addQueryParameter("duration", durationMs.toString())
        }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val hits = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            val arr = root.optJSONArray("candidates") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length().coerceAtMost(5)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    val accesskey = obj.optString("accesskey", "")
                    if (id.isBlank() || accesskey.isBlank()) continue
                    add(
                        KugouHit(
                            id = id,
                            accessKey = accesskey,
                            title = obj.optString("song", title),
                            artist = obj.optString("singer", artist),
                            durationSec = obj.optLong("duration", durationMs) / 1000.0
                        )
                    )
                }
            }
        }

        coroutineScope {
            hits.map { hit ->
                async { fetchKugouLyric(hit) }
            }.awaitAll().filterNotNull()
        }
    }

    private data class KugouHit(
        val id: String,
        val accessKey: String,
        val title: String,
        val artist: String,
        val durationSec: Double
    )

    private fun fetchKugouLyric(hit: KugouHit): OnlineLyricCandidate? {
        // Prefer native word-by-word KRC, fallback to LRC
        val krcText = downloadKugouFormat(hit, "krc")
        if (!krcText.isNullOrBlank() && krcText.contains("<")) {
            return OnlineLyricCandidate(
                id = hit.id.toLongOrNull() ?: hit.id.hashCode().toLong(),
                trackName = hit.title,
                artistName = hit.artist,
                albumName = "酷狗逐字曲库",
                durationSec = hit.durationSec,
                syncedLyrics = krcText,
                plainLyrics = null,
                source = LyricSource.KUGOU,
                isWordSynced = true,
                hasTranslation = false,
                previewSnippet = extractPreviewSnippet(krcText)
            )
        }
        val lrcText = downloadKugouFormat(hit, "lrc") ?: return null
        return OnlineLyricCandidate(
            id = hit.id.toLongOrNull() ?: hit.id.hashCode().toLong(),
            trackName = hit.title,
            artistName = hit.artist,
            albumName = "酷狗词库",
            durationSec = hit.durationSec,
            syncedLyrics = if (lrcText.contains("[")) lrcText else null,
            plainLyrics = if (!lrcText.contains("[")) lrcText else null,
            source = LyricSource.KUGOU,
            isWordSynced = false,
            hasTranslation = false,
            previewSnippet = extractPreviewSnippet(lrcText)
        )
    }

    private fun downloadKugouFormat(hit: KugouHit, fmt: String): String? = runCatching {
        val url = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "pc")
            .addQueryParameter("id", hit.id)
            .addQueryParameter("accesskey", hit.accessKey)
            .addQueryParameter("fmt", fmt)
            .addQueryParameter("charset", "utf8")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            val b64 = root.optString("content", "")
            if (b64.isBlank()) return null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            LyricParser.decodeRawBytes(bytes, "lyric.$fmt").trim().takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    // -------------------------------------------------------------------------
    // 4. LRCLIB (Open Synced Lyrics Index)
    // -------------------------------------------------------------------------
    private fun searchLrcLib(
        title: String,
        artist: String,
        album: String
    ): Result<List<OnlineLyricCandidate>> = runCatching {
        val urlBuilder = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
        if (artist.isNotBlank()) urlBuilder.addQueryParameter("artist_name", artist)
        if (album.isNotBlank()) urlBuilder.addQueryParameter("album_name", album)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "AuralisMusicPlayer/1.0 (Android)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length().coerceAtMost(8)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val synced = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
                    val plain = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
                    if (synced == null && plain == null) continue
                    val rawPreview = synced ?: plain.orEmpty()
                    add(
                        OnlineLyricCandidate(
                            id = obj.optLong("id", i.toLong()),
                            trackName = obj.optString("trackName", title),
                            artistName = obj.optString("artistName", artist),
                            albumName = obj.optString("albumName", album),
                            durationSec = obj.optDouble("duration", 0.0),
                            syncedLyrics = synced,
                            plainLyrics = plain,
                            source = LyricSource.LRCLIB,
                            isWordSynced = false,
                            hasTranslation = false,
                            previewSnippet = extractPreviewSnippet(rawPreview)
                        )
                    )
                }
            }
        }
    }

    private fun extractPreviewSnippet(raw: String): String {
        val cleaned = raw.lineSequence()
            .map { line ->
                line.replace(Regex("""\[[^\]]*]"""), "")
                    .replace(Regex("""<[^>]*>"""), "")
                    .replace(Regex("""\(\d+,\d+(?:,\d+)?\)"""), "")
                    .trim()
            }
            .filter {
                it.length >= 2 &&
                    !it.startsWith("作词") &&
                    !it.startsWith("作曲") &&
                    !it.startsWith("编曲") &&
                    !it.contains("纯音乐")
            }
            .take(2)
            .joinToString(" / ")
        return cleaned.take(64)
    }
}

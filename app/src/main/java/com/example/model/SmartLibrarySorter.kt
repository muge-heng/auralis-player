package com.example.model

import kotlin.math.ln

/**
 * Dynamic multi-factor Smart Sort engine for Auralis Library.
 *
 * Combines favorite affinity, log-scaled play engagement, anti-fatigue cooldown for recently
 * played tracks, rediscovery boost for beloved tracks not heard in 2-14 days, fresh-arrival lift,
 * studio Hi-Res/Lossless subtle weighting, controlled session+time-bucket jitter, and artist
 * diversity de-clustering so the library feels alive while never altering an already-created playback queue.
 */
object SmartLibrarySorter {

    private const val THREE_HOURS_MS = 3L * 60L * 60L * 1000L
    private const val TWO_DAYS_MS = 2L * 24L * 60L * 60L * 1000L
    private const val FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L
    private const val SEVEN_DAYS_SEC = 7L * 24L * 60L * 60L
    private const val HALF_HOUR_MS = 30L * 60L * 1000L

    fun sort(
        tracks: List<Track>,
        sessionSeed: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<Track> {
        if (tracks.size <= 1) return tracks

        val timeBucket = nowMs / HALF_HOUR_MS
        val nowSec = nowMs / 1000L

        val scored = tracks.map { track ->
            track to computeTrackScore(
                track = track,
                sessionSeed = sessionSeed,
                timeBucket = timeBucket,
                nowMs = nowMs,
                nowSec = nowSec
            )
        }.sortedByDescending { it.second }.toMutableList()

        // Artist diversity de-clustering: avoid back-to-back identical artists at the top
        // when another artist is close in score.
        val result = ArrayList<Track>(scored.size)
        var lastArtistKey: String? = null
        while (scored.isNotEmpty()) {
            var bestIdx = 0
            if (lastArtistKey != null && scored.size > 1) {
                val topScore = scored[0].second
                val candidateIdx = scored.indexOfFirst { (t, sc) ->
                    normalizeArtistKey(t.artist) != lastArtistKey && (topScore - sc) <= 2.2
                }
                if (candidateIdx >= 0) {
                    bestIdx = candidateIdx
                }
            }
            val chosen = scored.removeAt(bestIdx).first
            result.add(chosen)
            lastArtistKey = normalizeArtistKey(chosen.artist)
        }
        return result
    }

    internal fun computeTrackScore(
        track: Track,
        sessionSeed: Long,
        timeBucket: Long,
        nowMs: Long,
        nowSec: Long
    ): Double {
        var score = 0.0

        // 1. Favorite affinity
        if (track.isFavorite) {
            score += 2.40
        }

        // 2. Engagement (log-scaled play count, gently capped so one song never monopolizes #1 forever)
        val plays = track.playCount.coerceAtLeast(0)
        if (plays > 0) {
            score += (ln(1.0 + plays) * 1.15).coerceAtMost(4.8)
        } else {
            // Unheard discovery lift
            score += 0.85
        }

        // 3. Recency anti-fatigue vs. rediscovery boost
        val lastPlayed = track.lastPlayedAt
        if (lastPlayed > 0L && nowMs > lastPlayed) {
            val ageMs = nowMs - lastPlayed
            when {
                ageMs < THREE_HOURS_MS -> score -= 1.45
                ageMs in TWO_DAYS_MS..FOURTEEN_DAYS_MS && (track.isFavorite || plays >= 2) -> score += 1.65
                ageMs > FOURTEEN_DAYS_MS && track.isFavorite -> score += 1.10
            }
        }

        // 4. Fresh arrival lift (added within last 7 days)
        if (track.dateAddedSec > 0L && nowSec - track.dateAddedSec in 0L..SEVEN_DAYS_SEC) {
            score += 1.25
        }

        // 5. Studio quality subtle polish
        when {
            track.isHiRes -> score += 0.35
            track.isLossless -> score += 0.18
        }

        // 6. Controlled dynamic session + 30-min time-bucket jitter in [-1.85, +1.85]
        score += deterministicUnitJitter(track.id, sessionSeed, timeBucket) * 1.85

        return score
    }

    private fun normalizeArtistKey(artist: String): String {
        return artist.trim().lowercase().ifBlank { "unknown" }
    }

    private fun deterministicUnitJitter(trackId: String, seed: Long, timeBucket: Long): Double {
        var h = seed xor (timeBucket * -7046029254386353131L)
        for (ch in trackId) {
            h = (h * 31L) + ch.code.toLong()
        }
        // SplitMix64 avalanche for clean uniform distribution in [-1.0, +1.0]
        h = (h xor (h ushr 30)) * -4658895280553007687L
        h = (h xor (h ushr 27)) * -7723592293110705685L
        h = h xor (h ushr 31)
        val normalized = ((h ushr 11).toDouble() / (1L shl 53).toDouble()) // [0.0, 1.0)
        return normalized * 2.0 - 1.0
    }
}

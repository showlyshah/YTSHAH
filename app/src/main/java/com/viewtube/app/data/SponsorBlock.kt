package com.viewtube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Skips sponsor reads, self-promotion and similar segments *inside* videos, using the
 * community SponsorBlock database. Only the first 4 characters of a hash of the video id
 * are sent, so the server never learns which video you are watching.
 */
class SponsorBlock(private val http: OkHttpClient) {

    data class Segment(val startMs: Long, val endMs: Long, val category: String)

    suspend fun segments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(videoId.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(4)
            val cats = URLEncoder.encode(
                """["sponsor","selfpromo","interaction","intro","outro","preview","music_offtopic"]""",
                "UTF-8"
            )
            val actions = URLEncoder.encode("""["skip"]""", "UTF-8")
            val req = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments/$hash?categories=$cats&actionTypes=$actions")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Segment>()
                val arr = JSONArray(resp.body?.string().orEmpty())
                val out = mutableListOf<Segment>()
                for (i in 0 until arr.length()) {
                    val video = arr.getJSONObject(i)
                    if (video.optString("videoID") != videoId) continue
                    val segs = video.getJSONArray("segments")
                    for (j in 0 until segs.length()) {
                        val s = segs.getJSONObject(j)
                        val times = s.getJSONArray("segment")
                        out += Segment(
                            (times.getDouble(0) * 1000).toLong(),
                            (times.getDouble(1) * 1000).toLong(),
                            s.optString("category")
                        )
                    }
                }
                out.sortedBy { it.startMs }
            }
        }.getOrDefault(emptyList())
    }
}

package eu.kanade.tachiyomi.data.cloud.model

import androidx.annotation.Keep
import java.security.MessageDigest

/**
 * Utility for generating deterministic, safe Firestore document IDs for manga.
 * A manga across devices is uniquely identified by (source, url).
 */
object CloudMangaId {
    fun create(source: Long, url: String): String {
        val input = "${source}_$url"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Cloud representation of a Manga entity stored in Firestore under:
 * `users/{userId}/manga_library/{cloudMangaId}`
 */
@Keep
data class CloudManga(
    val id: String = "",
    val source: Long = 0L,
    val url: String = "",
    val title: String = "",
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0L,
    val thumbnailUrl: String? = null,
    val updateStrategy: String = "ALWAYS_UPDATE",
    val initialized: Boolean = false,
    val favoriteAt: Long? = null,
    val viewerFlags: Long = 0L,
    val chapterFlags: Long = 0L,
    val lastUpdate: Long = 0L,
    val nextUpdate: Long = 0L,
    val notes: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val version: Long = 1L,
)

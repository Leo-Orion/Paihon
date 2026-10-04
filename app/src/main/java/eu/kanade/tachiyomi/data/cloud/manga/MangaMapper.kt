package eu.kanade.tachiyomi.data.cloud.manga

import eu.kanade.tachiyomi.data.cloud.model.CloudManga
import eu.kanade.tachiyomi.data.cloud.model.CloudMangaId
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate

object MangaMapper {

    /**
     * Maps a domain [Manga] to its Firestore representation [CloudManga].
     */
    fun toCloud(manga: Manga): CloudManga {
        val cloudId = CloudMangaId.create(manga.source, manga.url)
        return CloudManga(
            id = cloudId,
            source = manga.source,
            url = manga.url,
            title = manga.title,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.genre,
            status = manga.status,
            thumbnailUrl = manga.thumbnailUrl,
            updateStrategy = manga.updateStrategy.name,
            initialized = manga.initialized,
            favoriteAt = manga.favoriteAt,
            viewerFlags = manga.viewerFlags,
            chapterFlags = manga.chapterFlags,
            lastUpdate = manga.lastUpdate,
            nextUpdate = manga.nextUpdate,
            notes = manga.notes,
            lastModified = System.currentTimeMillis(),
            version = 1L,
        )
    }

    /**
     * Maps a [LibraryManga] to its Firestore representation [CloudManga].
     */
    fun toCloud(libraryManga: LibraryManga): CloudManga {
        return toCloud(libraryManga.manga)
    }

    /**
     * Maps a [CloudManga] to a domain [Manga] for insertion or in-memory comparison.
     */
    fun toDomain(cloud: CloudManga, localId: Long = -1L): Manga {
        val strategy = runCatching { UpdateStrategy.valueOf(cloud.updateStrategy) }
            .getOrDefault(UpdateStrategy.ALWAYS_UPDATE)

        return Manga(
            id = localId,
            source = cloud.source,
            lastUpdate = cloud.lastUpdate,
            nextUpdate = cloud.nextUpdate,
            fetchInterval = 0,
            favoriteAt = cloud.favoriteAt,
            viewerFlags = cloud.viewerFlags,
            chapterFlags = cloud.chapterFlags,
            coverLastModified = 0L,
            url = cloud.url,
            title = cloud.title,
            artist = cloud.artist,
            author = cloud.author,
            description = cloud.description,
            genre = cloud.genre,
            status = cloud.status,
            thumbnailUrl = cloud.thumbnailUrl,
            updateStrategy = strategy,
            initialized = cloud.initialized,
            notes = cloud.notes,
            memo = JsonObject.EMPTY,
        )
    }

    /**
     * Maps a [CloudManga] to a [MangaUpdate] to update flags and favorite state for an existing local manga.
     */
    fun toDomainUpdate(cloud: CloudManga, localId: Long): MangaUpdate {
        return MangaUpdate(localId) {
            favoriteAt = cloud.favoriteAt
            viewerFlags = cloud.viewerFlags
            chapterFlags = cloud.chapterFlags
            if (cloud.notes.isNotEmpty()) {
                notes = cloud.notes
            }
        }
    }

    /**
     * Maps a [CloudManga] to a [MangaRemoteUpdate] to update metadata of an existing local manga.
     */
    fun toRemoteUpdate(cloud: CloudManga, localId: Long): MangaRemoteUpdate {
        val strategy = runCatching { UpdateStrategy.valueOf(cloud.updateStrategy) }
            .getOrDefault(UpdateStrategy.ALWAYS_UPDATE)

        return MangaRemoteUpdate(
            id = localId,
            title = cloud.title,
            author = cloud.author,
            artist = cloud.artist,
            description = cloud.description,
            genre = cloud.genre,
            status = cloud.status,
            thumbnailUrl = cloud.thumbnailUrl,
            updateStrategy = strategy,
            memo = JsonObject.EMPTY,
            initialized = cloud.initialized,
            coverLastModified = null,
        )
    }
}

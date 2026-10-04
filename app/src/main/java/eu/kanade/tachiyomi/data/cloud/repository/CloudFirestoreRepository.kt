package eu.kanade.tachiyomi.data.cloud.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.auth.FirebaseAuthManager
import eu.kanade.tachiyomi.data.cloud.model.CloudManga
import kotlinx.coroutines.tasks.await
import logcat.LogPriority
import logcat.logcat

/**
 * Repository providing secure, user-scoped Firestore operations for Paihon Cloud.
 *
 * All cloud data operations are strictly routed under:
 * `users/{userId}/...`
 *
 * Firestore security rules enforce:
 * match /users/{userId}/{document=**} {
 *     allow read, write: if request.auth != null && request.auth.uid == userId;
 * }
 */
@SingleIn(AppScope::class)
@Inject
class CloudFirestoreRepository(
    private val authManager: FirebaseAuthManager,
) {
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * Returns the current authenticated Firebase user UID, or throws [IllegalStateException]
     * if the user is signed out.
     */
    val currentUserId: String
        get() = authManager.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in to perform Paihon Cloud operations")

    /**
     * Checks if a user is currently signed in.
     */
    val isUserSignedIn: Boolean
        get() = authManager.isSignedIn

    /**
     * Root document path for the current authenticated user: `users/{userId}`.
     */
    private fun userDocument(userId: String = currentUserId) =
        firestore.collection("users").document(userId)

    /**
     * Collection reference for the user's manga library: `users/{userId}/manga_library`
     */
    private fun mangaLibraryCollection(userId: String = currentUserId) =
        userDocument(userId).collection("manga_library")

    /**
     * Non-destructive verification method to test Firestore connectivity and authentication.
     * Writes/reads a lightweight ping document in `users/{userId}/_ping/status`.
     *
     * @return Result containing true if successful, or the encountered exception.
     */
    suspend fun verifyConnectivity(): Result<Boolean> = runCatching {
        val uid = currentUserId
        val pingDoc = userDocument(uid).collection("_ping").document("status")
        val pingData = mapOf(
            "lastVerifiedAt" to System.currentTimeMillis(),
            "client" to "Paihon-Android",
        )
        pingDoc.set(pingData, SetOptions.merge()).await()
        val snapshot = pingDoc.get().await()
        snapshot.exists()
    }.onFailure { error ->
        logcat(LogPriority.ERROR) { "Firestore connectivity verification failed: ${error.message}" }
    }

    /**
     * Safely reads a single manga entry from the cloud by its deterministic document ID.
     */
    suspend fun getCloudManga(cloudId: String): Result<CloudManga?> = runCatching {
        val doc = mangaLibraryCollection().document(cloudId).get().await()
        if (doc.exists()) doc.toObject(CloudManga::class.java) else null
    }.onFailure { error ->
        logcat(LogPriority.ERROR) { "Failed to get CloudManga ($cloudId): ${error.message}" }
    }

    /**
     * Gets the total count of manga documents in the user's cloud library.
     */
    suspend fun getCloudLibraryCount(): Result<Int> = runCatching {
        val snapshot = mangaLibraryCollection().get().await()
        snapshot.size()
    }.onFailure { error ->
        logcat(LogPriority.ERROR) { "Failed to get Cloud library count: ${error.message}" }
    }

    /**
     * Uploads a list of [CloudManga] to Firestore under `users/{userId}/manga_library/{cloudMangaId}`.
     *
     * Batches writes in chunks of up to 400 documents (below Firestore's 500 operation limit).
     * Uses [SetOptions.merge] to non-destructively upsert documents without removing fields or existing docs.
     *
     * @param mangaList The list of CloudManga items to upload.
     * @param onProgress Callback invoked as batches are committed with (uploadedCount, totalCount).
     * @return [Result] containing the total count of uploaded manga.
     */
    suspend fun uploadMangaLibrary(
        mangaList: List<CloudManga>,
        onProgress: ((uploaded: Int, total: Int) -> Unit)? = null,
    ): Result<Int> = runCatching {
        if (!isUserSignedIn) {
            throw IllegalStateException("User must be signed in to upload library")
        }
        if (mangaList.isEmpty()) {
            return@runCatching 0
        }

        val uid = currentUserId
        val collection = mangaLibraryCollection(uid)
        val chunkSize = 400
        var uploadedCount = 0

        val chunks = mangaList.chunked(chunkSize)
        for (chunk in chunks) {
            val batch = firestore.batch()
            for (manga in chunk) {
                val docRef = collection.document(manga.id)
                batch.set(docRef, manga, SetOptions.merge())
            }
            batch.commit().await()
            uploadedCount += chunk.size
            onProgress?.invoke(uploadedCount, mangaList.size)
        }

        uploadedCount
    }.onFailure { error ->
        logcat(LogPriority.ERROR) { "Failed to upload manga library to Firestore: ${error.message}" }
    }
}


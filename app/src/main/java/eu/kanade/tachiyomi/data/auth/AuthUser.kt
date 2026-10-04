package eu.kanade.tachiyomi.data.auth

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

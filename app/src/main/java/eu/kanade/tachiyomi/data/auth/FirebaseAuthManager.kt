package eu.kanade.tachiyomi.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

sealed interface SignInResult {
    data class Success(val user: AuthUser) : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String?) : SignInResult
}

@Inject
@SingleIn(AppScope::class)
class FirebaseAuthManager(
    private val context: Context,
) {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    val authState: StateFlow<AuthUser?> = callbackFlow {
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(authListener)
        trySend(firebaseAuth.currentUser?.toAuthUser())
        awaitClose {
            firebaseAuth.removeAuthStateListener(authListener)
        }
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        started = SharingStarted.Eagerly,
        initialValue = firebaseAuth.currentUser?.toAuthUser(),
    )

    val currentUser: AuthUser?
        get() = firebaseAuth.currentUser?.toAuthUser()

    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request,
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val user = authResult.user?.toAuthUser()
                if (user != null) {
                    SignInResult.Success(user)
                } else {
                    SignInResult.Failure("Failed to obtain authenticated user.")
                }
            } else {
                SignInResult.Failure("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            logcat(LogPriority.INFO) { "Google Sign-In was cancelled by user" }
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            logcat(LogPriority.ERROR, e) { "Google credential request failed: ${e.message}" }
            SignInResult.Failure(e.localizedMessage ?: e.message)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Firebase authentication failed: ${e.message}" }
            SignInResult.Failure(e.localizedMessage ?: e.message)
        }
    }

    suspend fun signOut(): Boolean {
        return try {
            firebaseAuth.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to clear credential manager state: ${e.message}" }
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to sign out: ${e.message}" }
            false
        }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl?.toString(),
        )
    }
}

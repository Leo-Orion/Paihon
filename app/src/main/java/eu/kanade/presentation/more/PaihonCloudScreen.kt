package eu.kanade.presentation.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.auth.FirebaseAuthManager
import eu.kanade.tachiyomi.data.auth.SignInResult
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Sync
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import java.io.File
import java.io.FileOutputStream

object PaihonCloudScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val authManager = remember { context.appGraph.firebaseAuthManager }
        val cloudRepository = remember { context.appGraph.cloudFirestoreRepository }
        val getLibraryManga = remember { context.appGraph.getLibraryManga }
        val uiPreferences = remember { context.appGraph.uiPreferences }

        val authUser by authManager.authState.collectAsState()
        var avatarUri by remember { mutableStateOf(uiPreferences.profileAvatarUri.get()) }
        var selectedImageUriForCrop by remember { mutableStateOf<Uri?>(null) }
        var showSignOutDialog by remember { mutableStateOf(false) }

        var isUploading by remember { mutableStateOf(false) }
        var uploadProgressText by remember { mutableStateOf<String?>(null) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                selectedImageUriForCrop = uri
            }
        }

        if (selectedImageUriForCrop != null) {
            AvatarCropDialog(
                imageUri = selectedImageUriForCrop!!,
                onDismissRequest = { selectedImageUriForCrop = null },
                onCropConfirmed = { croppedFile ->
                    selectedImageUriForCrop = null
                    try {
                        val avatarFile = File(context.filesDir, "custom_profile_avatar.png")
                        if (croppedFile.absolutePath != avatarFile.absolutePath) {
                            croppedFile.inputStream().use { input ->
                                FileOutputStream(avatarFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        uiPreferences.profileAvatarUri.set(avatarFile.absolutePath)
                        avatarUri = avatarFile.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
            )
        }

        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                title = { Text(text = stringResource(MR.strings.label_paihon_cloud)) },
                text = { Text(text = stringResource(MR.strings.cloud_account_sign_out_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSignOutDialog = false
                            scope.launch {
                                authManager.signOut()
                                context.toast(MR.strings.cloud_account_sign_out_success)
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_paihon_cloud),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Profile Avatar Container
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable {
                            imagePickerLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUri.isNotBlank()) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = "Profile Avatar",
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Person,
                            contentDescription = "Profile Avatar Placeholder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(MR.strings.label_paihon_cloud),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Google Cloud Sync",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 24.dp)
                        .secondaryItemAlpha(),
                    textAlign = TextAlign.Center,
                )

                val user = authUser
                if (user == null) {
                    Text(
                        text = stringResource(MR.strings.user_not_signed_in),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 20.dp),
                        textAlign = TextAlign.Center,
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                when (val result = authManager.signInWithGoogle(context)) {
                                    is SignInResult.Success -> {
                                        context.toast(MR.strings.cloud_account_sign_in_success)
                                    }
                                    is SignInResult.Cancelled -> {
                                        context.toast(MR.strings.cloud_account_sign_in_cancelled)
                                    }
                                    is SignInResult.Failure -> {
                                        val msg = result.message ?: "Unknown error"
                                        context.toast(context.stringResource(MR.strings.cloud_account_sign_in_error, msg))
                                    }
                                }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.cloud_account_sign_in_google))
                    }
                } else {
                    // Upload / Download Library Buttons
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(top = 8.dp),
                        enabled = !isUploading,
                        onClick = {
                            if (!authManager.isSignedIn) {
                                context.toast(MR.strings.cloud_upload_sign_in_required)
                                return@Button
                            }
                            scope.launch {
                                isUploading = true
                                uploadProgressText = null
                                try {
                                    val localLibrary = getLibraryManga.await()
                                    if (localLibrary.isEmpty()) {
                                        context.toast(MR.strings.cloud_upload_library_empty)
                                        isUploading = false
                                        return@launch
                                    }

                                    val cloudMangaList = localLibrary.map {
                                        eu.kanade.tachiyomi.data.cloud.manga.MangaMapper.toCloud(it)
                                    }

                                    val result = cloudRepository.uploadMangaLibrary(
                                        mangaList = cloudMangaList,
                                        onProgress = { uploaded, total ->
                                            uploadProgressText = context.stringResource(
                                                MR.strings.cloud_uploading_library,
                                                uploaded,
                                                total,
                                            )
                                        },
                                    )

                                    if (result.isSuccess) {
                                        val count = result.getOrNull() ?: cloudMangaList.size
                                        context.toast(context.stringResource(MR.strings.cloud_upload_success, count))
                                    } else {
                                        val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                                        context.toast(context.stringResource(MR.strings.cloud_upload_error, errorMsg))
                                    }
                                } catch (e: Exception) {
                                    val errorMsg = e.localizedMessage ?: "Unknown error"
                                    context.toast(context.stringResource(MR.strings.cloud_upload_error, errorMsg))
                                } finally {
                                    isUploading = false
                                    uploadProgressText = null
                                }
                            }
                        },
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(text = stringResource(MR.strings.action_upload_library))
                    }

                    if (isUploading && uploadProgressText != null) {
                        Text(
                            text = uploadProgressText!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .secondaryItemAlpha(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(top = 10.dp),
                        enabled = !isUploading,
                        onClick = {
                            context.toast(MR.strings.cloud_download_not_implemented)
                        },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(text = stringResource(MR.strings.action_download_library))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Signed in as",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.secondaryItemAlpha(),
                    )

                    Text(
                        text = user.displayName?.takeIf { it.isNotBlank() } ?: "User",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (!user.email.isNullOrBlank()) {
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.secondaryItemAlpha(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Button(
                        modifier = Modifier.padding(top = 24.dp),
                        onClick = { showSignOutDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
                }
            }
        }
    }
}

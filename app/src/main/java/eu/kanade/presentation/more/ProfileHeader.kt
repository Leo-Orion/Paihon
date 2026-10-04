package eu.kanade.presentation.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Person
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Composable
fun ProfileHeader(
    avatarUri: String?,
    onAvatarSelected: (Uri) -> Unit,
    isSignedIn: Boolean,
    userName: String?,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedImageUriForCrop by remember { mutableStateOf<Uri?>(null) }

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
                onAvatarSelected(Uri.fromFile(croppedFile))
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .padding(top = 28.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Circular profile avatar container
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable {
                    imagePickerLauncher.launch("image/*")
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUri.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Profile Avatar",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Person,
                    contentDescription = "Profile Avatar Placeholder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSignedIn) {
            Text(
                text = stringResource(MR.strings.user_signed_in),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (!userName.isNullOrBlank()) userName else "User",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = stringResource(MR.strings.user_not_signed_in),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(MR.strings.cloud_account_sign_in_summary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.secondaryItemAlpha(),
                textAlign = TextAlign.Center,
            )
        }
    }

    HorizontalDivider()
}

@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismissRequest: () -> Unit,
    onCropConfirmed: (File) -> Unit,
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    loadedBitmap = BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(MR.strings.crop_avatar_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(MR.strings.crop_avatar_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (loadedBitmap != null) {
                    val bitmap = loadedBitmap!!
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    var containerWidthPx by remember { mutableFloatStateOf(0f) }
                    var containerHeightPx by remember { mutableFloatStateOf(0f) }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        containerWidthPx = constraints.maxWidth.toFloat()
                        containerHeightPx = constraints.maxHeight.toFloat()

                        val cropCircleRadius = (minOf(containerWidthPx, containerHeightPx) * 0.42f)
                        val cropCircleCenter = Offset(containerWidthPx / 2f, containerHeightPx / 2f)

                        val baseScale = maxOf(
                            (cropCircleRadius * 2f) / bitmap.width.toFloat(),
                            (cropCircleRadius * 2f) / bitmap.height.toFloat(),
                        )

                        val effectiveScale = baseScale * scale
                        val renderedWidth = bitmap.width * effectiveScale
                        val renderedHeight = bitmap.height * effectiveScale

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.8f, 5.0f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                val imageBitmap = bitmap.asImageBitmap()

                                val topLeft = Offset(
                                    x = (size.width - renderedWidth) / 2f + offset.x,
                                    y = (size.height - renderedHeight) / 2f + offset.y,
                                )

                                drawImage(
                                    image = imageBitmap,
                                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                                    dstSize = IntSize(renderedWidth.roundToInt(), renderedHeight.roundToInt()),
                                )

                                // Dim overlay outside crop circle
                                val overlayPath = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    addOval(
                                        Rect(
                                            center = cropCircleCenter,
                                            radius = cropCircleRadius,
                                        ),
                                    )
                                }
                                drawPath(
                                    path = overlayPath,
                                    color = Color(0xCC000000),
                                )

                                // Circle guide border
                                drawCircle(
                                    color = Color.White,
                                    radius = cropCircleRadius,
                                    center = cropCircleCenter,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Zoom",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.8f..5.0f,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                        ) {
                            Text(stringResource(MR.strings.action_cancel))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val cropCircleRadius = (minOf(containerWidthPx, containerHeightPx) * 0.42f)
                                val baseScale = maxOf(
                                    (cropCircleRadius * 2f) / bitmap.width.toFloat(),
                                    (cropCircleRadius * 2f) / bitmap.height.toFloat(),
                                )
                                val effectiveScale = baseScale * scale
                                val renderedWidth = bitmap.width * effectiveScale
                                val renderedHeight = bitmap.height * effectiveScale

                                val topLeftX = (containerWidthPx - renderedWidth) / 2f + offset.x
                                val topLeftY = (containerHeightPx - renderedHeight) / 2f + offset.y

                                val cropCenter = Offset(containerWidthPx / 2f, containerHeightPx / 2f)
                                val cropWindowLeft = cropCenter.x - cropCircleRadius
                                val cropWindowTop = cropCenter.y - cropCircleRadius
                                val cropWindowSize = cropCircleRadius * 2f

                                val srcX = ((cropWindowLeft - topLeftX) / effectiveScale).coerceIn(0f, bitmap.width.toFloat())
                                val srcY = ((cropWindowTop - topLeftY) / effectiveScale).coerceIn(0f, bitmap.height.toFloat())
                                val srcDim = (cropWindowSize / effectiveScale).coerceAtLeast(1f)

                                val availableWidth = (bitmap.width - srcX).coerceAtLeast(1f)
                                val availableHeight = (bitmap.height - srcY).coerceAtLeast(1f)
                                val finalSrcDim = minOf(srcDim, availableWidth, availableHeight)

                                try {
                                    val croppedSourceBitmap = Bitmap.createBitmap(
                                        bitmap,
                                        srcX.roundToInt(),
                                        srcY.roundToInt(),
                                        finalSrcDim.roundToInt(),
                                        finalSrcDim.roundToInt(),
                                    )

                                    val outputSize = 512
                                    val squareBitmap = Bitmap.createScaledBitmap(
                                        croppedSourceBitmap,
                                        outputSize,
                                        outputSize,
                                        true,
                                    )

                                    val outputFile = File(context.filesDir, "custom_profile_avatar.png")
                                    FileOutputStream(outputFile).use { out ->
                                        squareBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }

                                    onCropConfirmed(outputFile)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    onDismissRequest()
                                }
                            },
                        ) {
                            Text(stringResource(MR.strings.action_ok))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

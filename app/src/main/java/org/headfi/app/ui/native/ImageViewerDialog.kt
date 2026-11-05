package org.headfi.app.ui.native

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.headfi.app.util.DownloadAndShareHelper

/**
 * Full-screen post image viewer. Native posts used to hand images straight to
 * the browser, which lost the authenticated session and any chance to save or
 * share without leaving the app.
 */
@Composable
fun ImageViewerDialog(
    url: String,
    onDismiss: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = nextScale
        if (nextScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Post image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .transformable(transformableState)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { DownloadAndShareHelper.downloadFileOrImage(context, url) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Save image",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        DownloadAndShareHelper.sharePage(context, "Head-Fi image", url)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share image link",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { onOpenExternal(url) }) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = "Open image in browser",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close image",
                        tint = Color.White
                    )
                }
            }

            Text(
                text = "Pinch to zoom",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

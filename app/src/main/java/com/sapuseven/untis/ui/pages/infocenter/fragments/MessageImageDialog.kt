package com.sapuseven.untis.ui.pages.infocenter.fragments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.sapuseven.untis.R
import java.io.File

/**
 * Full screen viewer for an image attachment, see [MessageAttachments].
 *
 * The image can be zoomed and panned with the usual two finger gestures, a tap resets the zoom
 * and closes the viewer once the image is not zoomed anymore.
 */
@Composable
fun MessageImageDialog(
	file: File,
	name: String,
	onDismiss: () -> Unit,
) {
	var scale by remember { mutableFloatStateOf(1f) }
	var offsetX by remember { mutableFloatStateOf(0f) }
	var offsetY by remember { mutableFloatStateOf(0f) }

	fun reset() {
		scale = 1f
		offsetX = 0f
		offsetY = 0f
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false)
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.95f))
				.pointerInput(Unit) {
					detectTapGestures(
						onTap = {
							if (scale > 1f) reset() else onDismiss()
						}
					)
				}
				.pointerInput(Unit) {
					detectTransformGestures { _, pan, zoom, _ ->
						scale = (scale * zoom).coerceIn(1f, 8f)
						if (scale > 1f) {
							offsetX += pan.x
							offsetY += pan.y
						} else {
							offsetX = 0f
							offsetY = 0f
						}
					}
				}
		) {
			val painter = rememberAsyncImagePainter(model = file, contentScale = ContentScale.Fit)

			Image(
				painter = painter,
				contentDescription = name,
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						scaleX = scale
						scaleY = scale
						translationX = offsetX
						translationY = offsetY
					}
			)

			IconButton(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(8.dp),
				onClick = onDismiss
			) {
				Icon(
					painter = painterResource(id = R.drawable.all_close),
					contentDescription = stringResource(id = R.string.all_close),
					tint = MaterialTheme.colorScheme.onPrimary
				)
			}
		}
	}
}

package com.sapuseven.untis.ui.pages.infocenter.fragments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.sapuseven.untis.R
import com.sapuseven.untis.helpers.attachmentName
import com.sapuseven.untis.helpers.shareAttachment
import com.sapuseven.untis.model.rest.MessageAttachment
import com.sapuseven.untis.ui.common.HorizontalPagerIndicator
import com.sapuseven.untis.ui.pages.infocenter.AttachmentUiState
import java.io.File

/**
 * Full screen viewer for the image attachments of a message, see [MessageAttachments].
 *
 * Swiping left or right shows the previous or next image. The current image can be zoomed and
 * panned with the usual two finger gestures, a tap resets the zoom and closes the viewer once the
 * image is not zoomed anymore. The image can be shared through the Android share sheet.
 */
@Composable
fun MessageImageDialog(
	images: List<MessageAttachment>,
	initialIndex: Int,
	states: Map<String, AttachmentUiState>,
	keyOf: (MessageAttachment) -> String,
	onLoad: (MessageAttachment) -> Unit,
	onRetry: (MessageAttachment) -> Unit,
	onDismiss: () -> Unit,
) {
	if (images.isEmpty()) return

	val pagerState = rememberPagerState(
		initialPage = initialIndex.coerceIn(0, images.lastIndex),
		pageCount = { images.size }
	)
	val context = LocalContext.current

	// The pager must not scroll while the current image is zoomed, otherwise panning the image
	// would switch to the next one.
	var zoomed by remember { mutableStateOf(false) }
	LaunchedEffect(pagerState.currentPage) {
		zoomed = false
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false)
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.95f))
		) {
			HorizontalPager(
				state = pagerState,
				modifier = Modifier.fillMaxSize(),
				userScrollEnabled = !zoomed
			) { page ->
				val attachment = images[page]
				val attachmentState = states[keyOf(attachment)]

				LaunchedEffect(attachment) {
					onLoad(attachment)
				}

				MessageImagePage(
					attachment = attachment,
					state = attachmentState,
					isCurrentPage = page == pagerState.currentPage,
					onZoomChanged = { if (page == pagerState.currentPage) zoomed = it },
					onRetry = { onRetry(attachment) },
					onDismiss = onDismiss
				)
			}

			val currentImage = images.getOrNull(pagerState.currentPage)
			val currentState = currentImage?.let { states[keyOf(it)] }

			Row(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(8.dp)
			) {
				if (currentState is AttachmentUiState.Ready) {
					IconButton(onClick = {
						shareAttachment(context, currentState.file)
					}) {
						Icon(
							painter = painterResource(id = R.drawable.all_share),
							contentDescription = stringResource(
								id = R.string.infocenter_messages_attachment_share
							),
							tint = Color.White
						)
					}
				}

				IconButton(onClick = onDismiss) {
					Icon(
						painter = painterResource(id = R.drawable.all_close),
						contentDescription = stringResource(id = R.string.all_close),
						tint = Color.White
					)
				}
			}

			if (images.size > 1) {
				Text(
					text = "${pagerState.currentPage + 1} / ${images.size}",
					style = MaterialTheme.typography.bodyMedium,
					color = Color.White,
					modifier = Modifier
						.align(Alignment.TopCenter)
						.padding(top = 20.dp)
				)

				HorizontalPagerIndicator(
					pagerState = pagerState,
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(bottom = 24.dp),
					activeColor = Color.White,
					inactiveColor = Color.White.copy(alpha = 0.38f)
				)
			}
		}
	}
}

@Composable
private fun MessageImagePage(
	attachment: MessageAttachment,
	state: AttachmentUiState?,
	isCurrentPage: Boolean,
	onZoomChanged: (Boolean) -> Unit,
	onRetry: () -> Unit,
	onDismiss: () -> Unit,
) {
	when (state) {
		is AttachmentUiState.Ready -> ZoomableImage(
			file = state.file,
			contentDescription = attachment.attachmentName(),
			isCurrentPage = isCurrentPage,
			onZoomChanged = onZoomChanged,
			onDismiss = onDismiss
		)

		is AttachmentUiState.Failed -> Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(32.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = stringResource(id = R.string.infocenter_messages_attachment_image_failed),
				style = MaterialTheme.typography.bodyMedium,
				color = Color.White,
				textAlign = TextAlign.Center
			)
			if (!state.reason.isNullOrBlank()) {
				Text(
					text = state.reason,
					style = MaterialTheme.typography.bodySmall,
					color = Color.White.copy(alpha = 0.7f),
					textAlign = TextAlign.Center
				)
			}
			TextButton(onClick = onRetry) {
				Text(
					text = stringResource(id = R.string.infocenter_messages_attachment_image_retry),
					color = Color.White
				)
			}
		}

		else -> Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			CircularProgressIndicator(color = Color.White)
		}
	}
}

@Composable
private fun ZoomableImage(
	file: File,
	contentDescription: String,
	isCurrentPage: Boolean,
	onZoomChanged: (Boolean) -> Unit,
	onDismiss: () -> Unit,
) {
	var scale by remember(file) { mutableFloatStateOf(1f) }
	var offsetX by remember(file) { mutableFloatStateOf(0f) }
	var offsetY by remember(file) { mutableFloatStateOf(0f) }

	fun reset() {
		scale = 1f
		offsetX = 0f
		offsetY = 0f
	}

	LaunchedEffect(isCurrentPage) {
		if (!isCurrentPage) {
			reset()
			onZoomChanged(false)
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.pointerInput(file) {
				detectTapGestures(
					onTap = {
						if (scale > 1f) {
							reset()
							onZoomChanged(false)
						} else {
							onDismiss()
						}
					}
				)
			}
			.pointerInput(file) {
				awaitEachGesture {
					var event: PointerEvent
					do {
						event = awaitPointerEvent()
						val zoom = event.calculateZoom()
						val pan = event.calculatePan()

						when {
							zoom != 1f -> {
								scale = (scale * zoom).coerceIn(1f, 8f)
								if (scale > 1f) {
									offsetX += pan.x
									offsetY += pan.y
								} else {
									offsetX = 0f
									offsetY = 0f
								}
								onZoomChanged(scale > 1f)
								event.changes.forEach { change ->
									if (change.pressed) change.consume()
								}
							}

							// A single finger pan only belongs to the image while it is zoomed,
							// otherwise it must stay available to the pager.
							scale > 1f && pan != Offset.Zero -> {
								offsetX += pan.x
								offsetY += pan.y
								event.changes.forEach { change ->
									if (change.pressed) change.consume()
								}
							}
						}
					} while (event.changes.any { it.pressed })
				}
			}
	) {
		val painter = rememberAsyncImagePainter(model = file, contentScale = ContentScale.Fit)

		Image(
			painter = painter,
			contentDescription = contentDescription,
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
	}
}

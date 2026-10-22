package com.sapuseven.untis.ui.pages.infocenter.fragments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.sapuseven.untis.R
import com.sapuseven.untis.helpers.attachmentName
import com.sapuseven.untis.helpers.isInlineImage
import com.sapuseven.untis.model.rest.MessageAttachment
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Shows all image attachments of a message inline, below its content.
 *
 * Tapping an image opens [MessageImageDialog] with the full size picture.
 */
@Composable
fun MessageAttachments(
	messageId: Long,
	attachments: List<MessageAttachment>,
	downloadAttachment: (Long, MessageAttachment) -> Flow<Result<File>>,
	onImageClick: (File, String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val images = remember(attachments) { attachments.filter { it.isInlineImage() } }
	if (images.isEmpty()) return

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		images.forEach { attachment ->
			key(attachment.id, attachment.attachmentName()) {
				MessageAttachmentImage(
					messageId = messageId,
					attachment = attachment,
					downloadAttachment = downloadAttachment,
					onImageClick = onImageClick
				)
			}
		}
	}
}

/**
 * A single inline image attachment.
 *
 * The image is downloaded on demand and the state of that download is always visible: a
 * placeholder while it is running and an error block with the reason and a retry action when it
 * failed. Nothing is hidden silently, otherwise a broken download looks like a missing image.
 */
@Composable
private fun MessageAttachmentImage(
	messageId: Long,
	attachment: MessageAttachment,
	downloadAttachment: (Long, MessageAttachment) -> Flow<Result<File>>,
	onImageClick: (File, String) -> Unit,
) {
	var attempt by remember { mutableIntStateOf(0) }

	// The download is memoised by the view model, so the flow only has to be requested again when
	// the user retries. Never key this on the lambda parameter: a method reference is a new
	// object on every recomposition and would restart the download over and over again.
	val download = remember(messageId, attachment, attempt) {
		downloadAttachment(messageId, attachment)
	}
	val result by download.collectAsStateWithLifecycle(initialValue = null)

	val name = attachment.attachmentName()
	val file = result?.getOrNull()

	when {
		file != null -> MessageAttachmentImageContent(
			file = file,
			name = name,
			onClick = { onImageClick(file, name) },
			onRetry = {
				// The cached file cannot be decoded, download it again.
				file.delete()
				attempt++
			}
		)

		result?.isFailure == true -> MessageAttachmentImageError(
			name = name,
			reason = result?.exceptionOrNull()?.message,
			onRetry = { attempt++ }
		)

		else -> MessageAttachmentImagePlaceholder()
	}
}

@Composable
private fun MessageAttachmentImageContent(
	file: File,
	name: String,
	onClick: () -> Unit,
	onRetry: () -> Unit,
) {
	val context = LocalContext.current
	val request = remember(file) {
		ImageRequest.Builder(context)
			.data(file)
			.crossfade(true)
			.build()
	}

	SubcomposeAsyncImage(
		model = request,
		contentDescription = name,
		contentScale = ContentScale.FillWidth,
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.clickable(onClick = onClick),
		loading = {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(4f / 3f)
					.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator()
			}
		},
		success = {
			SubcomposeAsyncImageContent()
		},
		error = {
			MessageAttachmentImageError(name = name, reason = null, onRetry = onRetry)
		}
	)
}

@Composable
private fun MessageAttachmentImagePlaceholder() {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.aspectRatio(4f / 3f)
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
		contentAlignment = Alignment.Center
	) {
		CircularProgressIndicator()
	}
}

/**
 * Shown when an image could not be downloaded or decoded.
 *
 * The reason is displayed on purpose: without it a failed attachment is indistinguishable from a
 * message that simply has no image.
 */
@Composable
private fun MessageAttachmentImageError(
	name: String,
	reason: String?,
	onRetry: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
			.clickable(onClick = onRetry)
			.padding(12.dp),
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		Text(
			text = name,
			style = MaterialTheme.typography.labelLarge,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Text(
			text = stringResource(id = R.string.infocenter_messages_attachment_image_failed),
			style = MaterialTheme.typography.bodySmall
		)
		if (!reason.isNullOrBlank()) {
			Text(
				text = reason,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis
			)
		}
		Text(
			text = stringResource(id = R.string.infocenter_messages_attachment_image_retry),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.primary
		)
	}
}

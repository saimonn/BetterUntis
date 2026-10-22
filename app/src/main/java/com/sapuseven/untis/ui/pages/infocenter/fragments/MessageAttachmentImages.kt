package com.sapuseven.untis.ui.pages.infocenter.fragments

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
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
	val images = attachments.filter { it.isInlineImage() }
	if (images.isEmpty()) return

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		images.forEach { attachment ->
			MessageAttachmentImage(
				messageId = messageId,
				attachment = attachment,
				downloadAttachment = downloadAttachment,
				onImageClick = onImageClick
			)
		}
	}
}

/**
 * A single inline image attachment.
 *
 * The image is downloaded on demand; while the download is running a placeholder is shown.
 * On failure nothing is rendered, the attachment can still be opened from the attachments
 * dialog.
 */
@Composable
private fun MessageAttachmentImage(
	messageId: Long,
	attachment: MessageAttachment,
	downloadAttachment: (Long, MessageAttachment) -> Flow<Result<File>>,
	onImageClick: (File, String) -> Unit,
) {
	val download = remember(downloadAttachment, messageId, attachment) {
		downloadAttachment(messageId, attachment)
	}
	val result by download.collectAsStateWithLifecycle(initialValue = null)

	if (result?.isFailure == true) return

	val file = result?.getOrNull()
	val name = attachment.attachmentName()

	val painter = rememberAsyncImagePainter(model = file, contentScale = ContentScale.Fit)

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
			.clickable(enabled = file != null) {
				file?.let { onImageClick(it, name) }
			},
		contentAlignment = Alignment.Center
	) {
		if (file == null) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(4f / 3f)
			) {
				CircularProgressIndicator(
					modifier = Modifier
						.align(Alignment.Center)
						.padding(24.dp)
				)
			}
		} else {
			Image(
				painter = painter,
				contentDescription = name,
				contentScale = ContentScale.Fit,
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}

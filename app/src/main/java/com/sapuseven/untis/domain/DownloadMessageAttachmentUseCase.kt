package com.sapuseven.untis.domain

import com.sapuseven.untis.data.repository.MessagesRepository
import com.sapuseven.untis.model.rest.MessageAttachment
import java.io.File
import javax.inject.Inject
import javax.inject.Named

interface DownloadMessageAttachmentUseCase {
	suspend operator fun invoke(messageId: Long, attachment: MessageAttachment): File
}

class DownloadMessageAttachmentUseCaseImpl @Inject constructor(
	private val messagesRepository: MessagesRepository,
	@Named("cacheDir") private val cacheDir: File,
) : DownloadMessageAttachmentUseCase {
	override suspend operator fun invoke(messageId: Long, attachment: MessageAttachment): File {
		val destination = File(attachmentCacheDir(cacheDir), fileName(messageId, attachment))
		if (destination.exists() && destination.length() > 0L)
			return destination

		val storageUrl = messagesRepository.attachmentStorageUrl(requireNotNull(attachment.id) {
			"Attachment ${attachment.name} has no id"
		})
		val url = requireNotNull(storageUrl.downloadUrl) {
			"Attachment ${attachment.name} has no download URL"
		}

		messagesRepository.downloadAttachment(
			url = url,
			headers = storageUrl.additionalHeaders.orEmpty()
				.associate { header -> header.key.orEmpty() to header.value.orEmpty() },
			destination = destination,
		)

		return destination
	}

	companion object {
		private const val MAX_FILE_NAME_LENGTH = 100

		fun attachmentCacheDir(cacheDir: File): File = File(cacheDir, "messenger/attachments")

		/**
		 * The file name of an attachment as it is stored in the local cache.
		 *
		 * The prefix is used to avoid collisions between attachments of different messages.
		 */
		fun fileName(messageId: Long, attachment: MessageAttachment): String {
			val name = (attachment.fileName ?: attachment.name).orEmpty()
				.substringAfterLast('/')
				.substringAfterLast('\\')
				.filter { it != ':' && it.code >= 0x20 }
				.take(MAX_FILE_NAME_LENGTH)
				.ifBlank { "attachment" }

			return "${messageId}_${attachment.id}_$name"
		}
	}
}

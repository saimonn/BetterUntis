package com.sapuseven.untis.domain

import android.util.Log
import com.sapuseven.untis.data.cache.AttachmentCache
import com.sapuseven.untis.data.repository.MessagesRepository
import com.sapuseven.untis.model.rest.MessageAttachment
import com.sapuseven.untis.model.rest.MessageAttachmentStorageUrl
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

interface DownloadMessageAttachmentUseCase {
	suspend operator fun invoke(messageId: Long, attachment: MessageAttachment): File
}

/**
 * Downloads an attachment into [AttachmentCache] and returns the cached file.
 *
 * A cached file is returned without any network access, so reopening a message is instant. The
 * signed download URL of an attachment is only valid for a short time and costs an extra round
 * trip, so it is cached as well and refreshed when the server rejects it.
 */
class DownloadMessageAttachmentUseCaseImpl @Inject constructor(
	private val messagesRepository: MessagesRepository,
	private val attachmentCache: AttachmentCache,
) : DownloadMessageAttachmentUseCase {
	private data class CachedStorageUrl(
		val value: MessageAttachmentStorageUrl,
		val fetchedAt: Long,
	)

	private val storageUrlMutex = Mutex()
	private val storageUrls = mutableMapOf<String, CachedStorageUrl>()

	override suspend fun invoke(messageId: Long, attachment: MessageAttachment): File {
		attachmentCache.find(messageId, attachment)?.let { return it }

		val attachmentId = requireNotNull(attachment.id) {
			"Attachment ${attachment.name} has no id"
		}

		val temporaryFile = attachmentCache.temporaryFile(messageId, attachment)
		try {
			download(attachmentId, attachment, temporaryFile)
			return attachmentCache.commit(temporaryFile, messageId, attachment)
		} finally {
			// No-op after a successful commit, removes the partial file otherwise.
			temporaryFile.delete()
		}
	}

	private suspend fun download(
		attachmentId: String,
		attachment: MessageAttachment,
		destination: File,
	) {
		try {
			downloadWithStorageUrl(storageUrl(attachmentId), attachment, destination)
		} catch (e: ClientRequestException) {
			// The signed URL expired (or was never valid for this session): a single retry with a
			// freshly requested one is enough, everything else is a real error.
			if (e.response.status !in TRANSIENT_URL_STATUSES) throw e

			Log.d(TAG, "Refreshing the storage URL of ${attachment.name} after ${e.response.status}")
			downloadWithStorageUrl(storageUrl(attachmentId, refresh = true), attachment, destination)
		}
	}

	private suspend fun downloadWithStorageUrl(
		storageUrl: MessageAttachmentStorageUrl,
		attachment: MessageAttachment,
		destination: File,
	) {
		val url = requireNotNull(storageUrl.downloadUrl) {
			"Attachment ${attachment.name} has no download URL"
		}

		messagesRepository.downloadAttachment(
			url = url,
			headers = storageUrl.additionalHeaders.orEmpty()
				.associate { header -> header.key.orEmpty() to header.value.orEmpty() },
			destination = destination,
		)
	}

	private suspend fun storageUrl(
		attachmentId: String,
		refresh: Boolean = false,
	): MessageAttachmentStorageUrl {
		if (!refresh) {
			storageUrlMutex.withLock {
				val cached = storageUrls[attachmentId]
				if (cached != null && System.currentTimeMillis() - cached.fetchedAt < STORAGE_URL_LIFETIME_MS)
					return cached.value
			}
		}

		val value = messagesRepository.attachmentStorageUrl(attachmentId)
		storageUrlMutex.withLock {
			storageUrls[attachmentId] = CachedStorageUrl(value, System.currentTimeMillis())
		}
		return value
	}

	private companion object {
		private const val TAG = "AttachmentDownload"

		/**
		 * How long a signed attachment download URL is reused. It is refreshed early enough to
		 * avoid downloads failing because of an expired signature.
		 */
		private const val STORAGE_URL_LIFETIME_MS = 5 * 60 * 1000L

		/**
		 * Statuses that mean "this URL is not usable (anymore)", which a fresh URL can fix.
		 */
		private val TRANSIENT_URL_STATUSES = setOf(
			HttpStatusCode.Unauthorized,
			HttpStatusCode.Forbidden,
			HttpStatusCode.NotFound,
			HttpStatusCode.Gone,
		)
	}
}

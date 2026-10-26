package com.sapuseven.untis.domain

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.sapuseven.untis.data.cache.AttachmentCache
import com.sapuseven.untis.data.repository.MessagesRepository
import com.sapuseven.untis.model.rest.MessageAttachment
import com.sapuseven.untis.model.rest.MessageAttachmentStorageUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
	@ApplicationContext private val context: Context,
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
		attachmentCache.find(messageId, attachment)?.let { cached ->
			saveToPublicDownloads(cached, attachment)
			return cached
		}

		val attachmentId = requireNotNull(attachment.id) {
			"Attachment ${attachment.name} has no id"
		}

		val temporaryFile = attachmentCache.temporaryFile(messageId, attachment)
		try {
			download(attachmentId, attachment, temporaryFile)
			val result = attachmentCache.commit(temporaryFile, messageId, attachment)
			saveToPublicDownloads(result, attachment)
			return result
		} finally {
			// No-op after a successful commit, removes the partial file otherwise.
			temporaryFile.delete()
		}
	}

	private suspend fun saveToPublicDownloads(cached: File, attachment: MessageAttachment) =
		withContext(Dispatchers.IO) {
			try {
				val displayName = (attachment.fileName ?: attachment.name)
					?.substringAfterLast('/')
					?.substringAfterLast('\\')
					?.takeIf { it.isNotBlank() }
					?: cached.name.substringAfterLast('_', cached.name)

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					val resolver = context.contentResolver
					val contentValues = ContentValues().apply {
						put(MediaStore.Downloads.DISPLAY_NAME, displayName)
						put(MediaStore.Downloads.MIME_TYPE, mimeType(displayName))
						put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Untis")
						put(MediaStore.Downloads.IS_PENDING, 1)
					}

					val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
						?: return@withContext

					resolver.openOutputStream(uri)?.use { output ->
						FileInputStream(cached).use { input ->
							input.copyTo(output)
						}
					}

					contentValues.clear()
					contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
					resolver.update(uri, contentValues, null, null)
				} else {
					// Android 9 and older need WRITE_EXTERNAL_STORAGE to write into the public
					// Downloads folder and can only be granted it interactively, so fall back to
					// the app-specific external folder (still browsable, removed on uninstall).
					val canWritePublicStorage = ContextCompat.checkSelfPermission(
						context,
						Manifest.permission.WRITE_EXTERNAL_STORAGE
					) == PackageManager.PERMISSION_GRANTED

					val baseDir = if (canWritePublicStorage) {
						Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
					} else {
						context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
							?: return@withContext
					}

					val untisDir = File(baseDir, "Untis")
					if (!untisDir.exists()) {
						untisDir.mkdirs()
					}
					val destination = File(untisDir, displayName)
					if (!destination.exists()) {
						cached.copyTo(destination, overwrite = false)
					}
				}
			} catch (e: Exception) {
				Log.w(TAG, "Failed to save attachment to Downloads/Untis", e)
			}
		}

	private fun mimeType(name: String): String {
		val extension = name.substringAfterLast('.', "").lowercase()
		return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
			?: "application/octet-stream"
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

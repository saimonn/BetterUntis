package com.sapuseven.untis.data.cache

import android.util.Log
import com.sapuseven.untis.model.rest.MessageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * On-disk cache for downloaded message attachments.
 *
 * Attachments are downloaded once and then reused: every message, attachment and inline image
 * first asks the cache, so opening a message again (or coming back to it after a tab switch)
 * never downloads the same file twice. Files are written through a temporary file and renamed
 * on completion, so an interrupted download can never leave a truncated file that would then be
 * served as if it was complete.
 *
 * The cache is bounded: see [MAX_SIZE_BYTES]. The oldest files are removed first.
 */
@Singleton
class AttachmentCache @Inject constructor(@Named("cacheDir") cacheDir: File) {
	private val directory = File(cacheDir, "messenger/attachments")
	private val lock = Mutex()

	/**
	 * The name of the cached file of an attachment.
	 *
	 * The prefix avoids collisions between attachments of different messages.
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

	private fun fileFor(name: String) = File(directory, name)

	/**
	 * The attachment file if it is already cached and complete, `null` otherwise.
	 */
	suspend fun find(messageId: Long, attachment: MessageAttachment): File? =
		withContext(Dispatchers.IO) {
			lock.withLock {
				fileFor(fileName(messageId, attachment))
					.takeIf { it.isFile && it.length() > 0L }
			}
		}

	/**
	 * The file a download is written to before [commit] renames it.
	 */
	suspend fun temporaryFile(messageId: Long, attachment: MessageAttachment): File =
		withContext(Dispatchers.IO) {
			lock.withLock {
				directory.mkdirs()
				fileFor("${fileName(messageId, attachment)}.part")
			}
		}

	/**
	 * Makes a completed download available in the cache and enforces the size limit.
	 */
	suspend fun commit(
		temporaryFile: File,
		messageId: Long,
		attachment: MessageAttachment
	): File = withContext(Dispatchers.IO) {
		lock.withLock {
			val destination = fileFor(fileName(messageId, attachment))
			if (destination.exists())
				destination.delete()

			check(temporaryFile.renameTo(destination)) {
				"Could not move the downloaded attachment ${destination.name} into the cache"
			}

			prune()
			destination
		}
	}

	suspend fun delete(messageId: Long, attachment: MessageAttachment) =
		withContext(Dispatchers.IO) {
			lock.withLock {
				val destination = fileFor(fileName(messageId, attachment))
				destination.delete()
				fileFor("${destination.name}.part").delete()
			}
		}

	/**
	 * Removes the oldest files until the cache fits into [MAX_SIZE_BYTES].
	 *
	 * Called after every successful download; must be called with [lock] held.
	 */
	private fun prune() {
		val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
		var totalSize = files.sumOf { it.length() }
		if (totalSize <= MAX_SIZE_BYTES) return

		files.sortedBy { it.lastModified() }.forEach { file ->
			if (totalSize <= MAX_SIZE_BYTES) return
			val length = file.length()
			if (file.delete()) {
				totalSize -= length
				Log.d("AttachmentCache", "Pruned ${file.name} ($length bytes)")
			}
		}
	}

	private companion object {
		private const val MAX_FILE_NAME_LENGTH = 100

		/**
		 * Upper bound of the attachment cache. Android may still clear the cache directory at any
		 * time (it is a cache), a missing file is simply downloaded again.
		 */
		private const val MAX_SIZE_BYTES = 64L * 1024 * 1024
	}
}

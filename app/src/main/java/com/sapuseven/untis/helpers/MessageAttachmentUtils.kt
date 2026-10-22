package com.sapuseven.untis.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sapuseven.untis.BuildConfig
import com.sapuseven.untis.R
import com.sapuseven.untis.model.rest.MessageAttachment
import java.io.File

/**
 * Extensions that are rendered inline in a message, see the inline image view.
 */
private val INLINE_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

/**
 * The name an attachment is shown and downloaded with.
 *
 * This matches the file name used in the local cache, see
 * `DownloadMessageAttachmentUseCaseImpl.fileName`.
 */
fun MessageAttachment.attachmentName(): String = fileName ?: name.orEmpty()

/**
 * Whether the attachment is an image that can be shown inline.
 *
 * The Untis API does not provide a reliable content type, so the file name is used.
 */
fun MessageAttachment.isInlineImage(): Boolean =
	attachmentName().substringAfterLast('.', "").lowercase() in INLINE_IMAGE_EXTENSIONS

/**
 * Hands a downloaded attachment over to the app that can display it.
 */
fun openAttachment(context: Context, file: File) {
	val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

	val intent = Intent(Intent.ACTION_VIEW)
		.setDataAndType(uri, mimeType(file))
		.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

	try {
		context.startActivity(intent)
	} catch (e: ActivityNotFoundException) {
		Toast.makeText(
			context,
			context.getString(R.string.infocenter_messages_attachment_no_app),
			Toast.LENGTH_LONG
		).show()
	}
}

private fun mimeType(file: File): String {
	val extension = file.name.substringAfterLast('.', "").lowercase()
	val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
	return mimeType ?: "application/octet-stream"
}

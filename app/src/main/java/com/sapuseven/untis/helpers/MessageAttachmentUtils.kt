package com.sapuseven.untis.helpers

import com.sapuseven.untis.model.rest.MessageAttachment

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

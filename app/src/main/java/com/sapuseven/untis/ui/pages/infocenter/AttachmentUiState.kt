package com.sapuseven.untis.ui.pages.infocenter

import java.io.File

/**
 * State of the download of a single message attachment.
 *
 * The state is kept in [InfoCenterViewModel] (and not in the composable), so that it survives
 * switching between the Info Center tabs and is never left behind as a never ending "loading"
 * spinner: a [Loading] state is always backed by a running download job, every other outcome is
 * either [Ready] or [Failed].
 */
sealed interface AttachmentUiState {
	data object Loading : AttachmentUiState

	data class Ready(val file: File) : AttachmentUiState

	data class Failed(val reason: String?) : AttachmentUiState
}

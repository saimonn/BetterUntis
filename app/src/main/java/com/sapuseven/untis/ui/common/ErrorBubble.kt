package com.sapuseven.untis.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sapuseven.untis.R
import com.sapuseven.untis.api.exception.UntisApiException
import com.sapuseven.untis.api.model.response.UntisErrorCode
import com.sapuseven.untis.domain.ElementNotAvailableException
import com.sapuseven.untis.helpers.ErrorMessageDictionary

/**
 * Turns an exception into something a user can act on.
 *
 * The Untis server answers with technical messages such as `invalid element type: 12` or
 * `no right`. Those are kept as a secondary line for troubleshooting, while the headline explains
 * what is going on, using [unavailableMessage] when the endpoint simply does not serve this
 * account or school (no right, unknown element, …).
 */
@StringRes
fun errorMessageRes(error: Throwable, @StringRes unavailableMessage: Int? = null): Int =
	when {
		error is ElementNotAvailableException ->
			unavailableMessage ?: R.string.errormessagedictionary_generic

		error is UntisApiException -> when (error.error?.code) {
			UntisErrorCode.NO_RIGHT,
			UntisErrorCode.INVALID_ELEMENT_ID,
			UntisErrorCode.INVALID_PERSON_TYPE,
			UntisErrorCode.INVALID_TIME_TABLE_TYPE ->
				unavailableMessage ?: R.string.errormessagedictionary_generic

			else -> ErrorMessageDictionary.getErrorMessageResource(error.error?.code)
				?: R.string.errormessagedictionary_generic
		}

		else -> R.string.errormessagedictionary_generic
	}

/**
 * An error explanation with the raw server message underneath, if there is one.
 */
@Composable
fun ErrorBubble(
	error: Throwable,
	modifier: Modifier = Modifier,
	@StringRes unavailableMessage: Int? = null,
) {
	val details = (error as? UntisApiException)?.error?.message
		?.takeIf { it.isNotBlank() }
		?: error.message?.takeIf { it.isNotBlank() && error !is ElementNotAvailableException }

	MessageBubble(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp),
		icon = {
			Icon(
				painter = painterResource(id = R.drawable.all_error),
				contentDescription = stringResource(id = R.string.all_error)
			)
		},
		messageText = errorMessageRes(error, unavailableMessage),
		messageTextRaw = details
	)
}

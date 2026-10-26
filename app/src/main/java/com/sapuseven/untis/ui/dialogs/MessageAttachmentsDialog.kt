package com.sapuseven.untis.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sapuseven.untis.R
import com.sapuseven.untis.helpers.attachmentName
import com.sapuseven.untis.helpers.openAttachment
import com.sapuseven.untis.helpers.shareAttachment
import com.sapuseven.untis.model.rest.MessageAttachment
import com.sapuseven.untis.ui.pages.infocenter.AttachmentUiState

/**
 * Shows the attachments of a message and opens them when they are tapped.
 *
 * Attachments are downloaded on demand, the file is then handed over to another app using a
 * [FileProvider] content URI. The download itself is done by the view model, so its state
 * survives the dialog being recreated and a download that is already running is not restarted.
 */
private enum class AttachmentAction { Open, Share }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageAttachmentsDialog(
	messageId: Long,
	attachments: List<MessageAttachment>,
	onDismiss: () -> Unit,
	states: Map<String, AttachmentUiState>,
	keyOf: (MessageAttachment) -> String,
	onLoad: (MessageAttachment) -> Unit,
	onRetry: (MessageAttachment) -> Unit,
) {
	var selected by remember { mutableStateOf<MessageAttachment?>(null) }
	var action by remember { mutableStateOf(AttachmentAction.Open) }

	val context = LocalContext.current
	val selectedState = selected?.let { states[keyOf(it)] }

	LaunchedEffect(selected, selectedState, action) {
		val attachment = selected ?: return@LaunchedEffect
		when (selectedState) {
			is AttachmentUiState.Ready -> {
				if (action == AttachmentAction.Share)
					shareAttachment(context, selectedState.file)
				else
					openAttachment(context, selectedState.file)
				selected = null
			}

			is AttachmentUiState.Failed -> {
				Toast.makeText(
					context,
					context.getString(
						R.string.infocenter_messages_attachment_download_failed,
						attachment.attachmentName()
					),
					Toast.LENGTH_LONG
				).show()
				selected = null
			}

			else -> Unit
		}
	}

	BasicAlertDialog(onDismissRequest = onDismiss) {
		Surface(
			modifier = Modifier.padding(vertical = 24.dp),
			shape = AlertDialogDefaults.shape,
			color = AlertDialogDefaults.containerColor,
			tonalElevation = AlertDialogDefaults.TonalElevation
		) {
			Column {
				ProvideTextStyle(value = MaterialTheme.typography.headlineSmall) {
					Box(
						Modifier
							.padding(top = 24.dp, bottom = 16.dp)
							.padding(horizontal = 24.dp)
					) {
						Text(text = stringResource(id = R.string.infocenter_messages_attachments))
					}
				}

				LazyColumn(
					modifier = Modifier.fillMaxWidth()
				) {
					items(attachments) { attachment ->
						ListItem(
							colors = ListItemDefaults.colors(containerColor = Color.Transparent), // No idea why, but ListItem has the wrong background color otherwise
							headlineContent = {
								Text(attachment.attachmentName())
							},
							leadingContent = {
								Icon(
									painter = painterResource(id = R.drawable.infocenter_attachment),
									contentDescription = null
								)
							},
							trailingContent = {
								if (attachment == selected)
									CircularProgressIndicator(modifier = Modifier.size(24.dp))
								else
									IconButton(onClick = {
										action = AttachmentAction.Share
										if (states[keyOf(attachment)] is AttachmentUiState.Failed)
											onRetry(attachment)
										else
											onLoad(attachment)
										selected = attachment
									}) {
										Icon(
											painter = painterResource(id = R.drawable.all_share),
											contentDescription = stringResource(
												id = R.string.infocenter_messages_attachment_share
											)
										)
									}
							},
							modifier = Modifier
								.clickable(enabled = selected == null) {
									action = AttachmentAction.Open
									if (states[keyOf(attachment)] is AttachmentUiState.Failed)
										onRetry(attachment)
									else
										onLoad(attachment)
									selected = attachment
								}
								.padding(horizontal = 8.dp)
						)
					}
				}

				TextButton(
					modifier = Modifier
						.align(Alignment.End)
						.padding(top = 16.dp, bottom = 24.dp)
						.padding(horizontal = 24.dp),
					onClick = onDismiss
				) {
					Text(text = stringResource(id = R.string.all_close))
				}
			}
		}
	}
}

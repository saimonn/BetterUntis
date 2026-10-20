package com.sapuseven.untis.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sapuseven.untis.BuildConfig
import com.sapuseven.untis.R
import com.sapuseven.untis.model.rest.MessageAttachment
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Shows the attachments of a message and opens them when they are tapped.
 *
 * Attachments are downloaded on demand, the file is then handed over to another app using a
 * [FileProvider] content URI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageAttachmentsDialog(
	messageId: Long,
	attachments: List<MessageAttachment>,
	onDismiss: () -> Unit,
	downloadAttachment: (Long, MessageAttachment) -> Flow<Result<File>>,
) {
	var downloadStartedFor by remember { mutableStateOf<MessageAttachment?>(null) }
	var errorFor by remember { mutableStateOf<MessageAttachment?>(null) }

	val context = LocalContext.current

	if (errorFor != null) {
		LaunchedEffect(errorFor) {
			val attachment = errorFor ?: return@LaunchedEffect
			Toast.makeText(
				context,
				context.getString(
					R.string.infocenter_messages_attachment_download_failed,
					attachment.name ?: attachment.fileName.orEmpty()
				),
				Toast.LENGTH_LONG
			).show()
			errorFor = null
		}
	}

	val path = downloadStartedFor?.let { downloadAttachment(messageId, it) }

	if (path != null) {
		val result by path.collectAsStateWithLifecycle(initialValue = null)
		val downloadResult = result

		LaunchedEffect(downloadResult) {
			when {
				downloadResult == null -> Unit
				downloadResult.isSuccess -> {
					openAttachment(context, downloadResult.getOrThrow())
					downloadStartedFor = null
				}

				else -> {
					errorFor = downloadStartedFor
					downloadStartedFor = null
				}
			}
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
								Text(attachment.name ?: attachment.fileName.orEmpty())
							},
							leadingContent = {
								Icon(
									painter = painterResource(id = R.drawable.infocenter_attachment),
									contentDescription = null
								)
							},
							trailingContent = {
								if (attachment == downloadStartedFor)
									CircularProgressIndicator(modifier = Modifier.size(24.dp))
							},
							modifier = Modifier
								.clickable(enabled = downloadStartedFor == null) {
									downloadStartedFor = attachment
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

private fun openAttachment(context: Context, file: File) {
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

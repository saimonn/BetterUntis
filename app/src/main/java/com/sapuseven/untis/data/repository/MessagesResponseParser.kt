package com.sapuseven.untis.data.repository

import android.util.Log
import com.sapuseven.untis.model.rest.Message
import com.sapuseven.untis.model.rest.MessagesResponse
import com.sapuseven.untis.model.rest.Sender
import com.sapuseven.untis.modules.restJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val TAG = "MessagesResponse"

/**
 * Decodes the response of `GET /view/v1/messages`.
 *
 * The message list contains messages in different states (for example messages that request a
 * read confirmation), and the server is free to add fields for one group of them only. Decoding
 * the whole response at once means that a single unexpected message hides every other message:
 * the app showed an empty Info Center even though the account had messages.
 *
 * The messages are therefore decoded one by one. A message that still cannot be decoded is
 * reduced to the fields that the list needs instead of being dropped.
 */
internal fun parseMessagesResponse(raw: String): MessagesResponse {
	val root = runCatching { restJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
		?: return restJson.decodeFromString(MessagesResponse.serializer(), raw)

	return MessagesResponse(
		incomingMessages = root["incomingMessages"].toMessageList(),
		readConfirmationMessages = root["readConfirmationMessages"].toMessageList()
	)
}

private fun JsonElement?.toMessageList(): List<Message>? {
	val messages = this as? JsonArray ?: return null

	return messages.mapNotNull { element ->
		try {
			restJson.decodeFromJsonElement(Message.serializer(), element)
		} catch (e: Exception) {
			Log.w(TAG, "Could not decode a message, keeping the fields shown in the list: $element", e)
			element.toPartialMessage()
		}
	}
}

/**
 * A message with only the fields that are needed to display and open it in the list.
 *
 * The full content and the attachments are loaded from the message detail endpoint anyway.
 */
private fun JsonElement.toPartialMessage(): Message? {
	val message = this as? JsonObject ?: return null
	val id = (message["id"] as? JsonPrimitive)?.longOrNull ?: return null
	val sender = message["sender"] as? JsonObject

	return Message(
		id = id,
		subject = (message["subject"] as? JsonPrimitive)?.contentOrNull,
		contentPreview = (message["contentPreview"] as? JsonPrimitive)?.contentOrNull,
		sender = sender?.let {
			Sender(
				userId = (it["userId"] as? JsonPrimitive)?.longOrNull,
				displayName = (it["displayName"] as? JsonPrimitive)?.contentOrNull,
				imageUrl = (it["imageUrl"] as? JsonPrimitive)?.contentOrNull,
				className = (it["className"] as? JsonPrimitive)?.contentOrNull
			)
		},
		isMessageRead = (message["isMessageRead"] as? JsonPrimitive)?.booleanOrNull,
		isRevoked = (message["isRevoked"] as? JsonPrimitive)?.booleanOrNull,
		hasAttachments = (message["hasAttachments"] as? JsonPrimitive)?.booleanOrNull
	)
}

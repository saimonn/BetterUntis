package com.sapuseven.untis.modules

import kotlinx.serialization.json.Json

/**
 * JSON configuration for the REST API of the Untis mobile app.
 *
 * The REST API is not documented by Untis, so new fields can appear at any time (and only on
 * some objects, for example on messages that request a read confirmation). Ktor's default JSON
 * configuration rejects unknown keys, which made the whole response undecodable in that case -
 * the message list stayed empty until the server stopped sending the field again.
 *
 * The configuration therefore has to be lenient:
 *  - [Json.ignoreUnknownKeys] drops fields the app does not know about yet,
 *  - [Json.coerceInputValues] replaces unknown enum values with the default value,
 *  - [Json.isLenient] allows the differences in quoting that the server produces.
 */
internal val restJson: Json = Json {
	ignoreUnknownKeys = true
	coerceInputValues = true
	isLenient = true
	encodeDefaults = true
}

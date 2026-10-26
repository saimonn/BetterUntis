package com.sapuseven.untis.domain

/**
 * Thrown when the current account has no element (student, class, teacher, …) that the requested
 * endpoint works with, e.g. a guardian account whose children are not provided by the server.
 *
 * The screens turn this into an explanation for the user instead of showing an empty list.
 */
class ElementNotAvailableException : Throwable(
	"No element available for this account"
)

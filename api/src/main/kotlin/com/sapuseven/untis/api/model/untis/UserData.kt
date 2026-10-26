package com.sapuseven.untis.api.model.untis

import com.sapuseven.untis.api.model.untis.enumeration.ElementType
import com.sapuseven.untis.api.model.untis.enumeration.Right
import com.sapuseven.untis.api.model.untis.timetable.PeriodElement
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
	val elemId: Long,
	val elemType: ElementType?,
	val displayName: String,
	val schoolName: String,
	val departmentId: Long,
	val children: List<Person?> = emptyList(),
	val klassenIds: List<Long> = emptyList(),
	val rights: List<Right> = emptyList()
) {
	fun getPeriodElement() = elemType?.let { PeriodElement(it, elemId) }

	/**
	 * The element whose data (timetable, exams, homework) belongs to this account.
	 *
	 * Guardian accounts are logged in as the guardian itself (`LEGAL_GUARDIAN`,
	 * `APPRENTICE_REPRESENTATIVE`), but the timetable and class register endpoints only accept
	 * class, teacher, subject, room, student or parent elements; asking them for a guardian
	 * element fails with `invalid element type: 12`. The data of a guardian account is the data of
	 * the represented child, so the child is used as a student element instead.
	 *
	 * `null` means the account has no element that the timetable and class register endpoints
	 * accept, i.e. the screen has to tell the user that the data is unavailable instead of showing
	 * an empty week.
	 */
	val personalElement: PeriodElement?
		get() = when (elemType) {
			null -> firstChildAsStudent()
			ElementType.LEGAL_GUARDIAN, ElementType.APPRENTICE_REPRESENTATIVE -> firstChildAsStudent()
			else -> PeriodElement(elemType, elemId)
		}

	private fun firstChildAsStudent(): PeriodElement? =
		children.firstOrNull()?.let { PeriodElement(ElementType.STUDENT, it.id) }
}

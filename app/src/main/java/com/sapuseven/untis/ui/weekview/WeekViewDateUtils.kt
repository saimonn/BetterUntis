package com.sapuseven.untis.ui.weekview

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/**
 * Calculates the relative page index corresponding to a date.
 * The page index for today will always be `0`.
 */
internal fun pageIndexForDate(
	date: LocalDate,
	clock: Clock = Clock.systemDefaultZone(),
	firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
	weekLength: Int = 5,
	defaultToNext: Boolean = true,
	daysPerPage: Int = weekLength,
): Long {
	val daysPerPage = daysPerPage.coerceIn(1, 7)
	if (daysPerPage == 7) {
		val weeks = ChronoUnit.WEEKS.between(
			weekStartForDate(LocalDate.now(clock), firstDayOfWeek), weekStartForDate(date, firstDayOfWeek)
		)
		return if (ChronoUnit.DAYS.between(
				weekStartForDate(date, firstDayOfWeek),
				date
			) >= weekLength && defaultToNext
		) weeks + 1
		else weeks
	}

	val todayStart = pageStartDate(LocalDate.now(clock), firstDayOfWeek, daysPerPage)
	val dateStart = pageStartDate(date, firstDayOfWeek, daysPerPage)
	return ChronoUnit.DAYS.between(todayStart, dateStart) / daysPerPage.toLong()
}

/**
 * Calculates the start date for a specific page.
 */
internal fun startDateForPageIndex(
	pageIndex: Long,
	clock: Clock = Clock.systemDefaultZone(),
	firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
	daysPerPage: Int = 5,
): LocalDate {
	val daysPerPage = daysPerPage.coerceIn(1, 7)
	if (daysPerPage == 7) {
		return weekStartForDate(LocalDate.now(clock), firstDayOfWeek).plusWeeks(pageIndex)
	}
	return pageStartDate(LocalDate.now(clock), firstDayOfWeek, daysPerPage).plusDays(pageIndex * daysPerPage.toLong())
}

private fun pageStartDate(date: LocalDate, firstDayOfWeek: DayOfWeek, daysPerPage: Int): LocalDate {
	if (daysPerPage >= 7) return weekStartForDate(date, firstDayOfWeek)
	if (daysPerPage == 1) return date
	// For 3 days: align to week start so the view shifts in a predictable way
	val weekStart = weekStartForDate(date, firstDayOfWeek)
	val offset = ChronoUnit.DAYS.between(weekStart, date).toInt() / daysPerPage * daysPerPage
	return weekStart.plusDays(offset.toLong())
}

private fun weekStartForDate(
	date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
): LocalDate = date.with(WeekFields.of(firstDayOfWeek, 4).dayOfWeek(), 1).let {
	if (it.isAfter(date)) it.minusWeeks(1)
	else it
}

internal class DateIterator(
	val startDate: LocalDate, val endDateInclusive: LocalDate, val stepDays: Long
) : Iterator<LocalDate> {
	private var currentDate = startDate

	override fun hasNext() = currentDate <= endDateInclusive

	override fun next(): LocalDate {
		val next = currentDate
		currentDate = currentDate.plusDays(stepDays)
		return next
	}
}

internal class DateProgression(
	override val start: LocalDate, override val endInclusive: LocalDate, val stepDays: Long = 1
) : Iterable<LocalDate>, ClosedRange<LocalDate> {

	override fun iterator(): Iterator<LocalDate> = DateIterator(start, endInclusive, stepDays)

	infix fun step(days: Long) = DateProgression(start, endInclusive, days)
}

internal operator fun LocalDate.rangeTo(other: LocalDate) = DateProgression(this, other)

/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.AppViewModel
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.openEventDetail
import com.arishawke.asala.calendar.rescheduleEvent
import com.arishawke.asala.calendar.ui.day.DayScreen
import com.arishawke.asala.calendar.ui.month.MonthScreen
import com.arishawke.asala.calendar.ui.schedule.ScheduleScreen
import com.arishawke.asala.calendar.ui.settings.UserPrefs
import com.arishawke.asala.calendar.ui.settings.toWorkingDaysMask
import com.arishawke.asala.calendar.ui.tasks.TasksComingSoonScreen
import com.arishawke.asala.calendar.ui.threeday.ThreeDayScreen
import com.arishawke.asala.calendar.ui.week.WeekScreen

// Switches between Month/Week/Day/Schedule/Tasks with the slide-and-fade
// animation. The animation collapses to None when the user disables
// animations via system accessibility (rememberAnimationsEnabled).
@Composable
internal fun CalendarViewSwitcher(
    vm: AppViewModel,
    currentView: CalendarView,
    prefs: UserPrefs,
    animationsEnabled: Boolean,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compose can't infer Set<DayOfWeek> as stable; encode the working
    // days as a bitmask once at this boundary so the screens take a Long.
    val workingDaysMask = remember(prefs.workingDays) { prefs.workingDays.toWorkingDaysMask() }
    AnimatedContent(
        targetState = currentView,
        modifier = modifier,
        transitionSpec = {
            if (!animationsEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = targetState.ordinal > initialState.ordinal
                val direction = if (forward) SlideDirection.Left else SlideDirection.Right
                (fadeIn() + slideIntoContainer(direction)) togetherWith
                    (fadeOut() + slideOutOfContainer(direction))
            }
        },
        label = "view-switch",
    ) { view ->
        when (view) {
            CalendarView.Month -> MonthScreen(
                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                onTitleChange = onTitleChange,
                todayJumpCounter = vm.todayJumpCounter,
                pendingDateJump = vm.pendingDateJump,
                onConsumePendingDateJump = vm::consumePendingDateJump,
                onViewedMonthChange = vm::setViewedMonth,
                monthScrollStyle = prefs.monthScrollStyle,
                firstDayOfWeekOverride = prefs.weekStartsOn,
                dimPastDates = prefs.dimPastDates,
                showWeekNumber = prefs.showWeekNumber,
                onDayCellClick = { date -> vm.requestJumpTo(date, CalendarView.Day) },
                onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                onViewedDateChange = vm::setViewedDate,
            )
            CalendarView.Week -> WeekScreen(
                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                onTitleChange = onTitleChange,
                todayJumpCounter = vm.todayJumpCounter,
                pendingDateJump = vm.pendingDateJump,
                onConsumePendingDateJump = vm::consumePendingDateJump,
                firstDayOfWeekOverride = prefs.weekStartsOn,
                dimPastDates = prefs.dimPastDates,
                workingHoursEnabled = prefs.workingHoursEnabled,
                workingHoursStartHour = prefs.workingHoursStartHour,
                workingHoursEndHour = prefs.workingHoursEndHour,
                workingDaysEnabled = prefs.workingDaysEnabled,
                workingDaysMask = workingDaysMask,
                showWeekNumber = prefs.showWeekNumber,
                onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                onReschedule = { eid, instMillis, newStart ->
                    vm.rescheduleEvent(eid, instMillis, newStart)
                },
                onViewedDateChange = vm::setViewedDate,
            )
            CalendarView.ThreeDay -> ThreeDayScreen(
                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                onTitleChange = onTitleChange,
                todayJumpCounter = vm.todayJumpCounter,
                pendingDateJump = vm.pendingDateJump,
                onConsumePendingDateJump = vm::consumePendingDateJump,
                workingHoursEnabled = prefs.workingHoursEnabled,
                workingHoursStartHour = prefs.workingHoursStartHour,
                workingHoursEndHour = prefs.workingHoursEndHour,
                workingDaysEnabled = prefs.workingDaysEnabled,
                workingDaysMask = workingDaysMask,
                onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                onReschedule = { eid, instMillis, newStart ->
                    vm.rescheduleEvent(eid, instMillis, newStart)
                },
                onViewedDateChange = vm::setViewedDate,
            )
            CalendarView.Day -> DayScreen(
                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                onTitleChange = onTitleChange,
                todayJumpCounter = vm.todayJumpCounter,
                pendingDateJump = vm.pendingDateJump,
                onConsumePendingDateJump = vm::consumePendingDateJump,
                workingHoursEnabled = prefs.workingHoursEnabled,
                workingHoursStartHour = prefs.workingHoursStartHour,
                workingHoursEndHour = prefs.workingHoursEndHour,
                workingDaysEnabled = prefs.workingDaysEnabled,
                workingDaysMask = workingDaysMask,
                onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                onReschedule = { eid, instMillis, newStart ->
                    vm.rescheduleEvent(eid, instMillis, newStart)
                },
                onViewedDateChange = vm::setViewedDate,
            )
            CalendarView.Schedule -> ScheduleScreen(
                hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
                eventColorOverridesFlow = vm.eventColorOverridesFlow,
                onTitleChange = onTitleChange,
                todayJumpCounter = vm.todayJumpCounter,
                pendingDateJump = vm.pendingDateJump,
                onConsumePendingDateJump = vm::consumePendingDateJump,
                onEventClick = { eid, millis -> vm.openEventDetail(eid, millis) },
                onViewedDateChange = vm::setViewedDate,
            )
            CalendarView.Tasks -> {
                val tasksTitle = stringResource(R.string.view_tasks)
                LaunchedEffect(tasksTitle) { onTitleChange(tasksTitle) }
                TasksComingSoonScreen()
            }
        }
    }
}

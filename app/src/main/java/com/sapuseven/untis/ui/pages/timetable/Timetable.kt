package com.sapuseven.untis.ui.pages.timetable

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sapuseven.untis.R
import com.sapuseven.untis.data.model.github.GitHubApi.URL_GITHUB_REPOSITORY
import com.sapuseven.untis.ui.animations.fullscreenDialogAnimationEnter
import com.sapuseven.untis.ui.animations.fullscreenDialogAnimationExit
import com.sapuseven.untis.ui.common.AppScaffold
import com.sapuseven.untis.ui.common.DebugDesclaimerAction
import com.sapuseven.untis.ui.common.ErrorBubble
import com.sapuseven.untis.ui.common.NavigationBarInset
import com.sapuseven.untis.ui.common.ProfileSelectorAction
import com.sapuseven.untis.ui.common.disabled
import com.sapuseven.untis.ui.dialogs.ProfileManagementDialog
import com.sapuseven.untis.ui.functional.bottomInsets
import com.sapuseven.untis.ui.functional.insetsPaddingValues
import com.sapuseven.untis.ui.navigation.AppRoutes
import com.sapuseven.untis.ui.pages.timetable.details.TimetableItemDetailsDialog
import com.sapuseven.untis.ui.weekview.WeekViewCompose
import com.sapuseven.untis.ui.weekview.WeekViewStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Timetable(
	viewModel: TimetableViewModel = hiltViewModel(),
	onNavigate: (Any) -> Unit = {}
	//factory: TimetableViewModel.Factory = hiltEntryPointViewModelFactory() // explained below
	/*viewModel: TimetableViewModel = hiltViewModel<TimetableViewModel, TimetableViewModel.Factory>(
		creationCallback = { factory -> factory.create(colorScheme, typography) }
	)*/
) {
	val ready by viewModel.ready.collectAsStateWithLifecycle()

	val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
	val scope = rememberCoroutineScope()
	val user = viewModel.currentUser
	val users by viewModel.allUsersState.collectAsStateWithLifecycle()

	val needsPersonalTimetable by viewModel.needsPersonalTimetable.collectAsStateWithLifecycle()
	val hourList by viewModel.hourList.collectAsStateWithLifecycle()
	val events by viewModel.events.collectAsStateWithLifecycle()
	val holidays by viewModel.holidays.collectAsStateWithLifecycle()
	val lastRefresh by viewModel.lastRefresh.collectAsStateWithLifecycle()
	val loadError by viewModel.loadError.collectAsStateWithLifecycle()
	val weekViewColorScheme by viewModel.weekViewColorScheme.collectAsStateWithLifecycle()
	val weekViewScale by viewModel.weekViewScale.collectAsStateWithLifecycle()
	val weekViewZoomEnabled by viewModel.weekViewZoomEnabled.collectAsStateWithLifecycle()
	val weekViewEventStyle by viewModel.weekViewEventStyle.collectAsStateWithLifecycle()

	DisposableEffect(LocalLifecycleOwner.current) {
		Log.d("Timetable", "Creating TimetableViewModel")

		onDispose {
			Log.d("Timetable", "Disposing TimetableViewModel")
		}
	}

	TimetableDrawer(
		drawerState = drawerState,
		masterDataRepository = viewModel.masterDataRepository,
		displayedElement = viewModel.requestedElement,
		onElementPicked = {
			viewModel.showElement(it)
		}
	) {
		AppScaffold(
			topBar = {
				CenterAlignedTopAppBar(
					title = {
						Text(viewModel.getTitle(LocalContext.current))
					},
					navigationIcon = {
						IconButton(onClick = {
							scope.launch {
								drawerState.open()
							}
						}) {
							Icon(
								imageVector = Icons.Outlined.Menu,
								contentDescription = stringResource(id = R.string.main_drawer_open)
							)
						}
					},
					actions = {
						if (viewModel.isDebug)
							DebugDesclaimerAction()

						ProfileSelectorAction(
							users = users,
							currentSelection = user,
							showProfileActions = true,
							onSelectionChange = {
								viewModel.switchUser(it)
							},
							onActionEdit = {
								viewModel.editUsers()
							}
						)
					}
				)
			},
			bottomBar = {
				MainNavigationBar(onNavigate = onNavigate)
			}
		) { innerPadding ->
			AnimatedVisibility(ready,
				enter = fadeIn(tween()),
				exit = fadeOut(tween())
			) {
				Box(
					modifier = Modifier
						.padding(innerPadding)
						.fillMaxSize()
				) {
					val insets = insetsPaddingValues()
					val navBarHeight = remember { insets.calculateBottomPadding() + 48.dp }

					var currentTime by remember { mutableStateOf(LocalDateTime.now(viewModel.clock)) }

					LaunchedEffect(Unit) {
						while (true) {
							// Refresh the weekview (and last refresh text) periodically
							// This could probably be increased to 1 minute, but 10 seconds seems fine
							currentTime = LocalDateTime.now(viewModel.clock)
							delay(10_000)
						}
					}

					WeekViewStyle(weekViewEventStyle) {
						WeekViewCompose(
							events = events,
							holidays = holidays,
							loading = if (viewModel.loading) true else null,
							weekLogicService = viewModel.weekLogicService,
							onPageChange = { pageOffset ->
								viewModel.onPageChange(pageOffset)
							},
							onReload = { pageOffset ->
								viewModel.onPageReload(pageOffset)
							},
							onItemClick = { itemsWithIndex ->
								viewModel.onItemClick(itemsWithIndex)
							},
							onZoom = { zoomLevel ->
								viewModel.onZoom(zoomLevel)
							},
							currentTime = currentTime,
							startTime = hourList.firstOrNull()?.startTime ?: LocalTime.MIDNIGHT,
							endTime = hourList.lastOrNull()?.endTime ?: LocalTime.MIDNIGHT,
							endTimeOffset = navBarHeight,
							initialScale = weekViewScale,
							enableZoomGesture = weekViewZoomEnabled,
							hourHeight = /*state.weekViewPreferences.hourHeight ?:*/ 72.dp,
							hourList = hourList,
							//dividerWidth = viewModel.weekViewPreferences.dividerWidth,
							colorScheme = weekViewColorScheme,
							modifier = Modifier
								.fillMaxSize()
								.disabled(disabled = needsPersonalTimetable)
						) { startPadding ->
							// Report an issue on GitHub
							val uriHandler = LocalUriHandler.current
							IconButton(
								modifier = Modifier
									.align(Alignment.BottomEnd)
									.padding(end = 8.dp)
									.bottomInsets(),
								onClick = {
									uriHandler.openUri("$URL_GITHUB_REPOSITORY/issues/new")
								}
							) {
								Icon(
									painter = painterResource(R.drawable.all_feedback),
									contentDescription = stringResource(R.string.main_report_issue)
								)
							}

							// Custom personal timetable hint
							if (needsPersonalTimetable) {
								Column(
									verticalArrangement = Arrangement.Center,
									horizontalAlignment = Alignment.CenterHorizontally,
									modifier = Modifier
										.fillMaxSize()
								) {
									Text(
										text = stringResource(id = R.string.main_anonymous_login_info_text),
										textAlign = TextAlign.Center,
										modifier = Modifier
											.padding(horizontal = 32.dp)
									)

									Button(
										onClick = viewModel.onAnonymousSettingsClick,
										modifier = Modifier
											.padding(top = 16.dp)
									) {
										Text(text = stringResource(id = R.string.main_go_to_settings))
									}
								}
							} else if (loadError == null) {
								// Last refresh text
								Text(
									text = stringResource(
										id = R.string.main_last_refreshed,
										formatTimeDiffMillis(lastRefresh?.let {
											currentTime.atZone(ZoneId.systemDefault()).toInstant()
												.toEpochMilli() - it.toEpochMilli()
										})
									),
									modifier = Modifier
										.align(Alignment.BottomStart)
										.padding(start = startPadding + 8.dp, bottom = 8.dp)
										.bottomInsets()
								)
							}
						}
					}

					loadError?.let { error ->
						ErrorBubble(
							error = error,
							unavailableMessage = R.string.timetable_unavailable,
							modifier = Modifier
								.align(Alignment.TopCenter)
								.padding(top = 8.dp)
								.bottomInsets()
						)
					}
				}
			}
		}
	}

	// TODO: Implement a nicer animation (see https://m3.material.io/components/dialogs/guidelines#007536b9-76b1-474a-a152-2f340caaff6f)
	AnimatedVisibility(
		visible = viewModel.timetableItemDetailsDialog != null,
		enter = fullscreenDialogAnimationEnter(),
		exit = fullscreenDialogAnimationExit()
	) {
		TimetableItemDetailsDialog(
			timetableRepository = viewModel.timetableRepository,
			periodItems = remember {
				viewModel.timetableItemDetailsDialog?.first?.mapNotNull { it.data } ?: emptyList()
			},
			initialPage = remember {
				viewModel.timetableItemDetailsDialog?.second ?: 0
			},
			masterDataRepository = viewModel.masterDataRepository,
			onDismiss = {
				viewModel.timetableItemDetailsDialog = null
				it?.let { viewModel.showElement(it) }
			}
		)
	}

	AnimatedVisibility(
		visible = viewModel.profileManagementDialog,
		enter = fullscreenDialogAnimationEnter(),
		exit = fullscreenDialogAnimationExit()
	) {
		ProfileManagementDialog(
			userRepository = viewModel.userRepository,
			onEdit = {
				viewModel.editUser(it)
			},
			onDismiss = {
				viewModel.profileManagementDialog = false
			}
		)
	}
}

@Composable
private fun formatTimeDiffMillis(diff: Long?): String {
	val MINUTE_MILLIS: Int = 60 * 1000
	val HOUR_MILLIS: Int = 60 * MINUTE_MILLIS
	val DAY_MILLIS: Int = 24 * HOUR_MILLIS

	if (diff == null) return stringResource(R.string.main_last_refreshed_never)

	return when {
		diff < MINUTE_MILLIS -> stringResource(R.string.main_time_diff_just_now)
		diff < HOUR_MILLIS -> pluralStringResource(
			R.plurals.main_time_diff_minutes, ((diff / MINUTE_MILLIS).toInt()), diff / MINUTE_MILLIS
		)

		diff < DAY_MILLIS -> pluralStringResource(
			R.plurals.main_time_diff_hours, ((diff / HOUR_MILLIS).toInt()), diff / HOUR_MILLIS
		)

		else -> pluralStringResource(
			R.plurals.main_time_diff_days, ((diff / DAY_MILLIS).toInt()), diff / DAY_MILLIS
		)
	}
}

/**
 * Bottom navigation of the home screen, mirroring the four main sections of the
 * official Untis client. Only the current destination (the timetable) is
 * selected; the others open their own screen, which provides its own top bar
 * and back navigation.
 */
@Composable
private fun MainNavigationBar(onNavigate: (Any) -> Unit) {
	NavigationBarInset {
		NavigationBarItem(
			icon = {
				Icon(
					painter = painterResource(R.drawable.all_calendar),
					contentDescription = null
				)
			},
			label = { Text(stringResource(id = R.string.activity_title_timetable)) },
			selected = true,
			onClick = {}
		)

		NavigationBarItem(
			icon = {
				Icon(
					painter = painterResource(R.drawable.infocenter_messages),
					contentDescription = null
				)
			},
			label = { Text(stringResource(id = R.string.menu_infocenter_messagesofday)) },
			selected = false,
			onClick = { onNavigate(AppRoutes.InfoCenter) }
		)

		NavigationBarItem(
			icon = {
				Icon(
					painter = painterResource(R.drawable.all_search_rooms),
					contentDescription = null
				)
			},
			label = { Text(stringResource(id = R.string.activity_title_free_rooms)) },
			selected = false,
			onClick = { onNavigate(AppRoutes.RoomFinder) }
		)

		NavigationBarItem(
			icon = {
				Icon(
					painter = painterResource(R.drawable.all_settings),
					contentDescription = null
				)
			},
			label = { Text(stringResource(id = R.string.activity_title_settings)) },
			selected = false,
			onClick = { onNavigate(AppRoutes.Settings) }
		)
	}
}

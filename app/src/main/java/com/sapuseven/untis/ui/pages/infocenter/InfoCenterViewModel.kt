package com.sapuseven.untis.ui.pages.infocenter

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sapuseven.untis.api.model.untis.enumeration.Right
import com.sapuseven.untis.data.cache.AttachmentCache
import com.sapuseven.untis.data.repository.MasterDataRepository
import com.sapuseven.untis.data.repository.UserRepository
import com.sapuseven.untis.data.repository.UserSettingsRepository
import com.sapuseven.untis.domain.DownloadMessageAttachmentUseCase
import com.sapuseven.untis.domain.GetAbsencesUseCase
import com.sapuseven.untis.domain.GetExamsUseCase
import com.sapuseven.untis.domain.GetHomeworkUseCase
import com.sapuseven.untis.domain.GetMessagesOfDayUseCase
import com.sapuseven.untis.domain.GetMessagesUseCase
import com.sapuseven.untis.domain.GetOfficeHoursUseCase
import com.sapuseven.untis.model.rest.Message
import com.sapuseven.untis.model.rest.MessageAttachment
import com.sapuseven.untis.ui.navigation.AppNavigator
import com.sapuseven.untis.ui.pages.infocenter.fragments.AbsencesUiState
import com.sapuseven.untis.ui.pages.infocenter.fragments.EventsUiState
import com.sapuseven.untis.ui.pages.infocenter.fragments.MessagesUiState
import com.sapuseven.untis.ui.pages.infocenter.fragments.OfficeHoursUiState
import crocodile8.universal_cache.FromCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class InfoCenterViewModel @Inject constructor(
	private val userRepository: UserRepository,
	internal val masterDataRepository: MasterDataRepository,
	private val navigator: AppNavigator,
	internal val userSettingsRepository: UserSettingsRepository,
	private val getMessages: GetMessagesUseCase,
	private val downloadMessageAttachment: DownloadMessageAttachmentUseCase,
	private val attachmentCache: AttachmentCache,
	getMessagesOfDay: GetMessagesOfDayUseCase,
	getExams: GetExamsUseCase,
	getHomework: GetHomeworkUseCase,
	getAbsences: GetAbsencesUseCase,
	getOfficeHours: GetOfficeHoursUseCase,
) : ViewModel() {
	private val excuseStatuses = masterDataRepository.userData?.excuseStatuses ?: emptyList()

	val shouldShowAbsences: Boolean
		get() = Right.R_MY_ABSENCES in userRepository.currentUser!!.userData.rights
	val shouldShowAbsencesAdd: Boolean
		get() = Right.W_OWN_ABSENCE in userRepository.currentUser!!.userData.rights
	val shouldShowAbsencesAddReason: Boolean
		get() = Right.W_OWN_ABSENCEREASON in userRepository.currentUser!!.userData.rights

	val shouldShowOfficeHours: Boolean
		get() = Right.R_OFFICEHOURS in userRepository.currentUser!!.userData.rights

	val messagesState: StateFlow<MessagesUiState> = combine(
		getMessagesOfDay(),
		getMessages(),
		MessagesUiState::Success
	).stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = MessagesUiState.Loading
	)

	private val _selectedMessage = MutableStateFlow<Message?>(null)
	val selectedMessage: StateFlow<Message?> = _selectedMessage
	private val _selectedMessageContent = MutableStateFlow<String?>(null)
	val selectedMessageContent: StateFlow<String?> = _selectedMessageContent

	/**
	 * Why the detail of the selected message could not be loaded, `null` while everything is fine.
	 *
	 * Without this the screen just kept showing the list entry with a loading placeholder, which
	 * is indistinguishable from a click that did nothing.
	 */
	private val _messageDetailError = MutableStateFlow<String?>(null)
	val messageDetailError: StateFlow<String?> = _messageDetailError

	private var messageDetailJob: Job? = null

	private val _attachmentStates = MutableStateFlow<Map<String, AttachmentUiState>>(emptyMap())

	/**
	 * Download state of every attachment of the selected message, keyed by [attachmentKey].
	 */
	val attachmentStates: StateFlow<Map<String, AttachmentUiState>> = _attachmentStates.asStateFlow()

	private val attachmentJobs = ConcurrentHashMap<String, Job>()

	/**
	 * The key of an attachment in [attachmentStates]. It matches the file name in the cache, so
	 * the same attachment always maps to the same state, across tab switches and recompositions.
	 */
	fun attachmentKey(messageId: Long, attachment: MessageAttachment): String =
		attachmentCache.fileName(messageId, attachment)

	/**
	 * Starts the download of an attachment unless it is cached already or a download is running.
	 *
	 * Called whenever an attachment becomes visible: it is idempotent, so showing a message again
	 * (after switching tabs, rotating the screen, ...) never restarts a download or leaves the
	 * view in a loading state.
	 */
	fun loadAttachment(messageId: Long, attachment: MessageAttachment) {
		val key = attachmentKey(messageId, attachment)
		if (_attachmentStates.value[key] is AttachmentUiState.Ready) return
		if (attachmentJobs[key]?.isActive == true) return

		startAttachmentDownload(key, messageId, attachment)
	}

	/**
	 * Starts the download again after it failed or after the cached file could not be used.
	 */
	fun retryAttachment(messageId: Long, attachment: MessageAttachment) {
		startAttachmentDownload(attachmentKey(messageId, attachment), messageId, attachment, retry = true)
	}

	private fun startAttachmentDownload(
		key: String,
		messageId: Long,
		attachment: MessageAttachment,
		retry: Boolean = false,
	) {
		attachmentJobs.remove(key)?.cancel()

		val job = viewModelScope.launch {
			_attachmentStates.update { it + (key to AttachmentUiState.Loading) }

			if (retry)
				runCatching { attachmentCache.delete(messageId, attachment) }

			try {
				val file = downloadMessageAttachment(messageId, attachment)
				_attachmentStates.update { it + (key to AttachmentUiState.Ready(file)) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.w("InfoCenter", "Attachment $key could not be downloaded", e)
				_attachmentStates.update {
					it + (key to AttachmentUiState.Failed(e.message ?: e::class.java.simpleName))
				}
			}
		}

		attachmentJobs[key] = job
		job.invokeOnCompletion {
			// A newer download replaced this one: leave its state alone.
			if (attachmentJobs[key] !== job) return@invokeOnCompletion

			attachmentJobs.remove(key)
			if (job.isCancelled)
				_attachmentStates.update { it - key }
		}
	}

	/**
	 * Loads the selected message again, e.g. after its request failed.
	 */
	fun retrySelectedMessage() {
		_selectedMessage.value?.let { onMessageClicked(it) }
	}

	fun onMessageClicked(message: Message) {
		// The message from the list only carries `hasAttachments`; the actual
		// attachments are part of the message detail, so show the list entry
		// first and replace it once the detail has been loaded.
		_selectedMessage.value = message
		_selectedMessageContent.value = null
		_messageDetailError.value = null

		// Clicking a second message must not be overwritten by the (slower) answer of the first.
		messageDetailJob?.cancel()
		messageDetailJob = viewModelScope.launch {
			loadMessageDetail(message.id).fold(
				onSuccess = {
					_selectedMessage.value = it
					_selectedMessageContent.value = it.content
				},
				onFailure = {
					Log.w("InfoCenter", "Message ${message.id} could not be loaded", it)
					_messageDetailError.value = it.message ?: it::class.java.simpleName
				}
			)
		}
	}

	/**
	 * Loads the detail of a message, falling back to a request without the local cache.
	 *
	 * The cached models come from the generated REST client and may become unreadable between two
	 * app versions. A stale entry must not make a message permanently unopenable: the second
	 * attempt bypasses the cache and replaces the broken entry.
	 */
	private suspend fun loadMessageDetail(id: Long): Result<Message> {
		val cached = getMessages(id).first()
		if (cached.isSuccess) return cached

		Log.d("InfoCenter", "Retrying message $id without the local cache")
		return getMessages(id, FromCache.NEVER).first()
	}

	fun onMessageDismiss() {
		clearSelectedMessage()
	}

	fun onMessageReply() {
		clearSelectedMessage()
	}

	fun onMessageDelete() {
		clearSelectedMessage()
	}

	private fun clearSelectedMessage() {
		messageDetailJob?.cancel()
		messageDetailJob = null

		val messageId = _selectedMessage.value?.id
		_selectedMessage.value = null
		_selectedMessageContent.value = null
		_messageDetailError.value = null

		// Keep the download states of the messages that are not shown anymore from accumulating.
		if (messageId != null)
			_attachmentStates.update { states -> states.filterKeys { !it.startsWith("${messageId}_") } }
	}

	val eventsState: StateFlow<EventsUiState> = combine(
		getExams(),
		getHomework(),
		EventsUiState::Success
	).stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = EventsUiState.Loading
	)

	val absencesState: StateFlow<AbsencesUiState> =
		(if (!shouldShowAbsences) emptyFlow() else getAbsences())
			.map { AbsencesUiState.Success(it, excuseStatuses) }
			.stateIn(
				scope = viewModelScope,
				started = SharingStarted.WhileSubscribed(5_000),
				initialValue = AbsencesUiState.Loading
			)

	val officeHoursState: StateFlow<OfficeHoursUiState> =
		(if (!shouldShowAbsences) emptyFlow() else getOfficeHours())
			.map(OfficeHoursUiState::Success)
			.stateIn(
				scope = viewModelScope,
				started = SharingStarted.WhileSubscribed(5_000),
				initialValue = OfficeHoursUiState.Loading
			)

	/**
	 * Back navigation of the Info Center.
	 *
	 * An open message is closed first; only a second back press leaves the screen. Without this
	 * the back arrow of the top bar jumped straight to the timetable while a message was open.
	 */
	fun goBack() {
		if (_selectedMessage.value != null) clearSelectedMessage()
		else navigator.popBackStack()
	}
}

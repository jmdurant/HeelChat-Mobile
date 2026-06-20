package com.garfiec.librechat.feature.heelcode.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RemoteRepository
import com.garfiec.librechat.core.model.RemoteMessage
import com.garfiec.librechat.core.model.RemoteSession
import com.garfiec.librechat.core.network.api.RemoteEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A group of sessions sharing one machine, for the sectioned list. */
@Immutable
data class MachineGroup(
    val machineId: String,
    val machineName: String,
    val online: Boolean,
    val sessions: List<RemoteSession>,
)

@Immutable
data class HeelCodeUiState(
    val machines: List<MachineGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Non-null when a session detail view is open. */
    val selected: RemoteSession? = null,
    /** Loaded transcript for [selected]. */
    val transcript: List<RemoteMessage> = emptyList(),
    /** Live output streamed since the detail view opened, appended in arrival order. */
    val liveOutput: String = "",
    val isDetailLoading: Boolean = false,
    /** True while the owning machine is actively running the agent. */
    val isRunning: Boolean = false,
    val isSending: Boolean = false,
    val detailError: String? = null,
)

/**
 * Drives the HeelCode Remote screen: lists sessions (grouped by machine), opens a session's
 * transcript, streams its live output, and continues the run with new instructions.
 */
class HeelCodeViewModel(
    private val remoteRepository: RemoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeelCodeUiState())
    val uiState: StateFlow<HeelCodeUiState> = _uiState.asStateFlow()

    /** Cancelled when the detail view closes or a new session opens, tearing down the SSE collect. */
    private var eventsJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = remoteRepository.listSessions()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, machines = groupByMachine(result.data))
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message ?: "Failed to load sessions")
                }
                Result.Loading -> Unit
            }
        }
    }

    /** Opens a session: loads its transcript, then starts streaming its live event feed. */
    fun open(session: RemoteSession) {
        eventsJob?.cancel()
        _uiState.update {
            it.copy(
                selected = session,
                transcript = emptyList(),
                liveOutput = "",
                isDetailLoading = true,
                isRunning = false,
                detailError = null,
            )
        }
        viewModelScope.launch {
            when (val result = remoteRepository.history(session.id)) {
                is Result.Success -> _uiState.update {
                    it.copy(isDetailLoading = false, transcript = result.data.messages)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isDetailLoading = false, detailError = result.message ?: "Failed to load history")
                }
                Result.Loading -> Unit
            }
        }
        startStreaming(session.id)
    }

    /** Sends an instruction to continue the run, then re-attaches the live stream if needed. */
    fun send(text: String) {
        val session = _uiState.value.selected ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return

        _uiState.update { it.copy(isSending = true, detailError = null) }
        viewModelScope.launch {
            when (val result = remoteRepository.continueSession(session.id, trimmed)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSending = false, isRunning = true) }
                    // Reflect the just-sent instruction locally so the user sees it immediately.
                    appendOutput("\n\n> $trimmed\n")
                    // Ensure we're streaming output for this run (re-attach if the prior stream ended).
                    if (eventsJob?.isActive != true) startStreaming(session.id)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSending = false, detailError = result.message ?: "Failed to send")
                }
                Result.Loading -> Unit
            }
        }
    }

    /** Closes the detail view and tears down the live stream. */
    fun back() {
        eventsJob?.cancel()
        eventsJob = null
        _uiState.update {
            it.copy(
                selected = null,
                transcript = emptyList(),
                liveOutput = "",
                isRunning = false,
                detailError = null,
            )
        }
    }

    private fun startStreaming(id: String) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            remoteRepository.events(id).collect { event ->
                when (event) {
                    is RemoteEvent.Output -> appendOutput(event.text)
                    is RemoteEvent.Status -> _uiState.update { it.copy(isRunning = event.running) }
                }
            }
            // Flow completed (stream closed or run went idle).
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    private fun appendOutput(text: String) {
        if (text.isEmpty()) return
        _uiState.update { it.copy(liveOutput = it.liveOutput + text) }
    }

    private fun groupByMachine(sessions: List<RemoteSession>): List<MachineGroup> =
        sessions
            .groupBy { it.machineId }
            .map { (machineId, group) ->
                val first = group.first()
                MachineGroup(
                    machineId = machineId,
                    machineName = first.machineName,
                    online = group.any { it.online },
                    // Most-recently-active session first within each machine.
                    sessions = group.sortedByDescending { it.mtime },
                )
            }
            // Online machines first, then most-recently-active.
            .sortedWith(
                compareByDescending<MachineGroup> { it.online }
                    .thenByDescending { group -> group.sessions.maxOfOrNull { it.mtime } ?: 0L },
            )

    override fun onCleared() {
        super.onCleared()
        eventsJob?.cancel()
    }
}

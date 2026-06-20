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
    /** Live turns (user + assistant), labelled like history, streamed since the view opened. */
    val liveTurns: List<RemoteMessage> = emptyList(),
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
                liveTurns = emptyList(),
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
                    // Add our message as a user turn immediately (we ignore the hub's echo of it).
                    _uiState.update {
                        it.copy(isSending = false, isRunning = true, liveTurns = it.liveTurns + RemoteMessage(role = "user", text = trimmed))
                    }
                    // The stream stays open across turns now; only re-attach if it somehow dropped.
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
                liveTurns = emptyList(),
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
                    is RemoteEvent.Output ->
                        // role "user" is the hub echoing our own message — we already added it in send().
                        if (event.role != "user" && event.text.isNotBlank()) appendAssistant(event.text)
                    is RemoteEvent.Status -> _uiState.update { it.copy(isRunning = event.running) }
                }
            }
            // Stream ended (view closed / connection dropped).
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    // Append agent output to the current assistant turn, or start one if the last turn was the user's.
    private fun appendAssistant(text: String) {
        _uiState.update { st ->
            val turns = st.liveTurns
            val last = turns.lastOrNull()
            if (last != null && last.role == "assistant") {
                st.copy(liveTurns = turns.dropLast(1) + last.copy(text = last.text + text))
            } else {
                st.copy(liveTurns = turns + RemoteMessage(role = "assistant", text = text))
            }
        }
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

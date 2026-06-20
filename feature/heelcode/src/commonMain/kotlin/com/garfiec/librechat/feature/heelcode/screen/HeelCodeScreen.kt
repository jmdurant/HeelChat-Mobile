package com.garfiec.librechat.feature.heelcode.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.RemoteMessage
import com.garfiec.librechat.core.model.RemoteSession
import com.garfiec.librechat.feature.heelcode.viewmodel.HeelCodeUiState
import com.garfiec.librechat.feature.heelcode.viewmodel.HeelCodeViewModel
import com.garfiec.librechat.feature.heelcode.viewmodel.MachineGroup
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * HeelCode Remote — view and continue HeelCode coding-agent sessions from the phone.
 * A single destination that flips between a machine-grouped session list and a session
 * detail (transcript + live streaming output + a continue field), driven by [HeelCodeUiState].
 */
@Composable
fun HeelCodeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeelCodeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh the list each time the screen returns to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (uiState.selected == null) viewModel.refresh()
    }

    if (uiState.selected != null) {
        SessionDetail(
            uiState = uiState,
            onBack = viewModel::back,
            onSend = viewModel::send,
            modifier = modifier,
        )
    } else {
        SessionList(
            uiState = uiState,
            onRefresh = viewModel::refresh,
            onOpen = viewModel::open,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionList(
    uiState: HeelCodeUiState,
    onRefresh: () -> Unit,
    onOpen: (RemoteSession) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("HeelCode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.machines.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null && uiState.machines.isEmpty() -> {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
                uiState.machines.isEmpty() -> {
                    Text(
                        text = "No HeelCode sessions found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        uiState.machines.forEach { machine ->
                            item(key = "machine_${machine.machineId}") {
                                MachineHeader(machine)
                            }
                            machine.sessions.forEach { session ->
                                item(key = session.id) {
                                    SessionRow(
                                        machine = machine,
                                        session = session,
                                        onClick = { onOpen(session) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineHeader(machine: MachineGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (machine.online) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = machine.machineName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (machine.online) "online" else "offline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(
    machine: MachineGroup,
    session: RemoteSession,
    onClick: () -> Unit,
) {
    // Offline sessions can't accept a continue right now — dim the row to signal that.
    val rowAlpha = if (machine.online) 1f else 0.5f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = machine.online, onClick = onClick)
            .alpha(rowAlpha)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = session.title.ifBlank { basename(session.cwd) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = remember(session) {
            buildString {
                append(basename(session.cwd))
                session.gitBranch?.takeIf { it.isNotBlank() }?.let { append("  ⎇ ").append(it) }
                append("  ·  ").append(relativeTime(session.mtime))
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetail(
    uiState: HeelCodeUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = uiState.selected ?: return
    var input by remember(session.id) { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Auto-scroll the transcript/live area to the bottom as new output streams in.
    LaunchedEffect(uiState.liveTurns, uiState.transcript) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.title.ifBlank { basename(session.cwd) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (uiState.isRunning) "running…" else basename(session.cwd),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isDetailLoading && uiState.transcript.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        uiState.transcript.forEach { msg -> TranscriptTurn(msg) }
                        // Live turns render with the same USER/ASSISTANT labels as history.
                        uiState.liveTurns.forEach { msg -> TranscriptTurn(msg) }
                        if (uiState.isRunning) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "working…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            uiState.detailError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Continue this session…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                val canSend = input.isNotBlankTrimmed() && !uiState.isSending && session.online
                Surface(
                    onClick = {
                        if (canSend) {
                            onSend(input)
                            input = ""
                        }
                    },
                    enabled = canSend,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp).padding(top = 4.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (uiState.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptTurn(msg: RemoteMessage) {
    val isUser = msg.role.equals("user", ignoreCase = true)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = msg.role.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = msg.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// --- helpers ---

private fun String.isNotBlankTrimmed(): Boolean = trim().isNotEmpty()

private fun basename(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }

/** Coarse "3m ago" style label from an epoch-millis timestamp. */
private fun relativeTime(mtime: Long): String {
    if (mtime <= 0L) return ""
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - mtime
    if (diff < 0) return "just now"
    val mins = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> "${days / 30}mo ago"
    }
}

package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

/**
 * A HeelCode coding-agent session discovered on one of the user's machines, surfaced by the
 * remote-control hub at `<server>/rc/local-sessions`. Mirrors the JSON shape returned there:
 * `{ id, cwd, gitBranch?, title, mtime, machineId, machineName, online }`.
 */
@Serializable
data class RemoteSession(
    val id: String,
    /** Absolute working directory of the session on its owning machine. */
    val cwd: String,
    /** Current git branch, or null when the cwd isn't a git work tree. */
    val gitBranch: String? = null,
    val title: String,
    /** Last-modified epoch millis — used for the relative-time label and list ordering. */
    val mtime: Long = 0,
    val machineId: String,
    val machineName: String,
    /** Whether the owning machine is currently connected to the hub (can accept a continue). */
    val online: Boolean = false,
)

/** Full transcript of a session, returned by `<server>/rc/{id}/history`. */
@Serializable
data class RemoteHistory(
    val id: String,
    val cwd: String,
    val messages: List<RemoteMessage> = emptyList(),
)

/** One turn in a session transcript. [role] is e.g. "user" / "assistant" / "tool". */
@Serializable
data class RemoteMessage(
    val role: String,
    val text: String,
)

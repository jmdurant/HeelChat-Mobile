package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.RemoteHistory
import com.garfiec.librechat.core.model.RemoteSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Live frame from the `/rc/{id}/events` SSE stream. */
sealed interface RemoteEvent {
    /** A chunk of agent/user output. [role] is "user" for echoed user turns, null for agent output. */
    data class Output(val time: Long, val text: String, val role: String?) : RemoteEvent

    /** Run status transitions. [running] true while the agent is working, false once idle. */
    data class Status(val running: Boolean) : RemoteEvent
}

@Serializable
private data class ContinueRequest(val text: String)

@Serializable
private data class ContinueResponse(val ok: Boolean = false)

// SSE `data:` payload shapes (kept private — the API surface exposes RemoteEvent only).
@Serializable
private data class OutFrame(val t: Long = 0, val text: String = "", val role: String? = null)

@Serializable
private data class StatusFrame(val status: String = "")

/**
 * HeelCode Remote — drives the remote-control hub mounted at the SAME origin as the HeelChat
 * server under the `/rc/` paths. Reuses the shared authenticated [HttpClient] (base URL + LibreChat JWT
 * are already attached by the client's defaultRequest + auth interceptor), so a relative
 * `path("rc/...")` resolves to `<server>/rc/...` with the bearer token in place.
 */
class RemoteApi constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun listSessions(): List<RemoteSession> =
        client.get {
            url { path("rc/local-sessions") }
        }.body()

    suspend fun history(id: String): RemoteHistory =
        client.get {
            url { path("rc/$id/history") }
        }.body()

    /** Starts a run on the session's owning machine. Returns the hub's ok flag. */
    suspend fun continueSession(id: String, text: String): Boolean =
        client.post {
            url { path("rc/$id/continue") }
            setBody(ContinueRequest(text = text))
        }.body<ContinueResponse>().ok

    /**
     * Streams the `/rc/{id}/events` SSE feed as a cold [Flow] of [RemoteEvent]s. Reads the
     * response body channel line-by-line, accumulating `event:`/`data:` lines and emitting one
     * event per blank-line-terminated frame. Completes when a `status: idle` frame arrives or
     * the stream closes.
     *
     * Uses a per-request timeout override (the shared client's default 30s request timeout is far
     * too short for a long-lived stream); the connection stays open until the run goes idle.
     */
    fun events(id: String): Flow<RemoteEvent> = flow {
        client.prepareGet {
            url { path("rc/$id/events") }
            accept(ContentType.Text.EventStream)
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            if (!response.status.isSuccess()) return@execute
            val channel = response.bodyAsChannel()
            var event = ""
            val data = StringBuilder()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith(":") -> Unit // keepalive comment
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trim())
                    }
                    line.isBlank() -> {
                        if (data.isNotEmpty()) {
                            val mapped = parseFrame(event, data.toString())
                            if (mapped != null) {
                                emit(mapped)
                                // Stop once the run reports idle — the agent is done.
                                if (mapped is RemoteEvent.Status && !mapped.running) return@execute
                            }
                        }
                        event = ""
                        data.clear()
                    }
                }
            }
        }
    }

    private fun parseFrame(event: String, data: String): RemoteEvent? = try {
        when (event) {
            "out" -> json.decodeFromString(OutFrame.serializer(), data).let {
                RemoteEvent.Output(time = it.t, text = it.text, role = it.role)
            }
            "status" -> json.decodeFromString(StatusFrame.serializer(), data).let {
                RemoteEvent.Status(running = it.status == "running")
            }
            else -> null
        }
    } catch (e: Exception) {
        // A malformed frame must not tear down the whole stream — skip it.
        null
    }
}

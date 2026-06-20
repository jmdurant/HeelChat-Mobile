package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.RemoteHistory
import com.garfiec.librechat.core.model.RemoteSession
import com.garfiec.librechat.core.network.api.RemoteEvent
import kotlinx.coroutines.flow.Flow

/**
 * Read/continue HeelCode coding-agent sessions through the remote-control hub at `<server>/rc/*`.
 * One-shot reads return [Result]; the live event feed is a cold [Flow] the caller collects.
 */
interface RemoteRepository {
    suspend fun listSessions(): Result<List<RemoteSession>>

    suspend fun history(id: String): Result<RemoteHistory>

    suspend fun continueSession(id: String, text: String): Result<Boolean>

    fun events(id: String): Flow<RemoteEvent>
}

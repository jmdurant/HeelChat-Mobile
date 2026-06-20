package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.RemoteHistory
import com.garfiec.librechat.core.model.RemoteSession
import com.garfiec.librechat.core.network.api.RemoteApi
import com.garfiec.librechat.core.network.api.RemoteEvent
import kotlinx.coroutines.flow.Flow

class RemoteRepositoryImpl(
    private val remoteApi: RemoteApi,
) : RemoteRepository {

    override suspend fun listSessions(): Result<List<RemoteSession>> = safeApiCall {
        remoteApi.listSessions()
    }

    override suspend fun history(id: String): Result<RemoteHistory> = safeApiCall {
        remoteApi.history(id)
    }

    override suspend fun continueSession(id: String, text: String): Result<Boolean> = safeApiCall {
        remoteApi.continueSession(id, text)
    }

    override fun events(id: String): Flow<RemoteEvent> = remoteApi.events(id)
}

package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.path

/**
 * Token balance — points at the PromptLab proxy's `/balance` route (the REAL UNC PromptLab
 * credits), which runs on the same host as the HeelChat (LibreChat) server but on port 8788.
 *
 * NOT LibreChat's own `/api/balance`: that's intentionally disabled in the HeelChat backend
 * (it tracks LibreChat's internal counter, mis-counts the unlimited mini, and blocks at zero).
 * The proxy returns the genuine remaining balance, where the free mini correctly doesn't move it.
 */
class BalanceApi constructor(
    private val client: HttpClient,
    private val serverUrlProvider: ServerUrlProvider,
) {
    suspend fun getBalance(): Balance {
        val server = URLBuilder(serverUrlProvider.awaitBaseUrl())
        // Override every URL component explicitly so the client's defaultRequest base URL
        // (the LibreChat server) doesn't bleed through — same host, proxy port, /balance.
        return client.get {
            url {
                protocol = server.protocol
                host = server.host
                port = PROMPTLAB_PROXY_PORT
                path("balance")
            }
        }.body()
    }

    private companion object {
        const val PROMPTLAB_PROXY_PORT = 8788
    }
}

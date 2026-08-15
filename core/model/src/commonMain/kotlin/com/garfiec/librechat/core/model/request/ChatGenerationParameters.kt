package com.garfiec.librechat.core.model.request

/**
 * Request-relevant generation settings selected in the chat UI.
 *
 * Kept in :core:model so the data layer does not depend on Compose UI state.
 */
data class ChatGenerationParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val maxContextTokens: Int? = null,
    val system: String? = null,
    val reasoningEffort: String? = null,
    val effort: String? = null,
    val thinkingLevel: String? = null,
    val stop: List<String>? = null,
    val promptPrefix: String? = null,
    val modelLabel: String? = null,
    val maxTokens: Int? = null,
    val resendFiles: Boolean? = null,
    val imageDetail: String? = null,
)
package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Balance(
    val tokenCredits: Long = 0,
    /** PromptLab's daily refill ceiling (e.g. 1_500_000), so the UI can show "used / total". */
    val refillAmount: Long = 0,
)

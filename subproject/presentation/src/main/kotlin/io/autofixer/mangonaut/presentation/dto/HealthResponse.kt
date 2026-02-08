package io.autofixer.mangonaut.presentation.dto

/**
 * Health Check response DTO.
 */
data class HealthResponse(
    val status: String,
    val components: Map<String, ComponentHealth>,
)

data class ComponentHealth(
    val status: String,
    val details: Map<String, Any>? = null,
)

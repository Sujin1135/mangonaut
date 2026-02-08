package io.autofixer.mangonaut.presentation.dto

/**
 * Webhook processing response DTO.
 */
data class WebhookResponse(
    val status: String,
    val message: String,
    val data: WebhookResponseData? = null,
)

data class WebhookResponseData(
    val issueId: String,
    val analysisCompleted: Boolean,
    val prUrl: String? = null,
)

/**
 * Error response DTO.
 */
data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val details: Map<String, Any>? = null,
)

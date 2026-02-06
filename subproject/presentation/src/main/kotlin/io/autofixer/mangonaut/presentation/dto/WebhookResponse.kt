package io.autofixer.mangonaut.presentation.dto

/**
 * Webhook 처리 응답 DTO
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
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val details: Map<String, Any>? = null,
)

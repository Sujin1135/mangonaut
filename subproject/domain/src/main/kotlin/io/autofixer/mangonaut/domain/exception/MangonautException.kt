package io.autofixer.mangonaut.domain.exception

import io.autofixer.mangonaut.domain.model.ErrorEvent

/**
 * Mangonaut 애플리케이션의 기본 예외 클래스
 */
sealed class MangonautException(
    open val errorCode: ErrorCode,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Sentry API 관련 예외
 */
data class SentryApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.SENTRY_API_ERROR, message, cause)

/**
 * GitHub API 관련 예외
 */
data class GitHubApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.GITHUB_API_ERROR, message, cause)

/**
 * LLM API 관련 예외
 */
data class LlmApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.LLM_API_ERROR, message, cause)

/**
 * Webhook 검증 실패 예외
 */
data class WebhookValidationException(
    override val message: String,
) : MangonautException(ErrorCode.WEBHOOK_VALIDATION_ERROR, message)

/**
 * 설정 오류 예외
 */
data class ConfigurationException(
    override val message: String,
) : MangonautException(ErrorCode.CONFIGURATION_ERROR, message)

/**
 * 중복 처리 방지 예외 (이미 처리 중인 이슈)
 */
data class DuplicateProcessingException(
    val issueId: ErrorEvent.Id,
) : MangonautException(ErrorCode.DUPLICATE_PROCESSING, "Issue ${issueId.value} is already being processed")

/**
 * 리소스를 찾을 수 없음 예외
 */
data class ResourceNotFoundException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.RESOURCE_NOT_FOUND, message, cause)

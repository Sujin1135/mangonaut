package io.autofixer.mangonaut.domain.exception

import io.autofixer.mangonaut.domain.model.ErrorEvent

/**
 * Base exception class for the Mangonaut application.
 */
sealed class MangonautException(
    open val errorCode: ErrorCode,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Sentry API exception.
 */
data class SentryApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.SENTRY_API_ERROR, message, cause)

/**
 * GitHub API exception.
 */
data class GitHubApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.GITHUB_API_ERROR, message, cause)

/**
 * LLM API exception.
 */
data class LlmApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.LLM_API_ERROR, message, cause)

/**
 * Webhook validation failure exception.
 */
data class WebhookValidationException(
    override val message: String,
) : MangonautException(ErrorCode.WEBHOOK_VALIDATION_ERROR, message)

/**
 * Configuration error exception.
 */
data class ConfigurationException(
    override val message: String,
) : MangonautException(ErrorCode.CONFIGURATION_ERROR, message)

/**
 * Duplicate processing prevention exception (issue already being processed).
 */
data class DuplicateProcessingException(
    val issueId: ErrorEvent.Id,
) : MangonautException(ErrorCode.DUPLICATE_PROCESSING, "Issue ${issueId.value} is already being processed")

/**
 * Resource not found exception.
 */
data class ResourceNotFoundException(
    override val message: String,
    override val cause: Throwable? = null,
) : MangonautException(ErrorCode.RESOURCE_NOT_FOUND, message, cause)

/**
 * Sentinel exception thrown when commitFiles has no committable changes
 * remaining after applying the path-resolution guard (every change was skipped).
 *
 * Lets callers distinguish "no work was committed" from a successful commit so
 * they can avoid creating an empty branch/PR (which GitHub rejects with 422).
 */
data class NoCommittableChangesException(
    override val message: String,
) : MangonautException(ErrorCode.GITHUB_NO_COMMITTABLE_CHANGES, message)

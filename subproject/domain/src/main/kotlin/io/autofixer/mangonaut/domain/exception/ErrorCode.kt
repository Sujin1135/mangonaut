package io.autofixer.mangonaut.domain.exception

enum class ErrorCode(
    val code: String,
    val description: String,
) {
    // Sentry
    SENTRY_API_ERROR("SENTRY_001", "Sentry API call failed"),
    SENTRY_EVENT_NOT_FOUND("SENTRY_002", "Sentry event not found"),
    SENTRY_PARSE_ERROR("SENTRY_003", "Sentry response parse failed"),

    // GitHub
    GITHUB_API_ERROR("GITHUB_001", "GitHub API call failed"),
    GITHUB_FILE_NOT_FOUND("GITHUB_002", "GitHub file not found"),
    GITHUB_BRANCH_EXISTS("GITHUB_003", "Branch already exists"),
    GITHUB_PR_CREATE_FAILED("GITHUB_004", "PR creation failed"),

    // LLM
    LLM_API_ERROR("LLM_001", "LLM API call failed"),
    LLM_PARSE_ERROR("LLM_002", "LLM response parse failed"),
    LLM_RATE_LIMITED("LLM_003", "LLM API rate limit exceeded"),

    // Webhook
    WEBHOOK_VALIDATION_ERROR("WEBHOOK_001", "Webhook signature verification failed"),
    WEBHOOK_PARSE_ERROR("WEBHOOK_002", "Webhook payload parse failed"),

    // General
    CONFIGURATION_ERROR("CONFIG_001", "Configuration error"),
    DUPLICATE_PROCESSING("PROCESS_001", "Duplicate processing attempt"),
    RESOURCE_NOT_FOUND("RESOURCE_001", "Resource not found"),
    INTERNAL_ERROR("INTERNAL_001", "Internal server error"),
}

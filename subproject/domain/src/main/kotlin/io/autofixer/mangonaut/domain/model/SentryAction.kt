package io.autofixer.mangonaut.domain.model

/**
 * Sentry webhook `action` field, shared by presentation DTOs and domain logic.
 *
 * Constants are uppercase by Kotlin convention; Jackson is configured to map
 * Sentry's lowercase string values case-insensitively. Unknown values are
 * deserialized as `null`, so callers must treat the action as nullable.
 */
enum class SentryAction {
    CREATED,
    RESOLVED,
    ASSIGNED,
    IGNORED,
    ARCHIVED,
    TRIGGERED,
    ;

    val isProcessable: Boolean
        get() = this == CREATED || this == TRIGGERED
}

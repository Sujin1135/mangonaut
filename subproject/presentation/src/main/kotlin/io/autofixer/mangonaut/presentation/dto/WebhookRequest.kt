package io.autofixer.mangonaut.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Sentry Webhook request DTO.
 *
 * Webhook payload sent by Sentry when an issue occurs.
 */
data class SentryWebhookRequest(
    val action: String,
    val data: SentryIssueData,
    val installation: SentryInstallation?,
)

data class SentryIssueData(
    val issue: SentryIssue,
)

data class SentryIssue(
    val id: String,
    val title: String,
    val project: SentryProject,
    @JsonProperty("shortId")
    val shortId: String,
    val level: String?,
    val status: String?,
)

data class SentryProject(
    val id: String,
    val name: String,
    val slug: String,
)

data class SentryInstallation(
    val uuid: String,
)

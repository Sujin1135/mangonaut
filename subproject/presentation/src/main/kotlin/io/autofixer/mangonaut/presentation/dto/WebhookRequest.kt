package io.autofixer.mangonaut.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Sentry Webhook 요청 DTO
 *
 * Sentry에서 이슈가 발생했을 때 전송되는 webhook payload
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

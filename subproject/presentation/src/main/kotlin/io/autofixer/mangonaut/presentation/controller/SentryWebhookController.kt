package io.autofixer.mangonaut.presentation.controller

import io.autofixer.mangonaut.application.usecase.ProcessErrorAlertUseCase
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.SentryWebhookRequest
import io.autofixer.mangonaut.presentation.dto.WebhookResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

@RestController
@RequestMapping("/webhooks/sentry")
class SentryWebhookController(
    private val processErrorAlertUseCase: ProcessErrorAlertUseCase,
    private val webhookProcessingScope: CoroutineScope,
    private val objectMapper: ObjectMapper,
    @Value("\${mangonaut.sentry.webhook-secret}")
    private val webhookSecret: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun handleWebhook(
        @RequestHeader("Sentry-Hook-Signature") signature: String,
        @RequestHeader("Sentry-Hook-Resource") resource: String,
        @RequestBody rawBody: String,
    ): ResponseEntity<WebhookResponse> {
        webhookProcessingScope.launch {
            runCatching { processWebhook(rawBody, signature, resource) }
                .onFailure { logger.error("Failed to process Sentry webhook", it) }
        }

        return ResponseEntity.ok(
            WebhookResponse(
                status = "accepted",
                message = "Webhook received, processing asynchronously",
            )
        )
    }

    private suspend fun processWebhook(rawBody: String, signature: String, resource: String) {
        val request = SentryWebhookRequest.from(rawBody, signature, webhookSecret) {
            objectMapper.readValue(it, SentryWebhookRequest::class.java)
        }
        logger.info("Received Sentry webhook: action={}, resource={}", request.action, resource)

        if (request.action?.isProcessable != true) {
            logger.info("Ignoring webhook action: {}", request.action)
            return
        }

        val issue = request.data.issue
        processErrorAlertUseCase(
            ProcessErrorAlertUseCase.Params(
                issueId = ErrorEvent.Id(issue.id),
                sourceProject = ErrorEvent.SourceProject(issue.project.slug),
            )
        )
    }
}

package io.autofixer.mangonaut.presentation.controller

import tools.jackson.databind.ObjectMapper
import io.autofixer.mangonaut.application.usecase.ProcessErrorAlertUseCase
import io.autofixer.mangonaut.domain.exception.WebhookValidationException
import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.presentation.dto.SentryWebhookRequest
import io.autofixer.mangonaut.presentation.dto.WebhookResponse
import io.autofixer.mangonaut.presentation.service.ProjectMappingService
import io.autofixer.mangonaut.presentation.service.WebhookVerificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks/sentry")
class SentryWebhookController(
    private val webhookVerificationService: WebhookVerificationService,
    private val projectMappingService: ProjectMappingService,
    private val processErrorAlertUseCase: ProcessErrorAlertUseCase,
    private val webhookProcessingScope: CoroutineScope,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping
    suspend fun handleWebhook(
        @RequestHeader("Sentry-Hook-Signature") signature: String,
        @RequestHeader("Sentry-Hook-Resource") resource: String,
        @RequestBody rawBody: String,
    ): ResponseEntity<WebhookResponse> {
        // 서명 검증
        if (!webhookVerificationService.verifySentrySignature(rawBody, signature)) {
            throw WebhookValidationException("Invalid webhook signature")
        }

        val request = objectMapper.readValue(rawBody, SentryWebhookRequest::class.java)
        logger.info("Received Sentry webhook: action={}, resource={}", request.action, resource)

        // 지원하는 이벤트 타입인지 확인
        if (request.action != "created" && request.action != "triggered") {
            logger.info("Ignoring webhook action: {}", request.action)
            return ResponseEntity.ok(
                WebhookResponse(
                    status = "ignored",
                    message = "Action not supported: ${request.action}",
                )
            )
        }

        // 프로젝트 매핑 조회
        val projectSlug = request.data.issue.project.slug
        val mapping = projectMappingService.findMapping(projectSlug)
            ?: return ResponseEntity.ok(
                WebhookResponse(
                    status = "ignored",
                    message = "No mapping configured for project: $projectSlug",
                )
            )

        // 비동기 처리 시작
        val issueId = request.data.issue.id
        webhookProcessingScope.launch {
            try {
                processErrorAlertUseCase(
                    ProcessErrorAlertUseCase.Params(
                        issueId = ErrorEvent.Id(issueId),
                        sourceProject = ErrorEvent.SourceProject(projectSlug),
                        repoId = RepoId.of(mapping.scmRepo),
                        defaultBranch = mapping.defaultBranch,
                        sourceRoot = mapping.sourceRoot,
                        branchPrefix = mapping.branchPrefix,
                        labels = mapping.labels,
                        minConfidence = Confidence.valueOf(mapping.minConfidence),
                        autoPr = mapping.autoPr,
                    )
                )
                logger.info("Successfully processed error alert for issue {}", issueId)
            } catch (e: Exception) {
                logger.error("Failed to process error alert for issue {}", issueId, e)
            }
        }

        // 즉시 200 OK 반환
        return ResponseEntity.ok(
            WebhookResponse(
                status = "accepted",
                message = "Webhook received, processing asynchronously",
            )
        )
    }
}

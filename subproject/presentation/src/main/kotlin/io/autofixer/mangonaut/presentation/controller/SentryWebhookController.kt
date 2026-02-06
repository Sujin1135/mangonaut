package io.autofixer.mangonaut.presentation.controller

import io.autofixer.mangonaut.application.usecase.ProcessErrorAlertUseCase
import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.presentation.dto.SentryWebhookRequest
import io.autofixer.mangonaut.presentation.dto.WebhookResponse
import io.autofixer.mangonaut.presentation.dto.WebhookResponseData
import io.autofixer.mangonaut.presentation.service.ProjectMappingService
import io.autofixer.mangonaut.presentation.service.WebhookVerificationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Sentry Webhook 수신 컨트롤러
 */
@RestController
@RequestMapping("/webhooks/sentry")
class SentryWebhookController(
    private val webhookVerificationService: WebhookVerificationService,
    private val projectMappingService: ProjectMappingService,
    private val processErrorAlertUseCase: ProcessErrorAlertUseCase,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping
    suspend fun handleWebhook(
        @RequestHeader("Sentry-Hook-Signature") signature: String,
        @RequestHeader("Sentry-Hook-Resource") resource: String,
        @RequestBody rawBody: String,
        @RequestBody request: SentryWebhookRequest,
    ): ResponseEntity<WebhookResponse> {
        logger.info("Received Sentry webhook: action={}, resource={}", request.action, resource)

        // 서명 검증
        if (!webhookVerificationService.verifySentrySignature(rawBody, signature)) {
            logger.warn("Invalid webhook signature")
            return ResponseEntity.status(401).body(
                WebhookResponse(
                    status = "error",
                    message = "Invalid signature",
                )
            )
        }

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

        // 에러 처리 수행
        val result = processErrorAlertUseCase(
            ProcessErrorAlertUseCase.Params(
                issueId = ErrorEvent.Id(request.data.issue.id),
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

        return ResponseEntity.ok(
            WebhookResponse(
                status = "success",
                message = "Error processed successfully",
                data = WebhookResponseData(
                    issueId = result.errorEvent.id.value,
                    analysisCompleted = result.analysisCompleted,
                    prUrl = result.prResult?.htmlUrl?.value,
                ),
            )
        )
    }
}

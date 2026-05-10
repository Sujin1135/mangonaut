package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import io.autofixer.mangonaut.domain.port.ProjectMappingPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Use Case that orchestrates the entire error alert processing pipeline.
 *
 * 1. Resolve project mapping
 * 2. Fetch detailed event from the error source
 * 3. Perform error analysis
 * 4. Create PR (based on mapping configuration)
 *
 * Returns `null` when no mapping is configured for the source project.
 */
@Service
class ProcessErrorAlertUseCase(
    private val projectMappingPort: ProjectMappingPort,
    private val errorSourcePort: ErrorSourcePort,
    private val analyzeErrorUseCase: AnalyzeErrorUseCase,
    private val createFixPullRequestUseCase: CreateFixPullRequestUseCase,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class Params(
        val issueId: ErrorEvent.Id,
        val sourceProject: ErrorEvent.SourceProject,
    )

    data class Result(
        val errorEvent: ErrorEvent,
        val analysisCompleted: Boolean,
        val prResult: PrResult?,
    )

    suspend operator fun invoke(params: Params): Result? {
        logger.info("Processing error alert: issueId={}, project={}", params.issueId.value, params.sourceProject.value)

        val mapping = projectMappingPort(ProjectMappingPort.Params(params.sourceProject))
        if (mapping == null) {
            logger.info("No mapping configured for project: {}", params.sourceProject.value)
            return null
        }

        val errorEvent = errorSourcePort.fetchEvent(params.issueId)

        logger.info(
            "Fetched error event: issueId={}, errorType={}",
            errorEvent.id.value,
            errorEvent.errorType.value,
        )

        val fixResult = analyzeErrorUseCase(
            AnalyzeErrorUseCase.Params(
                errorEvent = errorEvent,
                repoId = RepoId.of(mapping.scmRepo),
                defaultBranch = mapping.defaultBranch,
            )
        )

        logger.info(
            "Analysis result: issueId={}, confidence={}, changes={}",
            errorEvent.id.value,
            fixResult.confidence,
            fixResult.changes.size,
        )

        if (!mapping.autoPr) {
            logger.info("Auto PR disabled, skipping PR creation")
            return null
        }

        val prResult = createFixPullRequestUseCase(
            CreateFixPullRequestUseCase.Params(
                errorEvent = errorEvent,
                fixResult = fixResult,
                repoId = RepoId.of(mapping.scmRepo),
                defaultBranch = mapping.defaultBranch,
                branchPrefix = mapping.branchPrefix,
                labels = mapping.labels,
                minConfidence = mapping.minConfidence,
            )
        )

        return Result(
            errorEvent = errorEvent,
            analysisCompleted = true,
            prResult = prResult,
        )
    }
}

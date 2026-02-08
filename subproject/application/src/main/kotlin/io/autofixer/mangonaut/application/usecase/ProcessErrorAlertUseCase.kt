package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Use Case that orchestrates the entire error alert processing pipeline.
 *
 * 1. Fetch detailed event from the error source
 * 2. Perform error analysis
 * 3. Create PR (based on configuration)
 */
@Service
class ProcessErrorAlertUseCase(
    private val errorSourcePort: ErrorSourcePort,
    private val analyzeErrorUseCase: AnalyzeErrorUseCase,
    private val createFixPullRequestUseCase: CreateFixPullRequestUseCase,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class Params(
        val issueId: ErrorEvent.Id,
        val sourceProject: ErrorEvent.SourceProject,
        val repoId: RepoId,
        val defaultBranch: String,
        val sourceRoots: List<String>,
        val branchPrefix: String,
        val labels: List<String>,
        val minConfidence: Confidence,
        val autoPr: Boolean,
    )

    data class Result(
        val errorEvent: ErrorEvent,
        val analysisCompleted: Boolean,
        val prResult: PrResult?,
    )

    /**
     * Processes an error alert.
     */
    suspend operator fun invoke(params: Params): Result {
        logger.info("Processing error alert: issueId={}, project={}", params.issueId.value, params.sourceProject.value)

        // 1. Fetch error details
        val errorEvent = errorSourcePort.fetchEvent(params.issueId)
        logger.info("Fetched error event: title={}", errorEvent.title.value)

        // 2. Analyze error
        val fixResult = analyzeErrorUseCase(
            AnalyzeErrorUseCase.Params(
                errorEvent = errorEvent,
                repoId = params.repoId,
                defaultBranch = params.defaultBranch,
                sourceRoots = params.sourceRoots,
            )
        )
        logger.info(
            "Analysis completed: confidence={}, changes={}",
            fixResult.confidence,
            fixResult.changes.size,
        )

        // 3. Create PR (based on configuration)
        val prResult = if (params.autoPr) {
            createFixPullRequestUseCase(
                CreateFixPullRequestUseCase.Params(
                    errorEvent = errorEvent,
                    fixResult = fixResult,
                    repoId = params.repoId,
                    defaultBranch = params.defaultBranch,
                    branchPrefix = params.branchPrefix,
                    labels = params.labels,
                    minConfidence = params.minConfidence,
                )
            )
        } else {
            logger.info("Auto PR disabled, skipping PR creation")
            null
        }

        prResult?.let {
            logger.info("PR created: url={}", it.htmlUrl.value)
        }

        return Result(
            errorEvent = errorEvent,
            analysisCompleted = true,
            prResult = prResult,
        )
    }
}

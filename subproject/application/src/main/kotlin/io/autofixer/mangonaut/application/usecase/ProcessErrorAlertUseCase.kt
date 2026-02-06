package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 에러 알림 처리 전체 파이프라인을 조율하는 Use Case (Orchestrator)
 *
 * 1. 에러 소스에서 상세 이벤트 조회
 * 2. 에러 분석 수행
 * 3. PR 생성 (설정에 따라)
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
        val sourceRoot: String,
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
     * 에러 알림을 처리합니다.
     */
    suspend operator fun invoke(params: Params): Result {
        logger.info("Processing error alert: issueId={}, project={}", params.issueId.value, params.sourceProject.value)

        // 1. 에러 상세 조회
        val errorEvent = errorSourcePort.fetchEvent(params.issueId)
        logger.info("Fetched error event: title={}", errorEvent.title.value)

        // 2. 에러 분석
        val fixResult = analyzeErrorUseCase(
            AnalyzeErrorUseCase.Params(
                errorEvent = errorEvent,
                repoId = params.repoId,
                defaultBranch = params.defaultBranch,
                sourceRoot = params.sourceRoot,
            )
        )
        logger.info(
            "Analysis completed: confidence={}, changes={}",
            fixResult.confidence,
            fixResult.changes.size,
        )

        // 3. PR 생성 (설정에 따라)
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

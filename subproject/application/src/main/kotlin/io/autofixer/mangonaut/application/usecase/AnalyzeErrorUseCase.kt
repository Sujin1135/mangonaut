package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FixResult
import io.autofixer.mangonaut.domain.model.RepoContext
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Use Case that analyzes error events and generates fix suggestions.
 *
 * Stage 3 (agentic): no longer pre-fetches source files. The LLM provider
 * receives the full ErrorEvent and a RepoContext, and drives codebase
 * exploration itself via tool calls.
 */
@Service
class AnalyzeErrorUseCase(
    private val llmProviderPort: LlmProviderPort,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class Params(
        val errorEvent: ErrorEvent,
        val repoId: RepoId,
        val defaultBranch: String,
    )

    /**
     * Performs error analysis.
     */
    suspend operator fun invoke(params: Params): FixResult {
        val fixResult =
            llmProviderPort.analyzeError(
                errorEvent = params.errorEvent,
                repoContext = RepoContext.of(params.repoId, params.defaultBranch),
            )

        logger.info(
            "Analysis completed: confidence={}, changes={}",
            fixResult.confidence,
            fixResult.changes.size,
        )

        return fixResult
    }
}

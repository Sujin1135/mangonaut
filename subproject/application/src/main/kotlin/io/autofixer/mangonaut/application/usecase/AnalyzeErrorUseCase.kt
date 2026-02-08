package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FixResult
import io.autofixer.mangonaut.domain.model.StackFrame
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import org.springframework.stereotype.Service

/**
 * Use Case that analyzes error events and generates fix suggestions.
 *
 * 1. Extract relevant files from the error event's stack trace
 * 2. Fetch source code from SCM
 * 3. Request analysis from LLM to generate fix suggestions
 */
@Service
class AnalyzeErrorUseCase(
    private val scmProviderPort: ScmProviderPort,
    private val llmProviderPort: LlmProviderPort,
) {
    data class Params(
        val errorEvent: ErrorEvent,
        val repoId: io.autofixer.mangonaut.domain.model.RepoId,
        val defaultBranch: String,
        val sourceRoots: List<String>,
    )

    /**
     * Performs error analysis.
     */
    suspend operator fun invoke(params: Params): FixResult {
        val errorEvent = params.errorEvent
        val repoId = params.repoId

        // Extract application code frames only
        val appFrames = errorEvent.applicationStackFrames()

        // Fetch related source files
        val sourceFiles = fetchSourceFiles(
            repoId = repoId,
            frames = appFrames,
            ref = params.defaultBranch,
            sourceRoots = params.sourceRoots,
        )

        // Analyze via LLM
        return llmProviderPort.analyzeError(
            errorEvent = errorEvent,
            sourceFiles = sourceFiles,
        )
    }

    /**
     * Fetches source files referenced in the stack frames.
     */
    private suspend fun fetchSourceFiles(
        repoId: io.autofixer.mangonaut.domain.model.RepoId,
        frames: List<StackFrame>,
        ref: String,
        sourceRoots: List<String>,
    ): Map<String, String> {
        val uniqueFiles = frames
            .map { it.filename.value }
            .distinct()
            .take(MAX_FILES_TO_FETCH)

        return uniqueFiles.mapNotNull { filename ->
            sourceRoots.firstNotNullOfOrNull { sourceRoot ->
                runCatching {
                    val filePath = io.autofixer.mangonaut.domain.model.FileChange.FilePath(
                        "$sourceRoot$filename"
                    )
                    val content = scmProviderPort.getFileContent(repoId, filePath, ref)
                    filename to content
                }.getOrNull()
            }
        }.toMap()
    }

    companion object {
        private const val MAX_FILES_TO_FETCH = 10
    }
}

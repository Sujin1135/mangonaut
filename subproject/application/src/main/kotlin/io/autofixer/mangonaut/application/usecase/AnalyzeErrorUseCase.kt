package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FixResult
import io.autofixer.mangonaut.domain.model.StackFrame
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import org.springframework.stereotype.Service

/**
 * 에러 이벤트를 분석하여 수정 제안을 생성하는 Use Case
 *
 * 1. 에러 이벤트의 스택 트레이스에서 관련 파일 추출
 * 2. SCM에서 해당 파일들의 소스코드 조회
 * 3. LLM에 분석 요청하여 수정 제안 생성
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
        val sourceRoot: String,
    )

    /**
     * 에러 분석을 수행합니다.
     */
    suspend operator fun invoke(params: Params): FixResult {
        val errorEvent = params.errorEvent
        val repoId = params.repoId

        // 애플리케이션 코드 프레임만 추출
        val appFrames = errorEvent.applicationStackFrames()

        // 관련 소스 파일들 조회
        val sourceFiles = fetchSourceFiles(
            repoId = repoId,
            frames = appFrames,
            ref = params.defaultBranch,
            sourceRoot = params.sourceRoot,
        )

        // LLM을 통한 분석
        return llmProviderPort.analyzeError(
            errorEvent = errorEvent,
            sourceFiles = sourceFiles,
        )
    }

    /**
     * 스택 프레임에서 참조된 소스 파일들을 조회합니다.
     */
    private suspend fun fetchSourceFiles(
        repoId: io.autofixer.mangonaut.domain.model.RepoId,
        frames: List<StackFrame>,
        ref: String,
        sourceRoot: String,
    ): Map<String, String> {
        val uniqueFiles = frames
            .map { it.filename.value }
            .distinct()
            .take(MAX_FILES_TO_FETCH)

        return uniqueFiles.mapNotNull { filename ->
            runCatching {
                val filePath = io.autofixer.mangonaut.domain.model.FileChange.FilePath(
                    "$sourceRoot$filename"
                )
                val content = scmProviderPort.getFileContent(repoId, filePath, ref)
                filename to content
            }.getOrNull()
        }.toMap()
    }

    companion object {
        private const val MAX_FILES_TO_FETCH = 10
    }
}

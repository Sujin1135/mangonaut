package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FixResult

/**
 * LLM(Large Language Model) 프로바이더와의 통신을 위한 포트
 *
 * Claude, OpenAI, Ollama 등의 구현체를 Infrastructure 레이어에서 제공합니다.
 */
interface LlmProviderPort {
    /**
     * LLM 프로바이더의 식별자
     * 예: "claude", "openai", "ollama"
     */
    val name: String

    /**
     * 에러 이벤트와 관련 소스코드를 분석하여 수정 제안을 받습니다.
     *
     * @param errorEvent 분석할 에러 이벤트
     * @param sourceFiles 관련 소스 파일들 (파일경로 -> 내용)
     * @return 분석 결과 및 수정 제안
     */
    suspend fun analyzeError(
        errorEvent: ErrorEvent,
        sourceFiles: Map<String, String>,
    ): FixResult

    /**
     * LLM 프로바이더의 연결 상태를 확인합니다.
     *
     * @return 연결 성공 여부
     */
    suspend fun healthCheck(): Boolean
}

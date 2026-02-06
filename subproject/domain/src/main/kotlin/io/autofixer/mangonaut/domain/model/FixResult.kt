package io.autofixer.mangonaut.domain.model

/**
 * LLM 분석 결과를 나타내는 도메인 모델
 */
data class FixResult(
    val analysis: Analysis,
    val rootCause: RootCause,
    val confidence: Confidence,
    val changes: List<FileChange>,
    val prTitle: PrTitle,
    val prBody: PrBody,
) {
    @JvmInline
    value class Analysis(val value: String)

    @JvmInline
    value class RootCause(val value: String)

    @JvmInline
    value class PrTitle(val value: String)

    @JvmInline
    value class PrBody(val value: String)

    /**
     * 자동 PR 생성이 가능한지 확인
     */
    fun canCreateAutoPr(minConfidence: Confidence): Boolean =
        confidence.ordinal >= minConfidence.ordinal && changes.isNotEmpty()
}

/**
 * 코드 수정 사항
 */
data class FileChange(
    val file: FilePath,
    val description: Description,
    val original: OriginalContent,
    val modified: ModifiedContent,
) {
    @JvmInline
    value class FilePath(val value: String)

    @JvmInline
    value class Description(val value: String)

    @JvmInline
    value class OriginalContent(val value: String)

    @JvmInline
    value class ModifiedContent(val value: String)

    /**
     * 실제 변경이 있는지 확인
     */
    fun hasChanges(): Boolean = original.value != modified.value
}

/**
 * AI 분석의 신뢰도 수준
 */
enum class Confidence {
    /**
     * 낮은 신뢰도 - PR 미생성, 분석 결과만 로깅/알림
     */
    LOW,

    /**
     * 중간 신뢰도 - 설정에 따라 PR 생성 또는 로깅만
     */
    MEDIUM,

    /**
     * 높은 신뢰도 - 자동 PR 생성
     */
    HIGH,
}

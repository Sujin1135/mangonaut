package io.autofixer.mangonaut.domain.model

import java.time.Instant

/**
 * 에러 소스(Sentry, Datadog 등)에서 수신한 에러 이벤트의 표준화된 도메인 모델
 */
data class ErrorEvent(
    val id: Id,
    val title: Title,
    val errorType: ErrorType,
    val errorMessage: ErrorMessage,
    val stackTrace: List<StackFrame>,
    val breadcrumbs: List<Breadcrumb>,
    val tags: Map<String, String>,
    val request: RequestContext? = null,
    val release: Release? = null,
    val sourceProject: SourceProject,
    val timestamp: Instant,
) {
    @JvmInline
    value class Id(val value: String)

    @JvmInline
    value class Title(val value: String)

    @JvmInline
    value class ErrorType(val value: String)

    @JvmInline
    value class ErrorMessage(val value: String)

    @JvmInline
    value class Release(val value: String)

    @JvmInline
    value class SourceProject(val value: String)

    /**
     * 애플리케이션 코드에서 발생한 스택 프레임만 필터링
     */
    fun applicationStackFrames(): List<StackFrame> =
        stackTrace.filter { it.isApplicationCode() }
}

/**
 * 스택 트레이스의 개별 프레임
 */
data class StackFrame(
    val filename: Filename,
    val function: FunctionName,
    val lineNo: LineNumber,
    val colNo: ColumnNumber? = null,
    val preContext: List<String> = emptyList(),
    val contextLine: ContextLine? = null,
    val postContext: List<String> = emptyList(),
    val inApp: InApp,
) {
    @JvmInline
    value class Filename(val value: String)

    @JvmInline
    value class FunctionName(val value: String)

    @JvmInline
    value class LineNumber(val value: Int)

    @JvmInline
    value class ColumnNumber(val value: Int)

    @JvmInline
    value class ContextLine(val value: String)

    @JvmInline
    value class InApp(val value: Boolean)

    /**
     * 우리 애플리케이션 코드인지 확인 (라이브러리 코드 제외)
     */
    fun isApplicationCode(): Boolean = inApp.value
}

/**
 * 에러 발생 전 사용자/시스템 행동 기록
 */
data class Breadcrumb(
    val timestamp: Instant,
    val category: Category?,
    val message: Message?,
    val level: Level?,
    val type: Type?,
    val data: Map<String, Any>? = null,
) {
    @JvmInline
    value class Category(val value: String)

    @JvmInline
    value class Message(val value: String)

    @JvmInline
    value class Level(val value: String)

    @JvmInline
    value class Type(val value: String)
}

/**
 * HTTP 요청 컨텍스트 (웹 에러인 경우)
 */
data class RequestContext(
    val url: Url?,
    val method: Method?,
    val headers: Map<String, String>? = null,
    val queryString: QueryString? = null,
    val data: Map<String, Any>? = null,
) {
    @JvmInline
    value class Url(val value: String)

    @JvmInline
    value class Method(val value: String)

    @JvmInline
    value class QueryString(val value: String)
}

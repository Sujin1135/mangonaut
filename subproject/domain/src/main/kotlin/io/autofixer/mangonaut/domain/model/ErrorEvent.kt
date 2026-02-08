package io.autofixer.mangonaut.domain.model

import java.time.Instant

/**
 * Standardized domain model for error events received from error sources (Sentry, Datadog, etc.).
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
     * Filters only stack frames originating from application code.
     */
    fun applicationStackFrames(): List<StackFrame> =
        stackTrace.filter { it.isApplicationCode() }
}

/**
 * Individual frame within a stack trace.
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
     * Checks whether this frame is application code (excludes library code).
     */
    fun isApplicationCode(): Boolean = inApp.value
}

/**
 * Record of user/system actions before the error occurred.
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
 * HTTP request context (for web errors).
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

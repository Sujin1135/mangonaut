package io.autofixer.mangonaut.infrastructure.adapter

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.autofixer.mangonaut.domain.exception.SentryApiException
import io.autofixer.mangonaut.domain.model.Breadcrumb
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.RequestContext
import io.autofixer.mangonaut.domain.model.StackFrame
import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant

/**
 * Sentry API를 사용한 ErrorSourcePort 구현체
 */
@Component
class SentryErrorSourceAdapter(
    private val sentryWebClient: WebClient,
    private val properties: MangonautProperties,
) : ErrorSourcePort {

    override val name: String = "sentry"

    override suspend fun fetchEvent(issueId: ErrorEvent.Id): ErrorEvent {
        try {
            val response = sentryWebClient
                .get()
                .uri("/api/0/organizations/${properties.sentry.org}/issues/${issueId.value}/events/latest/")
                .retrieve()
                .bodyToMono(SentryEventResponse::class.java)
                .awaitSingle()

            return response.toDomain(issueId)
        } catch (e: WebClientResponseException) {
            throw SentryApiException(
                message = "Failed to fetch event from Sentry: ${e.statusCode} - ${e.responseBodyAsString}",
                cause = e,
            )
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            sentryWebClient
                .get()
                .uri("/api/0/")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sentry API 응답 -> 도메인 모델 변환
     */
    private fun SentryEventResponse.toDomain(issueId: ErrorEvent.Id): ErrorEvent {
        val exception = this.entries
            ?.filterIsInstance<SentryExceptionEntry>()
            ?.firstOrNull()
            ?.data
            ?.values
            ?.firstOrNull()

        return ErrorEvent(
            id = issueId,
            title = ErrorEvent.Title(this.title ?: "Unknown Error"),
            errorType = ErrorEvent.ErrorType(exception?.type ?: "Exception"),
            errorMessage = ErrorEvent.ErrorMessage(exception?.value ?: this.message ?: ""),
            stackTrace = exception?.stacktrace?.frames?.map { it.toDomain() } ?: emptyList(),
            breadcrumbs = this.entries
                ?.filterIsInstance<SentryBreadcrumbEntry>()
                ?.firstOrNull()
                ?.data
                ?.values
                ?.map { it.toDomain() }
                ?: emptyList(),
            tags = this.tags?.associate { it.key to it.value } ?: emptyMap(),
            request = this.request?.toDomain(),
            release = this.release?.let { ErrorEvent.Release(it) },
            sourceProject = ErrorEvent.SourceProject(this.project ?: ""),
            timestamp = this.dateCreated?.let { Instant.parse(it) } ?: Instant.now(),
        )
    }

    private fun SentryStackFrame.toDomain(): StackFrame {
        return StackFrame(
            filename = StackFrame.Filename(this.filename ?: "unknown"),
            function = StackFrame.FunctionName(this.function ?: "unknown"),
            lineNo = StackFrame.LineNumber(this.lineNo ?: 0),
            colNo = this.colNo?.let { StackFrame.ColumnNumber(it) },
            preContext = this.preContext ?: emptyList(),
            contextLine = this.contextLine?.let { StackFrame.ContextLine(it) },
            postContext = this.postContext ?: emptyList(),
            inApp = StackFrame.InApp(this.inApp ?: false),
        )
    }

    private fun SentryBreadcrumb.toDomain(): Breadcrumb {
        return Breadcrumb(
            timestamp = this.timestamp?.let { Instant.parse(it) } ?: Instant.now(),
            category = this.category?.let { Breadcrumb.Category(it) },
            message = this.message?.let { Breadcrumb.Message(it) },
            level = this.level?.let { Breadcrumb.Level(it) },
            type = this.type?.let { Breadcrumb.Type(it) },
            data = this.data,
        )
    }

    private fun SentryRequest.toDomain(): RequestContext {
        return RequestContext(
            url = this.url?.let { RequestContext.Url(it) },
            method = this.method?.let { RequestContext.Method(it) },
            headers = this.headers,
            queryString = this.queryString?.let { RequestContext.QueryString(it) },
            data = this.data,
        )
    }
}

// Sentry API Response DTOs
data class SentryEventResponse(
    val eventID: String?,
    val title: String?,
    val message: String?,
    val dateCreated: String?,
    val project: String?,
    val release: String?,
    val tags: List<SentryTag>?,
    val entries: List<SentryEntry>?,
    val request: SentryRequest?,
)

data class SentryTag(
    val key: String,
    val value: String,
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    defaultImpl = SentryUnknownEntry::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SentryExceptionEntry::class, name = "exception"),
    JsonSubTypes.Type(value = SentryBreadcrumbEntry::class, name = "breadcrumbs"),
)
sealed interface SentryEntry

data class SentryExceptionEntry(
    val type: String,
    val data: SentryExceptionData,
) : SentryEntry

data class SentryBreadcrumbEntry(
    val type: String,
    val data: SentryBreadcrumbData,
) : SentryEntry

data class SentryUnknownEntry(
    val type: String? = null,
) : SentryEntry

data class SentryExceptionData(
    val values: List<SentryException>?,
)

data class SentryException(
    val type: String?,
    val value: String?,
    val stacktrace: SentryStacktrace?,
)

data class SentryStacktrace(
    val frames: List<SentryStackFrame>?,
)

data class SentryStackFrame(
    val filename: String?,
    val function: String?,
    val lineNo: Int?,
    val colNo: Int?,
    val preContext: List<String>?,
    val contextLine: String?,
    val postContext: List<String>?,
    val inApp: Boolean?,
)

data class SentryBreadcrumbData(
    val values: List<SentryBreadcrumb>?,
)

data class SentryBreadcrumb(
    val timestamp: String?,
    val category: String?,
    val message: String?,
    val level: String?,
    val type: String?,
    val data: Map<String, Any>?,
)

data class SentryRequest(
    val url: String?,
    val method: String?,
    val headers: Map<String, String>?,
    val queryString: String?,
    val data: Map<String, Any>?,
)

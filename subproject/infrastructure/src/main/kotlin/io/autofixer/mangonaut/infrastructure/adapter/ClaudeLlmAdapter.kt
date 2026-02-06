package io.autofixer.mangonaut.infrastructure.adapter

import tools.jackson.databind.ObjectMapper
import io.autofixer.mangonaut.domain.exception.LlmApiException
import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.FixResult
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Claude API를 사용한 LlmProviderPort 구현체
 */
@Component
class ClaudeLlmAdapter(
    private val claudeWebClient: WebClient,
    private val properties: MangonautProperties,
    private val objectMapper: ObjectMapper,
) : LlmProviderPort {

    override val name: String = "claude"

    override suspend fun analyzeError(errorEvent: ErrorEvent, sourceFiles: Map<String, String>): FixResult {
        try {
            val prompt = buildAnalysisPrompt(errorEvent, sourceFiles)

            val response = claudeWebClient
                .post()
                .uri("/v1/messages")
                .bodyValue(
                    mapOf(
                        "model" to properties.llm.model,
                        "max_tokens" to 4096,
                        "messages" to listOf(
                            mapOf(
                                "role" to "user",
                                "content" to prompt,
                            )
                        ),
                    )
                )
                .retrieve()
                .bodyToMono(ClaudeResponse::class.java)
                .awaitSingle()

            return parseAnalysisResponse(response)
        } catch (e: WebClientResponseException) {
            throw LlmApiException(
                message = "Failed to analyze error with Claude: ${e.statusCode}",
                cause = e,
            )
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            // Claude API는 헬스체크 엔드포인트가 없어서 간단한 요청으로 확인
            claudeWebClient
                .post()
                .uri("/v1/messages")
                .bodyValue(
                    mapOf(
                        "model" to properties.llm.model,
                        "max_tokens" to 10,
                        "messages" to listOf(
                            mapOf(
                                "role" to "user",
                                "content" to "ping",
                            )
                        ),
                    )
                )
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildAnalysisPrompt(errorEvent: ErrorEvent, sourceFiles: Map<String, String>): String {
        val stackTraceStr = errorEvent.applicationStackFrames()
            .joinToString("\n") { frame ->
                "  at ${frame.function.value} (${frame.filename.value}:${frame.lineNo.value})"
            }

        val sourceFilesStr = sourceFiles.entries.joinToString("\n\n") { (filename, content) ->
            "=== $filename ===\n$content"
        }

        return """
            |You are an expert software engineer analyzing an error to propose a fix.
            |
            |## Error Information
            |**Type:** ${errorEvent.errorType.value}
            |**Message:** ${errorEvent.errorMessage.value}
            |**Title:** ${errorEvent.title.value}
            |
            |## Stack Trace (Application Code Only)
            |$stackTraceStr
            |
            |## Related Source Files
            |$sourceFilesStr
            |
            |## Task
            |Analyze this error and provide a fix. Respond in the following JSON format:
            |
            |```json
            |{
            |  "analysis": "Brief explanation of what went wrong",
            |  "rootCause": "The fundamental cause of the error",
            |  "confidence": "HIGH" | "MEDIUM" | "LOW",
            |  "changes": [
            |    {
            |      "file": "path/to/file.kt",
            |      "description": "What this change does",
            |      "original": "original code snippet",
            |      "modified": "modified code snippet"
            |    }
            |  ],
            |  "prTitle": "fix: Brief description of the fix",
            |  "prBody": "Detailed PR description in markdown"
            |}
            |```
            |
            |Important:
            |- Only propose changes if you are confident they will fix the issue
            |- Set confidence to LOW if you're unsure or need more context
            |- Keep changes minimal and focused on the fix
        """.trimMargin()
    }

    private fun parseAnalysisResponse(response: ClaudeResponse): FixResult {
        val content = response.content.firstOrNull()?.text
            ?: throw LlmApiException("Empty response from Claude")

        // JSON 블록 추출
        val jsonMatch = Regex("```json\\s*(.+?)\\s*```", RegexOption.DOT_MATCHES_ALL)
            .find(content)
            ?.groupValues
            ?.get(1)
            ?: content

        try {
            val parsed = objectMapper.readValue(jsonMatch, LlmAnalysisResponse::class.java)

            return FixResult(
                analysis = FixResult.Analysis(parsed.analysis),
                rootCause = FixResult.RootCause(parsed.rootCause),
                confidence = Confidence.valueOf(parsed.confidence.uppercase()),
                changes = parsed.changes.map { change ->
                    FileChange(
                        file = FileChange.FilePath(change.file),
                        description = FileChange.Description(change.description),
                        original = FileChange.OriginalContent(change.original),
                        modified = FileChange.ModifiedContent(change.modified),
                    )
                },
                prTitle = FixResult.PrTitle(parsed.prTitle),
                prBody = FixResult.PrBody(parsed.prBody),
            )
        } catch (e: Exception) {
            throw LlmApiException(
                message = "Failed to parse LLM response: ${e.message}",
                cause = e,
            )
        }
    }
}

// Claude API Response DTOs
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    val stopReason: String?,
)

data class ClaudeContent(
    val type: String,
    val text: String,
)

// LLM Analysis Response DTO
data class LlmAnalysisResponse(
    val analysis: String,
    val rootCause: String,
    val confidence: String,
    val changes: List<LlmFileChange>,
    val prTitle: String,
    val prBody: String,
)

data class LlmFileChange(
    val file: String,
    val description: String,
    val original: String,
    val modified: String,
)

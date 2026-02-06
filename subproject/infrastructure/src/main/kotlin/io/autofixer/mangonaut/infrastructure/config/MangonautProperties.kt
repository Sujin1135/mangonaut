package io.autofixer.mangonaut.infrastructure.config

import io.autofixer.mangonaut.domain.model.Confidence
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Mangonaut 애플리케이션 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "mangonaut")
data class MangonautProperties(
    val sentry: SentryProperties = SentryProperties(),
    val github: GitHubProperties = GitHubProperties(),
    val llm: LlmProperties = LlmProperties(),
    val projects: List<ProjectMappingProperties> = emptyList(),
    val behavior: BehaviorProperties = BehaviorProperties(),
)

/**
 * Sentry 연동 설정
 */
data class SentryProperties(
    val baseUrl: String = "https://sentry.io",
    val org: String = "",
    val token: String = "",
    val webhookSecret: String = "",
)

/**
 * GitHub 연동 설정
 */
data class GitHubProperties(
    val baseUrl: String = "https://api.github.com",
    val token: String = "",
)

/**
 * LLM 연동 설정
 */
data class LlmProperties(
    val provider: String = "claude",
    val model: String = "claude-sonnet-4-20250514",
    val apiKey: String = "",
    val baseUrl: String = "https://api.anthropic.com",
)

/**
 * Sentry 프로젝트와 SCM 저장소 간의 매핑
 */
data class ProjectMappingProperties(
    val sourceProject: String,
    val scmRepo: String,
    val sourceRoot: String = "src/main/kotlin/",
    val defaultBranch: String = "main",
)

/**
 * 동작 방식 설정
 */
data class BehaviorProperties(
    val autoPr: Boolean = true,
    val minConfidence: Confidence = Confidence.MEDIUM,
    val labels: List<String> = listOf("auto-fix", "ai-generated"),
    val branchPrefix: String = "fix/mangonaut-",
    val dryRun: Boolean = false,
)

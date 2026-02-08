package io.autofixer.mangonaut.infrastructure.config

import io.autofixer.mangonaut.domain.model.Confidence
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Mangonaut application configuration properties.
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
 * Sentry integration configuration.
 */
data class SentryProperties(
    val baseUrl: String = "https://sentry.io",
    val org: String = "",
    val token: String = "",
    val webhookSecret: String = "",
)

/**
 * GitHub integration configuration.
 */
data class GitHubProperties(
    val baseUrl: String = "https://api.github.com",
    val token: String = "",
)

/**
 * LLM integration configuration.
 */
data class LlmProperties(
    val provider: String = "claude",
    val model: String = "claude-sonnet-4-20250514",
    val apiKey: String = "",
    val baseUrl: String = "https://api.anthropic.com",
)

/**
 * Mapping between Sentry projects and SCM repositories.
 */
data class ProjectMappingProperties(
    val sourceProject: String,
    val scmRepo: String,
    val sourceRoots: List<String> = listOf("src/main/kotlin/"),
    val defaultBranch: String = "main",
)

/**
 * Behavior configuration.
 */
data class BehaviorProperties(
    val autoPr: Boolean = true,
    val minConfidence: Confidence = Confidence.MEDIUM,
    val labels: List<String> = listOf("auto-fix", "ai-generated"),
    val branchPrefix: String = "fix/mangonaut-",
    val dryRun: Boolean = false,
)

package io.autofixer.mangonaut.infrastructure.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

@Component
class GitHubInstallationRepositoryClient(
    private val githubWebClient: WebClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cached: CachedRepositories = CachedRepositories.EMPTY

    fun getRepositories(): List<InstalledRepository> = cached.repositories

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        refresh()
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    fun refresh() {
        try {
            val repositories = githubWebClient
                .get()
                .uri("/installation/repositories?per_page=100")
                .retrieve()
                .bodyToMono(InstallationRepositoriesResponse::class.java)
                .block()
                ?.repositories ?: emptyList()

            cached = CachedRepositories(
                repositories = repositories,
                fetchedAt = Instant.now(),
            )
            log.info("Cached {} installed repositories", repositories.size)
        } catch (e: Exception) {
            log.error("Failed to fetch installation repositories", e)
        }
    }

    internal data class CachedRepositories(
        val repositories: List<InstalledRepository>,
        val fetchedAt: Instant,
    ) {
        fun isExpired(): Boolean = Instant.now().isAfter(fetchedAt.plusSeconds(CACHE_TTL_SECONDS))

        companion object {
            const val CACHE_TTL_SECONDS = 300L
            val EMPTY = CachedRepositories(emptyList(), Instant.EPOCH)
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstallationRepositoriesResponse(
    val repositories: List<InstalledRepository>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstalledRepository(
    val id: Long,
    val name: String,
    @JsonProperty("full_name")
    val fullName: String,
    @JsonProperty("default_branch")
    val defaultBranch: String,
)

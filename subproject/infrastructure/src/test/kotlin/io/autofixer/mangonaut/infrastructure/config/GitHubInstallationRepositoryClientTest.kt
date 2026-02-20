package io.autofixer.mangonaut.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class GitHubInstallationRepositoryClientTest : BehaviorSpec({

    context("CachedRepositories expiration") {
        given("a cache fetched less than 5 minutes ago") {
            val cache = GitHubInstallationRepositoryClient.CachedRepositories(
                repositories = emptyList(),
                fetchedAt = Instant.now().minusSeconds(200),
            )

            then("should not be expired") {
                cache.isExpired() shouldBe false
            }
        }

        given("a cache fetched more than 5 minutes ago") {
            val cache = GitHubInstallationRepositoryClient.CachedRepositories(
                repositories = emptyList(),
                fetchedAt = Instant.now().minusSeconds(400),
            )

            then("should be expired") {
                cache.isExpired() shouldBe true
            }
        }

        given("the EMPTY sentinel cache") {
            val cache = GitHubInstallationRepositoryClient.CachedRepositories.EMPTY

            then("should be expired") {
                cache.isExpired() shouldBe true
            }
        }
    }
})

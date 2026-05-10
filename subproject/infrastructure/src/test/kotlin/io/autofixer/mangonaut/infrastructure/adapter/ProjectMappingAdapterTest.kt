package io.autofixer.mangonaut.infrastructure.adapter

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.port.ProjectMappingPort
import io.autofixer.mangonaut.infrastructure.config.BehaviorProperties
import io.autofixer.mangonaut.infrastructure.config.GitHubInstallationRepositoryClient
import io.autofixer.mangonaut.infrastructure.config.InstalledRepository
import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import io.autofixer.mangonaut.infrastructure.config.ProjectMappingProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ProjectMappingAdapterTest : BehaviorSpec({

    val repositoryClient = mockk<GitHubInstallationRepositoryClient>()

    fun paramsFor(slug: String) = ProjectMappingPort.Params(ErrorEvent.SourceProject(slug))

    context("invoke") {
        given("a static config matching the source project") {
            val properties = MangonautProperties(
                projects = listOf(
                    ProjectMappingProperties(
                        sourceProject = "my-app",
                        scmRepo = "org/my-app-custom",
                        defaultBranch = "develop",
                    ),
                ),
                behavior = BehaviorProperties(),
            )
            val adapter = ProjectMappingAdapter(properties, repositoryClient)

            `when`("invoking with 'my-app'") {
                val mapping = adapter(paramsFor("my-app"))

                then("should return the static config values") {
                    mapping!!.scmRepo shouldBe "org/my-app-custom"
                    mapping.defaultBranch shouldBe "develop"
                }

                then("should not call the repository client") {
                    verify(exactly = 0) { repositoryClient.getRepositories() }
                }
            }
        }

        given("no static config but a matching GitHub installed repository") {
            val properties = MangonautProperties(
                behavior = BehaviorProperties(
                    branchPrefix = "fix/mangonaut-",
                    labels = listOf("auto-fix"),
                    minConfidence = Confidence.MEDIUM,
                    autoPr = true,
                ),
            )
            val adapter = ProjectMappingAdapter(properties, repositoryClient)

            every { repositoryClient.getRepositories() } returns listOf(
                InstalledRepository(
                    id = 1,
                    name = "my-service",
                    fullName = "org/my-service",
                    defaultBranch = "main",
                ),
            )

            `when`("invoking with 'my-service'") {
                val mapping = adapter(paramsFor("my-service"))

                then("should return values from the GitHub API response") {
                    mapping!!.scmRepo shouldBe "org/my-service"
                    mapping.defaultBranch shouldBe "main"
                }

                then("should include behavior properties") {
                    mapping!!.branchPrefix shouldBe "fix/mangonaut-"
                    mapping.labels shouldBe listOf("auto-fix")
                    mapping.minConfidence shouldBe Confidence.MEDIUM
                    mapping.autoPr shouldBe true
                }
            }
        }

        given("no static config and no matching GitHub repository") {
            val properties = MangonautProperties()
            val adapter = ProjectMappingAdapter(properties, repositoryClient)

            every { repositoryClient.getRepositories() } returns listOf(
                InstalledRepository(
                    id = 1,
                    name = "other-repo",
                    fullName = "org/other-repo",
                    defaultBranch = "main",
                ),
            )

            `when`("invoking with 'unknown-project'") {
                val mapping = adapter(paramsFor("unknown-project"))

                then("should return null") {
                    mapping.shouldBeNull()
                }
            }
        }
    }
})

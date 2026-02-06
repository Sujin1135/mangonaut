package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.PrParams
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class CreateFixPullRequestUseCaseTest : BehaviorSpec({

    fun createParams(
        scmProviderPort: ScmProviderPort,
        confidence: Confidence = Confidence.HIGH,
        changes: List<FileChange> = listOf(TestFixtures.createFileChange()),
        minConfidence: Confidence = Confidence.MEDIUM,
    ): Pair<CreateFixPullRequestUseCase, CreateFixPullRequestUseCase.Params> {
        val useCase = CreateFixPullRequestUseCase(scmProviderPort)
        val params = CreateFixPullRequestUseCase.Params(
            errorEvent = TestFixtures.createErrorEvent(),
            fixResult = TestFixtures.createFixResult(confidence = confidence, changes = changes),
            repoId = TestFixtures.REPO_ID,
            defaultBranch = TestFixtures.DEFAULT_BRANCH,
            branchPrefix = TestFixtures.BRANCH_PREFIX,
            labels = TestFixtures.LABELS,
            minConfidence = minConfidence,
        )
        return useCase to params
    }

    context("CreateFixPullRequestUseCase") {
        given("a HIGH confidence result with changes") {
            `when`("the use case is invoked") {
                then("should create a PR after checking duplicates, creating branch, committing, and opening PR") {
                    val scmProviderPort = mockk<ScmProviderPort>(relaxUnitFun = true)
                    val prResult = TestFixtures.createPrResult()

                    coEvery { scmProviderPort.hasOpenPR(any(), any()) } returns false
                    coEvery { scmProviderPort.createPullRequest(any(), any()) } returns prResult

                    val (useCase, params) = createParams(scmProviderPort)
                    val result = useCase(params)

                    result.shouldNotBeNull()
                    result.number.value shouldBe 42

                    coVerify(exactly = 1) {
                        scmProviderPort.hasOpenPR(
                            TestFixtures.REPO_ID,
                            PrParams.HeadBranch("fix/mangonaut-issue-123"),
                        )
                    }

                    coVerify(exactly = 1) {
                        scmProviderPort.createBranch(
                            TestFixtures.REPO_ID,
                            PrParams.BaseBranch(TestFixtures.DEFAULT_BRANCH),
                            PrParams.HeadBranch("fix/mangonaut-issue-123"),
                        )
                    }

                    coVerify(exactly = 1) {
                        scmProviderPort.commitFiles(
                            repoId = TestFixtures.REPO_ID,
                            branch = PrParams.HeadBranch("fix/mangonaut-issue-123"),
                            changes = any(),
                            message = any(),
                        )
                    }

                    coVerify(exactly = 1) {
                        scmProviderPort.createPullRequest(TestFixtures.REPO_ID, any())
                    }
                }
            }
        }

        given("a LOW confidence result when minConfidence is MEDIUM") {
            `when`("the use case is invoked") {
                then("should return null and not interact with SCM") {
                    val scmProviderPort = mockk<ScmProviderPort>(relaxUnitFun = true)
                    val (useCase, params) = createParams(
                        scmProviderPort,
                        confidence = Confidence.LOW,
                        minConfidence = Confidence.MEDIUM,
                    )

                    val result = useCase(params)

                    result.shouldBeNull()

                    coVerify(exactly = 0) { scmProviderPort.hasOpenPR(any(), any()) }
                    coVerify(exactly = 0) { scmProviderPort.createBranch(any(), any(), any()) }
                    coVerify(exactly = 0) { scmProviderPort.commitFiles(any(), any(), any(), any()) }
                    coVerify(exactly = 0) { scmProviderPort.createPullRequest(any(), any()) }
                }
            }
        }

        given("a HIGH confidence result with no changes") {
            `when`("the use case is invoked") {
                then("should return null (no changes to commit)") {
                    val scmProviderPort = mockk<ScmProviderPort>(relaxUnitFun = true)
                    val (useCase, params) = createParams(
                        scmProviderPort,
                        confidence = Confidence.HIGH,
                        changes = emptyList(),
                    )
                    val result = useCase(params)
                    result.shouldBeNull()
                }
            }
        }

        given("an existing open PR for the same branch") {
            `when`("the use case is invoked") {
                then("should return null (duplicate prevention) and not create a branch or PR") {
                    val scmProviderPort = mockk<ScmProviderPort>(relaxUnitFun = true)
                    coEvery { scmProviderPort.hasOpenPR(any(), any()) } returns true

                    val (useCase, params) = createParams(scmProviderPort)
                    val result = useCase(params)

                    result.shouldBeNull()

                    coVerify(exactly = 0) { scmProviderPort.createBranch(any(), any(), any()) }
                    coVerify(exactly = 0) { scmProviderPort.createPullRequest(any(), any()) }
                }
            }
        }

        given("changes where some have no actual diff") {
            `when`("the use case is invoked") {
                then("should only commit files with actual changes") {
                    val scmProviderPort = mockk<ScmProviderPort>(relaxUnitFun = true)
                    val noChangeFile = FileChange(
                        file = FileChange.FilePath("same.kt"),
                        description = FileChange.Description("No real change"),
                        original = FileChange.OriginalContent("same code"),
                        modified = FileChange.ModifiedContent("same code"),
                    )
                    val realChange = TestFixtures.createFileChange()

                    coEvery { scmProviderPort.hasOpenPR(any(), any()) } returns false
                    coEvery { scmProviderPort.createPullRequest(any(), any()) } returns TestFixtures.createPrResult()

                    val (useCase, params) = createParams(
                        scmProviderPort,
                        changes = listOf(realChange, noChangeFile),
                    )
                    useCase(params)

                    coVerify(exactly = 1) {
                        scmProviderPort.commitFiles(
                            repoId = any(),
                            branch = any(),
                            changes = match { changes ->
                                changes.size == 1 && changes.all { it.hasChanges() }
                            },
                            message = any(),
                        )
                    }
                }
            }
        }
    }
})

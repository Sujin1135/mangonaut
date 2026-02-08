package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ProcessErrorAlertUseCaseTest : BehaviorSpec({

    fun createUseCaseAndParams(
        autoPr: Boolean = true,
        minConfidence: Confidence = Confidence.MEDIUM,
    ): Triple<ProcessErrorAlertUseCase, ProcessErrorAlertUseCase.Params, Triple<ErrorSourcePort, AnalyzeErrorUseCase, CreateFixPullRequestUseCase>> {
        val errorSourcePort = mockk<ErrorSourcePort>()
        val analyzeErrorUseCase = mockk<AnalyzeErrorUseCase>()
        val createFixPullRequestUseCase = mockk<CreateFixPullRequestUseCase>()
        val useCase = ProcessErrorAlertUseCase(errorSourcePort, analyzeErrorUseCase, createFixPullRequestUseCase)
        val params = ProcessErrorAlertUseCase.Params(
            issueId = ErrorEvent.Id("issue-123"),
            sourceProject = ErrorEvent.SourceProject("my-backend"),
            repoId = TestFixtures.REPO_ID,
            defaultBranch = TestFixtures.DEFAULT_BRANCH,
            sourceRoots = TestFixtures.SOURCE_ROOTS,
            branchPrefix = TestFixtures.BRANCH_PREFIX,
            labels = TestFixtures.LABELS,
            minConfidence = minConfidence,
            autoPr = autoPr,
        )
        return Triple(useCase, params, Triple(errorSourcePort, analyzeErrorUseCase, createFixPullRequestUseCase))
    }

    context("ProcessErrorAlertUseCase") {
        given("a valid error alert with autoPr enabled") {
            `when`("the use case is invoked") {
                then("should fetch, analyze, create PR, and return successful result") {
                    val (useCase, params, mocks) = createUseCaseAndParams()
                    val (errorSourcePort, analyzeErrorUseCase, createFixPullRequestUseCase) = mocks
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult()
                    val prResult = TestFixtures.createPrResult()

                    coEvery { errorSourcePort.fetchEvent(any()) } returns errorEvent
                    coEvery { analyzeErrorUseCase(any()) } returns fixResult
                    coEvery { createFixPullRequestUseCase(any()) } returns prResult

                    val result = useCase(params)

                    coVerify(exactly = 1) {
                        errorSourcePort.fetchEvent(ErrorEvent.Id("issue-123"))
                    }

                    coVerify(exactly = 1) {
                        analyzeErrorUseCase(match { p ->
                            p.errorEvent == errorEvent && p.repoId == TestFixtures.REPO_ID
                        })
                    }

                    coVerify(exactly = 1) {
                        createFixPullRequestUseCase(match { p ->
                            p.fixResult == fixResult && p.errorEvent == errorEvent
                        })
                    }

                    result.errorEvent shouldBe errorEvent
                    result.analysisCompleted shouldBe true
                    result.prResult.shouldNotBeNull()
                    result.prResult!!.number.value shouldBe 42
                }
            }
        }

        given("a valid error alert with autoPr disabled") {
            `when`("the use case is invoked") {
                then("should analyze but not create PR, returning null prResult") {
                    val (useCase, params, mocks) = createUseCaseAndParams(autoPr = false)
                    val (errorSourcePort, analyzeErrorUseCase, createFixPullRequestUseCase) = mocks
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult()

                    coEvery { errorSourcePort.fetchEvent(any()) } returns errorEvent
                    coEvery { analyzeErrorUseCase(any()) } returns fixResult

                    val result = useCase(params)

                    coVerify(exactly = 1) { analyzeErrorUseCase(any()) }
                    coVerify(exactly = 0) { createFixPullRequestUseCase(any()) }

                    result.analysisCompleted shouldBe true
                    result.prResult.shouldBeNull()
                }
            }
        }

        given("a valid error alert where PR creation returns null") {
            `when`("the use case is invoked") {
                then("should return result with null prResult") {
                    val (useCase, params, mocks) = createUseCaseAndParams()
                    val (errorSourcePort, analyzeErrorUseCase, createFixPullRequestUseCase) = mocks
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult(confidence = Confidence.LOW)

                    coEvery { errorSourcePort.fetchEvent(any()) } returns errorEvent
                    coEvery { analyzeErrorUseCase(any()) } returns fixResult
                    coEvery { createFixPullRequestUseCase(any()) } returns null

                    val result = useCase(params)

                    result.analysisCompleted shouldBe true
                    result.prResult.shouldBeNull()
                }
            }
        }
    }
})

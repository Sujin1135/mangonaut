package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import io.autofixer.mangonaut.domain.port.ProjectMappingPort
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ProcessErrorAlertUseCaseTest : BehaviorSpec({

    data class Mocks(
        val projectMappingPort: ProjectMappingPort,
        val errorSourcePort: ErrorSourcePort,
        val analyzeErrorUseCase: AnalyzeErrorUseCase,
        val createFixPullRequestUseCase: CreateFixPullRequestUseCase,
    )

    fun createUseCaseAndParams(): Pair<ProcessErrorAlertUseCase, Mocks> {
        val mocks = Mocks(
            projectMappingPort = mockk(),
            errorSourcePort = mockk(),
            analyzeErrorUseCase = mockk(),
            createFixPullRequestUseCase = mockk(),
        )
        val useCase = ProcessErrorAlertUseCase(
            mocks.projectMappingPort,
            mocks.errorSourcePort,
            mocks.analyzeErrorUseCase,
            mocks.createFixPullRequestUseCase,
        )
        return useCase to mocks
    }

    val params = ProcessErrorAlertUseCase.Params(
        issueId = ErrorEvent.Id("issue-123"),
        sourceProject = ErrorEvent.SourceProject("my-backend"),
    )

    fun mappingResult(
        autoPr: Boolean = true,
        minConfidence: Confidence = Confidence.MEDIUM,
    ) = ProjectMappingPort.Result(
        sourceProject = "my-backend",
        scmRepo = TestFixtures.REPO_ID.value,
        defaultBranch = TestFixtures.DEFAULT_BRANCH,
        branchPrefix = TestFixtures.BRANCH_PREFIX,
        labels = TestFixtures.LABELS,
        minConfidence = minConfidence,
        autoPr = autoPr,
    )

    context("ProcessErrorAlertUseCase") {
        given("a valid error alert with autoPr enabled") {
            `when`("the use case is invoked") {
                then("should fetch, analyze, create PR, and return successful result") {
                    val (useCase, mocks) = createUseCaseAndParams()
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult()
                    val prResult = TestFixtures.createPrResult()

                    coEvery { mocks.projectMappingPort(any()) } returns mappingResult(autoPr = true)
                    coEvery { mocks.errorSourcePort.fetchEvent(any()) } returns errorEvent
                    coEvery { mocks.analyzeErrorUseCase(any()) } returns fixResult
                    coEvery { mocks.createFixPullRequestUseCase(any()) } returns prResult

                    val result = useCase(params)

                    coVerify(exactly = 1) {
                        mocks.projectMappingPort(ProjectMappingPort.Params(params.sourceProject))
                    }
                    coVerify(exactly = 1) {
                        mocks.errorSourcePort.fetchEvent(ErrorEvent.Id("issue-123"))
                    }
                    coVerify(exactly = 1) {
                        mocks.analyzeErrorUseCase(match { p ->
                            p.errorEvent == errorEvent && p.repoId == TestFixtures.REPO_ID
                        })
                    }
                    coVerify(exactly = 1) {
                        mocks.createFixPullRequestUseCase(match { p ->
                            p.fixResult == fixResult && p.errorEvent == errorEvent
                        })
                    }

                    result.shouldNotBeNull()
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
                    val (useCase, mocks) = createUseCaseAndParams()
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult()

                    coEvery { mocks.projectMappingPort(any()) } returns mappingResult(autoPr = false)
                    coEvery { mocks.errorSourcePort.fetchEvent(any()) } returns errorEvent
                    coEvery { mocks.analyzeErrorUseCase(any()) } returns fixResult

                    val result = useCase(params)

                    coVerify(exactly = 1) { mocks.analyzeErrorUseCase(any()) }
                    coVerify(exactly = 0) { mocks.createFixPullRequestUseCase(any()) }

                    result.shouldNotBeNull()
                    result.analysisCompleted shouldBe true
                    result.prResult.shouldBeNull()
                }
            }
        }

        given("a valid error alert where PR creation returns null") {
            `when`("the use case is invoked") {
                then("should return result with null prResult") {
                    val (useCase, mocks) = createUseCaseAndParams()
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult(confidence = Confidence.LOW)

                    coEvery { mocks.projectMappingPort(any()) } returns mappingResult()
                    coEvery { mocks.errorSourcePort.fetchEvent(any()) } returns errorEvent
                    coEvery { mocks.analyzeErrorUseCase(any()) } returns fixResult
                    coEvery { mocks.createFixPullRequestUseCase(any()) } returns null

                    val result = useCase(params)

                    result.shouldNotBeNull()
                    result.analysisCompleted shouldBe true
                    result.prResult.shouldBeNull()
                }
            }
        }

        given("no project mapping for the source project") {
            `when`("the use case is invoked") {
                then("should return null and skip downstream calls") {
                    val (useCase, mocks) = createUseCaseAndParams()

                    coEvery { mocks.projectMappingPort(any()) } returns null

                    val result = useCase(params)

                    result.shouldBeNull()
                    coVerify(exactly = 0) { mocks.errorSourcePort.fetchEvent(any()) }
                    coVerify(exactly = 0) { mocks.analyzeErrorUseCase(any()) }
                    coVerify(exactly = 0) { mocks.createFixPullRequestUseCase(any()) }
                }
            }
        }
    }
})

package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.RepoContext
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class AnalyzeErrorUseCaseTest :
    BehaviorSpec({

        context("AnalyzeErrorUseCase (Stage 3 — agentic)") {
            given("an error event and repo context") {
                `when`("the use case is invoked") {
                    then("delegates to the LLM provider with a RepoContext built from params") {
                        val llmProviderPort = mockk<LlmProviderPort>()
                        val useCase = AnalyzeErrorUseCase(llmProviderPort)
                        val errorEvent = TestFixtures.createErrorEvent()
                        val fixResult = TestFixtures.createFixResult()

                        coEvery { llmProviderPort.analyzeError(any(), any()) } returns fixResult

                        val result =
                            useCase(
                                AnalyzeErrorUseCase.Params(
                                    errorEvent = errorEvent,
                                    repoId = TestFixtures.REPO_ID,
                                    defaultBranch = TestFixtures.DEFAULT_BRANCH,
                                ),
                            )

                        result shouldBe fixResult

                        coVerify(exactly = 1) {
                            llmProviderPort.analyzeError(
                                errorEvent,
                                RepoContext(
                                    repoId = TestFixtures.REPO_ID,
                                    ref = RepoContext.Ref(TestFixtures.DEFAULT_BRANCH),
                                ),
                            )
                        }
                    }
                }
            }
        }
    })

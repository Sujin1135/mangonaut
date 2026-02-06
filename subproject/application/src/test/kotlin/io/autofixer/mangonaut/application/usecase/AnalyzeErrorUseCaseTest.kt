package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

class AnalyzeErrorUseCaseTest : BehaviorSpec({

    context("AnalyzeErrorUseCase") {
        given("an error event with application stack frames") {
            `when`("the use case is invoked") {
                then("should return the LLM analysis result and fetch source files for app frames only") {
                    val scmProviderPort = mockk<ScmProviderPort>()
                    val llmProviderPort = mockk<LlmProviderPort>()
                    val useCase = AnalyzeErrorUseCase(scmProviderPort, llmProviderPort)
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult()

                    coEvery {
                        scmProviderPort.getFileContent(any<RepoId>(), any<FileChange.FilePath>(), any<String>())
                    } returns "package com.example\n\nclass UserService { ... }"

                    coEvery {
                        llmProviderPort.analyzeError(any(), any())
                    } returns fixResult

                    val result = useCase(
                        AnalyzeErrorUseCase.Params(
                            errorEvent = errorEvent,
                            repoId = TestFixtures.REPO_ID,
                            defaultBranch = TestFixtures.DEFAULT_BRANCH,
                            sourceRoot = TestFixtures.SOURCE_ROOT,
                        )
                    )

                    result shouldBe fixResult

                    coVerify(exactly = 1) {
                        scmProviderPort.getFileContent(
                            TestFixtures.REPO_ID,
                            FileChange.FilePath("src/main/kotlin/com/example/UserService.kt"),
                            TestFixtures.DEFAULT_BRANCH,
                        )
                    }

                    coVerify(exactly = 1) {
                        llmProviderPort.analyzeError(
                            errorEvent,
                            mapOf("com/example/UserService.kt" to "package com.example\n\nclass UserService { ... }"),
                        )
                    }
                }
            }
        }

        given("an error event with no application stack frames") {
            `when`("the use case is invoked") {
                then("should call LLM with empty source files and not fetch from SCM") {
                    val scmProviderPort = mockk<ScmProviderPort>()
                    val llmProviderPort = mockk<LlmProviderPort>()
                    val useCase = AnalyzeErrorUseCase(scmProviderPort, llmProviderPort)
                    val errorEvent = TestFixtures.createErrorEvent(
                        stackTrace = listOf(
                            TestFixtures.createStackFrame(filename = "Library.kt", inApp = false),
                        ),
                    )
                    val fixResult = TestFixtures.createFixResult()

                    coEvery { llmProviderPort.analyzeError(any(), any()) } returns fixResult

                    useCase(
                        AnalyzeErrorUseCase.Params(
                            errorEvent = errorEvent,
                            repoId = TestFixtures.REPO_ID,
                            defaultBranch = TestFixtures.DEFAULT_BRANCH,
                            sourceRoot = TestFixtures.SOURCE_ROOT,
                        )
                    )

                    coVerify(exactly = 1) {
                        llmProviderPort.analyzeError(errorEvent, emptyMap())
                    }

                    coVerify(exactly = 0) {
                        scmProviderPort.getFileContent(any(), any(), any())
                    }
                }
            }
        }

        given("an SCM error when fetching a source file") {
            `when`("the use case is invoked") {
                then("should skip the failed file and include the successful one") {
                    val scmProviderPort = mockk<ScmProviderPort>()
                    val llmProviderPort = mockk<LlmProviderPort>()
                    val useCase = AnalyzeErrorUseCase(scmProviderPort, llmProviderPort)
                    val errorEvent = TestFixtures.createErrorEvent(
                        stackTrace = listOf(
                            TestFixtures.createStackFrame(filename = "Good.kt", inApp = true),
                            TestFixtures.createStackFrame(filename = "Missing.kt", inApp = true),
                        ),
                    )
                    val fixResult = TestFixtures.createFixResult()

                    // MockK의 eq() 매처는 @JvmInline value class와 호환성 문제가 있어
                    // slot 캡처를 사용하여 실제 호출된 인자를 검증한다.
                    val sourceFilesSlot = slot<Map<String, String>>()

                    coEvery {
                        scmProviderPort.getFileContent(any(), any(), any())
                    } answers {
                        // MockK이 @JvmInline value class를 언박싱할 수 있으므로
                        // Any 타입으로 받아서 String으로 변환한다.
                        val pathArg = args[1]
                        val pathValue = when (pathArg) {
                            is FileChange.FilePath -> pathArg.value
                            is String -> pathArg
                            else -> pathArg.toString()
                        }
                        when (pathValue) {
                            "src/main/kotlin/Good.kt" -> "good content"
                            "src/main/kotlin/Missing.kt" -> throw RuntimeException("File not found")
                            else -> throw IllegalArgumentException("Unexpected path: $pathValue")
                        }
                    }

                    coEvery {
                        llmProviderPort.analyzeError(any(), capture(sourceFilesSlot))
                    } returns fixResult

                    useCase(
                        AnalyzeErrorUseCase.Params(
                            errorEvent = errorEvent,
                            repoId = TestFixtures.REPO_ID,
                            defaultBranch = TestFixtures.DEFAULT_BRANCH,
                            sourceRoot = TestFixtures.SOURCE_ROOT,
                        )
                    )

                    sourceFilesSlot.captured shouldBe mapOf("Good.kt" to "good content")
                }
            }
        }

        given("duplicate filenames in stack frames") {
            `when`("the use case is invoked") {
                then("should fetch the file only once (deduplication)") {
                    val scmProviderPort = mockk<ScmProviderPort>()
                    val llmProviderPort = mockk<LlmProviderPort>()
                    val useCase = AnalyzeErrorUseCase(scmProviderPort, llmProviderPort)
                    val errorEvent = TestFixtures.createErrorEvent(
                        stackTrace = listOf(
                            TestFixtures.createStackFrame(filename = "UserService.kt", function = "getUser", inApp = true),
                            TestFixtures.createStackFrame(filename = "UserService.kt", function = "findUser", inApp = true),
                        ),
                    )
                    val fixResult = TestFixtures.createFixResult()

                    coEvery { scmProviderPort.getFileContent(any(), any(), any()) } returns "content"
                    coEvery { llmProviderPort.analyzeError(any(), any()) } returns fixResult

                    useCase(
                        AnalyzeErrorUseCase.Params(
                            errorEvent = errorEvent,
                            repoId = TestFixtures.REPO_ID,
                            defaultBranch = TestFixtures.DEFAULT_BRANCH,
                            sourceRoot = TestFixtures.SOURCE_ROOT,
                        )
                    )

                    coVerify(exactly = 1) {
                        scmProviderPort.getFileContent(any(), any(), any())
                    }
                }
            }
        }
    }
})

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
                then("should resolve file paths, fetch source files, and return LLM analysis result") {
                    val scmProviderPort = mockk<ScmProviderPort>()
                    val llmProviderPort = mockk<LlmProviderPort>()
                    val useCase = AnalyzeErrorUseCase(scmProviderPort, llmProviderPort)
                    val errorEvent = TestFixtures.createErrorEvent()
                    val fixResult = TestFixtures.createFixResult()

                    coEvery {
                        scmProviderPort.resolveFilePaths(any<RepoId>(), any<List<String>>(), any<String>())
                    } returns mapOf(
                        "com/example/UserService.kt" to FileChange.FilePath("src/main/kotlin/com/example/UserService.kt"),
                    )

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
                        )
                    )

                    result shouldBe fixResult

                    coVerify(exactly = 1) {
                        scmProviderPort.resolveFilePaths(
                            TestFixtures.REPO_ID,
                            listOf("com/example/UserService.kt"),
                            TestFixtures.DEFAULT_BRANCH,
                        )
                    }

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

                    coEvery {
                        scmProviderPort.resolveFilePaths(any(), any(), any())
                    } returns emptyMap()

                    coEvery { llmProviderPort.analyzeError(any(), any()) } returns fixResult

                    useCase(
                        AnalyzeErrorUseCase.Params(
                            errorEvent = errorEvent,
                            repoId = TestFixtures.REPO_ID,
                            defaultBranch = TestFixtures.DEFAULT_BRANCH,
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

                    val sourceFilesSlot = slot<Map<String, String>>()

                    coEvery {
                        scmProviderPort.resolveFilePaths(any(), any(), any())
                    } returns mapOf(
                        "Good.kt" to FileChange.FilePath("src/main/kotlin/Good.kt"),
                        "Missing.kt" to FileChange.FilePath("src/main/kotlin/Missing.kt"),
                    )

                    coEvery {
                        scmProviderPort.getFileContent(any(), any(), any())
                    } answers {
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

                    coEvery {
                        scmProviderPort.resolveFilePaths(any(), any(), any())
                    } returns mapOf(
                        "UserService.kt" to FileChange.FilePath("src/main/kotlin/UserService.kt"),
                    )

                    coEvery { scmProviderPort.getFileContent(any(), any(), any()) } returns "content"
                    coEvery { llmProviderPort.analyzeError(any(), any()) } returns fixResult

                    useCase(
                        AnalyzeErrorUseCase.Params(
                            errorEvent = errorEvent,
                            repoId = TestFixtures.REPO_ID,
                            defaultBranch = TestFixtures.DEFAULT_BRANCH,
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

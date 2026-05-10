package io.autofixer.mangonaut.infrastructure.adapter

import io.autofixer.mangonaut.domain.model.CodeSearchHit
import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.RepoContext
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.model.StackFrame
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stage-3 agentic-loop tests for [ClaudeAgenticLlmAdapter].
 * Each test stubs the Anthropic Messages API with a fixed sequence of
 * pre-canned responses (one per agent iteration) and asserts the
 * resulting [io.autofixer.mangonaut.domain.model.FixResult].
 */
class ClaudeAgenticLlmAdapterTest :
    BehaviorSpec({

        val properties = MangonautProperties()
        val objectMapper: ObjectMapper = jacksonObjectMapper()

        fun adapterWith(
            responses: List<String>,
            scm: ScmProviderPort,
        ): ClaudeAgenticLlmAdapter {
            val counter = AtomicInteger(0)
            val client =
                WebClient
                    .builder()
                    .baseUrl("https://api.anthropic.com")
                    .exchangeFunction(
                        ExchangeFunction {
                            val idx = counter.getAndIncrement().coerceAtMost(responses.lastIndex)
                            Mono.just(jsonResponse(HttpStatus.OK, responses[idx]))
                        },
                    ).build()
            return ClaudeAgenticLlmAdapter(client, properties, objectMapper, scm)
        }

        fun errorEvent(filename: String = "io/contents/collector/SentryTestRunner.kt"): ErrorEvent =
            ErrorEvent(
                id = ErrorEvent.Id("evt-1"),
                title = ErrorEvent.Title("NPE"),
                errorType = ErrorEvent.ErrorType("NullPointerException"),
                errorMessage = ErrorEvent.ErrorMessage("boom"),
                stackTrace =
                    listOf(
                        StackFrame(
                            filename = StackFrame.Filename(filename),
                            function = StackFrame.FunctionName("run"),
                            lineNo = StackFrame.LineNumber(10),
                            inApp = StackFrame.InApp(true),
                        ),
                    ),
                breadcrumbs = emptyList(),
                tags = emptyMap(),
                sourceProject = ErrorEvent.SourceProject("my-backend"),
                timestamp = Instant.parse("2026-02-07T10:00:00Z"),
            )

        val repoContext =
            RepoContext(
                repoId = RepoId.of("acme/svc"),
                ref = RepoContext.Ref("main"),
            )

        context("analyzeError - simple case") {
            given("read_file → propose_fix → finish") {
                then("returns a single change with the model's confidence") {
                    runTest {
                        val scm = mockk<ScmProviderPort>()
                        coEvery {
                            scm.getFileContent(any(), any(), any())
                        } returns "fun greet() {\n    println(\"hi\")\n}\n"

                        val responses =
                            listOf(
                                // Iteration 1: read_file
                                toolUseResponse(
                                    "tu_1",
                                    "read_file",
                                    """{"path":"src/Foo.kt"}""",
                                ),
                                // Iteration 2: propose_fix
                                toolUseResponse(
                                    "tu_2",
                                    "propose_fix",
                                    """{"file":"src/Foo.kt","original":"println(\"hi\")","modified":"println(\"hello\")","description":"d"}""",
                                ),
                                // Iteration 3: finish
                                toolUseResponse(
                                    "tu_3",
                                    "finish",
                                    """{"summary":"s","root_cause":"rc","pr_title":"fix: x","pr_body":"b","confidence":"HIGH"}""",
                                ),
                            )

                        val result = adapterWith(responses, scm).analyzeError(errorEvent(), repoContext)

                        result.changes shouldHaveSize 1
                        result.changes
                            .single()
                            .file.value shouldBe "src/Foo.kt"
                        result.confidence shouldBe Confidence.HIGH
                        result.prTitle.value shouldBe "fix: x"
                    }
                }
            }
        }

        context("analyzeError - multi-step exploration") {
            given("read_file → list_directory → read_file → propose_fix → finish") {
                then("accumulates one change and returns MEDIUM confidence") {
                    runTest {
                        val scm = mockk<ScmProviderPort>()
                        coEvery {
                            scm.getFileContent(any(), FileChange.FilePath("a/A.kt"), any())
                        } returns "class A { fun a() = b() }"
                        coEvery {
                            scm.getFileContent(any(), FileChange.FilePath("a/B.kt"), any())
                        } returns "fun b(): Int = 1 / 0"
                        coEvery {
                            scm.listDirectory(any(), any(), any(), any())
                        } returns listOf("a/A.kt", "a/B.kt")

                        val responses =
                            listOf(
                                toolUseResponse("tu_1", "read_file", """{"path":"a/A.kt"}"""),
                                toolUseResponse("tu_2", "list_directory", """{"path":"a"}"""),
                                toolUseResponse("tu_3", "read_file", """{"path":"a/B.kt"}"""),
                                toolUseResponse(
                                    "tu_4",
                                    "propose_fix",
                                    """{"file":"a/B.kt","original":"1 / 0","modified":"if (false) 0 else 1","description":"avoid div/0"}""",
                                ),
                                toolUseResponse(
                                    "tu_5",
                                    "finish",
                                    """{"summary":"s","root_cause":"div0","pr_title":"fix: div0","pr_body":"b","confidence":"MEDIUM"}""",
                                ),
                            )

                        val result = adapterWith(responses, scm).analyzeError(errorEvent(), repoContext)

                        result.changes shouldHaveSize 1
                        result.changes
                            .single()
                            .file.value shouldBe "a/B.kt"
                        result.confidence shouldBe Confidence.MEDIUM
                    }
                }
            }
        }

        context("analyzeError - hallucinated path on propose_fix") {
            given("propose_fix is called for a path that was never read") {
                then("returns a tool error and the change is NOT accumulated") {
                    runTest {
                        val scm = mockk<ScmProviderPort>()
                        // The model proposes a fix for a file that was never read in
                        // this session. The first response invokes propose_fix; the
                        // adapter must reject it and the model proceeds to finish.
                        val responses =
                            listOf(
                                toolUseResponse(
                                    "tu_1",
                                    "propose_fix",
                                    """{"file":"phantom/Ghost.kt","original":"x","modified":"y","description":"d"}""",
                                ),
                                toolUseResponse(
                                    "tu_2",
                                    "finish",
                                    """{"summary":"s","root_cause":"none","pr_title":"fix: x","pr_body":"b","confidence":"HIGH"}""",
                                ),
                            )

                        val result = adapterWith(responses, scm).analyzeError(errorEvent(), repoContext)

                        result.changes.shouldBeEmpty()
                        // Empty change set forces LOW (matches MUST FIX 4 invariant).
                        result.confidence shouldBe Confidence.LOW
                    }
                }
            }
        }

        context("analyzeError - propose_fix with non-matching original snippet") {
            given("propose_fix's original is not present in the cached file") {
                then("the change is rejected and not accumulated") {
                    runTest {
                        val scm = mockk<ScmProviderPort>()
                        coEvery {
                            scm.getFileContent(any(), any(), any())
                        } returns "package x\nclass Foo"

                        val responses =
                            listOf(
                                toolUseResponse("tu_1", "read_file", """{"path":"src/Foo.kt"}"""),
                                toolUseResponse(
                                    "tu_2",
                                    "propose_fix",
                                    """{"file":"src/Foo.kt","original":"DOES_NOT_EXIST","modified":"y","description":"d"}""",
                                ),
                                toolUseResponse(
                                    "tu_3",
                                    "finish",
                                    """{"summary":"s","root_cause":"r","pr_title":"t","pr_body":"b","confidence":"HIGH"}""",
                                ),
                            )

                        val result = adapterWith(responses, scm).analyzeError(errorEvent(), repoContext)

                        result.changes.shouldBeEmpty()
                        result.confidence shouldBe Confidence.LOW
                    }
                }
            }
        }

        context("analyzeError - max iteration budget exhausted") {
            given("the model never calls finish") {
                then("returns LOW confidence with no changes") {
                    runTest {
                        val scm = mockk<ScmProviderPort>()
                        coEvery { scm.searchCode(any(), any(), any(), any()) } returns
                            listOf(
                                CodeSearchHit(
                                    path = CodeSearchHit.Path("a.kt"),
                                    line = CodeSearchHit.LineNumber(0),
                                    snippet = CodeSearchHit.Snippet("x"),
                                ),
                            )

                        // 30 search_code responses; loop caps at 25 then returns LOW.
                        val responses =
                            List(30) {
                                toolUseResponse("tu_$it", "search_code", """{"query":"x"}""")
                            }

                        val result = adapterWith(responses, scm).analyzeError(errorEvent(), repoContext)

                        result.changes.shouldBeEmpty()
                        result.confidence shouldBe Confidence.LOW
                    }
                }
            }
        }
    })

/**
 * Builds an Anthropic Messages API response containing a single `tool_use` block.
 * The `inputJson` argument MUST be valid JSON (object) — it is embedded as the tool's input.
 */
private fun toolUseResponse(
    toolUseId: String,
    toolName: String,
    inputJson: String,
): String =
    """
    {
      "id": "msg_$toolUseId",
      "type": "message",
      "role": "assistant",
      "model": "claude",
      "content": [
        {
          "type": "tool_use",
          "id": "$toolUseId",
          "name": "$toolName",
          "input": $inputJson
        }
      ],
      "stop_reason": "tool_use"
    }
    """.trimIndent()

private fun jsonResponse(
    status: HttpStatus,
    body: String,
): ClientResponse =
    ClientResponse
        .create(status)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .build()

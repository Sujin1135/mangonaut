package io.autofixer.mangonaut.infrastructure.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import io.autofixer.mangonaut.domain.exception.NoCommittableChangesException
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.PrParams
import io.autofixer.mangonaut.domain.model.RepoId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for [GitHubScmAdapter.commitFiles].
 *
 * The mocked GitHub API uses a route table keyed by `METHOD path-prefix`.
 * Each test asserts on the captured request stream so we can verify
 * which PUT/contents calls did and did not reach the API, including
 * the base64-decoded body content for find-and-replace verification.
 */
class GitHubScmAdapterTest :
    BehaviorSpec({

        val repoId = RepoId("acme/widget")
        val branch = PrParams.HeadBranch("fix/auto-fixer-1")
        val mapper = ObjectMapper()

        fun fileChange(
            filePath: String,
            original: String = "orig",
            modified: String = "fixed",
        ): FileChange =
            FileChange(
                file = FileChange.FilePath(filePath),
                description = FileChange.Description("desc"),
                original = FileChange.OriginalContent(original),
                modified = FileChange.ModifiedContent(modified),
            )

        /**
         * @param existingFiles map of path -> (sha, plaintext content). null value means GET 404.
         */
        fun stub(
            treeBlobs: List<String>,
            existingFiles: Map<String, Pair<String, String>?> = emptyMap(),
            truncated: Boolean = false,
        ): TestHarness {
            val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()
            val exchange =
                ExchangeFunction { req ->
                    val rawPath = req.url().rawPath
                    val capturedBody = extractBody(req)

                    recordedRequests.add(
                        RecordedRequest(
                            method = req.method(),
                            path = rawPath,
                            query = req.url().rawQuery,
                            body = capturedBody,
                        ),
                    )

                    when {
                        req.method() == HttpMethod.GET && rawPath.contains("/git/trees/") -> {
                            val body =
                                buildString {
                                    append("""{"sha":"x","truncated":$truncated,"tree":[""")
                                    treeBlobs.forEachIndexed { i, p ->
                                        if (i > 0) append(',')
                                        append("""{"path":"$p","type":"blob"}""")
                                    }
                                    append("]}")
                                }
                            Mono.just(jsonResponse(HttpStatus.OK, body))
                        }

                        req.method() == HttpMethod.GET && rawPath.contains("/contents/") -> {
                            val path = rawPath.substringAfter("/contents/")
                            if (existingFiles.containsKey(path)) {
                                val entry = existingFiles[path]
                                if (entry == null) {
                                    Mono.just(jsonResponse(HttpStatus.NOT_FOUND, """{"message":"Not Found"}"""))
                                } else {
                                    val (sha, content) = entry
                                    val encoded = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
                                    val body = """{"name":"f","path":"$path","sha":"$sha","content":"$encoded","encoding":"base64"}"""
                                    Mono.just(jsonResponse(HttpStatus.OK, body))
                                }
                            } else {
                                Mono.just(jsonResponse(HttpStatus.NOT_FOUND, """{"message":"Not Found"}"""))
                            }
                        }

                        req.method() == HttpMethod.PUT && rawPath.contains("/contents/") -> {
                            Mono.just(jsonResponse(HttpStatus.OK, """{"content":{"sha":"new"}}"""))
                        }

                        else -> Mono.just(jsonResponse(HttpStatus.OK, "{}"))
                    }
                }
            val client =
                WebClient
                    .builder()
                    .baseUrl("https://api.github.com")
                    .exchangeFunction(exchange)
                    .build()
            return TestHarness(GitHubScmAdapter(client), recordedRequests, mapper)
        }

        context("commitFiles - path re-resolution + find-and-replace") {

            given("a snippet that matches exactly once in the existing file") {
                then("PUTs the full file content with original->modified replaced (preserving rest of file)") {
                    runTest {
                        val existingContent =
                            """
                            package com.example

                            class UserService {
                                fun foo() = 1
                                fun bar() = 2
                            }
                            """.trimIndent()
                        val original = "fun foo() = 1"
                        val modified = "fun foo() = 42"

                        val harness =
                            stub(
                                treeBlobs = listOf("src/main/kotlin/com/example/UserService.kt"),
                                existingFiles =
                                    mapOf(
                                        "src/main/kotlin/com/example/UserService.kt" to ("abc123" to existingContent),
                                    ),
                            )

                        harness.adapter.commitFiles(
                            repoId = repoId,
                            branch = branch,
                            changes = listOf(fileChange("UserService.kt", original = original, modified = modified)),
                            message = "fix",
                        )

                        val puts = harness.requests.filter { it.method == HttpMethod.PUT }
                        puts shouldHaveSize 1
                        val put = puts.single()
                        put.path shouldBe "/repos/acme/widget/contents/src/main/kotlin/com/example/UserService.kt"

                        val sentContent = harness.decodeContent(put)
                        val expected = existingContent.replaceFirst(original, modified)
                        sentContent shouldBe expected
                        // Sanity: rest of file is preserved
                        sentContent.contains("package com.example") shouldBe true
                        sentContent.contains("fun bar() = 2") shouldBe true
                    }
                }
            }

            given("a snippet that does not appear in the existing file (LLM hallucination)") {
                then("skips that change without PUTting; other valid changes still commit") {
                    runTest {
                        val fooContent = "fun foo() = 1"
                        val barContent = "fun bar() = 2"
                        val harness =
                            stub(
                                treeBlobs = listOf("src/Foo.kt", "src/Bar.kt"),
                                existingFiles =
                                    mapOf(
                                        "src/Foo.kt" to ("sha-foo" to fooContent),
                                        "src/Bar.kt" to ("sha-bar" to barContent),
                                    ),
                            )

                        harness.adapter.commitFiles(
                            repoId = repoId,
                            branch = branch,
                            changes =
                                listOf(
                                    fileChange("Foo.kt", original = "DOES_NOT_EXIST", modified = "X"),
                                    fileChange("Bar.kt", original = "fun bar() = 2", modified = "fun bar() = 99"),
                                ),
                            message = "fix",
                        )

                        val puts = harness.requests.filter { it.method == HttpMethod.PUT }
                        puts shouldHaveSize 1
                        puts.single().path shouldBe "/repos/acme/widget/contents/src/Bar.kt"
                        harness.decodeContent(puts.single()) shouldBe "fun bar() = 99"
                    }
                }
            }

            given("a snippet that appears multiple times in the existing file") {
                then("skips that change as ambiguous and never PUTs") {
                    runTest {
                        val existing = "x = 1\ny = 1\nz = 1\n"
                        val harness =
                            stub(
                                treeBlobs = listOf("src/Multi.kt"),
                                existingFiles = mapOf("src/Multi.kt" to ("sha-m" to existing)),
                            )

                        shouldThrow<NoCommittableChangesException> {
                            harness.adapter.commitFiles(
                                repoId = repoId,
                                branch = branch,
                                changes = listOf(fileChange("Multi.kt", original = "= 1", modified = "= 2")),
                                message = "fix",
                            )
                        }

                        harness.requests.filter { it.method == HttpMethod.PUT }.shouldBeEmpty()
                    }
                }
            }

            given("an empty original snippet") {
                then("skips the change (empty would match everywhere)") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs = listOf("src/Foo.kt"),
                                existingFiles = mapOf("src/Foo.kt" to ("sha-f" to "fun foo() = 1\n")),
                            )

                        shouldThrow<NoCommittableChangesException> {
                            harness.adapter.commitFiles(
                                repoId = repoId,
                                branch = branch,
                                changes = listOf(fileChange("Foo.kt", original = "", modified = "anything")),
                                message = "fix",
                            )
                        }

                        harness.requests.filter { it.method == HttpMethod.PUT }.shouldBeEmpty()
                    }
                }
            }

            given("an existing file in LF and an LLM snippet in CRLF") {
                then("normalizes both sides and matches; PUTs the LF-normalized replacement") {
                    runTest {
                        val existing = "line1\nfun foo() = 1\nline3\n"
                        val original = "fun foo() = 1\r\n" // CRLF from LLM
                        val modified = "fun foo() = 42\r\n"

                        val harness =
                            stub(
                                treeBlobs = listOf("src/Foo.kt"),
                                existingFiles = mapOf("src/Foo.kt" to ("sha-f" to existing)),
                            )

                        harness.adapter.commitFiles(
                            repoId = repoId,
                            branch = branch,
                            changes = listOf(fileChange("Foo.kt", original = original, modified = modified)),
                            message = "fix",
                        )

                        val puts = harness.requests.filter { it.method == HttpMethod.PUT }
                        puts shouldHaveSize 1
                        val sentContent = harness.decodeContent(puts.single())
                        // Result is LF-normalized end-to-end
                        sentContent shouldBe "line1\nfun foo() = 42\nline3\n"
                    }
                }
            }

            // ---- existing path-resolution guard cases (kept) ----

            given("an LLM-echoed short path that uniquely matches one full path in the tree") {
                then("re-resolves to the full path and PUTs there") {
                    runTest {
                        val existing = "orig text"
                        val harness =
                            stub(
                                treeBlobs = listOf("src/main/kotlin/com/example/UserService.kt", "README.md"),
                                existingFiles =
                                    mapOf(
                                        "src/main/kotlin/com/example/UserService.kt" to ("abc123" to existing),
                                    ),
                            )

                        harness.adapter.commitFiles(
                            repoId = repoId,
                            branch = branch,
                            changes = listOf(fileChange("UserService.kt", original = "orig", modified = "fixed")),
                            message = "fix",
                        )

                        val puts = harness.requests.filter { it.method == HttpMethod.PUT }
                        puts shouldHaveSize 1
                        puts.single().path shouldBe "/repos/acme/widget/contents/src/main/kotlin/com/example/UserService.kt"
                        harness.decodeContent(puts.single()) shouldBe "fixed text"
                    }
                }
            }

            given("a short path with zero matches in the tree") {
                then("throws NoCommittableChangesException and never PUTs") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs = listOf("src/main/kotlin/com/example/Other.kt"),
                            )

                        shouldThrow<NoCommittableChangesException> {
                            harness.adapter.commitFiles(
                                repoId = repoId,
                                branch = branch,
                                changes = listOf(fileChange("io/contents/collector/SentryTestRunner.kt")),
                                message = "fix",
                            )
                        }

                        harness.requests.filter { it.method == HttpMethod.PUT }.shouldBeEmpty()
                    }
                }
            }

            given("a short path that matches multiple full paths") {
                then("throws NoCommittableChangesException for the ambiguous-only batch and never PUTs") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs =
                                    listOf(
                                        "module-a/src/Foo.kt",
                                        "module-b/src/Foo.kt",
                                    ),
                                existingFiles =
                                    mapOf(
                                        "module-a/src/Foo.kt" to ("sha-a" to "orig"),
                                        "module-b/src/Foo.kt" to ("sha-b" to "orig"),
                                    ),
                            )

                        shouldThrow<NoCommittableChangesException> {
                            harness.adapter.commitFiles(
                                repoId = repoId,
                                branch = branch,
                                changes = listOf(fileChange("Foo.kt")),
                                message = "fix",
                            )
                        }

                        harness.requests.filter { it.method == HttpMethod.PUT }.shouldBeEmpty()
                    }
                }
            }

            given("a resolved path that has no existing sha (would be a new file)") {
                then("throws NoCommittableChangesException and never PUTs") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs = listOf("src/Foo.kt"),
                                existingFiles = emptyMap(), // GET /contents returns 404
                            )

                        shouldThrow<NoCommittableChangesException> {
                            harness.adapter.commitFiles(
                                repoId = repoId,
                                branch = branch,
                                changes = listOf(fileChange("Foo.kt")),
                                message = "fix",
                            )
                        }

                        harness.requests.filter { it.method == HttpMethod.PUT }.shouldBeEmpty()
                    }
                }
            }

            given("multiple changes where every change is invalid") {
                then("throws NoCommittableChangesException so the caller can abort PR creation") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs = listOf("src/Other.kt"),
                            )

                        shouldThrow<NoCommittableChangesException> {
                            harness.adapter.commitFiles(
                                repoId = repoId,
                                branch = branch,
                                changes =
                                    listOf(
                                        fileChange("Missing.kt"),
                                        fileChange("AlsoMissing.kt"),
                                    ),
                                message = "fix",
                            )
                        }

                        harness.requests.filter { it.method == HttpMethod.PUT }.shouldBeEmpty()
                    }
                }
            }

            given("a truncated tree response with a matching blob") {
                then("still resolves the path and PUTs (proceeds with partial result)") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs = listOf("src/main/kotlin/com/example/UserService.kt"),
                                existingFiles =
                                    mapOf(
                                        "src/main/kotlin/com/example/UserService.kt" to ("abc123" to "orig content"),
                                    ),
                                truncated = true,
                            )

                        harness.adapter.commitFiles(
                            repoId = repoId,
                            branch = branch,
                            changes = listOf(fileChange("UserService.kt", original = "orig", modified = "fixed")),
                            message = "fix",
                        )

                        val puts = harness.requests.filter { it.method == HttpMethod.PUT }
                        puts shouldHaveSize 1
                        puts.single().path shouldBe "/repos/acme/widget/contents/src/main/kotlin/com/example/UserService.kt"
                    }
                }
            }

            given("a mix of valid and invalid changes") {
                then("only PUTs the valid one") {
                    runTest {
                        val harness =
                            stub(
                                treeBlobs =
                                    listOf(
                                        "src/Foo.kt",
                                        "src/Bar.kt",
                                    ),
                                // Bar.kt has no sha (would be new file); Foo.kt has sha + matching content
                                existingFiles = mapOf("src/Foo.kt" to ("sha-foo" to "orig-foo content")),
                            )

                        harness.adapter.commitFiles(
                            repoId = repoId,
                            branch = branch,
                            changes =
                                listOf(
                                    fileChange("Foo.kt", original = "orig-foo", modified = "fixed-foo"),
                                    fileChange("Bar.kt", original = "x", modified = "y"),
                                    fileChange("Ghost.kt"),
                                ),
                            message = "fix",
                        )

                        val puts = harness.requests.filter { it.method == HttpMethod.PUT }
                        puts shouldHaveSize 1
                        puts.single().path shouldBe "/repos/acme/widget/contents/src/Foo.kt"
                    }
                }
            }
        }
    })

private data class RecordedRequest(
    val method: HttpMethod,
    val path: String,
    val query: String?,
    val body: String,
)

private class TestHarness(
    val adapter: GitHubScmAdapter,
    val requests: CopyOnWriteArrayList<RecordedRequest>,
    private val mapper: ObjectMapper,
) {
    fun decodeContent(req: RecordedRequest): String {
        val node = mapper.readTree(req.body)
        val encoded = node.get("content").asText()
        return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }
}

private fun jsonResponse(
    status: HttpStatus,
    body: String,
): ClientResponse =
    ClientResponse
        .create(status)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .build()

/**
 * Extracts the request body as a string by replaying the captured [ClientRequest]
 * into a [MockClientHttpRequest] and joining the resulting data buffers.
 */
private fun extractBody(req: ClientRequest): String {
    val mock = MockClientHttpRequest(req.method(), req.url())
    req.writeTo(mock, ExchangeStrategies.withDefaults()).block()
    return DataBufferUtils
        .join(mock.body)
        .map { buf ->
            val bytes = ByteArray(buf.readableByteCount())
            buf.read(bytes)
            DataBufferUtils.release(buf)
            String(bytes, Charsets.UTF_8)
        }.defaultIfEmpty("")
        .block()
        ?: ""
}

package io.autofixer.mangonaut.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PullRequestTest : BehaviorSpec({

    context("RepoId") {
        given("a valid owner/repo format via factory method") {
            val repoId = RepoId.of("myorg/my-repo")

            then("should parse owner correctly") {
                repoId.owner shouldBe "myorg"
            }

            then("should parse repo correctly") {
                repoId.repo shouldBe "my-repo"
            }

            then("should preserve full value") {
                repoId.value shouldBe "myorg/my-repo"
            }
        }

        given("a value with multiple slashes via factory method") {
            val repoId = RepoId.of("myorg/sub/repo")

            then("should parse owner as text before first slash") {
                repoId.owner shouldBe "myorg"
            }

            then("should parse repo as text after first slash") {
                repoId.repo shouldBe "sub/repo"
            }
        }

        given("a value without a slash via factory method") {
            then("should throw IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    RepoId.of("invalid-repo")
                }
            }
        }

        given("an empty string via factory method") {
            then("should throw IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    RepoId.of("")
                }
            }
        }
    }

    context("PrParams") {
        given("valid PR parameters") {
            val params = PrParams(
                title = PrParams.Title("fix: Handle NPE in UserService"),
                body = PrParams.Body("## Summary\nFix NPE"),
                baseBranch = PrParams.BaseBranch("main"),
                headBranch = PrParams.HeadBranch("fix/mangonaut-123"),
                labels = listOf(PrParams.Label("auto-fix"), PrParams.Label("ai-generated")),
            )

            then("should store all values correctly") {
                params.title.value shouldBe "fix: Handle NPE in UserService"
                params.body.value shouldBe "## Summary\nFix NPE"
                params.baseBranch.value shouldBe "main"
                params.headBranch.value shouldBe "fix/mangonaut-123"
                params.labels.map { it.value } shouldBe listOf("auto-fix", "ai-generated")
            }
        }

        given("PR parameters with empty labels") {
            val params = PrParams(
                title = PrParams.Title("fix: something"),
                body = PrParams.Body("body"),
                baseBranch = PrParams.BaseBranch("main"),
                headBranch = PrParams.HeadBranch("fix/branch"),
            )

            then("labels should default to empty list") {
                params.labels shouldBe emptyList()
            }
        }
    }

    context("PrResult") {
        given("a PR result") {
            val result = PrResult(
                number = PrResult.Number(42),
                url = PrResult.Url("https://api.github.com/repos/org/repo/pulls/42"),
                htmlUrl = PrResult.HtmlUrl("https://github.com/org/repo/pull/42"),
                state = PrResult.State("open"),
            )

            then("should store all values correctly") {
                result.number.value shouldBe 42
                result.url.value shouldBe "https://api.github.com/repos/org/repo/pulls/42"
                result.htmlUrl.value shouldBe "https://github.com/org/repo/pull/42"
                result.state.value shouldBe "open"
            }
        }
    }
})

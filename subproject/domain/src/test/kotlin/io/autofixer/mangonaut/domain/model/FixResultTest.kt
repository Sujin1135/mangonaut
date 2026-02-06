package io.autofixer.mangonaut.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FixResultTest : BehaviorSpec({

    fun createFileChange(
        original: String = "val x = null",
        modified: String = "val x = \"default\"",
    ): FileChange = FileChange(
        file = FileChange.FilePath("src/main/kotlin/UserService.kt"),
        description = FileChange.Description("Fix null assignment"),
        original = FileChange.OriginalContent(original),
        modified = FileChange.ModifiedContent(modified),
    )

    fun createFixResult(
        confidence: Confidence = Confidence.HIGH,
        changes: List<FileChange> = listOf(createFileChange()),
    ): FixResult = FixResult(
        analysis = FixResult.Analysis("NullPointerException due to uninitialized value"),
        rootCause = FixResult.RootCause("Variable x is null"),
        confidence = confidence,
        changes = changes,
        prTitle = FixResult.PrTitle("fix: Handle null value in UserService"),
        prBody = FixResult.PrBody("## Summary\nFix NullPointerException"),
    )

    context("FixResult.canCreateAutoPr") {
        given("HIGH confidence with changes and minConfidence=MEDIUM") {
            val result = createFixResult(confidence = Confidence.HIGH)

            then("should allow auto PR creation") {
                result.canCreateAutoPr(Confidence.MEDIUM) shouldBe true
            }
        }

        given("MEDIUM confidence with changes and minConfidence=MEDIUM") {
            val result = createFixResult(confidence = Confidence.MEDIUM)

            then("should allow auto PR creation") {
                result.canCreateAutoPr(Confidence.MEDIUM) shouldBe true
            }
        }

        given("LOW confidence with changes and minConfidence=MEDIUM") {
            val result = createFixResult(confidence = Confidence.LOW)

            then("should not allow auto PR creation") {
                result.canCreateAutoPr(Confidence.MEDIUM) shouldBe false
            }
        }

        given("HIGH confidence but no changes") {
            val result = createFixResult(
                confidence = Confidence.HIGH,
                changes = emptyList(),
            )

            then("should not allow auto PR creation") {
                result.canCreateAutoPr(Confidence.LOW) shouldBe false
            }
        }

        given("LOW confidence with changes and minConfidence=LOW") {
            val result = createFixResult(confidence = Confidence.LOW)

            then("should allow auto PR creation") {
                result.canCreateAutoPr(Confidence.LOW) shouldBe true
            }
        }

        given("HIGH confidence with changes and minConfidence=HIGH") {
            val result = createFixResult(confidence = Confidence.HIGH)

            then("should allow auto PR creation") {
                result.canCreateAutoPr(Confidence.HIGH) shouldBe true
            }
        }

        given("MEDIUM confidence with changes and minConfidence=HIGH") {
            val result = createFixResult(confidence = Confidence.MEDIUM)

            then("should not allow auto PR creation") {
                result.canCreateAutoPr(Confidence.HIGH) shouldBe false
            }
        }
    }

    context("FileChange.hasChanges") {
        given("different original and modified content") {
            val change = createFileChange(
                original = "val x = null",
                modified = "val x = \"default\"",
            )

            then("should return true") {
                change.hasChanges() shouldBe true
            }
        }

        given("identical original and modified content") {
            val change = createFileChange(
                original = "val x = null",
                modified = "val x = null",
            )

            then("should return false") {
                change.hasChanges() shouldBe false
            }
        }

        given("empty strings for both") {
            val change = createFileChange(original = "", modified = "")

            then("should return false") {
                change.hasChanges() shouldBe false
            }
        }
    }
})

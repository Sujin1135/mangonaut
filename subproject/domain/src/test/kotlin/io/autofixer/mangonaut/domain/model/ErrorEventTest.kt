package io.autofixer.mangonaut.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class ErrorEventTest : BehaviorSpec({

    val now = Instant.now()

    fun createStackFrame(
        filename: String = "UserService.kt",
        function: String = "getUser",
        lineNo: Int = 42,
        inApp: Boolean = true,
    ): StackFrame = StackFrame(
        filename = StackFrame.Filename(filename),
        function = StackFrame.FunctionName(function),
        lineNo = StackFrame.LineNumber(lineNo),
        inApp = StackFrame.InApp(inApp),
    )

    fun createErrorEvent(
        stackTrace: List<StackFrame> = emptyList(),
    ): ErrorEvent = ErrorEvent(
        id = ErrorEvent.Id("issue-123"),
        title = ErrorEvent.Title("NullPointerException"),
        errorType = ErrorEvent.ErrorType("NullPointerException"),
        errorMessage = ErrorEvent.ErrorMessage("Cannot invoke method on null"),
        stackTrace = stackTrace,
        breadcrumbs = emptyList(),
        tags = emptyMap(),
        sourceProject = ErrorEvent.SourceProject("my-backend"),
        timestamp = now,
    )

    context("ErrorEvent") {
        given("a mix of application and library stack frames") {
            val appFrame1 = createStackFrame(filename = "UserService.kt", inApp = true)
            val appFrame2 = createStackFrame(filename = "OrderService.kt", inApp = true)
            val libFrame1 = createStackFrame(filename = "Spring.kt", inApp = false)
            val libFrame2 = createStackFrame(filename = "Reactor.kt", inApp = false)

            val event = createErrorEvent(
                stackTrace = listOf(appFrame1, libFrame1, appFrame2, libFrame2),
            )

            `when`("filtering application stack frames") {
                val result = event.applicationStackFrames()

                then("should return only application code frames") {
                    result shouldHaveSize 2
                    result[0].filename.value shouldBe "UserService.kt"
                    result[1].filename.value shouldBe "OrderService.kt"
                }
            }
        }

        given("only library stack frames") {
            val event = createErrorEvent(
                stackTrace = listOf(
                    createStackFrame(inApp = false),
                    createStackFrame(inApp = false),
                ),
            )

            `when`("filtering application stack frames") {
                val result = event.applicationStackFrames()

                then("should return an empty list") {
                    result.shouldBeEmpty()
                }
            }
        }

        given("an empty stack trace") {
            val event = createErrorEvent(stackTrace = emptyList())

            `when`("filtering application stack frames") {
                val result = event.applicationStackFrames()

                then("should return an empty list") {
                    result.shouldBeEmpty()
                }
            }
        }
    }

    context("StackFrame") {
        given("an in-app stack frame") {
            val frame = createStackFrame(inApp = true)

            then("isApplicationCode should return true") {
                frame.isApplicationCode() shouldBe true
            }
        }

        given("a library stack frame") {
            val frame = createStackFrame(inApp = false)

            then("isApplicationCode should return false") {
                frame.isApplicationCode() shouldBe false
            }
        }
    }
})

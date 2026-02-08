package io.autofixer.mangonaut.application.usecase

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.FixResult
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.model.StackFrame
import java.time.Instant

/**
 * Common test fixtures used in Application layer tests.
 */
object TestFixtures {

    val REPO_ID = RepoId.of("testorg/test-repo")
    const val DEFAULT_BRANCH = "main"
    val SOURCE_ROOTS = listOf("src/main/kotlin/")
    const val BRANCH_PREFIX = "fix/mangonaut-"
    val LABELS = listOf("auto-fix", "ai-generated")

    fun createStackFrame(
        filename: String = "com/example/UserService.kt",
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
        issueId: String = "issue-123",
        stackTrace: List<StackFrame> = listOf(
            createStackFrame(filename = "com/example/UserService.kt", inApp = true),
            createStackFrame(filename = "org/spring/Framework.kt", inApp = false),
        ),
    ): ErrorEvent = ErrorEvent(
        id = ErrorEvent.Id(issueId),
        title = ErrorEvent.Title("NullPointerException in UserService"),
        errorType = ErrorEvent.ErrorType("NullPointerException"),
        errorMessage = ErrorEvent.ErrorMessage("Cannot invoke method on null"),
        stackTrace = stackTrace,
        breadcrumbs = emptyList(),
        tags = mapOf("environment" to "production"),
        sourceProject = ErrorEvent.SourceProject("my-backend"),
        timestamp = Instant.parse("2026-02-07T10:00:00Z"),
    )

    fun createFileChange(
        file: String = "com/example/UserService.kt",
        original: String = "val user = repository.findById(id)",
        modified: String = "val user = repository.findById(id) ?: throw UserNotFoundException(id)",
    ): FileChange = FileChange(
        file = FileChange.FilePath(file),
        description = FileChange.Description("Add null check for user lookup"),
        original = FileChange.OriginalContent(original),
        modified = FileChange.ModifiedContent(modified),
    )

    fun createFixResult(
        confidence: Confidence = Confidence.HIGH,
        changes: List<FileChange> = listOf(createFileChange()),
    ): FixResult = FixResult(
        analysis = FixResult.Analysis("NullPointerException occurs when user is not found"),
        rootCause = FixResult.RootCause("Missing null check after repository lookup"),
        confidence = confidence,
        changes = changes,
        prTitle = FixResult.PrTitle("fix: Handle null user in UserService"),
        prBody = FixResult.PrBody("## Summary\nFix NPE when user is not found"),
    )

    fun createPrResult(
        number: Int = 42,
    ): PrResult = PrResult(
        number = PrResult.Number(number),
        url = PrResult.Url("https://api.github.com/repos/testorg/test-repo/pulls/$number"),
        htmlUrl = PrResult.HtmlUrl("https://github.com/testorg/test-repo/pull/$number"),
        state = PrResult.State("open"),
    )
}

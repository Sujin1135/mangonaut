package io.autofixer.mangonaut.domain.model

/**
 * Domain model representing the LLM analysis result.
 */
data class FixResult(
    val analysis: Analysis,
    val rootCause: RootCause,
    val confidence: Confidence,
    val changes: List<FileChange>,
    val prTitle: PrTitle,
    val prBody: PrBody,
) {
    @JvmInline
    value class Analysis(val value: String)

    @JvmInline
    value class RootCause(val value: String)

    @JvmInline
    value class PrTitle(val value: String)

    @JvmInline
    value class PrBody(val value: String)

    /**
     * Checks whether an automatic PR can be created.
     */
    fun canCreateAutoPr(minConfidence: Confidence): Boolean =
        confidence.ordinal >= minConfidence.ordinal && changes.isNotEmpty()
}

/**
 * Code change entry.
 */
data class FileChange(
    val file: FilePath,
    val description: Description,
    val original: OriginalContent,
    val modified: ModifiedContent,
) {
    @JvmInline
    value class FilePath(val value: String)

    @JvmInline
    value class Description(val value: String)

    @JvmInline
    value class OriginalContent(val value: String)

    @JvmInline
    value class ModifiedContent(val value: String)

    /**
     * Checks whether there are actual changes.
     */
    fun hasChanges(): Boolean = original.value != modified.value
}

/**
 * Confidence level of the AI analysis.
 */
enum class Confidence {
    /**
     * Low confidence - no PR created, analysis result only logged/notified.
     */
    LOW,

    /**
     * Medium confidence - PR creation depends on configuration.
     */
    MEDIUM,

    /**
     * High confidence - automatic PR creation.
     */
    HIGH,
}

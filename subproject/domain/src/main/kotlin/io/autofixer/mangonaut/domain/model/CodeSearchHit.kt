package io.autofixer.mangonaut.domain.model

/**
 * A single match returned by [io.autofixer.mangonaut.domain.port.ScmProviderPort.searchCode].
 */
data class CodeSearchHit(
    val path: Path,
    val line: LineNumber,
    val snippet: Snippet,
) {
    @JvmInline
    value class Path(
        val value: String,
    )

    @JvmInline
    value class LineNumber(
        val value: Int,
    )

    @JvmInline
    value class Snippet(
        val value: String,
    )
}

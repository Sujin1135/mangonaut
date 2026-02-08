package io.autofixer.mangonaut.domain.model

/**
 * Pull Request creation parameters.
 */
data class PrParams(
    val title: Title,
    val body: Body,
    val baseBranch: BaseBranch,
    val headBranch: HeadBranch,
    val labels: List<Label> = emptyList(),
) {
    @JvmInline
    value class Title(val value: String)

    @JvmInline
    value class Body(val value: String)

    @JvmInline
    value class BaseBranch(val value: String)

    @JvmInline
    value class HeadBranch(val value: String)

    @JvmInline
    value class Label(val value: String)
}

/**
 * Pull Request creation result.
 */
data class PrResult(
    val number: Number,
    val url: Url,
    val htmlUrl: HtmlUrl,
    val state: State,
) {
    @JvmInline
    value class Number(val value: Int)

    @JvmInline
    value class Url(val value: String)

    @JvmInline
    value class HtmlUrl(val value: String)

    @JvmInline
    value class State(val value: String)
}

/**
 * Repository identifier.
 */
@JvmInline
value class RepoId(val value: String) {
    val owner: String get() = value.substringBefore("/")
    val repo: String get() = value.substringAfter("/")

    companion object {
        /**
         * Factory method with validation.
         * @throws IllegalArgumentException if not in 'owner/repo' format
         */
        fun of(value: String): RepoId {
            require(value.contains("/")) { "RepoId must be in 'owner/repo' format: $value" }
            return RepoId(value)
        }
    }
}

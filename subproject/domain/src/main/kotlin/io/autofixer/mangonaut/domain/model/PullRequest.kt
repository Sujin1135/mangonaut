package io.autofixer.mangonaut.domain.model

/**
 * Pull Request 생성 파라미터
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
 * Pull Request 생성 결과
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
 * Repository 식별자
 */
@JvmInline
value class RepoId(val value: String) {
    val owner: String get() = value.substringBefore("/")
    val repo: String get() = value.substringAfter("/")

    companion object {
        /**
         * 검증을 포함한 팩토리 메서드
         * @throws IllegalArgumentException 'owner/repo' 형식이 아닌 경우
         */
        fun of(value: String): RepoId {
            require(value.contains("/")) { "RepoId must be in 'owner/repo' format: $value" }
            return RepoId(value)
        }
    }
}

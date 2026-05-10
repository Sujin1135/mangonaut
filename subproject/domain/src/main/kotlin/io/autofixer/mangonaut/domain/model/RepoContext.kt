package io.autofixer.mangonaut.domain.model

/**
 * Repository context required by the agentic LLM analyzer to drive
 * tool calls (read_file / list_directory / search_code) against the
 * correct repo at the correct ref.
 */
data class RepoContext(
    val repoId: RepoId,
    val ref: Ref,
) {
    @JvmInline
    value class Ref(
        val value: String,
    )

    companion object {
        fun of(
            repoId: RepoId,
            defaultBranch: String,
        ): RepoContext = RepoContext(repoId = repoId, ref = Ref(defaultBranch))
    }
}

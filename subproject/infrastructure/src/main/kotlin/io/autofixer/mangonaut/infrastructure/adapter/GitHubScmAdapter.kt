package io.autofixer.mangonaut.infrastructure.adapter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.autofixer.mangonaut.domain.exception.GitHubApiException
import io.autofixer.mangonaut.domain.exception.NoCommittableChangesException
import io.autofixer.mangonaut.domain.model.CodeSearchHit
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.PrParams
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Base64

/**
 * ScmProviderPort implementation using the GitHub API.
 */
@Component
class GitHubScmAdapter(
    private val githubWebClient: WebClient,
) : ScmProviderPort {
    private val log = LoggerFactory.getLogger(GitHubScmAdapter::class.java)

    override val name: String = "github"

    override suspend fun getFileContent(
        repoId: RepoId,
        path: FileChange.FilePath,
        ref: String,
    ): String {
        try {
            val response =
                githubWebClient
                    .get()
                    .uri("/repos/${repoId.value}/contents/${path.value}?ref=$ref")
                    .retrieve()
                    .bodyToMono(GitHubContentResponse::class.java)
                    .awaitSingle()

            return String(Base64.getDecoder().decode(response.content.replace("\n", "")))
        } catch (e: WebClientResponseException) {
            throw GitHubApiException(
                message = "Failed to get file content: ${path.value} - ${e.statusCode}",
                cause = e,
            )
        }
    }

    override suspend fun createBranch(
        repoId: RepoId,
        baseBranch: PrParams.BaseBranch,
        newBranch: PrParams.HeadBranch,
    ) {
        try {
            // Fetch the latest commit SHA of the base branch
            val refResponse =
                githubWebClient
                    .get()
                    .uri("/repos/${repoId.value}/git/refs/heads/${baseBranch.value}")
                    .retrieve()
                    .bodyToMono(GitHubRefResponse::class.java)
                    .awaitSingle()

            // Create new branch
            githubWebClient
                .post()
                .uri("/repos/${repoId.value}/git/refs")
                .bodyValue(
                    mapOf(
                        "ref" to "refs/heads/${newBranch.value}",
                        "sha" to refResponse.`object`.sha,
                    ),
                ).retrieve()
                .toBodilessEntity()
                .awaitSingle()
        } catch (e: WebClientResponseException) {
            throw GitHubApiException(
                message = "Failed to create branch: ${newBranch.value} - ${e.statusCode}",
                cause = e,
            )
        }
    }

    override suspend fun commitFiles(
        repoId: RepoId,
        branch: PrParams.HeadBranch,
        changes: List<FileChange>,
        message: String,
    ) {
        if (changes.isEmpty()) {
            log.info("commitFiles called with no changes for repo=${repoId.value} branch=${branch.value}")
            return
        }

        try {
            // Phase 1 guard: re-resolve every change against the repo tree to avoid
            // committing LLM-echoed short paths as brand-new files at the repo root.
            val blobPaths = fetchBlobPaths(repoId, branch.value)

            var committed = 0
            for (change in changes) {
                val rawPath = change.file.value
                val candidates = matchByEndsWith(blobPaths, rawPath)

                val resolvedPath =
                    when {
                        candidates.isEmpty() -> {
                            log.warn(
                                "Skipping change: no tree entry ends with '{}' (repo={}, branch={})",
                                rawPath,
                                repoId.value,
                                branch.value,
                            )
                            continue
                        }
                        candidates.size > 1 -> {
                            log.warn(
                                "Skipping change: ambiguous path '{}' matched {} candidates (sample={}) (repo={}, branch={})",
                                rawPath,
                                candidates.size,
                                candidates.take(5),
                                repoId.value,
                                branch.value,
                            )
                            continue
                        }
                        else -> candidates.single()
                    }

                // Phase 1 guard: never create new files. Require a sha for the resolved path.
                // 404 → treat as "file does not exist" and skip (handled below).
                // Any other 4xx/5xx is rethrown by WebClient and propagates to the outer
                // try/catch, which wraps it as GitHubApiException → fails the entire
                // commitFiles call. This is intentional: a transient 5xx must NOT be
                // silently swallowed as "skip".
                val existingFile =
                    try {
                        githubWebClient
                            .get()
                            .uri("/repos/${repoId.value}/contents/$resolvedPath?ref=${branch.value}")
                            .retrieve()
                            .bodyToMono(GitHubContentResponse::class.java)
                            .awaitSingleOrNull()
                    } catch (e: WebClientResponseException.NotFound) {
                        null
                    }

                val sha = existingFile?.sha
                if (sha.isNullOrBlank()) {
                    log.warn(
                        "Skipping change: no existing sha for '{}' (repo={}, branch={}). New-file creation is disabled.",
                        resolvedPath,
                        repoId.value,
                        branch.value,
                    )
                    continue
                }

                // GitHub Contents API replaces the whole file. The LLM only returns a snippet,
                // so we must apply find-and-replace against the existing content (decoded from
                // base64) and PUT the resulting full-file content. Otherwise the rest of the
                // file (other methods, imports, package declaration) is wiped.
                val existingContent =
                    String(
                        Base64.getDecoder().decode(existingFile.content.replace("\n", "")),
                        Charsets.UTF_8,
                    )

                // Normalize line endings (CRLF -> LF) on both sides for matching purposes.
                // GitHub stores blobs in LF, but the LLM may emit CRLF; we normalize so the
                // snippet matches, and we PUT the LF-normalized result for consistency.
                val existingNormalized = existingContent.replace("\r\n", "\n")
                val originalSnippet = change.original.value.replace("\r\n", "\n")
                val modifiedSnippet = change.modified.value.replace("\r\n", "\n")

                if (originalSnippet.isEmpty()) {
                    log.warn(
                        "Skipping change: empty original snippet (repo={}, branch={}, path={})",
                        repoId.value,
                        branch.value,
                        resolvedPath,
                    )
                    continue
                }

                val occurrences = countOccurrences(existingNormalized, originalSnippet)
                when {
                    occurrences == 0 -> {
                        log.warn(
                            "Original snippet not found in existing file: path={}, snippetPreview={}",
                            resolvedPath,
                            snippetPreview(originalSnippet),
                        )
                        continue
                    }
                    occurrences > 1 -> {
                        log.warn(
                            "Original snippet matches multiple locations, ambiguous: path={}, count={}",
                            resolvedPath,
                            occurrences,
                        )
                        continue
                    }
                }

                val newContent = existingNormalized.replaceFirst(originalSnippet, modifiedSnippet)

                val requestBody =
                    mapOf(
                        "message" to message,
                        "content" to Base64.getEncoder().encodeToString(newContent.toByteArray(Charsets.UTF_8)),
                        "branch" to branch.value,
                        "sha" to sha,
                    )

                githubWebClient
                    .put()
                    .uri("/repos/${repoId.value}/contents/$resolvedPath")
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .awaitSingle()

                committed++
            }

            if (committed == 0) {
                throw NoCommittableChangesException(
                    "No committable changes for repo=${repoId.value} branch=${branch.value} " +
                        "(all ${changes.size} change(s) skipped by path-resolution guard)",
                )
            }
        } catch (e: WebClientResponseException) {
            throw GitHubApiException(
                message = "Failed to commit files - ${e.statusCode}",
                cause = e,
            )
        }
    }

    override suspend fun createPullRequest(
        repoId: RepoId,
        params: PrParams,
    ): PrResult {
        try {
            val response =
                githubWebClient
                    .post()
                    .uri("/repos/${repoId.value}/pulls")
                    .bodyValue(
                        mapOf(
                            "title" to params.title.value,
                            "body" to params.body.value,
                            "head" to params.headBranch.value,
                            "base" to params.baseBranch.value,
                        ),
                    ).retrieve()
                    .bodyToMono(GitHubPrResponse::class.java)
                    .awaitSingle()

            // Add labels
            if (params.labels.isNotEmpty()) {
                githubWebClient
                    .post()
                    .uri("/repos/${repoId.value}/issues/${response.number}/labels")
                    .bodyValue(
                        mapOf("labels" to params.labels.map { it.value }),
                    ).retrieve()
                    .toBodilessEntity()
                    .awaitSingle()
            }

            return PrResult(
                number = PrResult.Number(response.number),
                url = PrResult.Url(response.url),
                htmlUrl = PrResult.HtmlUrl(response.htmlUrl),
                state = PrResult.State(response.state),
            )
        } catch (e: WebClientResponseException) {
            throw GitHubApiException(
                message = "Failed to create PR - ${e.statusCode}",
                cause = e,
            )
        }
    }

    override suspend fun resolveFilePaths(
        repoId: RepoId,
        filenames: List<String>,
        ref: String,
    ): Map<String, FileChange.FilePath> {
        val blobPaths = fetchBlobPaths(repoId, ref)
        return filenames
            .mapNotNull { filename ->
                val candidates = matchByEndsWith(blobPaths, filename)
                candidates.singleOrNull()?.let { filename to FileChange.FilePath(it) }
            }.toMap()
    }

    override suspend fun listDirectory(
        repoId: RepoId,
        path: String,
        ref: String,
        recursive: Boolean,
    ): List<String> {
        val normalizedPrefix = path.trim().trim('/')

        // For listing the repo root (or recursively from any prefix) we reuse the
        // git-trees API used by path resolution. For a non-recursive listing under
        // a specific subpath we still walk the recursive tree and slice locally;
        // GitHub's contents API would give a flat directory listing but lacks the
        // single-call efficiency we get from cached fetchBlobPaths-style traversal.
        val tree = fetchBlobPaths(repoId, ref)

        val under =
            if (normalizedPrefix.isEmpty()) {
                tree
            } else {
                val prefix = "$normalizedPrefix/"
                tree.filter { it.startsWith(prefix) }
            }

        if (recursive) return under

        // Top-level listing under [normalizedPrefix]: take the first segment after the prefix.
        val depth = if (normalizedPrefix.isEmpty()) 0 else normalizedPrefix.count { it == '/' } + 1
        return under
            .map { it.split('/').take(depth + 1).joinToString("/") }
            .distinct()
    }

    override suspend fun searchCode(
        repoId: RepoId,
        query: String,
        ref: String,
        pathPrefix: String?,
    ): List<CodeSearchHit> {
        // GitHub's Search Code API does not honor an arbitrary `ref`; results are
        // computed against the default branch. The agent's `ref` is captured by
        // read_file (which IS branch-scoped), so search is used purely as a
        // discovery/index hint here. We surface only path/line/snippet — never
        // commit decisions are based on raw search output.
        val q =
            buildString {
                append(query.trim())
                append(" repo:${repoId.value}")
                if (!pathPrefix.isNullOrBlank()) {
                    append(" path:${pathPrefix.trim().trim('/')}")
                }
            }

        return try {
            val response =
                githubWebClient
                    .get()
                    .uri { b ->
                        b
                            .path("/search/code")
                            .queryParam("q", q)
                            .queryParam("per_page", 20)
                            .build()
                    }.header("Accept", "application/vnd.github.text-match+json")
                    .retrieve()
                    .bodyToMono(GitHubSearchCodeResponse::class.java)
                    .awaitSingle()

            response.items.take(20).map { item ->
                val firstFragment =
                    item.textMatches
                        ?.firstOrNull()
                        ?.fragment
                        .orEmpty()
                CodeSearchHit(
                    path = CodeSearchHit.Path(item.path),
                    line = CodeSearchHit.LineNumber(0),
                    snippet = CodeSearchHit.Snippet(firstFragment.take(400)),
                )
            }
        } catch (e: WebClientResponseException) {
            log.warn(
                "GitHub search/code failed (status={}); returning empty for query='{}' repo={}",
                e.statusCode,
                q,
                repoId.value,
            )
            emptyList()
        }
    }

    /**
     * Fetches every blob path of the repo tree at [ref]. Shared by [resolveFilePaths]
     * and [commitFiles] so both apply the same path-resolution rule.
     */
    private suspend fun fetchBlobPaths(
        repoId: RepoId,
        ref: String,
    ): List<String> {
        try {
            val tree =
                githubWebClient
                    .get()
                    .uri("/repos/${repoId.value}/git/trees/$ref?recursive=1")
                    .retrieve()
                    .bodyToMono(GitHubTreeResponse::class.java)
                    .awaitSingle()

            // GitHub truncates the recursive tree response at ~100k entries / 7MB.
            // When that happens we proceed with the partial result rather than failing,
            // but emit a WARN so operators see when files may be missed by the
            // path-resolution guard. TODO: fall back to contents API or paged tree.
            if (tree.truncated) {
                log.warn(
                    "GitHub tree truncated for repo={} ref={} entries={}; some files may be missed by path resolution",
                    repoId.value,
                    ref,
                    tree.tree.size,
                )
            }

            return tree.tree
                .asSequence()
                .filter { it.type == "blob" }
                .map { it.path }
                .toList()
        } catch (e: WebClientResponseException) {
            throw GitHubApiException(
                message = "Failed to resolve file paths - ${e.statusCode}",
                cause = e,
            )
        }
    }

    /**
     * Counts non-overlapping occurrences of [needle] in [haystack].
     * Used to detect whether the LLM-supplied original snippet is unique
     * within the existing file content before performing find-and-replace.
     */
    private fun countOccurrences(
        haystack: String,
        needle: String,
    ): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = haystack.indexOf(needle, idx)
            if (found < 0) break
            count++
            idx = found + needle.length
        }
        return count
    }

    /**
     * Truncates a snippet for log output so a multi-line LLM response does not
     * dominate logs. Newlines are escaped for readability on a single line.
     */
    private fun snippetPreview(
        snippet: String,
        max: Int = 80,
    ): String {
        val flat = snippet.replace("\n", "\\n").replace("\r", "\\r")
        return if (flat.length <= max) flat else flat.substring(0, max) + "..."
    }

    /**
     * Returns blob paths whose tail equals [candidate]. The candidate is normalized
     * by stripping a leading slash so that `/foo/Bar.kt` and `foo/Bar.kt` behave the same.
     * If the candidate is itself an exact full path, returns just that single match.
     */
    private fun matchByEndsWith(
        blobPaths: List<String>,
        candidate: String,
    ): List<String> {
        val normalized = candidate.trimStart('/')
        if (normalized.isBlank()) return emptyList()

        // Exact match short-circuit (cheap, also resolves ambiguity when LLM already gave a full path).
        val exact = blobPaths.firstOrNull { it == normalized }
        if (exact != null) return listOf(exact)

        // Boundary-aware suffix match: only accept matches at a path-segment boundary
        // so 'Foo.kt' does not match 'NotFoo.kt'.
        return blobPaths.filter { it.endsWith("/$normalized") }
    }

    override suspend fun hasOpenPR(
        repoId: RepoId,
        branchName: PrParams.HeadBranch,
    ): Boolean =
        try {
            val response =
                githubWebClient
                    .get()
                    .uri("/repos/${repoId.value}/pulls?head=${repoId.owner}:${branchName.value}&state=open")
                    .retrieve()
                    .bodyToMono(Array<GitHubPrResponse>::class.java)
                    .awaitSingle()

            response.isNotEmpty()
        } catch (e: Exception) {
            false
        }

    override suspend fun healthCheck(): Boolean =
        try {
            githubWebClient
                .get()
                .uri("/")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            true
        } catch (e: Exception) {
            false
        }
}

// GitHub API Response DTOs
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val content: String,
    val encoding: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRefResponse(
    val ref: String,
    val `object`: GitHubRefObject,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRefObject(
    val sha: String,
    val type: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubTreeResponse(
    val sha: String,
    val tree: List<GitHubTreeEntry>,
    @JsonProperty("truncated")
    val truncated: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubTreeEntry(
    val path: String,
    val type: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPrResponse(
    val number: Int,
    val url: String,
    @JsonProperty("html_url")
    val htmlUrl: String,
    val state: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubSearchCodeResponse(
    @JsonProperty("total_count")
    val totalCount: Int = 0,
    val items: List<GitHubSearchCodeItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubSearchCodeItem(
    val path: String,
    @JsonProperty("text_matches")
    val textMatches: List<GitHubSearchTextMatch>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubSearchTextMatch(
    val fragment: String? = null,
)

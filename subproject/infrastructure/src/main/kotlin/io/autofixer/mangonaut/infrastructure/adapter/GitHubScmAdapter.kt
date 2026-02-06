package io.autofixer.mangonaut.infrastructure.adapter

import io.autofixer.mangonaut.domain.exception.GitHubApiException
import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.PrParams
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Base64

/**
 * GitHub API를 사용한 ScmProviderPort 구현체
 */
@Component
class GitHubScmAdapter(
    private val githubWebClient: WebClient,
) : ScmProviderPort {

    override val name: String = "github"

    override suspend fun getFileContent(repoId: RepoId, path: FileChange.FilePath, ref: String): String {
        try {
            val response = githubWebClient
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

    override suspend fun createBranch(repoId: RepoId, baseBranch: PrParams.BaseBranch, newBranch: PrParams.HeadBranch) {
        try {
            // base 브랜치의 최신 커밋 SHA 조회
            val refResponse = githubWebClient
                .get()
                .uri("/repos/${repoId.value}/git/refs/heads/${baseBranch.value}")
                .retrieve()
                .bodyToMono(GitHubRefResponse::class.java)
                .awaitSingle()

            // 새 브랜치 생성
            githubWebClient
                .post()
                .uri("/repos/${repoId.value}/git/refs")
                .bodyValue(
                    mapOf(
                        "ref" to "refs/heads/${newBranch.value}",
                        "sha" to refResponse.`object`.sha,
                    )
                )
                .retrieve()
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
        try {
            for (change in changes) {
                // 기존 파일의 SHA 조회 (업데이트를 위해 필요)
                val existingFile = try {
                    githubWebClient
                        .get()
                        .uri("/repos/${repoId.value}/contents/${change.file.value}?ref=${branch.value}")
                        .retrieve()
                        .bodyToMono(GitHubContentResponse::class.java)
                        .awaitSingleOrNull()
                } catch (e: WebClientResponseException.NotFound) {
                    null
                }

                // 파일 업데이트/생성
                val requestBody = mutableMapOf(
                    "message" to message,
                    "content" to Base64.getEncoder().encodeToString(change.modified.value.toByteArray()),
                    "branch" to branch.value,
                )
                existingFile?.sha?.let { requestBody["sha"] = it }

                githubWebClient
                    .put()
                    .uri("/repos/${repoId.value}/contents/${change.file.value}")
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .awaitSingle()
            }
        } catch (e: WebClientResponseException) {
            throw GitHubApiException(
                message = "Failed to commit files - ${e.statusCode}",
                cause = e,
            )
        }
    }

    override suspend fun createPullRequest(repoId: RepoId, params: PrParams): PrResult {
        try {
            val response = githubWebClient
                .post()
                .uri("/repos/${repoId.value}/pulls")
                .bodyValue(
                    mapOf(
                        "title" to params.title.value,
                        "body" to params.body.value,
                        "head" to params.headBranch.value,
                        "base" to params.baseBranch.value,
                    )
                )
                .retrieve()
                .bodyToMono(GitHubPrResponse::class.java)
                .awaitSingle()

            // 라벨 추가
            if (params.labels.isNotEmpty()) {
                githubWebClient
                    .post()
                    .uri("/repos/${repoId.value}/issues/${response.number}/labels")
                    .bodyValue(
                        mapOf("labels" to params.labels.map { it.value })
                    )
                    .retrieve()
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

    override suspend fun hasOpenPR(repoId: RepoId, branchName: PrParams.HeadBranch): Boolean {
        return try {
            val response = githubWebClient
                .get()
                .uri("/repos/${repoId.value}/pulls?head=${repoId.owner}:${branchName.value}&state=open")
                .retrieve()
                .bodyToMono(Array<GitHubPrResponse>::class.java)
                .awaitSingle()

            response.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
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
}

// GitHub API Response DTOs
data class GitHubContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val content: String,
    val encoding: String,
)

data class GitHubRefResponse(
    val ref: String,
    val `object`: GitHubRefObject,
)

data class GitHubRefObject(
    val sha: String,
    val type: String,
)

data class GitHubPrResponse(
    val number: Int,
    val url: String,
    val htmlUrl: String,
    val state: String,
)

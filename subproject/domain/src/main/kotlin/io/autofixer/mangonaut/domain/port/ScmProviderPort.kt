package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.PrParams
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId

/**
 * Port for communicating with SCM (Source Control Management) providers.
 *
 * Implementations for GitHub, GitLab, Bitbucket, etc. are provided by the Infrastructure layer.
 */
interface ScmProviderPort {
    /**
     * Identifier for the SCM provider.
     * e.g., "github", "gitlab", "bitbucket"
     */
    val name: String

    /**
     * Retrieves file content.
     *
     * @param repoId repository identifier (e.g., "owner/repo")
     * @param path file path
     * @param ref branch or commit SHA
     * @return file content
     */
    suspend fun getFileContent(repoId: RepoId, path: FileChange.FilePath, ref: String): String

    /**
     * Creates a new branch.
     *
     * @param repoId repository identifier
     * @param baseBranch base branch
     * @param newBranch new branch name
     */
    suspend fun createBranch(repoId: RepoId, baseBranch: PrParams.BaseBranch, newBranch: PrParams.HeadBranch)

    /**
     * Commits file changes.
     *
     * @param repoId repository identifier
     * @param branch target branch
     * @param changes list of files to change
     * @param message commit message
     */
    suspend fun commitFiles(repoId: RepoId, branch: PrParams.HeadBranch, changes: List<FileChange>, message: String)

    /**
     * Creates a Pull Request.
     *
     * @param repoId repository identifier
     * @param params PR creation parameters
     * @return PR creation result
     */
    suspend fun createPullRequest(repoId: RepoId, params: PrParams): PrResult

    /**
     * Checks if there is an open PR on a specific branch.
     * Used to prevent duplicate PR creation.
     *
     * @param repoId repository identifier
     * @param branchName branch name to check
     * @return whether an open PR exists
     */
    suspend fun hasOpenPR(repoId: RepoId, branchName: PrParams.HeadBranch): Boolean

    /**
     * Checks the connectivity status of the SCM provider.
     *
     * @return true if connection is successful
     */
    suspend fun healthCheck(): Boolean
}

package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.FileChange
import io.autofixer.mangonaut.domain.model.PrParams
import io.autofixer.mangonaut.domain.model.PrResult
import io.autofixer.mangonaut.domain.model.RepoId

/**
 * SCM(Source Control Management) 프로바이더와의 통신을 위한 포트
 *
 * GitHub, GitLab, Bitbucket 등의 구현체를 Infrastructure 레이어에서 제공합니다.
 */
interface ScmProviderPort {
    /**
     * SCM 프로바이더의 식별자
     * 예: "github", "gitlab", "bitbucket"
     */
    val name: String

    /**
     * 파일 내용을 조회합니다.
     *
     * @param repoId 저장소 식별자 (예: "owner/repo")
     * @param path 파일 경로
     * @param ref 브랜치 또는 커밋 SHA
     * @return 파일 내용
     */
    suspend fun getFileContent(repoId: RepoId, path: FileChange.FilePath, ref: String): String

    /**
     * 새 브랜치를 생성합니다.
     *
     * @param repoId 저장소 식별자
     * @param baseBranch 기준 브랜치
     * @param newBranch 새 브랜치 이름
     */
    suspend fun createBranch(repoId: RepoId, baseBranch: PrParams.BaseBranch, newBranch: PrParams.HeadBranch)

    /**
     * 파일 변경사항을 커밋합니다.
     *
     * @param repoId 저장소 식별자
     * @param branch 대상 브랜치
     * @param changes 변경할 파일 목록
     * @param message 커밋 메시지
     */
    suspend fun commitFiles(repoId: RepoId, branch: PrParams.HeadBranch, changes: List<FileChange>, message: String)

    /**
     * Pull Request를 생성합니다.
     *
     * @param repoId 저장소 식별자
     * @param params PR 생성 파라미터
     * @return PR 생성 결과
     */
    suspend fun createPullRequest(repoId: RepoId, params: PrParams): PrResult

    /**
     * 특정 브랜치에 열린 PR이 있는지 확인합니다.
     * 중복 PR 생성 방지에 사용됩니다.
     *
     * @param repoId 저장소 식별자
     * @param branchName 확인할 브랜치 이름
     * @return 열린 PR 존재 여부
     */
    suspend fun hasOpenPR(repoId: RepoId, branchName: PrParams.HeadBranch): Boolean

    /**
     * SCM 프로바이더의 연결 상태를 확인합니다.
     *
     * @return 연결 성공 여부
     */
    suspend fun healthCheck(): Boolean
}

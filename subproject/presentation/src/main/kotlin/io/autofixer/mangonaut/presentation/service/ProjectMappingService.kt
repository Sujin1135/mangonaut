package io.autofixer.mangonaut.presentation.service

/**
 * 프로젝트 매핑 서비스 인터페이스
 *
 * Sentry 프로젝트와 SCM 저장소 간의 매핑을 관리합니다.
 * Infrastructure 레이어에서 구현 제공
 */
interface ProjectMappingService {
    /**
     * 소스 프로젝트에 해당하는 매핑 정보를 조회합니다.
     *
     * @param sourceProject Sentry 프로젝트 slug
     * @return 매핑 정보, 없으면 null
     */
    fun findMapping(sourceProject: String): ProjectMapping?
}

/**
 * 프로젝트 매핑 정보
 */
data class ProjectMapping(
    val sourceProject: String,
    val scmRepo: String,
    val sourceRoot: String,
    val defaultBranch: String,
    val branchPrefix: String,
    val labels: List<String>,
    val minConfidence: String,
    val autoPr: Boolean,
)

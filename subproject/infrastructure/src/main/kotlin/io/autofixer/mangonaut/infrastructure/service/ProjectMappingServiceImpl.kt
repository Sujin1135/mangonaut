package io.autofixer.mangonaut.infrastructure.service

import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import io.autofixer.mangonaut.presentation.service.ProjectMapping
import io.autofixer.mangonaut.presentation.service.ProjectMappingService
import org.springframework.stereotype.Service

/**
 * 프로젝트 매핑 서비스 구현체
 *
 * application.yml의 설정에서 매핑 정보를 조회합니다.
 */
@Service
class ProjectMappingServiceImpl(
    private val properties: MangonautProperties,
) : ProjectMappingService {

    override fun findMapping(sourceProject: String): ProjectMapping? {
        val projectConfig = properties.projects.find { it.sourceProject == sourceProject }
            ?: return null

        return ProjectMapping(
            sourceProject = projectConfig.sourceProject,
            scmRepo = projectConfig.scmRepo,
            sourceRoot = projectConfig.sourceRoot,
            defaultBranch = projectConfig.defaultBranch,
            branchPrefix = properties.behavior.branchPrefix,
            labels = properties.behavior.labels,
            minConfidence = properties.behavior.minConfidence.name,
            autoPr = properties.behavior.autoPr,
        )
    }
}

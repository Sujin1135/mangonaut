package io.autofixer.mangonaut.infrastructure.service

import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import io.autofixer.mangonaut.presentation.service.ProjectMapping
import io.autofixer.mangonaut.presentation.service.ProjectMappingService
import org.springframework.stereotype.Service

/**
 * Project mapping service implementation.
 *
 * Retrieves mapping information from application.yml configuration.
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
            sourceRoots = projectConfig.sourceRoots,
            defaultBranch = projectConfig.defaultBranch,
            branchPrefix = properties.behavior.branchPrefix,
            labels = properties.behavior.labels,
            minConfidence = properties.behavior.minConfidence.name,
            autoPr = properties.behavior.autoPr,
        )
    }
}

package io.autofixer.mangonaut.infrastructure.service

import io.autofixer.mangonaut.infrastructure.config.GitHubInstallationRepositoryClient
import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import io.autofixer.mangonaut.presentation.service.ProjectMapping
import io.autofixer.mangonaut.presentation.service.ProjectMappingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProjectMappingServiceImpl(
    private val properties: MangonautProperties,
    private val repositoryClient: GitHubInstallationRepositoryClient,
) : ProjectMappingService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findMapping(sourceProject: String): ProjectMapping? {
        // 1. Static config lookup
        val projectConfig = properties.projects.find { it.sourceProject == sourceProject }
        if (projectConfig != null) {
            log.debug("Static mapping found for '{}'", sourceProject)
            return ProjectMapping(
                sourceProject = projectConfig.sourceProject,
                scmRepo = projectConfig.scmRepo,
                defaultBranch = projectConfig.defaultBranch,
                branchPrefix = properties.behavior.branchPrefix,
                labels = properties.behavior.labels,
                minConfidence = properties.behavior.minConfidence.name,
                autoPr = properties.behavior.autoPr,
            )
        }

        // 2. Dynamic lookup from GitHub installed repositories
        val repos = repositoryClient.getRepositories()
        val repo = repos.find { it.name == sourceProject }
        if (repo == null) {
            log.warn(
                "No mapping found for '{}'. Available repos: {}",
                sourceProject,
                repos.map { it.name },
            )
            return null
        }

        log.debug("Dynamic mapping found for '{}' → {}", sourceProject, repo.fullName)
        return ProjectMapping(
            sourceProject = sourceProject,
            scmRepo = repo.fullName,
            defaultBranch = repo.defaultBranch,
            branchPrefix = properties.behavior.branchPrefix,
            labels = properties.behavior.labels,
            minConfidence = properties.behavior.minConfidence.name,
            autoPr = properties.behavior.autoPr,
        )
    }
}

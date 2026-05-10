package io.autofixer.mangonaut.infrastructure.adapter

import io.autofixer.mangonaut.domain.port.ProjectMappingPort
import io.autofixer.mangonaut.infrastructure.config.GitHubInstallationRepositoryClient
import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProjectMappingAdapter(
    private val properties: MangonautProperties,
    private val repositoryClient: GitHubInstallationRepositoryClient,
) : ProjectMappingPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun invoke(params: ProjectMappingPort.Params): ProjectMappingPort.Result? {
        val sourceProject = params.sourceProject.value
        val behavior = properties.behavior

        properties.projects.find { it.sourceProject == sourceProject }?.let { staticConfig ->
            log.debug("Static mapping found for '{}'", sourceProject)
            return ProjectMappingPort.Result(
                sourceProject = staticConfig.sourceProject,
                scmRepo = staticConfig.scmRepo,
                defaultBranch = staticConfig.defaultBranch,
                branchPrefix = behavior.branchPrefix,
                labels = behavior.labels,
                minConfidence = behavior.minConfidence,
                autoPr = behavior.autoPr,
            )
        }

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
        return ProjectMappingPort.Result(
            sourceProject = sourceProject,
            scmRepo = repo.fullName,
            defaultBranch = repo.defaultBranch,
            branchPrefix = behavior.branchPrefix,
            labels = behavior.labels,
            minConfidence = behavior.minConfidence,
            autoPr = behavior.autoPr,
        )
    }
}

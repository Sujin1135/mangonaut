package io.autofixer.mangonaut.presentation.service

/**
 * Project mapping service interface.
 *
 * Manages mappings between Sentry projects and SCM repositories.
 * Implementation provided by the Infrastructure layer.
 */
interface ProjectMappingService {
    /**
     * Looks up mapping information for the given source project.
     *
     * @param sourceProject Sentry project slug
     * @return mapping information, or null if not found
     */
    fun findMapping(sourceProject: String): ProjectMapping?
}

/**
 * Project mapping information.
 */
data class ProjectMapping(
    val sourceProject: String,
    val scmRepo: String,
    val defaultBranch: String,
    val branchPrefix: String,
    val labels: List<String>,
    val minConfidence: String,
    val autoPr: Boolean,
)

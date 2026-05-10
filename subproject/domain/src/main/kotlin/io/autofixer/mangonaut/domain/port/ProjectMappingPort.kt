package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.Confidence
import io.autofixer.mangonaut.domain.model.ErrorEvent

/**
 * Looks up SCM repository mapping information for a given source project (e.g. Sentry slug).
 *
 * Returns `null` when no mapping is configured or the source project is unknown.
 */
interface ProjectMappingPort {

    suspend operator fun invoke(params: Params): Result?

    data class Params(
        val sourceProject: ErrorEvent.SourceProject,
    )

    data class Result(
        val sourceProject: String,
        val scmRepo: String,
        val defaultBranch: String,
        val branchPrefix: String,
        val labels: List<String>,
        val minConfidence: Confidence,
        val autoPr: Boolean,
    )
}

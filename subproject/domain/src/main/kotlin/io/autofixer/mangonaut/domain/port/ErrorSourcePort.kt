package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.ErrorEvent

/**
 * Port for communicating with error sources (Sentry, Datadog, etc.).
 *
 * Concrete implementations are provided by the Infrastructure layer.
 */
interface ErrorSourcePort {
    /**
     * Identifier for the error source.
     * e.g., "sentry", "datadog", "rollbar"
     */
    val name: String

    /**
     * Fetches detailed error event by issue ID.
     *
     * @param issueId issue identifier from the error source
     * @return standardized error event
     * @throws ErrorSourceException on fetch failure
     */
    suspend fun fetchEvent(issueId: ErrorEvent.Id): ErrorEvent

    /**
     * Checks the connectivity status of the error source.
     *
     * @return true if connection is successful
     */
    suspend fun healthCheck(): Boolean
}

package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FixResult
import io.autofixer.mangonaut.domain.model.RepoContext

/**
 * Port for communicating with LLM (Large Language Model) providers.
 *
 * Implementations for Claude, OpenAI, Ollama, etc. are provided by the Infrastructure layer.
 *
 * The implementation drives an agentic loop: it receives only the [errorEvent]
 * and a [repoContext], and is expected to actively explore the codebase
 * through tool calls (read_file / list_directory / search_code) to identify
 * the bug location, then propose minimal fixes via propose_fix and finish.
 */
interface LlmProviderPort {
    /**
     * Identifier for the LLM provider.
     * e.g., "claude", "openai", "ollama"
     */
    val name: String

    /**
     * Analyzes the error event by autonomously exploring the codebase via tool calls.
     *
     * @param errorEvent error event to analyze
     * @param repoContext repository identifier + ref the agent may explore
     * @return analysis result and fix suggestions (always full-repo-path file references)
     */
    suspend fun analyzeError(
        errorEvent: ErrorEvent,
        repoContext: RepoContext,
    ): FixResult

    /**
     * Checks the connectivity status of the LLM provider.
     *
     * @return true if connection is successful
     */
    suspend fun healthCheck(): Boolean
}

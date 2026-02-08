package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.ErrorEvent
import io.autofixer.mangonaut.domain.model.FixResult

/**
 * Port for communicating with LLM (Large Language Model) providers.
 *
 * Implementations for Claude, OpenAI, Ollama, etc. are provided by the Infrastructure layer.
 */
interface LlmProviderPort {
    /**
     * Identifier for the LLM provider.
     * e.g., "claude", "openai", "ollama"
     */
    val name: String

    /**
     * Analyzes the error event and related source code to generate fix suggestions.
     *
     * @param errorEvent error event to analyze
     * @param sourceFiles related source files (file path -> content)
     * @return analysis result and fix suggestions
     */
    suspend fun analyzeError(
        errorEvent: ErrorEvent,
        sourceFiles: Map<String, String>,
    ): FixResult

    /**
     * Checks the connectivity status of the LLM provider.
     *
     * @return true if connection is successful
     */
    suspend fun healthCheck(): Boolean
}

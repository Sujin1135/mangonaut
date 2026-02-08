package io.autofixer.mangonaut.presentation.controller

import io.autofixer.mangonaut.domain.port.ErrorSourcePort
import io.autofixer.mangonaut.domain.port.LlmProviderPort
import io.autofixer.mangonaut.domain.port.ScmProviderPort
import io.autofixer.mangonaut.presentation.dto.ComponentHealth
import io.autofixer.mangonaut.presentation.dto.HealthResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Health Check controller.
 */
@RestController
@RequestMapping("/health")
class HealthController(
    private val errorSourcePort: ErrorSourcePort,
    private val scmProviderPort: ScmProviderPort,
    private val llmProviderPort: LlmProviderPort,
) {
    @GetMapping
    suspend fun health(): ResponseEntity<HealthResponse> = coroutineScope {
        val sentryHealth = async { checkComponent("sentry") { errorSourcePort.healthCheck() } }
        val githubHealth = async { checkComponent("github") { scmProviderPort.healthCheck() } }
        val llmHealth = async { checkComponent("llm") { llmProviderPort.healthCheck() } }

        val components = mapOf(
            errorSourcePort.name to sentryHealth.await(),
            scmProviderPort.name to githubHealth.await(),
            llmProviderPort.name to llmHealth.await(),
        )

        val overallStatus = if (components.values.all { it.status == "UP" }) "UP" else "DEGRADED"

        ResponseEntity.ok(
            HealthResponse(
                status = overallStatus,
                components = components,
            )
        )
    }

    private suspend fun checkComponent(name: String, check: suspend () -> Boolean): ComponentHealth {
        return try {
            val isHealthy = check()
            ComponentHealth(
                status = if (isHealthy) "UP" else "DOWN",
            )
        } catch (e: Exception) {
            ComponentHealth(
                status = "DOWN",
                details = mapOf("error" to (e.message ?: "Unknown error")),
            )
        }
    }
}

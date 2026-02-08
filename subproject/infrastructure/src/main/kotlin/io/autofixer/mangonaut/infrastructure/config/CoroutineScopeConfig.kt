package io.autofixer.mangonaut.infrastructure.config

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineScopeConfig {

    private val job = SupervisorJob()

    @Bean
    fun webhookProcessingScope(): CoroutineScope {
        return CoroutineScope(job + Dispatchers.Default + CoroutineName("webhook-processing"))
    }

    @PreDestroy
    fun destroy() {
        job.cancel()
    }
}

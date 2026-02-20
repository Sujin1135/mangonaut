package io.autofixer.mangonaut.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

/**
 * WebClient configuration.
 *
 * Provides WebClient beans for each external service.
 */
@Configuration
class WebClientConfig(
    private val properties: MangonautProperties,
    private val gitHubAppTokenProvider: GitHubAppTokenProvider,
) {
    @Bean
    fun sentryWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(properties.sentry.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.sentry.token}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    fun githubWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(properties.github.baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .filter(githubAuthFilter())
            .build()
    }

    private fun githubAuthFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { request ->
            gitHubAppTokenProvider.getToken().map { token ->
                ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .build()
            }
        }
    }

    @Bean
    fun claudeWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(properties.llm.baseUrl)
            .defaultHeader("x-api-key", properties.llm.apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}

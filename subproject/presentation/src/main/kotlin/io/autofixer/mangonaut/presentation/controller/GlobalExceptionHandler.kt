package io.autofixer.mangonaut.presentation.controller

import io.autofixer.mangonaut.domain.exception.ConfigurationException
import io.autofixer.mangonaut.domain.exception.DuplicateProcessingException
import io.autofixer.mangonaut.domain.exception.GitHubApiException
import io.autofixer.mangonaut.domain.exception.LlmApiException
import io.autofixer.mangonaut.domain.exception.MangonautException
import io.autofixer.mangonaut.domain.exception.ResourceNotFoundException
import io.autofixer.mangonaut.domain.exception.SentryApiException
import io.autofixer.mangonaut.domain.exception.WebhookValidationException
import io.autofixer.mangonaut.presentation.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MangonautException::class)
    fun handleMangonautException(ex: MangonautException): ResponseEntity<ErrorResponse> {
        val status = when (ex) {
            is WebhookValidationException -> HttpStatus.UNAUTHORIZED
            is ResourceNotFoundException -> HttpStatus.NOT_FOUND
            is DuplicateProcessingException -> HttpStatus.CONFLICT
            is ConfigurationException -> HttpStatus.INTERNAL_SERVER_ERROR
            is SentryApiException -> HttpStatus.BAD_GATEWAY
            is GitHubApiException -> HttpStatus.BAD_GATEWAY
            is LlmApiException -> HttpStatus.BAD_GATEWAY
        }

        if (status.is5xxServerError) {
            logger.error("[{}] {}", ex.errorCode.code, ex.message, ex)
        } else {
            logger.warn("[{}] {}", ex.errorCode.code, ex.message)
        }

        return ResponseEntity.status(status).body(
            ErrorResponse(
                errorCode = ex.errorCode.code,
                message = ex.message,
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                errorCode = "INTERNAL_001",
                message = "Internal server error",
            )
        )
    }
}

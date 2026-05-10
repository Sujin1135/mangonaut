package io.autofixer.mangonaut.presentation.dto

import io.autofixer.mangonaut.domain.exception.WebhookValidationException
import io.autofixer.mangonaut.domain.model.SentryWebhookRequest
import tools.jackson.databind.ObjectMapper
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Factory for [SentryWebhookRequest] that verifies the Sentry-Hook-Signature
 * before deserializing the raw payload.
 */
object SentryWebhookFactory {

    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Verifies the signature, then deserializes [rawBody] into a [SentryWebhookRequest].
     *
     * @throws WebhookValidationException when the signature does not match
     */
    fun from(
        rawBody: String,
        signature: String,
        secret: String,
        objectMapper: ObjectMapper,
    ): SentryWebhookRequest {
        verifySignature(rawBody, signature, secret)
        return objectMapper.readValue(rawBody, SentryWebhookRequest::class.java)
    }

    private fun verifySignature(payload: String, signature: String, secret: String) {
        if (secret.isBlank()) return

        val expected = computeHmacSha256(payload, secret)
        if (signature != expected) {
            throw WebhookValidationException("Invalid webhook signature")
        }
    }

    private fun computeHmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM))
        }
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

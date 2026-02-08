package io.autofixer.mangonaut.infrastructure.service

import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import io.autofixer.mangonaut.presentation.service.WebhookVerificationService
import org.springframework.stereotype.Service
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook signature verification service implementation.
 */
@Service
class WebhookVerificationServiceImpl(
    private val properties: MangonautProperties,
) : WebhookVerificationService {

    override fun verifySentrySignature(payload: String, signature: String): Boolean {
        if (properties.sentry.webhookSecret.isBlank()) {
            // Skip verification when secret is not configured (development environment)
            return true
        }

        val expectedSignature = computeHmacSha256(payload, properties.sentry.webhookSecret)
        return signature == expectedSignature
    }

    private fun computeHmacSha256(data: String, secret: String): String {
        val algorithm = "HmacSHA256"
        val secretKey = SecretKeySpec(secret.toByteArray(), algorithm)
        val mac = Mac.getInstance(algorithm)
        mac.init(secretKey)
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

package io.autofixer.mangonaut.presentation.service

/**
 * Webhook signature verification service interface.
 *
 * Implementation provided by the Infrastructure layer.
 */
interface WebhookVerificationService {
    /**
     * Verifies the Sentry Webhook signature.
     *
     * @param payload original request body
     * @param signature Sentry-Hook-Signature header value
     * @return whether verification succeeded
     */
    fun verifySentrySignature(payload: String, signature: String): Boolean
}

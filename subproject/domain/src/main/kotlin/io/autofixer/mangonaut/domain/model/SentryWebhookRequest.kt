package io.autofixer.mangonaut.domain.model

import io.autofixer.mangonaut.domain.exception.WebhookValidationException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Sentry Webhook payload as a domain model.
 *
 * `action` is `null` when Sentry sends a value not present in [SentryAction]
 * (configured via the JsonMapper unknown-enum handler).
 */
data class SentryWebhookRequest(
    val action: SentryAction?,
    val data: SentryIssueData,
    val installation: SentryInstallation?,
) {
    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"

        /**
         * Verifies the Sentry-Hook-Signature against [rawBody], then deserializes
         * via the supplied [deserialize] function. Keeping the JSON library out of
         * the domain layer is the reason [deserialize] is injected.
         *
         * @throws WebhookValidationException when the signature does not match
         */
        fun from(
            rawBody: String,
            signature: String,
            secret: String,
            deserialize: (String) -> SentryWebhookRequest,
        ): SentryWebhookRequest {
            verifySignature(rawBody, signature, secret)
            return deserialize(rawBody)
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
}

data class SentryIssueData(
    val issue: SentryIssue,
)

data class SentryIssue(
    val id: String,
    val title: String,
    val project: SentryProject,
    val shortId: String,
    val level: String?,
    val status: String?,
)

data class SentryProject(
    val id: String,
    val name: String,
    val slug: String,
)

data class SentryInstallation(
    val uuid: String,
)

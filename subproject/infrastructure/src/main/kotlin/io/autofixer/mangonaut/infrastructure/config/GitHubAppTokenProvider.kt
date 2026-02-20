package io.autofixer.mangonaut.infrastructure.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

@Component
class GitHubAppTokenProvider(
    private val properties: MangonautProperties,
) {
    private val bareWebClient: WebClient = WebClient.builder()
        .baseUrl(properties.github.baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build()

    @Volatile
    private var cachedToken: CachedToken? = null

    fun getToken(): Mono<String> {
        val cached = cachedToken
        if (cached != null && !cached.isExpiringSoon()) {
            return Mono.just(cached.token)
        }
        return fetchInstallationAccessToken()
    }

    private fun fetchInstallationAccessToken(): Mono<String> {
        val jwt = generateJwt()
        return bareWebClient
            .post()
            .uri("/app/installations/${properties.github.installationId}/access_tokens")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
            .retrieve()
            .bodyToMono(InstallationTokenResponse::class.java)
            .map { response ->
                cachedToken = CachedToken(
                    token = response.token,
                    expiresAt = response.expiresAt,
                )
                response.token
            }
    }

    internal fun generateJwt(): String {
        val now = Instant.now()
        val privateKey = parsePrivateKey(properties.github.privateKey)
        return JWT.create()
            .withIssuer(properties.github.appId)
            .withIssuedAt(Date.from(now.minusSeconds(60)))
            .withExpiresAt(Date.from(now.plusSeconds(600)))
            .sign(Algorithm.RSA256(null, privateKey))
    }

    internal data class CachedToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        fun isExpiringSoon(): Boolean = Instant.now().isAfter(expiresAt.minusSeconds(300))
    }

    companion object {
        fun parsePrivateKey(base64Pem: String): RSAPrivateKey {
            val normalized = base64Pem.replace('-', '+').replace('_', '/')
            val pem = String(Base64.getDecoder().decode(normalized))
            val isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")
            val keyContent = pem
                .replace(Regex("-----[A-Z ]+-----"), "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.getDecoder().decode(keyContent)

            val keySpec = if (isPkcs1) {
                // PKCS#1 → PKCS#8 conversion via RSA OID wrapping
                val pkcs8Bytes = wrapPkcs1InPkcs8(keyBytes)
                PKCS8EncodedKeySpec(pkcs8Bytes)
            } else {
                PKCS8EncodedKeySpec(keyBytes)
            }
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
        }

        private fun wrapPkcs1InPkcs8(pkcs1Bytes: ByteArray): ByteArray {
            // RSA OID: 1.2.840.113549.1.1.1
            val rsaOid = byteArrayOf(
                0x30, 0x0d,
                0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00,
            )
            val octetString = derEncode(0x04, pkcs1Bytes)
            val versionBytes = byteArrayOf(0x02, 0x01, 0x00) // INTEGER 0
            val sequenceContent = versionBytes + rsaOid + octetString
            return derEncode(0x30, sequenceContent)
        }

        private fun derEncode(tag: Int, content: ByteArray): ByteArray {
            val length = content.size
            val lengthBytes = when {
                length < 0x80 -> byteArrayOf(length.toByte())
                length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
                length < 0x10000 -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
                else -> byteArrayOf(
                    0x83.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte()
                )
            }
            return byteArrayOf(tag.toByte()) + lengthBytes + content
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstallationTokenResponse(
    val token: String,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)

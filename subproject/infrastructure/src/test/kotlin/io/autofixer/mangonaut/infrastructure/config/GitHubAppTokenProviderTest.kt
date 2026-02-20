package io.autofixer.mangonaut.infrastructure.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64

class GitHubAppTokenProviderTest : BehaviorSpec({

    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val privateKey = keyPair.private as RSAPrivateKey
    val publicKey = keyPair.public as RSAPublicKey

    fun encodePemToBase64(privateKey: RSAPrivateKey): String {
        val pem = buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            Base64.getEncoder().encodeToString(privateKey.encoded)
                .chunked(64)
                .forEach { appendLine(it) }
            appendLine("-----END PRIVATE KEY-----")
        }
        return Base64.getEncoder().encodeToString(pem.toByteArray())
    }

    val base64Pem = encodePemToBase64(privateKey)

    fun createProperties(
        appId: String = "12345",
        installationId: String = "67890",
        privateKey: String = base64Pem,
    ): MangonautProperties {
        return MangonautProperties(
            github = GitHubProperties(
                appId = appId,
                installationId = installationId,
                privateKey = privateKey,
            ),
        )
    }

    context("JWT generation") {
        given("valid GitHub App credentials") {
            val provider = GitHubAppTokenProvider(createProperties())

            `when`("generating a JWT") {
                val jwt = provider.generateJwt()

                then("should produce a valid 3-part JWT string") {
                    jwt.split(".") shouldHaveSize 3
                }

                then("should have the correct issuer claim") {
                    val decoded = JWT.require(Algorithm.RSA256(publicKey, null))
                        .withIssuer("12345")
                        .build()
                        .verify(jwt)

                    decoded.issuer shouldBe "12345"
                }
            }
        }
    }

    context("PEM key parsing") {
        given("a Base64-encoded PKCS#8 PEM key") {
            `when`("parsing the private key") {
                val parsed = GitHubAppTokenProvider.parsePrivateKey(base64Pem)

                then("should return an RSAPrivateKey") {
                    parsed.algorithm shouldBe "RSA"
                    parsed.format shouldBe "PKCS#8"
                }

                then("should match the original key") {
                    parsed.encoded shouldBe privateKey.encoded
                }
            }
        }
    }

    context("Token caching") {
        given("a cached token that has not expired") {
            val cachedToken = GitHubAppTokenProvider.CachedToken(
                token = "ghs_cached_token",
                expiresAt = Instant.now().plusSeconds(3600),
            )

            then("isExpiringSoon should return false") {
                cachedToken.isExpiringSoon() shouldBe false
            }
        }

        given("a cached token expiring within 5 minutes") {
            val cachedToken = GitHubAppTokenProvider.CachedToken(
                token = "ghs_expiring_token",
                expiresAt = Instant.now().plusSeconds(200),
            )

            then("isExpiringSoon should return true") {
                cachedToken.isExpiringSoon() shouldBe true
            }
        }

        given("an already-expired token") {
            val cachedToken = GitHubAppTokenProvider.CachedToken(
                token = "ghs_expired_token",
                expiresAt = Instant.now().minusSeconds(60),
            )

            then("isExpiringSoon should return true") {
                cachedToken.isExpiringSoon() shouldBe true
            }
        }
    }
})

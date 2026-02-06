package io.autofixer.mangonaut.presentation.service

/**
 * Webhook 서명 검증 서비스 인터페이스
 *
 * Infrastructure 레이어에서 구현 제공
 */
interface WebhookVerificationService {
    /**
     * Sentry Webhook 서명을 검증합니다.
     *
     * @param payload 원본 요청 본문
     * @param signature Sentry-Hook-Signature 헤더 값
     * @return 검증 성공 여부
     */
    fun verifySentrySignature(payload: String, signature: String): Boolean
}

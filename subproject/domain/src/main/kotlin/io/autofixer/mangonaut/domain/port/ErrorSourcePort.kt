package io.autofixer.mangonaut.domain.port

import io.autofixer.mangonaut.domain.model.ErrorEvent

/**
 * 에러 소스(Sentry, Datadog 등)와의 통신을 위한 포트
 *
 * Infrastructure 레이어에서 구체적인 구현체를 제공합니다.
 */
interface ErrorSourcePort {
    /**
     * 에러 소스의 식별자
     * 예: "sentry", "datadog", "rollbar"
     */
    val name: String

    /**
     * 이슈 ID로 상세 에러 이벤트를 조회합니다.
     *
     * @param issueId 에러 소스에서의 이슈 식별자
     * @return 표준화된 에러 이벤트
     * @throws ErrorSourceException 조회 실패 시
     */
    suspend fun fetchEvent(issueId: ErrorEvent.Id): ErrorEvent

    /**
     * 에러 소스의 연결 상태를 확인합니다.
     *
     * @return 연결 성공 여부
     */
    suspend fun healthCheck(): Boolean
}

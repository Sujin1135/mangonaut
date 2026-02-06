package io.autofixer.mangonaut.domain.exception

/**
 * 에러 코드 정의
 */
enum class ErrorCode(
    val code: String,
    val description: String,
) {
    // Sentry 관련
    SENTRY_API_ERROR("SENTRY_001", "Sentry API 호출 실패"),
    SENTRY_EVENT_NOT_FOUND("SENTRY_002", "Sentry 이벤트를 찾을 수 없음"),
    SENTRY_PARSE_ERROR("SENTRY_003", "Sentry 응답 파싱 실패"),

    // GitHub 관련
    GITHUB_API_ERROR("GITHUB_001", "GitHub API 호출 실패"),
    GITHUB_FILE_NOT_FOUND("GITHUB_002", "GitHub 파일을 찾을 수 없음"),
    GITHUB_BRANCH_EXISTS("GITHUB_003", "브랜치가 이미 존재함"),
    GITHUB_PR_CREATE_FAILED("GITHUB_004", "PR 생성 실패"),

    // LLM 관련
    LLM_API_ERROR("LLM_001", "LLM API 호출 실패"),
    LLM_PARSE_ERROR("LLM_002", "LLM 응답 파싱 실패"),
    LLM_RATE_LIMITED("LLM_003", "LLM API 요청 제한 초과"),

    // Webhook 관련
    WEBHOOK_VALIDATION_ERROR("WEBHOOK_001", "Webhook 시그니처 검증 실패"),
    WEBHOOK_PARSE_ERROR("WEBHOOK_002", "Webhook 페이로드 파싱 실패"),

    // 일반
    CONFIGURATION_ERROR("CONFIG_001", "설정 오류"),
    DUPLICATE_PROCESSING("PROCESS_001", "중복 처리 시도"),
    RESOURCE_NOT_FOUND("RESOURCE_001", "리소스를 찾을 수 없음"),
    INTERNAL_ERROR("INTERNAL_001", "내부 서버 오류"),
}

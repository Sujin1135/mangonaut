# AI Error Auto-Fixer: Development Reference

> Sentry 에러를 실시간 수신하여 AI로 분석하고, GitHub에 수정 PR을 자동 생성하는 오픈소스 백엔드 서버

---

## 1. 포지셔닝

Sentry Marketplace Integration이 **아닌**, 에러 모니터링 플랫폼에 독립적인 도구.

Sentry의 자체 AI(Seer)가 커버하지 못하는 시장을 타겟한다:

| Seer 한계 | 우리의 차별점 |
|-----------|--------------|
| GitHub만 지원 | GitHub + GitLab + Bitbucket |
| Self-hosted Sentry 미지원 | Self-hosted 포함 지원 |
| LLM 선택 불가 | BYOK: Claude / GPT / Ollama |
| Sentry SaaS 종속 | Sentry, Datadog, Rollbar 등 |

---

## 2. 개발 단계

```
Phase 1 (MVP): Self-hosted 백엔드 서버
  - Sentry Internal Integration (OAuth 불필요, 고정 토큰)
  - Webhook 실시간 수신 → 자동 분석 → GitHub PR 생성
  - Docker Compose로 배포, 사용자가 자기 서버에서 운영
  - LLM 프롬프트 품질 검증에 집중

Phase 2: 멀티 소스 확장
  - GitLab MR 생성, Bitbucket PR 생성
  - Datadog, Rollbar 등 추가 에러 소스
  - Slack/Discord 알림 연동

Phase 3 (선택): SaaS 전환
  - OAuth 멀티테넌트, 웹 대시보드, 결제
  - 오픈소스 커뮤니티 반응 확인 후 결정
```

---

## 3. 기술 스택

| 구성 요소 | 선택 | 비고 |
|-----------|------|------|
| 언어 | **Kotlin** | |
| 프레임워크 | **Spring Boot** | MVC + Virtual Threads 또는 WebFlux + Coroutines (미정) |
| 빌드 | Gradle (Kotlin DSL) | |
| HTTP 클라이언트 | RestClient (MVC) 또는 WebClient (WebFlux) | Sentry / GitHub / LLM API 호출 |
| LLM 연동 | Anthropic / OpenAI REST API 직접 호출 | |
| 모니터링 | Spring Actuator | /health, /metrics |
| 컨테이너 | Docker + Docker Compose | 사용자 배포용 |

### MVC + Virtual Threads vs WebFlux + Coroutines

```
MVC + Virtual Threads:
  - 코드가 직관적 (동기 스타일로 작성, 비동기로 실행)
  - 디버깅/테스트 쉬움
  - Spring 생태계 라이브러리 호환성 높음
  - Java 21+ 필요

WebFlux + Coroutines:
  - Kotlin Coroutines와 자연스러운 통합
  - suspend fun으로 비동기 흐름 명확
  - LLM 응답 스트리밍(SSE) 처리에 유리
  - Reactor 기반 — 학습 곡선 있음
```

> 이 서버의 주요 작업은 외부 API 3개 호출(Sentry, GitHub, LLM)이고 각각 수 초씩 소요될 수 있어 비동기 처리가 필요함.

---

## 4. 아키텍처

### 전체 흐름

```
Sentry Issue 발생
  → Sentry가 Webhook 발송
  → POST /api/webhook/sentry (이 서버)
  → HMAC-SHA256 Signature 검증
  → Issue ID 추출
  → Sentry API로 상세 조회 (StackTrace, Breadcrumbs)
  → GitHub API로 관련 소스코드 조회
  → LLM 분석 (에러 컨텍스트 + 소스코드 → 수정 코드 생성)
  → GitHub에 branch 생성 → commit → PR 오픈
  → (선택) Slack/Discord 알림
```

### 서버 구성

```
┌──────────────────────────────────────────────────────┐
│  Docker Container (Spring Boot)                       │
│                                                       │
│  ┌──────────────────┐    ┌─────────────────────────┐  │
│  │  Webhook Layer    │───▶│  Core Engine             │  │
│  │                  │    │                         │  │
│  │  POST /api/      │    │  Orchestrator            │  │
│  │   webhook/sentry │    │   ├─ Sentry API 조회     │  │
│  │                  │    │   ├─ GitHub 코드 조회     │  │
│  │  Signature 검증   │    │   ├─ LLM 분석            │  │
│  └──────────────────┘    │   └─ GitHub PR 생성      │  │
│                          └─────────────────────────┘  │
│  ┌──────────────────┐                                 │
│  │  Actuator        │  /actuator/health               │
│  │  /metrics        │  운영 모니터링                    │
│  └──────────────────┘                                 │
│                                                       │
│  Sentry Webhook ──▶ :8080                             │
└──────────────────────────────────────────────────────┘
         │
         ▼
   GitHub PR 생성
```

### 프로젝트 구조

```
src/main/kotlin/com/example/autofixer/
├── AutoFixerApplication.kt
│
├── core/                          # 핵심 비즈니스 로직
│   ├── Orchestrator.kt              # 전체 파이프라인 조율
│   ├── ContextBuilder.kt            # 에러 컨텍스트 + 소스코드 조합
│   └── PrBuilder.kt                 # PR 제목/본문/브랜치 생성
│
├── source/                        # Error Source Adapters
│   ├── ErrorSource.kt               # 공통 인터페이스
│   ├── ErrorEvent.kt                # 표준화된 에러 데이터 모델
│   └── sentry/
│       ├── SentrySource.kt          # Sentry API 클라이언트
│       └── SentryParser.kt          # StackTrace/Breadcrumb 파싱
│
├── scm/                           # SCM Providers
│   ├── ScmProvider.kt               # 공통 인터페이스
│   └── github/
│       └── GitHubProvider.kt        # 코드 조회, PR 생성
│
├── llm/                           # LLM Providers
│   ├── LlmProvider.kt               # 공통 인터페이스
│   ├── FixResult.kt                 # 분석 결과 모델
│   └── claude/
│       └── ClaudeProvider.kt        # Claude API 클라이언트
│
├── webhook/                       # Webhook 수신 (서버 진입점)
│   ├── SentryWebhookController.kt   # POST /api/webhook/sentry
│   └── SignatureVerifier.kt         # HMAC-SHA256 검증
│
└── config/
    └── AutoFixerProperties.kt      # application.yml 매핑
```

---

## 5. 핵심 인터페이스

```kotlin
// === Error Source ===
interface ErrorSource {
    val name: String  // "sentry" | "datadog" | ...
    suspend fun fetchEvent(issueId: String): ErrorEvent
}

data class ErrorEvent(
    val id: String,
    val title: String,
    val errorType: String,           // "TypeError"
    val errorMessage: String,        // "Cannot read property 'name' of undefined"
    val stackTrace: List<StackFrame>,
    val breadcrumbs: List<Breadcrumb>,
    val tags: Map<String, String>,
    val request: RequestContext? = null,
    val release: String? = null
)

data class StackFrame(
    val filename: String,            // "com/example/service/UserService.kt"
    val function: String,            // "getUserProfile"
    val lineNo: Int,
    val colNo: Int,
    val preContext: List<String>,
    val contextLine: String,
    val postContext: List<String>,
    val inApp: Boolean               // true = 우리 코드, false = 라이브러리
)

// === SCM Provider ===
interface ScmProvider {
    val name: String  // "github" | "gitlab" | ...
    suspend fun getFileContent(repo: String, path: String, ref: String): String
    suspend fun createBranch(repo: String, baseBranch: String, newBranch: String)
    suspend fun commitFiles(repo: String, branch: String, changes: List<FileChange>, message: String)
    suspend fun createPullRequest(repo: String, params: PrParams): PrResult
    suspend fun hasOpenPR(repo: String, branchName: String): Boolean
}

// === LLM Provider ===
interface LlmProvider {
    val name: String  // "claude" | "openai" | "ollama"
    suspend fun analyze(prompt: String): FixResult
}

data class FixResult(
    val analysis: String,            // 에러 원인 상세 분석
    val rootCause: String,           // 근본 원인 한 줄
    val confidence: Confidence,      // HIGH, MEDIUM, LOW
    val changes: List<FileChange>,
    val prTitle: String,
    val prBody: String
)

data class FileChange(
    val file: String,                // 수정 대상 파일 경로
    val description: String,         // 변경 사항 설명
    val original: String,            // 기존 코드 블록
    val modified: String             // 수정된 코드 블록
)
```

---

## 6. API 연동 요약

### Sentry

```
인증: Internal Integration Token (sntrys_...)
스코프: event:read, project:read, org:read

이벤트 조회:
  GET /api/0/issues/{issue_id}/events/latest/
  
데이터 위치:
  entries[type=exception].data.values[].stacktrace.frames[]
  entries[type=breadcrumbs].data.values[]

Webhook:
  - sentry-hook-signature 헤더로 HMAC-SHA256 검증
  - action: "created" 일 때만 처리
  - payload에 full StackTrace 미포함 → issue ID로 API 재조회 필요

Self-hosted: baseUrl만 변경하면 동일 API
```

### GitHub

```
인증: Personal Access Token (Fine-grained)
권한: Contents R/W, Pull requests R/W, Metadata R

코드 조회:
  GET /repos/{owner}/{repo}/contents/{path}?ref={sha}
  Accept: application/vnd.github.v3.raw

PR 생성 흐름:
  1. GET  .../git/ref/heads/{branch}   → baseSha
  2. POST .../git/refs                 → 브랜치 생성
  3. PUT  .../contents/{path}          → 파일 커밋
  4. POST .../pulls                    → PR 생성
  5. POST .../issues/{pr_number}/labels → 라벨 추가
```

### 경로 매핑

```
StackTrace:  com/example/service/UserService.kt
실제 레포:   src/main/kotlin/com/example/service/UserService.kt
sourceRoot:  src/main/kotlin/
```

---

## 7. LLM 프롬프트 설계

### 컨텍스트 구성

```
1. 에러 타입 + 메시지
2. StackTrace (inApp frames만)
3. Breadcrumbs (시간순, 에러 직전 맥락)
4. 관련 소스 파일 전체 코드
5. (선택) Request context, Tags
```

### 응답 포맷 (Structured JSON)

```json
{
  "analysis": "에러 원인 상세 분석",
  "rootCause": "근본 원인 한 줄",
  "confidence": "HIGH | MEDIUM | LOW",
  "changes": [
    {
      "file": "경로",
      "description": "변경 설명",
      "original": "기존 코드",
      "modified": "수정 코드"
    }
  ],
  "prTitle": "fix: ...",
  "prBody": "PR 본문 마크다운"
}
```

### Confidence 분기

| Confidence | 동작 |
|------------|------|
| HIGH | PR 자동 생성 |
| MEDIUM | 설정에 따라 PR 생성 또는 로깅만 |
| LOW | PR 미생성, 분석 결과만 로깅/알림 |

---

## 8. 설정

```yaml
# application.yml
autofixer:
  source:
    type: sentry
    base-url: https://sentry.io          # self-hosted면 변경
    org: my-org
    token: ${AUTO_FIXER_SENTRY_TOKEN}
    webhook-secret: ${AUTO_FIXER_WEBHOOK_SECRET}

  scm:
    type: github
    token: ${AUTO_FIXER_GITHUB_TOKEN}

  llm:
    provider: claude
    model: claude-sonnet-4-20250514
    api-key: ${AUTO_FIXER_LLM_KEY}

  projects:
    - source-project: my-backend
      scm-repo: org/backend-api
      source-root: src/main/kotlin/
      default-branch: main

  behavior:
    auto-pr: true
    min-confidence: MEDIUM
    labels: [auto-fix, ai-generated]
    branch-prefix: fix/auto-fixer-
    dry-run: false

# Spring Actuator
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
```

---

## 9. PR 생성 규칙

```
브랜치:    fix/auto-fixer-sentry-{issueId}
커밋:      fix: {rootCause 요약}
중복방지:  동일 브랜치명의 open PR이 있으면 skip
라벨:      auto-fix, ai-generated, confidence-{level}

PR Body:
  - 에러 타입/메시지
  - Root Cause 분석
  - 변경 파일별 설명
  - Confidence 표시
  - Sentry 이슈 링크
  - "AI 생성이므로 반드시 리뷰 후 머지" 경고
```

---

## 10. 배포

```yaml
# docker-compose.yml
services:
  auto-fixer:
    image: ghcr.io/<org>/auto-fixer:latest
    ports:
      - "8080:8080"
    environment:
      AUTO_FIXER_SENTRY_TOKEN: ${SENTRY_TOKEN}
      AUTO_FIXER_WEBHOOK_SECRET: ${WEBHOOK_SECRET}
      AUTO_FIXER_GITHUB_TOKEN: ${GITHUB_TOKEN}
      AUTO_FIXER_LLM_KEY: ${LLM_KEY}
    volumes:
      - ./application.yml:/app/config/application.yml:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      retries: 3
```

사용자 설치 흐름:
```
1. docker pull ghcr.io/<org>/auto-fixer:latest
2. .env 파일에 토큰 설정
3. docker compose up -d
4. Sentry → Settings → Internal Integration 생성
   → Webhook URL: https://{server}:8080/api/webhook/sentry
   → Permissions: Issue & Event (Read)
5. 서버 헬스체크: GET /actuator/health
```

---

## 11. MVP Scope

| 구성 요소 | Phase 1 | 후순위 |
|-----------|---------|--------|
| Error Source | **Sentry** | Datadog, Rollbar |
| SCM | **GitHub** | GitLab, Bitbucket |
| LLM | **Claude** | OpenAI, Ollama |
| 인증 | **Internal Integration 토큰** | OAuth |
| 배포 | **Docker Compose** | Kubernetes Helm |
| 알림 | **로깅** | Slack, Discord |

---

## 12. 운영 고려사항

### 재시도 & 에러 핸들링

```
- LLM API 호출 실패 시: 지수 백오프 재시도 (최대 3회)
- GitHub API rate limit: 429 응답 시 Retry-After 대기
- Sentry API 조회 실패: 로깅 후 skip (webhook은 200 응답)
- Webhook 응답: 항상 빠르게 200 반환, 실제 처리는 비동기
```

### 중복 처리 방지

```
- 같은 이슈에 대해 여러 webhook이 올 수 있음
- fix/auto-fixer-sentry-{issueId} 브랜치가 이미 존재하면 skip
- (선택) 처리 중인 이슈 ID를 인메모리 Set으로 관리
```

### 보안

```
- 토큰은 환경변수로만 제공 (설정 파일에 하드코딩 금지)
- Sentry Webhook signature 검증 필수 (HMAC-SHA256)
- LLM에 소스코드가 전송됨 → README에 명시
- 로컬 LLM(Ollama) 옵션으로 외부 전송 없이 사용 가능
```

---

## 13. 향후 SaaS 전환 시 추가 사항

코어 엔진을 인터페이스로 추상화해두면 전환 용이:

```
추가 필요:
  - OAuth 플로우 (Sentry Public Integration + GitHub App)
  - 멀티테넌트 (조직별 데이터 격리)
  - DB (PostgreSQL — 조직/설정/처리이력 저장)
  - 웹 대시보드 (설정 UI + 처리 이력)
  - 결제 (Stripe)
  - Job Queue (비동기 처리 스케일링)

가격 모델:
  OSS 무료 (self-hosted 무제한) / Cloud Free (월 50건) / Cloud Pro (무제한)

라이선스:
  코어 MIT 또는 Apache 2.0 + SaaS 전용 기능은 EE 라이선스

전환 시점:
  GitHub star 1,000+, "hosted 버전 없나" 요청 반복 시
```

---

## 14. 참고 링크

| 자료 | URL |
|------|-----|
| Sentry API | https://docs.sentry.io/api/ |
| Sentry Internal Integration | https://docs.sentry.io/organization/integrations/integration-platform/internal-integration/ |
| Sentry Seer | https://docs.sentry.io/product/ai-in-sentry/seer/ |
| Seer GitLab 요청 | https://github.com/getsentry/sentry/issues/93724 |
| GitHub REST API | https://docs.github.com/en/rest |
| Anthropic API | https://docs.anthropic.com/en/api |

---

## 15. 미결정

- [ ] 라이선스 (MIT / Apache 2.0)
- [ ] LLM 프롬프트 초안 및 실제 Sentry 이슈로 테스트

# Mangonaut

Open-source service that receives Sentry webhooks, lets Claude explore the offending repository through an **agentic tool-use loop**, and opens fix PRs on GitHub.

User-facing operations live in `README.md`; design rationale lives in `PROJECT_REFERENCE.md`. This file is the navigation/conventions guide for engineers (and Claude) working in the codebase.

## Stack

- **Language**: Kotlin 2.3.10 (`-Xjsr305=strict`, `-Xannotation-default-target=param-property`)
- **Runtime**: Java 25 toolchain (set in root `build.gradle.kts`)
- **Framework**: Spring Boot 4.0.2, Spring WebFlux
- **Async**: Kotlin Coroutines + Reactor (coroutine ↔ reactor bridges via `awaitSingle*`)
- **Build**: Gradle (Kotlin DSL), composite of 5 modules under `subproject/`
- **Test**: Kotest 6.0.0.M2 (JUnit5 runner) + MockK 1.14.2

## Module layout (DDD)

```
subproject/
├── boot/            Spring Boot entry point only — assembles other modules
├── domain/          Pure Kotlin. Models, ports, exceptions. NO framework imports.
├── application/     Use cases (orchestration). Depends on domain only.
├── presentation/    REST controllers, DTOs, webhook handling. Depends on domain + application.
└── infrastructure/  Port adapters (Sentry, GitHub, Claude), Spring config. Depends on domain + presentation.
```

Allowed dependency direction (Gradle-enforced — do not add reverse edges):

```
boot ──► {domain, application, presentation, infrastructure}
infrastructure ──► {domain, presentation}
presentation ──► {domain, application}
application ──► {domain}
domain ──► (nothing)
```

Each module has its own `CLAUDE.md` with module-specific rules.

## Where to make a change

| If you are… | Look in |
|---|---|
| Adding a model field, value type, or new error case | `subproject/domain/` |
| Adding/changing business orchestration (skip rules, confidence gates) | `subproject/application/` |
| Touching HTTP entry, webhook parsing, exception → HTTP mapping | `subproject/presentation/` |
| Calling a new external API or changing how Claude/Sentry/GitHub is called | `subproject/infrastructure/` |
| Wiring beans, scanning packages, `application.yml`, app entry | `subproject/boot/` |

## Cross-cutting conventions

- **Identifiers and string-typed values use `@JvmInline value class`.** Example: `RepoId`, `ErrorEvent.Id`. Validate in a factory (`RepoId.from(...)`), not in callers.
- **All external I/O goes through a domain `Port` interface** with an `infrastructure` adapter. Application/presentation never call WebClient or third-party SDKs directly.
- **Exceptions are a sealed hierarchy.** Throw `MangonautException` subtypes carrying an `ErrorCode`. Do not throw `IllegalStateException`/`RuntimeException` from production paths.
- **External calls are `suspend`.** Bridge Reactor with `awaitSingle()` / `awaitSingleOrNull()` inside adapters; keep the suspend boundary at the port.
- **Skips on the happy path return `null`, not exceptions.** Examples: no project mapping, confidence below threshold, duplicate PR, no committable changes after path resolution.
- **`@JvmInline value class` + Jackson**: register the Kotlin module (already done in `JacksonConfig`). Don't add `@JsonValue` ad hoc.

## Build & run

```bash
./gradlew build                              # build all modules + run tests
./gradlew :subproject:boot:bootRun           # run server (port 8080)
./gradlew :subproject:<module>:test          # run a single module's tests
./gradlew :subproject:<module>:check         # lint + tests for one module
```

Only `:subproject:boot` produces a runnable `bootJar`. All other modules build plain `jar`s — do not enable `bootJar` elsewhere.

## Configuration

Runtime config lives in `subproject/boot/src/main/resources/application.yml`, bound to `MangonautProperties` in infrastructure. All secrets come from `MANGONAUT_*` environment variables — never hardcode tokens, keys, or real user data in code, tests, or commit messages. Use dummy values in examples (e.g., `sk-ant-xxxxx`, `LS0tLS1CRUdJ...`).

Required env vars (see `README.md` for details):
`MANGONAUT_SENTRY_ORG`, `MANGONAUT_SENTRY_TOKEN`, `MANGONAUT_WEBHOOK_SECRET`, `MANGONAUT_GITHUB_APP_ID`, `MANGONAUT_GITHUB_INSTALLATION_ID`, `MANGONAUT_GITHUB_PRIVATE_KEY`, `MANGONAUT_LLM_API_KEY`.

## Safety invariants (do not weaken without explicit discussion)

These guards exist because Claude is writing production code through this pipeline. They appear in `infrastructure/adapter/ClaudeAgenticLlmAdapter.kt` and `infrastructure/adapter/GitHubScmAdapter.kt`:

1. **Webhook signature verified before parsing.** HMAC-SHA256 over the raw body using `MANGONAUT_WEBHOOK_SECRET`.
2. **`propose_fix` requires an exact, unique snippet match** in a previously `read_file`-cached file (after CRLF normalization). Hallucinated paths and ambiguous matches are rejected at propose time.
3. **Commit re-validates paths.** Anything that doesn't resolve via the Git Tree API is filtered. If everything is filtered, the use case raises `NoCommittableChangesException` instead of opening an empty PR.
4. **Empty `changes` from the LLM downgrades confidence to LOW** so the auto-PR gate blocks the run.
5. **`read_file` truncates at 200 KB and caches** within a session. `list_directory` caps at 500 entries; `search_code` caps at 20 hits; the agentic loop caps at 25 iterations and 50 proposed changes.

## Style

- Comments: only when *why* is non-obvious. Never restate *what* the code does. No multi-line doc blocks.
- New abstractions need a second caller. Don't add seams for hypothetical futures.
- Tests: Kotest `DescribeSpec`/`BehaviorSpec` + MockK. Re-use `TestFixtures` in `application` rather than re-deriving sample `ErrorEvent`s.

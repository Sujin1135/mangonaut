# application

Use cases — the orchestration layer. Composes domain ports to express *what the system does*, without knowing *how* any of it is implemented.

## Hard rules

- **Depends on `domain` only.** Do not import from `infrastructure` or `presentation`. Gradle won't stop you (the modules are reachable transitively in some configs), but a code review will.
- **Inject ports, not adapters.** `LlmProviderPort`, not `ClaudeAgenticLlmAdapter`.
- **No HTTP, no WebClient, no Jackson, no Spring web.** The only Spring annotation in this module should be `@Service`.

## Use cases

Three top-level use cases. Each is a Spring `@Service` with a single `operator fun invoke(...)`.

| Use case | Responsibility |
|---|---|
| `ProcessErrorAlertUseCase` | Top-level orchestrator. Resolve project mapping → fetch error event → analyze → create PR. Returns `null` when no mapping exists (early exit). |
| `AnalyzeErrorUseCase` | Hands `ErrorEvent` + `RepoContext` to `LlmProviderPort`. **The agentic loop is driven by the LLM adapter, not by this use case.** This file should stay short — its job is to package inputs and forward. |
| `CreateFixPullRequestUseCase` | Confidence gate → duplicate-PR check → branch → commit → PR. Returns `null` on any skip condition. |

## The "skip returns null" rule

Normal-flow skip conditions return `null`, not exceptions:

- No project mapping for the Sentry slug → `ProcessErrorAlertUseCase` returns `null`.
- `auto-pr: false` → `CreateFixPullRequestUseCase` returns `null`.
- Confidence below `min-confidence` → returns `null`.
- An open PR already exists on the target branch → returns `null`.

Exceptions are for failures, not for "nothing to do." The one exception is `NoCommittableChangesException` — raised when path resolution filters every proposed change. It signals an upstream issue (LLM proposed unresolvable paths) and is logged at WARN, not treated as silent skip.

## Adding a use case

1. New `@Service` class in `usecase/`.
2. Single `operator fun invoke(...)` entry point. Suspend if any port call is suspend.
3. Constructor-inject ports only.
4. Test with MockK against the ports; reuse `TestFixtures` in `src/test/.../usecase/` for sample `ErrorEvent`, `FixResult`, `RepoId`.

## Logging

Orchestration is the right place for high-level logs (which path was taken, why a skip happened). Adapters log their own external-call detail. Do not double-log.

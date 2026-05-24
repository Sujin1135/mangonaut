# infrastructure

Adapters for external systems (Sentry, GitHub, Claude), Spring configuration, and `application.yml` ↔ code binding. This is the only module allowed to talk to the outside world.

## Hard rules

- **Adapters implement domain ports, not the other way around.** Each adapter is a `@Component` annotated with its concrete name and exposes the port interface to the rest of the system.
- **No business logic.** If an adapter starts making "should we…" decisions, that logic belongs in `application`. Adapters translate ports ↔ external APIs and nothing else.
- **Wrap external failures in `MangonautException`.** Raw `WebClientResponseException`, `JsonProcessingException`, `JWTCreationException`, etc. must not leak out of this module.

## Port → adapter mapping

| Port (domain) | Adapter (here) |
|---|---|
| `ErrorSourcePort` | `SentryErrorSourceAdapter` |
| `ScmProviderPort` | `GitHubScmAdapter` |
| `LlmProviderPort` | `ClaudeAgenticLlmAdapter` |
| `ProjectMappingPort` | `ProjectMappingAdapter` |

## Configuration package

- `MangonautProperties` — `@ConfigurationProperties("mangonaut")`, nested `data class`es for `sentry`, `github`, `llm`, `behavior`. Add new settings as nested data classes; update `application.yml` and `README.md` in the same change.
- `WebClientConfig` — separate `WebClient` beans for Sentry / GitHub / Claude with their own base URLs and timeouts. Don't share a single WebClient across providers.
- `JacksonConfig` — registers the Kotlin module. Don't add a second `ObjectMapper` bean; reuse this one.
- `CoroutineScopeConfig` — provides `webhookProcessingScope` (Dispatchers.IO + SupervisorJob) used by `SentryWebhookController` for fire-and-forget dispatch.
- `GitHubAppTokenProvider` — generates the short-lived JWT (via `com.auth0:java-jwt`) from app ID + PEM, then exchanges for an installation token. Token caching with expiry is internal; don't bypass it by calling GitHub auth endpoints directly elsewhere.
- `GitHubInstallationRepositoryClient` — periodic (5 min) refresh of the GitHub App's installed repositories, used to map Sentry slugs to `RepoId`s without manual config.

## `ClaudeAgenticLlmAdapter` — the agentic loop

This adapter drives the loop; `application` does not. Key shape (see source for exact code):

- **Tools exposed to Claude** (each defined as a JSON schema sent on every request):
  - `read_file(path)` — fetch and cache file contents. Truncates at 200 KB.
  - `list_directory(path)` — list entries, max 500.
  - `search_code(query)` — code search across the repo, max 20 hits.
  - `propose_fix(path, original, modified, description)` — validated immediately (see below). Adds to staged changes.
  - `finish(summary, rootCause, prTitle, prBody, confidence)` — terminal tool.
- **Caps**: 25 iterations, 4096 max output tokens, 50 staged changes max. Exceeding any cap ends the loop.
- **Propose-time validation**:
  - The `path` must have been read via `read_file` in this session.
  - `original` must appear in the cached content **exactly once** after CRLF normalization. Zero matches = hallucinated edit; >1 match = ambiguous edit; both are rejected and surface as a tool error back to Claude so it can recover.
- **Empty `changes` after `finish` → confidence forced to LOW** so the auto-PR gate blocks publication.
- **Transient (5xx) failures** retry once with backoff, then raise `LlmApiException`.

**Do not weaken any of the propose-time guards without an explicit discussion.** They are the safety net between the LLM and production code.

## `GitHubScmAdapter` — commit safety

`commitFiles` re-resolves every `FileChange.path` against the Git Tree API before writing. Anything that doesn't resolve is filtered. If filtering removes all changes, the adapter raises `NoCommittableChangesException` — the use case treats this as a meaningful failure, not a silent skip. This catches paths that Claude proposed in a form Git doesn't recognize (case mismatch, stale path, etc.).

`hasOpenPR(repo, branchName)` is the duplicate-PR guard; rely on it instead of catching "PR already exists" responses.

Auth flow: `MANGONAUT_GITHUB_APP_ID` + `MANGONAUT_GITHUB_PRIVATE_KEY` → JWT → `POST /app/installations/{id}/access_tokens` → installation token (~1 hour TTL). `GitHubAppTokenProvider` owns this; everything else just asks it for a current token.

## `SentryErrorSourceAdapter`

Sentry's webhook payload **does not include** the full stack trace. The adapter re-fetches via `GET /api/0/issues/{id}/events/latest/` and parses:
- `entries[type=exception].data.values[].stacktrace.frames[]` → `StackFrame`
- `entries[type=breadcrumbs].data.values[]` → `Breadcrumb`
- `tags`, `request`, `release` into the respective domain fields

US vs EU region is controlled by `mangonaut.sentry.base-url` in config — do not hardcode `us.sentry.io`.

## Adding a new external integration

1. Define the `*Port` interface in `domain/port/` with `suspend` methods and `healthCheck()`.
2. Add an adapter class here implementing it.
3. Add a dedicated `WebClient` bean in `WebClientConfig` if it's HTTP.
4. Extend `MangonautProperties` with a nested `data class` for its config.
5. Wire the port into the relevant use case in `application`.
6. Add the new section to `application.yml`, `README.md` env-var table, and the root `CLAUDE.md` env-var list.

## Testing

MockK + Kotest. WebClient tests use the standard `MockWebServer` pattern (or canned `ClientResponse`s) — don't hit live Sentry/GitHub/Anthropic in tests. Token-provider tests should use dummy RSA keys generated in-test, never check fixture keys into the repo.

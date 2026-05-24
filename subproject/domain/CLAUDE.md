# domain

Pure Kotlin core: entities, value objects, port interfaces, sealed exceptions. **Has no dependency on any framework.**

## Hard rules

- **No framework imports.** No Spring, no Jackson, no `kotlinx.coroutines.reactor`, no Reactor types, no WebClient. The only allowed imports outside `kotlin.*` / `kotlinx.*` are JDK + this module itself.
  - `suspend` and `kotlinx.coroutines.*` core types are allowed on port interfaces. Reactor bridges (`awaitSingle`, `Mono`, `Flux`) are not.
- **No annotations from Spring, Jakarta, or Jackson.** If a model needs JSON shaping, do it at the adapter boundary, not on the domain type.
- **No I/O.** Everything in this module is a data shape or an interface. Implementations live in `infrastructure`.

If you find yourself wanting one of the above, the code probably belongs in `infrastructure` (adapter) or `presentation` (DTO), not here.

## Layout

```
domain/
├── model/      Entities, value objects, results
├── port/       Outbound interfaces (Hexagonal "driven" ports)
└── exception/  Sealed MangonautException hierarchy + ErrorCode enum
```

## Conventions

### Value classes for typed identifiers and strings

Every identifier or constrained string is a `@JvmInline value class` with a `from(...)` factory that validates. Examples: `RepoId` (`"owner/repo"`), `ErrorEvent.Id`, `FilePath`. **Do not pass raw `String` across module boundaries** when a value class exists — and add one if the value has invariants.

### Ports

All outbound dependencies are declared as `suspend` interfaces here:

- `ErrorSourcePort` — fetch error events from a monitoring system (Sentry today).
- `ScmProviderPort` — read repo content, create branches/commits/PRs, search code, resolve paths.
- `LlmProviderPort` — drive the agentic analysis loop.
- `ProjectMappingPort` — map a source-system project slug to a `RepoId`.

Every port exposes a `healthCheck()` so `HealthController` can report adapter status without coupling to infrastructure.

When adding a port:
1. Define the interface here with `suspend` methods and a `healthCheck()`.
2. Add the adapter in `infrastructure/adapter/`.
3. Inject the **port type** into application code, never the adapter.

### Exceptions

`MangonautException` is `sealed` and every subtype carries an `ErrorCode`. Two-step rule when adding a new failure mode:

1. Add a case to the `ErrorCode` enum (with HTTP-mappable semantics).
2. Add a subtype of `MangonautException` (or reuse an existing one) carrying that code.

`GlobalExceptionHandler` in `presentation` exhaustively maps these — adding a subtype without updating the handler is a compile-time miss, since the `when` over the sealed type relies on exhaustiveness.

Never throw `IllegalStateException`, `RuntimeException`, or `Error` from production paths. Tests may, but production code wraps in a `MangonautException`.

### Confidence

`FixResult.Confidence` is `LOW`/`MEDIUM`/`HIGH`. `application` gates auto-PR creation on this; do not add intermediate values without updating `CreateFixPullRequestUseCase`.

## Testing

Pure unit tests with Kotest. No Spring context, no mocks of external services (there are no externals to mock — this module has no I/O).

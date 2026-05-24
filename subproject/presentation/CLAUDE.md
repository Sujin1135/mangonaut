# presentation

HTTP entry. REST controllers, request/response DTOs, the webhook signature checkpoint, and the global exception → HTTP mapper.

## Hard rules

- **Depends on `domain` + `application` only.** Never import from `infrastructure`. If a controller needs an external call, it goes through a use case.
- **Use cases are the only thing controllers call.** Don't inject ports directly into controllers.
- **Controllers translate, they don't orchestrate.** Parse → validate → invoke use case → render response. No business branching.

## Webhook handling (`SentryWebhookController`)

Sentry treats `2xx` as "delivered, don't retry." This shapes everything in this controller:

1. **Read raw body as string first.** Signature verification is HMAC-SHA256 over the bytes Sentry sent — parse only after the signature matches.
2. **Verify `Sentry-Hook-Signature` before parsing.** A failed verification throws `WebhookValidationException`; `GlobalExceptionHandler` maps it to 401.
3. **Filter non-processable actions early.** `SentryAction.isProcessable` decides; unprocessable actions return 200 with a status message but don't enqueue work.
4. **Dispatch async, then return 200 immediately.** Use the injected `webhookProcessingScope` (defined in `infrastructure/config/CoroutineScopeConfig`) — `launch { useCase(...) }`. Do not block the request thread on the use case.
5. **Never propagate use-case failures into the HTTP response.** The webhook caller (Sentry) cannot act on them; they belong in logs and metrics.

If you add a new webhook source, follow the same shape: raw-body capture → signature check → action filter → async dispatch → 200.

## DTO ↔ domain conversion

- DTOs live under `dto/`. Conversion is centralized in factories (`SentryWebhookFactory`) — do not inline `dto.toDomain()` mapping logic inside controllers.
- The domain `ErrorEvent`/`FixResult`/`RepoId` are the only types that should cross into `application`. Don't pass DTOs deeper.

## Exception mapping

`GlobalExceptionHandler` (`@ControllerAdvice`) is the **single** place that converts `MangonautException` subtypes to HTTP status codes. Controllers should not catch domain exceptions — let them propagate.

Adding a new exception subtype in `domain` means adding a branch here. The `when` over the sealed hierarchy is exhaustive, so the compiler will flag the gap.

## Health (`HealthController`)

Exposes adapter status via each port's `healthCheck()`. New ports added in `domain` should be wired in here as well so operators can see them in `/actuator/health` and `/health`.

## Style

- Use suspend handlers (WebFlux). Don't mix `Mono<T>` return types with suspend signatures in the same controller.
- Response DTOs are immutable `data class`es. No Jackson annotations unless absolutely required — `JacksonConfig` registers the Kotlin module so defaults work.

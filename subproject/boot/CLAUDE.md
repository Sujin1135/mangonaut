# boot

Spring Boot entry point. Assembles `domain` + `application` + `presentation` + `infrastructure` into a runnable application. **No business logic lives here.**

## Contents

- `MangonautApplication.kt` — `@SpringBootApplication` + `@EnableScheduling` + `@EnableConfigurationProperties(MangonautProperties::class)`.
- `resources/application.yml` — the single source of runtime configuration.

`mainClass` is set in `build.gradle.kts` to `io.autofixer.mangonaut.MangonautApplicationKt`.

## Rules

- **`scanBasePackages` is set explicitly** on `@SpringBootApplication`. Do not replace it with the default (`io.autofixer`) — module-level scanning is intentional and keeps test slices fast.
- **`bootJar` is only enabled on this module.** The root `build.gradle.kts` disables `bootJar` for every other subproject and for the root. Don't re-enable it elsewhere.
- **Do not add `@Configuration`/`@Bean` here.** Wiring belongs in `infrastructure/config/`. The boot module only enables what infrastructure declares.
- **`application.yml` lives only in this module's resources.** Do not duplicate it under any other module. Profile-specific overrides (`application-<profile>.yml`) go in the same directory.

## Running

```bash
./gradlew :subproject:boot:bootRun         # local
./gradlew :subproject:boot:bootJar         # build runnable jar
java -jar subproject/boot/build/libs/boot-0.0.1-SNAPSHOT.jar
```

Required env vars before startup — see root `CLAUDE.md` for the full list. Missing required values surface as `ConfigurationException` during bean init, not at first request.

## Adding a new layer or external module

Add an `implementation(project(":subproject:<new>"))` line in this module's `build.gradle.kts`. The new module also needs its package added to `scanBasePackages` if it contributes Spring components.

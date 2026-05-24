package io.autofixer.mangonaut.domain.model

/**
 * Repository instruction files that LLM coding assistants read for
 * project-level conventions. Filenames vary by tool but the content is
 * provider-agnostic natural-language prose, so any LLM adapter can read
 * any of these.
 *
 * [scope] captures whether the file is meaningful only at the repo root
 * (tools that documented a single fixed location) or hierarchically
 * (tools that documented module-scoped overrides).
 */
enum class RepoConventionFile(
    val filename: String,
    val scope: Scope,
) {
    CLAUDE("CLAUDE.md", Scope.HIERARCHICAL),
    AGENTS("AGENTS.md", Scope.HIERARCHICAL),
    CURSOR_RULES(".cursorrules", Scope.ROOT_ONLY),
    COPILOT_INSTRUCTIONS(".github/copilot-instructions.md", Scope.ROOT_ONLY),
    ;

    enum class Scope {
        /** Loaded only from the repo root — the tool's spec fixes the location. */
        ROOT_ONLY,

        /** Loaded from root AND walked up from the bug file's directory. */
        HIERARCHICAL,
    }
}

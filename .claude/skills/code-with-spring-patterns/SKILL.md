---
name: code-with-spring-patterns
description: Orchestrate existing Spring skills (jpa-patterns, select-spring-design-pattern, implement-spring-backend-feature, test-patterns, review-spring-backend-change) when writing or modifying Spring Boot code in this project. Use before touching src/main/java or src/test/java for feature/refactor work. Not for reviewing an existing PR.
---

# Code With Spring Patterns

When writing Spring Boot code, invoke the project's existing Spring skills in sequence based on the change type.

## 1. When to Use

- Editing `src/main/java/**` or `src/test/java/**` for feature/refactor work
- User asks "implement this feature" or "refactor this"
- After Codex spec-review or `superpowers:writing-plans` has produced a plan, when entering the code phase

## 2. Do Not Use For

- Reviewing a completed PR → call `review-spring-backend-change` directly
- Trivial typos / doc fixes
- Spec/design phase (no code yet) → `superpowers:brainstorming`

## 3. Orchestration Rules

Call the skills below **in order** based on the change target. If multiple apply, call all of them.

| Change Target | Skill to Call |
|---------------|---------------|
| Entity, Repository, QueryDSL, query tuning, N+1 | `jpa-patterns` |
| Service logic needing branch/strategy/event/decorator/adapter/factory/builder | `select-spring-design-pattern` |
| Controller / DTO / API endpoint / exception response / transaction boundary | `implement-spring-backend-feature` |
| Tests (unit/slice/integration) | `test-patterns` |
| Self-review after the above | `review-spring-backend-change` |

## 4. Execution Order

1. **Classify the request**: map the change scope to the table above. If multiple, note them all.
2. **Call the mapped skills in order**. Follow each skill's guidance exactly.
3. **Self-review after implementation**: call `review-spring-backend-change` against the diff you just produced.
4. **Verify**: run `./gradlew test`. Fix root cause on failure. Report to user on success.

## 5. Verification

- Compile: `./gradlew build -x test`
- Tests: `./gradlew test`
- Format: `./gradlew spotlessCheck` (on failure: `./gradlew spotlessApply`)
- If actual app behavior is needed, move to `verify` or `run` skill

## 6. Anti-Patterns

- Skipping the skills and writing code directly → likely to break project conventions
- Calling the skills but ignoring their guidance → defeats the purpose
- Skipping `review-spring-backend-change` → self-review step is required
- Marking the task done without running tests → violates CLAUDE.md "Verification Before Done"

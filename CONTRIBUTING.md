# Contributing

## Before you start

Read [`README.md`](README.md) for setup, and [`documentation/ARCHITECTURE.md`](documentation/ARCHITECTURE.md)
for the layer conventions this codebase follows. Check [`documentation/adr/`](documentation/adr/)
before changing anything related to the toolchain, dependency injection, chart rendering, or the
workout-summary sharing feature — those areas have a specific, already-settled rationale worth
reading first.

## Commits

- Write commit messages in the imperative mood ("Fix X", "Add Y"), explaining **why** a change was
  made, not just what changed line-by-line.
- Keep commits logically scoped — one coherent change per commit rather than a mixed batch. If
  you're fixing a bug and doing an unrelated cleanup, that's two commits.
- Verify the build and test suite pass **before** committing, not after:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

## Tests

- New repository/data-layer tests should use an in-memory fake (see the existing
  `app/src/test/java/com/enil/logez/fakes/Fake*.kt` classes) rather than a mocking framework —
  match the codebase's existing pattern.
- Business logic belongs in `core/domain/calc/*.kt` as pure Kotlin, specifically so it can be
  tested without Robolectric/Android dependencies.
- There is no instrumented/Compose UI test suite yet (`app/src/androidTest/` doesn't exist). If you
  add one, keep it there rather than mixing UI tests into `app/src/test/`.

## Code style

- Avoid comments that just restate what the code already says. A comment is worth writing when it
  explains a non-obvious constraint, a workaround for a specific bug, or a decision that would
  otherwise look arbitrary — that's also the standard the existing ADRs and inline comments in this
  codebase try to meet.
- Don't add new third-party dependencies without checking `documentation/adr/` first — this project
  has a deliberate, previously-discussed stance on when a library is worth pulling in versus writing
  something dependency-free (see ADR-0009 for the reasoning pattern to follow).
- Match the existing package placement: UI logic in `feature/*/…Screen.kt`, state in
  `feature/*/…ViewModel.kt`, pure calculations in `core/domain/calc/`, persistence in `core/data/`.

## Secrets

Never commit `local.properties`, `keystore.properties`, or any `.jks`/`.keystore` file — all are
gitignored. Use `local.properties.example` / `keystore.properties.example` as the template for what
you need to supply locally.

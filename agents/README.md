# Agent Workspace

Shared memory for the sub-agents that research, build, review and verify features.
Every agent reads the relevant files before it starts and writes its output back here.

## Layout

```
agents/
  README.md            this file: the process and the rules
  BACKLOG.md           prioritized feature list with status (owned by the orchestrator/planner)
  research/            research findings + codebase audit (read-only after research phase)
  _template/           copy this folder for every new feature
  <NN-feature-slug>/   one folder per feature
    brief.md           scope, acceptance criteria, design notes   (planner)
    dev-log.md         what was built, commits, decisions, tests  (developer)
    review.md          findings per review round + verdict        (reviewer)
    verify.md          manual test steps, results, screenshots    (verifier)
screenshots/<NN-feature-slug>/   PNGs captured by the verifier
```

## Pipeline per feature

1. **Plan** – planner writes `brief.md` from `BACKLOG.md` + `research/`.
2. **Develop** – developer implements, writes unit tests for critical business logic, commits, fills `dev-log.md`.
3. **Review** – reviewer (fresh context) reads the diff of the feature's commits, writes `review.md`.
   Verdict `CHANGES_REQUESTED` → developer fixes (new `fix:` commits) → re-review. Max 3 rounds.
4. **Verify** – verifier builds, installs on the emulator, runs the acceptance flow, saves screenshots, writes `verify.md`.
   Failures go back to the developer.
5. Orchestrator marks the feature `DONE` in `BACKLOG.md`.

## Rules for all agents

- Work directly on `main`. Do not push. Do not rewrite history (no amend/rebase/reset of existing commits).
- Commit messages: Conventional Commits – `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`. One logical change per commit.
  Short subject line; body only when the *why* isn't obvious. No long prose explaining code.
- Only write inside your own section/file of the feature folder. Append, don't erase other agents' notes.
- Keep docs concise: bullets, file paths, decisions. No code dumps.
- Code standards: simple, readable Kotlin; match existing style; no speculative abstractions; comments only for non-obvious logic.
- Tests: JUnit unit tests for critical business logic only (crypto, persistence/repository, password generation, validation, import/export parsing). No UI tests.
- Build check before handing off: `./gradlew assembleDebug testDebugUnitTest`.

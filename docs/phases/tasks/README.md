# Tasks

**Status:** Ready to assign, 2026-10-14. Each task is written test-first, as [testing.md](../../rules/testing.md) requires.

## How to read a task

```
**F1-03 · Refresh-token rotation** · API · after F1-02 · Owner: —
- Unit (red): …
- E2E (red): …
- Accept: …
```

| Field | Meaning |
|---|---|
| ID | `F<phase>-<number>`. Stable. Never renumbered after assignment |
| Area | `API`, `Web`, `App`, or `Ops` (pipeline, environment, deployment) |
| After | Tasks that must be done first |
| Owner | Empty until someone takes it |
| Unit (red) | The unit test to write first, and what it checks |
| E2E (red) | The end-to-end test to write first, and what it checks |
| Accept | Acceptance criteria. All must be true before the task is done |

A task is done when section 7 of [testing.md](../../rules/testing.md) passes.

## Files

- [F0-tasks.md](F0-tasks.md): foundations.
- [F1-tasks.md](F1-tasks.md): access and catalogs.
- [F2-tasks.md](F2-tasks.md): master data.
- [F3-tasks.md](F3-tasks.md): labels, printers and lookup.
- [F4-tasks.md](F4-tasks.md): stock and material entry.
- [F5-tasks.md](F5-tasks.md): orders.
- [F6-tasks.md](F6-tasks.md): reports and kardex.
- [F7-tasks.md](F7-tasks.md): hardening and deployment.

## Rules for assigning

1. Assign by ID. Two people never take tasks from the same phase that share a file, unless the second one waits for the first.
2. A task whose "After" list is not done is not started, except for its red tests, which can be written in parallel.
3. A phase closes when all its tasks are done **and** its phase document's acceptance criteria pass.

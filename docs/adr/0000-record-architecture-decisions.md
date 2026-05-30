# 0000 — Record architecture decisions

## Status

Accepted, 2026-05-20.

## Context

Asala Calendar is a solo, AI-assisted project. Architecture choices made now
will be hard to recover six months later if they live only in commit messages
or chat history. A future contributor (including a future-me) should be able
to learn the project's load-bearing decisions in one place.

## Decision

Use Architecture Decision Records (ADRs) stored under `docs/adr/`. One file
per decision, numbered in order, Michael Nygard's template:

- Title
- Status
- Context
- Decision
- Consequences

Decisions that get reversed are not deleted; they get a new ADR that points
back and marks the old one Superseded.

## Consequences

- Significant architectural choices have a discoverable home.
- New ADRs are cheap to write (a single markdown file) and reviewable in the
  same PR as the code that depends on them.
- The ADR set will grow over time; the `README.md` index has to be kept in
  sync.
- Trivia and operational notes do not belong here. ADRs are for decisions that
  would be worth re-litigating later.

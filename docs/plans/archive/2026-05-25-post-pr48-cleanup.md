# Post-PR-#48 cleanup plan

> **For agentic workers:** Cleanup PR; no new feature. Execute
> task-by-task. Steps use `- [ ]` for tracking.

**Goal:** Apply the converged review findings from the three
multi-agent review of PR #48 (88fa889): consolidate the now-5-way
ViewModel override-threading duplication, fix override map leaks
on event/calendar delete, harden the DataStore setters, and clear
a small backlog of naming + style + Compose stability nits.

**Branch:** `refactor/post-pr48-cleanup`

**Suggested commit split:**

1. `fix(colors): clean up override entries when an event or local calendar is deleted`
2. `refactor(colors): consolidate visibility-and-overrides pipeline; harden setters; hygiene`

**Out of scope (separate work):**

- All-day chip rendering / WCAG text contrast (A3): visual change,
  belongs in a UX-focused PR.
- Recurring "this and following" reset semantics: design question,
  needs a spec entry.
- Factory `EventRepository` injection: medium-sized refactor,
  not converged across reviews.
- ADR-0006 documenting "per-app overrides live in DataStore":
  one-paragraph; can ride later.

---

## Task 1: Fix override leaks on delete (data integrity)

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModelSheetState.kt`
- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt`
- Add: `app/src/test/kotlin/com/arishawke/asala/calendar/data/OverrideCleanupTest.kt`

- [ ] **1.1** `deleteEvent` extension: on success, when scope is
      `AllEvents`, call `userPreferences.setEventColorOverride(eventId, null)`.
      Skip for `ThisInstance` and `ThisAndFollowing` (those don't
      remove the original `eventId` row; the override is still
      relevant). Comment names the rationale.
- [ ] **1.2** `deleteLocalCalendar`: after the provider delete
      succeeds, call `userPreferences.setCalendarColorOverride(calendarId, null)`.
- [ ] **1.3** Pure helper `shouldClearEventOverrideOnDelete(scope: RecurringEditScope): Boolean`
      in `data/EventDetail.kt` (already a precedence helper home)
      so the cleanup decision is unit-testable without a VM.
- [ ] **1.4** `OverrideCleanupTest`: assert the predicate returns
      true for `AllEvents`, false for the other two scopes.

## Task 2: Consolidate the 5-way `combine(events, hidden, calOverrides, evtOverrides)`

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventItem.kt`
- Modify: `MonthViewModel.kt`, `WeekViewModel.kt`, `DayViewModel.kt`,
  `ScheduleViewModel.kt`, `SearchViewModel.kt`
- Add: `app/src/test/kotlin/com/arishawke/asala/calendar/data/VisibilityAndOverridesTest.kt`

- [ ] **2.1** Add to `EventItem.kt`:
      ```kotlin
      fun List<EventItem>.filteredAndRecolored(
          hidden: Set<Long>,
          calendarOverrides: Map<Long, Int>,
          eventOverrides: Map<Long, Int>,
      ): List<EventItem> = filter { it.calendarId !in hidden }
          .applyColorOverrides(calendarOverrides, eventOverrides)
      ```
      Pure function. Composes the two operations the five ViewModels
      currently chain by hand.
- [ ] **2.2** Update each of the five ViewModels: replace the
      inline `.filter { ... }.applyColorOverrides(...)` chain with
      `.filteredAndRecolored(hidden, cal, evt)`. The `combine`
      arity stays the same (5 args: 4 flows + the events flow), so
      Factories don't change. This is a single-line replacement
      inside the lambda body.
- [ ] **2.3** `VisibilityAndOverridesTest`: hidden filter wins;
      event > calendar > default precedence preserved; empty
      hidden + empty maps returns input (instance equality not
      required since `filter` always copies).

## Task 3: Harden DataStore override setters

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/AppViewModel.kt`

- [ ] **3.1** Wrap `setEventColorOverride`, `setCalendarColorOverride`,
      and `setAccountAvatarColor` bodies in `runCatching { ... }.onFailure { Timber.e(it, ...) }`.
      Failure is rare (disk full / IO error) but currently silent;
      the log makes it observable. UI surfacing deferred until users
      actually report a wrong color (no error path exists today and
      adding one is bigger than this PR).

## Task 4: Compose stability + naming

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/ui/settings/UserPreferences.kt`

- [ ] **4.1** Add `@Immutable` to `UserPrefs` (import
      `androidx.compose.runtime.Immutable`). The maps are
      effectively immutable by construction (every emission is a
      fresh `UserPrefs` from `userPreferences.prefs.map { ... }`),
      so the annotation is honest. Lets Compose's smart-recomposer
      skip on equality.
- [ ] **4.2** Rename parameter `color: Int?` → `argb: Int?` in
      `setAccountAvatarColor`, `setCalendarColorOverride`, and
      `setEventColorOverride` for consistency with the rest of
      the codebase (`RecolorDialog.currentArgb`,
      `EventEditFormState.colorOverrideArgb`,
      `AppViewModel.setCalendarColorOverride(argb: Int)`, etc.).
- [ ] **4.3** Remove the em dash on line ~217 (the JSON-decode
      comment block). Replace with a period.

## Task 5: Drop deprecated `applyCalendarColorOverrides`

**Files:**

- Modify: `app/src/main/kotlin/com/arishawke/asala/calendar/data/EventItem.kt`
- Modify: `app/src/test/kotlin/com/arishawke/asala/calendar/data/EventItemColorOverrideTest.kt`

- [ ] **5.1** Confirm there are no production callers
      (`grep -r applyCalendarColorOverrides app/src/main`).
- [ ] **5.2** Delete the `@Deprecated` extension and its only test
      (`deprecated applyCalendarColorOverrides delegates to the new extension`).
      Project is solo, no external API, so the "one release" promise
      buys nothing.

## Task 6: Spec contrast-threshold doc correction

**Files:**

- Modify: `docs/specs/archive/2026-05-25-color-customization-expansion-design.md`

- [ ] **6.1** The Testing section says ">=4.5:1 against every theme
      surface in both modes"; the body says ">=3:1 in at least one
      mode" (the implemented and tested behavior). Align the Testing
      section to the body.

## Task 7: Static analysis + tests

- [ ] **7.1** `./gradlew :app:spotlessApply` clean.
- [ ] **7.2** `./gradlew :app:detekt :app:lintDebug :app:testDebugUnitTest`
      green. Regenerate detekt baseline only if the changes legitimately
      add violations (the consolidation should reduce LongParameterList).
- [ ] **7.3** `./gradlew :app:assembleRelease` clean (R8 + resource
      shrink).

## Task 8: CHANGELOG

- [ ] **8.1** Add `[Unreleased]` Fixed entry: per-event /
      per-calendar override leaks on delete (data-integrity fix).
      Add Changed entry for the deprecation removal.

## Task 9: Commit + PR + merge + archive

- [ ] **9.1** Commit split (two commits).
- [ ] **9.2** Push, open PR, wait for CI green.
- [ ] **9.3** FF push `main` from local per the project's branch
      hygiene rule.
- [ ] **9.4** Archive this plan to
      `docs/plans/archive/2026-05-25-post-pr48-cleanup.md` in a
      follow-up PR.

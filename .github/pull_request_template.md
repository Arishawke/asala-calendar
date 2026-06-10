<!--
PR template for Asala Calendar. Solo / hobby / offline-first context;
heavier process (STRIDE, mandatory code-reviewer dispatch) intentionally
omitted. Reach for the code-reviewer subagent when it adds value, not on
every PR. Risk-tier checkbox is a one-glance label, not a gate.
-->

## Intent

One sentence: what is this PR for? Link the issue if applicable.

## Risk tier

- [ ] Low: pure refactor, docs, dependency-version bump, internal tooling, formatting.
- [ ] Medium: user-visible feature, schema change without migration, new external dependency, change to permission-gated flow.
- [ ] High: parsing untrusted input (calendar provider data, intent extras from external apps), crypto, file I/O outside the app sandbox, exported component changes, deep-link receiver changes.

For High-risk changes, add a brief security note below explaining what crosses a trust boundary and how it's bounded.

## Test plan

What automated tests cover this? What manual checks did you run? What didn't you test?

For any function with error returns, async I/O, or external dependencies (CalendarContract, AlarmManager, NotificationManager, ContentResolver), include at least one failure-mode test (timeout, permission denied, malformed data, empty input).

## Verification

- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest spotlessCheck detekt` green locally.
- [ ] CHANGELOG.md updated under `[Unreleased]` if the change is user-visible.
- [ ] If permission-gated flow touched: verified with fresh install (`adb uninstall com.arishawke.asala.calendar` then `./gradlew :app:installDebug`).

## Out of scope

What this PR explicitly does not do, so reviewers don't ask.

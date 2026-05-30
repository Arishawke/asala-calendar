# 0004 — Quality gates for Asala Calendar

## Status

Proposed, 2026-05-24.

Tags: tooling, security (supply-chain), CI

## Context

The previous session shipped a cross-project quality-gates initiative
(see the internal quality-gates reference). That work
produced the universal gate index, per-stack tool menu, PR template,
and ADR-with-threat-model extension to Nygard. This ADR is the per-
project application.

Asala's current quality posture (audited 2026-05-24):

- Android lint with `warningsAsErrors = true`, `abortOnError = true`,
  `checkReleaseBuilds = true`. Baseline at `app/lint-baseline.xml`
  (4 entries pre-PR: `DataExtractionRules`, an `ObsoleteSdkInt v26`
  folder, two `MonochromeLauncherIcon`; Phase A adds ~40 more from
  slack/compose-lints).
- Version catalog in `gradle/libs.versions.toml`. AGP 9.1.1, Kotlin
  2.3.21, Compose BOM 2026.05.00.
- CI runs `lintDebug + testDebugUnitTest + assembleDebug` on every
  push and PR. Gitleaks secret-scan job runs alongside.
- Pre-commit hooks: gitleaks plus the standard pre-commit-hooks set
  (trailing-whitespace, end-of-file-fixer, check-yaml, check-merge-
  conflict, check-case-conflict, detect-private-key).
- Dependabot weekly for Gradle and GitHub Actions, grouped by
  `androidx` / `compose` / `kotlin`.
- 14 unit tests under `app/src/test/`, all pure-logic JUnit 4. No
  `androidTest/` directory exists.
- R8 + resource shrink in release; APK reduced ~87% at v0.4.1.

Two activities are `exported="true"` in `AndroidManifest.xml`:
`MainActivity` (LAUNCHER intent filter) and `BootRescheduler`
(`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`
/ `TIMEZONE_CHANGED`). Both are appropriate exports; the system
requires `exported=true` to deliver those broadcasts. No remediation
needed.

Gaps versus the internal quality-gates reference §2 (universal gates):
no Kotlin static analysis beyond Android lint, no Compose-specific
lint, no Compose Compiler stability reports, no coverage tooling,
no screenshot tests, no dependency vulnerability scan beyond
Dependabot for known CVEs, no Gradle verification metadata, no PR
template, no threat-model section convention in ADRs.

This ADR proposes the additive wire-up that closes those gaps,
sequenced to minimize churn.

## Decision

Adopt the gates below in three phases. Earlier phases are additive
and low-risk; later phases require more setup and have higher
operational cost.

### Phase A — additive, low-risk (target: one session)

1. **Spotless 8.5.1 wrapping ktlint 1.5.0.** Style enforcement on
   `src/**/*.kt` and `*.gradle.kts`. Configured at the root
   `build.gradle.kts` so subprojects inherit. Pin ktlint at 1.5.0
   explicitly — Spotless 8.0 (Sept 2025) bumped the default to
   1.7.1, and pinning protects against rule churn on a Spotless
   minor bump.
2. **detekt 1.23.8** with baseline. Enable the rules called out in
   the internal quality-gates reference §4.1: `LongMethod` (60),
   `LongParameterList` (functionThreshold 6, constructorThreshold 7),
   `ComplexCondition` (4), `NestedBlockDepth` (4),
   `MagicNumber` (with `ignoreNumbers: [-1, 0, 1, 2]`,
   `ignoreEnums: true`, `ignoreCompanionObjectPropertyDeclarations:
   true`), `SwallowedException`, `TooGenericExceptionCaught`.
   Generate baseline so existing code does not break CI on day one;
   new violations fail.
3. **Android lint flips.** Promote `HardcodedText`,
   `ContentDescription`, `RtlHardcoded` from warning to error inside
   the existing `lint { }` block. `MissingTranslation` is already
   error by default; do not re-promote. Use `error += setOf(...)`
   (mutating, not assignment).
4. **slack/compose-lints 1.4.3.** Added as a `lintChecks`
   dependency in `app/build.gradle.kts`. No plugin id, no DSL
   block. Note: 1.4.3 shipped 2026-05-06 with several false-
   positive fixes in `SlotReused` / `ModifierReused` and Kotlin 2.2
   + lint API v32 support. Bleeding-edge but actively maintained
   again after a 2024-2026 quiet stretch.
5. **Compose Compiler stability reports.** Add a `composeCompiler
   { reportsDestination = ..., metricsDestination = ...,
   stabilityConfigurationFiles.add(...) }` block to
   `app/build.gradle.kts`. Property is the plural ListProperty
   form (Kotlin 2.0.20+ / Compose Compiler 1.5.12+); the singular
   `stabilityConfigurationFile` is deprecated. Reports emit to
   `app/build/compose_compiler/`.

Phase A produces, on `main`: zero detekt findings (baselined),
zero new lint warnings, three lint checks promoted to error, one
extra lint-checks JAR loaded by Android lint, recompose-stability
reports available on demand.

### Phase B — test infrastructure (target: one session, separate PR)

6. **Kover 0.9.8.** Apply at the root; aggregate into root if /
   when we add modules. Filters exclude `*Activity*`,
   `*Fragment*`, generated Hilt / DI classes, `*ComposableSingletons*`,
   `*Preview*`, and any function annotated `@Composable`. NO
   coverage threshold yet — adopt the tracking first, decide the
   threshold once we have a few weeks of data. Tasks:
   `koverHtmlReport` for HTML output, `koverXmlReport` for CI,
   `koverLog` for quick text summaries.
7. **Roborazzi 1.63.0 — DEFERRED 2026-05-24.** Moved to §"NOT
   adopting" below; see that entry for the diagnosis and the
   condition under which we revisit.

Phase B produces: coverage report visible per PR (`koverXmlReport`
output uploadable as artifact).

### Phase C — heaviest, most disruptive (target: one session, separate PR; can defer)

> Superseded 2026-05-24 by ADR-0005. OWASP DC ran for one cycle and
> consistently added 15-30 minutes to each PR. Replaced with
> `gradle/actions/dependency-submission` + `actions/dependency-review-action`.
> Phase C history preserved below as the audit trail.

8. **OWASP Dependency-Check 12.2.2 Gradle plugin.** PR + nightly
   schedule, not every push (first run downloads ~5-10 minutes of
   NVD feed; subsequent runs are cached). `failBuildOnCVSS = 7.0f`
   (Float). Requires an NVD API key as a GitHub Actions secret
   (`NVD_API_KEY`); 12.1.0+ effectively requires this to avoid
   rate-limit timeout. Cache `~/.gradle/dependency-check-data` in
   CI. Suppression file at `config/owasp/suppressions.xml` for
   the inevitable false positives.
9. **Gradle dependency verification metadata — DEFERRED 2026-05-24.**
   Moved to §"NOT adopting" below; see that entry for the diagnosis
   and the condition under which we revisit.

Phase C produces: known-CVE scanner running on PR and nightly. The
cryptographic-verification piece is deferred per item 9 above.

### Workflow gates (no Gradle config)

10. **PR template at `.github/pull_request_template.md`.** Copy
    the markdown block from the internal quality-gates reference §5
    verbatim, adjusted only to omit fields not relevant to a
    single-developer offline app (Risk tier still useful, Threat
    model still useful for security-tagged changes, AI-contribution
    disclosure useful, code-reviewer subagent checkbox useful, PR
    size still useful).
11. **Threat-model section in security-flavored ADRs.** This ADR
    (0004) is the first to use it (see below). Going forward, any
    ADR tagged `security` includes the STRIDE table per
    the internal quality-gates reference §6.
12. **Code-reviewer subagent dispatched before declaring a PR done.**
    The code-review subagent was updated
    in a prior session to read those gates on every
    invocation; this ADR records the workflow rule for Asala. The
    PR template checkbox is the enforcement mechanism.

### NOT adopting

- **detekt 2.0.0-alpha.** The new `dev.detekt` plugin id is in
  active alpha with full Kotlin 2.x analyzer support, but is not
  stable. Revisit once 2.0.x ships GA. Trade-off accepted: 1.23.8's
  bundled Kotlin compiler may parse K2-only syntax oddly given
  Asala is on Kotlin 2.3.21.
- **Migration to Renovate from Dependabot.** The quality-gates
  research recommended Renovate for Gradle Version Catalog support,
  but Dependabot's current grouping (`androidx`, `compose`,
  `kotlin`) is working. Migration is a separate small ADR; not
  urgent.
- **Coverage threshold enforcement.** Collect data via Kover first;
  pick a threshold once we know what current coverage actually is.
  An arbitrary 80% set today would either be vacuous (already met)
  or noisy (immediately failing). Decide based on data.
- **JaCoCo.** Kover is Kotlin-native and the official JetBrains
  choice for Kotlin-heavy modules. JaCoCo only makes sense for
  mixed-Java codebases or existing Sonar pipelines; neither applies.
- **Paparazzi.** Roborazzi is the only screenshot framework
  supporting interactions (Paparazzi 2.0 has been alpha since
  2024). Same caveat applies as Roborazzi's deferral below;
  revisit the choice when AGP-9 compatibility lands in either tool.
- **Roborazzi 1.63.0 (DEFERRED 2026-05-24).** Roborazzi's preview-
  scanner pipeline cannot discover `@Preview` functions under AGP 9's
  built-in Kotlin compilation. PR #782 (1.56.0) fixed task plumbing
  but not the scanner classpath; nothing through 1.63.0 fixes it
  either. Tracking: Roborazzi issue #830. Revisit when AGP 9
  built-in Kotlin support lands in a release.
- **Gradle dependency verification metadata (DEFERRED 2026-05-24).**
  AGP's `aapt2` binary resolves via a detached configuration that
  bypasses `--write-verification-metadata`, so bootstrap can never
  capture it; every build then fails on the unrecorded artifact.
  Combined with per-Dependabot-PR re-bootstrap friction, the cost
  outweighs the benefit for Asala's offline single-user threat model.
  Local bootstrap files are gitignored (`gradle/verification-
  metadata.xml`, keyring files, plus `.deferred-bootstrap` rename
  variants). Revisit if Gradle / AGP solve the aapt2 capture gap
  upstream, or if Asala adds a remote backend that shifts the
  threat model.

## Threat model

Data flow: build pipeline pulls Maven dependencies from Google +
Maven Central + Gradle Plugin Portal, OSS code goes through static
analysis (Spotless, ktlint, detekt, lint), tests run, R8 minifies,
APK signs (locally, not in CI per current setup), GitHub Actions
emits artifacts.

Most STRIDE rows below are about the build / supply-chain surface,
not Asala's runtime. Asala is an offline single-user Android app
with no auth, no remote DB, no network beyond DAVx5 sync via
calendar provider (which is the user's choice and outside the
threat surface of this ADR).

| Threat (STRIDE) | Scenario | Mitigation |
|---|---|---|
| Spoofing | Attacker publishes a slop-squatted dep that resolves to our package name (less common in Maven than npm, but possible with version catalog typos). | Maven Central / Google's per-package signing; Dependabot grouped updates so a new resolution is reviewable; version catalog committed and PR-reviewed on every change. Note: Gradle verification metadata was originally planned (Phase C2) for cryptographic verification on every resolve, but is DEFERRED per §"NOT adopting" due to aapt2 detached-config capture gap + Dependabot operational cost. |
| Tampering | Compromised dep substitution in transit or at rest in the Gradle cache. | TLS to Maven Central / Google (already enforced). Detection at runtime falls back to the per-package signing model rather than per-artifact sha256 + PGP. Acceptable given the Phase C2 deferral rationale. |
| Repudiation | Unknown who introduced a regression. | Signed commits enforced on `main` (already in place). PR template requires AI-contribution disclosure (Phase Workflow). |
| Information disclosure | NVD API key leaks via OWASP plugin invocation in CI logs. | Store as GitHub Actions secret (`NVD_API_KEY`); the plugin reads it via env var, never echoed. Rotate annually. |
| Denial of service | OWASP plugin first-run downloads ~5-10 min of NVD feed; runaway CI minutes. | Cache `~/.gradle/dependency-check-data` in CI; schedule plugin on PR + nightly, not every push. |
| Elevation of privilege | N/A. Asala is an offline app with no auth surface; build pipeline runs on GitHub-hosted runners with default scoping (no production credentials, no deploy targets). | N/A explicitly. |

Threats out of scope for this ADR (and reason):

- Runtime app threats (intent redirection, deep-link hijack,
  WebView XSS) — Asala has no WebView, no deep-link receiver, no
  custom-scheme handling. Both `exported=true` components are
  system-broadcast receivers / launchers with appropriate guards.
- Calendar provider tampering — that's the user's data; the threat
  model belongs to the calendar storage choice (covered in ADR-
  0001).

## Verification

How we will validate each phase before considering it shipped:

**Phase A:**
- `./gradlew :app:lintDebug :app:testDebugUnitTest spotlessCheck detekt` returns green on `main` after the baseline is committed.
- New PR that introduces a `LongMethod` (61+ lines) fails CI with a detekt finding.
- New PR that hardcodes a string in a Composable fails CI with `HardcodedText` lint error.
- Compose Compiler reports emit to `app/build/compose_compiler/` after a release build; manually inspect one report to confirm stability information is present.

**Phase B:**
- `./gradlew koverHtmlReport` produces a report at `app/build/reports/kover/html/index.html`; coverage number is recorded in CHANGELOG under the v0.x.0 release that lands the gate.
- (Roborazzi acceptance criteria deferred along with the gate; see §"NOT adopting → Roborazzi 1.63.0 (DEFERRED 2026-05-24)".)

**Phase C:**
- `./gradlew dependencyCheckAnalyze` runs in CI on PR and nightly with no `failBuildOnCVSS` exceedance for the current dep set (or: any exceedance has a suppression with rationale in `config/owasp/suppressions.xml`).
- (Gradle dependency verification metadata acceptance criteria deferred along with the gate; see §"NOT adopting → Gradle dependency verification metadata (DEFERRED 2026-05-24)".)

**Workflow:**
- New PR opened against the repo shows the template's prefilled fields.
- One PR uses the code-reviewer subagent and its output is referenced in the PR description; SEVERE findings (if any) are addressed before merge.

## Consequences

**Becomes easier:**
- AI-generated PRs subject to style / smell / complexity gates without manual review of each.
- Recompose / stability regressions become visible before users feel them.
- Screenshot regressions caught before a release builds the wrong-colored Day view.
- Known-CVE'd deps caught at CI rather than at a future audit.
- Code-reviewer subagent gives writer-reviewer separation on every PR cheaply.

**Becomes harder:**
- PR cycle time slightly longer (a few extra Gradle tasks; Spotless can auto-fix with `spotlessApply`).
- Adding a new dep requires regenerating `verification-metadata.xml` (Phase C only); typically a one-minute cost on the dev machine.
- Screenshot PNGs add LFS-tracked binaries; the repo size grows slowly with each new `@Preview`.
- Three new lint errors mean three new categories of CI failure to debug for new contributors.

**What we accept:**
- detekt 1.23.8's bundled Kotlin compiler may have limited support for some K2-only syntax. The fix (detekt 2.0) is alpha; the trade-off is acceptable until 2.0 ships GA.
- compose-lints 1.4.3 is the bleeding-edge release (May 2026); minor false-positive risk from `SlotReused` / `ModifierReused` rule changes. Acceptable because the rules catch real bugs and the project is actively maintained again.
- OWASP plugin's NVD-feed-download cost on first CI run (cached afterward); requires an NVD API key as a secret.
- Gradle verification metadata bootstrap trusts the current local artifact set. The mitigation (diff against a second-machine clean bootstrap) is a one-time operational cost.

**What we explicitly do not change:**
- Dependabot stays as the dep-update bot; no Renovate migration.
- Single-module Gradle structure; no `:core` / `:ui` split.
- No Hilt or DI framework; manual factory pattern continues.
- Conventional Commits, CHANGELOG-with-every-commit, prerelease tag policy all unchanged.

## Follow-ups (deferred to future ADRs / PRs)

Tracked here so they're not lost; each warrants its own small ADR
or PR rather than folding into Phase A / B / C above.

- **Automated release-APK upload.** Today release APKs reach
  GitHub Releases via manual upload (working through v0.6.0,
  dropped for v0.7.0 and v0.8.0). The fix is one workflow file
  (~50 lines): trigger on tag push, restore the signing keystore
  from a base64-encoded GitHub Actions secret, run
  `./gradlew :app:assembleRelease`, upload the signed APK to the
  release. `app/build.gradle.kts` already reads `SIGNING_*` env
  vars (lines 19-22), so no app-side change needed. Worth its
  own small ADR-0005 to document the keystore-in-secret approach
  and rotation policy. Backfill v0.7.0 / v0.8.0 with a local
  build + `gh release upload` is optional; old enough that
  downloaders who care can build from source.
- **OWASP path-filter narrowing.** This ADR's Phase C set up
  the `security-scan.yml` workflow with a path filter so only
  PRs that touch `gradle/libs.versions.toml`, any `build.gradle.kts`,
  `config/owasp/`, or the workflow itself trigger the scan.
  If false-positive triggers surface (e.g. a `build.gradle.kts`
  comment change firing the scan), tighten the filter further.
- **Roborazzi revisit.** Watch Roborazzi releases for AGP 9
  built-in Kotlin support and revisit the Phase B item 7
  deferral when shipped.
- **Gradle dependency verification metadata revisit.** Watch
  Gradle / AGP issue trackers for aapt2 detached-config capture
  fix and revisit Phase C2 deferral when shipped, or when Asala
  adds a remote backend that shifts the threat model.

# Quality gates for Asala Calendar: execution plan

> **For agentic workers:** Use `superpowers:executing-plans` to walk this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. Each phase is self-contained: ends with verify + commit and is its own PR.

**Goal:** Wire the universal quality-gates from an internal quality-gates reference into Asala Calendar per ADR-0004. Three phases (low-risk additive → test infrastructure → heaviest), three PRs.

**ADR reference:** [docs/adr/0004-quality-gates.md](../adr/0004-quality-gates.md)

**Versions (pinned May 2026):** Spotless 8.5.1, ktlint 1.5.0, detekt 1.23.8, slack/compose-lints 1.4.3, Kover 0.9.8, Roborazzi 1.63.0, OWASP Dependency-Check 12.2.2. All Gradle DSL examples assume Kotlin DSL (`.kts`), AGP 9.x, Kotlin 2.3.x, version catalog at `gradle/libs.versions.toml`.

---

## Phase A — Additive, low-risk (one session, one PR)

Spotless+ktlint, detekt+baseline, Android lint flips, slack/compose-lints, Compose Compiler stability reports, plus the workflow gates (PR template, code-reviewer rule).

### A1. Spotless + ktlint

- [ ] Add to `gradle/libs.versions.toml`:
  - `[versions]` block: `spotless = "8.5.1"`, `ktlint = "1.5.0"`
  - `[plugins]` block: `spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }`
- [ ] Add `alias(libs.plugins.spotless) apply false` to root `build.gradle.kts` plugins block.
- [ ] Add `subprojects { apply(plugin = "com.diffplug.spotless"); configure<...> { kotlin { target("src/**/*.kt"); targetExclude("**/build/**", "**/generated/**"); ktlint(libs.versions.ktlint.get()) }; kotlinGradle { target("*.gradle.kts"); ktlint(libs.versions.ktlint.get()) } } }` block to root `build.gradle.kts`.
- [ ] Run `./gradlew spotlessApply` once to auto-format the existing codebase. Commit the formatting changes as their own commit (`style:` per Conventional Commits) so the substantive Phase A1 diff stays clean.
- [ ] Confirm `./gradlew spotlessCheck` is green.

### A2. detekt with baseline

- [ ] Add to `gradle/libs.versions.toml`:
  - `[versions]`: `detekt = "1.23.8"`
  - `[plugins]`: `detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }`
- [ ] Add `alias(libs.plugins.detekt) apply false` to root `build.gradle.kts`.
- [ ] Add `subprojects` apply + DetektExtension configure block to root `build.gradle.kts`:
  - `toolVersion = libs.versions.detekt.get()`, `buildUponDefaultConfig = true`, `allRules = false`, `parallel = true`
  - `config.setFrom(rootProject.files("config/detekt/detekt.yml"))`
  - `baseline = file("$rootDir/config/detekt/baseline.xml")`
- [ ] Create `config/detekt/detekt.yml` with the rules in ADR-0004 §A2: LongMethod (60), LongParameterList (functionThreshold 6, constructorThreshold 7), ComplexCondition (4), NestedBlockDepth (4), MagicNumber (with the documented `ignoreNumbers: [-1,0,1,2]`, `ignoreEnums: true`, etc.), SwallowedException, TooGenericExceptionCaught.
- [ ] Generate baseline: `./gradlew detektBaseline`. Commit `config/detekt/baseline.xml`.
- [ ] Confirm `./gradlew detekt` is green.

### A3. Android lint flips

- [ ] Inside `app/build.gradle.kts` `android { lint { ... } }` block (already exists), add: `error += setOf("HardcodedText", "ContentDescription", "RtlHardcoded")`.
- [ ] Run `./gradlew :app:lintDebug`. If new errors surface (likely from existing strings or content descriptions), either fix in this PR (small set) or add to `app/lint-baseline.xml` with a TODO comment to fix in follow-up. Do not silently disable the rule.
- [ ] Confirm `./gradlew :app:lintDebug` is green.

### A4. slack/compose-lints

- [ ] Add to `gradle/libs.versions.toml`:
  - `[versions]`: `composeLints = "1.4.3"`
  - `[libraries]`: `compose-lint-checks = { module = "com.slack.lint.compose:compose-lint-checks", version.ref = "composeLints" }`
- [ ] Add `lintChecks(libs.compose.lint.checks)` to `dependencies { }` block in `app/build.gradle.kts`.
- [ ] Run `./gradlew :app:lintDebug`. Address any new Compose findings or add to lint baseline.
- [ ] Confirm lint green.

### A5. Compose Compiler stability reports

- [ ] Add to `app/build.gradle.kts` (inside the `android { }` block or as a top-level `composeCompiler { }` block — the plugin auto-detects):
  ```kotlin
  composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.add(
      rootProject.layout.projectDirectory.file("config/compose/stability_config.conf")
    )
  }
  ```
- [ ] Create `config/compose/stability_config.conf` with one fully-qualified class name per line (start empty; add as we discover unstable types from the first report).
- [ ] Run `./gradlew :app:assembleRelease`. Confirm reports emit to `app/build/compose_compiler/`.
- [ ] Spot-check one report for any unexpectedly-unstable composables; file follow-ups for the worst offenders.

### A6. PR template

- [ ] Create `.github/pull_request_template.md` by adapting an internal quality-gates reference §5. Remove fields not relevant to a solo offline app:
  - Keep: Intent, Risk tier, AI contribution disclosure, Test plan, Threat model (if Risk tier ≥ Medium), Code-reviewer subagent checkbox, PR size, Out of scope.
  - Adjust language to match Asala's voice (no AI self-reference in commit messages; PR description is fine).

### A7. CI updates

- [ ] Update `.github/workflows/ci.yml` `build` job to add Phase A gradle tasks:
  - Existing: `./gradlew lintDebug testDebugUnitTest assembleDebug`
  - New: `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug`
- [ ] Confirm CI runs green on the Phase A PR. If detekt finds new issues from the formatting-changed code, regenerate baseline.

### A8. CHANGELOG + commit Phase A

- [ ] Update `CHANGELOG.md` under `[Unreleased]`:
  - `### Added` — Spotless+ktlint, detekt with baseline, slack/compose-lints, Compose Compiler stability reports, PR template, three lint flips (HardcodedText / ContentDescription / RtlHardcoded → error).
- [ ] Commit per file group with Conventional Commits prefix. Suggested split:
  - `style: spotless-format existing codebase` (auto-format only)
  - `chore: wire Spotless 8.5.1 + ktlint 1.5.0 enforcement`
  - `chore: wire detekt 1.23.8 with baseline`
  - `chore: flip HardcodedText / ContentDescription / RtlHardcoded to lint error`
  - `chore: wire slack/compose-lints 1.4.3`
  - `chore: enable Compose Compiler stability reports`
  - `docs: add PR template per quality-gates.md`
  - `chore(ci): add spotless + detekt to build job`
- [ ] Push to feature branch `quality-gates-phase-a`. Open PR using new template. Dispatch `code-reviewer` subagent against the diff. Address SEVERE findings. Merge after CI green.

---

## Phase B — Test infrastructure (separate session, separate PR)

Kover for coverage; Roborazzi for screenshot tests. No `androidTest/` directory needed.

### B1. Kover 0.9.8

- [ ] Add to `gradle/libs.versions.toml`:
  - `[versions]`: `kover = "0.9.8"`
  - `[plugins]`: `kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }`
- [ ] Apply at root: `alias(libs.plugins.kover)` in root `build.gradle.kts` plugins block. Add `dependencies { kover(project(":app")) }` to root for the aggregate report.
- [ ] Add `kover { reports { total { html { onCheck = false }; xml { onCheck = false }; filters { excludes { classes("*Activity*", "*Fragment*", "*_Factory*", "*_HiltModules*", "*ComposableSingletons*", "*Preview*"); annotatedBy("androidx.compose.runtime.Composable") } } } } }` block to root `build.gradle.kts`.
- [ ] Run `./gradlew koverHtmlReport`. Open `app/build/reports/kover/html/index.html` and record the baseline coverage percent.
- [ ] Add the baseline coverage percent to CHANGELOG (`### Changed` or `### Added`). Do NOT enforce a threshold yet.

### B2. Roborazzi 1.63.0 — DEFERRED 2026-05-24

Wire-up attempted in this session, blocked by upstream gap:
Roborazzi 1.63.0 + ComposablePreviewScanner-android 0.9.0 cannot
discover `@Preview` functions on the test runtime classpath under
AGP 9.0+'s built-in Kotlin compilation. The generated parameterized
test class compiles but discovers 0 previews at runtime, so JUnit
reports "no tests found". Diagnostics + recommendation in
ADR-0004 §"NOT adopting → Roborazzi 1.63.0 — DEFERRED".

Revisit when Roborazzi explicitly notes AGP 9 built-in Kotlin support
in a release. Watch Roborazzi releases and issue #830. No checkbox
work in this plan until then.

### B3. CI updates

- [x] Update `.github/workflows/ci.yml` `build` job:
  - Add: `koverXmlReport` after the existing tasks.
  - (Roborazzi tasks deferred per B2 above.)
  - Also: add `--continue` to the gradle chain so all gates report
    even when an early one fails (addressing the deferred MODERATE
    from PR #19's review).
- [x] Upload Kover XML report as CI artifact on every run (not just
  failure) so coverage trends are visible per PR.

### B4. CHANGELOG + commit Phase B

- [ ] Update `CHANGELOG.md` `[Unreleased]`:
  - `### Added` — Kover 0.9.8 coverage tracking (baseline X%). Note: Roborazzi deferred per ADR-0004.
- [ ] Commit:
  - `chore: wire Kover 0.9.8 coverage tracking (no threshold)`
  - `chore(ci): run koverXmlReport on PR`
- [ ] Branch `quality-gates-phase-b`. PR. Code-reviewer subagent. CI green. Merge.

---

## Phase C — Heaviest, most disruptive (separate session, separate PR; can be deferred indefinitely without blocking)

OWASP Dependency-Check on PR + nightly. Gradle dependency verification metadata.

### C1. OWASP Dependency-Check 12.2.2

- [ ] Get a free NVD API key from https://nvd.nist.gov/developers/request-an-api-key.
- [ ] Add the key as a GitHub Actions secret named `NVD_API_KEY` at the repo level. Do NOT commit it.
- [ ] Add to `gradle/libs.versions.toml`:
  - `[versions]`: `owaspDepCheck = "12.2.2"`
  - `[plugins]`: `owasp-depcheck = { id = "org.owasp.dependencycheck", version.ref = "owaspDepCheck" }`
- [ ] Apply at root: `alias(libs.plugins.owasp.depcheck)` in root `build.gradle.kts`.
- [ ] Add `dependencyCheck { failBuildOnCVSS = 7.0f; formats = listOf("HTML", "SARIF"); nvd.apiKey = System.getenv("NVD_API_KEY"); suppressionFile = "config/owasp/suppressions.xml"; analyzers.assemblyEnabled = false; analyzers.nodeEnabled = false }` block to root `build.gradle.kts`.
- [ ] Create an empty `config/owasp/suppressions.xml` skeleton (root element + comment explaining the format).
- [ ] Create a NEW CI workflow `.github/workflows/security-scan.yml`:
  - Triggers: `pull_request` to main, plus `schedule: cron: '0 6 * * *'` (nightly 06:00 UTC).
  - Steps: checkout, JDK 17, cache `~/.gradle/dependency-check-data`, restore `NVD_API_KEY` secret, `./gradlew dependencyCheckAnalyze --no-daemon`, upload SARIF as artifact.
- [ ] First CI run will download the NVD feed (~5-10 min). Confirm the cache restores correctly on the second run.
- [ ] If any current dep fails the CVSS 7.0 threshold, add an entry to `config/owasp/suppressions.xml` with a rationale, OR upgrade the dep. Never silently accept.

### C2. Gradle dependency verification metadata — DEFERRED 2026-05-24

Wire-up attempted in this session, deferred. Two blockers:

1. AGP's `aapt2` binary cannot be captured by
   `--write-verification-metadata` (resolves via detached
   configuration that bypasses the capture mechanism). Every build
   after bootstrap then fails verification on the unrecorded
   `aapt2-9.1.1-14792394-linux.jar` and `.pom`.
2. Per-Dependabot-PR friction: every accepted dep upgrade requires
   re-bootstrapping the metadata. With the aapt2 gap above, this
   becomes ongoing operational cost rather than the plan's
   originally-budgeted "week of babysitting."

Diagnosis and the cost/benefit reasoning live in ADR-0004 §"NOT
adopting → Gradle dependency verification metadata (DEFERRED
2026-05-24)". Generated files (`verification-metadata.xml`,
`verification-keyring.gpg`, `verification-keyring.keys`) gitignored
in `.gitignore` so a future bootstrap attempt does not accidentally
commit them.

Revisit when (a) Gradle / AGP solve aapt2 detached-config capture
upstream, OR (b) Asala adds a remote backend whose threat model
shifts the cost/benefit balance.

### C3. CHANGELOG + commit Phase C

- [x] Update `CHANGELOG.md` `[Unreleased]`:
  - `### Added` — OWASP Dependency-Check 12.2.2 in CI (PR + nightly).
  - `### Notes` — Gradle dependency verification metadata DEFERRED
    per C2 deferral above; rationale links to ADR-0004.
- [x] Commit:
  - `chore: wire OWASP Dependency-Check 12.2.2 (PR + nightly)`
  - `docs: defer Gradle dependency verification metadata (aapt2 gap)`
- [x] Branch `quality-gates-phase-c`. PR. Code-reviewer subagent. CI green. Merge.

### C4. Operational follow-up — N/A after C2 deferral

Originally scoped to babysit Dependabot PRs through the first week
of verification-metadata churn. Not applicable with C2 deferred.

If Phase C2 is revisited later (per ADR-0004 §"NOT adopting"
revisit conditions), restore the C4 babysitting checklist at that
time.

---

## Cross-phase verification

After each phase ships:
- [ ] CI green on the PR.
- [ ] Lint baseline updated if needed (committed in the same PR, not a follow-up).
- [ ] CHANGELOG entry under `[Unreleased]`.
- [ ] Memory updated if a non-obvious lesson surfaced (e.g. "compose-lints 1.4.3 false-positives in X file").

After all three phases ship and before tagging the next release:
- [ ] Manual fresh-install device test (per internal test notes): `adb uninstall com.arishawke.asala.calendar` then `./gradlew :app:installDebug`. Confirm app launches, calendar permission flow, notification permission flow.
- [ ] Tag release `vX.Y.Z` per the internal release/publish notes (push to feature branch, PR, CI green, FF push to main; do not use `gh pr merge --merge`).
- [ ] Release notes acknowledge the quality-gates initiative.

---

## Out of scope (file as separate work)

- Migration from Dependabot to Renovate (separate ADR-0005 if pursued; current Dependabot grouping works).
- Coverage threshold enforcement in Kover (collect data first, decide threshold once a few weeks of PRs are visible).
- detekt 2.0.0-alpha migration (revisit once 2.0.x ships GA).
- Roborazzi diff PR comments via the official GitHub Action (nice-to-have; not blocking).
- TypeScript projects' equivalent quality-gates wire-up (luxe_cafe_dashboard, cat_management_tracker) — separate plans, separate sessions.
- Carry-forward items from the v0.8.0 handoff (a11y audit, multi-push origin split, RELEASE.md, pre-flight release script, snooze long-term verify, README screenshot refresh, branch cleanup).

---

## Estimated effort

- Phase A: one session (~2-3 hours including PR + verify).
- Phase B: one session (~2-3 hours, more if Git LFS setup is needed).
- Phase C: one session for setup, plus a week of babysitting Dependabot PRs as verification metadata regens on each accepted update.

Total: 2-3 sessions for the wire-up; ongoing operational cost is one Gradle command (`--write-verification-metadata`) per accepted dep upgrade.

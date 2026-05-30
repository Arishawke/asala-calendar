# 0005 — Replace OWASP Dependency-Check with GitHub-native scanning

Status: Accepted, 2026-05-24. Supersedes ADR-0004 §Phase C.

## Why

OWASP Dependency-Check ran for one merge cycle and consistently
added 15-30 minutes to each PR's CI even with NVD-feed caching and a
path-filter scoping the scan to dep-touching PRs. For a solo-dev
offline Android app with no remote backend, that operational cost
was not justified by the security signal returned (Dependabot
already catches the same CVEs in the runtime dep set).

Two secondary facts reinforce the swap:
- NVD enrichment is degrading in 2026 (NIST moved to risk-based
  triage; ~80% of new CVEs lack CPE / CVSS / CWE). OWASP DC's
  matching engine depends on those identifiers.
- The Trivy 2026-03-19 supply-chain compromise made the
  third-party-scanner risk concrete. First-party `gradle/actions/*`
  and `actions/*` reduce that surface.

## What changes

Replace `.github/workflows/security-scan.yml` and the
`dependencyCheck` Gradle wiring with two GitHub-native workflows:

- `.github/workflows/dependency-graph.yml`:
  `gradle/actions/dependency-submission@v6.1.0` on push to main,
  PRs to main, weekly Monday 06:00 UTC. `cache-provider: basic`
  (MIT-licensed cache, since v6.0.0 moved enhanced caching under
  Gradle ToU). `dependency-graph-include-configurations:
  '(debug|release)RuntimeClasspath'` scopes the submitted graph
  to what actually ships in the APK; without this, Gradle and AGP
  plugin internals (Netty, BouncyCastle, jose4j, etc.) appear in
  the graph and Dependabot raises alerts on build-tool CVEs that
  are not in Asala's runtime. Same scope decision as PR #21's
  OWASP setup.
- `.github/workflows/dependency-review.yml`:
  `actions/dependency-review-action@v5.0.0` on PRs.
  `fail-on-severity: moderate`, `allow-licenses` whitelist of
  GPL-3.0-or-later + permissives (MIT, Apache-2.0, BSD-2/3, ISC).
  `retry-on-snapshot-warnings: true` + `3600s timeout` for the
  base-ref upload race.

Both actions pinned to full 40-char commit SHAs (verified
commit-type via `gh api`).

Dependabot stays unchanged; it now has a populated dep graph to
alert against (previously only saw the version catalog).

## Removed

- `.github/workflows/security-scan.yml`
- `config/owasp/` (suppressions.xml; `deny-packages` in
  dependency-review covers the same use case)
- `dependencyCheck { }` block in `app/build.gradle.kts`
- OWASP plugin alias in `app/build.gradle.kts` and root
  `build.gradle.kts`
- OWASP version and plugin entries in `gradle/libs.versions.toml`
- `NVD_API_KEY` secret (manual `gh secret delete` after merge)

## Verification

- `./gradlew :app:lintDebug :app:testDebugUnitTest spotlessCheck
  detekt koverXmlReport --continue` returns green locally.
- `./gradlew tasks --all | grep -i depcheck` returns nothing.
- After merge: Security tab → Dependency graph populates within
  ~10 min; `dependency-review` runs on the next PR.

## Open

- SBOM at release is still a separate decision. Synchronous SBOM
  API deprecated 2026-05-12 (removal 2026-11-13); any future SBOM
  workflow must use the async pattern.
- `fail-on-severity: moderate` may need calibration if grouped
  Dependabot PRs surface merge-fatigue.

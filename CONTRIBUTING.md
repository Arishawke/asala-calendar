# Contributing

Asala Calendar is a solo, hobby project. The notes below are for future-me
or anyone curious about how the code is structured.

## Prerequisites

- JDK 17 (Eclipse Temurin or equivalent)
- Android SDK with `platforms;android-36` and `build-tools;36.1.0`
- `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) exported in your shell

The Gradle wrapper handles Gradle itself; no separate install is needed.

## Build and run

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test

```
./gradlew testDebugUnitTest
```

Unit tests live in `app/src/test/`. They cover JVM-only logic
(date math, DTO mapping). Provider-level integration tests will move
into `app/src/androidTest/` when there is something CRUD to instrument.

Before pushing, run the same gate CI enforces, so formatting and
static-analysis failures surface locally instead of on the PR:

```
./gradlew spotlessApply
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
```

## Conventions

- **Commits.** Conventional Commits format
  (`type(scope): subject`). The template at `.gitmessage` is wired up via
  `git config commit.template .gitmessage`. Run that once per clone.
- **Formatting.** Governed by `.editorconfig`: four spaces in Kotlin, LF
  line endings, trim trailing whitespace, final newline.
- **Architecture.** Significant decisions are captured in
  [docs/adr/](docs/adr/) using the Michael Nygard template.
- **Packages.** `data/`, `ui/<screen>/`, `ui/components/`, `ui/theme/`,
  `ui/permissions/`, `ui/accessibility/`. Calendar Provider details do not
  leak into Composables; ViewModels do not import Compose.
- **License headers.** Every Kotlin source file starts with the short GPL
  v3 notice and `Copyright (C) <year> Arishawke`.

## Product principles

These hold across every feature, not just one task:

- **Privacy respecting.** No telemetry, no third-party trackers, no
  analytics SDKs. Calendar data never leaves the device unless the user
  explicitly configures CalDAV (or similar) to a server they choose.
- **Permission-on-demand.** Don't ask for a permission until the user
  takes the action that needs it. `POST_NOTIFICATIONS` only when setting
  a reminder; `WRITE_CALENDAR` is gated by the read flow.
- **Accessibility first.** WCAG 2.2 AA on UI surfaces; 48 dp touch
  targets; honor reduce-motion and system font scale.
- **Internationalization first.** Honor `Locale.getDefault()` for every
  date, time, number, and string format. Never hardcode English
  month/day names or US date order. Audit
  `DateTimeFormatter.ofPattern(...)` call sites for locale-sensitive
  output. Strings live in `res/values/strings.xml` so translations can
  be added without touching code.

## Signed commits and tags

Commits and tags are signed with an SSH key (`gpg.format = ssh`)
so GitHub shows the green Verified badge. The `Signed Commits`
ruleset on `main` enforces this, so unsigned commits cannot land.

Reusing your existing GitHub auth key as the signing key is the
simplest setup, and it is what this repo uses today. If you want
a separate dedicated signing key, generate one and substitute its
path below.

```
git config gpg.format ssh
git config user.signingkey ~/.ssh/id_ed25519.pub
git config commit.gpgsign true
git config tag.gpgsign true

# Trust your own key for `git log --show-signature` locally
echo "$(git config --get user.email) $(cat ~/.ssh/id_ed25519.pub)" \
  >> ~/.ssh/allowed_signers
git config gpg.ssh.allowedSignersFile ~/.ssh/allowed_signers
```

Then upload the public key to GitHub as a **signing** key (not
auth, even if it's the same key as your auth one) at
https://github.com/settings/ssh/signing/new. Paste the contents
of your `.pub` file and pick **Signing Key** in the type dropdown.
After that, GitHub verifies every commit you push.

Because `tag.gpgsign = true` is set, plain `git tag vX.Y.Z` will
error with "no tag message?". Always create annotated, signed
tags with an explicit message:

```
git tag vX.Y.Z -m "vX.Y.Z" <commit-sha>
```

Existing commits before signing was wired up (commit `f0f74a6`
and prior) remain unsigned. Rewriting history to sign them would
require a force-push and would invalidate the signed-APK
fingerprints attached to existing release tags; not worth it.

### Email privacy

If your GitHub account has "Block command line pushes that
expose my email" enabled, configure your noreply proxy as the
commit author:

```
git config --global user.email <id>+<username>@users.noreply.github.com
```

Find the proxy address at https://github.com/settings/emails or
compute it from your numeric user ID (visible in the URL when you
view your profile API at `api.github.com/users/<username>`).
Setting it globally is fine; setting per-repo also works.

## Branch workflow

The `main` ruleset requires **signed commits** and blocks
force-pushes and branch deletion. It does not require pull
requests, linear history, or passing CI, so you can land work
either way:

- **Direct push** for small, low-risk changes: commit (signing is
  automatic) and `git push origin main`.
- **Pull request** when you want a review checkpoint or to let CI
  finish first: branch (`feat/...`, `fix/...`, `docs/...`,
  `release/vX.Y.Z`), push, open it with `gh pr create`, then land
  it with GitHub's **Merge** or **Squash** button once you are
  happy.

CI (`build`, `secret-scan`, and the Gradle checks) runs on every
push to `main` and every PR, but it does not block. Run the gate
locally first and do not land red:

```
./gradlew spotlessApply
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
```

Two caveats from the signing rule:

- The GitHub web editor creates unsigned commits and is rejected.
- GitHub's **Rebase** button is rejected too, because GitHub cannot
  sign the rebased commits. If you want a linear history with no
  merge commit, fast-forward `main` locally instead:

  ```
  git checkout main
  git merge --ff-only <branch>
  git push origin main
  ```

  The PR auto-closes as merged. Delete the branch with
  `git push origin --delete <branch>` and `git branch -d <branch>`.

## Local checks (optional but recommended)

Pre-commit runs gitleaks against staged changes and a few hygiene
hooks (trailing whitespace, end-of-file newline, YAML validity, merge
conflict markers, private keys). CI re-runs gitleaks across the full
history regardless, so this is fast local feedback rather than the
trust boundary.

```
pip install --user pre-commit
pre-commit install
```

Dependencies and GitHub Actions are kept fresh by Dependabot
([.github/dependabot.yml](.github/dependabot.yml)), which opens
weekly grouped PRs.

## Releasing

Releases are published as signed APKs attached to a GitHub Release.

### One-time setup (per machine)

1. **Generate a release keystore.** The file lives outside the repo and
   is never committed:
   ```bash
   keytool -genkeypair -v \
     -keystore ~/asala-release.jks \
     -alias asala-release \
     -keyalg RSA -keysize 4096 \
     -validity 10000
   ```
   RSA 4096 matches Google Play App Signing's default. Validity is
   10000 days (~27 years), exceeding Google Play's 25-year requirement.
2. **Copy** `keystore.properties.example` to `keystore.properties` at
   the repo root and fill in the keystore path and passwords.
   `keystore.properties` is gitignored.

### Per release

Land the release commit on `main` the usual way (a release branch
plus PR is recommended so CI runs first; see "Branch workflow"
above). The tag is created separately, after `main` is updated.

1. On a release branch:
   ```bash
   git checkout -b release/vX.Y.Z
   ```
2. Move `[Unreleased]` entries in `CHANGELOG.md` under a new
   `[X.Y.Z] - YYYY-MM-DD` heading. Leave a fresh empty
   `[Unreleased]` block above it.
3. Bump `versionName` and increment `versionCode` in
   [app/build.gradle.kts](app/build.gradle.kts).
4. Commit and push the branch:
   ```bash
   git add CHANGELOG.md app/build.gradle.kts
   git commit -m "chore(release): X.Y.Z"
   git push -u origin release/vX.Y.Z
   gh pr create --base main --head release/vX.Y.Z \
     --title "chore(release): X.Y.Z" \
     --body "Release notes in the [X.Y.Z] CHANGELOG block."
   ```
5. Wait for the `build` and `secret-scan` checks. Once both
   pass, land it on `main`: click **Merge** or **Squash** on the
   PR, or fast-forward locally to keep the commit verbatim:
   ```bash
   git checkout main
   git merge --ff-only <release-branch-sha>
   git push origin main
   ```
6. Tag the release commit (must be annotated and signed because
   `tag.gpgsign = true`):
   ```bash
   git tag vX.Y.Z -m "vX.Y.Z" <release-commit-sha>
   git push origin vX.Y.Z
   ```
7. Build the signed APK and AAB:
   ```bash
   ./gradlew assembleRelease bundleRelease
   ```
   Outputs: `app/build/outputs/apk/release/app-release.apk` and
   `app/build/outputs/bundle/release/app-release.aab`. Deliver each
   under the versioned name `asala-calendar-vX.Y.Z.{apk,aab}`. The APK
   attaches to the GitHub release; the AAB is uploaded to Play separately.
8. Verify the cert fingerprint matches prior releases (so users
   get in-place upgrades, not "this app is signed differently"
   rejections):
   ```bash
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk \
     | grep -i sha-256
   ```
   Expected since v0.2.0:
   `a701b85f0f356ac30833303c1a13976cd112806a4b1c15afda01ba005302c68e`.
9. Create the GitHub release (a full release; the `--prerelease`
   policy was lifted at v0.18.0):
   ```bash
   gh release create vX.Y.Z \
     app/build/outputs/apk/release/app-release.apk \
     --notes-file <(awk '/^## \[X\.Y\.Z\]/{p=1; next} /^## \[/{p=0} p' CHANGELOG.md)
   ```
   The `awk` script prints the body of the `[X.Y.Z]` section
   without the version heading and stops at the next `## [`
   heading.
10. Delete the release branch:
    ```bash
    git branch -d release/vX.Y.Z
    git push origin --delete release/vX.Y.Z
    ```

SemVer: PATCH for fixes, MINOR for new features that do not break
existing behavior, MAJOR for breaking changes. Pre-1.0 (`0.x.y`),
MINOR is the typical bump even for breaking changes.

### Keystore security

The signing keystore is the single point of failure for this app's
identity on Android. Losing the keystore or its passwords means future
updates cannot be signed as the same app; users would have to
uninstall and reinstall to receive further updates from you. Treat it
with care.

**Do:**

- Store the `.jks` file off the working machine: encrypted external
  drive, password-manager file attachment, or encrypted cloud (Cryptomator,
  rclone-crypt). At least two locations.
- Store the passwords in a password manager. Long random passwords are
  fine; you only type them at release time.
- Use distinct values for `storePassword` and `keyPassword` if you
  want defense in depth. Same value is acceptable for a hobby project.
- For CI (when added later), inject signing values as GitHub Actions
  secrets (`SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`,
  `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`). The Gradle config
  already supports this fallback.

**Do not:**

- Commit `keystore.properties` or any `.jks` / `.keystore` file. Both
  are gitignored, but verify with `git status` before each push.
- Email the keystore to yourself or upload it unencrypted to cloud
  storage.
- Paste passwords into chat, issue trackers, or commit messages.

**If the keystore is leaked or you suspect it was:**

For GitHub Releases distribution, there is no central authority to
re-issue an upload key. You would have to publish a new app under a
different package name (or signing identity) and ask users to migrate
manually. Treat key leakage as a serious incident.

## License

GPL v3. See [LICENSE](LICENSE).

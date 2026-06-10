# 0007. Release signing stays local; CI scaffolds draft releases

Date: 2026-06-10

## Status

Accepted.

## Context

Asala is distributed as a signed APK on GitHub Releases (Obtainium auto-updates
users from there) and as an AAB on Play. The release path was fully manual and
had dropped assets before (v0.7.0 and v0.8.0 shipped a tag with no APK), and CI
never built the release variant, so R8 + resource-shrink breakage could reach a
tag unseen.

Closing that reliability gap means automating the release on a `v*` tag. The
open question was where signing happens, and there are two established answers:

1. **Keystore in CI.** Store the keystore base64-encoded and the passwords as
   encrypted GitHub Actions secrets; decode and sign in the workflow. This is
   the mainstream tutorial pattern and gives hands-off releases. The key now
   lives in the cloud; realistic exposure is a malicious or compromised
   third-party action exfiltrating the secret, or a compromised GitHub account.
   Well mitigated by pinning actions to commit SHAs and minimal `permissions`,
   but the residual risk is nonzero.
2. **Keystore stays local.** CI builds unsigned and scaffolds the release; the
   owner signs on their own machine and publishes. This matches the F-Droid
   reproducible-builds philosophy, where the developer holds the signing key and
   it never goes to a third party.

The decisive factor is that Asala is **already published**. A leaked signing key
cannot be rotated cleanly: every user would have to uninstall and reinstall to
keep receiving updates (the same constraint already documented for keystore
security). That raises the cost of a leak above the convenience that CI signing
buys for an infrequently-released solo app.

## Decision

Signing keys never enter CI. Release signing stays on the owner's machine.

`.github/workflows/release.yml`, triggered on `v*` tags, only scaffolds the
release:

- builds **unsigned** `:app:assembleRelease` + `:app:bundleRelease` (no keystore
  is present, so the Gradle config produces unsigned outputs);
- guards that the tag equals the app `versionName`, failing loud on a mismatch;
- slices the matching `CHANGELOG.md` section for the release body, failing loud
  if it is missing;
- opens a **draft** GitHub release with that body and no binaries attached, and
  uploads the unsigned APK/AAB as workflow artifacts.

The workflow uses only the built-in `GITHUB_TOKEN` scoped to `contents: write`,
and all actions are pinned to commit SHAs. The owner finishes each release
locally: build the signed artifacts, attach the signed APK to the draft, upload
the AAB to Play, and publish.

Separately, `assembleRelease` was added to the regular CI gate so the R8 +
resource-shrink build is smoke-tested on every push to `main`, not only at tag
time.

## Consequences

- The signing key's blast radius stays one machine. There is no cloud secret to
  leak, and nothing to rotate in GitHub.
- Releases are never silently asset-less. The draft is always created with the
  correct tag, title, and notes, the build is verified before the draft exists,
  and any failure hard-stops (version mismatch, missing CHANGELOG section, or
  missing artifacts).
- The asset-attach and publish step stays manual by design. CI cannot attach a
  signed binary without the key, so the human still signs and publishes. What is
  closed is "the release scaffold is wrong or missing," not "the human must
  still publish."
- Build-provenance attestation (`actions/attest-build-provenance`) is deferred:
  it would attest the unsigned CI bytes rather than the signed artifact users
  install, so it adds little under local signing. Revisit only if signing ever
  moves into CI.
- Trade-off accepted: less hands-off than full CI signing, and no provenance
  attestation on the shipped binary. Acceptable for a solo, offline-first,
  infrequently-released app where key custody matters more than release
  automation.
- Supersedes the manual `gh release create` step previously documented; the
  release process documentation is updated to the CI-drafts / owner-signs flow.

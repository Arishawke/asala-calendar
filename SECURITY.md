# Security policy

Asala Calendar is a personal-use Android app. It has no backend, no
network calls, and no telemetry. Calendar data stays on the device and is
accessed exclusively through Android's Calendar Provider.

## Supported versions

Only the latest commit on `main` is supported. Releases are tagged but
not branched.

## Verifying a release download

Release APKs are signed. Before installing a sideloaded APK, confirm it was
signed with the expected key:

```
apksigner verify --print-certs asala-calendar-vX.Y.Z.apk | grep -i sha-256
```

The SHA-256 certificate digest has been stable since v0.2.0 and must read:

```
a701b85f0f356ac30833303c1a13976cd112806a4b1c15afda01ba005302c68e
```

A different fingerprint means the APK was not signed by this project. Do not
install it.

## Reporting a vulnerability

Please report security issues privately through GitHub: open the
repository's **Security** tab and choose **Report a vulnerability**
(private vulnerability reporting). Include:

- A short description of the issue
- Steps to reproduce
- The build (commit SHA or release tag) it affects

Please do **not** open a public GitHub issue for security-sensitive
reports.

## Scope

In scope:

- The Asala app code in this repository
- Build configuration that ships in the APK (manifest, ProGuard, Gradle)

Out of scope:

- Vulnerabilities in the underlying Android Calendar Provider, sync
  adapters (CalDAV via DAVx5, corporate sync providers, or other
  third-party sync apps), or third-party calendar accounts. Report
  those upstream.
- Vulnerabilities in transitive dependencies (Compose, Material, Gradle).
  Report upstream; this project will pick up the fix on the next
  dependency bump.

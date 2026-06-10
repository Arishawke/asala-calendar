# Contributing

Asala Calendar is a solo, hobby project with no open contribution process.
It is GPLv3, so you are free to fork, build, and adapt it.

- **Build from source:** see the [README](README.md#build-from-source).
- **Code orientation:** [docs/CODE_TOUR.md](docs/CODE_TOUR.md).
- **Conventions:** Conventional Commits, four-space Kotlin (`.editorconfig`),
  signed commits on `main`, and an ADR in [docs/adr/](docs/adr/) for decisions
  worth remembering. Run the same gate CI enforces before pushing:

  ```
  ./gradlew spotlessApply
  ./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
  ```

- **Security:** report privately and verify a download per [SECURITY.md](SECURITY.md).

## License

GPL v3. See [LICENSE](LICENSE).

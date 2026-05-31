# Contributing

Thanks for helping improve **Create Radar: Mobile Radars**.

## Setup

1. Clone the repository.
2. Copy the JARs listed in [`lib/README.md`](lib/README.md) into `lib/`.
   - **Monorepo:** you can copy from `../lib/` and `../build/libs/create_radar-*.jar` (match names in `gradle.properties`).
3. **Java 21**
4. Build:

```bash
./gradlew build
```

Output: `build/libs/create_radar_mobile_radars-<version>.jar`

## Issues & pull requests

- **Bugs, features, compatibility:** use [GitHub Issues](https://github.com/Arsenalists-of-Create/Create-Radar/issues) with the templates under [`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/) when this folder is the project root, or pick **Mobile Radars** in the monorepo templates.
- **Security:** report via [GitHub Security Advisories](https://github.com/Arsenalists-of-Create/Create-Radar/security/advisories/new) on the [core Create Radars repository](https://github.com/Arsenalists-of-Create/Create-Radar) — not public issues.
- Keep PRs focused; fill in the [pull request template](.github/pull_request_template.md) with versions you tested.

## Code style

- Match naming and structure in the files you touch.
- Avoid drive-by refactors unrelated to your change.

## License

By contributing, you agree your contributions are licensed under the **MIT License** ([LICENSE](LICENSE)), unless you state otherwise clearly in the pull request.

# Local dependency JARs (not committed)

Gradle expects these files here so the add-on can compile against APIs that are not all published on Maven under stable coordinates.

| File | Purpose |
|------|---------|
| `create_radar-<version>.jar` | Must match `radar_compat_version` in `gradle.properties` (e.g. `create_radar-0.4.4-1.21.1.jar`). Use the same NeoForge build you ship against. |
| `createbigcannons-….jar` | Must match `cbc_jar_name` in `gradle.properties`. |

**Developing inside the main Create-Radar monorepo:** copy from `../lib/` and `../build/libs/create_radar-*.jar` into this folder (matching names in `gradle.properties`).

After copying, run from this folder:

```bash
./gradlew build
```

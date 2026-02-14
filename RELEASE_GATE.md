# Pathmind Release Gate

This gate defines the minimum bar for promoting a build from beta to full release.

## 1. Automated Gate (must pass)

- `./gradlew test`
- `./gradlew buildAllTargets`
- CI workflow `Build` is green on `main`.
- CI verifies one non-sources jar exists per configured Minecraft target.

## 2. Manual Smoke Gate (must pass)

Run this on at least:
- Oldest supported target (`1.21`)
- Latest supported target (`1.21.11`)

Checklist:
- Launch client with Fabric Loader + Fabric API + Pathmind.
- Open the visual editor via keybind.
- Create a small graph: Start -> Action -> End.
- Save graph, close world, reload world, confirm graph persists.
- Execute the graph and verify expected in-game behavior.
- Confirm no crashes or hard UI rendering issues.

## 3. Release Hygiene (must pass)

- `README.md` version info matches current release intent.
- Mod version in `gradle.properties` is finalized (no beta suffixes).
- Release notes include:
  - User-visible changes
  - Known limitations
  - Supported Minecraft versions
- Artifact naming follows `pathmind-<modVersion>+mc<mcVersion>.jar`.

## 4. Optional But Recommended Before Major Release

- Run at least one multiplayer/server-join smoke test (client-side compatibility sanity check).
- Run with and without optional Baritone runtime dependency.
- Spot-check non-English language keys for major UI paths.

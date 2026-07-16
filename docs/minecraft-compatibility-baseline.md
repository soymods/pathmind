# Minecraft Compatibility Baseline

This document records the compatibility state protected by Pass 1 of the [Minecraft multiversion roadmap](minecraft-multiversion-roadmap.md). It is the regression baseline for the mapping, build-generation, adapter, and Minecraft 26.x work that follows.

## Source of truth

[`gradle/minecraft-versions.properties`](../gradle/minecraft-versions.properties) is the machine-readable compatibility manifest. It owns:

- the ordered supported-version list and default development version;
- compatibility and source-family selection;
- target Java version and packaging generation;
- release loaders;
- canonical Mojang mapping artifacts plus Fabric Loader, Fabric API, and NeoForge build pins;
- minimum Fabric Loader metadata;
- range-named Fabric use-item and render capability selection;
- NeoForge UI source selection;
- optional Baritone runtime availability.

The generation-specific mechanics and stable contributor commands are documented in [Pathmind Build Generations](build-generations.md).

Use these tasks when inspecting or changing compatibility:

```bash
./gradlew compatibilityReport
./gradlew printSupportedMinecraftVersions -q
./gradlew verifyCompatibilityManifest
```

`verifyCompatibilityManifest` rejects missing or unknown manifest fields, invalid family values, stale runtime support, stale README targets, hard-coded metadata versions, duplicated Gradle defaults, and CI discovery that no longer reads the manifest. The root `check` lifecycle task includes this verification.

Gradle's Java toolchain is always set from the selected manifest row. The pre-26 projects use Java 21 even when Gradle was started by a newer host JVM, while the isolated 26.x build uses Java 25. This prevents either loader generation from inheriting an incompatible class-file level.

## Protected target matrix

The `1.21.x` baseline targets use Java 21 and `pre26-remapped`; `26.1` and `26.2` use Java 25 and `mc26-unobfuscated`. Fabric and NeoForge are release loaders for every row.

| Minecraft | Compatibility family | Fabric API | NeoForge |
| --- | --- | --- | --- |
| `1.21` | `mc-1.21.0-1.21.8` | `0.102.0+1.21` | `21.0.166` |
| `1.21.1` | `mc-1.21.0-1.21.8` | `0.116.7+1.21.1` | `21.1.230` |
| `1.21.2` | `mc-1.21.0-1.21.8` | `0.106.1+1.21.2` | `21.2.1-beta` |
| `1.21.3` | `mc-1.21.0-1.21.8` | `0.114.1+1.21.3` | `21.3.96` |
| `1.21.4` | `mc-1.21.0-1.21.8` | `0.119.4+1.21.4` | `21.4.157` |
| `1.21.5` | `mc-1.21.0-1.21.8` | `0.128.2+1.21.5` | `21.5.97` |
| `1.21.6` | `mc-1.21.0-1.21.8` | `0.128.2+1.21.6` | `21.6.20-beta` |
| `1.21.7` | `mc-1.21.0-1.21.8` | `0.129.0+1.21.7` | `21.7.25-beta` |
| `1.21.8` | `mc-1.21.0-1.21.8` | `0.133.4+1.21.8` | `21.8.53` |
| `1.21.9` | `mc-1.21.9-1.21.10` | `0.134.1+1.21.9` | `21.9.16-beta` |
| `1.21.10` | `mc-1.21.9-1.21.10` | `0.138.4+1.21.10` | `21.10.64` |
| `1.21.11` | `mc-1.21.11` | `0.140.2+1.21.11` | `21.11.42` |
| `26.1` | `mc-26.1` | `0.145.1+26.1` | `26.1.0.19-beta` |
| `26.2` | `mc-26.2` | `0.154.2+26.2` | `26.2.0.15-beta` |

The `1.21.x` Fabric build pin is `0.17.3`, with release metadata accepting `0.17.2` or newer. Minecraft `26.1` and `26.2` build and require Fabric Loader `0.19.3`. These values remain explicit in the manifest so packaging cannot silently change.

## Source-family baseline

The manifest preserves the following pre-migration source selection exactly:

| Minecraft | Common/Fabric base | Fabric use-item | Fabric world render | NeoForge UI |
| --- | --- | --- | --- | --- |
| `1.21`–`1.21.1` | `mc-1.21.0-1.21.8` | `mc-1.21.0-1.21.1` | `mc-1.21.0-1.21.1` | `mc-1.21.0-1.21.10` |
| `1.21.2`–`1.21.3` | `mc-1.21.0-1.21.8` | `mc-1.21.2-1.21.8` | `mc-1.21.2-1.21.3` | `mc-1.21.0-1.21.10` |
| `1.21.4` | `mc-1.21.0-1.21.8` | `mc-1.21.2-1.21.8` | `mc-1.21.4` | `mc-1.21.0-1.21.10` |
| `1.21.5` | `mc-1.21.0-1.21.8` | `mc-1.21.2-1.21.8` | `mc-1.21.5` | `mc-1.21.0-1.21.10` |
| `1.21.6`–`1.21.8` | `mc-1.21.0-1.21.8` | `mc-1.21.2-1.21.8` | `mc-1.21.6-1.21.8` | `mc-1.21.0-1.21.10` |
| `1.21.9`–`1.21.10` | `mc-1.21.9-1.21.10` | built into family | built into family | `mc-1.21.0-1.21.10` |
| `1.21.11` | `mc-1.21.11` | built into family | built into family | `mc-1.21.11` |
| `26.1`–`26.2` | `mc-26.1-26.2` | built into family | built into family | `mc-26.1-26.2` |

Baritone can be attached as a local development runtime only for `1.21.6` through `1.21.8`. The API remains compile-optional for other versions when a local API jar is present.

These names are contracts, not relative labels. A future target must declare a new explicit range or deliberately reuse one whose API contract is unchanged.

## Artifact and metadata contract

Release artifacts use these exact names:

```text
pathmind-fabric-<modVersion>+mc<minecraftVersion>.jar
pathmind-neoforge-<modVersion>+mc<minecraftVersion>.jar
```

For mod version `1.1.5` and Minecraft `1.21.11`, the baseline names are:

```text
pathmind-fabric-1.1.5+mc1.21.11.jar
pathmind-neoforge-1.1.5+mc1.21.11.jar
```

The public jar must not have `-dev`, `-dev-shadow`, `-sources`, `-all`, or `-javadoc` in its name. Development and sources jars may exist beside it but are excluded from staging.

Fabric metadata is client-only and declares exact Minecraft compatibility plus the row's Java, Fabric Loader, and Fabric API requirements. NeoForge metadata is client-only and declares the exact Minecraft version plus the target's minimum NeoForge version.

## Source duplication inventory

The historical Pass 1 inventory found:

- 239 files under `common/src/main`;
- 12 files in each common `legacy`, `mid`, and `modern` compatibility family;
- 17 Fabric legacy-base files, 7 additional legacy use-item/render implementations, and 19 files in each Fabric `mid` and `modern` family;
- 5 main and 2 compatibility files in the NeoForge module;
- no active Java or resource sources in the root `src` tree; it currently contains only Finder metadata and is not a build input.

Every one of the 36 common compatibility files has a matching Fabric path. Of those pairs, 27 are byte-identical and 9 differ. The differing paths are the same three product files in every family:

- `PathmindMarketplaceScreen.java`;
- `PathmindSettingsPopupController.java`;
- `PathmindVisualEditorScreen.java`.

Pass 4 removed the Fabric product mirror, promoted family-independent marketplace behavior to `common/src/main`, and consolidated the preview loader, preset popup, and graph preview renderer shared by `1.21` through `1.21.10`. The historical counts above remain useful for measuring the migration, but they no longer describe the active layout.

The Fabric-only compatibility files cover loader events, key bindings, chat hooks, use-item callbacks, main-menu integration, and version-specific world-overlay rendering. NeoForge has loader-specific bootstrap/event logic plus its legacy/modern main-menu button implementation. These are expected loader or Minecraft API boundaries, although Pass 4 will narrow their contracts.

## Runtime smoke-test contract

Use a disposable development game directory or backed-up test world. Test with no unrelated mods beyond the target's required Fabric/NeoForge dependencies; run optional integrations separately.

For each representative target and loader:

1. Launch the packaged jar to the title screen with no Mixin or dependency errors.
2. Open Pathmind from its key binding and from main-menu integration.
3. Close and reopen the visual editor without losing the current preset.
4. Enter a test world and load a saved preset created by the baseline release.
5. Execute a minimal graph, observe active-node/HUD feedback, and stop it cleanly.
6. Exercise a navigator target and confirm its HUD/world overlay renders correctly.
7. Exercise one inventory or GUI action and confirm the expected slot/screen transition.
8. Open the marketplace, settings, and routine UI and check text input, scrolling, tooltips, item icons, and popups.
9. Exit the world and return to the title screen without a crash.
10. Inspect `latest.log` for Mixin failures, linkage errors, missing methods/fields, render exceptions, and dependency warnings.

Compilation covers every target. Interactive smoke testing is a release-validation activity, not a normal pull-request requirement. Choose the smallest representative set that covers the compatibility family changed by a patch; do not launch every version when unit, configuration, and full-matrix build checks already cover the change.

During the initial Pass 1 audit on July 16, 2026, focused development launches reached Pathmind initialization and resource loading on Fabric and NeoForge for `1.21`, `1.21.8`, and `1.21.11`. This audit exposed and fixed development clients inheriting Java 25 instead of the manifest's Java 21 toolchain. These launches establish loader/Mixin startup evidence only; they do not replace the interactive checklist below.

Record manual runs in this table when a compatibility-changing pass is prepared for release:

| Target | Loader | Packaged launch | Editor/main menu | World execution | HUD/render | Inventory/GUI | Log clean | Tester/date |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `1.21` | Fabric | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21` | NeoForge | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21.8` | Fabric | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21.8` | NeoForge | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21.9` | Fabric | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21.9` | NeoForge | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21.11` | Fabric | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `1.21.11` | NeoForge | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `26.1` | Fabric | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `26.1` | NeoForge | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `26.2` | Fabric | Pending | Pending | Pending | Pending | Pending | Pending | — |
| `26.2` | NeoForge | Pending | Pending | Pending | Pending | Pending | Pending | — |

## Automated verification commands

During normal development:

```bash
./gradlew check -Pmc_version=1.21.11
./gradlew buildSelectedTarget -Pmc_version=1.21.11
```

Before merging compatibility, mapping, build, source-set, metadata, or release changes:

```bash
./gradlew buildAllTargets -q
```

The full command builds and validates 28 public jars: one Fabric and one NeoForge artifact for each of the 14 supported Minecraft targets.

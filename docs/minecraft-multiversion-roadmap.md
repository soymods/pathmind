# Minecraft 1.21.x and 26.x Compatibility Roadmap

## Purpose

This roadmap describes the transition from Pathmind's current `1.21` through `1.21.11` build matrix to a maintainable architecture that supports the complete `1.21.x` line alongside Minecraft `26.1`, `26.2`, and future releases.

The migration must preserve existing user-facing behavior and existing `1.21.x` artifacts. It is not a rewrite of Pathmind. It is a controlled replacement of the mapping, build, compatibility, and release foundations beneath the product.

The intended end state is:

- one canonical Pathmind implementation;
- Mojang names throughout shared and loader code;
- explicit build behavior for remapped pre-26 targets and unobfuscated 26+ targets;
- small, capability-oriented compatibility adapters instead of complete source copies;
- thin Fabric and NeoForge entrypoint layers;
- a single version catalog that drives builds, metadata, documentation, and CI;
- fast contributor checks plus a complete release verification matrix;
- no loss of support for Minecraft `1.21` through `1.21.11`.

## Non-negotiable compatibility contract

The following rules apply to every pass:

1. A pass is not complete while an existing supported target that built before the pass no longer builds, unless the failure is recorded as a temporary checkpoint inside an isolated migration branch and is repaired before the pass is merged.
2. Existing graph, routine, preset, settings, and marketplace data formats remain compatible. Minecraft support work must not silently change persisted Pathmind behavior.
3. Fabric and NeoForge remain first-class release platforms.
4. Version-specific behavior must be explicit. An unknown Minecraft version must fail configuration instead of falling through to a source set named `modern`.
5. Large editor, marketplace, navigator, or execution implementations must not be copied to add a version. Version differences belong in narrow adapters or deliberately small source-set overrides.
6. Reflection is allowed only inside a documented compatibility adapter when a typed implementation would cause worse duplication. Normal product code must not accumulate reflective version checks.
7. Every pass ends with documentation and verification that lets a new contributor understand the new state without reading the migration history.

## Target architecture

The exact Gradle project names can change during implementation, but the dependency direction should settle around these layers:

```text
Pathmind domain/core
    Graphs, routines, persistence, validation, marketplace models
                         |
                         v
Shared Minecraft integration (Mojang names)
    Execution, inventory/world access, screens, common UI behavior
                         |
              +----------+----------+
              |                     |
              v                     v
Minecraft compatibility       Loader integration
    1.21 API families              Fabric
    26.1                            NeoForge
    26.2
              |                     |
              +----------+----------+
                         v
                    Release jars
```

Compatibility has two independent dimensions and they must not be multiplied into complete implementations:

- **Minecraft API family:** screens, input, HUD, rendering, recipes, inventory, and renamed/moved vanilla APIs.
- **Loader:** Fabric or NeoForge lifecycle, events, key registration, metadata, and packaging.

The shared code calls stable Pathmind-owned contracts. A Minecraft-family adapter implements unstable vanilla behavior, while a loader adapter implements loader behavior. This avoids creating combinations such as a complete `fabric-26.1` editor and a separate complete `neoforge-26.1` editor.

The desired source organization is approximately:

```text
core/                         Minecraft-independent product logic
common/                       Shared Mojang-named Minecraft integration
compat/
    mc-1.21.0-1.21.8/
    mc-1.21.9-1.21.10/
    mc-1.21.11/
    mc-26.1-26.2/            # shared API contract where 26.x agrees
    api/mc-26.2/             # narrow release-specific deltas only
fabric/                       Fabric-only bootstrap and event wiring
neoforge/                     NeoForge-only bootstrap and event wiring
build-logic/                  Version catalog and build-generation conventions
```

Version-family directories should contain only files that genuinely differ. Shared implementations should live in `common`, even when they use Minecraft APIs.

## Pass 1: Freeze the compatibility contract and establish a trustworthy baseline

**Status:** Implemented July 16, 2026. The manifest, drift checks, source/artifact inventory, 24-jar verification, Java-toolchain enforcement, and focused loader launch checks are in place. Interactive editor/world checks remain part of release validation rather than routine migration work.

### Goal

Create a repeatable baseline for all current targets before changing mappings or build infrastructure. At the end of this pass, later failures can be identified as real migration regressions rather than pre-existing uncertainty.

### Work

- Inventory every current target, dependency pin, selected source set, output artifact, Java requirement, metadata range, and CI job.
- Record which compatibility files are duplicated, byte-identical, intentionally divergent, or stale across `common/src`, `src`, and `fabric/src`.
- Add a machine-readable compatibility manifest as the initial source of truth for:
  - Minecraft version;
  - compatibility family;
  - Java version;
  - Fabric Loader and Fabric API versions;
  - NeoForge version or an explicit unsupported marker;
  - packaging generation (`remapped` or `unobfuscated`).
- Make the existing build tasks consume or validate against that manifest without changing the artifact format yet.
- Add checks for version-list drift between Gradle, `VersionSupport`, Fabric metadata, NeoForge metadata, README claims, CI, and release publishing.
- Establish representative runtime smoke-test scenarios:
  - launch to title screen;
  - open and close the visual editor;
  - enter a world;
  - load a saved preset;
  - execute a minimal graph;
  - render HUD and navigator overlays;
  - exercise an inventory or screen action;
  - stop execution cleanly.
- Capture the current expected jar names and metadata for regression comparison.
- Document known intentional behavioral differences between version families and loaders.

### Exit criteria

- `1.21` through `1.21.11` build from the declared matrix on every loader currently claimed as supported.
- Unit tests pass on the default target.
- The smoke-test contract is documented and focused loader launch checks cover the oldest, late-legacy, and newest baseline boundaries; full interactive checks are required before a compatibility release.
- CI and release discovery use or validate the same machine-readable target list.
- A contributor can identify the selected compatibility family and toolchain for any target from one manifest.

## Pass 2: Standardize the entire codebase on Mojang names

**Status:** Implemented July 16, 2026. Authored common and loader sources use Mojang names, older targets consume generated canonical mapping jars, and the custom common-to-NeoForge remapper is gone. Default-target and oldest-target compilation have been confirmed; final full-matrix verification remains pending.

### Goal

Remove the mapping-language divide between Fabric, shared code, and NeoForge. All authored Java and Mixin sources should use Mojang names, including sources that still produce remapped `1.21.x` Fabric jars.

This is the foundational source migration required before adding unobfuscated 26+ targets.

### Work

- Migrate the `1.21.11` shared, Fabric, and compatibility sources from Yarn to Mojang mappings using the supported migration tooling.
- Review every automated rename, with special attention to:
  - Mixins and injection descriptors;
  - access wideners or class tweakers;
  - reflection strings;
  - serialized class-name assumptions;
  - screen, input, inventory, recipe, entity, and rendering APIs.
- Convert the older `1.21.x` compatibility families to the same authored naming scheme.
- Configure old Fabric targets to develop against Mojang mappings while still producing the required intermediary/remapped release artifacts.
- Remove the custom shared Yarn-to-Mojang production-jar conversion once both loader pipelines consume the same canonical names.
- Rename compatibility helpers whose names or comments assume Yarn terminology.
- Ensure generated sources, IDE runs, sources jars, and stack traces remain useful to contributors.
- Update contributor documentation so examples and debugging instructions consistently use Mojang names.

### Structural rule introduced by this pass

No new Yarn-named production source may be added. Yarn may remain an implementation detail of a legacy packaging tool only if the build requires it; it must not determine names in authored Pathmind code.

### Exit criteria

- All supported `1.21.x` Fabric and NeoForge jars build from Mojang-named source.
- The custom common-to-NeoForge mapping task is deleted or reduced to a documented transitional no-op scheduled for removal in the same pass.
- All Mixins apply during representative client launches.
- Sources jars and development runs work for both loaders.
- Full `1.21.x` build output and runtime smoke behavior match the Pass 1 baseline.

## Pass 3: Split build generations and centralize version selection

**Status:** Implemented July 16, 2026; user-run verification pending. Build-generation contracts, stable root tasks, generation-aware packaging, Java selection, CI discovery/cache separation, unknown-target checks, and a non-release 26.x Java 25 placeholder are in place.

### Goal

Teach the repository that `1.21.x` and `26.x` share product source but have different toolchain and packaging rules.

### Work

- Introduce explicit build-generation conventions:
  - `pre26-remapped`: Java 21, mappings-aware legacy packaging, and remapped Fabric output;
  - `mc26-unobfuscated`: Java 25, official unobfuscated names, and normal jar packaging where required by the current loaders.
- Upgrade Gradle, Loom/Architectury tooling, Fabric Loader, and related plugins to versions that can support the complete matrix. If one plugin generation cannot safely configure both eras in one Gradle invocation, isolate the generations in included builds behind stable root tasks.
- Keep contributor-facing commands stable and predictable, including:
  - `buildMc<version>` for one Minecraft target on all supported loaders;
  - loader-specific build and run tasks;
  - `buildAllTargets` for the complete release matrix.
- Make task selection depend on the compatibility manifest rather than string comparisons spread across module scripts.
- Select Java toolchains per target without requiring contributors to manually change their shell Java between builds.
- Generate processed mod metadata from the manifest and validate exact Minecraft, Java, loader, and Architectury constraints.
- Make output collection understand both `remapJar`-style and normal `jar`-style artifacts.
- Update CI caching so the two toolchain generations cannot contaminate one another.
- Add configuration tests that assert unknown targets fail with a useful message.

### Exit criteria

- The build can configure a placeholder `26.x` target far enough to select Java 25 and the unobfuscated packaging path without pretending Yarn mappings exist.
- Every `1.21.x` target still builds with unchanged public artifact naming.
- Version-specific dependency and task decisions originate in the manifest and build conventions, not ad hoc version sets in each module.
- Root build and run commands hide included-build or plugin-generation complexity from contributors.

## Pass 4: Replace mirrored implementations with compatibility contracts

**Status:** In progress July 16, 2026. Ambiguous source-family names and the complete Fabric editor/marketplace mirror have been removed. Family-independent marketplace behavior and the shared `1.21.0`–`1.21.10` preview/preset implementations are canonicalized, capability ranges are explicit, and structural verification prevents those mirrors from returning. Canonicalizing the remaining common screen shells is the next Pass 4 stage.

### Goal

Make adding a Minecraft version proportional to its actual API differences. Retire the current practice of mirroring complete editor and marketplace implementations across `legacy`, `mid`, and `modern` trees.

### Work

- Rename ambiguous compatibility families so their supported ranges are visible. Do not retain `modern` as an open-ended fallback.
- Compare the large screen copies and extract their common behavior into one implementation.
- Introduce small, typed Pathmind-owned compatibility contracts for unstable areas, likely including:
  - current-screen lookup and screen transitions;
  - screen input dispatch;
  - key state and key registration;
  - HUD registration and render context access;
  - GUI textures, item drawing, text, scissor, matrix, and tooltip operations;
  - world-overlay render state and depth behavior;
  - camera and window access;
  - inventory, menu, slot, recipe, registry, identifier, entity, and player operations;
  - loader lifecycle/event hooks where Architectury does not provide a suitable common contract.
- Move reflection that remains necessary into the relevant adapter and cover its failure mode explicitly.
- Split large Mixins by Minecraft family only when their targets or descriptors genuinely differ.
- Delete byte-identical and mechanically mirrored copies after their source sets consume the canonical implementation.
- Add focused adapter contract tests where behavior can be tested without a running game.
- Update `node-architecture.md` and `marketplace-ui.md` to remove instructions that require contributors to mirror files.

### Design test

Adding a synthetic compatibility family should require registering a family and supplying only its divergent adapters. It must not require copying `PathmindVisualEditorScreen`, `PathmindMarketplaceScreen`, `Node`, or an execution engine.

### Exit criteria

- The visual editor and marketplace each have one canonical behavioral implementation.
- Compatibility directories contain narrow divergent classes rather than complete product subsystems.
- Existing `1.21.x` targets pass the full build matrix and representative smoke tests.
- Documentation tells contributors exactly whether a change belongs in core, common Minecraft code, a Minecraft-family adapter, or a loader adapter.

## Pass 5: Implement Minecraft 26.1 across Fabric and NeoForge

**Status:** Build implementation complete July 16, 2026. The verified `26.1` dependency pins, Java 25 manifest row, isolated unobfuscated builds, normal-jar packaging, metadata, shared source families, and root task delegation are implemented. Fabric and NeoForge production jars compile successfully, including the extraction-rendering and data-driven villager-trade boundaries. Interactive runtime smoke checks remain release-validation work.

### Goal

Add production-quality `26.1` support on both loaders using the new unobfuscated build generation and compatibility architecture.

### Work

- Add stable `26.1` dependency pins and metadata to the compatibility manifest.
- Complete Java 25 compilation and development launch support.
- Port vanilla API changes from `1.21.11` to `26.1`, including mapping-independent signature and behavior changes.
- Port Fabric API names and removed APIs, including replacing `HudRenderCallback` with the supported HUD registration mechanism.
- Update Fabric entrypoints, events, input handling, screen hooks, Mixins, and packaging for the unobfuscated toolchain.
- Update NeoForge lifecycle, event wiring, key mappings, screen hooks, Mixins, metadata, and packaging.
- Audit all reflection strings after removal of obfuscation and replace reflection with typed calls where practical.
- Validate execution-sensitive systems:
  - movement and key simulation;
  - inventory/menu interaction;
  - block, item, entity, and recipe lookup;
  - world navigation and overlays;
  - GUI automation and stored-screen restoration;
  - title-screen integration and editor pause behavior.
- Verify persisted graphs and presets created on `1.21.x` load and behave correctly on `26.1`, subject to normal Minecraft world compatibility constraints.

### Exit criteria

- Fabric and NeoForge `26.1` clients launch from development tasks and packaged jars.
- The complete smoke-test checklist passes on both loaders.
- No `26.1` implementation is a copied product subsystem.
- All `1.21.x` targets continue to build and representative boundary targets continue to launch.
- `26.1` artifacts contain correct Java, Minecraft, loader, and Architectury metadata.

## Pass 6: Implement Minecraft 26.2 and harden the rendering boundary

**Status:** In progress July 16, 2026. The verified `26.2` Fabric/NeoForge pins, Gradle 9.5.1 and Loom 1.17 toolchain, manifest/build routing, shared `mc-26.1-26.2` families, version-scoped GUI ownership transform, and structural raw-OpenGL guard are implemented. Fabric compilation is the next checkpoint before porting any additional concrete API deltas.

### Goal

Add `26.2` on both loaders while treating its GUI/HUD reorganization and render-backend work as the model for future rendering compatibility.

### Work

- Add stable Fabric, Architectury, and NeoForge dependency pins as releases become suitable for production.
- Implement `26.2` screen and HUD access changes through the compatibility contracts rather than scattering `26.2` checks through product code.
- Port GUI, registry, input, and rendering API changes from `26.1` to `26.2`.
- Audit Pathmind for direct OpenGL/LWJGL calls and backend assumptions.
- Replace raw render-state manipulation with supported Blaze3D/render-pipeline abstractions wherever Pathmind can execute on `26.2`.
- Isolate any unavoidable OpenGL-only fallback so it is disabled or replaced under the Vulkan backend.
- Exercise editor widgets, marketplace media, graph previews, item icons, tooltips, navigator world overlays, HUD overlays, scissoring, and matrix transformations.
- Test both OpenGL and the available Vulkan path where the game and CI environment permit it; otherwise document a repeatable manual Vulkan test.
- Confirm that `26.1` remains a separate explicit family only where behavior differs; shared 26.x adapters should be factored upward.

### Exit criteria

- Packaged Fabric and NeoForge `26.2` jars launch and pass the smoke-test checklist.
- Pathmind has no untracked raw-OpenGL dependency in a code path expected to work with Vulkan.
- `26.1` and `26.2` share implementations wherever the APIs are actually compatible.
- The complete `1.21.x`, `26.1`, and `26.2` compilation matrix passes.

## Pass 7: Make multi-version verification and releases routine

### Goal

Turn the completed port into an ordinary, contributor-friendly maintenance system rather than a one-time migration that decays.

### Work

- Define two CI tiers:
  - fast pull-request checks on representative API boundaries and both loaders;
  - full release/nightly checks on every supported Minecraft/loader combination.
- Choose representative targets based on compatibility families rather than arbitrary newest versions.
- Add artifact assertions for filenames, duplicate classes, metadata ranges, embedded dependencies, Mixin configs, and accidental dev classifiers.
- Add a compatibility-report task that prints every target, family, Java toolchain, loader availability, and dependency pin.
- Generate or validate README support badges, supported-version tables, release descriptions, and marketplace metadata from the manifest.
- Update publishing so a failed target cannot produce a partial release presented as a complete matrix.
- Add a documented new-version playbook:
  1. add the target to the manifest;
  2. select or create a compatibility family;
  3. update dependency pins;
  4. compile to discover API changes;
  5. implement narrow adapters;
  6. run representative smoke tests;
  7. enable full release verification.
- Document deprecation policy separately from implementation. Old versions are removed only through an explicit project decision, never because the build silently stops exercising them.
- Remove transitional scripts, stale source trees, obsolete bridge methods, and migration-only comments.

### Exit criteria

- A contributor can build and run one target without understanding both build generations.
- A maintainer can add a compatible patch target mostly by editing the manifest.
- A future breaking Minecraft release leads to a new adapter family, not another copy of Pathmind.
- The full release job verifies all supported artifacts before publishing any of them.
- README and architecture documentation describe the final structure rather than the migration process.

## Recommended verification matrix

Fast pull-request checks should cover at least one target per meaningful boundary and both loaders where supported. The exact selection belongs in the manifest, but the initial set should include:

| Target | Reason |
| --- | --- |
| `1.21` | Oldest supported target and earliest API family |
| `1.21.8` | Upper edge of the early `1.21.x` family |
| `1.21.9` or `1.21.10` | Mid-family input and rendering boundary |
| `1.21.11` | Final remapped-era target and migration baseline |
| `26.1` | First Java 25/unobfuscated target |
| `26.2` | New GUI/rendering boundary and current 26.x target |

Release verification must build every explicitly supported version, not only these representatives.

## Definition of done for the roadmap

The roadmap is complete when:

- Minecraft `1.21` through `1.21.11`, `26.1`, and `26.2` are built from one repository and one canonical product implementation;
- Fabric and NeoForge artifacts are published for every supported combination declared in the compatibility manifest;
- all authored Minecraft-facing code uses Mojang names;
- remapped and unobfuscated packaging are explicit build-generation choices;
- complete editor and marketplace sources are no longer mirrored across compatibility families;
- version differences are implemented through small typed adapters with documented ownership;
- CI prevents target, metadata, documentation, or artifact drift;
- a new contributor can locate the correct layer for a change from the architecture docs alone;
- adding the next Minecraft release is a bounded compatibility exercise rather than a repository-wide duplication effort.

## Pass dependency order

The passes are intentionally large and ordered:

```text
1. Baseline and manifest
          |
2. Mojang-name migration
          |
3. Build-generation split
          |
4. Compatibility extraction
          |
5. Minecraft 26.1
          |
6. Minecraft 26.2/render hardening
          |
7. CI, publishing, and contributor finish
```

Some investigation may overlap, but implementation should not skip the dependency order. In particular, porting `26.1` before standardizing names and build generations would mix mapping migration, toolchain migration, and game API changes into one difficult-to-review patch. Likewise, adding `26.2` before extracting rendering and screen contracts would preserve the current copy-heavy structure instead of fixing it.

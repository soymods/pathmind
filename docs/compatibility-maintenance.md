# Compatibility Maintenance and Releases

This is the operational guide for maintaining Pathmind's Minecraft matrix after the multi-version migration. The compatibility manifest is the only target inventory; Gradle, CI, documentation checks, and release staging consume it.

## Daily contributor workflow

Build the target and loader affected by the change:

```bash
./gradlew buildSelectedFabric -Pmc_version=26.2
./gradlew buildSelectedNeoForge -Pmc_version=26.2
```

Use `buildSelectedTarget` when both loaders are relevant. Add `verifySelectedCompatibilityArtifacts` to check filenames, exact Minecraft/Java/loader metadata, Mixin configurations, duplicate archive entries, embedded jars, and loader cross-contamination:

```bash
./gradlew buildSelectedTarget verifySelectedCompatibilityArtifacts -Pmc_version=26.2
```

`./gradlew compatibilityReport` prints every target, whether it participates in fast CI, all selected source families, Java, build generation, release loaders, and dependency pins.

## Verification tiers

The manifest's `fast_verification_versions` list owns the representative matrix. It covers the oldest and upper early-1.21 boundaries, the later 1.21 family, the final remapped target, the first unobfuscated target, and the current GUI/render boundary. Pull requests and non-default branch pushes build this tier on both loaders.

Default-branch pushes, scheduled nightly runs, and manually dispatched workflows build every target and loader. Publishing depends on the complete matrix job, so one failed target prevents a partial release from being presented as complete.

Use these local equivalents only when their scope is justified:

```bash
# Cheap structural/configuration verification
./gradlew verifyCompatibilityManifest verifyCompatibilityStructure verifyBuildGenerationRouting

# One production target plus archive contract
./gradlew buildSelectedTarget verifySelectedCompatibilityArtifacts -Pmc_version=26.2

# Complete release matrix and all archive contracts
./gradlew buildAllTargets
```

## Adding a Minecraft target

1. Verify the Minecraft, Fabric Loader, Fabric API, NeoForge, Java, Gradle, and loader-plugin requirements from primary release sources.
2. Add the version to ordered `supported_versions` and add every required `version.<target>.*` manifest property.
3. Reuse an existing source family only when its API contract truly compiles and behaves the same. Otherwise create the narrowest explicitly named family or capability adapter.
4. Add the target to `fast_verification_versions` only when it introduces or closes a meaningful compatibility boundary. Full CI discovers every supported target automatically.
5. Select `pre26-remapped` or `mc26-unobfuscated`; never infer generation from a version comparison in product code.
6. Compile Fabric first, implement narrow vanilla/Fabric deltas, then compile NeoForge and implement loader-specific deltas.
7. Run structural verification and the selected artifact verifier.
8. Perform the relevant packaged-client smoke checks. For a rendering-boundary release, test both graphics backends.
9. Update compatibility documentation. The manifest verifier rejects a stale runtime list or README target list.

Do not copy the editor, marketplace, node catalog, execution engine, or loader-independent behavior to add a target. A future breaking release should add an adapter family, not another Pathmind implementation.

## Minecraft 26.2 graphics smoke test

Run this checklist once with OpenGL and once with Vulkan on each release loader where the backend is available:

1. Launch the packaged jar and select the backend in Minecraft's graphics options; restart if requested.
2. Reach the title screen and open Pathmind through main-menu integration.
3. Open the visual editor and exercise text fields, scrolling, selection, tooltips, item icons, popups, and scissored panels.
4. Open the marketplace and inspect avatars/media, graph previews, and popup layering.
5. Enter a world, execute a small graph, and verify the Pathmind HUD.
6. Exercise navigator world overlays from several camera angles and through translucent geometry.
7. Resize the window and change GUI scale, then repeat editor and overlay checks.
8. Return to the title screen and inspect `latest.log` for render-pipeline, shader, Mixin, linkage, or backend-specific errors.

Pathmind's shared and 26.x source trees are structurally forbidden from importing raw OpenGL APIs. Any unavoidable backend-specific implementation must live outside those roots, have an explicit capability boundary, and define safe behavior for Vulkan before it is accepted.

## Deprecation policy

Supported targets are never removed because a dependency disappeared from a default, a CI row was inconvenient, or an adapter stopped compiling silently. Removing a Minecraft version requires an explicit project decision that:

- updates the manifest, runtime support, README, and compatibility documentation together;
- states the user-facing reason and final supported Pathmind release;
- confirms that release assets for remaining targets are unaffected;
- removes source families only after proving no supported row selects them.

Compatibility aliases and bridge methods may be removed only after their last manifest consumer is gone. Migration comments and generated build trees are not permanent architecture and must not be committed.

## Release checklist

1. Confirm the full CI tier passed every discovered target.
2. Confirm every matrix artifact passed `verifySelectedCompatibilityArtifacts`.
3. Confirm the release job downloaded the expected number of uniquely named jars.
4. Complete representative packaged-client smoke tests, including OpenGL and Vulkan for 26.2.
5. Review the compatibility report and release notes for target/pin accuracy.
6. Publish only after the complete matrix is available; never manually publish a successful subset as a full release.

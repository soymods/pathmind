# Pathmind Build Generations

Pathmind has one contributor-facing build interface and two internal build generations. The selected row in [`gradle/minecraft-versions.properties`](../gradle/minecraft-versions.properties) owns the choice; production code and CI must not infer it from version strings.

| Generation | Targets | Java | Authored names | Mapping dependency | Public packaging task |
| --- | --- | --- | --- | --- | --- |
| `pre26-remapped` | Minecraft `1.21.x` | 21 | canonical Mojang names | official/canonical Mojang mapping jar | Fabric and NeoForge `remapJar` |
| `mc26-unobfuscated` | Minecraft `26.x` and later | 25 | official unobfuscated names | none | Fabric and NeoForge `jar` |

The task names in the last column are implementation details. Use the stable root tasks:

```bash
# All release loaders for one selected target
./gradlew buildSelectedTarget -Pmc_version=1.21.11

# One loader for one selected target
./gradlew buildSelectedFabric -Pmc_version=1.21.11
./gradlew buildSelectedNeoForge -Pmc_version=1.21.11

# One target through an explicit convenience task
./gradlew buildMc1_21_11

# Every supported release target
./gradlew buildAllTargets
```

Development launch commands remain `runFabricClient`, `runNeoForgeClient`, `runFabricServer`, and `runNeoForgeServer`. Gradle selects the manifest's Java toolchain, so changing the shell's active JDK is unnecessary when the required JDK is installed.

## Adding a target

1. Add a complete, ordered row to the compatibility manifest and `supported_versions`.
2. Select a named compatibility/source family; unknown families fail configuration.
3. Select `pre26-remapped` or `mc26-unobfuscated`. Do not add version comparisons to module build scripts.
4. Supply exact loader/API pins and release loaders.
5. For pre-26 targets, generate and retain the revisioned canonical Mojang mapping jar. The build resolves it through local module coordinates rather than a raw file dependency, giving Loom a stable generation-aware cache key. Bump the mapping revision when generator semantics change so Loom cannot reuse an incompatible cached layer. For 26+ targets, declare no mapping input.
6. Update runtime support and the documented supported-target list, then run the manifest and matrix checks.

The [`gradle/minecraft-version-templates/26.x.properties`](../gradle/minecraft-version-templates/26.x.properties) file records the 26.x generation contract. Passes 5 and 6 added verified `26.1` and `26.2` manifest rows and isolated Java 25 builds. The shared build uses Gradle 9.5.1, Fabric Loom 1.17, and backend-neutral 26.x source families; future rows must still use verified releases rather than guessed pins.

## Artifact and CI contract

Both generations publish the same names:

```text
pathmind-fabric-<modVersion>+mc<minecraftVersion>.jar
pathmind-neoforge-<modVersion>+mc<minecraftVersion>.jar
```

For pre-26 builds, the unclassified public jar is the remapped shadow jar. For 26+ builds, it is the normal unobfuscated jar and `remapJar` is absent. Sources and development jars keep classifiers and are excluded from release staging.

The 26.x generation lives in the isolated `mc26` build because unobfuscated Fabric Loom and NeoForge ModDevGradle are incompatible with the pre-26 Architectury/Loom configuration. Root tasks delegate automatically; contributors should not invoke the nested build directly during normal work.

CI discovers version, Java, and packaging generation from the manifest. Generation marker files participate in Gradle cache keys so Java 21/remapped caches and Java 25/unobfuscated caches cannot share the same dependency cache entry.

## Configuration checks

```bash
./gradlew verifyCompatibilityManifest verifyBuildGenerationRouting
./gradlew configureMc26BuildGeneration
```

The second command resolves a Java 25 launcher and validates the shared `mc26-unobfuscated`, mapping-free contract. CI also verifies that an unregistered `mc_version` fails configuration with an actionable message.

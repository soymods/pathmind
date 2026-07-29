# Contributing To Pathmind

Thanks for wanting to help. Pathmind is a visual scripting mod for Minecraft, and
it supports a wide range of Minecraft versions across two mod loaders from one
source tree. That last part drives most of what is unusual about this repository,
so this document leads with it.

- [Getting Set Up](#getting-set-up)
- [Which Source Set Does My Code Go In](#which-source-set-does-my-code-go-in)
- [Building And Running](#building-and-running)
- [What To Verify Before Opening A Pull Request](#what-to-verify-before-opening-a-pull-request)
- [Code Style](#code-style)
- [Adding A Node](#adding-a-node)
- [Adding A Minecraft Version](#adding-a-minecraft-version)
- [Commit And Pull Request Conventions](#commit-and-pull-request-conventions)
- [Where To Ask](#where-to-ask)

## Getting Set Up

You need Git and a JDK. Which JDK depends on the Minecraft version you target:

- Minecraft `1.21.x` targets build on **Java 21**
- Minecraft `26.x` targets build on **Java 25**

Gradle resolves toolchains per target, so having both installed is the smoothest
setup. If you only have one, stick to targets that use it.

```bash
git clone https://github.com/soymods/pathmind.git
cd pathmind
./gradlew :common:compileJava -Pmc_version=1.21.11
```

`gradle/minecraft-versions.properties` is the **source of truth** for which
Minecraft versions are supported and what loader, Fabric API, NeoForge, and Java
version each one uses. This document deliberately does not repeat that list, so
it cannot drift out of date. Read the manifest.

Every Gradle invocation takes `-Pmc_version=<version>`. It is not optional in
spirit — it selects the entire build graph, including which source directories
compile, which mappings apply, and which Gradle build actually runs. Omitting it
falls back to `minecraft_version` in `gradle.properties`.

## Which Source Set Does My Code Go In

This is the single most confusing thing about the repository, and getting it wrong
is the most common way a change compiles locally and fails in CI. There are four
kinds of source directory.

### `src/main` — version-agnostic

Plain Java that compiles identically on every supported target. **Default here.**
If your code does not touch a Minecraft API that changed across versions, it
belongs in `src/main` and nowhere else.

```
common/src/main/java/com/pathmind/...
```

### `src/stonecutter` — one file, version-conditional regions

Preprocessed by Stonecutter (see `docs/stonecutter-migration.md`). Use this when a
single class needs small version-specific differences and splitting it would
duplicate hundreds of lines. Conditional regions look like this:

```java
//? if MC_1_21_8 {
/*public boolean mouseClicked(double mouseX, double mouseY, int button) {
    *///?} else {
public boolean mouseClicked(MouseButtonEvent click, boolean inBounds) {
    double mouseX = click.x();
    double mouseY = click.y();
    int button = click.button();
    //?}
```

The inactive branch lives inside a comment. Stonecutter swaps which branch is
commented out based on the target, so **both branches must stay syntactically
valid**. Editors will not help you here; the compiler only checks the active one,
which is exactly why the full-matrix check below matters.

A concrete rule from real experience: if a class forwards Minecraft input events
(`MouseButtonEvent`, `KeyEvent`, `CharacterEvent`) or calls an API whose signature
moved, it has to be in `src/stonecutter`. If it only deals in `int`/`double`
coordinates, keep it in `src/main`.

### `src/compat/<family>` — whole-file variants

One complete implementation per compatibility family, selected by the manifest's
`*_family` keys. Use this when versions diverge too much for inline conditionals —
different imports, different class hierarchies, a rewritten method.

```
common/src/compat/mc-1.21.0-1.21.8/...
common/src/compat/mc-1.21.9-1.21.10/...
common/src/compat/mc-1.21.11/...
common/src/compat/mc-26.1-26.2/...
common/src/compat/api/...             # shared interface the variants implement
```

Every family the manifest can select **must** have a directory, and each variant
must expose the same type and signatures. `verifyCompatibilityManifest` fails if a
selected family's directory is missing, and `verifyCompatibilityStructure`
separately rejects vague directory names (`legacy`, `mid`, `modern`) and
loader-level mirrors — name families by the version range they cover.

### `mc26/` — a separate Gradle build

Minecraft `26.x` is unobfuscated and mapping-free, so it cannot share a toolchain
with the remapped `1.21.x` builds. Loading both would configure incompatible
remapping. The root `settings.gradle.kts` therefore **excludes** `common`,
`fabric`, and `neoforge` when you ask for a `26.x` target and delegates to the
isolated build in `mc26/`, which consumes the same sources.

The practical consequence: `:common:compileJava` does not exist for `26.x`. Use
`-p mc26` instead (see below). `docs/build-generations.md` explains the boundary
in full.

## Building And Running

```bash
# Compile shared code for a 1.21.x target
./gradlew :common:compileJava -Pmc_version=1.21.11

# Compile for a 26.x target (isolated build)
./gradlew -p mc26 :fabric:compileJava -Pmc_version=26.2
./gradlew -p mc26 :neoforge:compileJava -Pmc_version=26.2

# Unit tests (shared code; not version-specific)
./gradlew :common:test -Pmc_version=1.21.11

# Full jars for one target
./gradlew buildSelectedTarget -Pmc_version=1.21.11

# Dev client
./gradlew runFabricClient -Pmc_version=1.21.11
./gradlew runNeoForgeClient -Pmc_version=1.21.11
```

`README.md` has more build variants. `docs/build-generations.md` covers the
generation split, and `docs/compatibility-maintenance.md` is the release playbook.

## What To Verify Before Opening A Pull Request

**Interactive in-game testing is not required for a pull request.** It is a
release-validation activity, documented in
`docs/minecraft-compatibility-baseline.md`. Do not feel obliged to launch twelve
clients. What is expected:

**1. Unit tests pass.**

```bash
./gradlew :common:test -Pmc_version=1.21.11
```

**2. The repository's own consistency checks pass.**

```bash
./gradlew verifyCompatibilityManifest verifyCompatibilityStructure \
    verifyBuildGenerationRouting -Pmc_version=1.21.11
```

**3. It compiles on more than your own target.** This is the one people skip and
regret. A change can compile cleanly on `1.21.11` and fail on `26.2` because a
Minecraft method moved, or fail on `1.21.8` because the inactive Stonecutter
branch was never checked.

The manifest defines `fast_verification_versions` — the smallest set that covers
every compatibility family. Compile those:

```bash
for v in 1.21 1.21.8 1.21.10 1.21.11; do
  ./gradlew :common:compileJava -Pmc_version=$v || break
done
for v in 26.1 26.2; do
  ./gradlew -p mc26 :fabric:compileJava   -Pmc_version=$v || break
  ./gradlew -p mc26 :neoforge:compileJava -Pmc_version=$v || break
done
```

If you cannot run all of them locally — no Java 25, for instance — say so in the
pull request. CI builds the complete matrix on every pull request and will catch
it; flagging it just saves a review round trip.

**4. If you touched UI or the visual editor, click through it.** The `ui` and
`screen` packages have very little automated coverage, so a human is currently the
only check on interaction behaviour. Exercise the specific thing you changed.

## Code Style

Match the surrounding code. `.editorconfig` covers the mechanics (4-space indent,
LF, UTF-8, final newline, no trailing whitespace) and most editors apply it
automatically.

Beyond that:

- Follow the naming and comment density of the file you are editing.
- Comment the *why*, not the *what*. Existing comments that explain intent —
  ordering constraints, why a branch exists — are load-bearing; keep them when you
  move code.
- **Input handling order is behaviour.** In the visual editor, the sequence of
  early-returns in `mouseClicked`, `keyPressed`, and friends encodes popup
  precedence and interaction rules. It is largely untested. Preserve order exactly
  unless changing it is the point of your patch, and say so if it is.
- Prefer extracting a controller behind a narrow `Host` interface over growing an
  existing screen class. `common/src/main/java/com/pathmind/screen/` has several
  examples.

There is no autoformatter, so please do not reformat files you are not otherwise
changing — it buries the real diff.

## Adding A Node

Nodes are the main extension point, and right now adding one touches several
shared files. A dedicated node API is planned; until it lands, follow an existing
node closely. `git show 187e6e15` (a sensor node) and `git show 3f5dd7d8` (an
action node) are good, small references.

Roughly, you will touch:

| File | Why |
| --- | --- |
| `nodes/NodeType.java` | The enum constant |
| `nodes/Node.java` | Sockets, dimensions, per-type behaviour |
| `nodes/NodeCatalog.java` | Catalog entry and category |
| `nodes/NodeTraitRegistry.java` | Traits the node opts into |
| `ui/sidebar/Sidebar.java` | Sidebar placement |
| `resources/assets/pathmind/lang/en_us.json` | Display name and tooltip |
| a `Node*CommandExecutor` | What the node actually does at runtime |

Some nodes also need `data/NodeGraphPersistence.java`, `nodes/NodeMode.java`, or
`execution/ExecutionManager.java`.

Notes that will save you time:

- **Add the `en_us.json` keys.** A missing key surfaces as a raw translation key
  in the UI. Other locales fall back to English, so translating is optional.
- **Check persistence round-trips.** If your node stores parameters, load a saved
  preset containing it and confirm the values survive.
- **Prefer the declarative path where it exists.** `NodeParameterDefinition`
  implementations and `NodeBehaviorDefinition` already cover a lot; a new `switch`
  case on `NodeType` should be a last resort, because that is the pattern we are
  moving away from.

`docs/node-architecture.md` goes deeper on how nodes are structured.

## Adding A Minecraft Version

Start with `docs/compatibility-maintenance.md`; it is the authoritative playbook.
In outline: add the version block to `gradle/minecraft-versions.properties`, add
or extend whatever `src/compat/<family>` directories the new families need, then
run the verification tasks. `verifyCompatibilityManifest` will tell you what is
missing, including which documentation is now out of date — it checks `README.md`,
`docs/build-generations.md`, and `docs/compatibility-maintenance.md` against the
manifest.

## Commit And Pull Request Conventions

Commit subjects in this repository are short, imperative, and describe the change
rather than the process:

```
Extract visual editor workspace viewport
Restore marketplace gallery scrollbar
Cap marketplace pages at five card rows
```

Write the body for a reader who is trying to understand *why* six months from now.
If you made a non-obvious call — chose one seam over another, preserved an odd
ordering, deliberately left something alone — that belongs in the body.

For pull requests:

- One logical change per pull request. Large mechanical refactors are much easier
  to review split into passes that each compile and pass tests.
- Say which targets you verified, and which you could not.
- Call out anything you changed that is not covered by tests, so a reviewer knows
  where to look manually.

## Where To Ask

Open an issue for bugs and feature discussion. If you are unsure whether an
approach fits before writing it, open an issue first — especially for anything
touching the compatibility system, since a design that works on one Minecraft
generation may not survive the other.

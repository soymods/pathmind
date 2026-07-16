<div align="center">

# Pathmind

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.1--26.2-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.17.3%2B-CC6E3E?style=for-the-badge&logo=modrinth)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.0%2B-E04E14?style=for-the-badge)](https://neoforged.net)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.java.net)
[![License](https://img.shields.io/badge/License-See%20LICENSE-lightgrey?style=for-the-badge)](LICENSE.txt)

A visual node editor for building Minecraft automation workflows.

Created by `soymods`.

🇺🇸 English · 🇩🇪 Deutsch · 🇪🇸 Español · 🇫🇷 Français · 🇵🇱 Polski · 🇧🇷 Português (BR) · 🇷🇺 Русский

### Download On Modrinth

<a href="https://modrinth.com/mod/pathmind">
  <img src="https://img.shields.io/badge/Modrinth-DOWNLOAD-00D5AA?style=for-the-badge&logo=modrinth&logoColor=white">
</a>

</div>

## What Pathmind Is

Pathmind lets you build automation with a visual graph instead of writing commands or scripts. You place nodes, connect them, configure parameters, then run the graph in-game.

The current mod includes:

- A visual editor for building automation graphs in-game.
- Workspace and preset management for saving and organizing your graphs.
- Runtime automation features for executing workflows and reacting to game state.
- Pathfinding and movement automation, with optional integrations for expanded behavior.
- An in-game marketplace for sharing and discovering community presets.
- HUD and editor feedback to help monitor execution and troubleshoot graphs.

## Feature Overview

### Visual Editor

- Full-screen graph editing built around nodes and connections.
- Tools for organizing, editing, and validating workflows.
- Customizable editor presentation and general usability settings.

### Nodes And Logic

- Node categories for flow control, world interaction, player actions, data handling, sensing, parameters, and reusable logic.
- Support for combining simple actions into larger automation workflows.
- Scratch-style **Routines**: create a named flow block, add typed inputs, define it once, and call it anywhere in that preset.
- A separate Routine Library for explicitly linking or copying routines between presets.
- **Run Preset** for launching another complete automation without confusing it with an in-preset routine.

### Your First Routine

1. Open **Routines** and choose **Create Routine**.
2. Give it a useful label and add typed inputs, such as `break [block] within [range]`.
3. Build the behavior under the Routine entry node.
4. Exit the routine workspace, then drag the generated routine from the sidebar into your main workspace.
5. Attach different values to each call. Every call gets its own input values and waits for the routine to finish.

Variables are separate from routine inputs: local variables belong to one running chain, while global variables are shared by all running chains.

### Execution And Runtime

- Run graphs directly in-game and monitor what they are doing.
- Build workflows that respond to events, conditions, and changing state.
- Use runtime state and feedback overlays while automation is active.

### Navigation And Pathfinding

Pathmind ships with its own local movement backend and also supports Baritone-aware nodes.

- Built-in movement and pathfinding support for navigation-focused automation.
- Optional Baritone integration for players who want expanded navigation behavior.
- Visual feedback for navigation state while workflows are running.

### Marketplace

The in-game marketplace is more than a static browser:

- Browse shared presets from inside the mod.
- Import community presets into your own workspace.
- Publish and manage your own presets through the in-game UI.

## Controls

Default keybinds:

- Open editor: `Right Alt`
- Play graphs: `K`
- Stop graphs: `J`

Pathmind also adds main-menu integration so the editor is reachable before joining a world.

## Installation

### Required

- Minecraft `1.21` through `1.21.11`, or `26.1` through `26.2`
- Java `21` for Minecraft 1.21.x; Java `25` for Minecraft 26.x
- **Fabric:** Fabric Loader `0.17.3` or newer + matching Fabric API
- **NeoForge:** NeoForge `21.0.166` or newer

### Optional

- Baritone API mod for Baritone-backed nodes and extended navigation/building integration
- UI Utils for UI automation nodes and related integrations

### Steps

**Fabric:**

1. Install Fabric Loader for your target Minecraft version.
2. Install the matching Fabric API release.
3. Download the correct `pathmind-fabric-*+mc<version>.jar` from Modrinth.
4. Place the Pathmind and Fabric API jars in your `mods` folder.
5. Launch the game and open Pathmind with the configured keybind.

**NeoForge:**

1. Install NeoForge for your target Minecraft version.
2. Download the correct `pathmind-neoforge-*+mc<version>.jar` from Modrinth.
3. Place the Pathmind jar in your `mods` folder.
4. Launch the game and open Pathmind with the configured keybind.

## Workspace Files

Pathmind stores data inside your Minecraft directory under `pathmind/`.

- `pathmind/presets/`: saved workspace graphs
- `pathmind/routine-library.json`: routines explicitly added to the shared local library
- `pathmind/active_preset.txt`: current preset selection
- `pathmind/settings.json`: user settings
- `pathmind/marketplace_auth.json`: marketplace session data

Imported marketplace presets and exported graphs also flow through this preset system.

## Compatibility

- Release jars are versioned as `pathmind-{fabric|neoforge}-<modVersion>+mc<gameVersion>.jar`.
- The same codebase is built for Fabric and NeoForge across every supported Minecraft target from `1.21` through `1.21.11` and `26.1` through `26.2`.
- Multiple language files are included.
- Marketplace listings include version compatibility metadata.

## Development

Contributor documentation:

- [`docs/node-architecture.md`](docs/node-architecture.md) maps the node system, execution routing, UI helpers, localization rules, and current compat source sets.
- [`docs/minecraft-compatibility-baseline.md`](docs/minecraft-compatibility-baseline.md) records the protected `1.21.x` matrix, artifact contract, source inventory, and smoke-test procedure.
- [`docs/build-generations.md`](docs/build-generations.md) explains the stable build commands and the Java 21/remapped versus Java 25/unobfuscated boundary.
- [`docs/minecraft-multiversion-roadmap.md`](docs/minecraft-multiversion-roadmap.md) defines the staged transition to clean `1.21.x` and `26.x` support.

### Build From Source

```bash
git clone https://github.com/soymods/pathmind.git
cd pathmind
./gradlew buildSelectedTarget "-Pmc_version=1.21.11"
```

Fabric jars are written to `fabric/build/libs/`, NeoForge jars to `neoforge/build/libs/`.

### Run In Dev

```bash
# Default dev client (Fabric, Minecraft 1.21.11)
./gradlew runClient

# Fabric dev client
./gradlew runFabricClient "-Pmc_version=1.21.11"

# NeoForge dev client
./gradlew runNeoForgeClient "-Pmc_version=1.21.11"

# Direct project tasks also work
./gradlew :fabric:runClient "-Pmc_version=1.21.11"
./gradlew :neoforge:runClient "-Pmc_version=1.21.11"
```

Unqualified `runClient` and `runServer` default to Fabric because Fabric is the primary loader. The aliases are case-insensitive, so `runclient` also resolves to Fabric instead of launching every matching subproject run task.

### Build A Specific Minecraft Target

```bash
# All declared release loaders
./gradlew buildSelectedTarget -Pmc_version="1.21.11"

# Fabric only
./gradlew buildSelectedFabric -Pmc_version="1.21.11"

# NeoForge only
./gradlew buildSelectedNeoForge -Pmc_version="1.21.11"
```

Convenience tasks:

- `./gradlew buildMc1_21_11` - builds both platforms for 1.21.11
- `./gradlew buildAllTargets` - builds all 13 declared MC targets

Compatibility tasks:

- `./gradlew compatibilityReport` - prints dependency, Java, loader, and source-family selection for every target
- `./gradlew verifyCompatibilityManifest` - checks the manifest against runtime support, metadata, docs, and CI
- `./gradlew verifyCompatibilityStructure` - rejects ambiguous family names and loader-level product mirrors
- `./gradlew configureMc26BuildGeneration` - validates the Java 25, mapping-free 26.x generation contract

The machine-readable version source of truth is [`gradle/minecraft-versions.properties`](gradle/minecraft-versions.properties).

### Supported Build Targets

`1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`, `26.1`, `26.2`

## Version Information

| Component | Version |
|-----------|---------|
| Mod Version | `1.1.5` |
| Supported Minecraft Versions | `1.21 - 1.21.11`, `26.1 - 26.2` |
| Fabric Loader | `0.17.3+` |
| NeoForge | `21.0.166+` |
| Java | `21` for 1.21.x; `25` for 26.x |

## License

This project is distributed under the custom **Pathmind License (All Rights Reserved)** in [`LICENSE.txt`](LICENSE.txt).

In short:

- Redistribution, modification, or re-uploading is not allowed without explicit written permission.
- Videos featuring the mod are allowed, including monetized videos.
- Modpack inclusion is allowed under the limits described in the license.
- The mod is provided as-is without warranty.

## Support And Feedback

- Issues: [GitHub Issues](https://github.com/soymods/pathmind/issues)
- Downloads: [Modrinth](https://modrinth.com/mod/pathmind)
- Community: [Discord](https://discord.gg/7nGRX2d8a6)

## Acknowledgments

- FabricMC for the Fabric modding framework
- NeoForged for the NeoForge modding framework
- Architectury for the cross-platform mod toolchain
- Blender and Scratch for helping inspire the node-based workflow direction

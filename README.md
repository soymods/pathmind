<div align="center">

# Pathmind

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21--1.21.11-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
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
- Reusable graph structures for building modular systems.

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

- Minecraft `1.21` through `1.21.11`
- Java `21+`
- Architectury API (matching your Minecraft version)
- **Fabric:** Fabric Loader `0.17.3` or newer + matching Fabric API
- **NeoForge:** NeoForge `21.0.166` or newer

### Optional

- Baritone API mod for Baritone-backed nodes and extended navigation/building integration
- UI Utils for UI automation nodes and related integrations

### Steps

**Fabric:**

1. Install Fabric Loader for your target Minecraft version.
2. Install the matching Fabric API release.
3. Install the matching Architectury API release.
4. Download the correct `pathmind-fabric-*+mc<version>.jar` from Modrinth.
5. Place the Pathmind, Fabric API, and Architectury API jars in your `mods` folder.
6. Launch the game and open Pathmind with the configured keybind.

**NeoForge:**

1. Install NeoForge for your target Minecraft version.
2. Install the matching Architectury API release.
3. Download the correct `pathmind-neoforge-*+mc<version>.jar` from Modrinth.
4. Place the Pathmind and Architectury API jars in your `mods` folder.
5. Launch the game and open Pathmind with the configured keybind.

## Workspace Files

Pathmind stores data inside your Minecraft directory under `pathmind/`.

- `pathmind/presets/`: saved workspace graphs
- `pathmind/active_preset.txt`: current preset selection
- `pathmind/settings.json`: user settings
- `pathmind/marketplace_auth.json`: marketplace session data

Imported marketplace presets and exported graphs also flow through this preset system.

## Compatibility

- Release jars are versioned as `pathmind-{fabric|neoforge}-<modVersion>+mc<gameVersion>.jar`.
- The same codebase is built for Fabric and NeoForge across every supported Minecraft target from `1.21` through `1.21.11`.
- Multiple language files are included.
- Marketplace listings include version compatibility metadata.

## Development

For a contributor-oriented map of the node system, execution routing, UI helpers, localization rules, and compat source-set expectations, see [`docs/node-architecture.md`](docs/node-architecture.md).

### Build From Source

```bash
git clone https://github.com/soymods/pathmind.git
cd pathmind
./gradlew :fabric:remapJar :neoforge:remapJar "-Pmc_version=1.21.11"
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
# Both platforms
./gradlew :fabric:remapJar :neoforge:remapJar -Pmc_version="1.21.11"

# Fabric only
./gradlew :fabric:remapJar -Pmc_version="1.21.11"

# NeoForge only
./gradlew :neoforge:remapJar -Pmc_version="1.21.11"
```

Convenience tasks:

- `./gradlew buildMc1_21_11` - builds both platforms for 1.21.11
- `./gradlew buildAllTargets` - builds all 12 MC versions

### Check Architectury API Versions

```bash
./gradlew checkArchitecturyVersions
```

Queries Modrinth for the latest Architectury API version for each configured MC version and reports whether updates are available.

### Supported Build Targets

`1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`

## Version Information

| Component | Version |
|-----------|---------|
| Mod Version | `1.1.5` |
| Supported Minecraft Versions | `1.21 - 1.21.11` |
| Fabric Loader | `0.17.3+` |
| NeoForge | `21.0.166+` |
| Java | `21+` |

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

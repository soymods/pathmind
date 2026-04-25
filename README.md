<div align="center">

# Pathmind

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21--1.21.11-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.17.3%2B-CC6E3E?style=for-the-badge&logo=modrinth)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.java.net)
[![License](https://img.shields.io/badge/License-See%20LICENSE-lightgrey?style=for-the-badge)](LICENSE.txt)

A visual node editor for building Minecraft automation workflows.

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
- Fabric Loader `0.17.3` or newer
- Matching Fabric API
- Java `21+`

### Optional

- Baritone API mod for Baritone-backed nodes and extended navigation/building integration
- UI Utils for UI automation nodes and related integrations

### Steps

1. Install Fabric Loader for your target Minecraft version.
2. Install the matching Fabric API release.
3. Download the correct Pathmind jar from Modrinth.
4. Place the jars in your `mods` folder.
5. Launch the game and open Pathmind with the configured keybind.

## Workspace Files

Pathmind stores data inside your Minecraft directory under `pathmind/`.

- `pathmind/presets/`: saved workspace graphs
- `pathmind/active_preset.txt`: current preset selection
- `pathmind/settings.json`: user settings
- `pathmind/marketplace_auth.json`: marketplace session data

Imported marketplace presets and exported graphs also flow through this preset system.

## Compatibility

- Release jars are versioned as `pathmind-<modVersion>+mc<gameVersion>.jar`.
- The same codebase is built for every supported Minecraft target from `1.21` through `1.21.11`.
- Multiple language files are included.
- Marketplace listings include version compatibility metadata.

## Development

### Build From Source

```bash
git clone https://github.com/soymods/pathmind.git
cd pathmind
./gradlew build
```

Artifacts are written to `build/libs/`.

### Run In Dev

```bash
./gradlew runClient
```

### Build A Specific Minecraft Target

```bash
./gradlew build -Pmc_version=1.21.11
```

Convenience tasks:

- `./gradlew buildMc1_21_11`
- `./gradlew buildAllTargets`

### Supported Build Targets

`1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`

## Version Information

| Component | Version |
|-----------|---------|
| Mod Version | `1.1.4` |
| Supported Minecraft Versions | `1.21 - 1.21.11` |
| Fabric Loader | `0.17.3+` |
| Java | `21+` |

## Release Readiness

Use [`RELEASE_GATE.md`](RELEASE_GATE.md) before promoting a build.

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

- FabricMC for the modding framework
- Blender and Scratch for helping inspire the node-based workflow direction

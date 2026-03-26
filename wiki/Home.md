# Pathmind Wiki

Pathmind is a client-side Fabric mod that adds a visual node editor for building automation workflows in Minecraft. Players assemble graphs from events, logic, sensors, movement commands, GUI actions, and optional Baritone-backed pathing behaviors, then run those graphs as reusable presets.

This wiki is intended to document the repository itself: how the mod is structured, where major systems live, and what contributors should understand before making changes.

## At a Glance

- Mod ID: `pathmind`
- Environment: client-only Fabric mod
- Java target: 21
- Supported Minecraft versions: `1.21` through `1.21.11`
- Build system: Gradle with Fabric Loom
- Optional integrations: Baritone runtime/API and UI Utils-dependent UI nodes

## What Pathmind Does

At the feature level, Pathmind combines a visual editor with a runtime execution engine:

- A node-based workspace lets players build automation graphs instead of writing commands or scripts.
- Graphs can express control flow, variables, lists, sensors, movement, interaction, and preset-to-preset composition.
- Validation runs before execution to catch missing start nodes, unresolved function calls, invalid preset references, duplicate connections, and missing optional dependencies.
- Runtime overlays expose active nodes, variables, and execution errors directly in the HUD.
- Workspaces are persisted as JSON presets under the player's Minecraft directory.

## Repository Map

The source tree is split into shared code plus version-specific shims for input and screen behavior:

```text
src/
  main/java/com/pathmind/
    data/        preset storage, settings, graph persistence
    execution/   graph runtime and active-chain state
    mixin/       client hooks and rendering/input integration
    nodes/       node definitions, traits, categories, parameters
    screen/      shared screen registration and support screens
    ui/          graph editor, overlays, menus, sidebars, controls, theme
    util/        compatibility bridges, optional dependency checks, helpers
    validation/  pre-run graph validation
  legacy/java/   client code for 1.21 through 1.21.8 input/screen APIs
  mid/java/      client code for 1.21.9 through 1.21.10 APIs
  modern/java/   client code for 1.21.11+ APIs
  test/java/     persistence, validation, utility, and execution tests
```

## Core Systems

### Entrypoints

- `PathmindMod` performs base mod initialization and version support checks.
- `PathmindClientMod` initializes presets, settings-backed overlays, keybinds, main menu integration, Fabric event forwarding, chat tracking, and execution lifecycle behavior.

### Node Model

The node system is centered in [`src/main/java/com/pathmind/nodes`](../src/main/java/com/pathmind/nodes):

- `NodeType` defines the available graph surface area.
- `NodeCategory` groups nodes for the sidebar.
- `Node`, `NodeConnection`, and `NodeParameter` represent workspace state.
- `NodeTraitRegistry` and related helpers encode node capabilities and slot requirements.

The current node set includes:

- Events and function-style entrypoints
- Variables and lists
- Boolean/comparison/random operators
- Movement, pathing, exploration, and resource automation
- GUI and inventory control
- Sensors for world state, entities, chat, GUI state, and Fabric events
- Templates, presets, and custom-node style composition

### Execution

[`ExecutionManager`](../src/main/java/com/pathmind/execution/ExecutionManager.java) is the runtime core. It tracks active chains, currently executing nodes, runtime variables/lists, cancellation state, event-driven execution, and overlay-facing execution snapshots.

Important execution characteristics:

- Multiple chains can run concurrently.
- Execution state is visible to HUD overlays.
- Optional Baritone-backed nodes are gated by dependency checks.
- Client lifecycle and Fabric events are forwarded into the graph runtime surface.

### Persistence

Graphs are stored as JSON through [`NodeGraphPersistence`](../src/main/java/com/pathmind/data/NodeGraphPersistence.java).

- Presets live under `.minecraft/pathmind/presets/`
- The active preset name is tracked in `.minecraft/pathmind/active_preset.txt`
- User settings live in `.minecraft/pathmind/settings.json`

`PresetManager` handles creation, import, rename, deletion, and active-preset selection. `SettingsManager` owns editor and HUD preferences such as language, accent color, tooltips, overlays, and node delay.

### Validation

[`GraphValidator`](../src/main/java/com/pathmind/validation/GraphValidator.java) performs structural checks before execution. It currently validates things like:

- presence of at least one `START` node
- unreachable or dead entry chains
- duplicate event-function names
- unresolved function calls
- missing preset/template targets
- multiple connections into a single input
- missing required parameter slots
- missing optional dependencies for Baritone or UI Utils nodes

## Versioning Strategy

Pathmind supports a wide Minecraft range from one repository and one shared feature set.

- Shared gameplay/editor logic lives in `src/main/java`.
- Version-specific classes live in `src/legacy/java`, `src/mid/java`, and `src/modern/java`.
- Gradle selects the correct versioned source set based on `-Pmc_version=<version>`.
- Convenience tasks such as `buildMc1_21_11` and `buildAllTargets` generate jars for specific or all supported targets.

This layout keeps most behavior unified while isolating API breakage to a small set of files.

## Development Workflow

Common commands:

```bash
./gradlew genSources
./gradlew build
./gradlew test
./gradlew runClient
./gradlew build -Pmc_version=1.21.11
./gradlew buildAllTargets
```

Before shipping a release, use [`RELEASE_GATE.md`](../RELEASE_GATE.md) as the required checklist.

## Where To Start

If you are new to the codebase, this order is usually the fastest way in:

1. Read [`README.md`](../README.md) for product-level context.
2. Read [`PathmindClientMod.java`](../src/main/java/com/pathmind/PathmindClientMod.java) to see initialization flow.
3. Read [`NodeType.java`](../src/main/java/com/pathmind/nodes/NodeType.java) to understand the graph surface area.
4. Read [`ExecutionManager.java`](../src/main/java/com/pathmind/execution/ExecutionManager.java) for runtime behavior.
5. Read [`NodeGraphPersistence.java`](../src/main/java/com/pathmind/data/NodeGraphPersistence.java) and [`GraphValidator.java`](../src/main/java/com/pathmind/validation/GraphValidator.java) for save/load and safety rules.

## Suggested Follow-Up Wiki Pages

Natural next pages for this wiki:

- Architecture overview
- Node taxonomy and category reference
- Execution model and runtime state
- Preset format and persistence schema
- Multi-version support strategy
- UI/editor component map
- Optional dependency behavior for Baritone and UI Utils
- Release and testing process

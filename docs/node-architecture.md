# Pathmind Node Architecture

This document describes how the current Pathmind node system is wired together. It is intended as an orientation guide for refactors: where graph data lives, how presets become node graphs, how the editor mutates those graphs, and how execution walks them.

## High-Level Model

Pathmind is built around saved preset graphs. A preset is a JSON file managed by `PresetManager`, deserialized into `NodeGraphData`, rebuilt into live `Node` and `NodeConnection` objects by `NodeGraphPersistence`, edited by `NodeGraph`, and executed by `ExecutionManager`.

The shortest version of the lifecycle is:

1. `PresetManager` decides which preset file is active.
2. `NodeGraphPersistence` loads that file into `NodeGraphData`.
3. `NodeGraphPersistence.convertToNodes` and `convertToConnections` rebuild runtime objects.
4. `NodeGraph` owns editor state: nodes, connections, selections, camera, drag state, history, rendering caches, and active preset name.
5. The visual editor screen delegates most graph editing to `NodeGraph` and starts execution through `ExecutionManager`.
6. `ExecutionManager` snapshots, validates, clones, scopes, and walks executable branches.
7. Each executable `Node` delegates command behavior to `NodeCommandDispatcher`, which forwards to focused executor classes.

## Presets And Files

`PresetManager` is the workspace-level file manager. It creates the base `pathmind` directory, creates the `presets` directory, tracks the active preset in `active_preset.txt`, sanitizes preset names, lists JSON preset files, imports and exports presets, and keeps marketplace link metadata.

The important point is that "active preset" is global file state, while `NodeGraph` also keeps a local `activePreset` string for the editor instance. Screens update both when switching tabs.

Preset graph data is stored as `NodeGraphData`:

- `nodes`: serialized `NodeData` records.
- `connections`: serialized `ConnectionData` records.
- `customNodeDefinition`: generated metadata that lets a preset behave like a reusable custom/template node.
- `routines`: preset-owned routine definitions with stable identities, input interfaces, independent versions, and embedded graphs.

`NodeGraphPersistence.saveNodeGraphForPreset` writes live nodes and connections to the preset file returned by `PresetManager.getPresetPath`. During save it also rebuilds `customNodeDefinition`, including discovered custom-node inputs, outputs, signature, and version.

`NodeGraphPersistence.loadNodeGraphForPreset` reads the preset JSON. If disk load fails but the graph was recently saved in-process, it can fall back to an in-memory JSON cache keyed by preset name.

## Serialization Shape

`NodeGraphData.NodeData` is broader than just type and position. It stores:

- identity: `id`, `type`, and `mode`;
- layout: `x` and `y`;
- inline parameters: `ParameterData` entries;
- attachment links: sensor, action, and parameter host/child ids;
- special per-node state such as start number, message lines, sticky note size/text, template graph, template version, custom node flag, runtime value scope, goto flags, and key sensor GUI behavior.

Connections are serialized separately as output node id/socket to input node id/socket.

Routine definitions remain inside their owning preset. Each routine and input has a UUID, while bindings are expected to use input IDs instead of labels or positions. Inputs persist their label, value kind, accepted traits, required state, default value, and order. A routine stores an embedded `NodeGraphData` graph plus two counters: `interfaceVersion` for binding-affecting interface edits and `implementationRevision` for metadata or internal graph edits.

`NodeGraphPersistence.sanitizeRoutineDefinitions` repairs missing or duplicate IDs, missing graphs, invalid versions, unknown value kinds, traits, and ordering. Its interface signature excludes editable labels but includes stable input IDs, kinds, traits, required/default state, and order. Its implementation signature includes names, labels, and graph structure, but deliberately excludes node coordinates. This means moving nodes does not version a routine; renaming only advances the implementation revision; and adding, removing, reordering, or changing an input advances the interface version while stable IDs preserve compatible bindings.

Attachments are not normal flow connections. Sensors, child action nodes, and parameter nodes are restored after all nodes are created so id references can be resolved.

When loading, `NodeGraphPersistence.convertToNodes`:

1. creates a new `Node` for each serialized type;
2. restores the old id by reflection so saved connections still match;
3. restores mode and parameters;
4. restores special node-specific fields;
5. repairs legacy or missing parameters;
6. recalculates dimensions;
7. restores sensor/action/parameter attachments in separate passes.

`convertToConnections` then restores normal graph edges. It skips sensor-linked nodes and uses conflict replacement so an input socket or output socket does not accumulate multiple restored connections.

## Node Types, Catalog Metadata, And Traits

`NodeType` is the stable enum used in save data. It should remain the durable id for a node kind.

Most mutable node metadata now belongs in `NodeCatalog`. Treat the catalog as the first place to look when changing how a node appears, where it is listed, what values it produces, what parameter slots it accepts, and which execution family handles it.

Catalog-owned metadata includes:

- category and sidebar placement;
- dependency flags such as Baritone and UI Utils;
- default parameters by node type and `NodeMode`;
- provided value traits;
- accepted parameter traits per slot;
- slot counts and slot labels;
- required slot rules;
- sidebar group headings;
- execution routes.

The older registry classes still exist mostly as compatibility facades:

- `NodeParameterDefinitionRegistry` forwards default-parameter lookups to the catalog.
- `NodeTraitRegistry` forwards trait and slot-schema lookups to the catalog.
- `NodeCompatibility` still owns slot compatibility checks, but those checks should line up with catalog traits and slot schemas.

This is why the system can feel spread out. A node's behavior is not defined in one class:

- `NodeType` names the kind.
- `NodeCatalog` categorizes it, exposes it in the sidebar, defines default parameters, defines traits and parameter slots, and declares its execution route.
- `NodeCompatibility` decides whether it can attach to another node.
- `NodeCommandDispatcher` decides which executor runs it.
- The executor class contains most command-side behavior.

For new work, prefer adding metadata to `NodeCatalog` and keeping the compatibility registries thin. If a registry needs a new public method for an old call site, it should usually delegate to the catalog instead of creating a second source of truth.

## Live Node Objects

`Node` is still the central compatibility shell. It represents one live editor/runtime node and owns stable APIs used by older call sites, but much of its state has been split out:

- `NodeRuntimeState`: transient execution and resolved runtime parameter data.
- `NodeLayoutState`: x/y/width/height and geometry.
- `NodeInteractionState`: selection, dragging, and interaction flags.
- `NodeAttachments`: parent/child attachment bookkeeping.

`Node` still handles a lot:

- rendering helpers and dimensions;
- socket counts and socket positions;
- mode and parameter initialization;
- parameter editing helpers;
- sensor/action/parameter attachment operations;
- validation before command execution;
- dispatch into command executors.

The current direction is for `Node.java` to keep shrinking. New behavior should usually be placed in the smallest owner that matches the concern.

## Graph Editing

`NodeGraph` is the visual editor's graph model. It owns:

- `List<Node> nodes`;
- `List<NodeConnection> connections`;
- selection state;
- camera/panning state;
- connection drag and connection cutting state;
- hierarchy/layout caches;
- context menu state;
- inline parameter editing state;
- history, clipboard, and persistence entry points.

The version-specific `PathmindVisualEditorScreen` classes handle screen integration and UI chrome, then delegate graph-specific actions to `NodeGraph`. For example, the screen creates `NodeGraph`, sets the active preset, routes mouse/key events, switches preset tabs, imports/exports, and calls `ExecutionManager` when the user presses play.

`NodeGraph` is therefore both a model and a large editor controller. It is not only a plain data structure.

## Attachments Versus Connections

There are two relationship systems:

1. Flow connections: `NodeConnection` edges between output and input sockets. Execution follows these.
2. Attachments: embedded child nodes inside a host slot.

Attachments cover:

- sensors attached to control/action nodes;
- action nodes attached inside control nodes;
- parameter nodes attached to parameter slots.

Attachments are persisted on `NodeData` as parent/child ids and restored by `NodeGraphPersistence.convertToNodes`. This distinction matters because attached nodes are visually and semantically part of the host node, while `NodeConnection` edges represent executable graph flow.

## Validation

`GraphValidator.validate` analyzes the current graph before execution and for editor feedback. It checks, among other things:

- at least one `START` node exists;
- required dependencies are present for node types that need Baritone or UI Utils;
- entry nodes are not dead;
- regular nodes are reachable from starts or event functions;
- input sockets do not have multiple incoming connections;
- required parameter slots are filled;
- event function and event call names resolve;
- run-preset/template/custom-node targets resolve;
- variable parameter usage has plausible inferred types.

`ExecutionManager` currently logs validation errors but still attempts execution so runtime errors can surface through overlays.

## Execution Lifecycle

Execution starts in `ExecutionManager`.

`executeGraph(nodes, connections)` is the "run all starts" path:

1. validate/log graph issues;
2. store workspace nodes/connections for replay and keybind starts;
3. find all `START` nodes;
4. cancel stale navigation commands;
5. filter connections;
6. create a `NodeGraphData` snapshot;
7. build isolated branch data for each start;
8. start global execution state;
9. create a `ChainController` for each branch;
10. call `runChain` for each branch.

`executeFromNode` and `executeBranch` are narrower launch paths used by node-level play controls and start-specific execution. They still snapshot, clone branch data, create a `ChainController`, seed runtime variables, and run a chain.

`ChainController` is the runtime scope for a branch. It tracks:

- root start node and execution id;
- cancellation;
- runtime variables and runtime lists;
- join-barrier input tracking;
- function handler templates;
- branch graph nodes/connections;
- parent scope for nested executions.

Named runtime values use `RuntimeValueScope`:

- `CHAIN` stores variables and lists at the root controller for one START execution tree. Nested preset executions inherit that root without seeing unrelated chains.
- `GLOBAL` stores variables and lists in the execution-wide shared maps and never falls through to chain state.
Explicit `CHAIN` and `GLOBAL` reads never fall through to one another. Nodes loaded without scope metadata default to `GLOBAL`, matching Pathmind's behavior before explicit scopes were added. Variables and runtime lists intentionally use the same scope rules.

Editor-created variable and Create List nodes default to `CHAIN`. Their top-right button uses a contained-value icon for local scope and a globe for global scope. Other list operations inherit the scope of the matching Create List declaration, so they do not show redundant scope controls. Clicking a visible scope button records undo state and saves through normal graph persistence. The runtime overlay labels values as local or global, and validation keeps type inference separate for equal names in different scopes.

This scope is why `RUN_PRESET`, `CUSTOM_NODE`, and `TEMPLATE` can start nested graphs without simply merging all state into the top-level editor graph.

## Command Dispatch

`Node.execute` does preflight checks, verifies required parameter slots, checks empty parameters, moves to the Minecraft client thread when necessary, and then calls `NodeCommandDispatcher.execute`.

Execution routing is declared in `NodeCatalog` and consumed through `NodeCommandDispatcher`. The dispatcher should stay a compatibility facade that maps catalog routes to focused executor classes:

- `NodeInventoryCommandExecutor`: hotbar, drop, slot clicks, screen clicks, item movement.
- `NodeGuiCommandExecutor`: UI Utils integration and player GUI open/close.
- `NodeNavigationCommandExecutor`: Baritone/pathing commands and navigation guards.
- `NodeTextIoCommandExecutor`: message, book, and sign writing.
- `NodeFlowCommandExecutor`: waits, control flow, start/stop chain, run preset/custom/template, stop all.
- `NodeMovementCommandExecutor`: look, walk, jump, key press, crawl, crouch, sprint, fly.
- `NodeEntityActionCommandExecutor`: interact, trade, swing, armor/hand equip, break.
- `NodeWorldActionCommandExecutor`: use/place/build/explore/follow style world actions.
- `NodeCraftCommandExecutor`: crafting.
- `NodeCollectCommandExecutor`: collection.
- `NodeSensorCommandExecutor`: boolean sensor evaluation.
- `NodeVariableListCommandExecutor`: variables and runtime lists.

Adding a command node should generally mean adding catalog metadata plus one focused executor implementation, not adding more logic to `Node.java`.

## Presets As Custom Nodes

Saved presets can expose `customNodeDefinition`. `NodeGraphPersistence` discovers:

- inputs from eligible `VARIABLE` nodes and initialization patterns;
- outputs from graph output/value usage;
- a signature based on graph contents;
- a monotonically increasing version when the signature changes.

`RUN_PRESET` loads another preset and starts its `START` nodes externally.

`CUSTOM_NODE` and `TEMPLATE` also load preset graph data, but they wait for nested execution completion and use template/custom-node metadata to behave more like reusable subgraphs. Their node data can include `templateName`, `templateVersion`, `customNodeInstance`, and embedded `templateGraph`.

The planned replacement for this overlapping preset/function/custom-node model is documented in [`routines-redesign-roadmap.md`](routines-redesign-roadmap.md). Implement that roadmap one reviewed pass at a time while retaining the compatibility behavior described here.

## Current Refactor Guidance

`Node.java` is the compatibility shell for editor state, serialization, and legacy call sites. New behavior should not be added there by default.

When adding or changing a node type, prefer the smallest owner:

1. Add stable type identity in `NodeType` only if a new persisted type is needed.
2. Add category, dependency, sidebar, default parameter, trait, slot-schema, and route metadata in `NodeCatalog`.
3. Add slot compatibility in `NodeCompatibility` when attachment rules change.
4. Add parameter/comparable behavior through behavior helpers when possible.
5. Put command execution in the executor for that behavior family, or create a new executor if it is a distinct family.
6. Keep `Node.java` wrappers thin and behavior-free.
7. Update `NodeGraphPersistence` only when the node has new persisted state beyond ordinary parameters, mode, position, connections, and attachments.
8. Update `GraphValidator` when the new type introduces a new graph-level invariant.

The goal is for `Node.java` and `NodeGraph.java` to keep losing responsibilities over time while preserving old save data and public APIs.

## Contributor Workflow: Adding Or Changing A Node

Use this checklist for most node changes.

1. Choose the stable id.

   Add a `NodeType` only for a new persisted node kind. Renaming enum constants breaks old saves unless there is an explicit migration path, so prefer changing display text through language keys when the behavior is the same.

2. Define catalog metadata.

   Update `NodeCatalog` for category, sidebar grouping, dependency flags, default parameters, provided traits, accepted parameter traits, slot labels, required slots, and execution route. If a node is moved to another category, colors and sidebar placement should follow from the catalog category.

3. Add or update translations.

   Node display names and descriptions live in language files under `common/src/main/resources/assets/pathmind/lang` and `src/main/resources/assets/pathmind/lang`. Add every key to every language file. If text appears in UI, use `Text.translatable(...)`, not hardcoded English.

4. Implement behavior.

   Put runtime behavior in the relevant executor family. Use `NodeCommandDispatcher` only to route to that family. Avoid growing `Node.java` unless you are adding a narrow compatibility wrapper or unavoidable node-state access.

5. Validate graph rules.

   Update `GraphValidator` when the node changes graph-level correctness, dependency requirements, required slots, event semantics, or custom-node behavior.

6. Persist only special state.

   Ordinary position, type, mode, parameters, connections, and attachments are already persisted. Only touch `NodeGraphPersistence` and `NodeGraphData` when the node needs additional state.

7. Mirror compat copies.

   If you touch a file under `common/src/compat/...`, update the matching `src/compat/...` and `fabric/src/compat/...` copies unless there is a documented version-specific reason not to. The current expectation is that these major editor files stay mirrored:

   - `PathmindVisualEditorScreen`
   - `PathmindMarketplaceScreen`
   - `PathmindSettingsPopupController`
   - `PathmindPresetPopupController`
   - `PathmindMarketplacePopupController`
   - `PathmindMarketplaceGraphPreviewRenderer`

8. Verify the change.

   For narrow node metadata changes, `./gradlew compileJava -q` is usually enough while iterating. For compat, persistence, validation, or execution-route changes, run `./gradlew test -q` and `./gradlew buildAllTargets -q` before release.

## UI And Localization Guidance

All user-facing text should be translatable. Use `Text.translatable("pathmind.some.key")` in UI code and add matching entries to every language file. Avoid `Text.literal(...)` for English words. Literals are fine for symbols, brand names, generated user content, file paths, numbers, and runtime data that should not be translated.

When adding UI text:

1. Add the key to `common/src/main/resources/assets/pathmind/lang/en_us.json`.
2. Add the same key to every other language file in `common/src/main/resources/assets/pathmind/lang`.
3. Mirror language files to `src/main/resources/assets/pathmind/lang`.
4. Use the key from UI code with `Text.translatable(...)`.
5. For tooltips, pass translated strings through `TooltipRenderer` or an existing helper.

Common UI helper ownership:

- `PathmindWorkspaceChrome`: workspace buttons, play/stop/publish/marketplace chrome, icon-button frames, and workspace button hit testing.
- `PathmindPopupRenderer`: shared popup frames, animated action buttons, section frames, badges, and popup text-button styling.
- `PathmindDropdownRenderer`: shared dropdown drawing.
- `PathmindSettingsRowRenderer`: settings rows and selector rows.
- `PathmindValidationPanelRenderer`: validation button and validation panel rows.
- `PathmindIconRenderer`: small shared icons.
- `PathmindPresetPopupController`: create/rename/delete/publish preset popups.
- `PathmindSettingsPopupController`: settings popup rendering and settings-node selection.
- `PathmindMarketplacePopupController`: marketplace detail, publish, confirm, and account popups.

If new UI repeats button, popup, dropdown, row, or validation styling, first add a small helper to one of these owners instead of duplicating drawing code in `PathmindVisualEditorScreen` or `PathmindMarketplaceScreen`.

## Compat Source Set Rules

The project has three relevant source trees:

- `common/src`: shared Architectury/common code and the source of truth for most compat UI files.
- `fabric/src`: Fabric platform source set.
- `src`: legacy/top-level Fabric source tree that is still kept aligned for compatibility.

Version-specific UI files are split under:

- `compat/legacy/base`
- `compat/mid`
- `compat/modern`

For mirrored files, make the change in `common/src` first, then copy the exact result to `src` and `fabric/src`. If a source set truly needs a different implementation, leave a short comment near the divergent code explaining why, and include that divergence in this document so future cleanup passes do not erase it accidentally.

Useful mirror check:

```bash
python3 - <<'PY'
from pathlib import Path
rels = [
    'compat/modern/java/com/pathmind/screen/PathmindVisualEditorScreen.java',
    'compat/mid/java/com/pathmind/screen/PathmindVisualEditorScreen.java',
    'compat/legacy/base/java/com/pathmind/screen/PathmindVisualEditorScreen.java',
    'compat/modern/java/com/pathmind/screen/PathmindMarketplaceScreen.java',
    'compat/mid/java/com/pathmind/screen/PathmindMarketplaceScreen.java',
    'compat/legacy/base/java/com/pathmind/screen/PathmindMarketplaceScreen.java',
]
for rel in rels:
    for root in ['src', 'fabric/src']:
        same = (Path('common/src') / rel).read_bytes() == (Path(root) / rel).read_bytes()
        print(f'{rel} {root}: {"ok" if same else "DRIFT"}')
PY
```

## Verification Checklist

Use the smallest verification that matches the risk:

- Metadata-only node/category/text change: `./gradlew compileJava -q`
- Validation or execution-route change: `./gradlew test -q`
- Compat source-set or platform-sensitive change: `./gradlew buildAllTargets -q`
- Resource/language change: parse every JSON language file and run `git diff --check`
- Before shipping: run `./gradlew buildAllTargets -q`

Useful cleanup scans:

```bash
rg -n 'Text\.literal\(Text\.translatable|RawJsonEditor|pathmind\.rawJson|rawJson|RAW_JSON' common/src src/main src/compat fabric/src --glob '!build/**'
rg -n 'drawText\([^\n]*"|drawTextWithShadow\([^\n]*"|Text\.literal\("[A-Za-z]' common/src src/main src/compat fabric/src --glob '!build/**'
```

These scans are not perfect. Symbols, brand names, user-provided text, file paths, and generated runtime data can be valid literals. Treat hits as review prompts, not automatic failures.

## Practical Debugging Map

Use this map when tracing a bug:

- Preset not appearing or wrong file path: start in `PresetManager`.
- Graph JSON looks wrong: inspect `NodeGraphData` and `NodeGraphPersistence.buildNodeGraphData`.
- Saved graph loads incorrectly: inspect `NodeGraphPersistence.convertToNodes` and `convertToConnections`.
- Editor drag/drop, selection, rendering, connection creation, or inline edit issue: start in `NodeGraph`, then the version-specific `PathmindVisualEditorScreen`.
- Parameter defaults are wrong: inspect `NodeCatalog`, then the `NodeParameterDefinitionRegistry` facade if a call site is stale.
- Parameter node cannot attach: inspect `NodeCatalog`, then `NodeTraitRegistry` and `NodeCompatibility`.
- Node category/sidebar/color/trait behavior is wrong: inspect `NodeCatalog`.
- Validation warning/error is wrong: inspect `GraphValidator`.
- Node runs but does the wrong action: inspect `NodeCommandDispatcher`, then the executor for that node family.
- Nested preset/custom-node behavior is wrong: inspect `NodeFlowCommandExecutor.executeRunPresetNode`, `ExecutionManager.executeExternalBranch*`, and `NodeGraphPersistence.resolveCustomNodeDefinition`.

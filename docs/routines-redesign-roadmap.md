# Pathmind Routines Redesign Roadmap

This document is the implementation contract for replacing Pathmind's overlapping function, template, and custom-node concepts with a Scratch-style reusable-node workflow.

The working product name is **Routines**. It can be changed before the user-facing work lands, but the technical term `Routine` should remain consistent during the implementation.

The roadmap is intentionally divided into passes. Complete, test, and review one pass before beginning the next. Do not opportunistically pull later-pass work into the current pass unless it is required to keep the repository compiling or preserve save compatibility.

## Product Goal

Pathmind should have three concepts that are easy to distinguish:

| Concept | Purpose | Lifetime and scope |
| --- | --- | --- |
| Variable | Remember state | One execution chain or all running chains |
| Routine | Reuse a named sequence of nodes with typed inputs | Runs synchronously inside its caller |
| Run Preset | Launch another complete automation | Starts the selected preset's START branches |

The intended user workflow is modeled after Scratch custom blocks:

1. Open the **Routines** category.
2. Choose **Create Routine**.
3. Build a label such as `break [block] within [range]`.
4. Add typed inputs and optional defaults.
5. Pathmind creates a definition workspace and a generated callable node.
6. Build the routine beneath its definition entry.
7. Drag the generated node into any graph in its owning preset.
8. Double-click an invocation to open its definition.

Routine inputs are local to one invocation. Ordinary variables retain their declared chain or global scope. Routines initially behave as command/flow nodes and do not return values.

## Terminology

- **Routine definition**: the name, ordered input contract, metadata, and internal graph.
- **Routine invocation**: a placed node that calls a routine definition.
- **Input reporter**: a parameter node available inside a definition that resolves one invocation input.
- **Call frame**: the temporary runtime scope created for one routine invocation.
- **Chain variable**: a variable isolated to one START execution tree, including nested routine calls.
- **Global variable**: a variable intentionally shared by all active execution chains.
- **Interface version**: changes only when a routine's public inputs or flow contract changes.
- **Implementation revision**: changes when the internal routine graph changes.
- **Legacy custom node**: the current preset-backed `CUSTOM_NODE` behavior.

## Non-Negotiable Behavior

These rules apply to every pass:

1. Existing preset files must continue to load.
2. Existing `EVENT_FUNCTION`, `EVENT_CALL`, `CUSTOM_NODE`, `TEMPLATE`, and `RUN_PRESET` nodes must not silently change behavior before their migration pass.
3. A routine invocation must wait for its routine to finish before continuing.
4. Two simultaneous invocations of the same routine must not share input values.
5. Recursive invocations must receive independent call frames and obey an execution-depth safety limit.
6. Renaming or reordering an input must not disconnect compatible invocation bindings. Bindings use stable input IDs, not display names or indexes.
7. Ordinary variables are not automatically routine-local. Only input reporters and explicitly call-local runtime state belong to a call frame.
8. New variables default to chain scope. Variables without scope metadata load as global, matching previous Pathmind behavior.
9. `Run Preset` remains the mechanism for starting a complete preset. Routines must not be represented as ordinary presets in the user-facing workspace.
10. User-facing strings must be translatable and added to every mirrored language resource.

## Storage Direction

Routines should be owned by a preset and embedded in that preset's `NodeGraphData`, similar to scripts belonging to a Scratch project.

A future workspace-level routine library may reference or copy embedded routines, but routine creation must not generate user-visible backing presets. This prevents presets and reusable procedures from becoming the same concept again.

The target persistence shape is conceptually:

```text
NodeGraphData
  nodes
  connections
  routines[]
    id
    name
    interfaceVersion
    implementationRevision
    inputs[]
      id
      label
      valueKind / acceptedTraits
      required
      defaultValue
      order
    graph
      nodes
      connections
```

Exact Java types may differ, but stable identity and separation of interface from implementation are required.

## Pass Protocol

For every implementation pass:

1. Confirm the worktree and current baseline.
2. Re-read this pass and identify files likely to change.
3. Add or update automated tests before considering the pass complete.
4. Run the narrowest relevant tests while iterating.
5. Run `./gradlew test -q` before handoff.
6. Run `./gradlew buildAllTargets -q` for persistence, execution, compat UI, or serialization changes.
7. Perform the pass-specific manual checks.
8. Update `docs/node-architecture.md` when ownership or runtime behavior changes.
9. Report completed behavior, compatibility notes, tests, and deferred work.
10. Stop for review. Do not begin the next pass until the user approves it.

---

## Pass 1: Correct Variable Scope Foundations

### Objective

Make variable scope explicit and reliable before routines depend on nested scopes.

### Work

- Introduce a persisted variable scope model with at least:
  - `CHAIN`
  - `GLOBAL`
- Add scope metadata to variable references/targets without encoding scope into the variable's display name.
- Refactor runtime variable lookup and assignment so chain writes are no longer unconditionally mirrored into the global map.
- Define lookup behavior:
  1. Explicit chain reference reads the current execution tree.
  2. Explicit global reference reads the shared runtime map.
  3. Unscoped references load as global, matching previous Pathmind behavior.
- Ensure nested preset execution and future routine call frames can inherit the caller's chain scope without inheriting unrelated chains.
- Define cleanup behavior when a chain, preset run, or all execution stops.
- Apply equivalent rules to runtime lists if they share the same scope machinery. Do not leave variables and lists with contradictory semantics.
- Add a depth-safe scope API to `ExecutionManager`; node executors should not manipulate backing maps directly.

### Compatibility

- Old saves have no scope field. Load them as global to preserve their previous shared behavior.
- Do not change editor defaults for existing variable nodes in this pass.
- Inline `$name` references must continue resolving. Document whether they use compatibility lookup or gain explicit scope syntax later.

### Automated coverage

- Two START chains can hold different chain-scoped values with the same name.
- A global value is visible to two START chains.
- A chain write cannot overwrite another chain's value.
- An explicit global write updates the shared value.
- Nested execution inherits its parent's chain values.
- Stop/cleanup removes the correct scopes.
- Old unscoped variables retain their previous shared behavior by loading as global.
- Runtime lists follow the chosen scope policy.

### Acceptance criteria

- Runtime scope rules are deterministic and tested.
- No variable assignment is accidentally made global.
- Existing presets still execute without serialization failures.
- There is a single runtime API for scoped reads and writes.

---

## Pass 2: Variable Scope UX

### Objective

Expose the new scope model in a way that novice users can understand.

### Work

- Add a scope selector to variable creation/editing.
- Prefer user-facing labels such as:
  - **This chain**
  - **All chains**
- Make **This chain** the default for newly created variables.
- Clearly identify global variables in nodes, dropdowns, overlays, and autocomplete without overwhelming the normal graph view.
- Update variable and list overlays to group or label values by scope.
- Update validation for contradictory or missing scope references.
- Ensure copy/paste, undo/redo, import/export, and parameter propagation preserve scope.
- Add all translations across mirrored language files.

### Manual checks

- Create two variables with the same name in separate chains and verify their displayed values differ.
- Change a global variable from one chain and observe it from another.
- Copy and paste scoped variable nodes.
- Save, reopen, and execute a graph containing both scopes.
- Verify overlays make it clear which value is being observed.

### Acceptance criteria

- A new user can choose a scope without needing to understand call stacks.
- Scope survives every normal editor and persistence workflow.
- Variables from old saves load and display as global.

---

## Pass 3: Routine Data Model and Persistence

### Objective

Add the durable routine definition model without replacing existing function/custom-node behavior yet.

### Work

- Add routine definitions to `NodeGraphData`.
- Give every routine and input a stable UUID-like ID.
- Store input label, accepted traits/value kind, required state, default value, and order.
- Store a nested graph for each routine definition.
- Separate interface version from implementation revision.
- Define version rules:
  - Rename without binding impact: implementation/metadata revision only.
  - Reorder with stable IDs: interface metadata change, bindings preserved.
  - Add/remove/type-change input: interface version change.
  - Internal graph edit: implementation revision only.
- Add conversion helpers between serialized and live routine graphs.
- Add sanitization for malformed definitions, duplicate IDs, missing graphs, and unknown value kinds.
- Ensure signatures do not depend on editor-only position unless layout changes are intentionally versioned.
- Keep routine registries scoped to the owning preset.

### Automated coverage

- Empty routine round trip.
- Multi-input routine round trip.
- Stable IDs survive save/load.
- Reordering inputs preserves identity.
- Internal edits do not increment interface version.
- Interface edits do increment interface version.
- Malformed routine data loads safely.
- Presets without a `routines` field remain valid.

### Acceptance criteria

- Routine definitions can be persisted without creating a separate preset file.
- Interface identity is stable enough to support invocation bindings.
- Existing save formats remain readable.

---

## Pass 4: Definition Workspace and Input Reporters

### Objective

Build the Scratch-style routine authoring experience.

### Work

- Add a **Routines** sidebar category with **Create Routine**.
- Add a routine builder dialog with a live label preview.
- Support adding, removing, renaming, typing, defaulting, and reordering inputs.
- Initially support all Pathmind value kinds that have stable parameter traits; at minimum support message/text, number, boolean, block, item, coordinate, player, and entity.
- Create a definition workspace containing a non-deletable routine entry node.
- Expose each input as an input reporter/parameter node available only inside that definition.
- Make input reporters visually match their value type and display their label.
- Allow dragging/copying input reporters from the definition header or a definition-local palette.
- Prevent input reporters from being moved into the parent graph or another routine.
- Add routine tabs/navigation and a clear return-to-parent action.
- Double-clicking definition metadata should reopen the builder.
- Persist editor history independently enough that routine edits support undo/redo.

### Product constraints

- The workflow should say “create reusable behavior,” not “create a backing preset.”
- Do not show version numbers or storage details in the primary definition UI.
- A definition has one flow entry. Multiple START nodes are not permitted inside a routine.
- Do not add return values in this pass.

### Manual checks

- Create `break [block] within [range]`.
- Reopen and edit the definition.
- Use both reporters inside compatible parameter slots.
- Confirm incompatible slots reject reporters.
- Save/reopen and verify the definition graph and layout.
- Verify input reporters cannot escape their definition.

### Acceptance criteria

- A routine can be created and defined without manually creating variables or presets.
- Input reporters are definition-local and strongly compatible with Pathmind parameter slots.
- The workflow is understandable without documentation.

---

## Pass 5: Generated Invocation Nodes

### Objective

Generate compact callable nodes from routine definitions and persist per-instance argument bindings.

### Work

- Add a routine invocation node type or durable invocation identity.
- Generate one sidebar entry per routine in the active preset.
- Render the routine's user-authored label and inputs directly on the node.
- Give each input a normal Pathmind parameter attachment slot.
- Resolve compatibility from the input's accepted traits/value kind.
- Persist bindings by stable input ID, not slot index or label.
- Preserve compatible bindings when inputs are renamed or reordered.
- Keep removed or type-incompatible bindings as recoverable orphan data until the user confirms deletion or migration.
- Support defaults and required inputs.
- Double-click an invocation to open its definition.
- Add invocation actions for open definition, duplicate, and inspect.
- Keep implementation details in the inspector/tooltip rather than the node body.
- Update dimensions, hit testing, hierarchy layout, clipboard, undo/redo, and graph validation.

### Automated coverage

- Invocation bindings round trip.
- Rename and reorder preserve bindings.
- Removing an input reports an orphan binding.
- Type changes report incompatible bindings.
- Defaults apply only when no binding is supplied.
- Required missing inputs produce validation errors.
- Copy/paste produces a new invocation while preserving the target routine ID.

### Manual checks

- Place two copies of the same routine and give them different arguments.
- Rename and reorder definition inputs.
- Confirm both invocations retain the correct bindings.
- Change an input type and verify a helpful repair state.
- Double-click each invocation and navigate back.

### Acceptance criteria

- Routine calls look and feel like first-class Pathmind nodes.
- Every invocation owns its argument bindings.
- Definition edits update invocations without fragile name/index matching.

---

## Pass 6: Routine Execution and Call Frames

### Objective

Execute routine invocations synchronously with isolated inputs and safe nested calls.

### Work

- Create a common callable execution path rather than adding more special cases to `Node.java`.
- On invocation:
  1. Resolve and validate argument parameter nodes.
  2. Snapshot/evaluate each supplied value.
  3. Create a child call frame attached to the caller's chain scope.
  4. Seed inputs by stable input ID.
  5. Clone or instantiate the definition graph for this invocation.
  6. Run the definition entry.
  7. Await all routine work.
  8. Dispose the call frame.
  9. Continue through the invocation's flow output.
- Input reporters must read only their current call frame.
- Ordinary chain variables must remain visible through the parent chain scope.
- Global variables must continue using explicit global scope.
- Add recursion depth and runaway branch limits with clear runtime errors.
- Propagate stop, pause, cancellation, and exceptions through nested calls.
- Ensure simultaneous calls use independent cloned runtime nodes and call frames.
- Ensure routine execution highlighting maps back to the definition and invocation meaningfully.

### Automated coverage

- Sequential calls receive different inputs.
- Parallel calls receive different inputs.
- Nested calls preserve caller and callee inputs.
- Recursive calls preserve each frame's values.
- A routine can read and update an ordinary chain variable.
- A routine can explicitly update a global variable.
- Input names may shadow variable names without collision.
- Caller waits for routine completion.
- Cancellation unwinds nested calls.
- Depth limits fail safely.

### Manual checks

- Run two parallel `travel to [destination]` calls with different destinations.
- Call a routine from another routine.
- Test a bounded recursive routine.
- Stop execution from both caller and callee views.
- Pause and resume during nested execution.

### Acceptance criteria

- Routine inputs are truly invocation-local.
- Routines compose, recurse, wait, cancel, and highlight correctly.
- Normal variables continue following their declared scope.

---

## Pass 7: Routine Lifecycle, Validation, and Polish

### Objective

Make routines safe to evolve after they have been used throughout a preset.

### Work

- Add routine management actions:
  - Rename
  - Edit inputs
  - Duplicate
  - Delete
  - Find usages
- Require confirmation or guided repair when deletion would break invocations.
- Add validation for missing definitions, duplicate routine IDs, invalid reporters, recursive depth risk, missing inputs, orphan bindings, and type mismatches.
- Add a compact inspector showing definition ownership and compatibility state.
- Add navigation from a validation issue to either the invocation or definition.
- Make sidebar ordering predictable and searchable.
- Add polished empty, loading, outdated, and broken-definition states.
- Verify routine edits participate in save state, dirty indicators, undo/redo, and crash recovery.
- Audit all user-facing text and mirrored compatibility UI.

### Acceptance criteria

- Editing a widely used routine is safe and understandable.
- Broken invocations are repairable rather than silently discarded.
- Validation covers both definition and invocation graphs.
- Routine UI meets the quality level of built-in nodes.

---

## Pass 8: Separate Run Preset from Reusable Behavior

### Objective

Make `Run Preset` the only user-facing mechanism for launching complete presets and remove the preset/custom-node conceptual overlap.

### Work

- Retain and polish the existing `Run Preset` dropdown workflow.
- Clearly document and present its semantics: it runs every START node in the selected preset.
- Decide and visibly communicate whether it waits or launches independently. Preserve current behavior unless a deliberate migration is approved.
- Remove automatic “every preset is a custom node” sidebar behavior for new data.
- Remove preset selectors and graph previews from new routine invocation nodes.
- Ensure routine definitions do not appear in the normal preset list.
- Add direct navigation/open actions for the selected preset.
- Validate missing, renamed, or recursive preset references.
- Keep a legacy adapter for old preset-backed custom nodes until Pass 9.

### Acceptance criteria

- Users can clearly explain the difference between Run Preset and a Routine.
- Creating a preset does not automatically create a reusable node.
- Creating a Routine does not create a user-visible preset.

---

## Pass 9: Legacy Migration

### Objective

Move existing Function/Call, Custom Node, and Template users to the new model without breaking old graphs.

### Work

- Inventory every current use of:
  - `EVENT_FUNCTION`
  - `EVENT_CALL`
  - `CUSTOM_NODE`
  - `TEMPLATE`
  - external event-triggered function names
- Keep external event handlers separate from synchronous routines. Do not accidentally convert game/chat event entry points into callable routines.
- Add conversion for named Function/Call pairs into zero-input or inferred-input routines.
- Convert eligible preset-backed custom nodes into embedded routines or explicit library routines.
- Preserve old custom-node per-preset input settings as migration defaults, not shared invocation arguments.
- Define Template migration based on its actual usage: duplicate graph content, linked routine, or legacy-only adapter.
- Load legacy nodes indefinitely or for a documented compatibility window.
- Hide legacy Function/Call/Custom Node/Template creation from the sidebar after migration is proven.
- Offer explicit conversion with preview and backup for ambiguous graphs.
- Never silently delete unresolved calls or inferred inputs.
- Add migration version metadata and idempotency tests.

### Automated coverage

- Old Function/Call graph loads and runs before conversion.
- Conversion preserves call targets and execution order.
- Duplicate function names produce a guided conflict.
- External event handlers remain event handlers.
- Old custom-node inputs become per-invocation defaults/bindings correctly.
- Re-running migration does not duplicate routines.
- Failed migration leaves original data intact.

### Manual checks

- Open representative presets from before the redesign.
- Convert simple, nested, recursive, and missing-target functions.
- Convert a preset-backed custom node with multiple inputs.
- Decline conversion and verify legacy execution still works.

### Acceptance criteria

- Existing users are not forced to rebuild graphs manually.
- New users no longer see overlapping legacy creation options.
- Events, routines, and presets remain distinct after conversion.

---

## Pass 10: Shared Routine Library

### Objective

Make routines powerful across presets without reverting to “every preset is a custom node.”

### Work

- Add an explicit **Add to Library** or **Share Routine** action.
- Define library ownership and storage separately from preset files.
- Let users insert a linked library routine or copy it into the current preset.
- For linked routines, expose update availability and interface compatibility in the inspector.
- Preserve stable routine and input IDs across export/import.
- Add conflict handling for duplicate IDs and names.
- Integrate routine packaging with marketplace/import/export only after local behavior is stable.
- Make dependency provenance visible without putting version badges on every normal invocation.
- Do not auto-publish or auto-share a routine.

### Acceptance criteria

- Cross-preset reuse is explicit.
- A user understands whether a routine is local, copied, or linked.
- Updating a library routine cannot silently corrupt existing invocation bindings.

---

## Pass 11: Cleanup, Documentation, and Release Hardening

### Objective

Remove obsolete implementation paths where safe and finish the redesign as a coherent release.

### Work

- Consolidate callable execution, signature, binding, and scope helpers into clear owners.
- Remove dead current-custom-node UI and inference code after compatibility adapters no longer depend on it.
- Remove global preset-input settings that have been fully migrated.
- Keep legacy enum values/deserializers when required for old save compatibility.
- Shrink special cases in `Node`, `NodeGraph`, and `ExecutionManager` rather than replacing them with new monoliths.
- Update `docs/node-architecture.md`, README feature descriptions, contributor guidance, and user documentation.
- Add an in-product tutorial for creating and calling a first Routine.
- Audit translations and all common/src, src, and fabric compat mirrors.
- Run performance tests with many definitions, invocations, nested calls, and parallel chains.
- Test import/export, marketplace payloads, crash recovery, undo/redo, replay, pause, stop-all, and every supported Minecraft target.
- Run the complete automated and target build matrix.

### Final acceptance criteria

- The primary sidebar presents Variables, Routines, and Run Preset as distinct concepts.
- A new user can create `break [block] within [range]`, define it, call it twice with different arguments, and run both calls successfully.
- Parallel and recursive calls do not leak inputs.
- Chain and global variables behave consistently and visibly.
- Old presets load and either execute through adapters or migrate safely.
- No user-visible routine depends on a hidden naming convention or ordinary preset selector.
- Full tests and all supported target builds pass.

## Explicitly Deferred Features

These features are not required to complete the initial Scratch-style routine redesign:

- Value-returning reporter routines.
- Boolean/predicate routines.
- Multiple flow outputs or early return values.
- Persistent variables that survive game/client restarts.
- Scratch's “run without screen refresh” option; Minecraft actions are asynchronous and need different semantics.
- Public marketplace distribution before the local/shared library model is stable.

The data model should leave room for custom value and condition nodes later, but these must not delay a reliable command/flow routine system.

## Completion Checklist

The redesign is complete only when all of the following are true:

- [x] Pass 1: Variable scope foundation
- [x] Pass 2: Variable scope UX
- [x] Pass 3: Routine persistence
- [ ] Pass 4: Definition workspace
- [ ] Pass 5: Invocation nodes
- [ ] Pass 6: Routine execution
- [ ] Pass 7: Lifecycle and polish
- [ ] Pass 8: Run Preset separation
- [ ] Pass 9: Legacy migration
- [ ] Pass 10: Shared routine library
- [ ] Pass 11: Cleanup and release hardening

# Pathmind 1.1.4 Release Notes

## User-visible Changes

- Added axis selection for the Position Of sensor with `x`, `y`, `z`, and `xyz` modes.
- Added coordinate parameter support to the Distance Between sensor.
- Added user parameter support to the Position Of sensor.
- Added a settings option to disable the in-game current node and current variables overlays.
- Added a new Mouse Button parameter node with a dropdown of mouse button inputs.
- Renamed Press Key to Press Button and updated its tooltip to match the broader input support.
- Added client-side and global output toggles to the Message node.
- Added double-click preset tab renaming.
- Added back the preset dropdown menu.
- Added a toggleable hold duration input to the Use node.
- Added an In Stock sensor.
- Simplified the Trade node to trade by list number instead of selecting a specific trade entry.
- Combined direction and cardinal direction into a single Direction parameter.
- Added a Block Face parameter with dropdown support, including Look node compatibility.
- Added a Wait Until logic node.
- Added search to the right-click node menu.
- Added a Custom Nodes category so presets can be run within other presets.
- Added session-persistent workspace pan and zoom state that resets when the game closes.
- Added double-click node execution when in-game, while preserving title-screen editing behavior.
- Added inline math editing so values can be adjusted with `+`, `-`, `/`, and `*` expressions.

## Fixes and Improvements

- Fixed Operator Not not working.
- Added support for the Hand parameter in the Interact node.
- Fixed editing for the List Length text input.
- Fixed the List Length node behavior.
- Fixed Craft node behavior on multiplayer.
- Combined Drop Item and Drop Slot into a single Drop node that accepts both slot and item parameters.
- Added a shared animated dropdown handler for in-node and parameter dropdowns.
- Updated dropdown animations to match the preset sandwich menu behavior.
- Updated the main menu icon.
- Updated missing translations.
- Replaced chat notifications with an overlay notification system.
- Added a debugger.

## Known Limitations

- Pathmind is a client-side Fabric mod and requires a matching Fabric Loader, Fabric API, and Java 21 environment.

## Supported Minecraft Versions

- `1.21`
- `1.21.1`
- `1.21.2`
- `1.21.3`
- `1.21.4`
- `1.21.5`
- `1.21.6`
- `1.21.7`
- `1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`
